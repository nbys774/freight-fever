package eu.nbys.freightfever.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import eu.nbys.freightfever.block.entity.PlatformBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;

/**
 * Cargo ghost blocks: while a freight platform zone holds items, translucent
 * placeholder cubes stack above each platform block (up to maxVisualLayers),
 * appearing and disappearing with the fill ratio. Purely visual: no block
 * is placed, nothing can be broken or picked up. Nicer models will replace
 * these plain cubes later.
 */
public class PlatformBlockEntityRenderer implements BlockEntityRenderer<PlatformBlockEntity> {
    public PlatformBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static final ResourceLocation CARGO_SPRITE = ResourceLocation.withDefaultNamespace("block/light_gray_concrete");
    private static final float INSET = 0.1f;
    private static final int MAX_LAYERS = 3;
    private static final float LAYER_HEIGHT = 1.05f;

    @Override
    public void render(PlatformBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        if (!be.isZoneActive() || be.getZoneFill() <= 0.001f) return;
        Level level = be.getLevel();
        if (level == null) return;

        int layers = Math.min(MAX_LAYERS, (int) Math.ceil(be.getZoneFill() * MAX_LAYERS - 0.0001f));
        if (layers <= 0) return;

        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(CARGO_SPRITE);
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
        int light = LevelRenderer.getLightColor(level, be.getBlockPos().above());

        for (int layer = 0; layer < layers; layer++) {
            float y0 = 1.0f + layer * LAYER_HEIGHT;
            drawCube(pose, consumer, sprite, light, packedOverlay, INSET, y0, INSET, 1.0f - INSET, y0 + 0.9f, 1.0f - INSET);
        }
    }

    /** Draws one translucent cube; the render type is not culled, so winding order is irrelevant. */
    private static void drawCube(PoseStack pose, VertexConsumer vc, TextureAtlasSprite sprite, int light, int overlay,
                                 float x0, float y0, float z0, float x1, float y1, float z1) {
        PoseStack.Pose poseState = pose.last();
        float r = 0.72f, g = 0.74f, b = 0.78f, a = 0.85f;
        // top
        face(poseState, vc, sprite, light, overlay, r, g, b, a,
                x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0f, 1f, 0f);
        // bottom
        face(poseState, vc, sprite, light, overlay, r, g, b, a,
                x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0f, -1f, 0f);
        // north (z0)
        face(poseState, vc, sprite, light, overlay, r, g, b, a,
                x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, 0f, 0f, -1f);
        // south (z1)
        face(poseState, vc, sprite, light, overlay, r, g, b, a,
                x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0f, 0f, 1f);
        // west (x0)
        face(poseState, vc, sprite, light, overlay, r, g, b, a,
                x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1f, 0f, 0f);
        // east (x1)
        face(poseState, vc, sprite, light, overlay, r, g, b, a,
                x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, 1f, 0f, 0f);
    }

    private static void face(PoseStack.Pose poseState, VertexConsumer vc, TextureAtlasSprite sprite, int light, int overlay,
                            float r, float g, float b, float a,
                            float ax, float ay, float az, float bx, float by, float bz,
                            float cx, float cy, float cz, float dx, float dy, float dz,
                            float nx, float ny, float nz) {
        float u0 = sprite.getU(0.0f);
        float u1 = sprite.getU(15.99f);
        float v0 = sprite.getV(0.0f);
        float v1 = sprite.getV(15.99f);
        vc.addVertex(poseState, ax, ay, az).setColor(r, g, b, a).setUv(u0, v0).setLight(light).setOverlay(overlay).setNormal(poseState, nx, ny, nz);
        vc.addVertex(poseState, bx, by, bz).setColor(r, g, b, a).setUv(u1, v0).setLight(light).setOverlay(overlay).setNormal(poseState, nx, ny, nz);
        vc.addVertex(poseState, cx, cy, cz).setColor(r, g, b, a).setUv(u1, v1).setLight(light).setOverlay(overlay).setNormal(poseState, nx, ny, nz);
        vc.addVertex(poseState, dx, dy, dz).setColor(r, g, b, a).setUv(u0, v1).setLight(light).setOverlay(overlay).setNormal(poseState, nx, ny, nz);
    }
}
