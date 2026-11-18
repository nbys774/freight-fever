package eu.nbys.freightfever.block.entity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import eu.nbys.freightfever.FreightFever;
import eu.nbys.freightfever.block.ConsumerState;
import eu.nbys.freightfever.block.PlatformMode;
import eu.nbys.freightfever.data.Demand;
import eu.nbys.freightfever.data.FreightData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ConsumerBlockEntity extends BlockEntity {
    public static final int SCAN_RADIUS = 8;
    public static final int SCAN_INTERVAL = 100;
    public static final int DECAY_INTERVAL = 100;
    public static final double SATISFACTION_PER_RATE = 10.0;

    private ResourceLocation demandId = null;
    private final Map<Item, Double> satisfaction = new HashMap<>();
    private final Set<BlockPos> inPlatforms = new HashSet<>();
    private int scanCooldown = 0;
    private int decayCooldown = 0;

    public ConsumerBlockEntity(BlockPos pos, BlockState state) {
        super(FreightFever.CONSUMER_BE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ConsumerBlockEntity be) {
        be.tick(level, pos);
    }

    private void tick(Level level, BlockPos pos) {
        Demand demand = currentDemand();
        if (demand == null) return;

        if (--scanCooldown <= 0) {
            rescan(level, pos);
            scanCooldown = SCAN_INTERVAL;
        }
        if (--decayCooldown <= 0) {
            decay();
            decayCooldown = DECAY_INTERVAL;
        }

        consume(level, demand);
        updateBlockState(level, pos);
    }

    private Demand currentDemand() {
        Demand demand = demandId == null ? null : FreightData.getDemand(demandId);
        if (demand == null) {
            demand = FreightData.defaultDemand();
            if (demand != null) demandId = demand.id();
        }
        return demand;
    }

    private void rescan(Level level, BlockPos pos) {
        for (BlockPos p : inPlatforms) {
            if (level.getBlockEntity(p) instanceof PlatformBlockEntity platform) {
                platform.unlinkIfOwnedBy(pos);
            }
        }
        inPlatforms.clear();

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dy = -SCAN_RADIUS; dy <= SCAN_RADIUS; dy++) {
                for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                    cursor.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (level.getBlockEntity(cursor) instanceof PlatformBlockEntity platform
                            && platform.getMode() == PlatformMode.IN) {
                        if (platform.tryLink(pos, "in")) {
                            inPlatforms.add(cursor.immutable());
                        } else {
                            FreightFever.LOGGER.debug("Consumer at {} skipped platform at {} (linked to {})",
                                    pos, cursor, platform.getLinkOwner());
                        }
                    }
                }
            }
        }
    }

    private void consume(Level level, Demand demand) {
        for (Demand.DemandItem demandItem : demand.items()) {
            double sat = satisfaction.getOrDefault(demandItem.item(), 0.0);
            if (sat >= 100.0) continue;
            for (BlockPos p : inPlatforms) {
                if (sat >= 100.0) break;
                if (!level.hasChunkAt(p)) continue;
                if (!(level.getBlockEntity(p) instanceof PlatformBlockEntity platform)) continue;
                net.minecraft.world.Container container = platform.getContainer();
                for (int slot = 0; slot < container.getContainerSize() && sat < 100.0; slot++) {
                    ItemStack stack = container.getItem(slot);
                    if (stack.isEmpty() || stack.getItem() != demandItem.item()) continue;
                    container.removeItem(slot, 1);
                    sat = Math.min(100.0, sat + demandItem.rate() * SATISFACTION_PER_RATE);
                }
                satisfaction.put(demandItem.item(), sat);
            }
        }
    }

    private void decay() {
        for (Map.Entry<Item, Double> entry : satisfaction.entrySet()) {
            entry.setValue(Math.max(0.0, entry.getValue() - 1.0));
        }
    }

    private void updateBlockState(Level level, BlockPos pos) {
        ConsumerState current = getBlockState().getValue(eu.nbys.freightfever.block.ConsumerBlock.STATE);
        ConsumerState target = computeState();
        if (current != target) {
            level.setBlock(pos, getBlockState().setValue(eu.nbys.freightfever.block.ConsumerBlock.STATE, target), Block.UPDATE_ALL);
        }
    }

    private ConsumerState computeState() {
        if (satisfaction.isEmpty()) return ConsumerState.HUNGRY;
        double avg = satisfaction.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        if (avg >= 66.0) return ConsumerState.SATISFIED;
        if (avg >= 33.0) return ConsumerState.AVERAGE;
        return ConsumerState.HUNGRY;
    }

    public String describeSatisfaction() {
        if (satisfaction.isEmpty()) return "no demand loaded";
        StringBuilder sb = new StringBuilder("satisfaction: ");
        satisfaction.forEach((item, sat) -> sb.append(item.toString()).append('=').append(String.format("%.0f", sat)).append(' '));
        return sb.toString().trim();
    }

    public ResourceLocation getDemandId() {
        return demandId;
    }

    public void setDemandId(ResourceLocation demandId) {
        this.demandId = demandId;
        this.satisfaction.clear();
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (demandId != null) tag.putString("DemandId", demandId.toString());
        CompoundTag satTag = new CompoundTag();
        satisfaction.forEach((item, sat) -> satTag.putDouble(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString(), sat));
        tag.put("Satisfaction", satTag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("DemandId")) demandId = ResourceLocation.parse(tag.getString("DemandId"));
        CompoundTag satTag = tag.getCompound("Satisfaction");
        for (String key : satTag.getAllKeys()) {
            Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse(key));
            satisfaction.put(item, satTag.getDouble(key));
        }
    }
}
