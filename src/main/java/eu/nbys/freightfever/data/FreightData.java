package eu.nbys.freightfever.data;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** In-memory store filled by the datapack reload listeners. */
public final class FreightData {
    private static final Map<ResourceLocation, IndustryRecipe> INDUSTRIES = new LinkedHashMap<>();
    private static final Map<ResourceLocation, Demand> DEMANDS = new LinkedHashMap<>();

    private FreightData() {}

    public static void setIndustries(Map<ResourceLocation, IndustryRecipe> map) {
        INDUSTRIES.clear();
        INDUSTRIES.putAll(map);
    }

    public static void setDemands(Map<ResourceLocation, Demand> map) {
        DEMANDS.clear();
        DEMANDS.putAll(map);
    }

    public static IndustryRecipe getIndustry(ResourceLocation id) {
        return INDUSTRIES.get(id);
    }

    public static Demand getDemand(ResourceLocation id) {
        return DEMANDS.get(id);
    }

    public static Collection<IndustryRecipe> industries() {
        return Collections.unmodifiableCollection(INDUSTRIES.values());
    }

    public static Collection<Demand> demands() {
        return Collections.unmodifiableCollection(DEMANDS.values());
    }

    public static IndustryRecipe defaultIndustry() {
        return INDUSTRIES.values().stream().findFirst().orElse(null);
    }

    public static Demand defaultDemand() {
        return DEMANDS.values().stream().findFirst().orElse(null);
    }
}
