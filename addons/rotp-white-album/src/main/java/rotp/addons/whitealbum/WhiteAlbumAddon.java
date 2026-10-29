package rotp.addons.whitealbum;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

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

import rotp.addons.whitealbum.ability.FreezeFloorAbility;
import rotp.addons.whitealbum.ability.GentlyWeepsAbility;
import rotp.addons.whitealbum.ability.UnfreezeAbility;
import rotp.addons.whitealbum.ability.WhiteAlbumFreezeAbility;
import rotp.addons.whitealbum.ability.WhiteAlbumGuardAbility;
import rotp.addons.whitealbum.ability.WhiteAlbumPunchAbility;
import rotp.addons.whitealbum.block.WhiteAlbumIceLayerBlock;
import rotp.addons.whitealbum.entity.WhiteAlbumControllerEntity;
import rotp.addons.whitealbum.stand.WhiteAlbumStandType;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(WhiteAlbumAddon.MOD_ID)
public final class WhiteAlbumAddon {
    public static final String MOD_ID = "rotp_zwa";
    private static final int REQUIRED_CORE_ABI = 1;

    public static final String PUNCH_NAME = "punch";
    public static final String GUARD_NAME = "guard";
    public static final String FREEZE_NAME = "wa_freeze";
    public static final String FREEZE_FLOOR_NAME = "wa_freeze_water";
    public static final String UNFREEZE_NAME = "wa_unfreeze";
    public static final String GENTLY_WEEPS_NAME = "wa_reflec";
    public static final String GENTLY_WEEPS_OFF_SPRITE = "wa_unreflec";

