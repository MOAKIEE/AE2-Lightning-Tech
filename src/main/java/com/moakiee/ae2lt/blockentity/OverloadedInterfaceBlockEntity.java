package com.moakiee.ae2lt.blockentity;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.google.common.util.concurrent.Runnables;
import com.moakiee.ae2lt.grid.OverloadedGridNodeOwner;
import com.moakiee.ae2lt.logic.AppFluxHelper;
import com.moakiee.ae2lt.logic.DirectMEInsertInventory;
import com.moakiee.ae2lt.logic.EjectModeRegistry;
import com.moakiee.ae2lt.logic.OverloadedInterfaceLogic;
import com.moakiee.ae2lt.menu.OverloadedInterfaceMenu;
import com.moakiee.ae2lt.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.api.util.AECableType;
import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.helpers.InterfaceLogic;

import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;
import appeng.parts.automation.StackWorldBehaviors;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class OverloadedInterfaceBlockEntity extends InterfaceBlockEntity
        implements OverloadedGridNodeOwner {

    public static final int SLOT_COUNT = 36;

    // ── NBT tags ─────────────────────────────────────────────────────────

    private static final String TAG_INTERFACE_MODE = "InterfaceMode";
    private static final String TAG_EXPORT_MODE   = "ExportMode";
    private static final String TAG_IMPORT_MODE   = "ImportMode";
    private static final String TAG_IO_SPEED_MODE  = "IOSpeedMode";
    private static final String TAG_CONNECTIONS    = "WirelessConnections";
    private static final String TAG_ENERGY_DIR     = "EnergyDir";
    private static final String TAG_UNLIMITED_SLOTS = "UnlimitedSlots";

    public enum InterfaceMode { NORMAL, WIRELESS }
    public enum IOSpeedMode   { NORMAL, FAST }
    public enum ExportMode    { OFF, AUTO }
    public enum ImportMode    { OFF, AUTO, EJECT }

    // ══════════════════════════════════════════════════════════════════════
    //  Transfer budget
    //  Reference: ExtAE extended bus — 96 base (4 speed cards) × 8 busSpeed
    //  = 768 items per activation.
    // ══════════════════════════════════════════════════════════════════════

    private static final int TRANSFER_BUDGET_NORMAL = 4096;
    private static final int TRANSFER_BUDGET_FAST   = 16384;

    /** ExternalStorageStrategy wrapper cache staleness guard (both directions). */
    private static final int WRAPPER_REFRESH_TICKS = 20;

    // ══════════════════════════════════════════════════════════════════════
    //  Cooldown / probe — per-mode parameters
    // ══════════════════════════════════════════════════════════════════════

    private static final int NORMAL_CD_INIT = 5;
    private static final int NORMAL_CD_MIN  = 5;
    private static final int NORMAL_CD_MAX  = 80;
    private static final int NORMAL_SPREAD  = 40;

    private static final int FAST_CD_INIT = 5;
    private static final int FAST_CD_MIN  = 1;
    private static final int FAST_CD_MAX  = 40;
    private static final int FAST_SPREAD  = 10;

    private static final int     COOLDOWN_NEAR_BAND       = 4;
    private static final int     COOLDOWN_STABLE_SUCCESSES = 2;
    private static final float[] PROBE_LEVELS = {5f, 3f, 2f, 1f, 0.5f, 0.3f, 0.1f};

    /** Reusable scan buffer for import — safe because server tick is single-threaded. */
    private final KeyCounter scanBuffer = new KeyCounter();

    // ── WirelessConnection ───────────────────────────────────────────────

    public record WirelessConnection(
            ResourceKey<Level> dimension, BlockPos pos, Direction boundFace
    ) {
        private static final String TAG_DIM  = "Dim";
        private static final String TAG_POS  = "Pos";
        private static final String TAG_FACE = "Face";

        public CompoundTag toTag() {
            var tag = new CompoundTag();
            tag.putString(TAG_DIM, dimension.location().toString());
            tag.putLong(TAG_POS, pos.asLong());
            tag.putInt(TAG_FACE, boundFace.get3DDataValue());
            return tag;
        }

        public static WirelessConnection fromTag(CompoundTag tag) {
            var dim = ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION,
                    ResourceLocation.parse(tag.getString(TAG_DIM)));
            return new WirelessConnection(
                    dim, BlockPos.of(tag.getLong(TAG_POS)),
                    Direction.from3DDataValue(tag.getInt(TAG_FACE)));
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  CooldownTracker — binary-search cooldown + early probe
    // ══════════════════════════════════════════════════════════════════════

    static final class CooldownTracker {
        final int cdMin, cdMax;
        long cooldownUntil = -1;
        int  cooldownN, searchLo, searchHi;
        int  stableSuccessStreak, failStreak;
        int  probeLevelIdx, probeSkipCounter;
        boolean probedThisCycle;

        CooldownTracker(int cdMin, int cdMax, int cdInit) {
            this.cdMin = cdMin; this.cdMax = cdMax;
            this.cooldownN = cdInit; this.searchLo = cdMin; this.searchHi = cdMax;
        }

        boolean isReady(long t) { return cooldownUntil < 0 || t >= cooldownUntil; }

        boolean isInProbeWindow(long t) {
            if (cooldownUntil < 0 || t >= cooldownUntil || probedThisCycle) return false;
            float lv = PROBE_LEVELS[probeLevelIdx];
            if (lv >= 1.0f) return t == cooldownUntil - (int) lv;
            int iv = Math.round(1.0f / lv);
            return probeSkipCounter >= iv && t == cooldownUntil - 1;
        }

        private boolean nearBand() { return searchHi - searchLo <= COOLDOWN_NEAR_BAND; }

        void onSuccess(long t) {
            if (cooldownUntil < 0) return;
            failStreak = 0;
            if (nearBand()) {
                if (++stableSuccessStreak >= COOLDOWN_STABLE_SUCCESSES) {
                    cooldownN = Math.max(cdMin, cooldownN - 1);
                    stableSuccessStreak = 0;
                }
            } else {
                stableSuccessStreak = 0; searchHi = cooldownN;
                cooldownN = Math.clamp(Math.max(searchLo, (searchLo + searchHi) / 2), cdMin, cdMax);
            }
            cooldownUntil = -1;
        }

        void onFail(long t) {
            if (cooldownUntil < 0) {
                cooldownUntil = t + cooldownN; stableSuccessStreak = 0;
                probedThisCycle = false; probeSkipCounter++; return;
            }
            stableSuccessStreak = 0;
            if (nearBand()) {
                failStreak++;
                cooldownN = Math.min(cdMax, cooldownN + Math.min(2, 1 + (failStreak - 1) / 4));
            } else {
                failStreak = 0; searchLo = cooldownN + 1;
                if (searchLo > searchHi) { searchLo = searchHi = cooldownN = cdMax; }
                else cooldownN = Math.clamp((searchLo + searchHi) / 2, cdMin, cdMax);
            }
            cooldownUntil = t + cooldownN; probedThisCycle = false; probeSkipCounter++;
        }

        void onProbeSuccess() {
            probeLevelIdx = 0; probeSkipCounter = 0; probedThisCycle = true; cooldownUntil = -1;
        }
        void onProbeFail() {
            probeLevelIdx = Math.min(probeLevelIdx + 1, PROBE_LEVELS.length - 1);
            probeSkipCounter = 0; probedThisCycle = true;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ConnectionState — AE2 storage bus caches + energy cache
    // ══════════════════════════════════════════════════════════════════════

    static final class ConnectionState {
        final CooldownTracker cd;

        // ── Storage: ExternalStorageStrategy wrappers (insert + extract) ──
        @Nullable WeakReference<BlockEntity> storageBERef;
        @Nullable Map<AEKeyType, ExternalStorageStrategy> storageStrategies;
        @Nullable Map<AEKeyType, MEStorage> storageWrappers;
        long storageWrapperTick = -1;

        // ── Energy ──
        @Nullable WeakReference<BlockEntity>    energyBERef;
        @Nullable WeakReference<IEnergyStorage> energyStorageRef;
        int  scheduleDelay = ENERGY_DELAY_MEAN;
        long lastCanReceive;

        ConnectionState(IOSpeedMode mode) {
            cd = mode == IOSpeedMode.FAST
                    ? new CooldownTracker(FAST_CD_MIN, FAST_CD_MAX, FAST_CD_INIT)
                    : new CooldownTracker(NORMAL_CD_MIN, NORMAL_CD_MAX, NORMAL_CD_INIT);
        }

        /**
         * Resolve cached storage wrappers (MEStorage facades from ExternalStorageStrategy).
         * Supports both insert (export) and extract (import) on the same wrappers.
         * Strategy objects are stable (they hold internal BlockCapabilityCache);
         * wrappers are rebuilt every {@link #WRAPPER_REFRESH_TICKS}.
         */
        @Nullable
        Map<AEKeyType, MEStorage> resolveWrappers(
                ServerLevel level, WirelessConnection conn) {
            BlockEntity be = level.getBlockEntity(conn.pos());
            if (be == null) {
                storageBERef = null; storageStrategies = null;
                storageWrappers = null; return null;
            }
            if (storageBERef == null || storageBERef.get() != be
                    || storageStrategies == null) {
                storageStrategies = StackWorldBehaviors.createExternalStorageStrategies(
                        level, conn.pos(), conn.boundFace());
                storageBERef = new WeakReference<>(be);
                storageWrappers = null; storageWrapperTick = -1;
            }
            if (storageStrategies.isEmpty()) return null;

            long gt = level.getGameTime();
            if (storageWrappers == null
                    || gt - storageWrapperTick >= WRAPPER_REFRESH_TICKS) {
                var map = new IdentityHashMap<AEKeyType, MEStorage>(
                        storageStrategies.size());
                for (var e : storageStrategies.entrySet()) {
                    var w = e.getValue().createWrapper(false, Runnables.doNothing());
                    if (w != null) map.put(e.getKey(), w);
                }
                storageWrappers = map.isEmpty() ? null : map;
                storageWrapperTick = gt;
            }
            return storageWrappers;
        }

        @Nullable
        IEnergyStorage resolveEnergy(ServerLevel level, WirelessConnection conn) {
            BlockEntity be = level.getBlockEntity(conn.pos());
            if (be == null) { energyBERef = null; energyStorageRef = null; return null; }
            if (energyBERef != null && energyBERef.get() == be
                    && energyStorageRef != null) {
                var s = energyStorageRef.get();
                if (s != null) return s;
            }
            var st = level.getCapability(Capabilities.EnergyStorage.BLOCK,
                    conn.pos(), conn.boundFace());
            if (st == null) { energyBERef = null; energyStorageRef = null; return null; }
            energyBERef      = new WeakReference<>(be);
            energyStorageRef = new WeakReference<>(st);
            return st;
        }
    }

    // ── Energy timing wheel ──────────────────────────────────────────────

    private static final int ENERGY_DELAY_MEAN = 5;
    private static final int ENERGY_DELAY_MAX  = 20;
    private static final int ENERGY_DELAY_MIN  = 1;
    private static final int WHEEL_SLOTS       = ENERGY_DELAY_MAX;

    static final class ScheduleEntry {
        final WirelessConnection conn; final ConnectionState state;
        ScheduleEntry(WirelessConnection c, ConnectionState s) { conn = c; state = s; }
    }

    @SuppressWarnings("unchecked")
    private final List<ScheduleEntry>[] energyWheel = new ArrayList[WHEEL_SLOTS];
    { for (int i = 0; i < WHEEL_SLOTS; i++) energyWheel[i] = new ArrayList<>(); }
    private List<ScheduleEntry> spareList = new ArrayList<>();
    private int  wheelPointer = 0;
    private final List<ScheduleEntry> deferredMachines = new ArrayList<>();
    private boolean wheelDirty = true;
    private long lastEnergyTickGameTime = -1;

    // ── Connection validation cache ──────────────────────────────────────

    private static final int VALIDATE_INTERVAL = 20;

    private final Map<WirelessConnection, ConnectionState> connectionStates =
            new HashMap<>();
    private List<WirelessConnection> validConnectionsCache = List.of();
    private long    validConnectionsCacheTick = -1;
    private boolean connectionsDirty = true;
    private int  ioRobinIndex;
    private long lastIORobinTick = -1;

    // ── Instance fields ──────────────────────────────────────────────────

    private InterfaceMode interfaceMode = InterfaceMode.NORMAL;
    private IOSpeedMode   ioSpeedMode   = IOSpeedMode.NORMAL;
    private ExportMode    exportMode    = ExportMode.OFF;
    private ImportMode    importMode    = ImportMode.OFF;
    private @Nullable Direction energyOutputDir = null;
    private final boolean[] unlimitedSlots = new boolean[SLOT_COUNT];
    private final List<WirelessConnection> connections = new ArrayList<>();
    private @Nullable DirectMEInsertInventory directInsertInv;
    private boolean ejectRegistered = false;
    private transient int lastViewedPage = 0;

    public int getLastViewedPage() { return lastViewedPage; }
    public void setLastViewedPage(int p) { lastViewedPage = p; }

    // ── Constructors + basic overrides ────────────────────────────────────

    public OverloadedInterfaceBlockEntity(BlockEntityType<?> betype,
                                          BlockPos pos, BlockState state) {
        super(betype, pos, state);
    }

    public OverloadedInterfaceBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.OVERLOADED_INTERFACE.get(), pos, state);
    }

    @Override
    protected InterfaceLogic createLogic() {
        return new OverloadedInterfaceLogic(getMainNode(), this,
                getItemFromBlockEntity().asItem(), SLOT_COUNT);
    }

    @Override
    public void openMenu(Player player, MenuHostLocator locator) {
        MenuOpener.open(OverloadedInterfaceMenu.TYPE, player, locator);
    }

    @Override
    public void returnToMainMenu(net.minecraft.world.entity.player.Player player,
                                  appeng.menu.ISubMenu subMenu) {
        MenuOpener.returnTo(OverloadedInterfaceMenu.TYPE, player, subMenu.getLocator());
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.DENSE_SMART;
    }

    // ── Direct ME insert inventory ───────────────────────────────────────

    public GenericInternalInventory getDirectInsertInventory() {
        if (directInsertInv == null) {
            directInsertInv = new DirectMEInsertInventory(
                    getMainNode(), IActionSource.ofMachine(this));
            rebuildFilter();
        }
        return directInsertInv;
    }

    public void rebuildFilter() {
        if (directInsertInv == null) return;
        var upgrades = getInterfaceLogic().getUpgrades();
        ItemStack filterStack = ItemStack.EMPTY;
        for (int i = 0; i < upgrades.size(); i++) {
            var s = upgrades.getStackInSlot(i);
            if (s.getItem() instanceof ICellWorkbenchItem) { filterStack = s; break; }
        }
        if (filterStack.isEmpty()
                || !(filterStack.getItem() instanceof ICellWorkbenchItem cwi)) {
            directInsertInv.setFilter(null); return;
        }
        var config = cwi.getConfigInventory(filterStack);
        var keys = new java.util.HashSet<AEKey>();
        for (int i = 0; i < config.size(); i++) {
            var k = config.getKey(i); if (k != null) keys.add(k);
        }
        if (keys.isEmpty()) { directInsertInv.setFilter(null); return; }

        boolean hasFuzzy = cwi.getUpgrades(filterStack)
                .getInstalledUpgrades(AEItems.FUZZY_CARD) > 0;
        FuzzyMode fm = hasFuzzy ? cwi.getFuzzyMode(filterStack) : null;
        if (fm != null) {
            directInsertInv.setFilter(w -> {
                for (var fk : keys)
                    if (w.equals(fk) || w.fuzzyEquals(fk, fm)) return true;
                return false;
            });
        } else {
            directInsertInv.setFilter(keys::contains);
        }
    }

    // ── Mode accessors ───────────────────────────────────────────────────

    public InterfaceMode getInterfaceMode() { return interfaceMode; }
    public void setInterfaceMode(InterfaceMode m) {
        if (interfaceMode == m) return; interfaceMode = m;
        invalidateConnectionCache(); refreshEjectRegistrations();
        saveChanges(); markForUpdate();
    }

    public IOSpeedMode getIOSpeedMode() { return ioSpeedMode; }
    public void setIOSpeedMode(IOSpeedMode m) {
        if (ioSpeedMode == m) return; ioSpeedMode = m;
        invalidateConnectionCache(); saveChanges(); markForUpdate();
    }

    public ExportMode getExportMode() { return exportMode; }
    public void setExportMode(ExportMode m) {
        if (exportMode == m) return; exportMode = m; saveChanges(); markForUpdate();
    }

    public ImportMode getImportMode() { return importMode; }
    public void setImportMode(ImportMode m) {
        if (importMode == m) return;
        var old = importMode; importMode = m;
        if ((old == ImportMode.EJECT) != (m == ImportMode.EJECT)) refreshEjectRegistrations();
        saveChanges(); markForUpdate();
    }

    public boolean isSlotUnlimited(int slot) {
        return slot >= 0 && slot < SLOT_COUNT && unlimitedSlots[slot];
    }
    public void setSlotUnlimited(int slot, boolean unlimited) {
        if (slot < 0 || slot >= SLOT_COUNT) return;
        unlimitedSlots[slot] = unlimited;
        saveChanges(); markForUpdate();
    }

    public @Nullable Direction getEnergyOutputDir() { return energyOutputDir; }
    public void setEnergyOutputDir(@Nullable Direction d) {
        if (energyOutputDir == d) return; energyOutputDir = d;
        saveChanges(); markForUpdate();
    }

    // ── Wireless connections ─────────────────────────────────────────────

    public List<WirelessConnection> getConnections() { return connections; }

    public void addOrUpdateConnection(WirelessConnection conn) {
        connections.removeIf(c ->
                c.dimension().equals(conn.dimension()) && c.pos().equals(conn.pos()));
        connections.add(conn);
        invalidateConnectionCache(); refreshEjectRegistrations();
        saveChanges(); markForUpdate();
    }

    public void removeConnection(ResourceKey<Level> dim, BlockPos pos) {
        connections.removeIf(c ->
                c.dimension().equals(dim) && c.pos().equals(pos));
        invalidateConnectionCache(); refreshEjectRegistrations();
        saveChanges(); markForUpdate();
    }

    // ── Connection state management ──────────────────────────────────────

    private ConnectionState getOrCreateState(WirelessConnection conn) {
        return connectionStates.computeIfAbsent(
                conn, k -> new ConnectionState(ioSpeedMode));
    }

    private void invalidateConnectionCache() {
        connectionsDirty = true;
        validConnectionsCache = List.of(); validConnectionsCacheTick = -1;
        wheelDirty = true;
        for (var sl : energyWheel) sl.clear();
        deferredMachines.clear(); connectionStates.clear();
    }

    private List<WirelessConnection> getOrRefreshValidConnections(
            ServerLevel sl, long gameTick) {
        if (!connectionsDirty
                && gameTick - validConnectionsCacheTick < VALIDATE_INTERVAL)
            return validConnectionsCache;
        var srv = sl.getServer();
        var valid = new ArrayList<WirelessConnection>();
        for (var c : connections) {
            var tl = srv.getLevel(c.dimension());
            if (tl == null || !tl.isLoaded(c.pos())) continue;
            if (tl.getBlockEntity(c.pos()) == null) continue;
            valid.add(c);
        }
        validConnectionsCache = List.copyOf(valid);
        validConnectionsCacheTick = gameTick; connectionsDirty = false;
        return validConnectionsCache;
    }

    @Nullable
    private ServerLevel resolveTargetLevel(
            ServerLevel origin, WirelessConnection conn) {
        var tl = origin.getServer().getLevel(conn.dimension());
        return (tl != null && tl.isLoaded(conn.pos())) ? tl : null;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Server tick
    // ══════════════════════════════════════════════════════════════════════

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                   OverloadedInterfaceBlockEntity be) {
        if (level == null || level.isClientSide()) return;
        if (!(level instanceof ServerLevel sl)) return;
        be.tickWirelessIO(sl);
        be.tickEnergyTransfer(sl);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Wireless I/O — shared cooldown, import first then export
    //  Uses AE2 ExternalStorageStrategy wrappers for both directions.
    // ══════════════════════════════════════════════════════════════════════

    private void tickWirelessIO(ServerLevel sl) {
        if (interfaceMode != InterfaceMode.WIRELESS) return;
        if (importMode == ImportMode.OFF && exportMode == ExportMode.OFF) return;
        var grid = getMainNode().getGrid();
        if (grid == null) return;

        long gameTick = sl.getGameTime();
        var valid = getOrRefreshValidConnections(sl, gameTick);
        int total = valid.size();
        if (total == 0) return;

        boolean fast = ioSpeedMode == IOSpeedMode.FAST;
        int spread = fast ? FAST_SPREAD : NORMAL_SPREAD;

        long elapsed = lastIORobinTick >= 0 ? gameTick - lastIORobinTick : 1;
        lastIORobinTick = gameTick;

        int perTick   = Math.max(1, (total + spread - 1) / spread);
        int toProcess = (int) Math.min((long) perTick * elapsed, total);

        var meStorage = grid.getStorageService().getInventory();
        var source    = IActionSource.ofMachine(this);

        for (int i = 0; i < toProcess; i++) {
            int idx = ioRobinIndex % total;
            ioRobinIndex = (ioRobinIndex + 1) % total;

            var conn  = valid.get(idx);
            var cst   = getOrCreateState(conn);
            var cd    = cst.cd;

            boolean probing = false;
            if (!cd.isReady(gameTick)) {
                if (fast && cd.isInProbeWindow(gameTick)) probing = true;
                else continue;
            }

            var targetLevel = resolveTargetLevel(sl, conn);
            if (targetLevel == null) {
                reportIO(cd, false, probing, gameTick); continue;
            }

            boolean importOk = false, exportOk = false;

            if (importMode != ImportMode.OFF)
                importOk = doImport(cst, targetLevel, conn, meStorage, source) > 0;
            if (exportMode == ExportMode.AUTO)
                exportOk = doExport(cst, targetLevel, conn, meStorage, source) > 0;

            reportIO(cd, importOk || exportOk, probing, gameTick);
        }
    }

    // ── Import: remote wrapper.extract → ME.insert ────────────────────

    private int doImport(ConnectionState cst, ServerLevel targetLevel,
                          WirelessConnection conn, MEStorage me, IActionSource src) {
        var wrappers = cst.resolveWrappers(targetLevel, conn);
        if (wrappers == null) return 0;

        int transferBudget = ioSpeedMode == IOSpeedMode.FAST ? TRANSFER_BUDGET_FAST : TRANSFER_BUDGET_NORMAL;
        int budget = transferBudget;
        int moved  = 0;

        // Merge available stacks across all wrappers to batch ME ops per unique key
        scanBuffer.reset();
        for (var wrapper : wrappers.values()) {
            wrapper.getAvailableStacks(scanBuffer);
        }

        for (var entry : scanBuffer) {
            if (budget <= 0) break;
            var key    = entry.getKey();
            long totalAvail = entry.getLongValue();
            if (totalAvail <= 0) continue;

            if (directInsertInv != null && !directInsertInv.isAllowedIn(0, key))
                continue;

            long toTransfer = Math.min(totalAvail, budget);
            long meCanAccept = me.insert(key, toTransfer, Actionable.SIMULATE, src);
            if (meCanAccept <= 0) continue;

            long extractBudget = Math.min(toTransfer, meCanAccept);
            long totalExtracted = 0;
            MEStorage lastExtractedWrapper = null;

            for (var wrapper : wrappers.values()) {
                if (totalExtracted >= extractBudget) break;
                long extracted = wrapper.extract(key, extractBudget - totalExtracted, Actionable.MODULATE, src);
                if (extracted > 0) {
                    totalExtracted += extracted;
                    lastExtractedWrapper = wrapper;
                }
            }

            if (totalExtracted <= 0) continue;

            long actualInserted = me.insert(key, totalExtracted, Actionable.MODULATE, src);
            if (totalExtracted > actualInserted && lastExtractedWrapper != null) {
                lastExtractedWrapper.insert(key, totalExtracted - actualInserted, Actionable.MODULATE, src);
            }
            moved  += (int) actualInserted;
            budget -= (int) actualInserted;
        }
        return moved;
    }

    // ── Export: ME.extract → remote wrapper.insert ────────────────────────

    private int doExport(ConnectionState cst, ServerLevel targetLevel,
                          WirelessConnection conn, MEStorage me, IActionSource src) {
        var wrappers = cst.resolveWrappers(targetLevel, conn);
        if (wrappers == null) return 0;

        var config     = getInterfaceLogic().getConfig();
        int configSize = config.size();
        int transferBudget = ioSpeedMode == IOSpeedMode.FAST ? TRANSFER_BUDGET_FAST : TRANSFER_BUDGET_NORMAL;
        int budget     = transferBudget;
        int moved      = 0;

        for (int ci = 0; ci < configSize && budget > 0; ci++) {
            var key = config.getKey(ci);
            if (key == null) continue;

            long maxAmount;
            if (unlimitedSlots[ci]) {
                maxAmount = Long.MAX_VALUE;
            } else {
                maxAmount = config.getAmount(ci);
                if (maxAmount <= 0) maxAmount = Long.MAX_VALUE;
            }

            long toMove = Math.min(Math.min(maxAmount, budget), transferBudget);

            // Simulate both sides to get a rough transfer estimate
            long meAvail = me.extract(key, toMove, Actionable.SIMULATE, src);
            if (meAvail <= 0) continue;
            long canAccept = 0;
            for (var wrapper : wrappers.values()) {
                long remaining = meAvail - canAccept;
                if (remaining <= 0) break;
                canAccept += wrapper.insert(key, remaining, Actionable.SIMULATE, src);
            }
            if (canAccept <= 0) continue;

            // Extract from ME first — authoritative source, prevents duplication
            long actualExtracted = me.extract(key, Math.min(meAvail, canAccept),
                    Actionable.MODULATE, src);
            if (actualExtracted <= 0) continue;

            long totalInserted = 0;
            for (var wrapper : wrappers.values()) {
                long remaining = actualExtracted - totalInserted;
                if (remaining <= 0) break;
                long ins = wrapper.insert(key, remaining, Actionable.MODULATE, src);
                totalInserted += ins;
            }

            // Return excess to ME if wrappers accepted less than extracted
            if (actualExtracted > totalInserted) {
                me.insert(key, actualExtracted - totalInserted, Actionable.MODULATE, src);
            }

            moved  += (int) totalInserted;
            budget -= (int) totalInserted;
        }
        return moved;
    }

    private static void reportIO(CooldownTracker cd, boolean ok,
                                  boolean probing, long t) {
        if (probing) { if (ok) cd.onProbeSuccess(); else cd.onProbeFail(); }
        else         { if (ok) cd.onSuccess(t);     else cd.onFail(t);     }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Energy transfer
    // ══════════════════════════════════════════════════════════════════════

    private boolean hasInductionCard() {
        if (!AppFluxHelper.isAvailable()) return false;
        var u = getInterfaceLogic().getUpgrades();
        for (int i = 0; i < u.size(); i++)
            if (AppFluxHelper.isInductionCard(u.getStackInSlot(i).getItem())) return true;
        return false;
    }

    private void tickEnergyTransfer(ServerLevel sl) {
        if (!hasInductionCard()) return;
        var feKey = AppFluxHelper.FE_KEY; if (feKey == null) return;
        var grid = getMainNode().getGrid(); if (grid == null) return;
        if (interfaceMode == InterfaceMode.WIRELESS) tickWirelessEnergy(sl, feKey);
        else if (energyOutputDir != null)            tickNormalEnergy(sl, feKey);
    }

    private void tickNormalEnergy(ServerLevel sl, AEKey feKey) {
        var tp = getBlockPos().relative(energyOutputDir);
        var st = sl.getCapability(Capabilities.EnergyStorage.BLOCK,
                tp, energyOutputDir.getOpposite());
        if (st == null) return;
        int cr = st.receiveEnergy(AppFluxHelper.TRANSFER_RATE, true);
        if (cr <= 0) return;
        var mes = getMainNode().getGrid().getStorageService().getInventory();
        var src = IActionSource.ofMachine(this);
        long ext = mes.extract(feKey, cr, Actionable.MODULATE, src);
        if (ext <= 0) return;
        int acc = st.receiveEnergy((int) ext, false);
        if (ext - acc > 0) mes.insert(feKey, ext - acc, Actionable.MODULATE, src);
    }

    // ── Wireless energy: timing-wheel scheduler ──────────────────────────

    private void tickWirelessEnergy(ServerLevel sl, AEKey feKey) {
        long gt = sl.getGameTime();
        if (gt == lastEnergyTickGameTime) return;
        int el = lastEnergyTickGameTime >= 0
                ? (int) Math.min(gt - lastEnergyTickGameTime, WHEEL_SLOTS) : 1;
        lastEnergyTickGameTime = gt;
        distributeWirelessEnergy(sl, feKey, gt, el);
    }

    private void distributeWirelessEnergy(
            ServerLevel sl, AEKey feKey, long tick, int elapsed) {
        var valid = getOrRefreshValidConnections(sl, tick);
        if (valid.isEmpty()) return;
        wheelPointer = (wheelPointer + 1) % WHEEL_SLOTS;
        if (wheelDirty) rebuildEWheel(valid);

        if (!deferredMachines.isEmpty()) {
            long avail = simulateFluxExtract(feKey,
                    (long) AppFluxHelper.TRANSFER_RATE * deferredMachines.size());
            if (avail <= 0) return;
            processEBatch(sl, deferredMachines, feKey, avail);
            if (!deferredMachines.isEmpty()) return;
        }
        for (int s = 1; s < elapsed && energyWheel[wheelPointer].isEmpty(); s++)
            wheelPointer = (wheelPointer + 1) % WHEEL_SLOTS;

        var eligible = pollWheel();
        if (eligible.isEmpty()) return;
        long avail = simulateFluxExtract(feKey, (long) AppFluxHelper.TRANSFER_RATE * eligible.size());
        if (avail <= 0) {
            deferredMachines.addAll(eligible);
            eligible.clear();
            return;
        }
        processEBatch(sl, eligible, feKey, avail);
    }

    private final List<ScheduleEntry> tempEnergyDefer = new ArrayList<>();
    private final List<ScheduleEntry> tempStarved     = new ArrayList<>();

    private void processEBatch(ServerLevel sl, List<ScheduleEntry> batch,
                                AEKey feKey, long avail) {
        int cnt = batch.size();
        long total = 0;
        long[] canReceive   = new long[cnt];
        int[]  maxCapacity  = new int[cnt];
        long[] storedEnergy = new long[cnt];

        for (int i = 0; i < cnt; i++) {
            var entry = batch.get(i);
            var targetLevel = resolveTargetLevel(sl, entry.conn);
            var storage = targetLevel != null ? entry.state.resolveEnergy(targetLevel, entry.conn) : null;
            if (storage != null) {
                canReceive[i]   = storage.receiveEnergy(Integer.MAX_VALUE, true);
                maxCapacity[i]  = storage.getMaxEnergyStored();
                storedEnergy[i] = storage.getEnergyStored();
                total += canReceive[i];
            }
        }

        if (total <= 0) {
            for (int i = 0; i < cnt; i++) {
                adjustScheduleDelay(batch.get(i), 0, storedEnergy[i], maxCapacity[i]);
                scheduleEnergyEntry(batch.get(i));
            }
            batch.clear();
            return;
        }

        long extracted = extractFlux(feKey, Math.min(total, avail));
        if (extracted <= 0) {
            tempEnergyDefer.clear();
            for (int i = 0; i < cnt; i++) {
                if (canReceive[i] == 0) {
                    adjustScheduleDelay(batch.get(i), 0, storedEnergy[i], maxCapacity[i]);
                    scheduleEnergyEntry(batch.get(i));
                } else {
                    tempEnergyDefer.add(batch.get(i));
                }
            }
            batch.clear();
            if (!tempEnergyDefer.isEmpty()) {
                deferredMachines.addAll(tempEnergyDefer);
                tempEnergyDefer.clear();
            }
            return;
        }

        long remaining = extracted;
        tempStarved.clear();

        for (int i = 0; i < cnt; i++) {
            var entry = batch.get(i);
            if (canReceive[i] > 0 && remaining > 0) {
                long share = Math.min(canReceive[i], remaining);
                var targetLevel = resolveTargetLevel(sl, entry.conn);
                var storage = targetLevel != null
                        ? entry.state.resolveEnergy(targetLevel, entry.conn) : null;
                long accepted = storage != null
                        ? storage.receiveEnergy((int) Math.min(share, Integer.MAX_VALUE), false) : 0;
                remaining -= accepted;

                boolean budgetSufficient = (share == canReceive[i]);
                if (budgetSufficient && accepted > 0 && canReceive[i] > accepted * 2) {
                    entry.state.scheduleDelay = Math.min(
                            entry.state.scheduleDelay * (int) (canReceive[i] / accepted),
                            ENERGY_DELAY_MAX);
                    entry.state.lastCanReceive = canReceive[i];
                } else if (budgetSufficient && accepted == 0) {
                    entry.state.scheduleDelay = ENERGY_DELAY_MAX;
                    entry.state.lastCanReceive = canReceive[i];
                } else {
                    adjustScheduleDelay(entry, canReceive[i], storedEnergy[i], maxCapacity[i]);
                }
                scheduleEnergyEntry(entry);
            } else if (canReceive[i] == 0) {
                adjustScheduleDelay(entry, 0, storedEnergy[i], maxCapacity[i]);
                scheduleEnergyEntry(entry);
            } else {
                tempStarved.add(entry);
            }
        }

        batch.clear();
        if (!tempStarved.isEmpty()) {
            deferredMachines.addAll(tempStarved);
            tempStarved.clear();
        }
        if (remaining > 0) {
            insertFlux(feKey, remaining);
        }
    }

    private void adjustScheduleDelay(ScheduleEntry entry, long canReceive,
                                      long storedEnergy, int maxCapacity) {
        var state = entry.state;
        int delay = state.scheduleDelay;

        if (canReceive > state.lastCanReceive) {
            delay = delay > ENERGY_DELAY_MEAN ? delay / 2 : delay - 1;
        } else if ((state.lastCanReceive - canReceive) << 2 < maxCapacity) {
            if (storedEnergy * 3 <= (long) maxCapacity << 1) {
                delay = delay > ENERGY_DELAY_MEAN ? delay / 2 : delay - 1;
            } else {
                if (delay > ENERGY_DELAY_MEAN) delay--;
                else if (delay < ENERGY_DELAY_MEAN) delay++;
            }
        } else {
            delay++;
        }

        state.scheduleDelay = Math.clamp(delay, ENERGY_DELAY_MIN, ENERGY_DELAY_MAX);
        state.lastCanReceive = canReceive;
    }

    private List<ScheduleEntry> pollWheel() {
        var list = energyWheel[wheelPointer];
        if (list.isEmpty()) return list;
        energyWheel[wheelPointer] = spareList;
        spareList = list;
        return list;
    }

    private void scheduleEnergyEntry(ScheduleEntry entry) {
        energyWheel[(wheelPointer + entry.state.scheduleDelay) % WHEEL_SLOTS].add(entry);
    }

    private void rebuildEWheel(List<WirelessConnection> valid) {
        for (var sl : energyWheel) sl.clear();
        deferredMachines.clear();
        for (var c : valid) {
            scheduleEnergyEntry(new ScheduleEntry(c, getOrCreateState(c)));
        }
        wheelDirty = false;
    }

    private long simulateFluxExtract(AEKey key, long max) {
        var grid = getMainNode().getGrid();
        if (grid == null) return 0;
        return grid.getStorageService().getInventory()
                .extract(key, max, Actionable.SIMULATE, IActionSource.ofMachine(this));
    }

    private long extractFlux(AEKey key, long amount) {
        if (amount <= 0) return 0;
        var grid = getMainNode().getGrid();
        if (grid == null) return 0;
        return grid.getStorageService().getInventory()
                .extract(key, amount, Actionable.MODULATE, IActionSource.ofMachine(this));
    }

    private void insertFlux(AEKey key, long amount) {
        if (amount <= 0) return;
        var grid = getMainNode().getGrid();
        if (grid == null) return;
        grid.getStorageService().getInventory()
                .insert(key, amount, Actionable.MODULATE, IActionSource.ofMachine(this));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Eject mode
    // ══════════════════════════════════════════════════════════════════════

    public void refreshEjectRegistrations() {
        unregisterEject();
        if (importMode != ImportMode.EJECT || level==null || level.isClientSide()) return;
        var srv = level.getServer(); if (srv==null) return;
        if (interfaceMode == InterfaceMode.WIRELESS) {
            for (var c : connections)
                registerEjectAt(srv, c.dimension(),
                        c.pos().relative(c.boundFace()), c.boundFace().getOpposite());
        } else {
            for (Direction d : Direction.values())
                registerEjectAt(srv, level.dimension(),
                        getBlockPos().relative(d), d.getOpposite());
        }
        ejectRegistered = true;
    }

    private void registerEjectAt(net.minecraft.server.MinecraftServer srv,
                                  ResourceKey<Level> dim, BlockPos ip, Direction iface) {
        var tl = srv.getLevel(dim); if (tl==null) return;
        var ghost = new GhostOutputBlockEntity(ip); ghost.setLevel(tl);
        EjectModeRegistry.register(dim, ip.asLong(), iface,
                new EjectModeRegistry.EjectEntry(
                        new java.lang.ref.WeakReference<>(this), ghost,
                        level.dimension(), getBlockPos()));
        if (tl instanceof ServerLevel s) s.invalidateCapabilities(ip);
    }

    private void unregisterEject() {
        if (!ejectRegistered || level==null) return;
        var removed = EjectModeRegistry.unregisterAll(this, true);
        if (level instanceof ServerLevel sl) {
            var srv = sl.getServer();
            for (var dp : removed) {
                var t = srv.getLevel(dp.dimension());
                if (t!=null) t.invalidateCapabilities(dp.pos());
            }
        }
        ejectRegistered = false;
    }

    @Override public void setRemoved()      { unregisterEject(); super.setRemoved(); }
    @Override public void onChunkUnloaded() { super.onChunkUnloaded(); unregisterEject(); }

    // ══════════════════════════════════════════════════════════════════════
    //  NBT
    // ══════════════════════════════════════════════════════════════════════

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeByte(interfaceMode.ordinal());
        data.writeByte(ioSpeedMode.ordinal());
        data.writeByte(exportMode.ordinal());
        data.writeByte(importMode.ordinal());
        data.writeByte(energyOutputDir != null ? energyOutputDir.get3DDataValue() : -1);

        long bits = 0;
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (unlimitedSlots[i]) {
                bits |= (1L << i);
            }
        }
        data.writeLong(bits);

        data.writeVarInt(connections.size());
        for (var c : connections) {
            data.writeResourceLocation(c.dimension().location());
            data.writeBlockPos(c.pos());
            data.writeByte(c.boundFace().get3DDataValue());
        }
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);

        var newInterfaceMode = InterfaceMode.values()[data.readByte()];

        int speedOrd = data.readByte();
        var newIoSpeedMode = speedOrd >= 0 && speedOrd < IOSpeedMode.values().length
                ? IOSpeedMode.values()[speedOrd] : IOSpeedMode.NORMAL;

        int exportOrd = data.readByte();
        var newExportMode = exportOrd >= 0 && exportOrd < ExportMode.values().length
                ? ExportMode.values()[exportOrd] : ExportMode.OFF;

        int importOrd = data.readByte();
        var newImportMode = importOrd >= 0 && importOrd < ImportMode.values().length
                ? ImportMode.values()[importOrd] : ImportMode.OFF;

        int energyOrd = data.readByte();
        Direction newEnergyDir = energyOrd >= 0 && energyOrd < 6
                ? Direction.from3DDataValue(energyOrd) : null;

        long newBits = data.readLong();
        boolean[] newUnlimitedSlots = new boolean[SLOT_COUNT];
        for (int i = 0; i < SLOT_COUNT; i++) {
            newUnlimitedSlots[i] = (newBits & (1L << i)) != 0;
        }

        int count = data.readVarInt();
        var newConnections = new ArrayList<WirelessConnection>(count);
        for (int i = 0; i < count; i++) {
            var dim = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    data.readResourceLocation());
            var pos = data.readBlockPos();
            var face = Direction.from3DDataValue(data.readByte());
            newConnections.add(new WirelessConnection(dim, pos, face));
        }

        boolean unlimitedChanged = false;
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (unlimitedSlots[i] != newUnlimitedSlots[i]) {
                unlimitedChanged = true;
                break;
            }
        }

        if (newInterfaceMode != interfaceMode
                || newIoSpeedMode != ioSpeedMode
                || newExportMode != exportMode
                || newImportMode != importMode
                || newEnergyDir != energyOutputDir
                || unlimitedChanged
                || !newConnections.equals(connections)) {
            interfaceMode = newInterfaceMode;
            ioSpeedMode = newIoSpeedMode;
            exportMode = newExportMode;
            importMode = newImportMode;
            energyOutputDir = newEnergyDir;
            System.arraycopy(newUnlimitedSlots, 0, unlimitedSlots, 0, SLOT_COUNT);
            connections.clear();
            connections.addAll(newConnections);
            changed = true;
        }

        return changed;
    }

    @Override
    public void saveAdditional(CompoundTag d, HolderLookup.Provider r) {
        super.saveAdditional(d, r);
        d.putString(TAG_INTERFACE_MODE, interfaceMode.name());
        d.putString(TAG_IO_SPEED_MODE, ioSpeedMode.name());
        d.putString(TAG_EXPORT_MODE, exportMode.name());
        d.putString(TAG_IMPORT_MODE, importMode.name());
        d.putInt(TAG_ENERGY_DIR, energyOutputDir!=null ? energyOutputDir.get3DDataValue() : -1);
        long bits = 0;
        for (int i = 0; i < SLOT_COUNT; i++) if (unlimitedSlots[i]) bits |= (1L << i);
        d.putLong(TAG_UNLIMITED_SLOTS, bits);
        var cl = new ListTag();
        for (var c : connections) cl.add(c.toTag());
        d.put(TAG_CONNECTIONS, cl);
    }

    @Override
    public void loadTag(CompoundTag d, HolderLookup.Provider r) {
        super.loadTag(d, r);
        if (d.contains(TAG_INTERFACE_MODE)) {
            try { interfaceMode = InterfaceMode.valueOf(d.getString(TAG_INTERFACE_MODE)); }
            catch (IllegalArgumentException e) { interfaceMode = InterfaceMode.NORMAL; }
        }
        if (d.contains(TAG_IO_SPEED_MODE)) {
            try { ioSpeedMode = IOSpeedMode.valueOf(d.getString(TAG_IO_SPEED_MODE)); }
            catch (IllegalArgumentException e) { ioSpeedMode = IOSpeedMode.NORMAL; }
        }
        if (d.contains(TAG_EXPORT_MODE)) {
            try { exportMode = ExportMode.valueOf(d.getString(TAG_EXPORT_MODE)); }
            catch (IllegalArgumentException e) { exportMode = ExportMode.OFF; }
        }
        if (d.contains(TAG_IMPORT_MODE)) {
            try { importMode = ImportMode.valueOf(d.getString(TAG_IMPORT_MODE)); }
            catch (IllegalArgumentException e) { importMode = ImportMode.OFF; }
        }
        long bits = d.getLong(TAG_UNLIMITED_SLOTS);
        for (int i = 0; i < SLOT_COUNT; i++) unlimitedSlots[i] = (bits & (1L << i)) != 0;
        int ev = d.contains(TAG_ENERGY_DIR) ? d.getInt(TAG_ENERGY_DIR) : -1;
        energyOutputDir = ev>=0 && ev<6 ? Direction.from3DDataValue(ev) : null;
        connections.clear();
        if (d.contains(TAG_CONNECTIONS, Tag.TAG_LIST)) {
            var cl = d.getList(TAG_CONNECTIONS, Tag.TAG_COMPOUND);
            for (int i = 0; i < cl.size(); i++)
                connections.add(WirelessConnection.fromTag(cl.getCompound(i)));
        }
    }

}
