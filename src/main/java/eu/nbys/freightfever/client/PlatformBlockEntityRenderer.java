package eu.nbys.freightfever.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import eu.nbys.freightfever.block.entity.PlatformBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;

/**
 * Cargo ghost blocks: while a freight platform zone holds items, translucent
 * placeholder cubes stack above each platform block (up to MAX_LAYERS),
 * appearing and disappearing with the fill ratio. Purely visual: no block
 * is placed, nothing can be broken or picked up. Nicer models will replace
 * these plain cubes later.
 *
 * The quads are borrowed from a real baked model (light gray concrete) so
 * the sprite UVs are exactly the ones the block renderer itself uses; they
 * are piped through putBulkData into an entity translucent buffer.
 */
public class PlatformBlockEntityRenderer implements BlockEntityRenderer<PlatformBlockEntity> {
    public PlatformBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static final int MAX_LAYERS = 3;
    private static final float LAYER_HEIGHT = 1.05f;
    private static final float SCALE = 0.9f;
    private static final float ALPHA = 0.85f;

    /** All faces of the placeholder model, cached after the first render. */
    private static List<BakedQuad> cargoQuads;

    private static List<BakedQuad> cargoQuads() {
        if (cargoQuads == null) {
            Minecraft mc = Minecraft.getInstance();
            BlockState state = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
            var model = mc.getBlockRenderer().getBlockModel(state);
            RandomSource rng = RandomSource.create(42);
            List<BakedQuad> list = new ArrayList<>(model.getQuads(state, null, rng));
            for (Direction dir : Direction.values()) {
                list.addAll(model.getQuads(state, dir, rng));
            }
            cargoQuads = list;
        }
        return cargoQuads;
    }

    @Override
    public void render(PlatformBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        if (!be.isZoneActive() || be.getZoneFill() <= 0.001f) return;
        Level level = be.getLevel();
        if (level == null) return;

        int layers = Math.min(MAX_LAYERS, (int) Math.ceil(be.getZoneFill() * MAX_LAYERS - 0.0001f));
        if (layers <= 0) return;

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
        int light = LevelRenderer.getLightColor(level, be.getBlockPos().above());

        for (int layer = 0; layer < layers; layer++) {
            pose.pushPose();
            float inset = (1.0f - SCALE) * 0.5f;
            pose.translate(inset, 1.0f + layer * LAYER_HEIGHT, inset);
            pose.scale(SCALE, SCALE, SCALE);
            PoseStack.Pose poseState = pose.last();
            for (BakedQuad quad : cargoQuads()) {
                consumer.putBulkData(poseState, quad, 1.0f, 1.0f, 1.0f, ALPHA, light, packedOverlay);
            }
            pose.popPose();
        }
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
