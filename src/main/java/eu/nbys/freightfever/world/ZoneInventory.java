package eu.nbys.freightfever.world;

import java.util.ArrayList;
import java.util.List;

import eu.nbys.freightfever.Config;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/**
 * Shared inventory of one freight platform zone, stored per dimension in
 * SavedData. Capacity is dynamic: itemsPerPlatformBlock times the number of
 * platform blocks currently inside the zone. The total item count is enforced
 * on every insertion, merging stacks and opening new virtual slots as needed.
 */
public class ZoneInventory implements Container {
    private final ServerLevel level;
    private final String key;
    private final NonNullList<ItemStack> stacks = NonNullList.create();
    private FreightZones.Zone zone;

    public ZoneInventory(ServerLevel level, String key) {
        this.level = level;
        this.key = key;
    }

    public String key() {
        return key;
    }

    private FreightZones.Zone zone() {
        if (zone == null) zone = FreightZones.Zone.fromKey(key);
        return zone;
    }

    /** Maximum number of items this zone can hold right now. */
    public int capacity() {
        FreightZones.Zone bounds = zone();
        if (bounds == null) return 0;
        int blocks = Math.max(0, FreightZones.countPlatforms(level, bounds));
        int perBlock = Math.max(1, Config.ITEMS_PER_PLATFORM_BLOCK.get());
        return blocks * perBlock;
    }

    public int totalItems() {
        int total = 0;
        for (ItemStack stack : stacks) total += stack.getCount();
        return total;
    }

    /** Fill ratio in 0..1, used by the client cargo rendering. */
    public float fill() {
        int cap = capacity();
        if (cap <= 0) return 0.0f;
        return Math.min(1.0f, totalItems() / (float) cap);
    }

    /** Capacity-aware insertion; returns the remainder that did not fit. */
    public ItemStack addItem(ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        int free = capacity() - totalItems();
        if (free <= 0) return stack.copy();
        int toAdd = Math.min(stack.getCount(), free);
        ItemStack remaining = stack.copy();
        // merge into existing stacks first
        for (ItemStack existing : stacks) {
            if (remaining.isEmpty() || toAdd <= 0) break;
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, remaining)) {
                int room = Math.min(existing.getMaxStackSize(), 64) - existing.getCount();
                int move = Math.min(Math.min(room, remaining.getCount()), toAdd);
                if (move > 0) {
                    existing.grow(move);
                    remaining.shrink(move);
                    toAdd -= move;
                }
            }
        }
        while (toAdd > 0 && !remaining.isEmpty()) {
            int move = Math.min(Math.min(remaining.getMaxStackSize(), 64), toAdd);
            ItemStack fresh = remaining.copy();
            fresh.setCount(move);
            stacks.add(fresh);
            remaining.shrink(move);
            toAdd -= move;
        }
        if (remaining.getCount() != stack.getCount()) setChanged();
        return remaining;
    }

    /** Simulated counterpart of addItem: no mutation, returns the remainder. */
    public ItemStack simulateAdd(ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        int free = capacity() - totalItems();
        if (free <= 0) return stack.copy();
        int accepted = Math.min(stack.getCount(), free);
        ItemStack remainder = stack.copy();
        remainder.shrink(accepted);
        return remainder;
    }

    /** Raw slot access used by the SavedData loader. Bypasses capacity. */
    public void rawAdd(ItemStack stack) {
        if (!stack.isEmpty()) stacks.add(stack);
    }

    /** Registry id of the most stacked item, used by the client to pick a cargo texture. */
    public String dominantItemKey() {
        ItemStack best = null;
        int bestCount = -1;
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            if (stack.getCount() > bestCount) {
                bestCount = stack.getCount();
                best = stack;
            }
        }
        return best == null ? null : BuiltInRegistries.ITEM.getKey(best.getItem()).toString();
    }

    public List<ItemStack> contents() {
        return new ArrayList<>(stacks);
    }

    private void changed() {
        FreightPlatformData.get(level).setDirty();
    }

    // --- Container ---

    @Override
    public int getContainerSize() {
        return stacks.size();
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot < 0 || slot >= stacks.size()) return ItemStack.EMPTY;
        return stacks.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot < 0 || slot >= stacks.size()) return ItemStack.EMPTY;
        ItemStack taken = stacks.get(slot).split(amount);
        if (taken.isEmpty()) return ItemStack.EMPTY;
        if (stacks.get(slot).isEmpty()) stacks.remove(slot);
        changed();
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot < 0 || slot >= stacks.size()) return ItemStack.EMPTY;
        ItemStack stack = stacks.remove(slot);
        changed();
        return stack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        // slot index beyond the list means append (industry put-back path)
        stacks.add(stack);
        changed();
    }

    @Override
    public void clearContent() {
        stacks.clear();
        changed();
    }

    @Override
    public boolean isEmpty() {
        return stacks.isEmpty();
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void setChanged() {
        changed();
    }
}
