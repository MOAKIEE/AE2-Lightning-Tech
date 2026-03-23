package com.moakiee.ae2lt.mixin.extae;

import com.moakiee.ae2lt.grid.OverloadedBETypeOverride;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "com.glodblock.github.extendedae.common.tileentities.TileWirelessHub", remap = false)
public abstract class TileWirelessHubMixin {

    @WrapOperation(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/glodblock/github/glodium/util/GlodUtil;getTileType(Ljava/lang/Class;Lnet/minecraft/world/level/block/entity/BlockEntityType$BlockEntitySupplier;Lnet/minecraft/world/level/block/Block;)Lnet/minecraft/world/level/block/entity/BlockEntityType;"
            )
    )
    private static BlockEntityType<?> ae2lt$overrideType(
            Class<?> cls,
            BlockEntityType.BlockEntitySupplier<?> factory,
            Block block,
            Operation<BlockEntityType<?>> original) {
        var pending = OverloadedBETypeOverride.pending;
        if (pending != null) return pending;
        return original.call(cls, factory, block);
    }
}
