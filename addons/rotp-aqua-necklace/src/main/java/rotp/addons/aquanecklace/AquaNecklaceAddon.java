package rotp.addons.aquanecklace;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import rotp.core.api.RotpAddonApi;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.util.sound.OstSoundList;

import rotp.addons.aquanecklace.ability.AquaNecklaceAbilities;
import rotp.addons.aquanecklace.entity.AquaNecklaceEntity;
import rotp.addons.aquanecklace.stand.AquaNecklaceStandType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(AquaNecklaceAddon.MOD_ID)
public final class AquaNecklaceAddon {
    public static final String MOD_ID = "rotp_aqua_necklace";

    public static final double POWER = 8.0D;
    // base -> fully developed values of the donor stats (speed 8.0/8.5, durability 15/18, precision 8/9)
    public static final double SPEED_BASE = 8.0D;
    public static final double SPEED = 8.5D;
    public static final double EFFECTIVE_RANGE = 50.0D;
    public static final double MAX_RANGE = 75.0D;
    public static final double DURABILITY_BASE = 15.0D;
    public static final double DURABILITY = 18.0D;
    public static final double PRECISION_BASE = 8.0D;
    public static final double PRECISION = 9.0D;
    public static final float STAND_WIDTH = 0.29F;
    public static final float STAND_HEIGHT = 0.76F;

    public static final String CHANGE_TO_LIQUID_NAME =
            "change_to_liquid";
    public static final String CHANGE_TO_GASEOUS_NAME =
            "change_to_gaseous";
    public static final String ENTER_ENTITY_NAME =
            "enter_entity";
    public static final String LEAVE_ENTITY_NAME =
            "leave_entity";
    public static final String DROWN_HOST_NAME =
            "drown_host";
    public static final String HOST_PRESSURE_NAME =
            "host_pressure";
    public static final String HOST_CRUSH_NAME =
            "host_crush";
    public static final String BRAIN_ATTACK_NAME =
            "brain_attack";
    public static final String FLUID_GUARD_NAME =
            "fluid_guard";

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

