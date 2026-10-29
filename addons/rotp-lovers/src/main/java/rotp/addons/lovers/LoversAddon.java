package rotp.addons.lovers;

import java.util.function.Supplier;

import rotp.core.api.RotpAddonApi;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.type.StandType;

import rotp.addons.lovers.ability.IntoEntityAbility;
import rotp.addons.lovers.ability.RetractAbility;
import rotp.addons.lovers.entity.LoversStandEntity;
import rotp.addons.lovers.stand.LoversStandType;
import rotp.addons.lovers.state.LoversLinkState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(LoversAddon.MOD_ID)
public final class LoversAddon {
    public static final String MOD_ID = "rotp_lovers";

    public static final double POWER = 2.0D;
    public static final double SPEED = 6.0D;
    public static final double EFFECTIVE_RANGE = 250.0D;
    public static final double MAX_RANGE = 300.0D;
    public static final double DURABILITY = 14.0D;
    public static final double PRECISION = 6.0D;
    public static final float LEGACY_STAND_WIDTH = 0.65F;
    public static final float LEGACY_STAND_HEIGHT = 1.8F;
    public static final float STAND_WIDTH = LEGACY_STAND_WIDTH;
    public static final float STAND_HEIGHT = LEGACY_STAND_HEIGHT;

    private static final int REQUIRED_CORE_ABI = 1;
    public static final String INTO_ENTITY_NAME = "into_entity";
    public static final String RETRACT_NAME = "retract";

    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(
                    JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<AttachmentType<?>>
            ATTACHMENT_TYPES = DeferredRegister.create(
                    NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(
                    JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<IntoEntityAbility>> INTO_ENTITY =
            ABILITY_TYPES.register(
                    INTO_ENTITY_NAME,
                    key -> new AbilityType<>(
                            key, IntoEntityAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<RetractAbility>> RETRACT =
            ABILITY_TYPES.register(
                    RETRACT_NAME,
                    key -> new AbilityType<>(key, RetractAbility::new));

    public static final Supplier<AttachmentType<LoversLinkState>>
            LINK_STATE = ATTACHMENT_TYPES.register(
                    "link_state",
                    () -> AttachmentType.builder(LoversLinkState::new)
                            .sync(
                                    (holder, player) -> holder == player,
                                    LoversLinkState.STREAM_CODEC)
                            .build());

    public static final DeferredHolder<
            EntityType<?>, EntityType<LoversStandEntity>> LOVERS_ENTITY =
            ENTITY_TYPES.register(
                    "lovers",
                    key -> EntityType.Builder
                            .<LoversStandEntity>of(
                                    LoversStandEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(STAND_WIDTH, STAND_HEIGHT)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<SoundEvent, SoundEvent> VOID =
            SOUND_EVENTS.register(
                    "void", SoundEvent::createVariableRangeEvent);

    public static final DeferredHolder<StandType, LoversStandType>
            LOVERS = STAND_TYPES.register(
                    "lovers", LoversAddon::createStand);

    public LoversAddon(
            IEventBus modEventBus, ModContainer modContainer) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_RANDOM_WEIGHTS_V1)) {
            throw new IllegalStateException(
                    "RotP Lovers requires ROTP addon ABI 1 with "
                            + "namespaced ability resources and Stand "
                            + "visual contexts and Stand random weights.");
        }

        ABILITY_TYPES.register(modEventBus);
        ATTACHMENT_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        modEventBus.addListener(LoversEvents::registerAttributes);
        modContainer.registerConfig(
                ModConfig.Type.COMMON, LoversConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static LoversLinkState linkState(LivingEntity entity) {
        return entity.getData(LINK_STATE);
    }

    public static boolean isLoversUser(LivingEntity entity) {
        StandPower power = entity != null ? StandPower.get(entity) : null;
        return power != null
                && power.hasPower()
                && power.getPowerType() == LOVERS.get();
    }

    public static LoversStandEntity activeStand(
            LivingEntity entity) {
        StandPower power = entity != null ? StandPower.get(entity) : null;
        return power != null
                && power.hasPower()
                && power.isSummoned()
                && power.getPowerType() == LOVERS.get()
                && power.getSummonedStandEntity()
                        instanceof LoversStandEntity stand
                && stand.isAlive()
                && !stand.isRemoved()
                ? stand
                : null;
    }

    private static LoversStandType createStand(
            ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addAbility(INTO_ENTITY_NAME, INTO_ENTITY)
                .addAbility(RETRACT_NAME, RETRACT)
                .makeControlScheme("hotbar")
                    .bind(
                            INTO_ENTITY_NAME,
                            InputMethod.CLICK,
                            InputKey.LMB)
                    .bind(
                            RETRACT_NAME,
                            InputMethod.CLICK,
                            InputKey.RMB)
                .finalizeControlScheme()
                .makeControlScheme("keybinds")
                    .bind(
                            INTO_ENTITY_NAME,
                            InputMethod.CLICK,
                            InputKey.LMB)
                    .bind(
                            RETRACT_NAME,
                            InputMethod.CLICK,
                            InputKey.RMB)
                .finalizeControlScheme()
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                INTO_ENTITY_NAME))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                RETRACT_NAME));

        LoversStandType stand = new LoversStandType(
                new StandStats.Builder()
                        .power(POWER)
                        .speed(SPEED)
                        .range(EFFECTIVE_RANGE, MAX_RANGE)
                        .durability(DURABILITY)
                        .precision(PRECISION)
                        .randomWeight(2.0D)
                        .build(),
                moveset,
                LOVERS_ENTITY.get(),
                id);
        stand.standControlType(StandControlType.PHENOMENON);
        stand.manualControl(false);
        stand.distanceStrengthDecay(false);
        stand.standDimensions(STAND_WIDTH, STAND_HEIGHT);
        stand.summonSound(() -> VOID);
        stand.unsummonSound(() -> VOID);
        return stand;
    }
}
