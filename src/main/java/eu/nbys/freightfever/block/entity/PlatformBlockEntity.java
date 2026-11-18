package eu.nbys.freightfever.block.entity;

import eu.nbys.freightfever.Config;
import eu.nbys.freightfever.FreightFever;
import eu.nbys.freightfever.block.PlatformBlock;
import eu.nbys.freightfever.block.PlatformMode;
import eu.nbys.freightfever.world.FreightPlatformData;
import eu.nbys.freightfever.world.FreightZones;
import eu.nbys.freightfever.world.ZoneInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Freight platform block entity. A platform only stores freight when it sits
 * strictly inside a freight platform zone framed by four corner posts; all
 * platforms of one zone share a single inventory whose capacity scales with
 * the platform block count. Outside a zone the platform is inert (no
 * capability, no linking). The legacy per-block container is migrated into
 * the shared zone inventory the first time a zone becomes active.
 */
public class PlatformBlockEntity extends BlockEntity {
    public static final int SLOTS = 18;

    /** Inert container view for platforms outside any zone. */
    private static final Container EMPTY_CONTAINER = new SimpleContainer(0);

    /** Legacy per-block slots (pre-0.3.0 worlds); migrated into zones. */
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

    /** Active zone, server cache (null when not framed by posts). */
    private FreightZones.Zone zone = null;
    /** Canonical zone key of the last known active zone (persisted). */
    private String zoneKey = null;
    /** Server cache + client mirror: platform is inside a valid zone. */
    private boolean zoneActive = false;
    /** Server feed + client mirror of the zone fill ratio (0..1). */
    private float zoneFill = 0.0f;
    private int zoneRefreshCooldown = 0;

    public PlatformBlockEntity(BlockPos pos, BlockState state) {
        super(FreightFever.PLATFORM_BE.get(), pos, state);
    }

    // --- zone lifecycle ---

    public static void serverTick(Level level, BlockPos pos, BlockState state, PlatformBlockEntity be) {
        be.tickServer(level);
    }

    private void tickServer(Level level) {
        if (--zoneRefreshCooldown <= 0) {
            zoneRefreshCooldown = 20;
            refreshZone();
        }
        if (zoneActive && level.getGameTime() % 10 == 0) {
            float fill = currentFill();
            if (Math.abs(fill - zoneFill) > 0.02f) {
                zoneFill = fill;
                syncToClient();
            }
        }
    }

    /** Recomputes zone membership. Defers while the surrounding chunks are not loaded. */
    public void refreshZone() {
        if (level == null || level.isClientSide()) return;
        int reach = Math.max(2, Config.PLATFORM_MAX_SIDE.get()) + 2;
        if (!level.hasChunksAt(worldPosition.offset(-reach, 0, -reach), worldPosition.offset(reach, 0, reach))) {
            return; // posts not loaded yet: decide later, never disband blind
        }
        FreightZones.Zone found = FreightZones.findZone(level, worldPosition);
        if (found == null) {
            if (zoneKey != null) {
                // zone disband: drop the leftover shared items once, then go inert
                FreightPlatformData.disband((ServerLevel) level, zoneKey, worldPosition);
                zoneKey = null;
                zone = null;
                zoneActive = false;
                zoneFill = 0.0f;
                setChanged();
                syncToClient();
            }
            return;
        }
        String newKey = found.key();
        if (newKey.equals(zoneKey) && zoneActive) {
            zone = found;
            return;
        }
        if (zoneKey != null && !newKey.equals(zoneKey)) {
            // moved to another zone: do not carry the old zone contents over
            FreightPlatformData.disband((ServerLevel) level, zoneKey, worldPosition);
        }
        zone = found;
        zoneKey = newKey;
        zoneActive = true;
        zoneFill = currentFill();
        migrateLegacyItems();
        setChanged();
        syncToClient();
    }

