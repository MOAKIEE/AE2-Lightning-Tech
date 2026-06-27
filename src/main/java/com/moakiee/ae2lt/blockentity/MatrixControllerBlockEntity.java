package com.moakiee.ae2lt.blockentity;

import java.util.ArrayList;
import java.util.List;

import com.moakiee.ae2lt.block.MatrixGlassBlock;
import com.moakiee.ae2lt.block.MatrixMultiblockDirectionalBlock;
import com.moakiee.ae2lt.block.MatrixPatternStorageBlock;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockComponent;
import com.moakiee.ae2lt.logic.craft.MatrixCraftingMath;
import com.moakiee.ae2lt.logic.craft.MatrixCraftingProfile;
import com.moakiee.ae2lt.logic.craft.MatrixCraftingUnit;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockMember;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockRole;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockScanAttempt;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockScanResult;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockScanner;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockTemplate;
import com.moakiee.ae2lt.network.MatrixControllerActionPacket;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class MatrixControllerBlockEntity extends BlockEntity {
    private static final BlockPos DEFAULT_PORT_LOCAL = new BlockPos(6, 5, 3);
    private static final long NO_SCHEDULED_SCAN = Long.MIN_VALUE;
    private static final String TAG_FORMED = "Formed";
    private static final String TAG_ORIENTATION = "Orientation";
    private static final String TAG_PORT_POS = "PortPos";
    private static final String TAG_MIN_POS = "MinPos";
    private static final String TAG_MAX_POS = "MaxPos";
    private static final String TAG_MEMBER_COUNT = "MemberCount";
    private static final String TAG_PATTERN_STORAGE_COUNT = "PatternStorageCount";
    private static final String TAG_CRAFTING_UNIT_COUNT = "CraftingUnitCount";

    private boolean formed;
    private Direction orientation = Direction.NORTH;
    private BlockPos portPos;
    private BlockPos minPos;
    private BlockPos maxPos;
    private int memberCount;
    private int patternStorageCount;
    private int craftingUnitCount;
    private List<BlockPos> patternStoragePositions = List.of();
    private List<MatrixCraftingUnit> cachedCraftingUnits = List.of();
    private boolean structureCacheValid;
    private long scheduledScanTick = NO_SCHEDULED_SCAN;

    public MatrixControllerBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.MATRIX_CONTROLLER.get(), pos, blockState);
        orientation = orientationFromState(blockState);
    }

    public static void serverTick(net.minecraft.world.level.Level level,
                                  BlockPos pos,
                                  BlockState state,
                                  MatrixControllerBlockEntity be) {
        if (level.isClientSide) {
            return;
        }
        if (be.formed && !be.structureCacheValid) {
            be.scheduleStructureCheck();
        }
        if (be.scheduledScanTick != NO_SCHEDULED_SCAN && level.getGameTime() >= be.scheduledScanTick) {
            be.scheduledScanTick = NO_SCHEDULED_SCAN;
            be.refreshStructure();
        }
    }

    public boolean isFormed() {
        return formed;
    }

    public Direction getOrientation() {
        return orientationFromState(getBlockState());
    }

    public BlockPos getPortPos() {
        return portPos;
    }

    public int getMemberCount() {
        return memberCount;
    }

    public int getPatternStorageCount() {
        return patternStorageCount;
    }

    public int getCraftingUnitCount() {
        return craftingUnitCount;
    }

    public int getPatternSlotCount() {
        int total = 0;
        for (var storage : findPatternStorages()) {
            total += storage.capacity();
        }
        return total;
    }

    public MatrixCraftingProfile getCraftingProfile() {
        var port = getPort();
        return port != null ? port.getCraftingProfile() : MatrixCraftingProfile.empty();
    }

    public MatrixCraftingMath.Snapshot getLimiterSnapshot() {
        var port = getPort();
        return port != null
                ? port.getLimiterSnapshot()
                : MatrixCraftingMath.idleSnapshot(0.0D, 0.0D);
    }

    public void performAction(MatrixControllerActionPacket.Action action, ServerPlayer player) {
        if (level == null || level.isClientSide) {
            return;
        }
        switch (action) {
            case AUTO_BUILD -> autoBuild(player);
            case UPGRADE_PATTERN_STORAGE -> upgradePatternStorage(player);
        }
    }

    public void scheduleStructureCheck() {
        if (level == null || level.isClientSide) {
            return;
        }
        long targetTick = level.getGameTime() + 1L;
        if (scheduledScanTick == NO_SCHEDULED_SCAN || targetTick < scheduledScanTick) {
            scheduledScanTick = targetTick;
        }
        setChanged();
    }

    public void clearStructureBindings() {
        clearBindingsInStoredBounds();
    }

    public void scanAndForm(ServerPlayer player) {
        var attempt = scanCurrent();
        if (!attempt.formed()) {
            deform();
            player.displayClientMessage(Component.translatable(
                    "ae2lt.matrix.scan_failed",
                    describeIssues(attempt)).withStyle(ChatFormatting.RED), true);
            return;
        }

        form(attempt.result());
        player.displayClientMessage(Component.translatable(
                "ae2lt.matrix.formed",
                memberCount,
                patternStorageCount,
                craftingUnitCount).withStyle(ChatFormatting.GREEN), true);
    }

    public void autoBuild(ServerPlayer player) {
        if (level == null || level.isClientSide) {
            return;
        }

        java.util.Map<Item, Integer> requirements = java.util.Map.of();
        if (!player.getAbilities().instabuild) {
            var blocked = findBlockedAutoBuildPositions();
            if (!blocked.isEmpty()) {
                player.displayClientMessage(Component.translatable(
                        "ae2lt.matrix.build_blocked",
                        describePositions(blocked)).withStyle(ChatFormatting.RED), true);
                return;
            }
            requirements = autoBuildRequirementsForMissingBlocks();
            int missingPatternStorages = missingPatternStorageRequirement();
            var missing = findMissingRequirements(player, requirements);
            int missingPatternStorageItems = Math.max(0, missingPatternStorages - countPatternStorageItems(player));
            if (!missing.isEmpty() || missingPatternStorageItems > 0) {
                player.displayClientMessage(Component.translatable(
                        "ae2lt.matrix.build_missing",
                        describeMissing(missing, missingPatternStorageItems)).withStyle(ChatFormatting.RED), true);
                return;
            }
        }

        Direction facing = getOrientation();
        for (var entry : MatrixMultiblockTemplate.entries()) {
            if (!shouldAutoBuild(entry.role())) {
                continue;
            }
            var local = entry.localPos();
            var pos = MatrixMultiblockScanner.worldPos(worldPosition, local, facing);
            if (acceptsExistingForAutoBuild(entry.role(), local, pos)) {
                continue;
            }
            Item consumedPatternStorage = null;
            BlockState state;
            if (entry.role() == MatrixMultiblockRole.PATTERN_BAY && !player.getAbilities().instabuild) {
                consumedPatternStorage = findPatternStorageItem(player);
                state = stateForPatternStorageItem(consumedPatternStorage);
            } else {
                state = stateForAutoBuild(entry.role(), local);
            }
            if (state == null) {
                continue;
            }
            if (state.isAir()) {
                continue;
            }
            if (pos.equals(worldPosition)) {
                level.setBlock(pos, getBlockState().setValue(MatrixMultiblockDirectionalBlock.FACING, facing),
                        Block.UPDATE_ALL);
            } else {
                level.setBlock(pos, state, Block.UPDATE_ALL);
            }
            if (consumedPatternStorage != null) {
                consumeItem(player, consumedPatternStorage, 1);
            }
        }
        if (!player.getAbilities().instabuild) {
            consumeRequirements(player, requirements);
        }

        scanAndForm(player);
    }

    public void upgradePatternStorage(ServerPlayer player) {
        var attempt = scanCurrent();
        if (!attempt.formed()) {
            player.displayClientMessage(Component.translatable(
                    "ae2lt.matrix.scan_failed",
                    describeIssues(attempt)).withStyle(ChatFormatting.RED), true);
            return;
        }

        var t1Storages = new ArrayList<MatrixPatternStorageBlockEntity>();
        for (var member : attempt.result().patternMembers()) {
            if (member.component() != MatrixMultiblockComponent.PATTERN_STORAGE_T1) {
                continue;
            }
            if (level.getBlockEntity(member.worldPos()) instanceof MatrixPatternStorageBlockEntity storage) {
                t1Storages.add(storage);
            }
        }

        if (t1Storages.isEmpty()) {
            player.displayClientMessage(Component.translatable("ae2lt.matrix.upgrade_none")
                    .withStyle(ChatFormatting.YELLOW), true);
            return;
        }

        int available = countUpgradeItems(player);
        if (available <= 0 && !player.getAbilities().instabuild) {
            player.displayClientMessage(Component.translatable("ae2lt.matrix.upgrade_missing")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        int toUpgrade = player.getAbilities().instabuild ? t1Storages.size() : Math.min(available, t1Storages.size());
        for (int i = 0; i < toUpgrade; i++) {
            upgradeStorageInPlace(t1Storages.get(i));
        }
        if (!player.getAbilities().instabuild) {
            consumeUpgradeItems(player, toUpgrade);
        }
        scanAndForm(player);
        player.displayClientMessage(Component.translatable(
                "ae2lt.matrix.upgraded",
                toUpgrade,
                t1Storages.size()).withStyle(ChatFormatting.GREEN), true);
    }

    public List<MatrixPatternStorageBlockEntity> findPatternStorages() {
        if (level == null || !formed) {
            return List.of();
        }
        ensureStructureCache();
        var storages = new ArrayList<MatrixPatternStorageBlockEntity>();
        for (var pos : patternStoragePositions) {
            if (level.getBlockEntity(pos) instanceof MatrixPatternStorageBlockEntity storage) {
                storages.add(storage);
            }
        }
        return List.copyOf(storages);
    }

    public List<MatrixCraftingUnit> findCraftingUnits() {
        if (level == null || !formed) {
            return List.of();
        }
        ensureStructureCache();
        return cachedCraftingUnits;
    }

    private MatrixMultiblockScanAttempt scanCurrent() {
        return MatrixMultiblockScanner.scan(worldPosition, getOrientation(),
                pos -> MatrixMultiblockScanner.componentAt(level, pos));
    }

    private MatrixPortBlockEntity getPort() {
        if (level == null || !formed || portPos == null) {
            return null;
        }
        return level.getBlockEntity(portPos) instanceof MatrixPortBlockEntity port ? port : null;
    }

    private void form(MatrixMultiblockScanResult result) {
        clearBindingsInStoredBounds();
        formed = true;
        orientation = result.orientation();
        portPos = result.portPos();
        minPos = result.minPos();
        maxPos = result.maxPos();
        memberCount = result.members().size();
        patternStorageCount = result.patternMembers().size();
        craftingUnitCount = result.craftingMembers().size();
        patternStoragePositions = result.patternMembers().stream()
                .map(member -> member.worldPos().immutable())
                .toList();
        cachedCraftingUnits = result.craftingUnits();
        structureCacheValid = true;

        bindMembers(result);
        setMembersFormed(result, true);
        setChangedAndUpdate();
    }

    private void deform() {
        clearBindingsInStoredBounds();
        setBoundsGlassFormed(false);
        formed = false;
        portPos = null;
        minPos = null;
        maxPos = null;
        memberCount = 0;
        patternStorageCount = 0;
        craftingUnitCount = 0;
        patternStoragePositions = List.of();
        cachedCraftingUnits = List.of();
        structureCacheValid = false;
        setChangedAndUpdate();
    }

    private void refreshStructure() {
        var attempt = scanCurrent();
        if (attempt.formed()) {
            form(attempt.result());
        } else if (formed) {
            deform();
        }
    }

    private void ensureStructureCache() {
        if (!formed || structureCacheValid || level == null || level.isClientSide) {
            return;
        }
        refreshStructure();
    }

    private void bindMembers(MatrixMultiblockScanResult result) {
        if (level.getBlockEntity(result.portPos()) instanceof MatrixPortBlockEntity port) {
            port.bindToController(worldPosition);
        }
        for (MatrixMultiblockMember member : result.patternMembers()) {
            if (level.getBlockEntity(member.worldPos()) instanceof MatrixPatternStorageBlockEntity storage) {
                storage.setControllerPos(worldPosition);
            }
        }
    }

    private void clearBindingsInStoredBounds() {
        if (level == null || minPos == null || maxPos == null) {
            return;
        }
        for (int x = minPos.getX(); x <= maxPos.getX(); x++) {
            for (int y = minPos.getY(); y <= maxPos.getY(); y++) {
                for (int z = minPos.getZ(); z <= maxPos.getZ(); z++) {
                    var pos = new BlockPos(x, y, z);
                    var be = level.getBlockEntity(pos);
                    if (be instanceof MatrixPortBlockEntity port && worldPosition.equals(port.getControllerPos())) {
                        port.bindToController(null);
                    } else if (be instanceof MatrixPatternStorageBlockEntity storage
                            && worldPosition.equals(storage.getControllerPos())) {
                        storage.setControllerPos(null);
                    }
                }
            }
        }
    }

    private void setMembersFormed(MatrixMultiblockScanResult result, boolean formedValue) {
        for (MatrixMultiblockMember member : result.members()) {
            setGlassFormed(member.worldPos(), formedValue);
        }
    }

    private void setBoundsGlassFormed(boolean formedValue) {
        if (level == null || minPos == null || maxPos == null) {
            return;
        }
        for (int x = minPos.getX(); x <= maxPos.getX(); x++) {
            for (int y = minPos.getY(); y <= maxPos.getY(); y++) {
                for (int z = minPos.getZ(); z <= maxPos.getZ(); z++) {
                    setGlassFormed(new BlockPos(x, y, z), formedValue);
                }
            }
        }
    }

    // Toggle FORMED on a matrix glass block so its client model switches between
    // the base and the assembled connected-texture appearance. Client-only update.
    private void setGlassFormed(BlockPos pos, boolean formedValue) {
        if (level == null) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof MatrixGlassBlock
                && state.getValue(MatrixGlassBlock.FORMED) != formedValue) {
            level.setBlock(pos, state.setValue(MatrixGlassBlock.FORMED, formedValue), Block.UPDATE_CLIENTS);
        }
    }

    private BlockState stateForAutoBuild(MatrixMultiblockRole role, BlockPos local) {
        return switch (role) {
            case EMPTY -> Blocks.AIR.defaultBlockState();
            case CASING -> ModBlocks.MATTER_WARPING_MATRIX_CASING.get().defaultBlockState();
            case CONSTRAINT_FRAME -> ModBlocks.MATTER_WARPING_MATRIX_CONSTRAINT_FRAME.get().defaultBlockState();
            case GLASS -> ModBlocks.MATTER_WARPING_MATRIX_GLASS.get().defaultBlockState();
            case CONTROLLER -> getBlockState().setValue(MatrixMultiblockDirectionalBlock.FACING, getOrientation());
            case PORT_CANDIDATE -> local.equals(DEFAULT_PORT_LOCAL)
                    ? ModBlocks.MATTER_WARPING_MATRIX_PORT.get().defaultBlockState()
                    : ModBlocks.MATTER_WARPING_MATRIX_CONSTRAINT_FRAME.get().defaultBlockState();
            case PATTERN_BAY -> ModBlocks.MATTER_WARPING_MATRIX_PATTERN_STORAGE_T1.get().defaultBlockState();
            case CRAFTING_BAY -> Blocks.AIR.defaultBlockState();
        };
    }

    private boolean shouldAutoBuild(MatrixMultiblockRole role) {
        return role != MatrixMultiblockRole.EMPTY
                && role != MatrixMultiblockRole.CONTROLLER
                && role != MatrixMultiblockRole.CRAFTING_BAY;
    }

    private java.util.Map<Item, Integer> autoBuildRequirementsForMissingBlocks() {
        var result = new java.util.LinkedHashMap<Item, Integer>();
        Direction facing = getOrientation();
        for (var entry : MatrixMultiblockTemplate.entries()) {
            if (!shouldAutoBuild(entry.role())) {
                continue;
            }
            if (entry.role() == MatrixMultiblockRole.PATTERN_BAY) {
                continue;
            }
            var state = stateForAutoBuild(entry.role(), entry.localPos());
            if (state == null || state.isAir()) {
                continue;
            }
            var pos = MatrixMultiblockScanner.worldPos(worldPosition, entry.localPos(), facing);
            if (acceptsExistingForAutoBuild(entry.role(), entry.localPos(), pos)) {
                continue;
            }
            if (level.getBlockState(pos).is(state.getBlock())) {
                continue;
            }
            Item item = state.getBlock().asItem();
            if (item != net.minecraft.world.item.Items.AIR) {
                result.merge(item, 1, Integer::sum);
            }
        }
        return result;
    }

    private List<BlockPos> findBlockedAutoBuildPositions() {
        var blocked = new ArrayList<BlockPos>();
        Direction facing = getOrientation();
        for (var entry : MatrixMultiblockTemplate.entries()) {
            if (!shouldAutoBuild(entry.role())) {
                continue;
            }
            var state = stateForAutoBuild(entry.role(), entry.localPos());
            if (state == null || state.isAir()) {
                continue;
            }
            var pos = MatrixMultiblockScanner.worldPos(worldPosition, entry.localPos(), facing);
            if (acceptsExistingForAutoBuild(entry.role(), entry.localPos(), pos)) {
                continue;
            }
            var existing = level.getBlockState(pos);
            if (!existing.isAir() && !existing.is(state.getBlock())) {
                blocked.add(pos.immutable());
            }
            if (blocked.size() >= 4) {
                break;
            }
        }
        return blocked;
    }

    private boolean acceptsExistingForAutoBuild(MatrixMultiblockRole role, BlockPos local, BlockPos worldPos) {
        var component = MatrixMultiblockScanner.componentAt(level, worldPos);
        return switch (role) {
            case EMPTY -> component == MatrixMultiblockComponent.AIR;
            case CASING -> component == MatrixMultiblockComponent.MATRIX_CASING;
            case CONSTRAINT_FRAME -> component == MatrixMultiblockComponent.MATRIX_CONSTRAINT_FRAME;
            case GLASS -> component == MatrixMultiblockComponent.MATRIX_GLASS;
            case CONTROLLER -> component == MatrixMultiblockComponent.MATRIX_CONTROLLER;
            case PORT_CANDIDATE -> component == MatrixMultiblockComponent.MATRIX_PORT
                    || component == MatrixMultiblockComponent.MATRIX_CONSTRAINT_FRAME;
            case PATTERN_BAY -> component.isPatternStorage();
            case CRAFTING_BAY -> local.equals(MatrixMultiblockTemplate.CRAFTING_CENTER_LOCAL)
                    ? component.isMainCore()
                    : component.isCraftingSubCore();
        };
    }

    private int missingPatternStorageRequirement() {
        return hasPatternStorageForAutoBuild() ? 0 : 1;
    }

    private boolean hasPatternStorageForAutoBuild() {
        Direction facing = getOrientation();
        for (var entry : MatrixMultiblockTemplate.entries()) {
            if (entry.role() != MatrixMultiblockRole.PATTERN_BAY) {
                continue;
            }
            var pos = MatrixMultiblockScanner.worldPos(worldPosition, entry.localPos(), facing);
            if (MatrixMultiblockScanner.componentAt(level, pos).isPatternStorage()) {
                return true;
            }
        }
        return false;
    }

    private java.util.Map<Item, Integer> findMissingRequirements(Player player, java.util.Map<Item, Integer> requirements) {
        var missing = new java.util.LinkedHashMap<Item, Integer>();
        for (var entry : requirements.entrySet()) {
            int available = countItem(player, entry.getKey());
            if (available < entry.getValue()) {
                missing.put(entry.getKey(), entry.getValue() - available);
            }
        }
        return missing;
    }

    private int countItem(Player player, Item item) {
        int count = 0;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private int countPatternStorageItems(Player player) {
        int count = 0;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (isPatternStorageItem(stack.getItem())) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private Item findPatternStorageItem(Player player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && isPatternStorageItem(stack.getItem())) {
                return stack.getItem();
            }
        }
        return null;
    }

    private BlockState stateForPatternStorageItem(Item item) {
        if (item instanceof BlockItem blockItem && blockItem.getBlock() instanceof MatrixPatternStorageBlock) {
            return blockItem.getBlock().defaultBlockState();
        }
        return null;
    }

    private boolean isPatternStorageItem(Item item) {
        return item instanceof BlockItem blockItem && blockItem.getBlock() instanceof MatrixPatternStorageBlock;
    }

    private void consumeRequirements(Player player, java.util.Map<Item, Integer> requirements) {
        for (var entry : requirements.entrySet()) {
            consumeItem(player, entry.getKey(), entry.getValue());
        }
    }

    private void consumeItem(Player player, Item item, int amount) {
        int remaining = amount;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
            var stack = inventory.getItem(i);
            if (!stack.is(item)) {
                continue;
            }
            int consumed = Math.min(remaining, stack.getCount());
            stack.shrink(consumed);
            if (stack.isEmpty()) {
                inventory.setItem(i, ItemStack.EMPTY);
            }
            remaining -= consumed;
        }
    }

    private String describeMissing(java.util.Map<Item, Integer> missing, int missingPatternStorages) {
        var parts = new ArrayList<String>();
        int totalEntries = missing.size() + (missingPatternStorages > 0 ? 1 : 0);
        if (missingPatternStorages > 0) {
            parts.add(Component.translatable("ae2lt.matrix.pattern_storage_any").getString()
                    + " x" + missingPatternStorages);
        }
        for (var entry : missing.entrySet()) {
            parts.add(entry.getKey().getDescription().getString() + " x" + entry.getValue());
            if (parts.size() >= 4 && totalEntries > parts.size()) {
                parts.add("...");
                break;
            }
        }
        return String.join(", ", parts);
    }

    private String describePositions(List<BlockPos> positions) {
        var parts = new ArrayList<String>();
        for (var pos : positions) {
            parts.add("[" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]");
        }
        return String.join(", ", parts);
    }

    private void upgradeStorageInPlace(MatrixPatternStorageBlockEntity oldStorage) {
        var pos = oldStorage.getBlockPos();
        var contents = oldStorage.copyContents();
        level.setBlock(pos, ModBlocks.MATTER_WARPING_MATRIX_PATTERN_STORAGE_T2.get().defaultBlockState(),
                Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof MatrixPatternStorageBlockEntity newStorage) {
            newStorage.loadContents(contents);
            newStorage.setControllerPos(worldPosition);
        }
    }

    private int countUpgradeItems(Player player) {
        Item upgrade = ModItems.MATTER_WARPING_MATRIX_PATTERN_STORAGE_UPGRADE.get();
        int count = 0;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            var stack = inventory.getItem(i);
            if (stack.is(upgrade)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private void consumeUpgradeItems(Player player, int amount) {
        Item upgrade = ModItems.MATTER_WARPING_MATRIX_PATTERN_STORAGE_UPGRADE.get();
        int remaining = amount;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
            var stack = inventory.getItem(i);
            if (!stack.is(upgrade)) {
                continue;
            }
            int consumed = Math.min(remaining, stack.getCount());
            stack.shrink(consumed);
            if (stack.isEmpty()) {
                inventory.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
            }
            remaining -= consumed;
        }
    }

    private String describeIssues(MatrixMultiblockScanAttempt attempt) {
        if (attempt.issues().isEmpty()) {
            return "unknown";
        }
        return attempt.issues().stream().map(Enum::name).reduce((a, b) -> a + ", " + b).orElse("unknown");
    }

    private Direction orientationFromState(BlockState state) {
        if (state.hasProperty(MatrixMultiblockDirectionalBlock.FACING)) {
            Direction facing = state.getValue(MatrixMultiblockDirectionalBlock.FACING);
            if (facing.getAxis() != Direction.Axis.Y) {
                return facing;
            }
        }
        return Direction.NORTH;
    }

    private void setChangedAndUpdate() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean(TAG_FORMED, formed);
        tag.putInt(TAG_ORIENTATION, orientation.get3DDataValue());
        if (portPos != null) {
            tag.putLong(TAG_PORT_POS, portPos.asLong());
        }
        if (minPos != null) {
            tag.putLong(TAG_MIN_POS, minPos.asLong());
        }
        if (maxPos != null) {
            tag.putLong(TAG_MAX_POS, maxPos.asLong());
        }
        tag.putInt(TAG_MEMBER_COUNT, memberCount);
        tag.putInt(TAG_PATTERN_STORAGE_COUNT, patternStorageCount);
        tag.putInt(TAG_CRAFTING_UNIT_COUNT, craftingUnitCount);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        formed = tag.getBoolean(TAG_FORMED);
        structureCacheValid = false;
        patternStoragePositions = List.of();
        cachedCraftingUnits = List.of();
        orientation = Direction.from3DDataValue(tag.getInt(TAG_ORIENTATION));
        if (orientation.getAxis() == Direction.Axis.Y) {
            orientation = Direction.NORTH;
        }
        portPos = tag.contains(TAG_PORT_POS, Tag.TAG_LONG) ? BlockPos.of(tag.getLong(TAG_PORT_POS)) : null;
        minPos = tag.contains(TAG_MIN_POS, Tag.TAG_LONG) ? BlockPos.of(tag.getLong(TAG_MIN_POS)) : null;
        maxPos = tag.contains(TAG_MAX_POS, Tag.TAG_LONG) ? BlockPos.of(tag.getLong(TAG_MAX_POS)) : null;
        memberCount = tag.getInt(TAG_MEMBER_COUNT);
        patternStorageCount = tag.getInt(TAG_PATTERN_STORAGE_COUNT);
        craftingUnitCount = tag.getInt(TAG_CRAFTING_UNIT_COUNT);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        scheduleStructureCheck();
    }
}
