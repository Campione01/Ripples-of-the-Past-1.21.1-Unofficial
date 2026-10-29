package rotp.addons.kingcrimson;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import rotp.core.init.ModItems;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 1.16 KCConfig (COMMON, rotp_kingcrimson.toml): same sections, keys and ranges; the defaults are the spec constants.
 * Not kept: epitaphStaminaCostTick (1.16 never read it) and timeErasePresentEntityDelay (the stationary afterimage
 * it timed is gone, owner decision 2026-09-23).
 */
public final class KingCrimsonConfig {
    public static final String FILE_NAME = "rotp_kingcrimson.toml";
    /** 1.16 ids used the core's old "jojo" namespace. */
    private static final String LEGACY_CORE_NAMESPACE = "jojo";

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue BARRAGE_HEALTH_THRESHOLD;
    public static final ModConfigSpec.DoubleValue BARRAGE_HEALTH_THRESHOLD_DOPPIO;
    public static final ModConfigSpec.DoubleValue DESPERATE_EYEJAB_HEALTH_THRESHOLD;
    public static final ModConfigSpec.IntValue DESPERATE_EYEJAB_COOLDOWN;
    public static final ModConfigSpec.IntValue CHOP_COOLDOWN;
    public static final ModConfigSpec.IntValue IMPALE_COOLDOWN;
    public static final ModConfigSpec.IntValue IMPALE_GAPING_WOUND_LEVEL;
    public static final ModConfigSpec.IntValue PROJECTILE_THROW_COOLDOWN;
    public static final ModConfigSpec.ConfigValue<String> ALLOWED_PROJECTILES;
    public static final ModConfigSpec.IntValue EPITAPH_DURATION;
    public static final ModConfigSpec.IntValue EPITAPH_COOLDOWN;
    public static final ModConfigSpec.IntValue TIME_ERASE_DURATION;
    public static final ModConfigSpec.IntValue TIME_ERASE_COOLDOWN;
    public static final ModConfigSpec.BooleanValue TIME_ERASE_DYNAMIC_COOLDOWN;
    public static final ModConfigSpec.DoubleValue TIME_ERASE_DYNAMIC_COOLDOWN_MULTIPLIER;
    public static final ModConfigSpec.IntValue TIME_ERASE_DYNAMIC_MINIMUM_COOLDOWN;
    public static final ModConfigSpec.DoubleValue TIME_ERASE_STAMINA_TICK;
    public static final ModConfigSpec.IntValue TIME_SKIP_DURATION;
    public static final ModConfigSpec.IntValue TIME_SKIP_COOLDOWN;

    private static volatile ParsedProjectiles parsedProjectiles;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("King Crimson Barrage Config");
        BARRAGE_HEALTH_THRESHOLD = builder
                .comment("Above this share of max health (0.0 to 1.0) a resolve 3+ user cannot use King Crimson's barrage.")
                .defineInRange("barrageHealthThreshold",
                        (double) KingCrimsonDonorGameplaySpec.BARRAGE_HEALTH_THRESHOLD, 0.0D, 1.0D);
        BARRAGE_HEALTH_THRESHOLD_DOPPIO = builder
                .comment("Above this share of max health (0.0 to 1.0) a resolve 3+ user cannot use Doppio's barrage.")
                .defineInRange("barrageHealthThresholdDoppio",
                        (double) KingCrimsonDonorGameplaySpec.DOPPIO_BARRAGE_HEALTH_THRESHOLD, 0.0D, 1.0D);
        builder.pop();

        builder.push("King Crimson Desperate Eyejab Config");
        DESPERATE_EYEJAB_HEALTH_THRESHOLD = builder
                .comment("Below this share of max health (0.0 to 1.0) Ground Punch is replaced by Desperate Eyejab.")
                .defineInRange("desperateEyejabHealthThreshold",
                        (double) KingCrimsonPhase4Spec.EYEJAB_HEALTH_THRESHOLD, 0.0D, 1.0D);
        DESPERATE_EYEJAB_COOLDOWN = builder
                .comment("Desperate Eyejab cooldown in ticks.")
                .defineInRange("desperateEyejabCooldown",
                        KingCrimsonPhase4Spec.EYEJAB_COOLDOWN_TICKS, 0, 12000);
        builder.pop();

