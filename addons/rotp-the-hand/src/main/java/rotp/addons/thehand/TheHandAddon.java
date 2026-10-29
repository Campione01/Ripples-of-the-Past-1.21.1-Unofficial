package rotp.addons.thehand;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import java.util.function.Supplier;

import rotp.core.api.RotpAddonApi;
import rotp.core.api.stand.StandSkillWalls;
import rotp.core.core.JojoRegistries;
import rotp.core.customobjects.RoadRollerEntity;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

import rotp.addons.thehand.ability.TheHandEraseAbility;
import rotp.addons.thehand.ability.TheHandEraseItemAbility;
import rotp.addons.thehand.ability.TheHandErasureBarrageAbility;
import rotp.addons.thehand.ability.TheHandKickAbility;
import rotp.addons.thehand.effect.ErasedHealthEffect;
import rotp.addons.thehand.effect.SurpriseEffect;
import rotp.addons.thehand.network.ErasedHealthSyncPayload;
import rotp.addons.thehand.state.ErasedHealthState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(TheHandAddon.MOD_ID)
public final class TheHandAddon {
    public static final String MOD_ID = "rotp_the_hand";
    public static final String ERASE_NAME = "the_hand_erase";
    public static final String ERASURE_BARRAGE_NAME =
            "the_hand_erasure_barrage";
    public static final String ERASE_ITEM_NAME = "the_hand_erase_item";
    public static final String KICK_NAME = "kick";