    public static final DeferredRegister<AbilityType<?>>
            ABILITY_TYPES = DeferredRegister.create(
                    JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<Block>
            BLOCKS = DeferredRegister.create(
                    Registries.BLOCK, MOD_ID);
    public static final DeferredRegister<EntityType<?>>
            ENTITY_TYPES = DeferredRegister.create(
                    Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<ParticleType<?>>
            PARTICLE_TYPES = DeferredRegister.create(
                    Registries.PARTICLE_TYPE, MOD_ID);
    public static final DeferredRegister<SoundEvent>
            SOUND_EVENTS = DeferredRegister.create(
                    Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<StandType>
            STAND_TYPES = DeferredRegister.create(
                    JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);

    // 1.16 wa_punch: the core light punch plus knockback(2.0) on a hit.
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<WhiteAlbumPunchAbility>>
            PUNCH = ABILITY_TYPES.register(
                    "wa_punch",
                    key -> new AbilityType<>(
                            key, WhiteAlbumPunchAbility::new));
    // 1.16 wa_block (FrozenBlock): the guard, held, let no other action through.
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<WhiteAlbumGuardAbility>>
            GUARD = ABILITY_TYPES.register(
                    "wa_block",
                    key -> new AbilityType<>(
                            key, WhiteAlbumGuardAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<WhiteAlbumFreezeAbility>>
            FREEZE = ABILITY_TYPES.register(
                    FREEZE_NAME,
                    key -> new AbilityType<>(
                            key, WhiteAlbumFreezeAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<FreezeFloorAbility>>
            FREEZE_FLOOR = ABILITY_TYPES.register(
                    FREEZE_FLOOR_NAME,
                    key -> new AbilityType<>(
                            key, FreezeFloorAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<UnfreezeAbility>>
            UNFREEZE = ABILITY_TYPES.register(
                    UNFREEZE_NAME,
                    key -> new AbilityType<>(
                            key, UnfreezeAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<GentlyWeepsAbility>>
            GENTLY_WEEPS = ABILITY_TYPES.register(
                    GENTLY_WEEPS_NAME,
                    key -> new AbilityType<>(
                            key, GentlyWeepsAbility::new));

    public static final DeferredHolder<Block, WhiteAlbumIceLayerBlock>
            ICE_LAYER = BLOCKS.register(
                    "wa_ice_layer",
                    key -> new WhiteAlbumIceLayerBlock(
                            BlockBehaviour.Properties
                                    .ofFullCopy(Blocks.FROSTED_ICE)
                                    .friction(0.98F)
                                    .noOcclusion()));

    public static final DeferredHolder<
            EntityType<?>, EntityType<WhiteAlbumControllerEntity>>
            WHITE_ALBUM_CONTROLLER = ENTITY_TYPES.register(
                    "white_album_controller",
                    key -> EntityType.Builder
                            .<WhiteAlbumControllerEntity>of(
                                    WhiteAlbumControllerEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(
                                    WhiteAlbumSpec.CONTROLLER_WIDTH,
                                    WhiteAlbumSpec.CONTROLLER_HEIGHT)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<
            ParticleType<?>, SimpleParticleType>
            ICE_SHARD = PARTICLE_TYPES.register(
                    "ice_shard",
                    () -> new SimpleParticleType(false));

    public static final DeferredHolder<SoundEvent, SoundEvent>
            WA_SUMMON = sound("wa_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            WA_UNSUMMON = sound("wa_unsummon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            ICE_SHIELD = sound("ice_shield");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            GHACCIO_WA = sound("ghaccio_wa");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            WA_DEFLECT = sound("wa_deflect");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            WA_GW = sound("wa_gw");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            USER_GW_ON = sound("us_gw_su");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            WA_GW_ON = sound("wa_gw_su");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            USER_GW_OFF = sound("us_gw_usu");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            WA_GW_OFF = sound("wa_gw_usu");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            WA_PUNCH = sound("wa_punch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            WA_HEAVY_PUNCH = sound("wa_heavy_punch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            WA_UNFREEZE = sound("wa_unfreeze");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            USER_UNFREEZE = sound("us_unfreeze");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            WA_FREEZE = sound("wa_freeze");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            USER_FREEZE = sound("us_freeze");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            WA_WALKER = sound("wa_walker");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            WA_ORA = sound("wa_ora");

    public static final DeferredHolder<
            StandType, WhiteAlbumStandType> WHITE_ALBUM =
            STAND_TYPES.register(
                    "white_album", WhiteAlbumAddon::createStand);

    public WhiteAlbumAddon(IEventBus modEventBus) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_VISUAL_CONTEXT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ABILITY_SELECTION_VISUAL_POLICIES_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_PLAYER_BASE_MODEL_VISIBILITY_POLICIES_V1)
                // attack-strength reset on perform (R146 core)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ENTITY_ACTION_HOOKS_V1)
                // guardsHitsOnUser opt-out (R147 core)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_USER_GUARD_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ABILITY_SHOUTS_V1)) {
            throw new IllegalStateException(
                    "RotP White Album requires addon ABI 1 with "
                            + "namespaced ability resources and local "
                            + "Stand visual contexts, plus ability "
                            + "selection visuals, base-player "
                            + "visibility policies, entity action "
                            + "hooks and the Stand user guard opt-out "
                            + "(a core from the same release).");
        }
        // the ice / controller code reads the integrated_stand effect (R148 core)
        if (!RotpAddonApi.supportsFeature(
                RotpAddonApi.FEATURE_INTEGRATED_STAND_V1)) {
            throw new IllegalStateException(
                    "RotP White Album requires the core integrated "
                            + "Stand effect (a core from the same "
                            + "release).");
        }

        ABILITY_TYPES.register(modEventBus);
        BLOCKS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        PARTICLE_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        modEventBus.addListener(
                WhiteAlbumGameEvents::registerAttributes);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                MOD_ID, path);
    }

    public static boolean isWhiteAlbumUser(
            LivingEntity user) {
        StandPower power = StandPower.get(user);
        return power != null
                && power.getPowerType() == WHITE_ALBUM.get();
    }

    public static WhiteAlbumControllerEntity controller(
            LivingEntity user) {
        StandPower power = StandPower.get(user);
        if (power == null
                || power.getPowerType() != WHITE_ALBUM.get()
                || !(power.getSummonedStandEntity()
                        instanceof WhiteAlbumControllerEntity
                        controller)
                || controller.getUser() != user
                || !controller.isAlive()
                || controller.isRemoved()) {
            return null;
        }
        return controller;
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(
            String path) {
        return SOUND_EVENTS.register(
                path, SoundEvent::createVariableRangeEvent);
    }

    private static WhiteAlbumStandType createStand(
            ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addAbility(
                        PUNCH_NAME,
                        PUNCH,
                        punch -> {
                            punch.punchImpactSound(WA_PUNCH);
                            punch.standCrySound(WA_ORA);
                            // 1.16 wa_punch was swingHand() without
                            // withUserPunch: performing it reset the
                            // user's attack strength.
                            punch.setResetsAttackStrengthOnPerform();
                        })
                .addAbility(
                        GUARD_NAME,
                        GUARD,
                        guard -> guard.cooldown(
                                WhiteAlbumSpec
                                        .GUARD_COOLDOWN_TICKS))
                .addAbility(
                        FREEZE_NAME,
                        FREEZE,
                        ability -> ability.resolveLevelToUnlock(2))
                .addAbility(
                        FREEZE_FLOOR_NAME,
                        FREEZE_FLOOR,
                        ability -> ability.resolveLevelToUnlock(1))
                .addAbility(UNFREEZE_NAME, UNFREEZE)
                .addAbility(
                        GENTLY_WEEPS_NAME,
                        GENTLY_WEEPS,
                        ability -> ability.resolveLevelToUnlock(4))
                .makeControlScheme("hotbar")
                    .bind(
                            PUNCH_NAME,
                            InputMethod.CLICK,
                            InputKey.LMB)
                    .bind(
                            GUARD_NAME,
                            InputMethod.HOLD,
                            InputKey.RMB)
                    .makeHotbar(
                            0, USE_SPECIAL, SWITCH_SPECIAL)
                    .addToHotbar(
                            FREEZE_NAME,
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            FREEZE_FLOOR_NAME,
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            GENTLY_WEEPS_NAME,
                            0,
                            InputMethod.CLICK)
                    // 1.16's Shift variation replaced the whole slot; the
                    // core keeps Shift on it (no fall back to the Freeze
                    // hold), so the click fires on the press.
                    .addHotbarSlotVariation(
                            UNFREEZE_NAME,
                            FREEZE_NAME,
                            InputKey.Modifier.SHIFT,
                            InputMethod.CLICK)
                .finalizeControlScheme()
                .makeControlScheme("keybinds")
                    .bind(
                            PUNCH_NAME,
                            InputMethod.CLICK,
                            InputKey.LMB)
                    .bind(
                            GUARD_NAME,
                            InputMethod.HOLD,
                            InputKey.RMB)
                .finalizeControlScheme()
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                PUNCH_NAME))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                GUARD_NAME))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                FREEZE_NAME))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                FREEZE_FLOOR_NAME))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                UNFREEZE_NAME))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                GENTLY_WEEPS_NAME));

        return new WhiteAlbumStandType(
                new StandStats.Builder()
                        .power(WhiteAlbumSpec.POWER)
                        .speed(WhiteAlbumSpec.SPEED)
                        .range(
                                WhiteAlbumSpec.EFFECTIVE_RANGE,
                                WhiteAlbumSpec.MAX_RANGE)
                        .durability(
                                WhiteAlbumSpec.DURABILITY)
                        .precision(
                                WhiteAlbumSpec.PRECISION)
                        .randomWeight(1.0D)
                        .build(),
                moveset,
                WHITE_ALBUM_CONTROLLER.get(),
                id)
                .standControlType(StandControlType.PHENOMENON)
                .manualControl(false)
                .distanceStrengthDecay(false)
                .standDimensions(
                        WhiteAlbumSpec.CONTROLLER_WIDTH,
                        WhiteAlbumSpec.CONTROLLER_HEIGHT)
                .summonSound(() -> WA_SUMMON)
                .unsummonSound(() -> WA_UNSUMMON)
                .summonShout(() -> GHACCIO_WA);
    }
}
