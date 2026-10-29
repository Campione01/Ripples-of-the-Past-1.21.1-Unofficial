package rotp.addons.littlefeet.scale;

import rotp.addons.littlefeet.LittleFeetAddon;
import net.minecraft.resources.ResourceLocation;
import virtuoel.pehkui.api.ScaleModifier;
import virtuoel.pehkui.api.ScaleRegistries;
import virtuoel.pehkui.api.ScaleType;
import virtuoel.pehkui.api.ScaleTypes;
import virtuoel.pehkui.api.TypedScaleModifier;

public final class LittleFeetScaleTypes {
    private static ScaleType littleFeetScale;
    private static ScaleType targetHealthScale;
    private static ScaleModifier sizeAndStatsModifier;
    private static ScaleModifier knockbackModifier;
    private static ScaleModifier targetHealthModifier;
    private static boolean initialized;

    private LittleFeetScaleTypes() {}

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }

        ResourceLocation scaleId = LittleFeetAddon.id("little_feet");
        ResourceLocation sizeModifierId =
                LittleFeetAddon.id("little_feet_multiplier");
        ResourceLocation knockbackModifierId =
                LittleFeetAddon.id("little_feet_knockback");
        ResourceLocation targetHealthScaleId =
                LittleFeetAddon.id("little_feet_target_health");
        ResourceLocation targetHealthModifierId =
                LittleFeetAddon.id(
                        "little_feet_target_health_multiplier");

        sizeAndStatsModifier = ScaleRegistries.register(
                ScaleRegistries.SCALE_MODIFIERS,
                sizeModifierId,
                new TypedScaleModifier(
                        LittleFeetScaleTypes::scale,
                        510.0F));
        knockbackModifier = ScaleRegistries.register(
                ScaleRegistries.SCALE_MODIFIERS,
                knockbackModifierId,
                new TypedScaleModifier(
                        LittleFeetScaleTypes::scale,
                        (current, littleFeet) ->
                                current
                                        * LittleFeetScaleMath
                                                .knockbackMultiplier(
                                                        littleFeet),
                        509.0F));
        targetHealthModifier = ScaleRegistries.register(
                ScaleRegistries.SCALE_MODIFIERS,
                targetHealthModifierId,
                new TypedScaleModifier(
                        LittleFeetScaleTypes::targetHealthScale,
                        508.0F));

        littleFeetScale = ScaleRegistries.register(
                ScaleRegistries.SCALE_TYPES,
                scaleId,
                ScaleType.Builder.create()
                        .defaultTickDelay(1)
                        .defaultMinPositiveScale(
                                LittleFeetScaleService.MIN_SCALE)
                        .defaultPersistence(false)
                        .affectsDimensions()
                        .addDependentModifier(sizeAndStatsModifier)
                        .addDependentModifier(knockbackModifier)
                        .build());
        targetHealthScale = ScaleRegistries.register(
                ScaleRegistries.SCALE_TYPES,
                targetHealthScaleId,
                ScaleType.Builder.create()
                        .defaultTickDelay(1)
                        .defaultMinPositiveScale(
                                LittleFeetScaleService.MIN_SCALE)
                        .defaultPersistence(false)
                        .build());

        ScaleTypes.BASE.getDefaultBaseValueModifiers()
                .add(sizeAndStatsModifier);
        ScaleTypes.ATTACK.getDefaultBaseValueModifiers()
                .add(sizeAndStatsModifier);
        ScaleTypes.DEFENSE.getDefaultBaseValueModifiers()
                .add(sizeAndStatsModifier);
        ScaleTypes.MINING_SPEED.getDefaultBaseValueModifiers()
                .add(sizeAndStatsModifier);
        ScaleTypes.HEALTH.getDefaultBaseValueModifiers()
                .add(targetHealthModifier);
        ScaleTypes.KNOCKBACK.getDefaultBaseValueModifiers()
                .add(knockbackModifier);

        initialized = true;
    }

    public static ScaleType scale() {
        if (littleFeetScale == null) {
            throw new IllegalStateException(
                    "Little Feet scale type was used before initialization.");
        }
        return littleFeetScale;
    }

    public static ScaleType targetHealthScale() {
        if (targetHealthScale == null) {
            throw new IllegalStateException(
                    "Little Feet target health scale type was used "
                            + "before initialization.");
        }
        return targetHealthScale;
    }

    public static ScaleModifier sizeAndStatsModifier() {
        return sizeAndStatsModifier;
    }

    public static ScaleModifier knockbackModifier() {
        return knockbackModifier;
    }
}
