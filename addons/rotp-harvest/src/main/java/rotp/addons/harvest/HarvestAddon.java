package rotp.addons.harvest;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import rotp.core.api.RotpAddonApi;
import rotp.core.api.stand.StandSkillWalls;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.type.StandType;

import rotp.addons.harvest.ability.HarvestCommandAbility;
import rotp.addons.harvest.ability.HarvestCommandAbility.Command;
import rotp.addons.harvest.entity.HarvestMemberEntity;
import rotp.addons.harvest.entity.HarvestStandEntity;
import rotp.addons.harvest.stand.HarvestStandType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(HarvestAddon.MOD_ID)
public final class HarvestAddon {
    public static final String MOD_ID = "rotp_harvest";
    public static final String SET_TARGET_NAME = "set_target";
    public static final String FORGET_TARGET_NAME = "forget_target";
    public static final String GO_TO_NAME = "go_to_this_place";
    public static final String STAY_WITH_NAME = "stay_with";
    public static final String SEARCH_NAME = "search";
    public static final String BE_CLOSER_NAME = "be_closer";
    public static final String GIVE_POTION_NAME = "give_a_potion";
    public static final String REMOVE_POTION_NAME = "remove_a_potion";
    public static final String CARRY_UP_NAME = "carry_up";
    public static final String CONTROL_HARVEST_NAME =
            "control_harvest";
    private static final int REQUIRED_CORE_ABI = 1;

    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(
                    JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(
                    JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<HarvestCommandAbility>>
            SET_TARGET = command(SET_TARGET_NAME, Command.SET_TARGET);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<HarvestCommandAbility>>
            FORGET_TARGET =
                    command(FORGET_TARGET_NAME, Command.FORGET_TARGET);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<HarvestCommandAbility>>
            GO_TO = command(GO_TO_NAME, Command.GO_TO);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<HarvestCommandAbility>>
            STAY_WITH = command(STAY_WITH_NAME, Command.STAY_WITH);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<HarvestCommandAbility>>
            SEARCH = command(SEARCH_NAME, Command.SEARCH);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<HarvestCommandAbility>>
            BE_CLOSER = command(BE_CLOSER_NAME, Command.BE_CLOSER);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<HarvestCommandAbility>>
            GIVE_POTION =
                    command(GIVE_POTION_NAME, Command.GIVE_POTION);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<HarvestCommandAbility>>
            REMOVE_POTION =
                    command(
                            REMOVE_POTION_NAME,
                            Command.REMOVE_POTION);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<HarvestCommandAbility>>
            CARRY_UP =
                    command(CARRY_UP_NAME, Command.CARRY_UP);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<HarvestCommandAbility>>
            CONTROL_HARVEST =
                    command(
                            CONTROL_HARVEST_NAME,
                            Command.CONTROL_HARVEST);

    public static final DeferredHolder<
            EntityType<?>, EntityType<HarvestStandEntity>>
            HARVEST_STAND_ENTITY = ENTITY_TYPES.register(
                    "harvest_stand",
                    key -> EntityType.Builder
                            .<HarvestStandEntity>of(
                                    HarvestStandEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.0F, 0.0F)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<
            EntityType<?>, EntityType<HarvestMemberEntity>>
            HARVEST_MEMBER = ENTITY_TYPES.register(
                    "harvest",
                    key -> EntityType.Builder
                            .<HarvestMemberEntity>of(
                                    HarvestMemberEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.2F, 0.4F)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<SoundEvent, SoundEvent>
            HARVEST_STEP = SOUND_EVENTS.register(
                    "hv_step", SoundEvent::createVariableRangeEvent);

    public static final DeferredHolder<
            StandType, HarvestStandType> HARVEST_STAND =
            STAND_TYPES.register(
                    "harvest_stand", HarvestAddon::createStand);

    public HarvestAddon(IEventBus modEventBus) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_POWER_TRANSITIONS_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ACTION_SUMMON_LOCK_HOOK_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ACTION_STAND_STUN_HOOK_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_POWER_ACTION_SLOT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_ACTION_PORT_SLOT_V1)) {
            throw new IllegalStateException(
                    "RotP Harvest requires ROTP addon ABI 1 with "
                            + "namespaced abilities, Stand visual "
                            + "contexts, Stand power transitions, "
                            + "the action summon lock hook, the "
                            + "action Stand stun hook, the Stand-power "
                            + "action slot and the Stand action port "
                            + "slot.");
        }

        ABILITY_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        modEventBus.addListener(HarvestAddon::markSkillsThroughWalls);
    }

    // The swarm goes where it is sent, through all of Morioh: the target of
    // set_target and stay_with is picked behind walls, as 1.16's ray did.
    private static void markSkillsThroughWalls(FMLCommonSetupEvent event) {
        StandSkillWalls.markThroughWalls(
                HARVEST_STAND.getId(), SET_TARGET_NAME);
        StandSkillWalls.markThroughWalls(
                HARVEST_STAND.getId(), STAY_WITH_NAME);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static DeferredHolder<
            AbilityType<?>, AbilityType<HarvestCommandAbility>>
            command(String id, Command command) {
        return ABILITY_TYPES.register(
                id,
                key -> new AbilityType<>(
                        key,
                        (type, abilityId) ->
                                new HarvestCommandAbility(
                                        type,
                                        abilityId,
                                        command)));
    }

    private static HarvestStandType createStand(ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addAbility(SET_TARGET_NAME, SET_TARGET)
                .addAbility(FORGET_TARGET_NAME, FORGET_TARGET)
                .addAbility(GO_TO_NAME, GO_TO)
                .addAbility(STAY_WITH_NAME, STAY_WITH)
                .addAbility(SEARCH_NAME, SEARCH)
                .addAbility(BE_CLOSER_NAME, BE_CLOSER)
                .addAbility(GIVE_POTION_NAME, GIVE_POTION)
                .addAbility(REMOVE_POTION_NAME, REMOVE_POTION)
                .addAbility(CARRY_UP_NAME, CARRY_UP)
                .addAbility(
                        CONTROL_HARVEST_NAME, CONTROL_HARVEST)
                .makeControlScheme("hotbar")
                    .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                    .addToHotbar(
                            SET_TARGET_NAME, 0, InputMethod.CLICK)
                    .addToHotbar(
                            FORGET_TARGET_NAME, 0, InputMethod.CLICK)
                    .addToHotbar(
                            GO_TO_NAME, 0, InputMethod.CLICK)
                    .addToHotbar(
                            STAY_WITH_NAME, 0, InputMethod.CLICK)
                    .addToHotbar(
                            SEARCH_NAME, 0, InputMethod.HOLD)
                    .addToHotbar(
                            BE_CLOSER_NAME, 0, InputMethod.CLICK)
                    .addToHotbar(
                            GIVE_POTION_NAME, 0, InputMethod.HOLD)
                    .addToHotbar(
                            REMOVE_POTION_NAME, 0, InputMethod.HOLD)
                    .addToHotbar(
                            CONTROL_HARVEST_NAME,
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            CARRY_UP_NAME, 0, InputMethod.HOLD)
                .finalizeControlScheme()
                .addSkill(StandUnlockableSkill.startingAbility(
                        SET_TARGET_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        FORGET_TARGET_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        GO_TO_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        STAY_WITH_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        SEARCH_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        BE_CLOSER_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        GIVE_POTION_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        REMOVE_POTION_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        CARRY_UP_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        CONTROL_HARVEST_NAME));

        return new HarvestStandType(
                new StandStats.Builder()
                        .power(2.0D)
                        .speed(12.0D)
                        .range(150.0D, 200.0D)
                        .durability(14.0D)
                        .precision(2.0D)
                        .randomWeight(2.0D)
                        .build(),
                moveset,
                HARVEST_STAND_ENTITY.get(),
                id)
                .standControlType(StandControlType.COLONY)
                .manualControl(false)
                .distanceStrengthDecay(false)
                .standDimensions(0.0F, 0.0F)
                .summonSound(() -> ModSoundEvents.STAND_SUMMON)
                .unsummonSound(() -> ModSoundEvents.STAND_UNSUMMON);
    }
}