        builder.push("King Crimson Chop Config");
        CHOP_COOLDOWN = builder
                .comment("Chop cooldown in ticks.")
                .defineInRange("chopCooldown", KingCrimsonPhase2Spec.CHOP_COOLDOWN_TICKS, 0, 12000);
        builder.pop();

        builder.push("King Crimson Impale Config");
        IMPALE_COOLDOWN = builder
                .comment("Impale cooldown in ticks.")
                .defineInRange("impaleCooldown", KingCrimsonPhase2Spec.IMPALE_COOLDOWN_TICKS, 0, 12000);
        // 1.16 allowed -1; a 1.21 effect amplifier cannot go below 0
        IMPALE_GAPING_WOUND_LEVEL = builder
                .comment("Gaping Wound amplifier applied by Impale (0 = level 1, 1 = level 2, ...).")
                .defineInRange("impaleGapingWoundLevel",
                        KingCrimsonPhase2Spec.IMPALE_GAPING_WOUND_AMPLIFIER, 0, 9);
        builder.pop();

        builder.push("King Crimson Projectile Throw Config");
        PROJECTILE_THROW_COOLDOWN = builder
                .comment("Projectile Throw cooldown in ticks (the triple throw shares it).")
                .defineInRange("projectileThrowCooldown",
                        KingCrimsonPhase2Spec.PROJECTILE_COOLDOWN_TICKS, 0, 12000);
        ALLOWED_PROJECTILES = builder
                .comment("Comma-separated item ids Projectile Throw may throw; arrows are always allowed. "
                        + "Only items King Crimson knows how to throw count. \"jojo:\" is read as the core namespace.")
                .define("allowedProjectiles", KingCrimsonPhase2Spec.ALLOWED_PROJECTILES);
        builder.pop();

        builder.push("King Crimson Epitaph Config");
        EPITAPH_DURATION = builder
                .comment("Shortest time in ticks Epitaph stays armed; a longer foresight at higher resolve extends it.")
                .defineInRange("epitaphDuration", KingCrimsonPhase3Spec.EPITAPH_WINDOW_TICKS, 0, 12000);
        EPITAPH_COOLDOWN = builder
                .comment("Epitaph cooldown in ticks.")
                .defineInRange("epitaphCooldown", KingCrimsonPhase3Spec.EPITAPH_COOLDOWN_TICKS, 0, 12000);
        builder.pop();

        builder.push("King Crimson Time Erase Config");
        TIME_ERASE_DURATION = builder
                .comment("Longest Time Erase in ticks.")
                .defineInRange("timeEraseDuration", KingCrimsonPhase5Spec.TIME_ERASE_DURATION_TICKS, 0, 12000);
        TIME_ERASE_COOLDOWN = builder
                .comment("Time Erase cooldown in ticks.")
                .defineInRange("timeEraseCooldown",
                        KingCrimsonPhase5Spec.TIME_ERASE_FIXED_COOLDOWN_TICKS, 0, 12000);
        TIME_ERASE_DYNAMIC_COOLDOWN = builder
                .comment("Kept from 1.16, which never applied it: Time Erase always uses timeEraseCooldown.")
                .define("timeEraseDynamicCooldown", KingCrimsonPhase5Spec.TIME_ERASE_DYNAMIC_COOLDOWN);
        TIME_ERASE_DYNAMIC_COOLDOWN_MULTIPLIER = builder
                .comment("Kept from 1.16, which never applied it (see timeEraseDynamicCooldown).")
                .defineInRange("timeEraseDynamicCooldownMultiplier",
                        (double) KingCrimsonPhase5Spec.TIME_ERASE_COOLDOWN_MULTIPLIER, 0.0D, 10.0D);
        TIME_ERASE_DYNAMIC_MINIMUM_COOLDOWN = builder
                .comment("Kept from 1.16, which never applied it (see timeEraseDynamicCooldown).")
                .defineInRange("timeEraseDynamicMinimumCooldown",
                        KingCrimsonPhase5Spec.TIME_ERASE_MINIMUM_COOLDOWN_TICKS, 0, 12000);
        TIME_ERASE_STAMINA_TICK = builder
                .comment("Stamina Time Erase costs per tick.")
                .defineInRange("timeEraseStaminaTick",
                        (double) KingCrimsonPhase5Spec.TIME_ERASE_STAMINA_PER_TICK, 0.0D, 100.0D);
        builder.pop();

