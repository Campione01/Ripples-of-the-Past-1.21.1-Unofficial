package rotp.addons.softandwet;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import rotp.core.api.RotpAddonApi;
import rotp.core.core.JojoRegistries;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.util.sound.OstSoundList;

import rotp.addons.softandwet.ability.BubbleVolleyAbility;
import rotp.addons.softandwet.ability.BubbleVolleyAbility.Pattern;
import rotp.addons.softandwet.ability.SoftAndWetFinisherAbility;
import rotp.addons.softandwet.ability.SoftAndWetHeavyPunchAbility;
import rotp.addons.softandwet.entity.SoftAndWetBubbleEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(SoftAndWetAddon.MOD_ID)
public final class SoftAndWetAddon {
    public static final String MOD_ID = "rotp_sw";
    private static final int REQUIRED_CORE_ABI = 1;

    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);

    public static final DeferredHolder<AbilityType<?>, AbilityType<BubbleVolleyAbility>>
            BUBBLES = ABILITY_TYPES.register(
                    "soft_and_wet_bubbles",
                    key -> new AbilityType<>(key, BubbleVolleyAbility::new));
    public static final DeferredHolder<AbilityType<?>, AbilityType<BubbleVolleyAbility>>
            BUBBLE_BLIND = ABILITY_TYPES.register(
                    "soft_and_wet_bubble_blind",
                    key -> new AbilityType<>(
                            key,
                            (type, abilityId) ->
                                    new BubbleVolleyAbility(
                                            type,
                                            abilityId,
                                            Pattern.BLIND)));
    public static final DeferredHolder<AbilityType<?>, AbilityType<BubbleVolleyAbility>>
            BUBBLE_STUN = ABILITY_TYPES.register(
                    "soft_and_wet_bubble_stun",
                    key -> new AbilityType<>(
                            key,
                            (type, abilityId) ->
                                    new BubbleVolleyAbility(
                                            type,
                                            abilityId,
                                            Pattern.STUN)));
    public static final DeferredHolder<AbilityType<?>, AbilityType<BubbleVolleyAbility>>
            BUBBLE_EXPLOSION = ABILITY_TYPES.register(
                    "soft_and_wet_bubble_explosion",
                    key -> new AbilityType<>(
                            key,
                            (type, abilityId) ->
                                    new BubbleVolleyAbility(
                                            type,
                                            abilityId,
                                            Pattern.EXPLOSION)));
    public static final DeferredHolder<AbilityType<?>, AbilityType<BubbleVolleyAbility>>
            BUBBLE_WALL = ABILITY_TYPES.register(
                    "soft_and_wet_bubble_wall",
                    key -> new AbilityType<>(
                            key,
                            (type, abilityId) ->
                                    new BubbleVolleyAbility(
                                            type,
                                            abilityId,
                                            Pattern.WALL)));
    // 1.16 heavy punch shouted JOSUKE_PUNCH_HEAVY from the user
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<SoftAndWetHeavyPunchAbility>>
            HEAVY_PUNCH = ABILITY_TYPES.register(
                    "soft_and_wet_heavy_punch",
                    key -> new AbilityType<>(
                            key, SoftAndWetHeavyPunchAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<SoftAndWetFinisherAbility>>
            COMBO_PUNCH = ABILITY_TYPES.register(
                    "soft_and_wet_combo_punch",
                    key -> new AbilityType<>(
                            key, SoftAndWetFinisherAbility::new));

    public static final DeferredHolder<EntityType<?>, EntityType<StandEntity>>
            SOFT_AND_WET_ENTITY = ENTITY_TYPES.register(
                    "soft_and_wet",
                    key -> EntityType.Builder.<StandEntity>of(
                                    StandEntity::new, MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.66F, 2.0F)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<EntityType<?>, EntityType<SoftAndWetBubbleEntity>>
            BUBBLE_ENTITY = ENTITY_TYPES.register(
                    "soft_and_wet_bubble",
                    key -> EntityType.Builder.<SoftAndWetBubbleEntity>of(
                                    SoftAndWetBubbleEntity::new, MobCategory.MISC)
                            .noSummon()
                            .sized(0.15F, 0.15F)
                            .clientTrackingRange(64)
                            .updateInterval(10)
                            .build(key.toString()));

    public static final DeferredHolder<SoundEvent, SoundEvent>
            JOSUKE_SOFT_AND_WET = sound("josuke_soft_and_wet");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            JOSUKE_BUBBLE_SHOOT = sound("josuke_bubble_shoot");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            JOSUKE_PUNCH_HEAVY = sound("josuke_punch_heavy");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SOFT_AND_WET_PUNCH = sound("soft_and_wet_punch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SOFT_AND_WET_BUBBLE_SOUND = sound("soft_and_wet_bubbles");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SOFT_AND_WET_SHIELD_UNSUMMON =
                    sound("soft_and_wet_shield_unsummon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SOFT_AND_WET_SHIELD = sound("soft_and_wet_shield");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SOFT_AND_WET_BARRAGE = sound("soft_and_wet_barrage");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SOFT_AND_WET_PUNCH_HEAVY = sound("soft_and_wet_punch_heavy");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SOFT_AND_WET_SUMMON = sound("soft_and_wet_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SOFT_AND_WET_UNSUMMON = sound("soft_and_wet_unsummon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SOFT_AND_WET_ORA_ORA_ORA = sound("soft_and_wet_ora_ora_ora");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SOFT_AND_WET_ORA = sound("soft_and_wet_ora");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SOFT_AND_WET_ORA_LONG = sound("soft_and_wet_ora_long");

    public static final OstSoundList SOFT_AND_WET_OST =
            new OstSoundList(id("soft_and_wet_ost"), SOUND_EVENTS);

    public static final DeferredHolder<StandType, EntityStandType> SOFT_AND_WET =
            STAND_TYPES.register("soft_and_wet", SoftAndWetAddon::createStand);

    public SoftAndWetAddon(IEventBus modEventBus) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_SHOUTS_V1)) {
            throw new IllegalStateException(
                    "RotP Soft And Wet requires ROTP addon ABI 1 with namespaced "
                            + "ability resources, stand visual contexts and "
                            + "ability shouts.");
        }

        ABILITY_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUND_EVENTS.register(name, SoundEvent::createVariableRangeEvent);
    }

    private static EntityStandType createStand(ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addHumanoidStandStuff()
                .addAbility("punch", ModStandAbilities.PUNCH, punch -> {
                    punch.punchImpactSound(SOFT_AND_WET_PUNCH);
                    punch.standCrySound(SOFT_AND_WET_ORA);
                })
                .addAbility("barrage", ModStandAbilities.BARRAGE, barrage -> {
                    barrage.barrageHitSound(SOFT_AND_WET_BARRAGE);
                    barrage.barrageCrySound(SOFT_AND_WET_ORA_ORA_ORA);
                    // 1.16 one-argument standSound: cried with the arms only too
                    barrage.barrageCryInArmsOnly();
                })
                .addAbility("heavy_punch", HEAVY_PUNCH, punch -> {
                    punch.heavyPunchImpactSound(SOFT_AND_WET_PUNCH_HEAVY);
                })
                .addAbility("heavy_charged", ModStandAbilities.HEAVY_CHARGED)
                .addAbility("combo_punch", COMBO_PUNCH, punch -> {
                    punch.heavyPunchImpactSound(
                            SOFT_AND_WET_PUNCH_HEAVY);
                    punch.heavyPunchCrySound(
                            SOFT_AND_WET_ORA_LONG);
                    punch.initIsFinisher("heavy_punch");
                })
                .addAbility("guard", ModStandAbilities.GUARD)
                .addAbility("bubbles", BUBBLES)
                .addAbility("bubble_blind", BUBBLE_BLIND)
                .addAbility("bubble_stun", BUBBLE_STUN)
                .addAbility("bubble_explosion", BUBBLE_EXPLOSION)
                .addAbility("bubble_wall", BUBBLE_WALL)
                .makeControlScheme("hotbar")
                    .bind("punch", InputMethod.CLICK, InputKey.LMB)
                    .bind("barrage", InputMethod.HOLD, InputKey.LMB)
                     .bind("heavy_punch", InputMethod.CLICK, InputKey.RMB)
                    .bind(
                            "heavy_punch",
                            InputMethod.CLICK,
                            MovesetBuilder.DEFAULT_GRAB_INPUT)
                    .bind(
                            "heavy_charged",
                            InputMethod.HOLD,
                            MovesetBuilder.DEFAULT_CHARGED_HEAVY_INPUT)
                    .bind("guard", InputMethod.HOLD, InputKey.RMB)
                    .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                    .addToHotbar("bubbles", 0, InputMethod.CLICK)
                    .addToHotbar(
                            "bubble_blind", 0, InputMethod.CLICK)
                    .addToHotbar(
                            "bubble_stun", 0, InputMethod.CLICK)
                    .addToHotbar(
                            "bubble_explosion", 0, InputMethod.CLICK)
                    .addToHotbar(
                            "bubble_wall", 0, InputMethod.HOLD)
                .finalizeControlScheme()
                .makeControlScheme("keybinds")
                    .bind("punch", InputMethod.CLICK, InputKey.LMB)
                    .bind("barrage", InputMethod.HOLD, InputKey.LMB)
                     .bind("heavy_punch", InputMethod.CLICK, InputKey.RMB)
                    .bind(
                            "heavy_punch",
                            InputMethod.CLICK,
                            MovesetBuilder.DEFAULT_GRAB_INPUT)
                    .bind(
                            "heavy_charged",
                            InputMethod.HOLD,
                            MovesetBuilder.DEFAULT_CHARGED_HEAVY_INPUT)
                    .bind("guard", InputMethod.HOLD, InputKey.RMB)
                .finalizeControlScheme()
                .addSkill(StandUnlockableSkill.startingAbility("punch"))
                .addSkill(StandUnlockableSkill.startingAbility("barrage"))
                .addSkill(StandUnlockableSkill.startingAbility("heavy_punch"))
                .addSkill(StandUnlockableSkill.startingAbility("heavy_charged")
                        .prerequisiteSkill("heavy_punch"))
                .addSkill(StandUnlockableSkill.startingAbility("bubbles"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "bubble_blind"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "bubble_stun"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "bubble_explosion"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "bubble_wall"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "combo_punch"))
                .addHumanoidStandSkills();

        return new EntityStandType(
                new StandStats.Builder()
                        .power(10.0)
                        .speed(13.0)
                        .range(2.5, 5.0)
                        .durability(13.0)
                        .precision(10.0)
                        .build(),
                moveset,
                SOFT_AND_WET_ENTITY.get(),
                id)
                .standControlType(StandControlType.CLOSE_RANGE_DIRECT)
                .manualControl(true)
                .distanceStrengthDecay(true)
                .standDimensions(0.66F, 2.0F)
                .summonShout(() -> JOSUKE_SOFT_AND_WET)
                .summonSound(() -> SOFT_AND_WET_SUMMON)
                .unsummonSound(() -> SOFT_AND_WET_UNSUMMON)
                .ost(() -> SOFT_AND_WET_OST);
    }
}
