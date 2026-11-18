package eu.nbys.freightfever.world;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Per-dimension storage of freight platform zone inventories, keyed by the
 * canonical zone corner string.
 */
public class FreightPlatformData extends SavedData {
    private static final String NAME = "freightfever_freight_platforms";

    private final ServerLevel level;
    private final Map<String, ZoneInventory> zones = new HashMap<>();

    public FreightPlatformData(ServerLevel level) {
        this.level = level;
    }

    public static FreightPlatformData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(() -> new FreightPlatformData(level),
                        (tag, registries) -> load(level, tag, registries), null),
                NAME);
    }

    private static FreightPlatformData load(ServerLevel level, CompoundTag tag, HolderLookup.Provider registries) {
        FreightPlatformData data = new FreightPlatformData(level);
        CompoundTag all = tag.getCompound("zones");
        for (String key : all.getAllKeys()) {
            ZoneInventory inventory = new ZoneInventory(level, key);
            ListTag list = all.getList(key, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                ItemStack stack = ItemStack.parse(registries, list.getCompound(i)).orElse(ItemStack.EMPTY);
                inventory.rawAdd(stack);
            }
            data.zones.put(key, inventory);
        }
        return data;
    }

    public static ZoneInventory zoneInventory(ServerLevel level, FreightZones.Zone zone) {
        FreightPlatformData data = get(level);
        return data.zones.computeIfAbsent(zone.key(), key -> new ZoneInventory(level, key));
    }

    /** Drops leftover zone items in the world and forgets the zone. */
    public static void disband(ServerLevel level, String key, BlockPos dropAt) {
        FreightPlatformData data = get(level);
        ZoneInventory inventory = data.zones.remove(key);
        if (inventory == null) return;
        for (ItemStack stack : inventory.contents()) {
            if (!stack.isEmpty()) {
                ItemEntity drop = new ItemEntity(level,
                        dropAt.getX() + 0.5, dropAt.getY() + 1.0, dropAt.getZ() + 0.5, stack);
                level.addFreshEntity(drop);
            }
        }
        data.setDirty();
    }

    public void touch() {
        // marker for readability; SavedData#setDirty already exposed
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag all = new CompoundTag();
        for (Map.Entry<String, ZoneInventory> entry : zones.entrySet()) {
            ListTag list = new ListTag();
            for (ItemStack stack : entry.getValue().contents()) {
                if (!stack.isEmpty()) {
                    list.add(stack.save(registries));
                }
            }
            all.put(entry.getKey(), list);
        }
        tag.put("zones", all);
        return tag;
    }
}
