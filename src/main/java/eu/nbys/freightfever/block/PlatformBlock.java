package eu.nbys.freightfever.block;

import com.mojang.serialization.MapCodec;
import eu.nbys.freightfever.FreightFever;
import eu.nbys.freightfever.block.entity.PlatformBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

public class PlatformBlock extends BaseEntityBlock {
    public static final MapCodec<PlatformBlock> CODEC = simpleCodec(PlatformBlock::new);
    public static final EnumProperty<PlatformMode> MODE = EnumProperty.create("mode", PlatformMode.class);

    public PlatformBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(MODE, PlatformMode.IN));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MODE);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PlatformBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, FreightFever.PLATFORM_BE.get(), PlatformBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                PlatformMode next = state.getValue(MODE).next();
                level.setBlock(pos, state.setValue(MODE, next), Block.UPDATE_ALL);
                if (level.getBlockEntity(pos) instanceof PlatformBlockEntity be) {
                    be.onModeChanged();
                }
                player.displayClientMessage(Component.translatable("block.freightfever.platform.mode", next.getSerializedName()), true);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
