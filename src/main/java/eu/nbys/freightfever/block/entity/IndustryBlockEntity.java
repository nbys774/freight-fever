package eu.nbys.freightfever.block.entity;

import java.util.HashSet;
import java.util.Set;
import eu.nbys.freightfever.FreightFever;
import eu.nbys.freightfever.block.PlatformMode;
import eu.nbys.freightfever.data.FreightData;
import eu.nbys.freightfever.data.IndustryRecipe;
import eu.nbys.freightfever.data.ItemAmount;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

public class IndustryBlockEntity extends BlockEntity {
    public static final int INPUT_SLOTS = 9;
    public static final int OUTPUT_SLOTS = 9;
    public static final int SCAN_RADIUS = 16;
    public static final int SCAN_INTERVAL = 100; // ticks
    public static final int TRANSFER_INTERVAL = 20; // ticks
    public static final int TRANSFER_LIMIT = 8; // items per cycle

    private final ItemStackHandler input = new ItemStackHandler(INPUT_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final ItemStackHandler output = new ItemStackHandler(OUTPUT_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private ResourceLocation recipeId = null;
    private double productionFactor = 1.0;
    private int progress = 0;
    private int scanCooldown = 0;
    private int transferCooldown = 0;
    private boolean ticked = false;
    private final Set<BlockPos> outPlatforms = new HashSet<>();
    private final Set<BlockPos> inPlatforms = new HashSet<>();

    public IndustryBlockEntity(BlockPos pos, BlockState state) {
        super(FreightFever.INDUSTRY_BE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, IndustryBlockEntity be) {
        be.tick(level, pos);
    }

    private void tick(Level level, BlockPos pos) {
        IndustryRecipe recipe = currentRecipe();
        if (!ticked) {
            ticked = true;
            FreightFever.LOGGER.info("Industry {} ticker online (recipe {}, factor x{}, progress {})", worldPosition,
                    recipe == null ? "none" : recipe.id().toString(), String.format("%.2f", productionFactor), progress);
        }

        if (--scanCooldown <= 0) {
            rescan(level, pos);
            scanCooldown = SCAN_INTERVAL;
        }
        if (--transferCooldown <= 0) {
            transfer(level, pos, recipe);
            transferCooldown = TRANSFER_INTERVAL;
        }

        if (recipe == null) return;
        if (!canProcess(recipe)) return;
        progress++;
        int effectiveTime = Math.max(1, (int) Math.ceil(recipe.processTimeTicks() / productionFactor));
        if (progress >= effectiveTime) {
            progress = 0;
            consumeInputs(recipe);
            for (ItemAmount out : recipe.outputs()) {
                ItemStack stack = new ItemStack(out.item(), out.amount());
                ItemStack remainder = insertInto(output, stack, false);
                if (!remainder.isEmpty() && level != null) {
                    // output buffer full: drop progress to avoid voiding items
                    progress = effectiveTime;
                }
            }
            FreightFever.LOGGER.info("Industry {} completed a production cycle (recipe {})", worldPosition, recipe.id());
        }
    }

    private IndustryRecipe currentRecipe() {
        IndustryRecipe recipe = recipeId == null ? null : FreightData.getIndustry(recipeId);
        if (recipe == null) {
            recipe = FreightData.defaultIndustry();
            if (recipe != null) recipeId = recipe.id();
        }
        return recipe;
    }

    private boolean canProcess(IndustryRecipe recipe) {
        if (recipe.outputOnly()) {
            return fitsInOutput(recipe);
        }
        for (ItemAmount in : recipe.inputs()) {
            if (countIn(input, in.item()) < in.amount()) return false;
        }
        return fitsInOutput(recipe);
    }

    private boolean fitsInOutput(IndustryRecipe recipe) {
        for (ItemAmount out : recipe.outputs()) {
            if (insertInto(output, new ItemStack(out.item(), out.amount()), true).getCount() > 0) return false;
        }
        return true;
    }

    private void consumeInputs(IndustryRecipe recipe) {
        for (ItemAmount in : recipe.inputs()) {
            extractFrom(input, in.item(), in.amount());
        }
    }

    private void rescan(Level level, BlockPos pos) {
        // release previous links owned by us
        for (BlockPos p : outPlatforms) unlinkIfPresent(level, p);
        for (BlockPos p : inPlatforms) unlinkIfPresent(level, p);
        outPlatforms.clear();
        inPlatforms.clear();

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dy = -SCAN_RADIUS; dy <= SCAN_RADIUS; dy++) {
                for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                    cursor.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (level.getBlockEntity(cursor) instanceof PlatformBlockEntity platform) {
                        PlatformMode mode = platform.getMode();
                        String role = mode == PlatformMode.OUT ? "out" : "in";
                        if (platform.tryLink(pos, role)) {
                            if (mode == PlatformMode.OUT) outPlatforms.add(cursor.immutable());
                            else inPlatforms.add(cursor.immutable());
                        } else {
                            FreightFever.LOGGER.debug("Industry at {} skipped platform at {} (linked to {})",
                                    pos, cursor, platform.getLinkOwner());
                        }
                    }
                }
            }
        }
        FreightFever.LOGGER.info("Industry {} scan linked {} OUT and {} IN platforms", worldPosition, outPlatforms.size(), inPlatforms.size());
    }

