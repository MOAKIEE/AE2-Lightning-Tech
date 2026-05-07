package appeng.blockentity.grid;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 1.21 class-name alias for the 1.20.1 Forge AE2 network block entity base.
 */
public class AENetworkedBlockEntity extends AENetworkBlockEntity {
    public AENetworkedBlockEntity(BlockEntityType<?> blockEntityType, BlockPos pos, BlockState blockState) {
        super(blockEntityType, pos, blockState);
    }
}
