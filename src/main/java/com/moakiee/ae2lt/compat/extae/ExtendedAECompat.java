package com.moakiee.ae2lt.compat.extae;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.grid.OverloadedBETypeOverride;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import appeng.api.AECapabilities;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.upgrades.Upgrades;
import appeng.block.AEBaseEntityBlock;
import appeng.core.definitions.AEItems;

/**
 * Conditional compatibility layer for ExtendedAE.
 * All references to ExtendedAE classes are confined to this package so
 * the rest of the codebase never triggers {@link ClassNotFoundException}
 * when ExtendedAE is absent.
 */
public final class ExtendedAECompat {

    private static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(AE2LightningTech.MODID);
    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(AE2LightningTech.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BE_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AE2LightningTech.MODID);
    private static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, AE2LightningTech.MODID);

    // ── Blocks ──────────────────────────────────────────────────────────

    public static final DeferredBlock<OverloadedWirelessConnectorBlock> OVERLOADED_WIRELESS_CONNECTOR =
            BLOCKS.register("overloaded_wireless_connector",
                    OverloadedWirelessConnectorBlock::new);

    public static final DeferredBlock<OverloadedWirelessHubBlock> OVERLOADED_WIRELESS_HUB =
            BLOCKS.register("overloaded_wireless_hub",
                    OverloadedWirelessHubBlock::new);

    // ── Block Items ─────────────────────────────────────────────────────

    @SuppressWarnings("unused")
    private static final DeferredItem<BlockItem> CONNECTOR_ITEM =
            ITEMS.register("overloaded_wireless_connector",
                    () -> new BlockItem(OVERLOADED_WIRELESS_CONNECTOR.get(),
                            new Item.Properties()));

    @SuppressWarnings("unused")
    private static final DeferredItem<BlockItem> HUB_ITEM =
            ITEMS.register("overloaded_wireless_hub",
                    () -> new BlockItem(OVERLOADED_WIRELESS_HUB.get(),
                            new Item.Properties()));

    // ── Block Entity Types ──────────────────────────────────────────────

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<OverloadedWirelessConnectorBlockEntity>> OVERLOADED_WIRELESS_CONNECTOR_BE =
            BE_TYPES.register("overloaded_wireless_connector",
                    () -> BlockEntityType.Builder.of(
                            ExtendedAECompat::createConnectorBE,
                            OVERLOADED_WIRELESS_CONNECTOR.get()
                    ).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<OverloadedWirelessHubBlockEntity>> OVERLOADED_WIRELESS_HUB_BE =
            BE_TYPES.register("overloaded_wireless_hub",
                    () -> BlockEntityType.Builder.of(
                            ExtendedAECompat::createHubBE,
                            OVERLOADED_WIRELESS_HUB.get()
                    ).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<OverloadedWirelessConnectorMenu>>
            OVERLOADED_WIRELESS_CONNECTOR_MENU =
            MENU_TYPES.register("overloaded_wireless_connector",
                    () -> OverloadedWirelessConnectorMenu.TYPE);

    public static final DeferredHolder<MenuType<?>, MenuType<OverloadedWirelessHubMenu>>
            OVERLOADED_WIRELESS_HUB_MENU =
            MENU_TYPES.register("overloaded_wireless_hub",
                    () -> OverloadedWirelessHubMenu.TYPE);

    private ExtendedAECompat() {}

    private static OverloadedWirelessConnectorBlockEntity createConnectorBE(BlockPos pos, BlockState state) {
        OverloadedBETypeOverride.pending = OVERLOADED_WIRELESS_CONNECTOR_BE.get();
        try {
            return new OverloadedWirelessConnectorBlockEntity(pos, state);
        } finally {
            OverloadedBETypeOverride.pending = null;
        }
    }

    private static OverloadedWirelessHubBlockEntity createHubBE(BlockPos pos, BlockState state) {
        OverloadedBETypeOverride.pending = OVERLOADED_WIRELESS_HUB_BE.get();
        try {
            return new OverloadedWirelessHubBlockEntity(pos, state);
        } finally {
            OverloadedBETypeOverride.pending = null;
        }
    }

    /**
     * Called from the mod constructor when ExtendedAE is present.
     */
    public static void init(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BE_TYPES.register(modEventBus);
        MENU_TYPES.register(modEventBus);

        modEventBus.addListener(ExtendedAECompat::registerCapabilities);
    }

    /**
     * Called from common setup to bind AE2 block-entity types.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void setupBlockEntities() {
        ((AEBaseEntityBlock) OVERLOADED_WIRELESS_CONNECTOR.get()).setBlockEntity(
                OverloadedWirelessConnectorBlockEntity.class,
                OVERLOADED_WIRELESS_CONNECTOR_BE.get(),
                null,
                (level, pos, state, be) ->
                        ((OverloadedWirelessConnectorBlockEntity) be).serverTick());

        ((AEBaseEntityBlock) OVERLOADED_WIRELESS_HUB.get()).setBlockEntity(
                OverloadedWirelessHubBlockEntity.class,
                OVERLOADED_WIRELESS_HUB_BE.get(),
                null,
                (level, pos, state, be) ->
                        ((OverloadedWirelessHubBlockEntity) be).serverTick());

        Upgrades.add(AEItems.ENERGY_CARD, OVERLOADED_WIRELESS_CONNECTOR.get(), 4);
        Upgrades.add(AEItems.ENERGY_CARD, OVERLOADED_WIRELESS_HUB.get(), 4);
    }

    public static void addCreativeTabItems(CreativeModeTab.Output output) {
        output.accept(OVERLOADED_WIRELESS_CONNECTOR);
        output.accept(OVERLOADED_WIRELESS_HUB);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                OVERLOADED_WIRELESS_CONNECTOR_BE.get(),
                (be, ctx) -> (IInWorldGridNodeHost) be);

        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                OVERLOADED_WIRELESS_HUB_BE.get(),
                (be, ctx) -> (IInWorldGridNodeHost) be);
    }
}
