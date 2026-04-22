package com.moakiee.ae2lt.registry;

import com.moakiee.ae2lt.block.FumoBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * 当 ME Placement Tool 未加载时，注册两个 fumo 装饰方块作为该 mod 功能的备用。
 * 该 mod 若已加载则不注册任何内容，避免 id 冲突。
 */
public final class ModFumos {

    private static final String PLACEMENT_TOOL_MODID = "meplacementtool";

    public static DeferredBlock<FumoBlock> MOAKIEE_FUMO;
    public static DeferredBlock<FumoBlock> CYSTRYSU_FUMO;
    public static DeferredItem<BlockItem> MOAKIEE_FUMO_ITEM;
    public static DeferredItem<BlockItem> CYSTRYSU_FUMO_ITEM;

    private ModFumos() {
    }

    public static boolean isEnabled() {
        return MOAKIEE_FUMO != null;
    }

    public static void register() {
        if (ModList.get().isLoaded(PLACEMENT_TOOL_MODID)) {
            return;
        }
        MOAKIEE_FUMO = ModBlocks.BLOCKS.register("moakiee_fumo", FumoBlock::new);
        MOAKIEE_FUMO_ITEM = ModItems.ITEMS.register("moakiee_fumo",
                () -> new BlockItem(MOAKIEE_FUMO.get(), new Item.Properties()));
        CYSTRYSU_FUMO = ModBlocks.BLOCKS.register("cystrysu_fumo", FumoBlock::new);
        CYSTRYSU_FUMO_ITEM = ModItems.ITEMS.register("cystrysu_fumo",
                () -> new BlockItem(CYSTRYSU_FUMO.get(), new Item.Properties()));
    }
}
