package com.moakiee.ae2lt.blockentity;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.google.common.util.concurrent.Runnables;
import com.moakiee.ae2lt.grid.OverloadedGridNodeOwner;
import com.moakiee.ae2lt.item.OverloadedFilterComponentItem;
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
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.api.util.AECableType;
import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.helpers.InterfaceLogic;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;

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
    private static final String TAG_FILTER_INV     = "FilterInv";

    public enum InterfaceMode { NORMAL, WIRELESS }
    public enum IOSpeedMode   { NORMAL, FAST }
    public enum ExportMode    { OFF, AUTO }
    public enum ImportMode    { OFF, AUTO, EJECT }

    // ══════════════════════════════════════════════════════════════════════
    //  Transfer budget
    //  Reference: ExtAE extended bus — 96 base (4 speed cards) × 8 busSpeed
    //  = 768 items per activation.
    // ══════════════════════════════════════════════════════════════════════

    /** ExternalStorageStrategy wrapper cache staleness guard (both directions). */
    private static final int WRAPPER_REFRESH_TICKS = 20;

    // ══════════════════════════════════════════════════════════════════════
    //  Cooldown — per-mode parameters
    // ══════════════════════════════════════════════════════════════════════

    private static final int NORMAL_CD_MIN  = 5;
    private static final int NORMAL_CD_MAX  = 80;

    private static final int FAST_CD_MIN  = 1;
    private static final int FAST_CD_MAX  = 40;

    /** IO timing wheel: 128 slots (power of 2, covers max cdMax=80). */
    private static final int IO_WHEEL_SIZE = 128;

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
    //  IO types
    // ══════════════════════════════════════════════════════════════════════

    enum IoDirection { IMPORT, EXPORT }
    enum IoPhase { PROBE, EXTRACT }

    static final class ProbeState {
        int levelIdx = 0;
        int skipCounter = 0;
        void reset() { levelIdx = 0; skipCounter = 0; }
    }

    static final class KeyModel {
        long maxObserved;
        long effectiveMax;
        long lastAvail;
        long lastTick;
        double rateEMA;
        long postExtractAvail;
        long postExtractTick = -1;
        long midProbeAvail;
        long midProbeTick = -1;
        double half1Rate;

        long predictedAvail(long now) {
            if (lastTick <= 0) return 0;
            long dt = now - lastTick;
            return lastAvail + (long)(rateEMA * dt);
        }

        void resetCycle() {
            postExtractTick = -1;
            midProbeTick = -1;
            half1Rate = 0;
        }
    }

    private static final float[] PROBE_LEVELS = {5f, 3f, 2f, 1f, 0.5f, 0.3f, 0.1f};
    private static final int IMPORT_FLUSH_INTERVAL = 5;
    private static final int STOP_IMPORT_TTL = 20;
    private static final String TAG_IMPORT_BUFFER = "ae2ltImportBuffer";
    private static final String TAG_IMPORT_FLUSH_TICK = "ae2ltImportFlushTick";

    // ══════════════════════════════════════════════════════════════════════
    //  CooldownTracker — per-channel adaptive cooldown
    // ══════════════════════════════════════════════════════════════════════

    static final class CooldownTracker {
        long cooldownUntil = -1;
        int cd = FAST_CD_MIN;
        int scheduleGeneration;
        long lastSuccessTick = -1;
        long lastSuccessInterval = -1;

        void onSuccess(long now, boolean fast, KeyModel model) {
            if (fast) {
                cd = FAST_CD_MIN;
                if (lastSuccessTick > 0) lastSuccessInterval = now - lastSuccessTick;
                lastSuccessTick = now;
            } else {
                long deficit = (long)(model.effectiveMax * 0.85) - model.lastAvail;
                if (deficit <= 0) {
                    cd = NORMAL_CD_MIN;
                } else if (model.rateEMA > 0) {
                    cd = (int) Math.max(NORMAL_CD_MIN,
                            Math.min((long)(deficit / model.rateEMA), NORMAL_CD_MAX));
                } else {
                    cd = (NORMAL_CD_MIN + NORMAL_CD_MAX) / 2;
                }
            }
            cooldownUntil = now + cd;
        }

        void onFail(long now, boolean fast) {
            if (fast) {
                cd = Math.min(cd + 1,
                        Math.min(lastSuccessInterval > 0
                                ? (int) lastSuccessInterval : FAST_CD_MAX, FAST_CD_MAX));
            } else {
                cd = Math.min(cd * 3 / 2 + 1, NORMAL_CD_MAX);
            }
            cooldownUntil = now + cd;
        }

        long cooldownUntil() { return cooldownUntil; }

        void reset() {
            cooldownUntil = -1;
            cd = FAST_CD_MIN;
            scheduleGeneration++;
            lastSuccessTick = -1;
            lastSuccessInterval = -1;
        }

        static CooldownTracker forChannel(AEKeyType type, IoDirection dir) {
            return new CooldownTracker();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ConnectionState — AE2 storage bus caches + energy cache
    // ══════════════════════════════════════════════════════════════════════

    static final class ConnectionState {
        final Map<AEKeyType, CooldownTracker> importCDs = new IdentityHashMap<>();
        final Map<AEKeyType, CooldownTracker> exportCDs = new IdentityHashMap<>();
        final Map<AEKeyType, ProbeState> importProbeStates = new IdentityHashMap<>();
        final Map<AEKeyType, KeyModel> keyModels = new IdentityHashMap<>();

        @Nullable WeakReference<BlockEntity> storageBERef;
        @Nullable Map<AEKeyType, ExternalStorageStrategy> storageStrategies;
        @Nullable Map<AEKeyType, MEStorage> storageWrappers;
        long storageWrapperTick = -1;

        @Nullable WeakReference<BlockEntity>    energyBERef;
        @Nullable WeakReference<IEnergyStorage> energyStorageRef;
        int  scheduleDelay = ENERGY_DELAY_MEAN;
        long lastCanReceive;

        CooldownTracker cdFor(AEKeyType type, IoDirection dir) {
            var map = (dir == IoDirection.IMPORT) ? importCDs : exportCDs;
            return map.computeIfAbsent(type, k -> CooldownTracker.forChannel(k, dir));
        }

        ProbeState probeStateFor(AEKeyType type) {
            return importProbeStates.computeIfAbsent(type, k -> new ProbeState());
        }

        KeyModel modelFor(AEKeyType type) {
            return keyModels.computeIfAbsent(type, k -> new KeyModel());
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

    // ── Energy schedule entry ─────────────────────────────────────────
    static final class ScheduleEntry {
        final WirelessConnection conn; final ConnectionState state;
        ScheduleEntry(WirelessConnection c, ConnectionState s) { conn = c; state = s; }
    }

    // ── IO timing-wheel entry ─────────────────────────────────────────
    static final class IoScheduledEntry {
        final WirelessConnection conn;
        final ConnectionState cst;
        final AEKeyType keyType;
        final IoDirection direction;
        final int generation;
        IoPhase phase;

        IoScheduledEntry(WirelessConnection conn, ConnectionState cst,
                         AEKeyType keyType, IoDirection direction, int generation) {
            this.conn = conn; this.cst = cst;
            this.keyType = keyType; this.direction = direction;
            this.generation = generation;
            this.phase = IoPhase.EXTRACT;
        }
    }

    // ── Energy wheel ─────────────────────────────────────────────────
    @SuppressWarnings("unchecked")
    private final List<ScheduleEntry>[] energyWheel = new ArrayList[WHEEL_SLOTS];
    { for (int i = 0; i < WHEEL_SLOTS; i++) energyWheel[i] = new ArrayList<>(); }
    private List<ScheduleEntry> spareList = new ArrayList<>();
    private int  wheelPointer = 0;
    private final List<ScheduleEntry> deferredMachines = new ArrayList<>();
    private boolean wheelDirty = true;
    private long lastEnergyTickGameTime = -1;

    // ── IO timing wheel ──────────────────────────────────────────────
    @SuppressWarnings("unchecked")
    private final List<IoScheduledEntry>[] ioWheel = new ArrayList[IO_WHEEL_SIZE];
    { for (int i = 0; i < IO_WHEEL_SIZE; i++) ioWheel[i] = new ArrayList<>(); }
    private List<IoScheduledEntry> ioSpareList = new ArrayList<>();
    private List<WirelessConnection> lastScheduledValid = List.of();

    // ── Connection validation cache ──────────────────────────────────────

    private static final int VALIDATE_INTERVAL = 20;

    private final Map<WirelessConnection, ConnectionState> connectionStates =
            new HashMap<>();
    private List<WirelessConnection> validConnectionsCache = List.of();
    private long    validConnectionsCacheTick = -1;
    private boolean connectionsDirty = true;

    // ── Instance fields ──────────────────────────────────────────────────

    private InterfaceMode interfaceMode = InterfaceMode.NORMAL;
    private IOSpeedMode   ioSpeedMode   = IOSpeedMode.NORMAL;
    private ExportMode    exportMode    = ExportMode.OFF;
    private ImportMode    importMode    = ImportMode.OFF;
    private @Nullable Direction energyOutputDir = null;
    private final boolean[] unlimitedSlots = new boolean[SLOT_COUNT];
    private final List<WirelessConnection> connections = new ArrayList<>();
    private final InternalInventoryHost filterInvHost = new InternalInventoryHost() {
        @Override
        public void saveChangedInventory(AppEngInternalInventory inv) {
            saveChanges(); markForUpdate();
        }

        @Override
        public void onChangeInventory(AppEngInternalInventory inv, int slot) {
            rebuildFilter();
        }

        @Override
        public boolean isClientSide() {
            return level != null && level.isClientSide();
        }
    };
    private final AppEngInternalInventory filterInv = new AppEngInternalInventory(filterInvHost, 1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return !stack.isEmpty() && stack.getItem() instanceof OverloadedFilterComponentItem;
        }
    };
    private @Nullable DirectMEInsertInventory directInsertInv;
    private @Nullable Set<AEKey> importFilterKeys;
    private @Nullable FuzzyMode importFilterFuzzyMode;
    private boolean inductionCardCacheDirty = true;
    private boolean inductionCardInstalledCache = false;
    private boolean unloadingChunk = false;
    private transient int lastViewedPage = 0;

    // ── Import buffer (persistent) + per-keytype backpressure (transient) ──
    private final Map<AEKey, Long> importBuffer = new LinkedHashMap<>();
    private long importBufferLastFlushTick = Long.MIN_VALUE;
    private final Map<AEKeyType, Long> keyTypeLockUntil = new HashMap<>();

    public Map<AEKey, Long> getImportBuffer() { return importBuffer; }

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
    public void onLoad() {
        super.onLoad();
        unloadingChunk = false;
        if (level != null && !level.isClientSide() && importMode == ImportMode.EJECT) {
            refreshEjectRegistrations();
        }
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

    public AppEngInternalInventory getFilterInv() {
        return filterInv;
    }

    public void rebuildFilter() {
        // 过滤器变动(无论是清空还是重填)都唤醒 IO:避免过滤器刚改完还卡在空转退避
        wakeWirelessIo();
        ItemStack filterStack = filterInv.getStackInSlot(0);
        if (filterStack.isEmpty()
                || !(filterStack.getItem() instanceof ICellWorkbenchItem cwi)) {
            importFilterKeys = null;
            importFilterFuzzyMode = null;
            if (directInsertInv != null) directInsertInv.setFilter(null);
            return;
        }
        var config = cwi.getConfigInventory(filterStack);
        var keys = new HashSet<AEKey>();
        for (int i = 0; i < config.size(); i++) {
            var k = config.getKey(i); if (k != null) keys.add(k);
        }
        if (keys.isEmpty()) {
            importFilterKeys = null;
            importFilterFuzzyMode = null;
            if (directInsertInv != null) directInsertInv.setFilter(null);
            return;
        }

        boolean hasFuzzy = cwi.getUpgrades(filterStack)
                .getInstalledUpgrades(AEItems.FUZZY_CARD) > 0;
        FuzzyMode fm = hasFuzzy ? cwi.getFuzzyMode(filterStack) : null;
        importFilterKeys = Set.copyOf(keys);
        importFilterFuzzyMode = fm;

        Predicate<AEKey> predicate;
        if (fm != null) {
            predicate = w -> {
                for (var fk : keys)
                    if (w.equals(fk) || w.fuzzyEquals(fk, fm)) return true;
                return false;
            };
        } else {
            predicate = keys::contains;
        }
        if (directInsertInv != null) directInsertInv.setFilter(predicate);
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
        wakeWirelessIo();
        saveChanges(); markForUpdate();
    }

    public ExportMode getExportMode() { return exportMode; }
    public void setExportMode(ExportMode m) {
        if (exportMode == m) return; exportMode = m;
        wakeWirelessIo();
        saveChanges(); markForUpdate();
    }

    public ImportMode getImportMode() { return importMode; }
    public void setImportMode(ImportMode m) {
        if (importMode == m) return;
        var old = importMode; importMode = m;
        if ((old == ImportMode.EJECT) != (m == ImportMode.EJECT)) refreshEjectRegistrations();
        wakeWirelessIo();
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

    public void invalidateInductionCardCache() {
        inductionCardCacheDirty = true;
        wakeWirelessIo();
    }

    // ── Wireless connections ─────────────────────────────────────────────

    public List<WirelessConnection> getConnections() { return Collections.unmodifiableList(connections); }

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
        return connectionStates.computeIfAbsent(conn, k -> new ConnectionState());
    }

    private void invalidateConnectionCache() {
        connectionsDirty = true;
        validConnectionsCache = List.of(); validConnectionsCacheTick = -1;
        wheelDirty = true;
        for (var sl : energyWheel) sl.clear();
        for (var sl : ioWheel) sl.clear();
        lastScheduledValid = List.of();
        deferredMachines.clear(); connectionStates.clear();
        wakeWirelessIo();
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
        if (!be.hasServerTickWork()) return;

        // 能量层:每 tick 触发(内部 wheel + scheduleDelay 已经是自适应的)
        be.tickEnergyTransfer(sl);

        // Wireless IO: timing-wheel driven per-connection scheduling
        if (be.interfaceMode != InterfaceMode.WIRELESS) return;
        be.tickWirelessIO(sl);
    }

    /**
     * 唤醒所有 Wireless IO 连接的 cooldown:配置/拓扑/速率档任何变动都应调用此方法。
     * 让每个 ConnectionState.cd 把搜索区间重置,下次扫描立即重新二分收敛到新目标。
     */
    private void wakeWirelessIo() {
        for (var st : connectionStates.values()) {
            st.importCDs.values().forEach(CooldownTracker::reset);
            st.exportCDs.values().forEach(CooldownTracker::reset);
            st.importProbeStates.values().forEach(ProbeState::reset);
            st.keyModels.values().forEach(KeyModel::resetCycle);
        }
        keyTypeLockUntil.clear();
        lastScheduledValid = List.of();
    }

    private boolean hasServerTickWork() {
        if (interfaceMode == InterfaceMode.WIRELESS) {
            if (importMode == ImportMode.AUTO || exportMode == ExportMode.AUTO
                    || !importBuffer.isEmpty()) {
                return true;
            }
        }
        if (interfaceMode == InterfaceMode.WIRELESS || energyOutputDir != null) {
            return AppFluxHelper.FE_KEY != null && hasInductionCard();
        }
        return false;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Wireless I/O — timing-wheel driven, triple entry per (conn, keyType, dir)
    // ══════════════════════════════════════════════════════════════════════

    private void scheduleEntryAt(IoScheduledEntry entry, long tick) {
        int bucket = (int)(tick & (IO_WHEEL_SIZE - 1));
        ioWheel[bucket].add(entry);
    }

    private List<IoScheduledEntry> pollIOWheel(long tick) {
        int bucket = (int)(tick & (IO_WHEEL_SIZE - 1));
        var list = ioWheel[bucket];
        ioWheel[bucket] = ioSpareList;
        ioSpareList = list;
        return list;
    }

    private boolean isEntryStillValid(IoScheduledEntry entry) {
        var cd = entry.cst.cdFor(entry.keyType, entry.direction);
        return entry.generation == cd.scheduleGeneration;
    }

    private void refreshIOWheel(ServerLevel sl, long now) {
        var valid = getOrRefreshValidConnections(sl, now);
        if (valid == lastScheduledValid) return;
        boolean activeImport = importMode == ImportMode.AUTO;
        boolean activeExport = exportMode == ExportMode.AUTO;
        for (var conn : valid) {
            if (lastScheduledValid.contains(conn)) continue;
            var cst = getOrCreateState(conn);
            var targetLevel = resolveTargetLevel(sl, conn);
            if (targetLevel == null) continue;
            var wrappers = cst.resolveWrappers(targetLevel, conn);
            if (wrappers == null) continue;
            for (var keyType : wrappers.keySet()) {
                if (isEnergyKeyType(keyType)) continue;
                if (activeImport) {
                    var cd = cst.cdFor(keyType, IoDirection.IMPORT);
                    var entry = new IoScheduledEntry(conn, cst, keyType,
                            IoDirection.IMPORT, cd.scheduleGeneration);
                    scheduleEntryAt(entry, Math.max(now + 1, cd.cooldownUntil()));
                }
                if (activeExport) {
                    var cd = cst.cdFor(keyType, IoDirection.EXPORT);
                    var entry = new IoScheduledEntry(conn, cst, keyType,
                            IoDirection.EXPORT, cd.scheduleGeneration);
                    scheduleEntryAt(entry, Math.max(now + 1, cd.cooldownUntil()));
                }
            }
        }
        lastScheduledValid = valid;
    }

    private long tickWirelessIO(ServerLevel sl) {
        if (interfaceMode != InterfaceMode.WIRELESS) return 0L;
        var grid = getMainNode().getGrid();
        if (grid == null) return 0L;

        long now = sl.getGameTime();
        var meStorage = grid.getStorageService().getInventory();
        var source    = IActionSource.ofMachine(this);

        flushImportBuffer(meStorage, source, now);

        if (importMode != ImportMode.AUTO && exportMode != ExportMode.AUTO
                && importBuffer.isEmpty()) return 0L;

        boolean fast = ioSpeedMode == IOSpeedMode.FAST;
        refreshIOWheel(sl, now);
        var due = pollIOWheel(now);
        if (DEBUG_IO && !due.isEmpty()) {
            IO_LOGGER.info("[{}] tick={} fast={} due={} import={} export={} buf={}",
                    getBlockPos(), now, fast, due.size(), importMode, exportMode, importBuffer.size());
        }
        if (due.isEmpty()) return 0L;

        long totalMoved = 0L;

        for (var entry : due) {
            if (!isEntryStillValid(entry)) {
                if (DEBUG_IO) IO_LOGGER.info("[{}]   stale entry gen={}", getBlockPos(), entry.generation);
                continue;
            }

            var targetLevel = resolveTargetLevel(sl, entry.conn);
            if (targetLevel == null) {
                scheduleEntryAt(entry, now + WRAPPER_REFRESH_TICKS);
                continue;
            }

            var wrappers = entry.cst.resolveWrappers(targetLevel, entry.conn);
            if (wrappers == null) {
                rescheduleEntry(entry, now, fast);
                continue;
            }
            var wrapper = wrappers.get(entry.keyType);
            if (wrapper == null) {
                rescheduleEntry(entry, now, fast);
                continue;
            }

            if (entry.direction == IoDirection.IMPORT) {
                if (importMode != ImportMode.AUTO) {
                    rescheduleEntry(entry, now, fast);
                    continue;
                }
                if (isKeyTypeLocked(entry.keyType, now)) {
                    if (DEBUG_IO) IO_LOGGER.info("[{}]   locked keyType={}", getBlockPos(), entry.keyType);
                    rescheduleEntry(entry, now, fast);
                    continue;
                }
                switch (entry.phase) {
                    case PROBE -> {
                        runProbe(entry.cst, entry.keyType, wrapper, source, now);
                        if (DEBUG_IO) {
                            var m = entry.cst.modelFor(entry.keyType);
                            IO_LOGGER.info("[{}]   PROBE {}/{} avail={} effMax={} rateEMA={}",
                                    getBlockPos(), entry.keyType, entry.conn.pos(),
                                    m.lastAvail, m.effectiveMax, m.rateEMA);
                        }
                    }
                    case EXTRACT -> {
                        long moved = runExtract(
                                entry.cst, entry.keyType, wrapper, source, now, fast);
                        totalMoved += moved;
                        if (DEBUG_IO) {
                            var cd = entry.cst.cdFor(entry.keyType, IoDirection.IMPORT);
                            IO_LOGGER.info("[{}]   EXTRACT {}/{} moved={} cd={} until={}",
                                    getBlockPos(), entry.keyType, entry.conn.pos(),
                                    moved, cd.cd, cd.cooldownUntil);
                        }
                    }
                }
            } else {
                if (exportMode != ExportMode.AUTO) {
                    rescheduleEntry(entry, now, fast);
                    continue;
                }
                long moved = runExport(entry.cst, entry.keyType, wrapper,
                        meStorage, source, now);
                totalMoved += moved;
                if (DEBUG_IO && moved > 0) {
                    IO_LOGGER.info("[{}]   EXPORT {}/{} moved={}",
                            getBlockPos(), entry.keyType, entry.conn.pos(), moved);
                }
            }

            rescheduleEntry(entry, now, fast);
        }
        due.clear();
        return totalMoved;
    }

    // ── Import buffer ─────────────────────────────────────────────────

    private void flushImportBuffer(MEStorage me, IActionSource src, long now) {
        if (importBuffer.isEmpty()) return;
        if (now - importBufferLastFlushTick < IMPORT_FLUSH_INTERVAL) return;
        importBufferLastFlushTick = now;

        Map<AEKeyType, Boolean> typeProgressed = new HashMap<>();
        Map<AEKeyType, Boolean> typeFullyRejected = new HashMap<>();

        var it = importBuffer.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            var key = e.getKey();
            var type = key.getType();
            long inserted = me.insert(key, e.getValue(), Actionable.MODULATE, src);
            if (inserted >= e.getValue()) {
                it.remove();
                typeProgressed.put(type, true);
            } else if (inserted > 0) {
                e.setValue(e.getValue() - inserted);
                typeProgressed.put(type, true);
            } else {
                typeFullyRejected.putIfAbsent(type, true);
            }
        }

        for (var type : typeProgressed.keySet()) {
            keyTypeLockUntil.remove(type);
        }
        for (var type : typeFullyRejected.keySet()) {
            if (!typeProgressed.getOrDefault(type, false)) {
                keyTypeLockUntil.put(type, now + STOP_IMPORT_TTL);
            }
        }
        setChanged();
    }

    private boolean isKeyTypeLocked(AEKeyType type, long now) {
        long until = keyTypeLockUntil.getOrDefault(type, 0L);
        if (until <= 0) return false;
        if (now >= until) {
            keyTypeLockUntil.remove(type);
            return false;
        }
        return true;
    }

    private long importExtractToBuffer(AEKey key, long amount,
                                       MEStorage wrapper, IActionSource src) {
        long extracted = wrapper.extract(key, amount, Actionable.MODULATE, src);
        if (extracted > 0) {
            importBuffer.merge(key, extracted, Long::sum);
        }
        return extracted;
    }

    private void exportOverflowToBuffer(AEKey key, long amount) {
        if (amount <= 0) return;
        importBuffer.merge(key, amount, Long::sum);
        setChanged();
    }

    // ── Energy keytype guard ──────────────────────────────────────────

    @Nullable
    private static final AEKeyType FE_KEY_TYPE = resolveFEKeyType();

    @Nullable
    private static AEKeyType resolveFEKeyType() {
        try {
            var feKey = AppFluxHelper.FE_KEY;
            return feKey != null ? feKey.getType() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean isEnergyKeyType(AEKeyType type) {
        return FE_KEY_TYPE != null && FE_KEY_TYPE == type;
    }

    // ── Import filter + export blacklist helpers ────────────────────────

    private Set<AEKey> exportBlacklistCache = Set.of();
    private long exportBlacklistTick = -1;

    private Set<AEKey> getExportBlacklist() {
        if (level != null && level.getGameTime() == exportBlacklistTick)
            return exportBlacklistCache;
        var config = getInterfaceLogic().getConfig();
        var set = new HashSet<AEKey>();
        for (int i = 0; i < config.size(); i++) {
            var ck = config.getKey(i);
            if (ck != null) set.add(ck);
        }
        exportBlacklistCache = set;
        if (level != null) exportBlacklistTick = level.getGameTime();
        return set;
    }

    @Nullable
    private Set<AEKey> getExactImportFilterKeys() {
        return importFilterFuzzyMode == null ? importFilterKeys : null;
    }

    private boolean isImportAllowed(AEKey key) {
        if (getExportBlacklist().contains(key)) return false;
        var keys = importFilterKeys;
        if (keys == null || keys.isEmpty()) return true;
        var fuzzyMode = importFilterFuzzyMode;
        if (fuzzyMode == null) return keys.contains(key);
        for (var filterKey : keys) {
            if (key.equals(filterKey) || key.fuzzyEquals(filterKey, fuzzyMode)) return true;
        }
        return false;
    }

    // ── runProbe / runExtract / runExport ──────────────────────────────

    private void runProbe(ConnectionState cst, AEKeyType type, MEStorage wrapper,
                          IActionSource src, long now) {
        long totalAvail;
        var exactFilterKeys = getExactImportFilterKeys();

        if (exactFilterKeys != null && !exactFilterKeys.isEmpty()) {
            totalAvail = 0;
            for (var key : exactFilterKeys) {
                if (key.getType() != type) continue;
                totalAvail += wrapper.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, src);
            }
        } else {
            scanBuffer.reset();
            wrapper.getAvailableStacks(scanBuffer);
            totalAvail = 0;
            for (var e : scanBuffer) {
                var key = e.getKey();
                if (key.getType() != type) continue;
                if (!isImportAllowed(key)) continue;
                totalAvail += e.getLongValue();
            }
        }

        var model = cst.modelFor(type);
        onProbe(model, totalAvail, now);
    }

    private long runExtract(ConnectionState cst, AEKeyType type, MEStorage wrapper,
                            IActionSource src, long now, boolean fast) {
        long totalAvail = 0;
        long totalExtracted = 0;
        var exactFilterKeys = getExactImportFilterKeys();
        boolean dirty = false;

        if (exactFilterKeys != null && !exactFilterKeys.isEmpty()) {
            for (var key : exactFilterKeys) {
                if (key.getType() != type) continue;
                long avail = wrapper.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, src);
                if (avail <= 0) continue;
                totalAvail += avail;
                long extracted = wrapper.extract(key, avail, Actionable.MODULATE, src);
                if (extracted > 0) {
                    importBuffer.merge(key, extracted, Long::sum);
                    totalExtracted += extracted;
                    dirty = true;
                }
            }
        } else {
            scanBuffer.reset();
            wrapper.getAvailableStacks(scanBuffer);
            for (var e : scanBuffer) {
                var key = e.getKey();
                long avail = e.getLongValue();
                if (avail <= 0 || key.getType() != type) continue;
                if (!isImportAllowed(key)) continue;
                totalAvail += avail;
                long extracted = importExtractToBuffer(key, avail, wrapper, src);
                totalExtracted += extracted;
                if (extracted > 0) dirty = true;
            }
        }

        if (dirty) setChanged();

        var model = cst.modelFor(type);
        var cd = cst.cdFor(type, IoDirection.IMPORT);

        onExtract(model, totalAvail, totalExtracted, now);

        if (totalExtracted > 0) cd.onSuccess(now, fast, model);
        else                    cd.onFail(now, fast);

        return totalExtracted;
    }

    private long runExport(ConnectionState cst, AEKeyType type, MEStorage wrapper,
                           MEStorage me, IActionSource src, long now) {
        var config = getInterfaceLogic().getConfig();
        int configSize = config.size();
        boolean fast = ioSpeedMode == IOSpeedMode.FAST;

        long moved = 0;
        boolean anyMoved = false;

        for (int slot = 0; slot < configSize; slot++) {
            var key = config.getKey(slot);
            if (key == null || key.getType() != type) continue;

            long maxAmount = key.getAmountPerUnit() * 64L;

            long canAccept = wrapper.insert(key, maxAmount, Actionable.SIMULATE, src);
            if (canAccept <= 0) continue;

            long extracted = me.extract(key, canAccept, Actionable.MODULATE, src);
            if (extracted <= 0) continue;

            long inserted = wrapper.insert(key, extracted, Actionable.MODULATE, src);
            moved += inserted;
            if (inserted > 0) anyMoved = true;

            long overflow = extracted - inserted;
            if (overflow > 0) exportOverflowToBuffer(key, overflow);
        }

        var cd = cst.cdFor(type, IoDirection.EXPORT);
        var model = cst.modelFor(type);
        if (anyMoved) cd.onSuccess(now, fast, model);
        else          cd.onFail(now, fast);
        return moved;
    }

    // ── Scheduling: reschedule + PROBE logic ──────────────────────────

    private void rescheduleEntry(IoScheduledEntry entry, long now, boolean fast) {
        var cd = entry.cst.cdFor(entry.keyType, entry.direction);

        if (entry.direction == IoDirection.EXPORT) {
            entry.phase = IoPhase.EXTRACT;
            scheduleEntryAt(entry, Math.max(now + 1, cd.cooldownUntil()));
            return;
        }

        var probe = entry.cst.probeStateFor(entry.keyType);
        long cdUntil = cd.cooldownUntil();

        if (entry.phase == IoPhase.PROBE) {
            var model = entry.cst.modelFor(entry.keyType);
            boolean probeHit = checkProbeSuccess(model);
            entry.phase = IoPhase.EXTRACT;
            if (probeHit) {
                probe.levelIdx = 0;
                scheduleEntryAt(entry, now + 1);
            } else {
                probe.levelIdx = Math.min(probe.levelIdx + 1, PROBE_LEVELS.length - 1);
                scheduleEntryAt(entry, Math.max(now + 1, cdUntil));
            }
            return;
        }

        long probeAt = computeProbeInsertTick(probe, cdUntil, now);
        if (probeAt > 0 && probeAt < cdUntil
                && cdUntil - now >= probeEnableThreshold(entry.keyType)) {
            entry.phase = IoPhase.PROBE;
            scheduleEntryAt(entry, probeAt);
        } else {
            entry.phase = IoPhase.EXTRACT;
            scheduleEntryAt(entry, Math.max(now + 1, cdUntil));
        }
    }

    private boolean checkProbeSuccess(KeyModel m) {
        return m.effectiveMax > 0 && m.lastAvail >= m.effectiveMax * 0.85;
    }

    private long computeProbeInsertTick(ProbeState probe, long cdUntil, long now) {
        float level = PROBE_LEVELS[probe.levelIdx];
        if (level >= 1.0f) {
            return cdUntil - (long) level;
        } else {
            int interval = Math.round(1.0f / level);
            probe.skipCounter++;
            if (probe.skipCounter >= interval) {
                probe.skipCounter = 0;
                return cdUntil - 1;
            }
            return -1;
        }
    }

    private int probeEnableThreshold(AEKeyType type) {
        return type == AEKeyType.items() ? 10 : 5;
    }

    // ── KeyModel update helpers ───────────────────────────────────────

    private void onExtract(KeyModel m, long totalAvail, long totalExtracted, long now) {
        if (totalAvail > m.maxObserved) {
            m.maxObserved = totalAvail;
            if (m.effectiveMax < totalAvail) m.effectiveMax = totalAvail;
        }

        if (m.midProbeTick > 0 && m.postExtractTick > 0) {
            double dt2 = now - m.midProbeTick;
            if (dt2 > 0) {
                double half2Rate = (totalAvail - m.midProbeAvail) / dt2;
                if (m.half1Rate > 0) {
                    double ratio = half2Rate / m.half1Rate;
                    if (ratio < 0.7) {
                        m.effectiveMax = Math.max(m.maxObserved / 4,
                                (long)(m.effectiveMax * 0.9));
                    } else if (ratio > 1.1) {
                        m.effectiveMax = Math.min(m.maxObserved,
                                (long)(m.effectiveMax * 1.05));
                    }
                }
            }
        }

        long postExtract = totalAvail - totalExtracted;
        m.postExtractAvail = postExtract;
        m.postExtractTick = now;
        m.lastAvail = postExtract;
        m.lastTick = now;
        m.midProbeTick = -1;
    }

    private void onProbe(KeyModel m, long avail, long now) {
        if (m.postExtractTick > 0) {
            double dt1 = now - m.postExtractTick;
            if (dt1 > 0) {
                m.half1Rate = (avail - m.postExtractAvail) / dt1;
            }
        }
        m.midProbeAvail = avail;
        m.midProbeTick = now;
        updateRateEMA(m, avail, now);
    }

    private void updateRateEMA(KeyModel m, long totalAvail, long now) {
        if (m.lastTick > 0 && now > m.lastTick) {
            long dt = now - m.lastTick;
            long da = totalAvail - m.lastAvail;
            if (da >= 0) {
                double instant = (double) da / dt;
                m.rateEMA = 0.2 * instant + 0.8 * m.rateEMA;
            }
        }
        if (totalAvail > m.maxObserved) {
            m.maxObserved = totalAvail;
            if (m.effectiveMax < totalAvail) m.effectiveMax = totalAvail;
        }
        m.lastAvail = totalAvail;
        m.lastTick = now;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Energy transfer
    // ══════════════════════════════════════════════════════════════════════

    private boolean hasInductionCard() {
        if (!AppFluxHelper.isAvailable()) return false;
        if (!inductionCardCacheDirty) return inductionCardInstalledCache;
        var u = getInterfaceLogic().getUpgrades();
        boolean installed = false;
        for (int i = 0; i < u.size(); i++) {
            if (AppFluxHelper.isInductionCard(u.getStackInSlot(i).getItem())) {
                installed = true;
                break;
            }
        }
        inductionCardInstalledCache = installed;
        inductionCardCacheDirty = false;
        return installed;
    }

    private static final boolean DEBUG_ENERGY = "1".equals(System.getProperty("ae2lt.debug.energy"));
    private static final boolean DEBUG_IO = "1".equals(System.getProperty("ae2lt.debug.io"));
    private static final org.slf4j.Logger ENERGY_LOGGER = org.slf4j.LoggerFactory.getLogger("ae2lt/iface-energy");
    private static final org.slf4j.Logger IO_LOGGER = org.slf4j.LoggerFactory.getLogger("ae2lt/iface-io");
    private final java.util.Map<String, Long> lastEnergyDebugByTag = new java.util.HashMap<>();

    private void tickEnergyTransfer(ServerLevel sl) {
        if (!hasInductionCard()) {
            if (DEBUG_ENERGY) debugEnergy(sl, "no-induction-card");
            return;
        }
        var feKey = AppFluxHelper.FE_KEY;
        if (feKey == null) {
            if (DEBUG_ENERGY) debugEnergy(sl, "FE_KEY=null(AppFlux-not-available)");
            return;
        }
        var grid = getMainNode().getGrid();
        if (grid == null) {
            if (DEBUG_ENERGY) debugEnergy(sl, "no-grid");
            return;
        }
        if (interfaceMode == InterfaceMode.WIRELESS) {
            if (DEBUG_ENERGY) debugEnergy(sl, "wireless connections=" + connections.size());
            tickWirelessEnergy(sl, feKey);
        } else if (energyOutputDir != null) {
            if (DEBUG_ENERGY) debugEnergy(sl, "normal dir=" + energyOutputDir);
            tickNormalEnergy(sl);
        } else {
            if (DEBUG_ENERGY) debugEnergy(sl, "normal NO-DIR-SET");
        }
    }

    private void debugEnergy(ServerLevel sl, String tag) {
        long gt = sl.getGameTime();
        int sp = tag.indexOf(' ');
        String bucket = sp > 0 ? tag.substring(0, sp) : tag;
        Long last = lastEnergyDebugByTag.get(bucket);
        if (last != null && gt - last < 20) return;
        lastEnergyDebugByTag.put(bucket, gt);
        ENERGY_LOGGER.info("[{}] mode={} {}", getBlockPos(), interfaceMode, tag);
    }

    private void tickNormalEnergy(ServerLevel sl) {
        var tp = getBlockPos().relative(energyOutputDir);
        var st = sl.getCapability(Capabilities.EnergyStorage.BLOCK,
                tp, energyOutputDir.getOpposite());
        if (st == null) {
            if (DEBUG_ENERGY) debugEnergy(sl, "normal target=" + tp + " NO-FE-CAP");
            return;
        }
        var mes = getMainNode().getGrid().getStorageService().getInventory();
        var src = IActionSource.ofMachine(this);
        int moved = AppFluxHelper.pullPowerFromNetwork(mes, st, src);
        if (DEBUG_ENERGY && moved > 0) debugEnergy(sl, "normal sent=" + moved + " FE to " + tp);
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
        if (valid.isEmpty()) {
            if (DEBUG_ENERGY) debugEnergy(sl, "distribute-empty valid=0");
            return;
        }
        wheelPointer = (wheelPointer + 1) % WHEEL_SLOTS;
        if (wheelDirty) rebuildEWheel(valid);

        if (!deferredMachines.isEmpty()) {
            long avail = simulateFluxExtract(feKey,
                    saturatingMul(AppFluxHelper.TRANSFER_RATE, deferredMachines.size()));
            if (DEBUG_ENERGY) debugEnergy(sl, "distribute-defer n=" + deferredMachines.size() + " avail=" + avail);
            if (avail <= 0) return;
            processEBatch(sl, deferredMachines, feKey, avail);
            if (!deferredMachines.isEmpty()) return;
        }
        for (int s = 1; s < elapsed && energyWheel[wheelPointer].isEmpty(); s++)
            wheelPointer = (wheelPointer + 1) % WHEEL_SLOTS;

        var eligible = pollWheel();
        if (DEBUG_ENERGY) debugEnergy(sl, "distribute-wheel ptr=" + wheelPointer + " eligible=" + eligible.size() + " valid=" + valid.size());
        if (eligible.isEmpty()) return;
        long avail = simulateFluxExtract(feKey, saturatingMul(AppFluxHelper.TRANSFER_RATE, eligible.size()));
        if (avail <= 0) {
            if (DEBUG_ENERGY) debugEnergy(sl, "distribute-me-empty simulate-avail=0");
            deferredMachines.addAll(eligible);
            eligible.clear();
            return;
        }
        processEBatch(sl, eligible, feKey, avail);
    }

    /**
     * Saturating multiply: a × b,但溢出时 clamp 到 Long.MAX_VALUE。
     *
     * 用途:AppFlux 的 TRANSFER_RATE 在 config=0(unlimited)时被哨兵值 Long.MAX_VALUE 代表,
     * 直接乘 count 会溢出成负数,让下游 extract(FE_KEY, -N) 返回 0,能量永远传不出去。
     */
    private static long saturatingMul(long a, long b) {
        if (a <= 0 || b <= 0) return 0;
        if (a > Long.MAX_VALUE / b) return Long.MAX_VALUE;
        return a * b;
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
        IEnergyStorage[] storages = new IEnergyStorage[cnt];

        int nullStorages = 0;
        for (int i = 0; i < cnt; i++) {
            var entry = batch.get(i);
            var targetLevel = resolveTargetLevel(sl, entry.conn);
            var storage = targetLevel != null ? entry.state.resolveEnergy(targetLevel, entry.conn) : null;
            storages[i] = storage;
            if (storage != null) {
                canReceive[i]   = AppFluxHelper.simulateReceivable(storage);
                maxCapacity[i]  = storage.getMaxEnergyStored();
                storedEnergy[i] = storage.getEnergyStored();
                total += canReceive[i];
            } else {
                nullStorages++;
            }
        }

        if (DEBUG_ENERGY) {
            debugEnergy(sl, "processEBatch cnt=" + cnt + " nullStorages=" + nullStorages
                    + " totalCanReceive=" + total + " avail=" + avail);
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
        if (DEBUG_ENERGY) debugEnergy(sl, "extractFlux asked=" + Math.min(total, avail) + " got=" + extracted);
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
                var storage = storages[i];
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

    private final KeyCounter debugMeStacksBuf = new KeyCounter();

    private long simulateFluxExtract(AEKey key, long max) {
        var grid = getMainNode().getGrid();
        if (grid == null) return 0;
        var inv = grid.getStorageService().getInventory();
        long got = inv.extract(key, max, Actionable.SIMULATE, IActionSource.ofMachine(this));
        if (DEBUG_ENERGY && got <= 0 && level instanceof ServerLevel sl) {
            // 当 simulate 返回 0 时,扫一遍 ME 里所有 FluxKey 的真实库存,判断是"ME 没 FE"还是"key 错"
            debugMeStacksBuf.reset();
            inv.getAvailableStacks(debugMeStacksBuf);
            long feSum = 0;
            int  fluxKeys = 0;
            int  totalKeys = 0;
            for (var e : debugMeStacksBuf) {
                totalKeys++;
                var k = e.getKey();
                if (k != null && k.getClass().getName().contains("FluxKey")) {
                    fluxKeys++;
                    feSum += e.getLongValue();
                }
            }
            debugEnergy(sl, "simulate-zero asked=" + max
                    + " feStoredInMe=" + feSum
                    + " fluxKeys=" + fluxKeys + "/" + totalKeys
                    + " feKeyClass=" + (key != null ? key.getClass().getSimpleName() : "null"));
        }
        return got;
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
        if (level==null) return;
        var removed = EjectModeRegistry.unregisterAll(this, true);
        if (level instanceof ServerLevel sl) {
            var srv = sl.getServer();
            for (var dp : removed) {
                var t = srv.getLevel(dp.dimension());
                if (t!=null) t.invalidateCapabilities(dp.pos());
            }
        }
    }

    @Override
    public void setRemoved() {
        if (!unloadingChunk) {
            unregisterEject();
        }
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        unloadingChunk = true;
        super.onChunkUnloaded();
    }

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

        int interfaceOrd = data.readByte();
        var newInterfaceMode = interfaceOrd >= 0 && interfaceOrd < InterfaceMode.values().length
                ? InterfaceMode.values()[interfaceOrd] : InterfaceMode.NORMAL;

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
        filterInv.writeToNBT(d, TAG_FILTER_INV, r);
        if (!importBuffer.isEmpty()) {
            var bufList = new ListTag();
            for (var e : importBuffer.entrySet()) {
                var gs = new GenericStack(e.getKey(), e.getValue());
                bufList.add(GenericStack.writeTag(r, gs));
            }
            d.put(TAG_IMPORT_BUFFER, bufList);
        }
        d.putLong(TAG_IMPORT_FLUSH_TICK, importBufferLastFlushTick);
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
        filterInv.readFromNBT(d, TAG_FILTER_INV, r);
        importBuffer.clear();
        if (d.contains(TAG_IMPORT_BUFFER, Tag.TAG_LIST)) {
            var bufList = d.getList(TAG_IMPORT_BUFFER, Tag.TAG_COMPOUND);
            for (int i = 0; i < bufList.size(); i++) {
                var gs = GenericStack.readTag(r, bufList.getCompound(i));
                if (gs != null) importBuffer.merge(gs.what(), gs.amount(), Long::sum);
            }
        }
        importBufferLastFlushTick = d.getLong(TAG_IMPORT_FLUSH_TICK);
        invalidateConnectionCache();
        refreshEjectRegistrations();
    }

    // ── Memory card copy/paste (machine-specific fields only) ───────────────
    // AE2's generic export only walks IUpgradeable / IConfigurableObject /
    // IPriorityHost / IConfigInvHost — none of our custom mode enums, the
    // energy output direction, or the unlimited-slot bitset live there.

    @Override
    public void exportSettings(appeng.util.SettingsFrom mode,
                               net.minecraft.core.component.DataComponentMap.Builder builder,
                               @Nullable Player player) {
        super.exportSettings(mode, builder, player);
        if (mode != appeng.util.SettingsFrom.MEMORY_CARD) {
            return;
        }
        var tag = new CompoundTag();
        com.moakiee.ae2lt.logic.MemoryCardConfigSupport.writeEnum(tag, TAG_INTERFACE_MODE, interfaceMode);
        com.moakiee.ae2lt.logic.MemoryCardConfigSupport.writeEnum(tag, TAG_IO_SPEED_MODE, ioSpeedMode);
        com.moakiee.ae2lt.logic.MemoryCardConfigSupport.writeEnum(tag, TAG_EXPORT_MODE, exportMode);
        com.moakiee.ae2lt.logic.MemoryCardConfigSupport.writeEnum(tag, TAG_IMPORT_MODE, importMode);
        com.moakiee.ae2lt.logic.MemoryCardConfigSupport.writeDirection(tag, TAG_ENERGY_DIR, energyOutputDir);
        long bits = 0;
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (unlimitedSlots[i]) bits |= (1L << i);
        }
        tag.putLong(TAG_UNLIMITED_SLOTS, bits);
        com.moakiee.ae2lt.logic.MemoryCardConfigSupport.writeCustomTag(builder, tag);
    }

    @Override
    public void importSettings(appeng.util.SettingsFrom mode,
                               net.minecraft.core.component.DataComponentMap input,
                               @Nullable Player player) {
        super.importSettings(mode, input, player);
        if (mode != appeng.util.SettingsFrom.MEMORY_CARD) {
            return;
        }
        var tag = com.moakiee.ae2lt.logic.MemoryCardConfigSupport.readCustomTag(input);
        if (tag == null) {
            return;
        }
        this.interfaceMode = com.moakiee.ae2lt.logic.MemoryCardConfigSupport.readEnum(
                tag, TAG_INTERFACE_MODE, InterfaceMode.class, this.interfaceMode);
        this.ioSpeedMode = com.moakiee.ae2lt.logic.MemoryCardConfigSupport.readEnum(
                tag, TAG_IO_SPEED_MODE, IOSpeedMode.class, this.ioSpeedMode);
        this.exportMode = com.moakiee.ae2lt.logic.MemoryCardConfigSupport.readEnum(
                tag, TAG_EXPORT_MODE, ExportMode.class, this.exportMode);
        var newImportMode = com.moakiee.ae2lt.logic.MemoryCardConfigSupport.readEnum(
                tag, TAG_IMPORT_MODE, ImportMode.class, this.importMode);
        if (newImportMode != this.importMode) {
            var old = this.importMode;
            this.importMode = newImportMode;
            if ((old == ImportMode.EJECT) != (newImportMode == ImportMode.EJECT)) {
                refreshEjectRegistrations();
            }
        }
        if (tag.contains(TAG_ENERGY_DIR)) {
            this.energyOutputDir = com.moakiee.ae2lt.logic.MemoryCardConfigSupport.readDirection(tag, TAG_ENERGY_DIR);
        }
        if (tag.contains(TAG_UNLIMITED_SLOTS)) {
            long bits = tag.getLong(TAG_UNLIMITED_SLOTS);
            for (int i = 0; i < SLOT_COUNT; i++) {
                unlimitedSlots[i] = (bits & (1L << i)) != 0;
            }
        }
        invalidateConnectionCache();
        saveChanges();
        markForUpdate();
    }

}
