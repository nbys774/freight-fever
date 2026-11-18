package eu.nbys.freightfever.world;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import eu.nbys.freightfever.Config;
import eu.nbys.freightfever.FreightFever;
import eu.nbys.freightfever.block.entity.PlatformBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Pure world queries for freight platform zones: a zone is the open rectangle
 * strictly between four same-height corner posts. Nothing is persisted: zone
 * identity is derived from the post blocks each time it is needed.
 */
public final class FreightZones {
    private FreightZones() {
    }

    /** Canonical zone: opposite corners (min/max) at the same Y. */
    public record Zone(BlockPos min, BlockPos max) {
        public String key() {
            return min.getX() + "," + min.getY() + "," + min.getZ() + "," + max.getX() + "," + max.getZ();
        }

        /** Parses a canonical 5-field key ("x1,y,z1,x2,z2"); null when unreadable. */
        public static Zone fromKey(String key) {
            try {
                String[] parts = key.split(",");
                if (parts.length != 5) return null;
                int y = Integer.parseInt(parts[1]);
                BlockPos min = new BlockPos(Integer.parseInt(parts[0]), y, Integer.parseInt(parts[2]));
                BlockPos max = new BlockPos(Integer.parseInt(parts[3]), y, Integer.parseInt(parts[4]));
                return new Zone(min, max);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    /**
     * Finds the smallest zone (in area) whose interior strictly contains pos,
     * or null. A zone needs four posts at the corners, all at pos Y, each side
     * between 2 and the configured maximum.
     */
    public static Zone findZone(Level level, BlockPos pos) {
        if (level == null || level.isClientSide()) return null;
        int max = Math.max(2, Config.PLATFORM_MAX_SIDE.get());
        int y = pos.getY();

        List<BlockPos> posts = new ArrayList<>();
        BlockPos min = new BlockPos(pos.getX() - max - 1, y, pos.getZ() - max - 1);
        BlockPos maxPos = new BlockPos(pos.getX() + max + 1, y, pos.getZ() + max + 1);
        for (BlockPos p : BlockPos.betweenClosed(min, maxPos)) {
            if (level.getBlockState(p).is(FreightFever.POST.get())) {
                posts.add(p.immutable());
            }
        }
        if (posts.size() < 4) return null;

        Set<Long> set = new HashSet<>();
        for (BlockPos p : posts) set.add(p.asLong());

        Zone best = null;
        int bestArea = Integer.MAX_VALUE;
        for (BlockPos a : posts) {
            if (a.getX() >= pos.getX() || a.getZ() >= pos.getZ()) continue; // min corner candidates
            for (BlockPos b : posts) {
                if (b.getX() <= pos.getX() || b.getZ() <= pos.getZ()) continue; // max corner candidates
                int w = b.getX() - a.getX();
                int h = b.getZ() - a.getZ();
                if (w < 2 || h < 2 || w > max || h > max) continue;
                BlockPos c = new BlockPos(a.getX(), y, b.getZ());
                BlockPos d = new BlockPos(b.getX(), y, a.getZ());
                if (!set.contains(c.asLong()) || !set.contains(d.asLong())) continue;
                int area = w * h;
                if (area < bestArea) {
                    bestArea = area;
                    best = new Zone(a.immutable(), b.immutable());
                }
            }
        }
        return best;
    }

    /** Number of platform blocks strictly inside the zone (capacity driver). */
    public static int countPlatforms(Level level, Zone zone) {
        if (zone == null) return 0;
        int count = 0;
        for (int x = zone.min().getX() + 1; x < zone.max().getX(); x++) {
            for (int z = zone.min().getZ() + 1; z < zone.max().getZ(); z++) {
                if (level.getBlockState(new BlockPos(x, zone.min().getY(), z)).is(FreightFever.PLATFORM.get())) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Called when a post changes: every platform on the same Y layer within
     * reach may belong to a zone adjacent to that post, so they all refresh.
     */
    public static void pokePlatformsAround(Level level, BlockPos changed) {
        if (level == null || level.isClientSide()) return;
        int max = Math.max(2, Config.PLATFORM_MAX_SIDE.get()) + 1;
        int y = changed.getY();
        BlockPos min = new BlockPos(changed.getX() - max, y, changed.getZ() - max);
        BlockPos maxPos = new BlockPos(changed.getX() + max, y, changed.getZ() + max);
        for (BlockPos p : BlockPos.betweenClosed(min, maxPos)) {
            if (level.getBlockState(p).is(FreightFever.PLATFORM.get())
                    && level.getBlockEntity(p) instanceof PlatformBlockEntity be) {
                be.refreshZone();
            }
        }
    }
}