    private void unlinkIfPresent(Level level, BlockPos p) {
        if (level.getBlockEntity(p) instanceof PlatformBlockEntity platform) {
            platform.unlinkIfOwnedBy(getBlockPos());
        }
    }

    private void transfer(Level level, BlockPos pos, IndustryRecipe recipe) {
        // push outputs to OUT platforms
        int pushed = 0;
        for (BlockPos p : outPlatforms) {
            if (!level.hasChunkAt(p)) continue; // silent skip
            if (!(level.getBlockEntity(p) instanceof PlatformBlockEntity platform)) continue;
            int moved = moveItems(output, platform.getContainer(), TRANSFER_LIMIT);
            if (moved > 0) {
                FreightFever.LOGGER.info("Industry {} pushed {} items to OUT platform {}", worldPosition, moved, p);
                pushed += moved;
            }
        }
        if (pushed == 0 && !output.getStackInSlot(0).isEmpty()) {
            FreightFever.LOGGER.debug("Industry {} has buffered output but pushed nothing ({} OUT platforms)", worldPosition, outPlatforms.size());
        }
        // pull inputs from IN platforms
        if (recipe != null && !recipe.outputOnly()) {
            for (BlockPos p : inPlatforms) {
                if (!level.hasChunkAt(p)) continue;
                if (!(level.getBlockEntity(p) instanceof PlatformBlockEntity platform)) continue;
                int budget = TRANSFER_LIMIT;
                for (ItemAmount in : recipe.inputs()) {
                    if (budget <= 0) break;
                    budget -= moveMatching(platform.getContainer(), input, in.item(), budget);
                }
            }
        }
    }

    private static int moveItems(IItemHandler from, net.minecraft.world.Container to, int limit) {
        int moved = 0;
        for (int slot = 0; slot < from.getSlots() && moved < limit; slot++) {
            ItemStack stack = from.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            int before = stack.getCount();
            ItemStack extracted = from.extractItem(slot, Math.min(before, limit - moved), false);
            if (extracted.isEmpty()) continue;
            ItemStack remainder = insertIntoContainer(to, extracted);
            int accepted = extracted.getCount() - remainder.getCount();
            moved += accepted;
            if (!remainder.isEmpty()) {
                // put back what did not fit
                ItemStack leftover = from.insertItem(slot, remainder, false);
                if (!leftover.isEmpty()) {
                    FreightFever.LOGGER.warn("Industry output lost items at {}", slot);
                }
            }
        }
        return moved;
    }

    private static int moveMatching(net.minecraft.world.Container from, IItemHandler to, net.minecraft.world.item.Item item, int limit) {
        int moved = 0;
        for (int slot = 0; slot < from.getContainerSize() && moved < limit; slot++) {
            ItemStack stack = from.getItem(slot);
            if (stack.isEmpty() || stack.getItem() != item) continue;
            ItemStack extracted = from.removeItem(slot, Math.min(stack.getCount(), limit - moved));
            if (extracted.isEmpty()) continue;
            ItemStack remainder = insertInto(to, extracted, false);
            int accepted = extracted.getCount() - remainder.getCount();
            moved += accepted;
            if (!remainder.isEmpty()) {
                from.setItem(slot, remainder); // simplistic: slot was emptied by removeItem
            }
        }
        return moved;
    }

