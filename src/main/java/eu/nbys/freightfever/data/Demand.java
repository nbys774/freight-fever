package eu.nbys.freightfever.data;

import java.util.List;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

public record Demand(ResourceLocation id, String type, List<DemandItem> items) {
    public record DemandItem(net.minecraft.world.item.Item item, double rate) {
        public static final Codec<DemandItem> CODEC = RecordCodecBuilder.create(i -> i.group(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(DemandItem::item),
                Codec.DOUBLE.fieldOf("rate").forGetter(DemandItem::rate)
        ).apply(i, DemandItem::new));
    }

    public static final Codec<Demand> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(Demand::id),
            Codec.STRING.fieldOf("type").forGetter(Demand::type),
            DemandItem.CODEC.listOf().fieldOf("items").forGetter(Demand::items)
    ).apply(i, Demand::new));
}
