package eu.nbys.freightfever.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import eu.nbys.freightfever.block.entity.PlatformBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Cargo ghost blocks: while a freight platform zone holds freight, translucent
 * placeholder blocks appear above each platform block, purely visual: no block
 * is placed, nothing can be broken or picked up.
 *
 * The zone fills layer by layer: cargo starts flat on the platform (layer 1),
 * that layer grows to full height, then layer 2 grows above it, then layer 3.
 * Cubes in a column are flush with each other; bottom-facing quads are skipped
 * since they are always hidden.
 *
 * The texture tracks the dominant stored item: block items show their own
 * block, everything else falls back to vanilla cargo imagery a villager
 * professional would use (fisherman barrels for fish, a crate of wheat...).
 * Bread uses a barrel too, upgraded to a Create single vault when Create is
 * installed. No mod-specific block is ever referenced unless it is loaded.
 * Quads are borrowed from the real baked model and piped through putBulkData on
 * the standard translucent block sheet.
 */
public class PlatformBlockEntityRenderer implements BlockEntityRenderer<PlatformBlockEntity> {
    public PlatformBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static final int MAX_LAYERS = 3;
    private static final float ALPHA = 0.85f;
    private static final int FULLBRIGHT = 0xF000F0;
    /** Hair gap so side faces of neighbouring columns are not coplanar. */
    private static final float GAP = 0.001f;

    /** Dominant item id -> representative cargo block. Block items resolve automatically. */
    private static final Map<String, String> FREIGHT_TO_BLOCK = Map.of(
            "minecraft:wheat", "minecraft:hay_block",
            "minecraft:cod", "minecraft:barrel",
            "minecraft:cooked_cod", "minecraft:barrel",
            "minecraft:iron_ingot", "minecraft:iron_block");

    private static final String FALLBACK_BLOCK = "minecraft:barrel";
    private static final String CREATE_VAULT = "create:item_vault";

    private static final Map<BlockState, List<BakedQuad>> QUAD_CACHE = new HashMap<>();

    /** Bread rides in a Create single vault when Create is present, else a barrel. */
    private static String breadBlock() {
        if (BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse(CREATE_VAULT))) {
            return CREATE_VAULT;
        }
        return FALLBACK_BLOCK;
    }

    private static BlockState ghostState(String itemKey) {
        Block block = null;
        String override = "minecraft:bread".equals(itemKey)
                ? breadBlock()
                : FREIGHT_TO_BLOCK.get(itemKey);
        if (override != null) {
            block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(override));
        } else {
            var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemKey));
            if (item instanceof BlockItem blockItem) block = blockItem.getBlock();
        }
        if (block == null || block == Blocks.AIR) {
            return Blocks.BARREL.defaultBlockState();
        }
        return block.defaultBlockState();
    }

    private static List<BakedQuad> cargoQuads(BlockState state) {
        return QUAD_CACHE.computeIfAbsent(state, s -> {
            var model = Minecraft.getInstance().getBlockRenderer().getBlockModel(s);
            RandomSource rng = RandomSource.create(42);
            List<BakedQuad> list = new ArrayList<>(model.getQuads(s, null, rng));
            for (Direction dir : Direction.values()) {
                list.addAll(model.getQuads(s, dir, rng));
            }
            return list;
        });
    }

    @Override
    public void render(PlatformBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        if (!be.isZoneActive() || be.getZoneFill() <= 0.001f) return;
        Level level = be.getLevel();
        if (level == null) return;

        // Cargo column height in blocks: fill 1/3 shows one flat layer, fill 2/3 one
        // full layer plus a growing one, and so on, layer by layer from the bottom.
        float totalBlocks = Math.min(1.0f, be.getZoneFill()) * MAX_LAYERS;
        int fullLayers = Math.min(MAX_LAYERS, (int) Math.floor(totalBlocks + 1.0e-4f));
        float partialHeight = totalBlocks - fullLayers;
        if (fullLayers + (partialHeight > 0.02f ? 1 : 0) == 0) return;
        if (fullLayers == MAX_LAYERS) partialHeight = 0.0f;

        String itemKey = be.getZoneItem();
        BlockState state = itemKey == null ? Blocks.BARREL.defaultBlockState() : ghostState(itemKey);
        List<BakedQuad> quads = cargoQuads(state);

        VertexConsumer consumer = buffer.getBuffer(Sheets.translucentCullBlockSheet());
        int light = LevelRenderer.getLightColor(level, be.getBlockPos().above());
        if (light == 0) light = FULLBRIGHT; // keep cargo readable in unlit pits

        for (int layer = 0; layer < fullLayers; layer++) {
            emitCube(pose, consumer, quads, layer, 1.0f, light, packedOverlay);
        }
        if (partialHeight > 0.02f) {
            emitCube(pose, consumer, quads, fullLayers, partialHeight, light, packedOverlay);
        }
    }

    /** One ghost cube filling the block cell above the platform, at the given layer. */
    private void emitCube(PoseStack pose, VertexConsumer consumer, List<BakedQuad> quads,
                          int layer, float height, int light, int overlay) {
        pose.pushPose();
        pose.translate(GAP, 1.0f + layer, GAP);
        pose.scale(1.0f - 2.0f * GAP, height, 1.0f - 2.0f * GAP);
        PoseStack.Pose poseState = pose.last();
        for (BakedQuad quad : quads) {
            if (quad.getDirection() == Direction.DOWN) continue; // always hidden below cargo
            consumer.putBulkData(poseState, quad, 1.0f, 1.0f, 1.0f, ALPHA, light, overlay);
        }
        pose.popPose();
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
