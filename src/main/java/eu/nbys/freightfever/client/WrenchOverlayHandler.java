package eu.nbys.freightfever.client;

import java.util.HashSet;
import java.util.Set;

import org.joml.Vector3f;

import eu.nbys.freightfever.FreightFever;
import eu.nbys.freightfever.block.entity.ConsumerBlockEntity;
import eu.nbys.freightfever.block.entity.IndustryBlockEntity;
import eu.nbys.freightfever.block.entity.PlatformBlockEntity;
import eu.nbys.freightfever.world.FreightZones;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** While the player holds the wrench, draws particle lines between platforms and their owners. */
public final class WrenchOverlayHandler {
    private static final int RADIUS = 16;
    private static final int EVERY_TICKS = 5;
    private static final Vector3f OUT_COLOR = new Vector3f(1.0f, 0.55f, 0.15f); // production output
    private static final Vector3f IN_COLOR = new Vector3f(0.25f, 0.75f, 1.0f); // input consumption
    private static final Vector3f INDUSTRY_RANGE_COLOR = new Vector3f(0.8f, 0.4f, 1.0f); // violet: industry scan radius
    private static final Vector3f CONSUMER_RANGE_COLOR = new Vector3f(0.4f, 0.9f, 0.3f); // lime: consumer scan radius
    private static final Vector3f ZONE_COLOR = new Vector3f(0.9f, 0.75f, 1.0f); // pale violet: freight platform zone frame
    private static final Vector3f INERT_COLOR = new Vector3f(1.0f, 0.2f, 0.2f); // red: platform outside any zone

    private WrenchOverlayHandler() {}

    public static boolean isHoldingWrench() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return false;
        return player.getMainHandItem().is(FreightFever.WRENCH_ITEM.get())
                || player.getOffhandItem().is(FreightFever.WRENCH_ITEM.get());
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (mc.level.getGameTime() % EVERY_TICKS != 0) return;
        if (!isHoldingWrench()) return;
        BlockPos center = mc.player.blockPosition();
        Set<String> seenZones = new HashSet<>();
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int y = -RADIUS; y <= RADIUS; y++) {
                for (int z = -RADIUS; z <= RADIUS; z++) {
                    BlockPos p = center.offset(x, y, z);
                    BlockEntity be = mc.level.getBlockEntity(p);
                    if (be instanceof PlatformBlockEntity platform) {
                        FreightZones.Zone zone = platform.getClientZone();
                        if (zone != null) {
                            if (seenZones.add(zone.key())) {
                                drawZoneFrame(mc, zone, ZONE_COLOR);
                            }
                        } else {
                            mc.level.addParticle(new DustParticleOptions(INERT_COLOR, 1.2f),
                                    p.getX() + 0.5, p.getY() + 1.3, p.getZ() + 0.5, 0.0, 0.01, 0.0);
                        }
                        BlockPos owner = platform.getLinkOwner();
                        if (owner == null) continue;
                        Vector3f color = "in".equals(platform.getLinkRole()) ? IN_COLOR : OUT_COLOR;
                        drawLink(mc, p, owner, color);
                    } else if (be instanceof IndustryBlockEntity industry) {
                        drawRangeCircle(mc, industry.getBlockPos(), IndustryBlockEntity.scanRadius(), INDUSTRY_RANGE_COLOR);
                    } else if (be instanceof ConsumerBlockEntity consumer) {
                        drawRangeCircle(mc, consumer.getBlockPos(), ConsumerBlockEntity.scanRadius(), CONSUMER_RANGE_COLOR);
                    }
                }
            }
        }
    }

    /** Draws a flat particle ring of the given block scan radius, one particle per world unit of circumference. */
    private static void drawRangeCircle(Minecraft mc, BlockPos center, int radius, Vector3f color) {
        if (mc.level == null) return;
        int steps = Math.max(16, (int) (2 * Math.PI * radius));
        for (int s = 0; s < steps; s++) {
            double theta = 2 * Math.PI * s / steps;
            double px = center.getX() + 0.5 + radius * Math.cos(theta);
            double pz = center.getZ() + 0.5 + radius * Math.sin(theta);
            mc.level.addParticle(new DustParticleOptions(color, 1.0f), px, center.getY() + 0.5, pz, 0.0, 0.002, 0.0);
        }
    }

    /** Draws the interior rectangle of a freight platform zone, just above post height. */
    private static void drawZoneFrame(Minecraft mc, FreightZones.Zone zone, Vector3f color) {
        if (mc.level == null) return;
        double y = zone.min().getY() + 1.05;
        double x0 = zone.min().getX() + 1.0; // first interior column
        double x1 = zone.max().getX();
        double z0 = zone.min().getZ() + 1.0;
        double z1 = zone.max().getZ();
        for (double x = x0; x <= x1 + 0.001; x += 0.5) {
            mc.level.addParticle(new DustParticleOptions(color, 1.0f), x, y, z0, 0.0, 0.002, 0.0);
            mc.level.addParticle(new DustParticleOptions(color, 1.0f), x, y, z1, 0.0, 0.002, 0.0);
        }
        for (double z = z0 + 0.5; z <= z1 - 0.5 + 0.001; z += 0.5) {
            mc.level.addParticle(new DustParticleOptions(color, 1.0f), x0, y, z, 0.0, 0.002, 0.0);
            mc.level.addParticle(new DustParticleOptions(color, 1.0f), x1, y, z, 0.0, 0.002, 0.0);
        }
    }

    private static void drawLink(Minecraft mc, BlockPos platformPos, BlockPos ownerPos, Vector3f color) {
        if (mc.level == null) return;
        Vec3 from = Vec3.atCenterOf(ownerPos);
        Vec3 to = Vec3.atCenterOf(platformPos);
        Vec3 dir = to.subtract(from);
        double length = dir.length();
        if (length < 0.1) return;
        Vec3 step = dir.normalize().scale(0.6);
        Vec3 cur = from;
        for (double d = 0.0; d < length; d += 0.6) {
            mc.level.addParticle(new DustParticleOptions(color, 0.7f), cur.x, cur.y, cur.z, 0.0, 0.005, 0.0);
            cur = cur.add(step);
        }
    }
}
