package com.github.standobyte.jojo.util.mc.entitysubtype;

import java.util.Locale;
import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;

public final class SubtypeResourceLocation {
    private final ResourceLocation withoutSubtype;
    @Nullable private final String variant;

    public SubtypeResourceLocation(String id) {
        int variantDelimPos = id.indexOf('#');
        String baseId = variantDelimPos >= 0 ? id.substring(0, variantDelimPos) : id;
        this.withoutSubtype = ResourceLocation.parse(baseId);
        this.variant = variantDelimPos >= 0 && variantDelimPos + 1 < id.length()
                ? id.substring(variantDelimPos + 1).toLowerCase(Locale.ROOT)
                : null;
    }

    public SubtypeResourceLocation(ResourceLocation id) {
        this(id, null);
    }

    public SubtypeResourceLocation(ResourceLocation id, @Nullable String variant) {
        this.withoutSubtype = id;
        this.variant = variant != null && !variant.isBlank() ? variant.toLowerCase(Locale.ROOT) : null;
    }

    public ResourceLocation withoutSubtype() {
        return withoutSubtype;
    }

    @Nullable
    public String getSubtypeId() {
        return variant;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof ResourceLocation resourceLocation) {
            return variant == null && withoutSubtype.equals(resourceLocation);
        }
        if (obj instanceof SubtypeResourceLocation other) {
            return withoutSubtype.equals(other.withoutSubtype) && Objects.equals(variant, other.variant);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 31 * withoutSubtype.hashCode() + Objects.hashCode(variant);
    }

    @Override
    public String toString() {
        return variant == null ? withoutSubtype.toString() : withoutSubtype + "#" + variant;
    }
}
