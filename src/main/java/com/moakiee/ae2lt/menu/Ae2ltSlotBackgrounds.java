package com.moakiee.ae2lt.menu;

import net.minecraft.resources.Identifier;

import com.moakiee.ae2lt.AE2LightningTech;

/**
 * 集中管理所有自定义槽位的空槽图标。
 *
 * <p>26.1 的槽位不再保存 atlas + sprite 二元组,而是通过
 * {@link net.minecraft.world.inventory.Slot#getNoItemIcon()} 返回 GUI sprite id。</p>
 */
public final class Ae2ltSlotBackgrounds {

    public static final Identifier ELECTRO_CHIME_CRYSTAL = sprite("electro_chime_crystal");
    public static final Identifier FILTER_COMPONENT = sprite("filter_component");
    public static final Identifier LIGHTNING_COLLAPSE_MATRIX = sprite("lightning_collapse_matrix");

    private static Identifier sprite(String name) {
        return Identifier.fromNamespaceAndPath(AE2LightningTech.MODID, "block/slot/" + name);
    }

    private Ae2ltSlotBackgrounds() {
    }
}
