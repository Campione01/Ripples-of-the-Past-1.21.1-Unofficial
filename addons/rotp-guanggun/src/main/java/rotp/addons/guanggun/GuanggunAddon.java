package rotp.addons.guanggun;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import rotp.core.api.RotpAddonApi;
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

import rotp.addons.guanggun.ability.GuanggunGunAbility;
import rotp.addons.guanggun.entity.GuanggunBulletEntity;
import rotp.addons.guanggun.entity.GuanggunStandEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(GuanggunAddon.MOD_ID)
public final class GuanggunAddon {
    public static final String MOD_ID = "guanggun";
    public static final int REQUIRED_CORE_ABI = 1;

    public static final double EFFECTIVE_RANGE = 100.0D;
    public static final double MAX_RANGE = 100.0D;
    public static final float STAND_WIDTH = 0.4F;
    public static final float STAND_HEIGHT = 2.0F;

    public static final String PUNCH = "guanggun_punch";
    public static final String BARRAGE = "guanggun_barrage";
    public static final String HEAVY_PUNCH = "guanggun_heavy_punch";
    public static final String BLOCK = "guanggun_block";
    public static final String GUN = "guanggun_gun";

    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<GuanggunGunAbility>>
            GUANGGUN_GUN = ABILITY_TYPES.register(
                    GUN,
                    key -> new AbilityType<>(
                            key, GuanggunGunAbility::new));

    public static final DeferredHolder<
            EntityType<?>, EntityType<GuanggunStandEntity>>
            GUANGGUN_ENTITY = ENTITY_TYPES.register(
                    "guanggun",
                    key -> EntityType.Builder.<GuanggunStandEntity>of(
                                    GuanggunStandEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(STAND_WIDTH, STAND_HEIGHT)
                            .clientTrackingRange(128)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<
            EntityType<?>, EntityType<GuanggunBulletEntity>>
            GUANGGUN_BULLET = ENTITY_TYPES.register(
                    "guanggun_bullet",
                    key -> EntityType.Builder.<GuanggunBulletEntity>of(
                                    GuanggunBulletEntity::new,
                                    MobCategory.MISC)
                            .noSummon()
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(128)
                            .updateInterval(1)
                            .build(key.toString()));

    public static final DeferredHolder<SoundEvent, SoundEvent>
            GUANGGUN_SUMMON = sound("guanggun_summon");
    public static final OstSoundList GUANGGUN_OST =
            new OstSoundList(id("guanggun_ost"), SOUND_EVENTS);

    public static final DeferredHolder<StandType, EntityStandType>
            GUANGGUN = STAND_TYPES.register(
                    "guanggun", GuanggunAddon::createStand);

    public GuanggunAddon(IEventBus modEventBus) {
        requireCoreApi();
        ABILITY_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        modEventBus.addListener(GuanggunEvents::registerAttributes);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(
            String path) {
        return SOUND_EVENTS.register(
                path, SoundEvent::createVariableRangeEvent);
    }

    private static void requireCoreApi() {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)) {
            throw new IllegalStateException(
                    "RotP Guanggun requires ROTP addon ABI 1.");
        }
    }

    private static EntityStandType createStand(ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addHumanoidStandStuff()
                .addManualControl()
                .addAbility(PUNCH, ModStandAbilities.PUNCH)
                .addAbility(BARRAGE, ModStandAbilities.BARRAGE)
                .addAbility(HEAVY_PUNCH, ModStandAbilities.HEAVY_PUNCH)
                .addAbility(BLOCK, ModStandAbilities.GUARD)
                .addAbility(GUN, GUANGGUN_GUN)
                .makeControlScheme("hotbar")
                    .bind(PUNCH, InputMethod.CLICK, InputKey.LMB)
                    .bind(BARRAGE, InputMethod.HOLD, InputKey.LMB)
                    .bind(HEAVY_PUNCH, InputMethod.CLICK, InputKey.RMB)
                    .bind(BLOCK, InputMethod.HOLD, InputKey.RMB)
                    .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                    .addToHotbar(GUN, 0, InputMethod.CLICK)
                .finalizeControlScheme()
                .makeControlScheme("keybinds")
                    .bind(PUNCH, InputMethod.CLICK, InputKey.LMB)
                    .bind(BARRAGE, InputMethod.HOLD, InputKey.LMB)
                    .bind(HEAVY_PUNCH, InputMethod.CLICK, InputKey.RMB)
                    .bind(BLOCK, InputMethod.HOLD, InputKey.RMB)
                .finalizeControlScheme()
                .addSkill(StandUnlockableSkill.startingAbility(PUNCH))
                .addSkill(StandUnlockableSkill.startingAbility(BARRAGE))
                .addSkill(StandUnlockableSkill.startingAbility(HEAVY_PUNCH))
                .addSkill(StandUnlockableSkill.startingAbility(BLOCK))
                .addSkill(StandUnlockableSkill.unlockableAbility(GUN, 100))
                .addHumanoidStandSkills();

        return new EntityStandType(
                new StandStats.Builder()
                        .power(14.0D, 16.0D)
                        .speed(12.0D, 14.0D)
                        .range(EFFECTIVE_RANGE, MAX_RANGE)
                        .durability(8.0D, 10.0D)
                        .precision(8.0D, 10.0D)
                        .randomWeight(1.0D)
                        .build(),
                moveset,
                GUANGGUN_ENTITY.get(),
                id)
                .standControlType(
                        StandControlType.LONG_DISTANCE_OPERATION)
                .manualControl(true)
                .distanceStrengthDecay(false)
                .standDimensions(STAND_WIDTH, STAND_HEIGHT)
                .summonShout(() -> GUANGGUN_SUMMON)
                .summonSound(() -> ModSoundEvents.STAND_SUMMON)
                .unsummonSound(() -> ModSoundEvents.STAND_UNSUMMON)
                .ost(() -> GUANGGUN_OST);
    }
}