    private static ItemStack insertIntoContainer(net.minecraft.world.Container container, ItemStack stack) {
        ItemStack remainder = stack.copy();
        for (int slot = 0; slot < container.getContainerSize() && !remainder.isEmpty(); slot++) {
            ItemStack existing = container.getItem(slot);
            if (existing.isEmpty()) {
                int limit = Math.min(container.getMaxStackSize(), remainder.getMaxStackSize());
                int move = Math.min(remainder.getCount(), limit);
                ItemStack placed = remainder.copy();
                placed.setCount(move);
                container.setItem(slot, placed);
                remainder.shrink(move);
            } else if (ItemStack.isSameItemSameComponents(existing, remainder)) {
                int limit = Math.min(container.getMaxStackSize(), remainder.getMaxStackSize());
                int move = Math.min(remainder.getCount(), limit - existing.getCount());
                if (move > 0) {
                    existing.grow(move);
                    container.setItem(slot, existing);
                    remainder.shrink(move);
                }
            }
        }
        return remainder;
    }

    private static int countIn(IItemHandler handler, net.minecraft.world.item.Item item) {
        int count = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.getItem() == item) count += stack.getCount();
        }
        return count;
    }

    private static void extractFrom(IItemHandler handler, net.minecraft.world.item.Item item, int amount) {
        int remaining = amount;
        for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.getItem() == item) {
                ItemStack taken = handler.extractItem(slot, remaining, false);
                remaining -= taken.getCount();
            }
        }
    }

    private static ItemStack insertInto(IItemHandler handler, ItemStack stack, boolean simulate) {
        ItemStack remainder = stack;
        for (int slot = 0; slot < handler.getSlots() && !remainder.isEmpty(); slot++) {
            remainder = handler.insertItem(slot, remainder, simulate);
        }
        return remainder;
    }

    public void rollProductionFactor() {
        this.productionFactor = 0.5 + level.getRandom().nextDouble() * 1.5;
        setChanged();
        FreightFever.LOGGER.info("Industry at {} rolled production factor x{}", worldPosition, String.format("%.2f", productionFactor));
    }

    public double getProductionFactor() {
        return productionFactor;
    }

    public ResourceLocation getRecipeId() {
        return recipeId;
    }

    public void setRecipeId(ResourceLocation recipeId) {
        this.recipeId = recipeId;
        this.progress = 0;
        setChanged();
    }

    /** Handler for automation: inputs insertable, outputs extractable. */
    public IItemHandler getItemHandler() {
        return new IItemHandler() {
            @Override
            public int getSlots() {
                return INPUT_SLOTS + OUTPUT_SLOTS;
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                return slot < INPUT_SLOTS ? input.getStackInSlot(slot) : output.getStackInSlot(slot - INPUT_SLOTS);
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (slot >= INPUT_SLOTS) return stack; // output side: no insertion
                return input.insertItem(slot, stack, simulate);
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                if (slot < INPUT_SLOTS) return ItemStack.EMPTY; // input side: no extraction
                return output.extractItem(slot - INPUT_SLOTS, amount, simulate);
            }

            @Override
            public int getSlotLimit(int slot) {
                return 64;
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return slot < INPUT_SLOTS;
            }
        };
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Input", input.serializeNBT(registries));
        tag.put("Output", output.serializeNBT(registries));
        if (recipeId != null) tag.putString("RecipeId", recipeId.toString());
        tag.putDouble("Factor", productionFactor);
        tag.putInt("Progress", progress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        input.deserializeNBT(registries, tag.getCompound("Input"));
        output.deserializeNBT(registries, tag.getCompound("Output"));
        if (tag.contains("RecipeId")) recipeId = ResourceLocation.parse(tag.getString("RecipeId"));
        productionFactor = tag.getDouble("Factor");
        if (productionFactor <= 0) productionFactor = 1.0;
        progress = tag.getInt("Progress");
    }
}
