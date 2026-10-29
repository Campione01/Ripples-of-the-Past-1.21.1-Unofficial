package rotp.addons.stonefree;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import java.util.function.Supplier;

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
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.type.StandType;

import rotp.addons.stonefree.ability.StoneFreeBarrageAbility;
import rotp.addons.stonefree.ability.StoneFreeChargedHeavyAbility;
import rotp.addons.stonefree.ability.StoneFreeExtendedPunchAbility;
import rotp.addons.stonefree.ability.StoneFreePunchAbility;
import rotp.addons.stonefree.ability.StoneFreeStringAbility;
import rotp.addons.stonefree.ability.StoneFreeStringPunchAbility;
import rotp.addons.stonefree.effect.MobiusStripEffect;
import rotp.addons.stonefree.effect.StringDecompositionEffect;
import rotp.addons.stonefree.entity.StoneFreeStandEntity;
import rotp.addons.stonefree.entity.StoneFreeStringEntity;
import rotp.addons.stonefree.state.StoneFreeState;
import rotp.addons.stonefree.stand.StoneFreeStandType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(StoneFreeAddon.MOD_ID)
public final class StoneFreeAddon {
    public static final String MOD_ID = "rotp_stone_free";
    public static final int REQUIRED_CORE_ABI = 1;

    public static final String STONE_FREE_PUNCH_NAME =
            "stone_free_punch";
    public static final String USER_STRING_ATTACK_NAME =
            "stone_free_user_string_attack";
    public static final String STONE_FREE_BARRAGE_NAME =
            "stone_free_barrage";
    public static final String USER_STRING_SWEEP_NAME =
            "stone_free_user_string_sweep";
    public static final String STRING_PUNCH_NAME =
            "stone_free_string_punch";
    public static final String STRING_CAPTURE_NAME =
            "stone_free_string_capture";
    public static final String HEAVY_PUNCH_NAME =
            "stone_free_heavy_punch";
    public static final String USER_STRING_WHIP_NAME =
            "stone_free_user_string_whip";
    public static final String STRING_BIND_NAME =
            "stone_free_attack_binding";
    public static final String USER_STRING_BIND_NAME =
            "stone_free_user_attack_binding";
    public static final String EXTENDED_PUNCH_NAME =
            "stone_free_extended_punch";
    public static final String USER_STRING_PICK_NAME =
            "stone_free_user_string_pick";
    public static final String BLOCK_NAME =
            "stone_free_block";
    public static final String USER_GRAPPLE_NAME =
            "stone_free_user_grapple";
    public static final String USER_GRAPPLE_ENTITY_NAME =
            "stone_free_user_grapple_entity";
    public static final String USER_RECOVER_STRING_NAME =
            "stone_free_user_recover_string";
    public static final String GRAPPLE_ENTITY_NAME =
            "stone_free_grapple_entity";
    public static final String USER_CLOSE_WOUNDS_NAME =
            "stone_free_user_close_wounds";
    public static final String USER_BARRIER_NAME =
            "stone_free_user_barrier";
    public static final String USER_REMOVE_BARRIER_NAME =
            "stone_free_user_remove_barrier";
    public static final String USER_MOBIUS_STRIP_NAME =
            "stone_free_user_mobius_strip";

