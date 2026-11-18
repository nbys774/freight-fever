package eu.nbys.freightfever;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import eu.nbys.freightfever.block.ConsumerBlock;
import eu.nbys.freightfever.block.IndustryBlock;
import eu.nbys.freightfever.block.PlatformBlock;
import eu.nbys.freightfever.block.entity.ConsumerBlockEntity;
import eu.nbys.freightfever.block.entity.IndustryBlockEntity;
import eu.nbys.freightfever.block.entity.PlatformBlockEntity;
import eu.nbys.freightfever.data.DemandReloadListener;
import eu.nbys.freightfever.data.IndustryReloadListener;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(FreightFever.MODID)
public class FreightFever {
    public static final String MODID = "freightfever";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredBlock<IndustryBlock> INDUSTRY = BLOCKS.registerBlock("industry",
            props -> new IndustryBlock(props.mapColor(MapColor.METAL).strength(3.5f).sound(SoundType.METAL)));
    public static final DeferredItem<BlockItem> INDUSTRY_ITEM = ITEMS.registerSimpleBlockItem("industry", INDUSTRY);

    public static final DeferredBlock<PlatformBlock> PLATFORM = BLOCKS.registerBlock("platform",
            props -> new PlatformBlock(props.mapColor(MapColor.STONE).strength(2.0f).sound(SoundType.STONE)));
    public static final DeferredItem<BlockItem> PLATFORM_ITEM = ITEMS.registerSimpleBlockItem("platform", PLATFORM);

    public static final DeferredBlock<ConsumerBlock> CONSUMER = BLOCKS.registerBlock("consumer",
            props -> new ConsumerBlock(props.mapColor(MapColor.WOOD).strength(2.0f).sound(SoundType.WOOD)));
    public static final DeferredItem<BlockItem> CONSUMER_ITEM = ITEMS.registerSimpleBlockItem("consumer", CONSUMER);

    public static final DeferredItem<Item> STEEL_INGOT = ITEMS.registerSimpleItem("steel_ingot",
            new Item.Properties());

    public static final DeferredItem<Item> WRENCH_ITEM = ITEMS.registerSimpleItem("wrench",
            new Item.Properties());

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IndustryBlockEntity>> INDUSTRY_BE =
            BLOCK_ENTITIES.register("industry", () -> BlockEntityType.Builder.of(IndustryBlockEntity::new, INDUSTRY.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PlatformBlockEntity>> PLATFORM_BE =
            BLOCK_ENTITIES.register("platform", () -> BlockEntityType.Builder.of(PlatformBlockEntity::new, PLATFORM.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ConsumerBlockEntity>> CONSUMER_BE =
            BLOCK_ENTITIES.register("consumer", () -> BlockEntityType.Builder.of(ConsumerBlockEntity::new, CONSUMER.get()).build(null));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = CREATIVE_MODE_TABS.register("freightfever", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.freightfever"))
            .icon(() -> INDUSTRY_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(INDUSTRY_ITEM.get());
                output.accept(PLATFORM_ITEM.get());
                output.accept(CONSUMER_ITEM.get());
                output.accept(STEEL_INGOT.get());
                output.accept(WRENCH_ITEM.get());
            }).build());

    public FreightFever(IEventBus modEventBus, ModContainer modContainer) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        NeoForge.EVENT_BUS.addListener(this::addReloadListeners);

        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(this::addCreative);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    /**
     * Activity logging gated by the debugLogging config: INFO when enabled,
     * DEBUG (debug.log only) otherwise.
     */
    public static void debugLog(String message, Object... args) {
        if (Config.DEBUG_LOGGING.get()) {
            LOGGER.info(message, args);
        } else {
            LOGGER.debug(message, args);
        }
    }

    private void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new IndustryReloadListener());
        event.addListener(new DemandReloadListener());
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, INDUSTRY_BE.get(), (be, side) -> be.getItemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, PLATFORM_BE.get(), (be, side) -> be.createItemHandler());
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        // Items are in our own tab via displayItems above.
    }
}
