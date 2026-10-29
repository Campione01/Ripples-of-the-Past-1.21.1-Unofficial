package rotp.addons.ger.ability;

import java.util.Map;

import javax.annotation.Nullable;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;

/** Exact GER moveset-name to animation-clip bindings. */
public final class GerActionAnimations {
    public static final String PUNCH_ABILITY = "ger_stand_punch";
    public static final String BARRAGE_ABILITY = "ger_stand_barrage";
    public static final String HEAVY_ABILITY = "ger_stand_heavy_punch";
    public static final String CHARGED_HEAVY_ABILITY = "heavy_charged";
    public static final String UNDIE_ABILITY = "ger_undie_punch";
    public static final String LIFE_SHOT_ABILITY = "ger_life_shot";
    public static final String LIFE_SHOT_MULTI_ABILITY =
            "ger_life_shot_multi";
    public static final String DISABLER_ABILITY = "ger_disabler";

    public static final String PUNCH_ANIMATION = "punch";
    public static final String BARRAGE_ANIMATION = "barrage";
    public static final String HEAVY_ANIMATION = "heavy_punch";
    public static final String CHARGED_HEAVY_ANIMATION =
            "heavy_charged";
    public static final String UNDIE_ANIMATION = "ger_undie_punch";
    public static final String LIFE_SHOT_ANIMATION = "ger_life_shot";
    public static final String LIFE_SHOT_MULTI_ANIMATION =
            "ger_life_shot_multi";
    public static final String DISABLER_ANIMATION = "ger_disabler";

    private static final Map<String, String> ANIMATION_BY_ABILITY =
            Map.ofEntries(
                    Map.entry(PUNCH_ABILITY, PUNCH_ANIMATION),
                    Map.entry(BARRAGE_ABILITY, BARRAGE_ANIMATION),
                    Map.entry(HEAVY_ABILITY, HEAVY_ANIMATION),
                    Map.entry(
                            CHARGED_HEAVY_ABILITY,
                            CHARGED_HEAVY_ANIMATION),
                    Map.entry(UNDIE_ABILITY, UNDIE_ANIMATION),
                    Map.entry(LIFE_SHOT_ABILITY, LIFE_SHOT_ANIMATION),
                    Map.entry(
                            LIFE_SHOT_MULTI_ABILITY,
                            LIFE_SHOT_MULTI_ANIMATION),
                    Map.entry(DISABLER_ABILITY, DISABLER_ANIMATION));

    private GerActionAnimations() {}

    public static ActionAnimIdentifier resolve(
            AbilityId abilityId,
            ActionAnimIdentifier fallback) {
        String animation = animationName(
                abilityId != null ? abilityId.nameInMoveset() : null);
        return animation != null
                ? ActionAnimIdentifier.getOrCreate(animation, false)
                : fallback;
    }

    @Nullable
    public static String animationName(@Nullable String abilityName) {
        if (abilityName == null) {
            return null;
        }
        String animation = ANIMATION_BY_ABILITY.get(abilityName);
        if (animation != null) {
            return animation;
        }
        int namespaceSeparator = abilityName.indexOf(':');
        return namespaceSeparator >= 0
                ? ANIMATION_BY_ABILITY.get(
                        abilityName.substring(namespaceSeparator + 1))
                : null;
    }
}
