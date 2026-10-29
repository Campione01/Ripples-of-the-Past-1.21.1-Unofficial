package rotp.addons.shadowtheworld;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import java.util.function.BiFunction;
import java.util.function.Supplier;

import rotp.core.api.RotpAddonApi;
import rotp.core.api.timestop.TimeStopAudioContext;
import rotp.core.api.timestop.TimeStopAudioDecision;
import rotp.core.api.timestop.TimeStopBehaviorPolicies;
import rotp.core.api.timestop.TimeStopBehaviorPolicy;
import rotp.core.api.timestop.TimeStopProgressionPolicy;
import rotp.core.api.timestop.TimeStopStartupCostContext;
import rotp.core.api.timestop.TimeStopStartupCostDecision;
import rotp.core.subsystems.timestop.TimeStopLearning;
import rotp.core.core.JojoRegistries;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.util.sound.OstSoundList;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;

import rotp.addons.shadowtheworld.ability.ShadowBarrageAbility;
import rotp.addons.shadowtheworld.ability.ShadowCounterAbility;
import rotp.addons.shadowtheworld.ability.ShadowEvolutionAbility;
import rotp.addons.shadowtheworld.ability.ShadowEvolutionAbility.Mode;
import rotp.addons.shadowtheworld.ability.ShadowHeavyPunchAbility;
import rotp.addons.shadowtheworld.ability.ShadowKnivesAbility;
import rotp.addons.shadowtheworld.ability.ShadowKnivesAbility.Pattern;
import rotp.addons.shadowtheworld.ability.ShadowKnockbackPunchAbility;
import rotp.addons.shadowtheworld.ability.ShadowStunComboAbility;
import rotp.addons.shadowtheworld.ability.ShadowSuperHeavyAbility;
import rotp.addons.shadowtheworld.ability.ShadowTimeStopAbility;
import rotp.addons.shadowtheworld.effect.KnockdownEffect;
import rotp.addons.shadowtheworld.entity.ShadowStandEntity;
import rotp.addons.shadowtheworld.item.DioNoteItem;
import rotp.addons.shadowtheworld.item.DioNotePageItem;
import rotp.addons.shadowtheworld.state.ShadowEvolutionState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(ShadowTheWorldAddon.MOD_ID)
public final class ShadowTheWorldAddon {
    public static final String MOD_ID = "rotp_shadowtheworld";
    private static final int REQUIRED_CORE_ABI = 1;

