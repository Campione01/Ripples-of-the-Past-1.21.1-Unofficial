package rotp.addons.kiss;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import rotp.core.api.RotpAddonApi;
import rotp.core.core.JojoRegistries;
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

import rotp.addons.kiss.ability.KissBarrageAbility;
import rotp.addons.kiss.ability.KissSpecialAbility;
import rotp.addons.kiss.ability.KissSpecialAbility.Mode;
import rotp.addons.kiss.entity.KissBombEntity;
import rotp.addons.kiss.entity.KissStickerEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(KissAddon.MOD_ID)
public final class KissAddon {
    public static final String MOD_ID = "kiss";
    public static final String PUNCH = "kiss_punch";
    public static final String BARRAGE = "kiss_barrage";
    public static final String FINISHER = "kiss_finisher_punch";
    public static final String HEAVY = "kiss_heavy_punch";
    public static final String CHARGED_HEAVY = "heavy_charged";
    public static final String BLOCK = "kiss_block";
    public static final String STICKER_PLACE = "kiss_sticker_place";
    public static final String STICKER = "kiss_sticker";
    public static final String PULL = "kiss_pull";
    public static final String BOMB = "kiss_bomb";

    public static final float EFFECTIVE_RANGE = 4.0F;
    public static final float MAX_RANGE = 12.0F;
    public static final float ENTITY_WIDTH = 0.7F;
    public static final float ENTITY_HEIGHT = 2.1F;

