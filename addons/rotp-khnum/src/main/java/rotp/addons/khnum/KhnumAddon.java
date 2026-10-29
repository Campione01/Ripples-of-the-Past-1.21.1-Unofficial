package rotp.addons.khnum;

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

import rotp.addons.khnum.ability.KhnumDisguiseAbility;
import rotp.addons.khnum.ability.KhnumDisguiseDisableAbility;
import rotp.addons.khnum.disguise.DisguiseMode;
import rotp.addons.khnum.disguise.KhnumScaleTypes;
import rotp.addons.khnum.entity.KhnumControllerEntity;
import rotp.addons.khnum.network.KhnumPayloads;
import rotp.addons.khnum.stand.KhnumStandType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(KhnumAddon.MOD_ID)
public final class KhnumAddon {
    public static final String MOD_ID = "rotp_khnum";
    private static final int REQUIRED_CORE_ABI = 1;

    public static final String DISGUISE_NAME = "khnum_disguise";
    public static final String DISGUISE_DISABLE_NAME =
            "khnum_disguise_disable";
    public static final String DISGUISE_PLAYER_NAME =
            "khnum_disguise_player";
    public static final String DISGUISE_PLAYER_DISABLE_NAME =
            "khnum_disguise_player_disable";

    public static final DeferredRegister<AbilityType<?>>
            ABILITY_TYPES = DeferredRegister.create(
                    JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<EntityType<?>>
            ENTITY_TYPES = DeferredRegister.create(
                    Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<StandType>
            STAND_TYPES = DeferredRegister.create(
                    JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<KhnumDisguiseAbility>>
            DISGUISE = disguise(
                    DISGUISE_NAME,
                    DisguiseMode.ENTITY,
                    DISGUISE_DISABLE_NAME,
                    0);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<KhnumDisguiseDisableAbility>>
            DISGUISE_DISABLE = disable(
                    DISGUISE_DISABLE_NAME, 0);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<KhnumDisguiseAbility>>
            DISGUISE_PLAYER = disguise(
                    DISGUISE_PLAYER_NAME,
                    DisguiseMode.PLAYER,
                    DISGUISE_PLAYER_DISABLE_NAME,
                    1);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<KhnumDisguiseDisableAbility>>
            DISGUISE_PLAYER_DISABLE = disable(
                    DISGUISE_PLAYER_DISABLE_NAME, 1);

    public static final DeferredHolder<
            EntityType<?>, EntityType<KhnumControllerEntity>>
            CONTROLLER = ENTITY_TYPES.register(
                    "khnum_controller",
                    key -> EntityType.Builder
                            .<KhnumControllerEntity>of(
                                    KhnumControllerEntity::new,
                                    MobCategory.MISC)
                            .noSummon()
                            .sized(
                                    KhnumPhase1Spec.CONTROLLER_WIDTH,
                                    KhnumPhase1Spec.CONTROLLER_HEIGHT)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<StandType, KhnumStandType>
            KHNUM = STAND_TYPES.register(
                    "khnum", KhnumAddon::createStand);

    public KhnumAddon(
            IEventBus modEventBus, ModContainer modContainer) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_VISUAL_CONTEXT_V1)) {
            throw new IllegalStateException(
                    "RotP Khnum requires addon ABI 1 with "
                            + "namespaced ability resources and "
                            + "local Stand visual contexts.");
        }
        KhnumScaleTypes.initialize();
        modContainer.registerConfig(
                ModConfig.Type.COMMON, KhnumConfig.SPEC);
        ABILITY_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        modEventBus.addListener(KhnumGameEvents::registerAttributes);
        modEventBus.addListener(KhnumPayloads::register);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static boolean isKhnumUser(LivingEntity user) {
        StandPower power = StandPower.get(user);
        return power != null && power.getPowerType() == KHNUM.get();
    }

    public static KhnumControllerEntity controller(
            LivingEntity user) {
        StandPower power = user != null ? StandPower.get(user) : null;
        if (power == null
                || power.getPowerType() != KHNUM.get()
                || !(power.getSummonedStandEntity()
                        instanceof KhnumControllerEntity controller)
                || controller.getUser() != user
                || !controller.isAlive()
                || controller.isRemoved()) {
            return null;
        }
        return controller;
    }

    private static DeferredHolder<
            AbilityType<?>, AbilityType<KhnumDisguiseAbility>>
            disguise(
                    String name,
                    DisguiseMode mode,
                    String disableName,
                    int resolveLevel) {
        return ABILITY_TYPES.register(
                name,
                key -> new AbilityType<>(
                        key,
                        (type, id) -> new KhnumDisguiseAbility(
                                type,
                                id,
                                mode,
                                disableName,
                                resolveLevel)));
    }

    private static DeferredHolder<
            AbilityType<?>, AbilityType<KhnumDisguiseDisableAbility>>
            disable(String name, int resolveLevel) {
        return ABILITY_TYPES.register(
                name,
                key -> new AbilityType<>(
                        key,
                        (type, id) ->
                                new KhnumDisguiseDisableAbility(
                                        type, id, resolveLevel)));
    }

    private static KhnumStandType createStand(ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addAbility(DISGUISE_NAME, DISGUISE)
                .addAbility(
                        DISGUISE_DISABLE_NAME, DISGUISE_DISABLE)
                .addAbility(
                        DISGUISE_PLAYER_NAME, DISGUISE_PLAYER)
                .addAbility(
                        DISGUISE_PLAYER_DISABLE_NAME,
                        DISGUISE_PLAYER_DISABLE)
                .makeControlScheme("hotbar")
                    .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                    .addToHotbar(
                            DISGUISE_NAME, 0, InputMethod.CLICK)
                    .addToHotbar(
                            DISGUISE_PLAYER_NAME,
                            0,
                            InputMethod.CLICK)
                .finalizeControlScheme();

        for (String ability : new String[] {
                DISGUISE_NAME,
                DISGUISE_DISABLE_NAME,
                DISGUISE_PLAYER_NAME,
                DISGUISE_PLAYER_DISABLE_NAME
        }) {
            moveset.addSkill(
                    StandUnlockableSkill.startingAbility(ability));
        }

        return new KhnumStandType(
                new StandStats.Builder()
                        .power(KhnumPhase1Spec.POWER)
                        .speed(KhnumPhase1Spec.SPEED)
                        .range(
                                KhnumPhase1Spec.EFFECTIVE_RANGE,
                                KhnumPhase1Spec.MAX_RANGE)
                        .durability(KhnumPhase1Spec.DURABILITY)
                        .precision(KhnumPhase1Spec.PRECISION)
                        .randomWeight(1.0D)
                        .build(),
                moveset,
                CONTROLLER.get(),
                id)
                .standControlType(StandControlType.PHENOMENON)
                .manualControl(false)
                .distanceStrengthDecay(false)
                .standDimensions(
                        KhnumPhase1Spec.CONTROLLER_WIDTH,
                        KhnumPhase1Spec.CONTROLLER_HEIGHT);
    }
}
