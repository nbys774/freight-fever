package eu.nbys.freightfever.block;

import eu.nbys.freightfever.world.FreightZones;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;


/**
 * Freight post: four posts placed at the corners of a same-height rectangle
 * (each side between 2 and platformMaxSide blocks) frame a freight platform
 * zone. Platform blocks strictly inside the posts share the zone storage,
 * whose capacity scales with the number of platform blocks.
 */
public class PostBlock extends Block {
    public PostBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        FreightZones.pokePlatformsAround(level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        FreightZones.pokePlatformsAround(level, pos);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                    BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        FreightZones.pokePlatformsAround(level, pos);
    }
}
