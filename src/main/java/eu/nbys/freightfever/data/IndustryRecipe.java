package eu.nbys.freightfever.data;

import java.util.List;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

public record IndustryRecipe(ResourceLocation id, List<ItemAmount> inputs, List<ItemAmount> outputs,
                             int processTimeTicks, boolean outputOnly) {
    public static final Codec<IndustryRecipe> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(IndustryRecipe::id),
            ItemAmount.CODEC.listOf().fieldOf("inputs").forGetter(IndustryRecipe::inputs),
            ItemAmount.CODEC.listOf().fieldOf("outputs").forGetter(IndustryRecipe::outputs),
            Codec.INT.fieldOf("processTimeTicks").forGetter(IndustryRecipe::processTimeTicks),
            Codec.BOOL.optionalFieldOf("outputOnly", false).forGetter(IndustryRecipe::outputOnly)
    ).apply(i, IndustryRecipe::new));
}
