package eu.nbys.freightfever.block;

import com.mojang.serialization.MapCodec;
import eu.nbys.freightfever.FreightFever;
import eu.nbys.freightfever.block.entity.ConsumerBlockEntity;
import eu.nbys.freightfever.data.Demand;
import eu.nbys.freightfever.data.FreightData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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

import java.util.ArrayList;
import java.util.List;

public class ConsumerBlock extends BaseEntityBlock {
    public static final MapCodec<ConsumerBlock> CODEC = simpleCodec(ConsumerBlock::new);
    public static final EnumProperty<ConsumerState> STATE = EnumProperty.create("state", ConsumerState.class);

    public ConsumerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(STATE, ConsumerState.HUNGRY));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STATE);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConsumerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, FreightFever.CONSUMER_BE.get(), ConsumerBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof ConsumerBlockEntity be) {
            List<Demand> demands = new ArrayList<>(FreightData.demands());
            if (!demands.isEmpty()) {
                ResourceLocation current = be.getDemandId();
                int idx = 0;
                for (int i = 0; i < demands.size(); i++) {
                    if (demands.get(i).id().equals(current)) { idx = i; break; }
                }
                Demand next = demands.get((idx + 1) % demands.size());
                be.setDemandId(next.id());
            }
            player.displayClientMessage(Component.translatable("block.freightfever.consumer.info", be.getDemandId().toString(), be.describeSatisfaction()), true);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }
}
