package rotp.addons.jumpingjackflash;

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
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.util.sound.OstSoundList;

import rotp.addons.jumpingjackflash.ability.JumpingJackFlashAbility;
import rotp.addons.jumpingjackflash.ability.JumpingJackFlashAbility.Mode;
import rotp.addons.jumpingjackflash.entity.GravityDomainEntity;
import rotp.addons.jumpingjackflash.entity.JumpingJackFlashStandEntity;
import rotp.addons.jumpingjackflash.entity.TrackingBulletEntity;
import rotp.addons.jumpingjackflash.entity.TrackingSnowballEntity;
import rotp.addons.jumpingjackflash.stand.JumpingJackFlashStandType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(JumpingJackFlashAddon.MOD_ID)
public final class JumpingJackFlashAddon {
    public static final String MOD_ID = "jumping_jack_flash";
    public static final int REQUIRED_CORE_ABI = 1;
    public static final double EFFECTIVE_RANGE = 20.0D;
    public static final double MAX_RANGE = 20.0D;

    public static final String PUNCH = "jumping_jack_flash_punch";
    public static final String BARRAGE = "jumping_jack_flash_barrage";
    public static final String FINISHER =
            "jumping_jack_flash_finisher_punch";
    public static final String HEAVY =
            "jumping_jack_flash_heavy_punch";
    public static final String GUARD = "jumping_jack_flash_block";
    public static final String VOLLEY =
            "jumping_jack_flash_throw_pickaxe";
    public static final String GRAVITY_SNOWBALL = "cd_blood_cutter1";
    public static final String CANCEL_GRAVITY = "cd_blood_cutter";
    public static final String DOMAIN = "jumping_jack_flash_domain";
    public static final String DOMAIN_CLOSE =
            "jumping_jack_flash_domain_close";

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
            AbilityType<?>, AbilityType<JumpingJackFlashAbility>>
            VOLLEY_ABILITY = ability(VOLLEY, Mode.VOLLEY);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<JumpingJackFlashAbility>>
            GRAVITY_SNOWBALL_ABILITY =
                    ability(GRAVITY_SNOWBALL, Mode.GRAVITY_SNOWBALL);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<JumpingJackFlashAbility>>
            CANCEL_GRAVITY_ABILITY =
                    ability(CANCEL_GRAVITY, Mode.CANCEL_GRAVITY);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<JumpingJackFlashAbility>>
            DOMAIN_ABILITY = ability(DOMAIN, Mode.DOMAIN_OPEN);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<JumpingJackFlashAbility>>
            DOMAIN_CLOSE_ABILITY =
                    ability(DOMAIN_CLOSE, Mode.DOMAIN_CLOSE);

