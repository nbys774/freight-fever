package eu.nbys.freightfever.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

public record ItemAmount(Item item, int amount) {
    public static final Codec<ItemAmount> CODEC = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(ItemAmount::item),
            Codec.INT.fieldOf("amount").forGetter(ItemAmount::amount)
    ).apply(i, ItemAmount::new));
}