    /** Moves legacy per-block items into the shared zone inventory, dropping overflow. */
    private void migrateLegacyItems() {
        if (container.isEmpty() || zone == null || !(level instanceof ServerLevel serverLevel)) return;
        ZoneInventory inventory = FreightPlatformData.zoneInventory(serverLevel, zone);
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            ItemStack rest = inventory.addItem(stack);
            if (!rest.isEmpty()) {
                level.addFreshEntity(new ItemEntity(level,
                        worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, rest));
            }
            container.setItem(i, ItemStack.EMPTY);
        }
    }

    private float currentFill() {
        if (zoneActive && zone != null && level instanceof ServerLevel serverLevel) {
            return FreightPlatformData.zoneInventory(serverLevel, zone).fill();
        }
        return 0.0f;
    }

    public boolean isZoneActive() {
        return zoneActive;
    }

    public float getZoneFill() {
        return zoneFill;
    }

    // --- linking (unchanged semantics, now zone-gated) ---

    /** Debug logging of capability queries (hoppers etc.), throttled to once per second. */
    private void logHandler(String message) {
        if (level == null) return;
        long now = level.getGameTime();
        if (now - lastHandlerLog < 20) return;
        lastHandlerLog = now;
        FreightFever.LOGGER.debug("Platform at {} capability: {}", worldPosition, message);
    }

    /** Shared inventory of the zone when framed, empty view otherwise. */
    public Container getContainer() {
        if (zoneActive && zone != null && level instanceof ServerLevel serverLevel) {
            return FreightPlatformData.zoneInventory(serverLevel, zone);
        }
        return EMPTY_CONTAINER;
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

    /** Claims this platform for owner; false if inert or a live owner already claims it. */
    public boolean tryLink(BlockPos owner, String role) {
        if (!zoneActive) return false; // only framed platforms can be claimed
        if (linkOwner != null && !linkOwner.equals(owner)) {
            if (isOwnerAlive()) return false;
            FreightFever.LOGGER.debug("Platform at {} took over stale link to {}", worldPosition, linkOwner);
        }
        linkOwner = owner;
        linkRole = role;
        setChanged();
        syncToClient();
        return true;
    }

    /** Pushes the link state to clients for the wrench overlay. */
    private void syncToClient() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        if (linkOwner != null) {
            tag.putLong("LinkOwner", linkOwner.asLong());
            tag.putString("LinkRole", linkRole == null ? "" : linkRole);
        }
        tag.putBoolean("ZoneActive", zoneActive);
        tag.putFloat("ZoneFill", zoneFill);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
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
            syncToClient();
        }
    }

    public void onModeChanged() {
        // role is derived from mode; drop any link so the new role can be claimed fresh
        linkOwner = null;
        linkRole = null;
        setChanged();
        syncToClient();
    }

    public String getLinkRole() {
        return linkRole;
    }

    /** Mode-aware handler exposed to hoppers/funnels over the shared zone storage. */
    public IItemHandler createItemHandler() {
        if (!zoneActive || zone == null || !(level instanceof ServerLevel serverLevel)) {
            return EMPTY_HANDLER; // inert outside a zone
        }
        ZoneInventory inventory = FreightPlatformData.zoneInventory(serverLevel, zone);
        return new IItemHandler() {
            @Override
            public int getSlots() {
                return inventory.getContainerSize();
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                return inventory.getItem(slot);
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (getMode() == PlatformMode.OUT) return stack; // insertion refused
                if (simulate) return inventory.simulateAdd(stack);
                logHandler("insert into zone " + inventory.key() + ": " + stack.getCount() + "x" + stack.getItem());
                return inventory.addItem(stack);
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                if (getMode() == PlatformMode.IN) return ItemStack.EMPTY; // extraction refused via capability
                if (simulate) {
                    ItemStack stack = inventory.getItem(slot);
                    if (stack.isEmpty() || amount <= 0) return ItemStack.EMPTY;
                    ItemStack peek = stack.copy();
                    peek.setCount(Math.min(stack.getCount(), amount));
                    return peek;
                }
                ItemStack taken = inventory.removeItem(slot, amount);
                if (!taken.isEmpty()) {
                    logHandler("extract from zone " + inventory.key() + ": " + taken.getCount() + "x" + taken.getItem());
                }
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

    private static final IItemHandler EMPTY_HANDLER = new IItemHandler() {
        @Override
        public int getSlots() {
            return 0;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    };

    // --- persistence ---

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Items", container.createTag(registries));
        if (linkOwner != null) {
            tag.putLong("LinkOwner", linkOwner.asLong());
            tag.putString("LinkRole", linkRole == null ? "" : linkRole);
        }
        if (zoneKey != null) {
            tag.putString("ZoneKey", zoneKey);
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
        if (tag.contains("ZoneKey")) {
            zoneKey = tag.getString("ZoneKey");
        } else {
            zoneKey = null;
        }
        if (tag.contains("ZoneActive")) {
            zoneActive = tag.getBoolean("ZoneActive"); // update-tag path (client mirror)
        }
        if (tag.contains("ZoneFill")) {
            zoneFill = tag.getFloat("ZoneFill");
        }
    }
}
