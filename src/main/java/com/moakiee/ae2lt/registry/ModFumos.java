package com.moakiee.ae2lt.registry;

import com.moakiee.ae2lt.block.FumoBlock;
import com.moakiee.ae2lt.item.FumoBlockItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Registers AE2LT fumo decoration blocks.
 * Moakiee/Cystrysu stay as ME Placement Tool fallbacks, while Pigmee is owned by AE2LT.
 */
public final class ModFumos {

    private static final String PLACEMENT_TOOL_MODID = "meplacementtool";

    public static DeferredBlock<FumoBlock> MOAKIEE_FUMO;
    public static DeferredBlock<FumoBlock> CYSTRYSU_FUMO;
    public static DeferredBlock<FumoBlock> PIGMEE_FUMO;
    public static DeferredItem<BlockItem> MOAKIEE_FUMO_ITEM;
    public static DeferredItem<BlockItem> CYSTRYSU_FUMO_ITEM;
    public static DeferredItem<FumoBlockItem> PIGMEE_FUMO_ITEM;

    private ModFumos() {
    }

    public static boolean isEnabled() {
        return MOAKIEE_FUMO != null;
    }

    public static boolean isPigmeeEnabled() {
        return PIGMEE_FUMO != null;
    }

    public static void register() {
        if (!ModList.get().isLoaded(PLACEMENT_TOOL_MODID)) {
            MOAKIEE_FUMO = ModBlocks.BLOCKS.register("moakiee_fumo", FumoBlock::new);
            MOAKIEE_FUMO_ITEM = ModItems.ITEMS.register("moakiee_fumo",
                    () -> new BlockItem(MOAKIEE_FUMO.get(), new Item.Properties()));
            CYSTRYSU_FUMO = ModBlocks.BLOCKS.register("cystrysu_fumo", FumoBlock::new);
            CYSTRYSU_FUMO_ITEM = ModItems.ITEMS.register("cystrysu_fumo",
                    () -> new BlockItem(CYSTRYSU_FUMO.get(), new Item.Properties()));
        }

        PIGMEE_FUMO = ModBlocks.BLOCKS.register("pigmee_fumo", FumoBlock::new);
        PIGMEE_FUMO_ITEM = ModItems.ITEMS.register("pigmee_fumo",
                () -> new FumoBlockItem(PIGMEE_FUMO.get(), new Item.Properties(),
                        "tooltip.ae2lt.pigmee_fumo"));
    }
}