    private static final int REQUIRED_CORE_ABI = 1;

    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);

    public static final DeferredHolder<AbilityType<?>, AbilityType<KissBarrageAbility>>
            BARRAGE_ABILITY = ABILITY_TYPES.register(
                    BARRAGE, key -> new AbilityType<>(key, KissBarrageAbility::new));
    public static final DeferredHolder<AbilityType<?>, AbilityType<KissSpecialAbility>>
            STICKER_PLACE_ABILITY = special(STICKER_PLACE, Mode.ATTACH);
    public static final DeferredHolder<AbilityType<?>, AbilityType<KissSpecialAbility>>
            STICKER_ABILITY = special(STICKER, Mode.LAUNCH);
    public static final DeferredHolder<AbilityType<?>, AbilityType<KissSpecialAbility>>
            PULL_ABILITY = special(PULL, Mode.PULL);
    public static final DeferredHolder<AbilityType<?>, AbilityType<KissSpecialAbility>>
            BOMB_ABILITY = special(BOMB, Mode.BOMB);

    public static final DeferredHolder<EntityType<?>, EntityType<StandEntity>>
            KISS_ENTITY = ENTITY_TYPES.register(
                    "kiss",
                    key -> EntityType.Builder.<StandEntity>of(
                                    StandEntity::new, MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(ENTITY_WIDTH, ENTITY_HEIGHT)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<KissStickerEntity>>
            STICKER_ENTITY = ENTITY_TYPES.register(
                    "kiss_sticker",
                    key -> EntityType.Builder.<KissStickerEntity>of(
                                    KissStickerEntity::new, MobCategory.MISC)
                            .noSummon()
                            .sized(0.3F, 0.3F)
                            .clientTrackingRange(64)
                            .updateInterval(1)
                            .build(key.toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<KissBombEntity>>
            BOMB_ENTITY = ENTITY_TYPES.register(
                    "kiss_bomb",
                    key -> EntityType.Builder.<KissBombEntity>of(
                                    KissBombEntity::new, MobCategory.MISC)
                            .noSummon()
                            .sized(0.3F, 0.3F)
                            .clientTrackingRange(64)
                            .updateInterval(1)
                            .build(key.toString()));

    public static final DeferredHolder<SoundEvent, SoundEvent> SUMMON = sound("kiss_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent> SUMMON_VOICE =
            sound("kiss_summon_voiceline");
    public static final DeferredHolder<SoundEvent, SoundEvent> BARRAGE_VOICE =
            sound("kiss_lianda");
    public static final DeferredHolder<SoundEvent, SoundEvent> FINISHER_VOICE =
            sound("kiss_teshuzhongji");
    public static final DeferredHolder<SoundEvent, SoundEvent> HEAVY_VOICE =
            sound("kiss_zhongji");
    public static final DeferredHolder<SoundEvent, SoundEvent> STICKER_SOUND =
            sound("kiss_tiezhi");
    public static final DeferredHolder<SoundEvent, SoundEvent> PULL_SOUND =
            sound("kiss_tiezhi2");

    public static final DeferredHolder<StandType, EntityStandType> KISS =
            STAND_TYPES.register("kiss", KissAddon::createStand);

    public KissAddon(IEventBus modEventBus) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)) {
            throw new IllegalStateException(
                    "RotP Kiss requires ROTP addon ABI 1 with namespaced ability resources and Stand visual contexts.");
        }
        ABILITY_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        modEventBus.addListener(KissEvents::registerAttributes);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static boolean isKissUser(Player player) {
        StandPower power = StandPower.get(player);
        return power != null && power.getPowerType() == KISS.get();
    }

    private static DeferredHolder<AbilityType<?>, AbilityType<KissSpecialAbility>> special(
            String name, Mode mode) {
        return ABILITY_TYPES.register(
                name,
                key -> new AbilityType<>(
                        key,
                        (type, id) -> new KissSpecialAbility(type, id, mode)));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUND_EVENTS.register(name, SoundEvent::createVariableRangeEvent);
    }

    private static EntityStandType createStand(ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addHumanoidStandStuff()
                .addAbility(PUNCH, ModStandAbilities.PUNCH)
                // 1.16 KissBarrageAction.getSounds gave the PERFORM cry unfiltered, so it cried with the arms only too
                .addAbility(BARRAGE, BARRAGE_ABILITY,
                        barrage -> barrage.barrageCrySound(BARRAGE_VOICE)
                                .barrageCryInArmsOnly())
                // 1.16 KissHeavyPunchAction.getSounds: kiss_zhongji at the PERFORM, unfiltered, so with the arms only too
                .addAbility(HEAVY, ModStandAbilities.HEAVY_PUNCH,
                        punch -> punch.heavyPunchPerformSound(HEAVY_VOICE))
                // 1.16 095 InitStands :61-62: kiss_finisher_punch (KissFinisherAttack) was the heavy's finisher
                // variation, crying kiss_teshuzhongji at its PERFORM (getSounds)
                .addAbility(FINISHER, ModStandAbilities.HEAVY_PUNCH,
                        punch -> {
                            punch.heavyPunchPerformSound(FINISHER_VOICE);
                            punch.initIsFinisher(HEAVY);
                        })
                .addAbility(CHARGED_HEAVY, ModStandAbilities.HEAVY_CHARGED)
                .addAbility(BLOCK, ModStandAbilities.GUARD)
                .addAbility(STICKER_PLACE, STICKER_PLACE_ABILITY)
                .addAbility(STICKER, STICKER_ABILITY)
                .addAbility(PULL, PULL_ABILITY)
                .addAbility(BOMB, BOMB_ABILITY)
                .makeControlScheme("hotbar")
                    .bind(PUNCH, InputMethod.CLICK, InputKey.LMB)
                    .bind(BARRAGE, InputMethod.HOLD, InputKey.LMB)
                    .bind(HEAVY, InputMethod.CLICK, InputKey.RMB)
                    .bind(CHARGED_HEAVY, InputMethod.HOLD,
                            MovesetBuilder.DEFAULT_CHARGED_HEAVY_INPUT)
                    .bind(BLOCK, InputMethod.HOLD, InputKey.RMB)
                    .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                    .addToHotbar(STICKER_PLACE, 0, InputMethod.CLICK)
                    .addToHotbar(STICKER, 0, InputMethod.CLICK)
                    .addToHotbar(PULL, 0, InputMethod.HOLD)
                    .addToHotbar(BOMB, 0, InputMethod.CLICK)
                .finalizeControlScheme()
                .makeControlScheme("keybinds")
                    .bind(PUNCH, InputMethod.CLICK, InputKey.LMB)
                    .bind(BARRAGE, InputMethod.HOLD, InputKey.LMB)
                    .bind(HEAVY, InputMethod.CLICK, InputKey.RMB)
                    .bind(CHARGED_HEAVY, InputMethod.HOLD,
                            MovesetBuilder.DEFAULT_CHARGED_HEAVY_INPUT)
                    .bind(BLOCK, InputMethod.HOLD, InputKey.RMB)
                .finalizeControlScheme()
                .addSkill(StandUnlockableSkill.startingAbility(PUNCH))
                .addSkill(StandUnlockableSkill.startingAbility(BARRAGE))
                .addSkill(StandUnlockableSkill.startingAbility(HEAVY))
                .addSkill(StandUnlockableSkill.startingAbility(FINISHER))
                .addSkill(StandUnlockableSkill.startingAbility(CHARGED_HEAVY))
                .addSkill(StandUnlockableSkill.startingAbility(BLOCK))
                .addSkill(StandUnlockableSkill.startingAbility(STICKER_PLACE))
                .addSkill(StandUnlockableSkill.startingAbility(STICKER))
                .addSkill(StandUnlockableSkill.startingAbility(PULL))
                .addSkill(StandUnlockableSkill.startingAbility(BOMB))
                .addSkill(StandUnlockableSkill.startingAbility("manual_control"));

        return new EntityStandType(
                new StandStats.Builder()
                        .power(17.0)
                        .speed(17.0)
                        .range(EFFECTIVE_RANGE, MAX_RANGE)
                        .durability(17.0)
                        .precision(8.0)
                        .build(),
                moveset,
                KISS_ENTITY.get(),
                id)
                .standControlType(StandControlType.CLOSE_RANGE_DIRECT)
                .manualControl(true)
                .distanceStrengthDecay(true)
                .standDimensions(ENTITY_WIDTH, ENTITY_HEIGHT)
                .summonSound(() -> SUMMON)
                .summonShout(() -> SUMMON_VOICE);
    }
}
