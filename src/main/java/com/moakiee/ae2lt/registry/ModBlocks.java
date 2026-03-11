package com.moakiee.ae2lt.registry;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.block.BuddingOverloadCrystalBlock;
import com.moakiee.ae2lt.block.OverloadCrystalClusterBlock;
import java.util.function.Supplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AE2LightningTech.MODID);

    private static final BlockBehaviour.Properties BUDDING_PROPERTIES = BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_CYAN)
            .strength(3.0F, 5.0F)
            .sound(SoundType.AMETHYST)
            .randomTicks()
            .requiresCorrectToolForDrops();

    private static final BlockBehaviour.Properties CLUSTER_PROPERTIES = BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_CYAN)
            .strength(1.5F)
            .sound(SoundType.AMETHYST_CLUSTER)
            .forceSolidOn()
            .requiresCorrectToolForDrops();

    private static final BlockBehaviour.Properties OVERLOAD_CRYSTAL_BLOCK_PROPERTIES = BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_CYAN)
            .strength(3.0F, 5.0F)
            .sound(SoundType.STONE)
            .forceSolidOn()
            .requiresCorrectToolForDrops();

    public static final DeferredBlock<Block> OVERLOAD_CRYSTAL_BLOCK =
            registerBlock("overload_crystal_block", () -> new Block(OVERLOAD_CRYSTAL_BLOCK_PROPERTIES));

    public static final DeferredBlock<Block> HIGH_VOLTAGE_AGGREGATOR =
            registerBlock("high_voltage_aggregator", () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.2F, 11.0F)
                    .sound(SoundType.COPPER)
                    .forceSolidOn()
                    .noOcclusion()));

    public static final DeferredBlock<BuddingOverloadCrystalBlock> FLAWLESS_BUDDING_OVERLOAD_CRYSTAL =
            registerBlock("flawless_budding_overload_crystal", () ->
                    new BuddingOverloadCrystalBlock(BUDDING_PROPERTIES));

    public static final DeferredBlock<BuddingOverloadCrystalBlock> FLAWED_BUDDING_OVERLOAD_CRYSTAL =
            registerBlock("flawed_budding_overload_crystal", () ->
                    new BuddingOverloadCrystalBlock(BUDDING_PROPERTIES));

    public static final DeferredBlock<BuddingOverloadCrystalBlock> CRACKED_BUDDING_OVERLOAD_CRYSTAL =
            registerBlock("cracked_budding_overload_crystal", () ->
                    new BuddingOverloadCrystalBlock(BUDDING_PROPERTIES));

    public static final DeferredBlock<BuddingOverloadCrystalBlock> DAMAGED_BUDDING_OVERLOAD_CRYSTAL =
            registerBlock("damaged_budding_overload_crystal", () ->
                    new BuddingOverloadCrystalBlock(BUDDING_PROPERTIES));

    public static final DeferredBlock<OverloadCrystalClusterBlock> SMALL_OVERLOAD_CRYSTAL_BUD =
            registerBlock("small_overload_crystal_bud", () ->
                    new OverloadCrystalClusterBlock(3, 4, CLUSTER_PROPERTIES.sound(SoundType.SMALL_AMETHYST_BUD).lightLevel(s -> 1)));

    public static final DeferredBlock<OverloadCrystalClusterBlock> MEDIUM_OVERLOAD_CRYSTAL_BUD =
            registerBlock("medium_overload_crystal_bud", () ->
                    new OverloadCrystalClusterBlock(4, 3, CLUSTER_PROPERTIES.sound(SoundType.MEDIUM_AMETHYST_BUD).lightLevel(s -> 2)));

    public static final DeferredBlock<OverloadCrystalClusterBlock> LARGE_OVERLOAD_CRYSTAL_BUD =
            registerBlock("large_overload_crystal_bud", () ->
                    new OverloadCrystalClusterBlock(5, 3, CLUSTER_PROPERTIES.sound(SoundType.LARGE_AMETHYST_BUD).lightLevel(s -> 4)));

    public static final DeferredBlock<OverloadCrystalClusterBlock> OVERLOAD_CRYSTAL_CLUSTER =
            registerBlock("overload_crystal_cluster", () ->
                    new OverloadCrystalClusterBlock(7, 3, CLUSTER_PROPERTIES.sound(SoundType.AMETHYST_CLUSTER).lightLevel(s -> 5)));

    private ModBlocks() {
    }

    private static <T extends Block> DeferredBlock<T> registerBlock(String name, Supplier<T> blockFactory) {
        var registered = BLOCKS.register(name, blockFactory);
        ModItems.ITEMS.register(name, () -> new BlockItem(registered.get(), new Item.Properties()));
        return registered;
    }
}