        builder.push("King Crimson Time Skip Config");
        TIME_SKIP_DURATION = builder
                .comment("Ticks one Time Skip moves the world ahead (high values stutter the game).")
                .defineInRange("timeSkipDuration", KingCrimsonPhase5Spec.TIME_SKIP_IMPLIED_TICKS, 0, 1200);
        TIME_SKIP_COOLDOWN = builder
                .comment("Time Skip cooldown in ticks.")
                .defineInRange("timeSkipCooldown", KingCrimsonPhase5Spec.TIME_SKIP_COOLDOWN_TICKS, 0, 12000);
        builder.pop();
        SPEC = builder.build();
    }

    private KingCrimsonConfig() {}

    public static float barrageHealthThreshold(boolean doppio) {
        return (float) read(doppio ? BARRAGE_HEALTH_THRESHOLD_DOPPIO : BARRAGE_HEALTH_THRESHOLD);
    }

    public static float eyejabHealthThreshold() {
        return (float) read(DESPERATE_EYEJAB_HEALTH_THRESHOLD);
    }

    public static int eyejabCooldownTicks() {
        return read(DESPERATE_EYEJAB_COOLDOWN);
    }

    public static int chopCooldownTicks() {
        return read(CHOP_COOLDOWN);
    }

    public static int impaleCooldownTicks() {
        return read(IMPALE_COOLDOWN);
    }

    public static int impaleGapingWoundAmplifier() {
        return read(IMPALE_GAPING_WOUND_LEVEL);
    }

    public static int projectileCooldownTicks() {
        return read(PROJECTILE_THROW_COOLDOWN);
    }

    public static int epitaphDurationTicks() {
        return read(EPITAPH_DURATION);
    }

    public static int epitaphCooldownTicks() {
        return read(EPITAPH_COOLDOWN);
    }

    public static int timeEraseDurationTicks() {
        return read(TIME_ERASE_DURATION);
    }

    public static float timeEraseStaminaPerTick() {
        return (float) read(TIME_ERASE_STAMINA_TICK);
    }

    public static int timeSkipDurationTicks() {
        return read(TIME_SKIP_DURATION);
    }

    public static int timeSkipCooldownTicks() {
        return read(TIME_SKIP_COOLDOWN);
    }

    /**
     * 1.16 applied timeEraseCooldown whatever timeEraseDynamicCooldown said: KingCrimsonTimeErase.onTaskStopped stored
     * the dynamic value in a local it never used, so the builder's cooldown(timeEraseCooldown) was the one set.
     */
    public static int timeEraseCooldownTicks() {
        return read(TIME_ERASE_COOLDOWN);
    }

    /**
     * The configured ticks in place of a spec-built cooldown. A built cooldown of 0 (creative, the no-cooldown
     * setting, or Resolve clearing a plain Stand cooldown) stays 0.
     */
    public static int configuredCooldown(int builtCooldown, int configuredTicks) {
        return builtCooldown > 0 ? Math.max(configuredTicks, 0) : 0;
    }

    /** Whether allowedProjectiles lists the item. */
    public static boolean allowsProjectile(Item item) {
        String raw = SPEC.isLoaded() ? ALLOWED_PROJECTILES.get() : ALLOWED_PROJECTILES.getDefault();
        ParsedProjectiles parsed = parsedProjectiles;
        if (parsed == null || !parsed.raw().equals(raw)) {
            parsed = new ParsedProjectiles(raw, parseIds(raw));
            parsedProjectiles = parsed;
        }
        return parsed.ids().contains(BuiltInRegistries.ITEM.getKey(item));
    }

    static Set<ResourceLocation> parseIds(String raw) {
        Set<ResourceLocation> ids = new HashSet<>();
        String coreNamespace = ModItems.KNIFE.getId().getNamespace();
        for (String entry : raw.split(",")) {
            ResourceLocation id = ResourceLocation.tryParse(entry.trim().toLowerCase(Locale.ROOT));
            if (id == null) {
                continue;
            }
            if (id.getNamespace().equals(LEGACY_CORE_NAMESPACE)) {
                id = ResourceLocation.fromNamespaceAndPath(coreNamespace, id.getPath());
            }
            ids.add(id);
        }
        return Set.copyOf(ids);
    }

    private static int read(ModConfigSpec.IntValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    private static double read(ModConfigSpec.DoubleValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    private record ParsedProjectiles(String raw, Set<ResourceLocation> ids) {}
}
