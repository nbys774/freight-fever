package eu.nbys.freightfever.block;

import com.mojang.serialization.MapCodec;
import eu.nbys.freightfever.FreightFever;
import eu.nbys.freightfever.block.entity.IndustryBlockEntity;
import eu.nbys.freightfever.data.FreightData;
import eu.nbys.freightfever.data.IndustryRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;

public class IndustryBlock extends BaseEntityBlock {
    public static final MapCodec<IndustryBlock> CODEC = simpleCodec(IndustryBlock::new);

    public IndustryBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new IndustryBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, FreightFever.INDUSTRY_BE.get(), IndustryBlockEntity::serverTick);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof IndustryBlockEntity be) {
            be.rollProductionFactor();
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof IndustryBlockEntity be) {
            // screen-less recipe selection: plain right-click cycles loaded recipes
            List<IndustryRecipe> recipes = new ArrayList<>(FreightData.industries());
            if (!recipes.isEmpty()) {
                ResourceLocation current = be.getRecipeId();
                int idx = 0;
                for (int i = 0; i < recipes.size(); i++) {
                    if (recipes.get(i).id().equals(current)) { idx = i; break; }
                }
                IndustryRecipe next = recipes.get((idx + 1) % recipes.size());
                be.setRecipeId(next.id());
                player.displayClientMessage(Component.translatable("block.freightfever.industry.recipe", next.id().toString()), true);
            }
            player.displayClientMessage(Component.translatable("block.freightfever.industry.factor", String.format("%.2f", be.getProductionFactor())), true);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }
}
