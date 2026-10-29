package rotp.addons.stand_bearing_creatures.debug;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;

import java.util.function.BiFunction;

import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import rotp.addons.stand_bearing_creatures.MobsWithPowersAddon;
import rotp.addons.stand_bearing_creatures.debug.DebugStandAbilities.AggroOneToTwo;
import rotp.addons.stand_bearing_creatures.debug.DebugStandAbilities.GiveRandomPower;
import rotp.addons.stand_bearing_creatures.debug.DebugStandAbilities.GiveRandomStand;
import rotp.addons.stand_bearing_creatures.debug.DebugStandAbilities.NoAi;
import rotp.addons.stand_bearing_creatures.debug.DebugStandAbilities.RemovePower;
import rotp.addons.stand_bearing_creatures.debug.DebugStandAbilities.RemoveStand;
import rotp.addons.stand_bearing_creatures.debug.DebugStandAbilities.ToggleSummon;
import rotp.addons.stand_bearing_creatures.debug.DebugStandAbilities.UseRandomAction;

public final class DebugStandRegistry {
    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(
                    JojoRegistries.ABILITY_TYPES_REG,
                    MobsWithPowersAddon.MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(
                    JojoRegistries.DEFAULT_STANDS_REG,
                    MobsWithPowersAddon.MOD_ID);

    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<GiveRandomStand>> GIVE_RANDOM_STAND =
            ability("give_random_stand", GiveRandomStand::new);
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<GiveRandomPower>> GIVE_RANDOM_POWER =
            ability("give_random_power", GiveRandomPower::new);
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<RemoveStand>> REMOVE_STAND =
            ability("remove_stand", RemoveStand::new);
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<RemovePower>> REMOVE_POWER =
            ability("remove_power", RemovePower::new);
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<NoAi>> NO_AI =
            ability("no_ai", NoAi::new);
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<UseRandomAction>> USE_RANDOM_ACTION =
            ability("use_random_action", UseRandomAction::new);
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<ToggleSummon>> TOGGLE_SUMMON =
            ability("toggle_summon", ToggleSummon::new);
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<AggroOneToTwo>> AGGRO_ONE_TO_TWO =
            ability("aggro_one_to_two", AggroOneToTwo::new);

    public static final DeferredHolder<StandType, DebugStandType>
            DEBUG_STAND = STAND_TYPES.register(
                    "debug_stand",
                    DebugStandRegistry::createDebugStand);

    public static void register(IEventBus modBus) {
        ABILITY_TYPES.register(modBus);
        STAND_TYPES.register(modBus);
    }

    private static DebugStandType createDebugStand(ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addAbility("give_random_stand", GIVE_RANDOM_STAND)
                .addAbility("give_random_power", GIVE_RANDOM_POWER)
                .addAbility("remove_stand", REMOVE_STAND)
                .addAbility("remove_power", REMOVE_POWER)
                .addAbility("no_ai", NO_AI)
                .addAbility("use_random_action", USE_RANDOM_ACTION)
                .addAbility("toggle_summon", TOGGLE_SUMMON)
                .addAbility("aggro_one_to_two", AGGRO_ONE_TO_TWO)
                .makeControlScheme("hotbar")
                    .makeHotbar(0, InputKey.LMB, SWITCH_SPECIAL)
                    .addToHotbar(
                            "toggle_summon",
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            "aggro_one_to_two",
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            "remove_stand",
                            0,
                            InputMethod.CLICK)
                    .makeHotbar(1, InputKey.RMB, SWITCH_SPECIAL)
                    .addToHotbar(
                            "give_random_stand",
                            1,
                            InputMethod.CLICK)
                    .addToHotbar(
                            "no_ai",
                            1,
                            InputMethod.CLICK)
                    .addToHotbar(
                            "use_random_action",
                            1,
                            InputMethod.CLICK)
                    .addHotbarSlotVariation(
                            "give_random_power",
                            "give_random_stand",
                            InputKey.Modifier.SHIFT,
                            InputMethod.CLICK)
                    .addHotbarSlotVariation(
                            "remove_power",
                            "remove_stand",
                            InputKey.Modifier.SHIFT,
                            InputMethod.CLICK)
                .finalizeControlScheme()
                .addSkill(StandUnlockableSkill.startingAbility(
                        "give_random_stand"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "give_random_power"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "remove_stand"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "remove_power"))
                .addSkill(StandUnlockableSkill.startingAbility("no_ai"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "use_random_action"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "toggle_summon"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "aggro_one_to_two"));

        return new DebugStandType(
                new StandStats.Builder()
                        .power(0.0D)
                        .speed(0.0D)
                        .range(0.0D, 0.0D)
                        .durability(0.0D)
                        .precision(0.0D)
                        .randomWeight(0.0D)
                        .build(),
                moveset,
                id);
    }

    private static <T extends Ability> DeferredHolder<
            AbilityType<?>,
            AbilityType<T>> ability(
                    String name,
                    BiFunction<AbilityType<T>, AbilityId, T> factory) {
        return ABILITY_TYPES.register(
                name,
                key -> new AbilityType<>(key, factory));
    }

    private DebugStandRegistry() {}
}
