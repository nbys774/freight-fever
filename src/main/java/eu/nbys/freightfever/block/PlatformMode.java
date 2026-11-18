package eu.nbys.freightfever.block;

import net.minecraft.util.StringRepresentable;

public enum PlatformMode implements StringRepresentable {
    IN("in"),
    OUT("out");

    private final String name;

    PlatformMode(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public PlatformMode next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
