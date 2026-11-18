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

public class DemandReloadListener extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public DemandReloadListener() {
        super(GSON, "freight/demands");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, Demand> map = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : objects.entrySet()) {
            Demand.CODEC.parse(JsonOps.INSTANCE, entry.getValue()).resultOrPartial(err ->
                    FreightFever.LOGGER.error("Failed to parse demand {}: {}", entry.getKey(), err))
                    .ifPresent(demand -> map.put(entry.getKey(), demand));
        }
        FreightData.setDemands(map);
        FreightFever.LOGGER.info("Loaded {} demands", map.size());
    }
}
