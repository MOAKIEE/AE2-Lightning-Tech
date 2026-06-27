package com.moakiee.ae2lt.client.ctm;

import java.util.HashMap;
import java.util.Map;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.block.MatrixCasingBlock;
import com.moakiee.ae2lt.block.MatrixGlassBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Registry of named {@link ConnectionPredicate}s referenced by the {@code connection}
 * field of a connected-texture model. Add new entries here to reuse the CTM
 * framework for other blocks.
 */
public final class ConnectionPredicates {

    private static final Map<ResourceLocation, ConnectionPredicate> REGISTRY = new HashMap<>();

    /** Generic: always active, connects to any adjacent block of the same type. */
    public static final ConnectionPredicate SAME_BLOCK = new ConnectionPredicate() {
        @Override
        public boolean isActive(BlockAndTintGetter level, BlockPos pos, BlockState self) {
            return true;
        }

        @Override
        public boolean connects(BlockAndTintGetter level, BlockPos pos, BlockState self, Direction dir) {
            return level.getBlockState(pos.relative(dir)).is(self.getBlock());
        }

        @Override
        public boolean connects(BlockAndTintGetter level, BlockPos pos, BlockState self, BlockPos neighbourPos) {
            return level.getBlockState(neighbourPos).is(self.getBlock());
        }
    };

    /** Matrix glass: active only once formed; connects to other formed matrix glass. */
    public static final ConnectionPredicate MATRIX_FORMED_GLASS = new ConnectionPredicate() {
        @Override
        public boolean isActive(BlockAndTintGetter level, BlockPos pos, BlockState self) {
            return self.getBlock() instanceof MatrixGlassBlock && self.getValue(MatrixGlassBlock.FORMED);
        }

        @Override
        public boolean connects(BlockAndTintGetter level, BlockPos pos, BlockState self, Direction dir) {
            BlockState neighbour = level.getBlockState(pos.relative(dir));
            return neighbour.getBlock() instanceof MatrixGlassBlock && neighbour.getValue(MatrixGlassBlock.FORMED);
        }

        @Override
        public boolean connects(BlockAndTintGetter level, BlockPos pos, BlockState self, BlockPos neighbourPos) {
            BlockState neighbour = level.getBlockState(neighbourPos);
            return neighbour.getBlock() instanceof MatrixGlassBlock && neighbour.getValue(MatrixGlassBlock.FORMED);
        }
    };

    /** Matrix casing: active only once formed; connects to other formed matrix casing blocks. */
    public static final ConnectionPredicate MATRIX_FORMED_CASING = new ConnectionPredicate() {
        @Override
        public boolean isActive(BlockAndTintGetter level, BlockPos pos, BlockState self) {
            return self.getBlock() instanceof MatrixCasingBlock && self.getValue(MatrixCasingBlock.FORMED);
        }

        @Override
        public boolean connects(BlockAndTintGetter level, BlockPos pos, BlockState self, Direction dir) {
            BlockState neighbour = level.getBlockState(pos.relative(dir));
            return neighbour.getBlock() instanceof MatrixCasingBlock && neighbour.getValue(MatrixCasingBlock.FORMED);
        }

        @Override
        public boolean connects(BlockAndTintGetter level, BlockPos pos, BlockState self, BlockPos neighbourPos) {
            BlockState neighbour = level.getBlockState(neighbourPos);
            return neighbour.getBlock() instanceof MatrixCasingBlock && neighbour.getValue(MatrixCasingBlock.FORMED);
        }
    };

    static {
        register(rl("same_block"), SAME_BLOCK);
        register(rl("matrix_formed_glass"), MATRIX_FORMED_GLASS);
        register(rl("matrix_formed_casing"), MATRIX_FORMED_CASING);
    }

    private ConnectionPredicates() {
    }

    public static void register(ResourceLocation id, ConnectionPredicate predicate) {
        REGISTRY.put(id, predicate);
    }

    public static ConnectionPredicate get(ResourceLocation id) {
        return REGISTRY.getOrDefault(id, SAME_BLOCK);
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, path);
    }
}
