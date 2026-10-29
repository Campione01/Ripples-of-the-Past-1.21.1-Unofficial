package rotp.addons.yellowtemperance.disguise;

import rotp.addons.yellowtemperance.YellowTemperanceAddon;
import net.minecraft.resources.ResourceLocation;
import virtuoel.pehkui.api.ScaleModifier;
import virtuoel.pehkui.api.ScaleRegistries;
import virtuoel.pehkui.api.ScaleType;
import virtuoel.pehkui.api.ScaleTypes;
import virtuoel.pehkui.api.TypedScaleModifier;

public final class YellowTemperanceScaleTypes {
    private static ScaleType widthScale;
    private static ScaleType heightScale;
    private static boolean initialized;

    private YellowTemperanceScaleTypes() {}

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        ScaleModifier widthModifier = modifier(
                "disguise_width_multiplier",
                YellowTemperanceScaleTypes::widthScale,
                520.0F);
        ScaleModifier heightModifier = modifier(
                "disguise_height_multiplier",
                YellowTemperanceScaleTypes::heightScale,
                521.0F);
        widthScale = scaleType(
                "disguise_width", widthModifier);
        heightScale = scaleType(
                "disguise_height", heightModifier);

        ScaleTypes.HITBOX_WIDTH.getDefaultBaseValueModifiers()
                .add(widthModifier);
        ScaleTypes.INTERACTION_BOX_WIDTH
                .getDefaultBaseValueModifiers()
                .add(widthModifier);
        ScaleTypes.HITBOX_HEIGHT.getDefaultBaseValueModifiers()
                .add(heightModifier);
        ScaleTypes.INTERACTION_BOX_HEIGHT
                .getDefaultBaseValueModifiers()
                .add(heightModifier);
        ScaleTypes.EYE_HEIGHT.getDefaultBaseValueModifiers()
                .add(heightModifier);
        initialized = true;
    }

    public static ScaleType widthScale() {
        if (widthScale == null) {
            throw new IllegalStateException(
                    "Yellow Temperance width scale is not initialized.");
        }
        return widthScale;
    }

    public static ScaleType heightScale() {
        if (heightScale == null) {
            throw new IllegalStateException(
                    "Yellow Temperance height scale is not initialized.");
        }
        return heightScale;
    }

    private static ScaleModifier modifier(
            String id,
            java.util.function.Supplier<ScaleType> type,
            float priority) {
        return ScaleRegistries.register(
                ScaleRegistries.SCALE_MODIFIERS,
                YellowTemperanceAddon.id(id),
                new TypedScaleModifier(type, priority));
    }

    private static ScaleType scaleType(
            String id, ScaleModifier dependent) {
        ResourceLocation key = YellowTemperanceAddon.id(id);
        return ScaleRegistries.register(
                ScaleRegistries.SCALE_TYPES,
                key,
                ScaleType.Builder.create()
                        .defaultTickDelay(0)
                        .defaultMinPositiveScale(0.05F)
                        .defaultPersistence(false)
                        .affectsDimensions()
                        .addDependentModifier(dependent)
                        .build());
    }
}
