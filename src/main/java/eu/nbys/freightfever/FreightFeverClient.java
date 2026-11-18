package eu.nbys.freightfever;

import eu.nbys.freightfever.client.ConsumerBlockEntityRenderer;
import eu.nbys.freightfever.client.IndustryBlockEntityRenderer;
import eu.nbys.freightfever.client.WrenchOverlayHandler;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = FreightFever.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = FreightFever.MODID, value = Dist.CLIENT)
public class FreightFeverClient {
    public FreightFeverClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        NeoForge.EVENT_BUS.addListener(WrenchOverlayHandler::onClientTick);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(FreightFever.INDUSTRY_BE.get(), IndustryBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(FreightFever.CONSUMER_BE.get(), ConsumerBlockEntityRenderer::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // Some client setup code
        FreightFever.LOGGER.info("HELLO FROM CLIENT SETUP");
        FreightFever.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }
}
