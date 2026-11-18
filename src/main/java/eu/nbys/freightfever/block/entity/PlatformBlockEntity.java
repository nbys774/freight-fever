package eu.nbys.freightfever.block.entity;

import eu.nbys.freightfever.FreightFever;
import eu.nbys.freightfever.block.PlatformBlock;
import eu.nbys.freightfever.block.PlatformMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

public class PlatformBlockEntity extends BlockEntity {
    public static final int SLOTS = 18;

    private final SimpleContainer container = new SimpleContainer(SLOTS) {
        @Override
        public void setChanged() {
            PlatformBlockEntity.this.setChanged();
        }
    };

    private long lastHandlerLog = 0L;

    /** BlockPos of the Industry/Consumer this platform is linked to, null if free. */
    private BlockPos linkOwner = null;
    /** "in" or "out" role relative to the owner. */
    private String linkRole = null;

    public PlatformBlockEntity(BlockPos pos, BlockState state) {
        super(FreightFever.PLATFORM_BE.get(), pos, state);
    }

    /** Debug logging of capability queries (hoppers etc.), throttled to once per second. */
    private void logHandler(String message) {
        if (level == null) return;
        long now = level.getGameTime();
        if (now - lastHandlerLog < 20) return;
        lastHandlerLog = now;
        FreightFever.LOGGER.debug("Platform at {} capability: {}", worldPosition, message);
    }

    public Container getContainer() {
        return container;
    }

    public PlatformMode getMode() {
        return getBlockState().getValue(PlatformBlock.MODE);
    }

    public BlockPos getLinkOwner() {
        return linkOwner;
    }

    public boolean isLinkedTo(BlockPos owner) {
        return owner.equals(linkOwner);
    }

    /** Claims this platform for owner; returns false if a live owner already claims it. */
    public boolean tryLink(BlockPos owner, String role) {
        if (linkOwner != null && !linkOwner.equals(owner)) {
            if (isOwnerAlive()) return false;
            FreightFever.LOGGER.debug("Platform at {} took over stale link to {}", worldPosition, linkOwner);
        }
        linkOwner = owner;
        linkRole = role;
        setChanged();
        return true;
    }

    /** True if the linked position still holds an owning block entity.
     *  Unloaded chunks count as alive so live owners are never stolen from. */
    private boolean isOwnerAlive() {
        if (level == null || !level.hasChunkAt(linkOwner)) return true;
        BlockEntity ownerBe = level.getBlockEntity(linkOwner);
        return ownerBe instanceof IndustryBlockEntity || ownerBe instanceof ConsumerBlockEntity;
    }

    public void unlinkIfOwnedBy(BlockPos owner) {
        if (owner.equals(linkOwner)) {
            linkOwner = null;
            linkRole = null;
            setChanged();
        }
    }

    public void onModeChanged() {
        // role is derived from mode; drop any link so the new role can be claimed fresh
        linkOwner = null;
        linkRole = null;
        setChanged();
    }

    public String getLinkRole() {
        return linkRole;
    }

    /** Mode-aware handler exposed to hoppers/funnels: OUT = extract-only, IN = insert-only. */
    public IItemHandler createItemHandler() {
        return new IItemHandler() {
            @Override
            public int getSlots() {
                return SLOTS;
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                return container.getItem(slot);
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (getMode() == PlatformMode.OUT) return stack; // insertion refused
                logHandler("insert requested " + stack.getCount() + "x" + stack.getItem() + " (slot " + slot + ")");
                ItemStack existing = container.getItem(slot);
                if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, stack)) return stack;
                int limit = Math.min(getSlotLimit(slot), stack.getMaxStackSize());
                int canInsert = Math.min(stack.getCount(), limit - existing.getCount());
                if (canInsert <= 0) return stack;
                if (!simulate) {
                    ItemStack copy = stack.copy();
                    copy.setCount(existing.getCount() + canInsert);
                    container.setItem(slot, copy);
                }
                ItemStack remainder = stack.copy();
                remainder.shrink(canInsert);
                return remainder;
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                if (getMode() == PlatformMode.IN) return ItemStack.EMPTY; // extraction refused via capability
                if (simulate) {
                    // peek only: a simulated extraction must never mutate the container
                    ItemStack stack = container.getItem(slot);
                    if (stack.isEmpty() || amount <= 0) return ItemStack.EMPTY;
                    ItemStack preview = stack.copy();
                    preview.setCount(Math.min(stack.getCount(), amount));
                    return preview;
                }
                ItemStack taken = container.removeItem(slot, amount);
                logHandler("extract slot " + slot + " amount " + amount + " -> " + taken.getCount() + "x" + taken.getItem());
                return taken;
            }

            @Override
            public int getSlotLimit(int slot) {
                return 64;
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return getMode() == PlatformMode.IN;
            }
        };
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Items", container.createTag(registries));
        if (linkOwner != null) {
            tag.putLong("LinkOwner", linkOwner.asLong());
            tag.putString("LinkRole", linkRole == null ? "" : linkRole);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        container.fromTag(tag.getList("Items", CompoundTag.TAG_COMPOUND), registries);
        if (tag.contains("LinkOwner")) {
            linkOwner = BlockPos.of(tag.getLong("LinkOwner"));
            linkRole = tag.getString("LinkRole");
        } else {
            linkOwner = null;
            linkRole = null;
        }
    }
}