    private static final int REQUIRED_CORE_ABI = 1;

    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(
                    NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);

    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<TheHandEraseAbility>>
            THE_HAND_ERASE = ABILITY_TYPES.register(
                    ERASE_NAME,
                    key -> new AbilityType<>(key, TheHandEraseAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<TheHandErasureBarrageAbility>>
            THE_HAND_ERASURE_BARRAGE = ABILITY_TYPES.register(
                    ERASURE_BARRAGE_NAME,
                    key -> new AbilityType<>(
                            key, TheHandErasureBarrageAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<TheHandEraseItemAbility>>
            THE_HAND_ERASE_ITEM = ABILITY_TYPES.register(
                    ERASE_ITEM_NAME,
                    key -> new AbilityType<>(
                            key, TheHandEraseItemAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<TheHandKickAbility>>
            THE_HAND_KICK = ABILITY_TYPES.register(
                    "the_hand_kick",
                    key -> new AbilityType<>(key, TheHandKickAbility::new));

    public static final Supplier<AttachmentType<ErasedHealthState>>
            ERASED_HEALTH = ATTACHMENT_TYPES.register(
                    "erased_health",
                    () -> AttachmentType.serializable(
                                    holder -> holder instanceof LivingEntity
                                                    || holder
                                                            instanceof RoadRollerEntity
                                            ? new ErasedHealthState()
                                            : null)
                            .build());

    public static final DeferredHolder<EntityType<?>, EntityType<StandEntity>>
            THE_HAND_ENTITY = ENTITY_TYPES.register(
                    "the_hand",
                    key -> EntityType.Builder.<StandEntity>of(
                                    StandEntity::new, MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.65F, 1.95F)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<MobEffect, ErasedHealthEffect>
            ERASED_EFFECT = MOB_EFFECTS.register(
                    "erased", key -> new ErasedHealthEffect());
    public static final DeferredHolder<MobEffect, SurpriseEffect>
            SURPRISE = MOB_EFFECTS.register(
                    "surprise", key -> new SurpriseEffect());

    public static final DeferredHolder<SoundEvent, SoundEvent>
            OKUYASU_THE_HAND = registerSound("okuyasu_the_hand");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            THE_HAND_SUMMON = registerSound("the_hand_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            THE_HAND_PUNCH_LIGHT = registerSound("the_hand_punch_light");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            THE_HAND_PUNCH_HEAVY = registerSound("the_hand_punch_heavy");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            THE_HAND_ERASE_SOUND = registerSound("the_hand_erase");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            THE_HAND_ERASURE_BARRAGE_SOUND =
                    registerSound("the_hand_erasure_barrage");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            THE_HAND_KICK_HEAVY =
                    registerSound("the_hand_kick_heavy");

    public static final DeferredHolder<StandType, EntityStandType> THE_HAND =
            STAND_TYPES.register("the_hand", TheHandAddon::createStand);

    public TheHandAddon(
            IEventBus modEventBus, ModContainer modContainer) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)
                // canFireReleasedHold (R146 core held-action rechecks)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ENTITY_ACTION_HOOKS_V1)
                // the Erase slot's green (isActionHeldReadyToFire)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_HELD_READY_TO_FIRE_V1)) {
            throw new IllegalStateException(
                    "RotP The Hand requires ROTP addon ABI 1 with "
                            + "namespaced ability resources, stand visual "
                            + "contexts, entity action hooks and the held "
                            + "ready-to-fire HUD.");
        }

        ABILITY_TYPES.register(modEventBus);
        ATTACHMENT_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        MOB_EFFECTS.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        modEventBus.addListener(TheHandAddon::registerPayloads);
        modEventBus.addListener(TheHandEvents::registerAttributes);
        modEventBus.addListener(TheHandAddon::markSkillsThroughWalls);
        modContainer.registerConfig(
                ModConfig.Type.COMMON, TheHandConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static ErasedHealthState erasedHealthState(Entity entity) {
        return entity.getData(ERASED_HEALTH);
    }

    public static boolean isTheHandUser(LivingEntity entity) {
        StandPower power = StandPower.get(entity);
        return power != null && power.getPowerType() == THE_HAND.get();
    }

    public static void syncErasedHealth(LivingEntity entity) {
        if (entity.level().isClientSide()) {
            return;
        }
        ErasedHealthState state = erasedHealthState(entity);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                entity,
                new ErasedHealthSyncPayload(
                        entity.getId(),
                        state.erasedHealth(),
                        state.expiresAtGameTime()));
    }

    // The swipe erases the matter or the space in its way (The Hand's power
    // crosses matter): Erase, its pull and teleport, and the erasure barrage
    // pass walls; the ordinary punches, barrage and kick are bodily blows.
    private static void markSkillsThroughWalls(FMLCommonSetupEvent event) {
        StandSkillWalls.markThroughWalls(THE_HAND.getId(), ERASE_NAME);
        StandSkillWalls.markThroughWalls(
                THE_HAND.getId(), ERASURE_BARRAGE_NAME);
    }

    private static void registerPayloads(
            RegisterPayloadHandlersEvent event) {
        event.registrar("2").playToClient(
                ErasedHealthSyncPayload.TYPE,
                ErasedHealthSyncPayload.STREAM_CODEC,
                ErasedHealthSyncPayload::handle);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> registerSound(
            String path) {
        return SOUND_EVENTS.register(
                path, SoundEvent::createVariableRangeEvent);
    }

    private static EntityStandType createStand(ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addHumanoidStandStuff()
                .addAbility(
                        "punch",
                        ModStandAbilities.PUNCH,
                        punch -> punch.punchImpactSound(
                                THE_HAND_PUNCH_LIGHT))
                .addAbility(
                        "barrage",
                        ModStandAbilities.BARRAGE,
                        barrage -> barrage.barrageHitSound(
                                THE_HAND_PUNCH_LIGHT))
                .addAbility(
                        "heavy_punch",
                        ModStandAbilities.HEAVY_PUNCH,
                        punch -> punch.heavyPunchImpactSound(
                                THE_HAND_PUNCH_HEAVY))
                .addAbility(
                        "heavy_charged",
                        ModStandAbilities.HEAVY_CHARGED)
                .addAbility(
                        KICK_NAME,
                        THE_HAND_KICK,
                        kick -> {
                            kick.initIsFinisher("heavy_punch");
                            kick.resolveLevelToUnlock(1);
                            kick.heavyPunchImpactSound(
                                    THE_HAND_KICK_HEAVY);
                        })
                .addAbility("guard", ModStandAbilities.GUARD)
                .addAbility(
                        ERASE_NAME,
                        THE_HAND_ERASE,
                        ability -> ability.resolveLevelToUnlock(2))
                .addAbility(
                        ERASURE_BARRAGE_NAME,
                        THE_HAND_ERASURE_BARRAGE,
                        ability -> ability.resolveLevelToUnlock(4))
                .addAbility(
                        ERASE_ITEM_NAME,
                        THE_HAND_ERASE_ITEM,
                        ability -> ability.resolveLevelToUnlock(3))
                .makeControlScheme("hotbar")
                    .bind("punch", InputMethod.CLICK, InputKey.LMB)
                    .bind("barrage", InputMethod.HOLD, InputKey.LMB)
                    .bind(
                            "heavy_punch",
                            InputMethod.CLICK,
                            InputKey.RMB)
                    .bind(
                            "heavy_charged",
                            InputMethod.HOLD,
                            MovesetBuilder.DEFAULT_CHARGED_HEAVY_INPUT)
                    .bind("guard", InputMethod.HOLD, InputKey.RMB)
                    .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                    .addToHotbar(ERASE_NAME, 0, InputMethod.HOLD)
                    .addToHotbar(
                            ERASURE_BARRAGE_NAME,
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(ERASE_ITEM_NAME, 0, InputMethod.HOLD)
                .finalizeControlScheme()
                .makeControlScheme("keybinds")
                    .bind("punch", InputMethod.CLICK, InputKey.LMB)
                    .bind("barrage", InputMethod.HOLD, InputKey.LMB)
                    .bind(
                            "heavy_punch",
                            InputMethod.CLICK,
                            InputKey.RMB)
                    .bind(
                            "heavy_charged",
                            InputMethod.HOLD,
                            MovesetBuilder.DEFAULT_CHARGED_HEAVY_INPUT)
                    .bind("guard", InputMethod.HOLD, InputKey.RMB)
                .finalizeControlScheme()
                .addSkill(StandUnlockableSkill.startingAbility("punch"))
                .addSkill(StandUnlockableSkill.startingAbility("barrage"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "heavy_punch"))
                .addSkill(StandUnlockableSkill.startingAbility(
                                "heavy_charged")
                        .prerequisiteSkill("heavy_punch"))
                .addSkill(StandUnlockableSkill.startingAbility(KICK_NAME)
                        .prerequisiteSkill("heavy_punch"))
                .addSkill(StandUnlockableSkill.startingAbility("guard"))
                .addSkill(StandUnlockableSkill.startingAbility(ERASE_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        ERASURE_BARRAGE_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        ERASE_ITEM_NAME))
                .addHumanoidStandSkills();

        return new EntityStandType(
                new StandStats.Builder()
                        .power(12.0)
                        .speed(11.0, 12.0)
                        .range(2.0, 4.0)
                        .durability(10.0)
                        .precision(8.0, 10.0)
                        .randomWeight(1.0D)
                        .build(),
                moveset,
                THE_HAND_ENTITY.get(),
                id)
                .standControlType(StandControlType.CLOSE_RANGE_DIRECT)
                .manualControl(true)
                .distanceStrengthDecay(true)
                .standDimensions(0.65F, 1.95F)
                .summonShout(() -> OKUYASU_THE_HAND)
                .summonSound(() -> THE_HAND_SUMMON)
                .unsummonSound(() -> ModSoundEvents.STAND_UNSUMMON);
    }
}
