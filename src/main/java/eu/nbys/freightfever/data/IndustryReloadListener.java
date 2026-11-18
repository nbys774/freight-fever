package eu.nbys.freightfever.data;

import java.util.HashMap;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import eu.nbys.freightfever.FreightFever;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

public class IndustryReloadListener extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public IndustryReloadListener() {
        super(GSON, "freight/industries");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, IndustryRecipe> map = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : objects.entrySet()) {
            IndustryRecipe.CODEC.parse(JsonOps.INSTANCE, entry.getValue()).resultOrPartial(err ->
                    FreightFever.LOGGER.error("Failed to parse industry recipe {}: {}", entry.getKey(), err))
                    .ifPresent(recipe -> map.put(entry.getKey(), recipe));
        }
        FreightData.setIndustries(map);
        FreightFever.LOGGER.info("Loaded {} industry recipes", map.size());
    }
}