    public static final DeferredHolder<
            EntityType<?>, EntityType<JumpingJackFlashStandEntity>>
            STAND_ENTITY = ENTITY_TYPES.register(
                    "jumping_jack_flash",
                    key -> EntityType.Builder
                            .<JumpingJackFlashStandEntity>of(
                                    JumpingJackFlashStandEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.7F, 2.1F)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));
    public static final DeferredHolder<
            EntityType<?>, EntityType<TrackingBulletEntity>>
            TRACKING_BULLET = ENTITY_TYPES.register(
                    "tracking_bullet",
                    key -> EntityType.Builder
                            .<TrackingBulletEntity>of(
                                    TrackingBulletEntity::new,
                                    MobCategory.MISC)
                            .noSummon()
                            .sized(0.0625F, 0.0625F)
                            .clientTrackingRange(64)
                            .updateInterval(1)
                            .build(key.toString()));
    public static final DeferredHolder<
            EntityType<?>, EntityType<TrackingSnowballEntity>>
            TRACKING_SNOWBALL = ENTITY_TYPES.register(
                    "tracking_snowball",
                    key -> EntityType.Builder
                            .<TrackingSnowballEntity>of(
                                    TrackingSnowballEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(64)
                            .updateInterval(1)
                            .build(key.toString()));
    public static final DeferredHolder<
            EntityType<?>, EntityType<GravityDomainEntity>>
            GRAVITY_DOMAIN = ENTITY_TYPES.register(
                    "gravity_domain",
                    key -> EntityType.Builder
                            .<GravityDomainEntity>of(
                                    GravityDomainEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .fireImmune()
                            .sized(1.0F, 1.0F)
                            .clientTrackingRange(96)
                            .updateInterval(1)
                            .build(key.toString()));

    public static final DeferredHolder<SoundEvent, SoundEvent>
            SUMMON_SHOUT = sound("jumping_jack_flash_summon_shout");
    public static final OstSoundList OST =
            new OstSoundList(id("example_stand_ost"), SOUND_EVENTS);

    public static final DeferredHolder<
            StandType, JumpingJackFlashStandType>
            JUMPING_JACK_FLASH = STAND_TYPES.register(
                    "jumping_jack_flash",
                    JumpingJackFlashAddon::createStand);

    public JumpingJackFlashAddon(IEventBus modEventBus) {
        requireCoreApi();
        ABILITY_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        modEventBus.addListener(
                JumpingJackFlashEvents::registerAttributes);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static boolean isUser(LivingEntity entity) {
        StandPower power = StandPower.get(entity);
        return power != null
                && power.getPowerType() == JUMPING_JACK_FLASH.get();
    }

    public static JumpingJackFlashStandEntity activeStand(
            LivingEntity user) {
        StandPower power = StandPower.get(user);
        StandEntity stand = power != null
                ? power.getSummonedStandEntity()
                : null;
        return stand instanceof JumpingJackFlashStandEntity jff
                && jff.isAlive() ? jff : null;
    }

    private static DeferredHolder<
            AbilityType<?>, AbilityType<JumpingJackFlashAbility>>
            ability(String name, Mode mode) {
        return ABILITY_TYPES.register(
                name,
                key -> new AbilityType<>(
                        key,
                        (type, abilityId) ->
                                new JumpingJackFlashAbility(
                                        type, abilityId, mode)));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(
            String name) {
        return SOUND_EVENTS.register(
                name,
                () -> SoundEvent.createVariableRangeEvent(id(name)));
    }

    private static void requireCoreApi() {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)
                // canFireReleasedHold (R146 core held-action rechecks)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ENTITY_ACTION_HOOKS_V1)) {
            throw new IllegalStateException(
                    "Jumpin' Jack Flash requires ROTP addon ABI 1 "
                            + "with entity action hooks.");
        }
    }

    private static JumpingJackFlashStandType createStand(
            ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addHumanoidStandStuff()
                .addAbility(PUNCH, ModStandAbilities.PUNCH)
                .addAbility(BARRAGE, ModStandAbilities.BARRAGE)
                .addAbility(HEAVY, ModStandAbilities.HEAVY_PUNCH)
                .addAbility(FINISHER, ModStandAbilities.HEAVY_CHARGED)
                .addAbility(GUARD, ModStandAbilities.GUARD)
                .addAbility(VOLLEY, VOLLEY_ABILITY)
                .addAbility(GRAVITY_SNOWBALL, GRAVITY_SNOWBALL_ABILITY)
                .addAbility(CANCEL_GRAVITY, CANCEL_GRAVITY_ABILITY)
                .addAbility(DOMAIN, DOMAIN_ABILITY)
                .addAbility(DOMAIN_CLOSE, DOMAIN_CLOSE_ABILITY)
                .makeControlScheme("hotbar")
                    .bind(PUNCH, InputMethod.CLICK, InputKey.LMB)
                    .bind(BARRAGE, InputMethod.HOLD, InputKey.LMB)
                    .bind(HEAVY, InputMethod.CLICK, InputKey.RMB)
                    .bind(GUARD, InputMethod.HOLD, InputKey.RMB)
                    .bind(
                            FINISHER,
                            InputMethod.HOLD,
                            MovesetBuilder.DEFAULT_CHARGED_HEAVY_INPUT)
                    .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                    .addToHotbar(VOLLEY, 0, InputMethod.HOLD)
                    .addToHotbar(
                            GRAVITY_SNOWBALL, 0, InputMethod.HOLD)
                    .addHotbarSlotVariation(
                            CANCEL_GRAVITY,
                            GRAVITY_SNOWBALL,
                            InputKey.Modifier.SHIFT,
                            InputMethod.CLICK)
                    .addToHotbar(DOMAIN, 0, InputMethod.HOLD)
                    .addHotbarSlotVariation(
                            DOMAIN_CLOSE,
                            DOMAIN,
                            InputKey.Modifier.SHIFT,
                            InputMethod.CLICK)
                .finalizeControlScheme()
                .makeControlScheme("keybinds")
                    .bind(PUNCH, InputMethod.CLICK, InputKey.LMB)
                    .bind(BARRAGE, InputMethod.HOLD, InputKey.LMB)
                    .bind(HEAVY, InputMethod.CLICK, InputKey.RMB)
                    .bind(GUARD, InputMethod.HOLD, InputKey.RMB)
                    .bind(
                            FINISHER,
                            InputMethod.HOLD,
                            MovesetBuilder.DEFAULT_CHARGED_HEAVY_INPUT)
                .finalizeControlScheme()
                .addSkill(StandUnlockableSkill.startingAbility(PUNCH))
                .addSkill(StandUnlockableSkill.startingAbility(BARRAGE))
                .addSkill(
                        StandUnlockableSkill.startingAbility(HEAVY)
                                .withAbility(FINISHER))
                .addSkill(StandUnlockableSkill.startingAbility(GUARD))
                .addSkill(StandUnlockableSkill.startingAbility(VOLLEY))
                .addSkill(
                        StandUnlockableSkill
                                .startingAbility(GRAVITY_SNOWBALL)
                                .withAbility(CANCEL_GRAVITY))
                .addSkill(
                        StandUnlockableSkill.startingAbility(DOMAIN)
                                .withAbility(DOMAIN_CLOSE))
                .addHumanoidStandSkills();

        JumpingJackFlashStandType stand =
                new JumpingJackFlashStandType(
                        new StandStats.Builder()
                                .power(12.0D)
                                .speed(8.0D)
                                .range(EFFECTIVE_RANGE, MAX_RANGE)
                                .durability(15.0D)
                                .precision(5.0D)
                                .build(),
                        moveset,
                        STAND_ENTITY.get(),
                        id);
        stand.standDimensions(0.7F, 2.1F);
        stand.summonShout(() -> SUMMON_SHOUT);
        stand.summonSound(() -> ModSoundEvents.STAND_SUMMON);
        stand.unsummonSound(() -> ModSoundEvents.STAND_UNSUMMON);
        stand.ost(() -> OST);
        return stand;
    }
}
