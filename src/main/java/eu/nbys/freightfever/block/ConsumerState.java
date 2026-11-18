package eu.nbys.freightfever.block;

import net.minecraft.util.StringRepresentable;

public enum ConsumerState implements StringRepresentable {
    HUNGRY("hungry"),
    AVERAGE("average"),
    SATISFIED("satisfied");

    private final String name;

    ConsumerState(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
