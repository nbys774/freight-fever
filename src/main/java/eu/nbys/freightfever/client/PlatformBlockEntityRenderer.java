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
 * placeholder cubes stack above each platform block (up to MAX_LAYERS),
 * appearing and disappearing with the fill ratio. Purely visual: no block is
 * placed, nothing can be broken or picked up.
 *
 * The texture tracks the dominant stored item: block items show their own
 * block, raw materials map to a representative vanilla block (freight map
 * below), anything unknown falls back to a barrel. Quads are borrowed from the
 * real baked model and piped through putBulkData on the standard translucent
 * block sheet, so UVs and lighting are exactly what the block renderer uses.
 */
public class PlatformBlockEntityRenderer implements BlockEntityRenderer<PlatformBlockEntity> {
    public PlatformBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static final int MAX_LAYERS = 3;
    private static final float LAYER_HEIGHT = 1.05f;
    private static final float SCALE = 0.9f;
    private static final float ALPHA = 0.85f;
    private static final int FULLBRIGHT = 0xF000F0;

    /** Dominant item id -> representative cargo block. Block items resolve automatically. */
    private static final Map<String, String> FREIGHT_TO_BLOCK = Map.of(
            "minecraft:wheat", "minecraft:hay_block",
            "minecraft:bread", "minecraft:cake",
            "minecraft:cod", "minecraft:prismarine",
            "minecraft:cooked_cod", "minecraft:dried_kelp_block",
            "minecraft:iron_ingot", "minecraft:iron_block",
            "freightfever:steel_ingot", "minecraft:anvil");

    private static final String FALLBACK_BLOCK = "minecraft:barrel";

    private static final Map<BlockState, List<BakedQuad>> QUAD_CACHE = new HashMap<>();

    private static BlockState ghostState(String itemKey) {
        Block block = null;
        String override = FREIGHT_TO_BLOCK.get(itemKey);
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

        int layers = Math.min(MAX_LAYERS, (int) Math.ceil(be.getZoneFill() * MAX_LAYERS - 0.0001f));
        if (layers <= 0) return;

        String itemKey = be.getZoneItem();
        BlockState state = itemKey == null ? Blocks.BARREL.defaultBlockState() : ghostState(itemKey);
        List<BakedQuad> quads = cargoQuads(state);

        VertexConsumer consumer = buffer.getBuffer(Sheets.translucentCullBlockSheet());
        int light = LevelRenderer.getLightColor(level, be.getBlockPos().above());
        if (light == 0) light = FULLBRIGHT; // keep cargo readable in unlit pits

        for (int layer = 0; layer < layers; layer++) {
            pose.pushPose();
            float inset = (1.0f - SCALE) * 0.5f;
            pose.translate(inset, 1.0f + layer * LAYER_HEIGHT, inset);
            pose.scale(SCALE, SCALE, SCALE);
            PoseStack.Pose poseState = pose.last();
            for (BakedQuad quad : quads) {
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