    public static final DeferredRegister<AbilityType<?>>
            ABILITY_TYPES = DeferredRegister.create(
                    JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<AttachmentType<?>>
            ATTACHMENT_TYPES = DeferredRegister.create(
                    NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);
    public static final DeferredRegister<EntityType<?>>
            ENTITY_TYPES = DeferredRegister.create(
                    Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<MobEffect>
            MOB_EFFECTS = DeferredRegister.create(
                    Registries.MOB_EFFECT, MOD_ID);
    public static final DeferredRegister<SoundEvent>
            SOUND_EVENTS = DeferredRegister.create(
                    Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<StandType>
            STAND_TYPES = DeferredRegister.create(
                    JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<StoneFreePunchAbility>>
            STONE_FREE_PUNCH = ABILITY_TYPES.register(
                    STONE_FREE_PUNCH_NAME,
                    key -> new AbilityType<>(
                            key, StoneFreePunchAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<StoneFreeBarrageAbility>>
            STONE_FREE_BARRAGE = ABILITY_TYPES.register(
                    STONE_FREE_BARRAGE_NAME,
                    key -> new AbilityType<>(
                            key, StoneFreeBarrageAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<StoneFreeStringPunchAbility>>
            STONE_FREE_STRING_PUNCH = ABILITY_TYPES.register(
                    STRING_PUNCH_NAME,
                    key -> new AbilityType<>(
                            key, StoneFreeStringPunchAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<rotp.core.impl
                    .stands._entitybase.StandEntityHeavyPunchAbility>>
            STONE_FREE_HEAVY_PUNCH = ABILITY_TYPES.register(
                    HEAVY_PUNCH_NAME,
                    key -> new AbilityType<>(
                            key,
                            StoneFreeStringPunchAbility.HeavyPunch::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<StoneFreeChargedHeavyAbility>>
            STONE_FREE_HEAVY_CHARGED = ABILITY_TYPES.register(
                    "stone_free_heavy_charged",
                    key -> new AbilityType<>(
                            key, StoneFreeChargedHeavyAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<StoneFreeExtendedPunchAbility>>
            STONE_FREE_EXTENDED_PUNCH = ABILITY_TYPES.register(
                    EXTENDED_PUNCH_NAME,
                    key -> new AbilityType<>(
                            key,
                            StoneFreeExtendedPunchAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<rotp.core.impl
                    .stands._entitybase.StandEntityBlockAbility>>
            STONE_FREE_BLOCK = ABILITY_TYPES.register(
                    BLOCK_NAME,
                    key -> new AbilityType<>(
                            key,
                            rotp.core.impl.stands
                                    ._entitybase
                                    .StandEntityBlockAbility::new));

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<StoneFreeStringAbility>>
            USER_STRING_ATTACK =
                    stringAbility(
                            USER_STRING_ATTACK_NAME,
                            StoneFreeStringAbility.Mode.USER_ATTACK);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<StoneFreeStringAbility>>
            USER_STRING_SWEEP =
                    stringAbility(
                            USER_STRING_SWEEP_NAME,
                            StoneFreeStringAbility.Mode.USER_SWEEP);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<StoneFreeStringAbility>>
            STRING_CAPTURE =
                    stringAbility(
                            STRING_CAPTURE_NAME,
                            StoneFreeStringAbility.Mode.CAPTURE);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<StoneFreeStringAbility>>
            USER_STRING_WHIP =
                    stringAbility(
                            USER_STRING_WHIP_NAME,
                            StoneFreeStringAbility.Mode.USER_WHIP);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<StoneFreeStringAbility>>
            STRING_BIND =
                    stringAbility(
                            STRING_BIND_NAME,
                            StoneFreeStringAbility.Mode.STAND_BIND);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<StoneFreeStringAbility>>
            USER_STRING_BIND =
                    stringAbility(
                            USER_STRING_BIND_NAME,
                            StoneFreeStringAbility.Mode.USER_BIND);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<StoneFreeStringAbility>>
            USER_STRING_PICK =
                    stringAbility(
                            USER_STRING_PICK_NAME,
                            StoneFreeStringAbility.Mode.USER_PICK);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<StoneFreeStringAbility>>
            USER_GRAPPLE =
                    stringAbility(
                            USER_GRAPPLE_NAME,
                            StoneFreeStringAbility.Mode.USER_GRAPPLE);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<StoneFreeStringAbility>>
            USER_GRAPPLE_ENTITY =
                    stringAbility(
                            USER_GRAPPLE_ENTITY_NAME,
                            StoneFreeStringAbility.Mode
                                    .USER_GRAPPLE_ENTITY);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<StoneFreeStringAbility>>
            USER_RECOVER_STRING =
                    stringAbility(
                            USER_RECOVER_STRING_NAME,
                            StoneFreeStringAbility.Mode
                                    .RECOVER_STRING);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<StoneFreeStringAbility>>
            GRAPPLE_ENTITY =
                    stringAbility(
                            GRAPPLE_ENTITY_NAME,
                            StoneFreeStringAbility.Mode.STAND_GRAPPLE);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<StoneFreeStringAbility>>
            USER_CLOSE_WOUNDS =
                    stringAbility(
                            USER_CLOSE_WOUNDS_NAME,
                            StoneFreeStringAbility.Mode.CLOSE_WOUNDS);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<StoneFreeStringAbility>>
            USER_BARRIER =
                    stringAbility(
                            USER_BARRIER_NAME,
                            StoneFreeStringAbility.Mode.BARRIER);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<StoneFreeStringAbility>>
            USER_REMOVE_BARRIER =
                    stringAbility(
                            USER_REMOVE_BARRIER_NAME,
                            StoneFreeStringAbility.Mode
                                    .REMOVE_BARRIER);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<StoneFreeStringAbility>>
            USER_MOBIUS_STRIP =
                    stringAbility(
                            USER_MOBIUS_STRIP_NAME,
                            StoneFreeStringAbility.Mode.MOBIUS);

    public static final Supplier<AttachmentType<StoneFreeState>>
            USER_STATE = ATTACHMENT_TYPES.register(
                    "user_state",
                    () -> AttachmentType.serializable(
                                    holder -> holder instanceof
                                            net.minecraft.world.entity
                                                    .LivingEntity
                                            ? new StoneFreeState()
                                            : null)
                            .sync(StoneFreeState.STREAM_CODEC)
                            .build());

    public static final DeferredHolder<
            EntityType<?>, EntityType<StoneFreeStandEntity>>
            STONE_FREE_ENTITY = ENTITY_TYPES.register(
                    "stone_free",
                    key -> EntityType.Builder
                            .<StoneFreeStandEntity>of(
                                    StoneFreeStandEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.65F, 1.95F)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<
            EntityType<?>, EntityType<StoneFreeStringEntity>>
            SF_STRING = stringEntity("sf_string");
    public static final DeferredHolder<
            EntityType<?>, EntityType<StoneFreeStringEntity>>
            SFU_STRING = stringEntity("sfu_string");
    public static final DeferredHolder<
            EntityType<?>, EntityType<StoneFreeStringEntity>>
            SFU_STRING_SWEEP = stringEntity("sfu_string_sweep");
    public static final DeferredHolder<
            EntityType<?>, EntityType<StoneFreeStringEntity>>
            SFU_STRING_WHIP = stringEntity("sfu_string_whip");
    public static final DeferredHolder<
            EntityType<?>, EntityType<StoneFreeStringEntity>>
            SF_EXTENDED_PUNCH = stringEntity("sf_extended_punch");
    public static final DeferredHolder<
            EntityType<?>, EntityType<StoneFreeStringEntity>>
            SFU_STRING_SPIKE = stringEntity("sfu_string_spike");
    public static final DeferredHolder<
            EntityType<?>, EntityType<StoneFreeStringEntity>>
            SF_GRAPPLING_STRING =
                    stringEntity("sf_grappling_string");
    public static final DeferredHolder<
            EntityType<?>, EntityType<StoneFreeStringEntity>>
            SFU_GRAPPLING_STRING =
                    stringEntity("sfu_grappling_string");
    public static final DeferredHolder<
            EntityType<?>, EntityType<StoneFreeStringEntity>>
            SFU_BARRIER = stringEntity("sfu_barrier", true);

    public static final DeferredHolder<
            MobEffect, StringDecompositionEffect>
            STRING_DECOMPOSITION = MOB_EFFECTS.register(
                    "string_decomposition",
                    key -> new StringDecompositionEffect());
    public static final DeferredHolder<
            MobEffect, MobiusStripEffect>
            MOBIUS_STRIP = MOB_EFFECTS.register(
                    "mobius_strip",
                    key -> new MobiusStripEffect(
                            MobEffectCategory.BENEFICIAL,
                            0x80DEF7));

    public static final DeferredHolder<SoundEvent, SoundEvent>
            JOLYNE_STONE_FREE = sound("jolyne_stone_free");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            STONE_FREE_SUMMON = sound("stone_free_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            STONE_FREE_PUNCH_LIGHT =
                    sound("stone_free_punch_light");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            STONE_FREE_PUNCH_HEAVY =
                    sound("stone_free_punch_heavy");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            STONE_FREE_ORA = sound("stone_free_ora");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            STONE_FREE_ORA_LONG = sound("stone_free_ora_long");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            STONE_FREE_ORA_ORA_ORA =
                    sound("stone_free_ora_ora_ora");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            STONE_FREE_STRING = sound("stone_free_string");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            STONE_FREE_GRAPPLE_CATCH =
                    sound("stone_free_grapple_catch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            STONE_FREE_CLOSING_WOUND =
                    sound("stone_free_closing_wound");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            STONE_FREE_CLOSED_WOUND =
                    sound("stone_free_closed_wound");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            STONE_FREE_BARRIER_PLACED =
                    sound("stone_free_barrier_placed");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            STONE_FREE_BARRIER_RIPPED =
                    sound("stone_free_barrier_ripped");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            STONE_FREE_MOBIUS_SOUND =
                    sound("stone_free_mobius_strip");

    public static final DeferredHolder<
            StandType, StoneFreeStandType>
            STONE_FREE = STAND_TYPES.register(
                    "stone_free", StoneFreeAddon::createStand);

    public StoneFreeAddon(IEventBus modEventBus) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_VISUAL_CONTEXT_V1)
                // attack-strength reset on perform (R146 core)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ENTITY_ACTION_HOOKS_V1)) {
            throw new IllegalStateException(
                    "RotP Stone Free requires addon ABI 1 with "
                            + "namespaced ability resources, "
                            + "Stand visual contexts and entity action "
                            + "hooks (a core from the same release).");
        }
        // StoneFreeGameEvents reads the integrated_stand effect (R148 core)
        if (!RotpAddonApi.supportsFeature(
                RotpAddonApi.FEATURE_INTEGRATED_STAND_V1)) {
            throw new IllegalStateException(
                    "RotP Stone Free requires the core integrated "
                            + "Stand effect (a core from the same "
                            + "release).");
        }
        // The user's strings run in the user's Stand-power slot and the
        // thrown ones opt out of the player action gate (K5-48 core).
        if (!RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_POWER_ACTION_SLOT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_ACTION_PORT_SLOT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_PLAYER_ACTION_GATE_V1)) {
            throw new IllegalStateException(
                    "RotP Stone Free requires the core Stand-power "
                            + "action slot, Stand action ports and "
                            + "player action gate (a core from the "
                            + "same release).");
        }

        ABILITY_TYPES.register(modEventBus);
        ATTACHMENT_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        MOB_EFFECTS.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static DeferredHolder<
            AbilityType<?>, AbilityType<StoneFreeStringAbility>>
            stringAbility(
                    String name,
                    StoneFreeStringAbility.Mode mode) {
        return ABILITY_TYPES.register(
                name,
                key -> StoneFreeStringAbility.createType(
                        key, mode));
    }

    private static DeferredHolder<
            EntityType<?>, EntityType<StoneFreeStringEntity>>
            stringEntity(String name) {
        return stringEntity(name, false);
    }

    private static DeferredHolder<
            EntityType<?>, EntityType<StoneFreeStringEntity>>
            stringEntity(String name, boolean barrier) {
        return ENTITY_TYPES.register(
                name,
                key -> {
                    EntityType.Builder<StoneFreeStringEntity> builder = EntityType.Builder
                        .<StoneFreeStringEntity>of(
                                StoneFreeStringEntity::new,
                                MobCategory.MISC)
                        .noSummon()
                        .sized(0.25F, 0.25F)
                        .clientTrackingRange(96)
                        .updateInterval(1);
                    if (barrier) {
                        // Donor barriers follow their owner locally, not a second motion stream.
                        builder.updateInterval(Integer.MAX_VALUE)
                                .setShouldReceiveVelocityUpdates(false);
                    }
                    return builder.build(key.toString());
                });
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(
            String path) {
        return SOUND_EVENTS.register(
                path, SoundEvent::createVariableRangeEvent);
    }

    private static StoneFreeStandType createStand(
            ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addHumanoidStandStuff()
                .addAbility(
                        "punch",
                        STONE_FREE_PUNCH,
                        punch -> punch
                                .punchImpactSound(
                                        STONE_FREE_PUNCH_LIGHT)
                                .standCrySound(STONE_FREE_ORA))
                .addAbility(
                        "barrage",
                        STONE_FREE_BARRAGE,
                        barrage -> barrage
                                .barrageHitSound(
                                        STONE_FREE_PUNCH_LIGHT)
                                .barrageCrySound(
                                        STONE_FREE_ORA_ORA_ORA)
                                // 1.16 one-argument standSound: the cry
                                // also plays while the Stand is arms-only
                                .barrageCryInArmsOnly())
                .addAbility(
                        "heavy_punch",
                        STONE_FREE_HEAVY_PUNCH,
                        punch -> punch
                                .heavyPunchImpactSound(
                                        STONE_FREE_PUNCH_HEAVY)
                                .heavyPunchCrySound(
                                        STONE_FREE_ORA_LONG))
                .addAbility(
                        STRING_PUNCH_NAME,
                        STONE_FREE_STRING_PUNCH,
                        punch -> {
                            punch.heavyPunchImpactSound(
                                    STONE_FREE_PUNCH_HEAVY);
                            punch.heavyPunchCrySound(
                                    STONE_FREE_ORA_LONG);
                            punch.initIsFinisher("heavy_punch");
                            punch.resolveLevelToUnlock(1);
                        })
                .addAbility(
                        "heavy_charged",
                        STONE_FREE_HEAVY_CHARGED)
                .addAbility("grab", ModStandAbilities.GRAB)
                .addAbility("grab_throw", ModStandAbilities.GRAB_THROW)
                .addAbility(
                        "grab_punch",
                        ModStandAbilities.PUNCH,
                        punch -> {
                            punch.punchImpactSound(
                                            STONE_FREE_PUNCH_LIGHT)
                                    .standCrySound(STONE_FREE_ORA);
                            punch.initIsGrabVariation();
                        })
                .addAbility(
                        "grab_barrage",
                        ModStandAbilities.BARRAGE,
                        barrage -> {
                            barrage.barrageHitSound(
                                            STONE_FREE_PUNCH_LIGHT)
                                    .barrageCrySound(
                                            STONE_FREE_ORA_ORA_ORA);
                            barrage.initIsGrabVariation();
                        })
                .addAbility(
                        "grab_heavy_punch",
                        ModStandAbilities.HEAVY_PUNCH,
                        punch -> {
                            punch.heavyPunchImpactSound(
                                            STONE_FREE_PUNCH_HEAVY)
                                    .heavyPunchCrySound(
                                            STONE_FREE_ORA_LONG);
                            punch.initIsGrabVariation();
                        })
                .addAbility("guard", STONE_FREE_BLOCK)
                .addAbility(
                        USER_STRING_ATTACK_NAME,
                        USER_STRING_ATTACK)
                .addAbility(
                        USER_STRING_SWEEP_NAME,
                        USER_STRING_SWEEP)
                .addAbility(
                        STRING_CAPTURE_NAME,
                        STRING_CAPTURE,
                        // 1.16: unlocked with the string bind.
                        ability -> ability.resolveLevelToUnlock(1))
                .addAbility(
                        USER_STRING_WHIP_NAME,
                        USER_STRING_WHIP)
                .addAbility(
                        STRING_BIND_NAME,
                        STRING_BIND,
                        ability -> ability.resolveLevelToUnlock(1))
                .addAbility(
                        USER_STRING_BIND_NAME,
                        USER_STRING_BIND)
                .addAbility(
                        EXTENDED_PUNCH_NAME,
                        STONE_FREE_EXTENDED_PUNCH,
                        ability -> ability.resolveLevelToUnlock(3))
                .addAbility(
                        USER_STRING_PICK_NAME,
                        USER_STRING_PICK)
                .addAbility(
                        USER_GRAPPLE_NAME,
                        USER_GRAPPLE,
                        ability -> ability.resolveLevelToUnlock(2))
                .addAbility(
                        USER_GRAPPLE_ENTITY_NAME,
                        USER_GRAPPLE_ENTITY,
                        ability -> ability.resolveLevelToUnlock(2))
                .addAbility(
                        USER_RECOVER_STRING_NAME,
                        USER_RECOVER_STRING)
                .addAbility(
                        GRAPPLE_ENTITY_NAME,
                        GRAPPLE_ENTITY,
                        ability -> ability.resolveLevelToUnlock(2))
                .addAbility(
                        USER_CLOSE_WOUNDS_NAME,
                        USER_CLOSE_WOUNDS,
                        ability -> ability.resolveLevelToUnlock(2))
                .addAbility(
                        USER_BARRIER_NAME,
                        USER_BARRIER,
                        ability -> ability.resolveLevelToUnlock(3))
                .addAbility(
                        USER_REMOVE_BARRIER_NAME,
                        USER_REMOVE_BARRIER,
                        ability -> ability.resolveLevelToUnlock(3))
                .addAbility(
                        USER_MOBIUS_STRIP_NAME,
                        USER_MOBIUS_STRIP)
                .makeControlScheme("hotbar")
                    .bind("punch", InputMethod.CLICK, InputKey.LMB)
                    // 1.16 shiftVariationOf(user_string_attack): Shift with
                    // the attack button. The attack is only the punch's
                    // context replacement, never a hotbar slot.
                    .bind(
                            USER_STRING_WHIP_NAME,
                            InputMethod.CLICK,
                            InputKey.LMB.withModifier(
                                    InputKey.Modifier.SHIFT))
                    .bind("barrage", InputMethod.HOLD, InputKey.LMB)
                    .bind(
                            "heavy_punch",
                            InputMethod.CLICK,
                            InputKey.RMB)
                    .bind(
                            "heavy_charged",
                            InputMethod.HOLD,
                            MovesetBuilder
                                    .DEFAULT_CHARGED_HEAVY_INPUT)
                    .bind(
                            "grab",
                            InputMethod.CLICK,
                            MovesetBuilder.DEFAULT_GRAB_INPUT)
                    .bind(
                            "grab_throw",
                            InputMethod.HOLD,
                            MovesetBuilder
                                    .DEFAULT_GRAB_THROW_INPUT)
                    .bind("guard", InputMethod.HOLD, InputKey.RMB)
                    .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                    .addToHotbar(
                            STRING_BIND_NAME, 0, InputMethod.HOLD)
                    .addToHotbar(
                            EXTENDED_PUNCH_NAME,
                            0,
                            InputMethod.CLICK)
                    // 1.16 InitStands:169-170: one press throws the user's
                    // grapple; only its recover string is held.
                    .addToHotbar(
                            USER_GRAPPLE_NAME,
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            USER_CLOSE_WOUNDS_NAME,
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            USER_BARRIER_NAME,
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            USER_MOBIUS_STRIP_NAME,
                            0,
                            InputMethod.CLICK)
                    .addHotbarSlotVariation(
                            USER_GRAPPLE_ENTITY_NAME,
                            USER_GRAPPLE_NAME,
                            InputKey.Modifier.SHIFT,
                            InputMethod.CLICK)
                    .addHotbarSlotVariation(
                            USER_REMOVE_BARRIER_NAME,
                            USER_BARRIER_NAME,
                            InputKey.Modifier.SHIFT,
                            InputMethod.CLICK)
                    .addHotbarSlotVariation(
                            USER_RECOVER_STRING_NAME,
                            USER_GRAPPLE_NAME,
                            InputKey.Modifier.CONTROL,
                            InputMethod.HOLD)
                .finalizeControlScheme()
                .makeControlScheme("keybinds")
                    .bind("punch", InputMethod.CLICK, InputKey.LMB)
                    .bind(
                            USER_STRING_WHIP_NAME,
                            InputMethod.CLICK,
                            InputKey.LMB.withModifier(
                                    InputKey.Modifier.SHIFT))
                    .bind("barrage", InputMethod.HOLD, InputKey.LMB)
                    .bind(
                            "heavy_punch",
                            InputMethod.CLICK,
                            InputKey.RMB)
                    .bind(
                            "heavy_charged",
                            InputMethod.HOLD,
                            MovesetBuilder
                                    .DEFAULT_CHARGED_HEAVY_INPUT)
                    .bind(
                            "grab",
                            InputMethod.CLICK,
                            MovesetBuilder.DEFAULT_GRAB_INPUT)
                    .bind(
                            "grab_throw",
                            InputMethod.HOLD,
                            MovesetBuilder
                                    .DEFAULT_GRAB_THROW_INPUT)
                    .bind("guard", InputMethod.HOLD, InputKey.RMB)
                .finalizeControlScheme()
                .addSkill(StandUnlockableSkill
                        .startingAbility("punch")
                        .withAbility(
                                USER_STRING_ATTACK_NAME,
                                USER_STRING_WHIP_NAME))
                .addSkill(StandUnlockableSkill
                        .startingAbility("barrage")
                        .withAbility(USER_STRING_SWEEP_NAME))
                .addSkill(StandUnlockableSkill
                        .startingAbility("heavy_punch")
                        .withAbility(STRING_PUNCH_NAME))
                .addSkill(StandUnlockableSkill
                        .startingAbility("heavy_charged")
                        .prerequisiteSkill("heavy_punch"))
                .addSkill(StandUnlockableSkill
                        .startingAbility("grab"))
                .addSkill(StandUnlockableSkill
                        .unlockableAbility("grab_throw", 100)
                        .withAbility(
                                "grab_punch",
                                "grab_barrage",
                                "grab_heavy_punch")
                        .prerequisiteSkill("grab"))
                .addSkill(StandUnlockableSkill
                        .startingAbility("guard"))
                .addSkill(StandUnlockableSkill
                        .unlockableAbility(
                                STRING_BIND_NAME, 150)
                        .withAbility(
                                USER_STRING_BIND_NAME,
                                STRING_CAPTURE_NAME))
                .addSkill(StandUnlockableSkill
                        .unlockableAbility(
                                EXTENDED_PUNCH_NAME, 250)
                        .withAbility(USER_STRING_PICK_NAME))
                .addSkill(StandUnlockableSkill
                        .unlockableAbility(
                                USER_GRAPPLE_NAME, 250)
                        .withAbility(
                                USER_GRAPPLE_ENTITY_NAME,
                                USER_RECOVER_STRING_NAME,
                                GRAPPLE_ENTITY_NAME))
                .addSkill(StandUnlockableSkill
                        .unlockableAbility(
                                USER_CLOSE_WOUNDS_NAME, 250))
                .addSkill(StandUnlockableSkill
                        .unlockableAbility(
                                USER_BARRIER_NAME, 350)
                        .withAbility(
                                USER_REMOVE_BARRIER_NAME))
                // 1.16 noResolveUnlock: only StoneFreeGameEvents'
                // special unlock (or a creative user) opens it.
                .addSkill(StandUnlockableSkill
                        .unlockableAbility(
                                USER_MOBIUS_STRIP_NAME,
                                1_000_000)
                        .setUnlockedByTraining())
                .addHumanoidStandSkills();

        return new StoneFreeStandType(
                new StandStats.Builder()
                        .power(14.0, 15.0)
                        .speed(11.0, 12.0)
                        .range(1.0, 2.0)
                        .durability(14.0, 16.0)
                        .precision(10.0)
                        .randomWeight(2.0D)
                        .build(),
                moveset,
                STONE_FREE_ENTITY.get(),
                id)
                .standControlType(StandControlType.CLOSE_RANGE_DIRECT)
                .manualControl(true)
                .distanceStrengthDecay(true)
                .standDimensions(0.65F, 1.95F)
                .summonShout(() -> JOLYNE_STONE_FREE)
                .summonSound(() -> STONE_FREE_SUMMON)
                .unsummonSound(() -> ModSoundEvents.STAND_UNSUMMON);
    }
}