    public static final OstSoundList AQUA_NECKLACE_OST =
            new OstSoundList(
                    id("aqua_necklace_ost"), SOUND_EVENTS);

    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<AquaNecklaceAbilities.ChangeToLiquidAbility>>
            CHANGE_TO_LIQUID = ABILITY_TYPES.register(
                    CHANGE_TO_LIQUID_NAME,
                    key -> new AbilityType<>(
                            key,
                            AquaNecklaceAbilities
                                    .ChangeToLiquidAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<AquaNecklaceAbilities.ChangeToGaseousAbility>>
            CHANGE_TO_GASEOUS = ABILITY_TYPES.register(
                    CHANGE_TO_GASEOUS_NAME,
                    key -> new AbilityType<>(
                            key,
                            AquaNecklaceAbilities
                                    .ChangeToGaseousAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<AquaNecklaceAbilities.EnterEntityAbility>>
            ENTER_ENTITY = ABILITY_TYPES.register(
                    ENTER_ENTITY_NAME,
                    key -> new AbilityType<>(
                            key,
                            AquaNecklaceAbilities
                                    .EnterEntityAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<AquaNecklaceAbilities.LeaveEntityAbility>>
            LEAVE_ENTITY = ABILITY_TYPES.register(
                    LEAVE_ENTITY_NAME,
                    key -> new AbilityType<>(
                            key,
                            AquaNecklaceAbilities
                                    .LeaveEntityAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<AquaNecklaceAbilities.DrownHostAbility>>
            DROWN_HOST = ABILITY_TYPES.register(
                    DROWN_HOST_NAME,
                    key -> new AbilityType<>(
                            key,
                            AquaNecklaceAbilities
                                    .DrownHostAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<AquaNecklaceAbilities.HostPressureAbility>>
            HOST_PRESSURE = ABILITY_TYPES.register(
                    HOST_PRESSURE_NAME,
                    key -> new AbilityType<>(
                            key,
                            AquaNecklaceAbilities
                                    .HostPressureAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<AquaNecklaceAbilities.HostCrushAbility>>
            HOST_CRUSH = ABILITY_TYPES.register(
                    HOST_CRUSH_NAME,
                    key -> new AbilityType<>(
                            key,
                            AquaNecklaceAbilities
                                    .HostCrushAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<AquaNecklaceAbilities.BrainAttackAbility>>
            BRAIN_ATTACK = ABILITY_TYPES.register(
                    BRAIN_ATTACK_NAME,
                    key -> new AbilityType<>(
                            key,
                            AquaNecklaceAbilities
                                    .BrainAttackAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<AquaNecklaceAbilities.FluidGuardAbility>>
            FLUID_GUARD = ABILITY_TYPES.register(
                    FLUID_GUARD_NAME,
                    key -> new AbilityType<>(
                            key,
                            AquaNecklaceAbilities
                                    .FluidGuardAbility::new));

    public static final DeferredHolder<
            EntityType<?>, EntityType<AquaNecklaceEntity>>
            AQUA_NECKLACE_ENTITY = ENTITY_TYPES.register(
                    "aqua_necklace",
                    key -> EntityType.Builder
                            .<AquaNecklaceEntity>of(
                                    AquaNecklaceEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(STAND_WIDTH, STAND_HEIGHT)
                            .clientTrackingRange(80)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<
            StandType, AquaNecklaceStandType> AQUA_NECKLACE =
            STAND_TYPES.register(
                    "aqua_necklace",
                    AquaNecklaceAddon::createStand);

    public AquaNecklaceAddon(
            IEventBus modEventBus, ModContainer modContainer) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)) {
            throw new IllegalStateException(
                    "RotP Aqua Necklace requires ROTP addon ABI 1 "
                            + "with namespaced ability resources and "
                            + "Stand visual contexts.");
        }

        ABILITY_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        modEventBus.addListener(
                AquaNecklaceEvents::registerAttributes);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static AquaNecklaceEntity activeStand(
            LivingEntity entity) {
        StandPower power = entity != null
                ? StandPower.get(entity)
                : null;
        return power != null
                && power.hasPower()
                && power.isSummoned()
                && power.getPowerType() == AQUA_NECKLACE.get()
                && power.getSummonedStandEntity()
                        instanceof AquaNecklaceEntity stand
                && stand.isAlive()
                && !stand.isRemoved()
                ? stand
                : null;
    }

    private static AquaNecklaceStandType createStand(
            ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addManualControl()
                .addAbility(
                        CHANGE_TO_LIQUID_NAME,
                        CHANGE_TO_LIQUID)
                .addAbility(
                        CHANGE_TO_GASEOUS_NAME,
                        CHANGE_TO_GASEOUS)
                .addAbility(ENTER_ENTITY_NAME, ENTER_ENTITY)
                .addAbility(LEAVE_ENTITY_NAME, LEAVE_ENTITY)
                .addAbility(DROWN_HOST_NAME, DROWN_HOST)
                .addAbility(HOST_PRESSURE_NAME, HOST_PRESSURE)
                .addAbility(HOST_CRUSH_NAME, HOST_CRUSH)
                .addAbility(BRAIN_ATTACK_NAME, BRAIN_ATTACK)
                .addAbility(FLUID_GUARD_NAME, FLUID_GUARD)
                .makeControlScheme("hotbar")
                    .makeHotbar(
                            0, USE_SPECIAL, SWITCH_SPECIAL)
                    .addToHotbar(
                            CHANGE_TO_LIQUID_NAME,
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            CHANGE_TO_GASEOUS_NAME,
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            ENTER_ENTITY_NAME,
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            LEAVE_ENTITY_NAME,
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            DROWN_HOST_NAME,
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            HOST_PRESSURE_NAME,
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            HOST_CRUSH_NAME,
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            BRAIN_ATTACK_NAME,
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            FLUID_GUARD_NAME,
                            0,
                            InputMethod.HOLD)
                .finalizeControlScheme()
                .makeControlScheme("keybinds")
                    .bind(
                            HOST_PRESSURE_NAME,
                            InputMethod.CLICK,
                            USE_SPECIAL)
                .finalizeControlScheme()
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                "manual_control"))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                CHANGE_TO_LIQUID_NAME))
                .addSkill(
                        StandUnlockableSkill.unlockableAbility(
                                        CHANGE_TO_GASEOUS_NAME,
                                        0)
                                .prerequisiteSkill(
                                        CHANGE_TO_LIQUID_NAME))
                .addSkill(
                        StandUnlockableSkill.unlockableAbility(
                                        ENTER_ENTITY_NAME,
                                        250)
                                .withAbility(LEAVE_ENTITY_NAME))
                .addSkill(
                        StandUnlockableSkill.unlockableAbility(
                                        DROWN_HOST_NAME,
                                        400)
                                .prerequisiteSkill(
                                        ENTER_ENTITY_NAME))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                HOST_PRESSURE_NAME))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                HOST_CRUSH_NAME))
                .addSkill(
                        StandUnlockableSkill.unlockableAbility(
                                        BRAIN_ATTACK_NAME,
                                        350)
                                .prerequisiteSkill(
                                        HOST_CRUSH_NAME))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                FLUID_GUARD_NAME));

        AquaNecklaceStandType stand =
                new AquaNecklaceStandType(
                        new StandStats.Builder()
                                .power(POWER)
                                .speed(SPEED_BASE, SPEED)
                                .range(
                                        EFFECTIVE_RANGE,
                                        MAX_RANGE)
                                .durability(DURABILITY_BASE, DURABILITY)
                                .precision(PRECISION_BASE, PRECISION)
                                .randomWeight(2.0D)
                                .build(),
                        moveset,
                        AQUA_NECKLACE_ENTITY.get(),
                        id);
        stand.standControlType(
                StandControlType.LONG_DISTANCE_OPERATION);
        stand.manualControl(true);
        stand.distanceStrengthDecay(false);
        stand.standDimensions(STAND_WIDTH, STAND_HEIGHT);
        stand.ost(() -> AQUA_NECKLACE_OST);
        return stand;
    }
}