    public static final String PUNCH = "shadow_world_punch";
    public static final String BARRAGE = "shadow_world_barrage";
    public static final String HEAVY = "shadow_world_heavy_punch";
    public static final String KNOCKBACK =
            "shadow_world_knockback_punch";
    public static final String GUARD = "shadow_world_block";
    public static final String SINGLE_KNIFE =
            "shadow_world_single_knife";
    public static final String MULTIPLE_KNIVES =
            "shadow_world_multiple_knives";
    public static final String CIRCLE_KNIVES =
            "shadow_world_circle_knives";
    public static final String SUPER_HEAVY = "super_heavy_punch";
    public static final String STUN_COMBO = "stun_combo_attack";
    public static final String COUNTER = "time_stop_counter";
    public static final String TIME_STOP = "shadow_world_time_stop";
    public static final String TIME_STOP_BLINK =
            "shadow_world_ts_blink";
    public static final String EVOLUTION = "shadow_world_evolution";
    public static final String STABLE = "state_conversion_stable";
    public static final String UNSTABLE = "state_conversion_unstable";
    public static final String QUICK_EVOLUTION = "quick_evolution";

    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(
                    JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<AttachmentType<?>>
            ATTACHMENT_TYPES = DeferredRegister.create(
                    NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, MOD_ID);
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(
                    JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<StandEntityPunchAbility>>
            PUNCH_ABILITY = ability(
                    PUNCH, (type, id) -> new StandEntityPunchAbility(type, id) {
                        @Override
                        public ActionAnimIdentifier getEntityAnim(
                                EntityActionInstance action) {
                            HumanoidArm side = action != null
                                    && action.getPerformer() instanceof StandEntity stand
                                    ? stand.getMainArm() : HumanoidArm.RIGHT;
                            if (action instanceof StandEntityPunch punch
                                    && punch.getPunchingHand() == InteractionHand.OFF_HAND) {
                                side = side.getOpposite();
                            }
                            return ActionAnimIdentifier.getOrCreate(
                                    side == HumanoidArm.LEFT ? "punch2" : "punch",
                                    false);
                        }
                    });
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<ShadowBarrageAbility>>
            BARRAGE_ABILITY = ability(
                    BARRAGE, ShadowBarrageAbility::new);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<ShadowHeavyPunchAbility>>
            HEAVY_ABILITY = ability(
                    HEAVY, ShadowHeavyPunchAbility::new);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<ShadowKnockbackPunchAbility>>
            KNOCKBACK_ABILITY = ability(
                    KNOCKBACK, ShadowKnockbackPunchAbility::new);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<ShadowKnivesAbility>>
            SINGLE_KNIFE_ABILITY = ability(
                    SINGLE_KNIFE,
                    (type, id) -> new ShadowKnivesAbility(
                            type, id, Pattern.SINGLE));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<ShadowKnivesAbility>>
            MULTIPLE_KNIVES_ABILITY = ability(
                    MULTIPLE_KNIVES,
                    (type, id) -> new ShadowKnivesAbility(
                            type, id, Pattern.MULTIPLE));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<ShadowKnivesAbility>>
            CIRCLE_KNIVES_ABILITY = ability(
                    CIRCLE_KNIVES,
                    (type, id) -> new ShadowKnivesAbility(
                            type, id, Pattern.CIRCLE));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<ShadowSuperHeavyAbility>>
            SUPER_HEAVY_ABILITY = ability(
                    SUPER_HEAVY, ShadowSuperHeavyAbility::new);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<ShadowStunComboAbility>>
            STUN_COMBO_ABILITY = ability(
                    STUN_COMBO, ShadowStunComboAbility::new);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<ShadowCounterAbility>>
            COUNTER_ABILITY = ability(
                    COUNTER, ShadowCounterAbility::new);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<ShadowTimeStopAbility>>
            TIME_STOP_ABILITY = ability(
                    TIME_STOP, ShadowTimeStopAbility::new);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<ShadowEvolutionAbility>>
            EVOLUTION_ABILITY = ability(
                    EVOLUTION,
                    (type, id) -> new ShadowEvolutionAbility(
                            type, id, Mode.EVOLVE));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<ShadowEvolutionAbility>>
            STABLE_ABILITY = ability(
                    STABLE,
                    (type, id) -> new ShadowEvolutionAbility(
                            type, id, Mode.STABLE));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<ShadowEvolutionAbility>>
            UNSTABLE_ABILITY = ability(
                    UNSTABLE,
                    (type, id) -> new ShadowEvolutionAbility(
                            type, id, Mode.UNSTABLE));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<ShadowEvolutionAbility>>
            QUICK_EVOLUTION_ABILITY = ability(
                    QUICK_EVOLUTION,
                    (type, id) -> new ShadowEvolutionAbility(
                            type, id, Mode.QUICK_EVOLVE));

    public static final Supplier<AttachmentType<ShadowEvolutionState>>
            EVOLUTION_STATE = ATTACHMENT_TYPES.register(
                    "evolution_state",
                    () -> AttachmentType.serializable(
                                    holder -> holder instanceof Player
                                            ? new ShadowEvolutionState()
                                            : null)
                            .copyOnDeath()
                            .sync(
                                    (holder, player) ->
                                            holder == player,
                                    ShadowEvolutionState.STREAM_CODEC)
                            .build());

    // 1.16 SDworldEntity: own entity for the Stand's knife volley
    public static final DeferredHolder<
            EntityType<?>, EntityType<ShadowStandEntity>>
            SHADOW_THE_WORLD_ENTITY = ENTITY_TYPES.register(
                    "shadowtheworld",
                    key -> EntityType.Builder.<ShadowStandEntity>of(
                                    ShadowStandEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.7F, 2.15F)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<Item, DioNotePageItem>
            DIO_NOTE_PAGE = ITEMS.register(
                    "dio_note_page",
                    key -> new DioNotePageItem(
                            new Item.Properties()
                                    .rarity(Rarity.RARE)));
    public static final DeferredHolder<Item, DioNoteItem>
            DIO_NOTE = ITEMS.register(
                    "dio_note",
                    key -> new DioNoteItem(
                            new Item.Properties()
                                    .rarity(Rarity.EPIC)
                                    .fireResistant()));

    public static final DeferredHolder<MobEffect, KnockdownEffect>
            KNOCKDOWN = MOB_EFFECTS.register(
                    "knockdown", KnockdownEffect::new);

    public static final DeferredHolder<SoundEvent, SoundEvent>
            SHADOW_WORLD_PUNCH_LIGHT =
                    sound("shadow_world_punch_light");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SHADOW_WORLD_UNSUMMON =
                    sound("shadow_world_unsummon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SHADOW_WORLD_BARRAGE =
                    sound("shadow_world_barrage");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SDDIO_EVOLUTION = sound("sddio_evolution");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SDDIO_SUPERPUNCH = sound("sddio_superpunch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SHADOW_WORLD_SUPER_PUNCH =
                    sound("shadow_world_superpunch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SHADOW_WORLD_SUMMON = sound("shadow_world_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SHADOW_WORLD_MUDA_LONG =
                    sound("shadow_world_muda_long");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SHADOW_WORLD_MUDA_MUDA_MUDA =
                    sound("shadow_world_muda_muda_muda");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SHADOW_WORLD_TIME_STOP =
                    sound("shadow_world_time_stop");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SHADOW_WORLD_TIME_RESUME =
                    sound("shadow_world_time_resume");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SHADOW_WORLD_TIME_STOP_BLINK =
                    sound("shadow_world_time_stop_blink");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SDDIO_SHADOW_WORLD = sound("sddio_shadow_world");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SDDIO_MUDA = sound("sddio_muda");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SDDIO_MUDA_MUDA = sound("sddio_muda_muda");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SDDIO_WRY = sound("sddio_wry");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SDDIO_DIE = sound("sddio_die");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SDDIO_THIS_IS_SHADOW_WORLD =
                    sound("sddio_this_is_shadow_world");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SDDIO_TIME_STOP = sound("sddio_time_stop");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SDDIO_TIME_RESUMES = sound("sddio_time_resumes");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SDDIO_TIMES_UP = sound("sddio_times_up");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SDDIO_5_SECONDS = sound("sddio_5_seconds");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SDDIO_ONE_MORE = sound("sddio_one_more");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SDDIO_CANT_MOVE = sound("sddio_cant_move");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SHADOW_WORLD_KNIFE_THROW =
                    sound("shadow_world_knife_throw");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SHADOW_WORLD_KNIVES_THROW =
                    sound("shadow_world_knives_throw");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SDDIO_KNIVES_THROW_VOICE =
                    sound("sddio_knives_throw_voice");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SDDIO_KNIFE_THROW_VOICE =
                    sound("sddio_knife_throw_voice");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            EVOLUTION_BGM = sound("shadow_world_ost");
    public static final OstSoundList SHADOW_WORLD_OST =
            new OstSoundList(id("shadow_world_ost"), SOUND_EVENTS);

    // Donor time stop: timeStopMaxTicks(60, 80, 1000, 70) - the 1.16 builder stores the vampire value for Pillar Men
    // and max(human, 70) for zombies, so humans 60, high-saturation zombies 70, high-blood vampires and Pillar Men
    // 80 ticks (the core adds its Pillar Man stage bonus to that) - with the default learning/decay/cooldown.
    // Its own start sound replaced The World's; ShadowTheWorldGameEvents plays it on Added, so the core START_SOUND
    // is silenced. Its resume sound and lines (1.16 timeResumeSound / addTimeResumeVoiceLine) are the core
    // RESUME_SOUND / RESUME_VOICE cues: 10 and 30 ticks before the end (1 and 2 ticks after a first resume press),
    // the line only for stops of 100+ ticks, as 1.16 TimeStopInstance.tickSounds.
    // Stamina: the donor's staminaCost(80) / staminaCostTick(3.5) against The World's 225 / 9, which the core formulas
    // are built on; the per-tick part is scaled in ShadowTheWorldGameEvents.onTimeStopPreStart.
    public static final float DONOR_TIME_STOP_STAMINA_COST = 80.0F;
    public static final float DONOR_TIME_STOP_STAMINA_COST_TICK = 3.5F;
    // Blink step-up cap: one block above the target's feet.
    static final double BLINK_STEP_UP_ABOVE_FEET = 1.0D;
    private static final TimeStopProgressionPolicy TIME_STOP_PROGRESSION =
            new TimeStopProgressionPolicy(60, 80, 70, 0.1F, 0.0F, 3.0F);
    private static final TimeStopBehaviorPolicy TIME_STOP_BEHAVIOR =
            new TimeStopBehaviorPolicy() {
                @Override
                public TimeStopAudioDecision audio(
                        TimeStopAudioContext context) {
                    return switch (context.cue()) {
                        case START_SOUND ->
                                TimeStopAudioDecision.silent();
                        case RESUME_SOUND -> TimeStopAudioDecision.sound(
                                SHADOW_WORLD_TIME_RESUME);
                        case RESUME_VOICE -> TimeStopAudioDecision.sound(
                                context.isManualResume()
                                        ? SDDIO_TIME_RESUMES
                                        : SDDIO_TIMES_UP);
                        default -> TimeStopAudioDecision.pass();
                    };
                }

                @Override
                public TimeStopStartupCostDecision startupCost(
                        TimeStopStartupCostContext context) {
                    return TimeStopStartupCostDecision.override(
                            context.defaultCost()
                                    * DONOR_TIME_STOP_STAMINA_COST
                                    / TimeStopLearning.BASE_STAMINA_COST);
                }

                @Override
                public TimeStopProgressionPolicy progression() {
                    return TIME_STOP_PROGRESSION;
                }
            };

    public static final DeferredHolder<StandType, EntityStandType>
            SHADOW_THE_WORLD = STAND_TYPES.register(
                    "shadowtheworld",
                    ShadowTheWorldAddon::createStand);

    public ShadowTheWorldAddon(IEventBus modEventBus) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_POWER_TRANSITIONS_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_TIME_STOP_LIFECYCLE_V1)
                // blink base costs and sound (R146 core)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_TIME_STOP_ABILITY_TUNING_V1)
                // blink landing behind the look vector (R147 core)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_TIME_STOP_BLINK_TARGET_POS_V1)
                // the time-stop held walk speed hook (the charge no
                // longer uses it: owner rule of 2026-10-15)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_TIME_STOP_HELD_WALK_SPEED_V1)
                // the counter's stun punch reads the time stop's charge
                // in the user's Stand-power slot
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_POWER_ACTION_SLOT_V1)
                // 1.16 action shouts follow the sneak rule
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_SHOUTS_V1)) {
            throw new IllegalStateException(
                    "Shadow The World requires ROTP addon ABI 1 "
                            + "with namespaced abilities, Stand "
                            + "visual contexts, Stand transitions, "
                            + "time-stop lifecycle events, "
                            + "time-stop ability tuning, the blink "
                            + "target position hook, the time-stop "
                            + "held walk speed and the Stand-power "
                            + "action slot (a core from "
                            + "the same release).");
        }

        ABILITY_TYPES.register(modEventBus);
        ATTACHMENT_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        ITEMS.register(modEventBus);
        MOB_EFFECTS.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        TimeStopBehaviorPolicies.register(
                id("time_stop_behavior"),
                id("shadowtheworld"),
                TIME_STOP_BEHAVIOR);
        modEventBus.addListener(
                ShadowTheWorldModEvents::registerAttributes);
        modEventBus.addListener(
                ShadowTheWorldModEvents::buildCreativeTab);
        modEventBus.addListener(
                ShadowTheWorldModEvents::commonSetup);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static ShadowEvolutionState state(Player player) {
        return player.getData(EVOLUTION_STATE);
    }

    private static <T extends Ability>
            DeferredHolder<AbilityType<?>, AbilityType<T>> ability(
                    String name,
                    BiFunction<AbilityType<T>, AbilityId, T> factory) {
        return ABILITY_TYPES.register(
                name, key -> new AbilityType<>(key, factory));
    }

    // 1.16 SHADOWWORLDTimeStopInstant.getEntityTargetTeleportPos:
    // target.position() - target.getLookAngle() * (both widths).
    static Vec3 behindTargetLook(LivingEntity user, Entity target) {
        return stepUpOutOfBlocks(user, target, target.position().subtract(
                target.getLookAngle().scale(
                        target.getBbWidth() + user.getBbWidth())));
    }

    // Owner anti-stuck step-up (not in 1.16, which buried the user): only
    // a landing below the target's feet whose box is in blocks rises, in
    // 1/16 steps, to the first free height at most a block above the
    // target's feet. Anything else keeps the 1.16 landing.
    static Vec3 stepUpOutOfBlocks(
            LivingEntity user, Entity target, Vec3 pos) {
        Level level = user.level();
        AABB box = user.getBoundingBox().move(
                pos.subtract(user.position()));
        if (pos.y >= target.getY() || level.noCollision(user, box)) {
            return pos;
        }
        int maxStep = Mth.floor(
                (target.getY() + BLINK_STEP_UP_ABOVE_FEET) * 16.0D);
        for (int step = Mth.ceil(pos.y * 16.0D); step <= maxStep; step++) {
            double y = step / 16.0D;
            if (level.noCollision(user, box.move(0.0D, y - pos.y, 0.0D))) {
                return new Vec3(pos.x, y, pos.z);
            }
        }
        return pos;
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(
            String name) {
        return SOUND_EVENTS.register(
                name, SoundEvent::createVariableRangeEvent);
    }

    private static EntityStandType createStand(
            ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addHumanoidStandStuff()
                .addAbility(
                        PUNCH,
                        PUNCH_ABILITY,
                        punch -> punch
                                .punchImpactSound(
                                        SHADOW_WORLD_PUNCH_LIGHT)
                                .standCrySound(SDDIO_MUDA))
                .addAbility(
                        BARRAGE,
                        BARRAGE_ABILITY,
                        barrage -> barrage
                                .barrageHitSound(
                                        SHADOW_WORLD_BARRAGE)
                                .barrageCrySound(
                                        SHADOW_WORLD_MUDA_MUDA_MUDA)
                                // 1.16 one-argument standSound: the cry
                                // also plays while the Stand is arms-only
                                .barrageCryInArmsOnly())
                .addAbility(HEAVY, HEAVY_ABILITY)
                .addAbility(
                        "heavy_charged",
                        ModStandAbilities.HEAVY_CHARGED)
                .addAbility(
                        KNOCKBACK,
                        KNOCKBACK_ABILITY,
                        ability -> {
                            ability.initIsFinisher(HEAVY);
                            ability.resolveLevelToUnlock(1);
                            // 1.16 standSound(WINDUP, MUDA_LONG)
                            ability.heavyPunchCrySound(
                                    SHADOW_WORLD_MUDA_LONG);
                        })
                .addAbility(GUARD, ModStandAbilities.GUARD)
                .addAbility(SINGLE_KNIFE, SINGLE_KNIFE_ABILITY)
                .addAbility(
                        MULTIPLE_KNIVES,
                        MULTIPLE_KNIVES_ABILITY)
                .addAbility(CIRCLE_KNIVES, CIRCLE_KNIVES_ABILITY)
                .addAbility(SUPER_HEAVY, SUPER_HEAVY_ABILITY)
                .addAbility(STUN_COMBO, STUN_COMBO_ABILITY)
                .addAbility(COUNTER, COUNTER_ABILITY)
                .addAbility(TIME_STOP, TIME_STOP_ABILITY)
                // 1.16 SHADOWWORLDTimeStopInstant: 0.8 of the donor time
                // stop's 80 / 3.5, its own blink sound, and it lands behind
                // the target's whole look vector, pitch included.
                .addAbility(
                        TIME_STOP_BLINK,
                        ModStandAbilities.TIME_STOP_BLINK,
                        ability -> ability
                                .setTimeStopAbilityName(TIME_STOP)
                                .setTeleportBehindEntity()
                                .setEntityTargetTeleportPos(
                                        ShadowTheWorldAddon
                                                ::behindTargetLook)
                                .setBaseTimeStopStaminaCosts(
                                        DONOR_TIME_STOP_STAMINA_COST,
                                        DONOR_TIME_STOP_STAMINA_COST_TICK)
                                .setBlinkSound(
                                        SHADOW_WORLD_TIME_STOP_BLINK))
                .addAbility(EVOLUTION, EVOLUTION_ABILITY)
                .addAbility(STABLE, STABLE_ABILITY)
                .addAbility(UNSTABLE, UNSTABLE_ABILITY)
                .addAbility(
                        QUICK_EVOLUTION,
                        QUICK_EVOLUTION_ABILITY)
                .makeControlScheme("hotbar")
                    .bind(
                            PUNCH, InputMethod.CLICK, InputKey.LMB)
                    .bind(
                            BARRAGE, InputMethod.HOLD, InputKey.LMB)
                    .bind(
                            HEAVY, InputMethod.CLICK, InputKey.RMB)
                    .bind(
                            "heavy_charged",
                            InputMethod.HOLD,
                            MovesetBuilder
                                    .DEFAULT_CHARGED_HEAVY_INPUT)
                    .bind(
                            GUARD, InputMethod.HOLD, InputKey.RMB)
                    .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                    .addToHotbar(
                            SINGLE_KNIFE, 0, InputMethod.CLICK)
                    .addToHotbar(
                            MULTIPLE_KNIVES, 0, InputMethod.CLICK)
                    .addToHotbar(
                            CIRCLE_KNIVES, 0, InputMethod.CLICK)
                    .addToHotbar(
                            SUPER_HEAVY, 0, InputMethod.CLICK)
                    .addToHotbar(
                            STUN_COMBO, 0, InputMethod.CLICK)
                    // 1.16 holdType(): held while the key is down
                    .addToHotbar(
                            COUNTER, 0, InputMethod.HOLD)
                    // The 1.16 blink (shadow_world_time_resume's SHIFT
                    // variation) is its own slot after the time stop,
                    // which stays a time stop, short taps included.
                    .addToHotbar(
                            TIME_STOP, 0, InputMethod.HOLD)
                    .addToHotbar(
                            TIME_STOP_BLINK, 0, InputMethod.CLICK)
                    .addToHotbar(
                            EVOLUTION, 0, InputMethod.HOLD)
                    .addToHotbar(
                            STABLE, 0, InputMethod.CLICK)
                    .addToHotbar(
                            UNSTABLE, 0, InputMethod.CLICK)
                    .addToHotbar(
                            QUICK_EVOLUTION,
                            0,
                            InputMethod.CLICK)
                .finalizeControlScheme()
                .makeControlScheme("keybinds")
                    .bind(
                            PUNCH, InputMethod.CLICK, InputKey.LMB)
                    .bind(
                            BARRAGE, InputMethod.HOLD, InputKey.LMB)
                    .bind(
                            HEAVY, InputMethod.CLICK, InputKey.RMB)
                    .bind(
                            "heavy_charged",
                            InputMethod.HOLD,
                            MovesetBuilder
                                    .DEFAULT_CHARGED_HEAVY_INPUT)
                    .bind(
                            GUARD, InputMethod.HOLD, InputKey.RMB)
                .finalizeControlScheme()
                .addSkill(
                        StandUnlockableSkill.startingAbility(PUNCH))
                .addSkill(
                        StandUnlockableSkill.startingAbility(BARRAGE))
                .addSkill(
                        StandUnlockableSkill.startingAbility(HEAVY))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                        "heavy_charged")
                                .prerequisiteSkill(HEAVY))
                .addSkill(
                        StandUnlockableSkill.unlockableAbility(
                                        KNOCKBACK, 150)
                                .prerequisiteSkill(HEAVY))
                .addSkill(
                        StandUnlockableSkill.startingAbility(GUARD))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                        SINGLE_KNIFE)
                                .withAbility(MULTIPLE_KNIVES))
                .addSkill(
                        StandUnlockableSkill.unlockableAbility(
                                CIRCLE_KNIVES, 800))
                .addSkill(
                        StandUnlockableSkill.unlockableAbility(
                                        SUPER_HEAVY, 500)
                                .withAbility(STUN_COMBO))
                .addSkill(
                        StandUnlockableSkill.unlockableAbility(
                                COUNTER, 500))
                .addSkill(
                        StandUnlockableSkill.unlockableAbility(
                                TIME_STOP, 5000))
                .addSkill(
                        StandUnlockableSkill.unlockableAbility(
                                        EVOLUTION, 1000)
                                .withAbility(
                                        STABLE,
                                        UNSTABLE,
                                        QUICK_EVOLUTION))
                .addHumanoidStandSkills();

        return new EntityStandType(
                new StandStats.Builder()
                        .power(13.5D)
                        .speed(15.0D)
                        .range(5.0D, 10.0D)
                        .durability(5.0D)
                        .precision(10.0D)
                        .randomWeight(2.0D)
                        .build(),
                moveset,
                SHADOW_THE_WORLD_ENTITY.get(),
                id)
                .standControlType(StandControlType.CLOSE_RANGE_DIRECT)
                .manualControl(true)
                .distanceStrengthDecay(true)
                .standDimensions(0.7F, 2.15F)
                .summonShout(() -> SDDIO_SHADOW_WORLD)
                .summonSound(() -> SHADOW_WORLD_SUMMON)
                .unsummonSound(() -> SHADOW_WORLD_UNSUMMON)
                .ost(() -> SHADOW_WORLD_OST);
    }
}
