package rotp.addons.mandom;

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

import rotp.addons.mandom.ability.TimeRewindAbility;
import rotp.addons.mandom.entity.MandomStandEntity;
import rotp.addons.mandom.item.RingoClockItem;
import rotp.addons.mandom.network.RewindEffectPayload;
import rotp.addons.mandom.stand.MandomStandType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(MandomAddon.MOD_ID)
public final class MandomAddon {
    public static final String MOD_ID = "rotp_mandom";

    public static final double POWER = 0.0D;
    public static final double SPEED = 18.0D;
    public static final double EFFECTIVE_RANGE = 2.0D;
    public static final double MAX_RANGE = 2.0D;
    public static final double DURABILITY = 1.0D;
    public static final double PRECISION = 0.0D;
    public static final double RANDOM_WEIGHT = 2.0D;

    private static final int REQUIRED_CORE_ABI = 1;

    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(
                    JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<TimeRewindAbility>>
            TIME_REWIND = ABILITY_TYPES.register(
                    "time_rewind",
                    key -> new AbilityType<>(
                            key, TimeRewindAbility::new));

    public static final DeferredHolder<
            EntityType<?>, EntityType<MandomStandEntity>>
            MANDOM_ENTITY = ENTITY_TYPES.register(
                    "mandom",
                    key -> EntityType.Builder.<MandomStandEntity>of(
                                    MandomStandEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.0F, 0.0F)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<SoundEvent, SoundEvent>
            USER_SUMMON = sound("summon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            STAND_SUMMON = sound("stand_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            STAND_UNSUMMON = sound("stand_unsummon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            REWIND = sound("rewind");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            VOID = sound("void");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            REWIND_START = sound("rewind_start");
    public static final OstSoundList MANDOM_OST =
            new OstSoundList(id("mandom_ost"), SOUND_EVENTS);

    public static final DeferredHolder<Item, RingoClockItem>
            RINGO_CLOCK = ITEMS.register(
                    "ringo_clock",
                    () -> new RingoClockItem(
                            new Item.Properties().durability(
                                    RingoClockItem.MAX_CRACKS)));

    public static final DeferredHolder<StandType, MandomStandType>
            MANDOM = STAND_TYPES.register(
                    "mandom", MandomAddon::createStand);

    public MandomAddon(IEventBus modEventBus) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ADDON_POST_EFFECT_LIFECYCLE_V1)) {
            throw new IllegalStateException(
                    "RotP Mandom requires addon ABI 1 and the "
                            + "Stand visual, namespaced ability, and "
                            + "addon post-effect features.");
        }

        ABILITY_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        ITEMS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        modEventBus.addListener(MandomEvents::registerAttributes);
        modEventBus.addListener(MandomEvents::buildCreativeTab);
        modEventBus.addListener(RewindEffectPayload::register);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static boolean isMandomUser(LivingEntity entity) {
        StandPower power = StandPower.get(entity);
        return power != null && power.getPowerType() == MANDOM.get();
    }

    public static MandomStandEntity controller(LivingEntity entity) {
        StandPower power = StandPower.get(entity);
        return power != null
                && power.getPowerType() == MANDOM.get()
                && power.getSummonedStandEntity()
                        instanceof MandomStandEntity controller
                ? controller
                : null;
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(
            String path) {
        return SOUND_EVENTS.register(
                path, SoundEvent::createVariableRangeEvent);
    }

    private static MandomStandType createStand(ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addAbility("time_rewind", TIME_REWIND)
                .makeControlScheme("hotbar")
                    .makeHotbar(
                            0, USE_SPECIAL, SWITCH_SPECIAL)
                    .addToHotbar(
                            "time_rewind",
                            0,
                            InputMethod.CLICK)
                .finalizeControlScheme()
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                "time_rewind"));

        return new MandomStandType(
                new StandStats.Builder()
                        .power(POWER)
                        .speed(SPEED)
                        .range(EFFECTIVE_RANGE, MAX_RANGE)
                        .durability(DURABILITY)
                        .precision(PRECISION)
                        .randomWeight(RANDOM_WEIGHT)
                        .build(),
                moveset,
                MANDOM_ENTITY.get(),
                id)
                .standControlType(StandControlType.PHENOMENON)
                .manualControl(false)
                .distanceStrengthDecay(false)
                .standDimensions(0.0F, 0.0F)
                .summonShout(() -> USER_SUMMON)
                .summonSound(() -> STAND_SUMMON)
                .unsummonSound(() -> STAND_UNSUMMON)
                .ost(() -> MANDOM_OST);
    }
}
