package rotp.addons.wonderofu;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import rotp.core.api.RotpAddonApi;
import rotp.core.api.stand.StandSkillWalls;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.util.sound.OstSoundList;

import rotp.addons.wonderofu.ability.CalamityPassiveAbility;
import rotp.addons.wonderofu.ability.CalamityActiveAbility;
import rotp.addons.wonderofu.ability.CarCalamityAbility;
import rotp.addons.wonderofu.ability.ExplosionAbility;
import rotp.addons.wonderofu.ability.LightningStrikeAbility;
import rotp.addons.wonderofu.entity.CarCalamityEntity;
import rotp.addons.wonderofu.entity.WonderOfUStandEntity;
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

@Mod(WonderOfUAddon.MOD_ID)
public final class WonderOfUAddon {
    public static final String MOD_ID = "rotp_wou";
    private static final int REQUIRED_CORE_ABI = 1;

    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<CalamityPassiveAbility>>
            CALAMITY_PASSIVE = ABILITY_TYPES.register(
                    "calamity_passive",
                    key -> new AbilityType<>(
                            key, CalamityPassiveAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<CalamityActiveAbility>>
            CALAMITY_ACTIVE = ABILITY_TYPES.register(
                    "calamity_active",
                    key -> new AbilityType<>(
                            key, CalamityActiveAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<CarCalamityAbility>>
            CALAMITY_CAR_ATTACK = ABILITY_TYPES.register(
                    "calamity_car_attack",
                    key -> new AbilityType<>(
                            key, CarCalamityAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<LightningStrikeAbility>>
            LIGHTNING_STRIKE = ABILITY_TYPES.register(
                    "lightning_strike",
                    key -> new AbilityType<>(
                            key, LightningStrikeAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<ExplosionAbility>>
            EXPLOSION = ABILITY_TYPES.register(
                    "explosion",
                    key -> new AbilityType<>(key, ExplosionAbility::new));

    public static final DeferredHolder<
            EntityType<?>, EntityType<WonderOfUStandEntity>>
            WONDER_OF_YOU_ENTITY = ENTITY_TYPES.register(
                    "wonder_of_you",
                    key -> EntityType.Builder.<WonderOfUStandEntity>of(
                                    WonderOfUStandEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.7F, 2.1F)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));
    public static final DeferredHolder<
            EntityType<?>, EntityType<CarCalamityEntity>>
            CAR_CALAMITY_ENTITY = ENTITY_TYPES.register(
                    "car_projectile",
                    key -> EntityType.Builder.<CarCalamityEntity>of(
                                    CarCalamityEntity::new,
                                    MobCategory.MISC)
                            .noSummon()
                            .sized(2.0F, 2.0F)
                            .clientTrackingRange(96)
                            .updateInterval(1)
                            .build(key.toString()));

    public static final DeferredHolder<SoundEvent, SoundEvent>
            SUMMON_VOICELINE = registerSound("summon_voiceline");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            CALAMITY1 = registerSound("calamity1");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            CALAMITY2 = registerSound("calamity2");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            CALAMITY3 = registerSound("calamity3");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            CALAMITY4 = registerSound("calamity4");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            CALAMITY5 = registerSound("calamity5");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            CAR_DAMAGE = registerSound("car_damage");
    public static final OstSoundList WOU_OST =
            new OstSoundList(id("wou_ost"), SOUND_EVENTS);

    public static final DeferredHolder<StandType, EntityStandType>
            WONDER_OF_YOU = STAND_TYPES.register(
                    "wonder_of_you", WonderOfUAddon::createStand);

    public WonderOfUAddon(IEventBus modEventBus) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ABILITY_SELECTION_VISUAL_POLICIES_V1)) {
            throw new IllegalStateException(
                    "RotP Wonder of U requires ROTP addon ABI 1 with "
                            + "namespaced ability resources and Stand "
                            + "visual contexts, plus ability selection "
                            + "visual policies.");
        }

        ABILITY_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        modEventBus.addListener(
                WonderOfUGameEvents::registerAttributes);
        modEventBus.addListener(WonderOfUAddon::markSkillsThroughWalls);
    }

    // Calamity strikes at its target as a law of misfortune (as 1.16's
    // 100-block entity ray did): the lightning and the explosions reach a
    // target behind walls; the punches are Wonder of U's own body.
    private static void markSkillsThroughWalls(FMLCommonSetupEvent event) {
        StandSkillWalls.markThroughWalls(
                WONDER_OF_YOU.getId(), "lightning_strike");
        StandSkillWalls.markThroughWalls(WONDER_OF_YOU.getId(), "explosion");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> registerSound(
            String path) {
        return SOUND_EVENTS.register(
                path, SoundEvent::createVariableRangeEvent);
    }

    private static EntityStandType createStand(ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addAbility("punch", ModStandAbilities.PUNCH)
                .addAbility(
                        "heavy_punch",
                        ModStandAbilities.HEAVY_PUNCH)
                .addAbility(
                        "heavy_charged",
                        ModStandAbilities.HEAVY_CHARGED)
                .addAbility(
                        "finisher_punch",
                        ModStandAbilities.HEAVY_PUNCH,
                        punch -> punch.initIsFinisher("heavy_punch"))
                .addAbility("guard", ModStandAbilities.GUARD)
                .addAbility("calamity_passive", CALAMITY_PASSIVE)
                .addAbility("calamity_active", CALAMITY_ACTIVE)
                .addAbility(
                        "calamity_car_attack",
                        CALAMITY_CAR_ATTACK)
                .addAbility("lightning_strike", LIGHTNING_STRIKE)
                .addAbility("explosion", EXPLOSION)
                .makeControlScheme("hotbar")
                    .bind("punch", InputMethod.CLICK, InputKey.LMB)
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
                    .addToHotbar(
                            "calamity_passive",
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            "calamity_active",
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            "calamity_car_attack",
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            "lightning_strike",
                            0,
                            InputMethod.HOLD)
                    .addHotbarSlotVariation(
                            "explosion",
                            "lightning_strike",
                            InputKey.Modifier.SHIFT,
                            InputMethod.HOLD)
                .finalizeControlScheme()
                .makeControlScheme("keybinds")
                    .bind("punch", InputMethod.CLICK, InputKey.LMB)
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
                .addSkill(StandUnlockableSkill.startingAbility(
                        "heavy_punch"))
                .addSkill(StandUnlockableSkill.startingAbility(
                                "heavy_charged")
                        .prerequisiteSkill("heavy_punch"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "finisher_punch"))
                .addSkill(StandUnlockableSkill.startingAbility("guard"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "calamity_passive"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "calamity_active"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "calamity_car_attack"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "lightning_strike"))
                .addSkill(StandUnlockableSkill.startingAbility(
                                "explosion")
                        .prerequisiteSkill("lightning_strike"));

        return new EntityStandType(
                new StandStats.Builder()
                        .power(18.0)
                        .speed(8.0)
                        .range(10000.0, 10000.0)
                        .durability(9.0)
                        .precision(14.0)
                        .build(),
                moveset,
                WONDER_OF_YOU_ENTITY.get(),
                id)
                .standControlType(StandControlType.AUTOMATIC)
                .manualControl(false)
                .distanceStrengthDecay(false)
                .standDimensions(0.7F, 2.1F)
                .summonShout(() -> CALAMITY5)
                .summonSound(() -> ModSoundEvents.STAND_SUMMON)
                .unsummonSound(() -> ModSoundEvents.STAND_UNSUMMON)
                .ost(() -> WOU_OST);
    }
}
