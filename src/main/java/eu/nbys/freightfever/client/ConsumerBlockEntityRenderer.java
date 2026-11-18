package eu.nbys.freightfever.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import eu.nbys.freightfever.block.entity.ConsumerBlockEntity;
import eu.nbys.freightfever.data.Demand;
import eu.nbys.freightfever.data.FreightData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** With the wrench in hand, floats the items demanded by this consumer above the block. */
public class ConsumerBlockEntityRenderer implements BlockEntityRenderer<ConsumerBlockEntity> {
    private static final float SPACING = 0.45f;
    private static final float SCALE = 0.4f;
    private static final float HEIGHT = 1.35f;

    public ConsumerBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(ConsumerBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (!WrenchOverlayHandler.isHoldingWrench()) return;
        ResourceLocation id = be.getDemandId();
        Demand demand = id == null ? null : FreightData.getDemand(id);
        if (demand == null) return;
        long gameTime = be.getLevel() == null ? 0L : be.getLevel().getGameTime();
        float time = gameTime + partialTick;

        List<ItemStack> row = new ArrayList<>();
        for (Demand.DemandItem item : demand.items()) row.add(new ItemStack(item.item()));
        if (row.isEmpty()) return;

        float bob = 0.06f * Mth.sin(time / 9.0f);
        float startX = -(row.size() - 1) * SPACING / 2.0f;
        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        for (int i = 0; i < row.size(); i++) {
            pose.pushPose();
            pose.translate(startX + i * SPACING, HEIGHT + bob, 0.5f);
            pose.mulPose(Axis.YP.rotationDegrees(time * 2.0f));
            pose.scale(SCALE, SCALE, SCALE);
            itemRenderer.renderStatic(row.get(i), ItemDisplayContext.GROUND, packedLight, OverlayTexture.NO_OVERLAY, pose, buffer, be.getLevel(), 0);
            pose.popPose();
        }
    }
}
