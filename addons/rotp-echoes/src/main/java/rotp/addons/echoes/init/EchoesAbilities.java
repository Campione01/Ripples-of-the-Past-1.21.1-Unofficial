package rotp.addons.echoes.init;

import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.ability.AbilityType;

import rotp.addons.echoes.RotpEchoes;
import rotp.addons.echoes.ability.EchoesAct3BarrageAbility;
import rotp.addons.echoes.ability.EchoesGravityAbility;
import rotp.addons.echoes.ability.EchoesGravityAbility.Mode;
import rotp.addons.echoes.ability.EchoesFinisherAbility;
import rotp.addons.echoes.ability.EchoesTransitionAbility;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class EchoesAbilities {
    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(
                    JojoRegistries.ABILITY_TYPES_REG, RotpEchoes.MOD_ID);

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<EchoesGravityAbility>>
            GRAVITY_PROJECTILE =
                    action("gravity_projectile", Mode.PROJECTILE);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<EchoesGravityAbility>>
            FREEZE = action("freeze", Mode.FREEZE_TARGET);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<EchoesGravityAbility>>
            FREEZE_SELF = action("freeze_self", Mode.FREEZE_SELF);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<EchoesGravityAbility>>
            GRAVITY_INFUSE =
                    action("gravity_infuse", Mode.INFUSE);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<EchoesGravityAbility>>
            HAND_FREEZE = action("hand_freeze", Mode.HAND_FREEZE);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<EchoesFinisherAbility>>
            ACT3_FINISHER = ABILITY_TYPES.register(
                    "act3_finisher",
                    key -> new AbilityType<>(
                            key,
                            (type, id) -> new EchoesFinisherAbility(
                                    type,
                                    id,
                                    EchoesFinisherAbility.Mode.ACT3_KNOCKBACK)));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<EchoesFinisherAbility>>
            ACT2_FINISHER = ABILITY_TYPES.register(
                    "act2_finisher",
                    key -> new AbilityType<>(
                            key,
                            (type, id) -> new EchoesFinisherAbility(
                                    type,
                                    id,
                                    EchoesFinisherAbility.Mode.ACT2_BRAINROT)));
    // 092 ACT3 barrage: start voice, no impact sound
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<EchoesAct3BarrageAbility>>
            ACT3_BARRAGE = ABILITY_TYPES.register(
                    "act3_barrage",
                    key -> new AbilityType<>(
                            key, EchoesAct3BarrageAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<EchoesTransitionAbility>>
            EVOLVE_TO_ACT2 = transition(
                    "evolve_to_act2",
                    EchoesTransitionAbility.Mode.EVOLVE_TO_ACT2);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<EchoesTransitionAbility>>
            DEGRADE_TO_ACT1 = transition(
                    "degrade_to_act1",
                    EchoesTransitionAbility.Mode.DEGRADE_TO_ACT1);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<EchoesTransitionAbility>>
            EVOLVE_TO_ACT3 = transition(
                    "evolve_to_act3",
                    EchoesTransitionAbility.Mode.EVOLVE_TO_ACT3);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<EchoesTransitionAbility>>
            DEGRADE_TO_ACT2 = transition(
                    "degrade_to_act2",
                    EchoesTransitionAbility.Mode.DEGRADE_TO_ACT2);

    private EchoesAbilities() {}

    private static DeferredHolder<
            AbilityType<?>, AbilityType<EchoesGravityAbility>>
            action(String path, Mode mode) {
        return ABILITY_TYPES.register(
                path,
                key -> new AbilityType<>(
                        key,
                        (type, id) ->
                                new EchoesGravityAbility(type, id, mode)));
    }

    private static DeferredHolder<
            AbilityType<?>, AbilityType<EchoesTransitionAbility>> transition(
                    String path, EchoesTransitionAbility.Mode mode) {
        return ABILITY_TYPES.register(
                path,
                key -> new AbilityType<>(
                        key,
                        (type, id) ->
                                new EchoesTransitionAbility(type, id, mode)));
    }
}
