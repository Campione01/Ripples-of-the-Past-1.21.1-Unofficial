package rotp.addons.diverdown;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import java.util.function.BiFunction;

import rotp.core.api.RotpAddonApi;
import rotp.core.api.stand.StandSkillWalls;
import rotp.core.core.JojoRegistries;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.mechanics.resolve.ResolveCounter;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.util.sound.OstSoundList;

import rotp.addons.diverdown.ability.BoneShardBombAbility;
import rotp.addons.diverdown.ability.DiverDownHeavyPunchAbility;
import rotp.addons.diverdown.ability.DiverDownPunchAbility;
import rotp.addons.diverdown.ability.DeformDashAbility;
import rotp.addons.diverdown.ability.EntityPhasingAbility;
import rotp.addons.diverdown.ability.InsideProtectionAbility;
import rotp.addons.diverdown.ability.KineticTrapAbility;
import rotp.addons.diverdown.ability.MobDisassembleAbility;
import rotp.addons.diverdown.ability.RetractAbility;
import rotp.addons.diverdown.ability.SpringLegsAbility;
import rotp.addons.diverdown.ability.StealItemAbility;
import rotp.addons.diverdown.ability.WallGlideAbility;
import rotp.addons.diverdown.effect.BoneShardBombEffect;
import rotp.addons.diverdown.effect.SpringLegsEffect;
import rotp.addons.diverdown.entity.BoneShardEntity;
import rotp.addons.diverdown.entity.DiverDownStandEntity;
import rotp.addons.diverdown.entity.KineticTrapEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(DiverDownAddon.MOD_ID)
public final class DiverDownAddon {
    public static final String MOD_ID = "rotp_dd";
    private static final int REQUIRED_CORE_ABI = 1;

    public static final String PUNCH = "punch";
    public static final String BARRAGE = "barrage";
    public static final String HEAVY_PUNCH = "heavy_punch";
    public static final String HEAVY_CHARGED = "heavy_charged";
    public static final String GRAB = "grab";
    public static final String GRAB_THROW = "grab_throw";
    public static final String GUARD = "guard";
    public static final String DEFORM_DASH_NAME = "deform_dash";
    public static final String WALL_GLIDE_NAME = "wall_glide";
    public static final String INSIDE_PROTECTION_NAME =
            "inside_protection";
    public static final String ENTITY_PHASING_NAME = "entity_phasing";
    public static final String KINETIC_TRAP_NAME = "kinetic_trap";
    public static final String DISASSEMBLE_MOB_NAME =
            "disassemble_mob";
    public static final String RETRACT_NAME = "retract";
    public static final String SPRING_LEGS_NAME = "spring_legs";
    public static final String BONE_SHARD_BOMB_NAME =
            "bone_shard_bomb";
    public static final String STEAL_ITEM_NAME = "steal_item";

    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(
                    JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(
                    JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<DeformDashAbility>>
            DEFORM_DASH = ability(
                    DEFORM_DASH_NAME, DeformDashAbility::new);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<WallGlideAbility>>
            WALL_GLIDE = ability(
                    WALL_GLIDE_NAME, WallGlideAbility::new);
    // 1.16 Anasui shouts on the punch and heavy punch
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<DiverDownPunchAbility>>
            DIVER_DOWN_PUNCH_TYPE = ability(
                    "diver_down_punch", DiverDownPunchAbility::new);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<DiverDownHeavyPunchAbility>>
            DIVER_DOWN_HEAVY_PUNCH_TYPE = ability(
                    "diver_down_heavy_punch",
                    DiverDownHeavyPunchAbility::new);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<InsideProtectionAbility>>
            INSIDE_PROTECTION = ability(
                    INSIDE_PROTECTION_NAME,
                    InsideProtectionAbility::new);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<EntityPhasingAbility>>
            ENTITY_PHASING = ability(
                    ENTITY_PHASING_NAME,
                    EntityPhasingAbility::new);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<KineticTrapAbility>>
            KINETIC_TRAP = ability(
                    KINETIC_TRAP_NAME, KineticTrapAbility::new);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<MobDisassembleAbility>>
            DISASSEMBLE_MOB = ability(
                    DISASSEMBLE_MOB_NAME,
                    MobDisassembleAbility::new);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<RetractAbility>>
            RETRACT = ability(RETRACT_NAME, RetractAbility::new);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<SpringLegsAbility>>
            SPRING_LEGS = ability(
                    SPRING_LEGS_NAME, SpringLegsAbility::new);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<BoneShardBombAbility>>
            BONE_SHARD_BOMB = ability(
                    BONE_SHARD_BOMB_NAME,
                    BoneShardBombAbility::new);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<StealItemAbility>>
            STEAL_ITEM = ability(
                    STEAL_ITEM_NAME, StealItemAbility::new);

    public static final DeferredHolder<
            EntityType<?>, EntityType<DiverDownStandEntity>>
            DIVER_DOWN_ENTITY = ENTITY_TYPES.register(
                    "diver_down",
                    key -> EntityType.Builder
                            .<DiverDownStandEntity>of(
                                    DiverDownStandEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.65F, 1.95F)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));
    public static final DeferredHolder<
            EntityType<?>, EntityType<KineticTrapEntity>>
            KINETIC_TRAP_ENTITY = ENTITY_TYPES.register(
                    "kinetic_trap",
                    key -> EntityType.Builder.<KineticTrapEntity>of(
                                    KineticTrapEntity::new,
                                    MobCategory.MISC)
                            .noSummon()
                            .sized(0.3F, 0.3F)
                            .clientTrackingRange(64)
                            .updateInterval(2)
                            .build(key.toString()));
    public static final DeferredHolder<
            EntityType<?>, EntityType<BoneShardEntity>>
            BONE_SHARD_ENTITY = ENTITY_TYPES.register(
                    "bone_shard",
                    key -> EntityType.Builder.<BoneShardEntity>of(
                                    BoneShardEntity::new,
                                    MobCategory.MISC)
                            .noSummon()
                            .sized(0.2F, 0.2F)
                            .clientTrackingRange(64)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<
            MobEffect, SpringLegsEffect> SPRING_LEGS_EFFECT =
            MOB_EFFECTS.register(
                    "spring_legs", SpringLegsEffect::new);
    public static final DeferredHolder<
            MobEffect, BoneShardBombEffect> BONE_SHARD_BOMB_EFFECT =
            MOB_EFFECTS.register(
                    "bone_shard_bomb", BoneShardBombEffect::new);

    public static final DeferredHolder<SoundEvent, SoundEvent>
            ANASUI_DIVER_DOWN = sound("anasui_diver_down");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            ANASUI_DIVER_PUNCH_LIGHT =
            sound("anasui_diver_punch_light");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            ANASUI_DIVER_PUNCH_HEAVY =
            sound("anasui_diver_punch_heavy");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            ANASUI_DIVER_DEFORM_DASH =
            sound("anasui_diver_deform_dash");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            ANASUI_DIVER_ENTITY_PHASING =
            sound("anasui_diver_entity_phasing");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            ANASUI_DIVER_RETRACT =
            sound("anasui_diver_retract");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIVER_DOWN_SUMMON = sound("diver_down_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIVER_DOWN_UNSUMMON = sound("diver_down_unsummon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIVER_DOWN_PUNCH_LIGHT =
            sound("diver_down_punch_light");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIVER_DOWN_PUNCH_HEAVY =
            sound("diver_down_punch_heavy");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIVER_DOWN_DEFORM_DASH =
            sound("diver_down_deform_dash");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIVER_DOWN_GLIDE = sound("diver_down_glide");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIVER_DOWN_GLIDING = sound("diver_down_gliding");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIVER_DOWN_ENTITY_PHASING =
            sound("diver_down_entity_phasing");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIVER_DOWN_INSIDE_PROTECTION =
            sound("diver_down_inside_protection");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIVER_DOWN_RETRACT = sound("diver_down_retract");
    public static final OstSoundList DIVER_DOWN_OST =
            new OstSoundList(
                    id("diver_down_ost"), SOUND_EVENTS);

    public static final DeferredHolder<StandType, EntityStandType>
            DIVER_DOWN = STAND_TYPES.register(
                    "diver_down", DiverDownAddon::createStand);

    // 1.16 InitStands: addAttackerResolveMultTier(1), tier 6, like Star
    // Platinum and The World (2x Resolve for an ordinary attacker).
    public static final int ATTACKER_RESOLVE_MULT_TIER = 1;

    public DiverDownAddon(IEventBus modEventBus) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)
                // attacker Resolve tier table (R146 core)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_ATTACKER_RESOLVE_TIERS_V1)
                // 1.16 action shouts follow the sneak rule
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_SHOUTS_V1)) {
            throw new IllegalStateException(
                    "RotP Diver Down requires ROTP addon ABI 1 with "
                            + "namespaced ability resources, Stand "
                            + "visual contexts and attacker Resolve "
                            + "tiers (a core from the same release).");
        }
        // the plain abilities call Ability.checkPerformerStun (R148 core)
        if (!RotpAddonApi.supportsFeature(
                RotpAddonApi.FEATURE_ABILITY_PERFORMER_STUN_V1)) {
            throw new IllegalStateException(
                    "RotP Diver Down requires the core performer stun "
                            + "check (a core from the same release).");
        }
        // the Kinetic Trap refreshes its front offset there (K5-50 core)
        if (!RotpAddonApi.supportsFeature(
                RotpAddonApi.FEATURE_ACTION_STAND_OFFSET_HOOK_V1)) {
            throw new IllegalStateException(
                    "RotP Diver Down requires the Stand action offset "
                            + "hook (a core from the same release).");
        }
        // Steal Item, a 1.16 StandAction its user performs, runs in the
        // user's Stand-power action slot (K5-48 core)
        if (!RotpAddonApi.supportsFeature(
                RotpAddonApi.FEATURE_STAND_POWER_ACTION_SLOT_V1)) {
            throw new IllegalStateException(
                    "RotP Diver Down requires the Stand-power action "
                            + "slot (a core from the same release).");
        }

        ABILITY_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        MOB_EFFECTS.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        ResolveCounter.addAttackerResolveMultTier(
                id("diver_down"), ATTACKER_RESOLVE_MULT_TIER);
        modEventBus.addListener(
                DiverDownGameEvents::registerAttributes);
        modEventBus.addListener(DiverDownAddon::markSkillsThroughWalls);
    }

    // Diver Down phases through solids (Stone Ocean ch. 74, 84, 106): its
    // punches, heavy punches and barrages strike what the aim meets behind
    // a wall, and the deform dash and the dive into a target aim through
    // walls. The kinetic trap is set on the block the aim meets.
    private static void markSkillsThroughWalls(FMLCommonSetupEvent event) {
        StandSkillWalls.markAttacksThroughWalls(DIVER_DOWN.getId());
        StandSkillWalls.markThroughWalls(
                DIVER_DOWN.getId(), DEFORM_DASH_NAME);
        StandSkillWalls.markThroughWalls(
                DIVER_DOWN.getId(), ENTITY_PHASING_NAME);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static boolean isDiverDownUser(LivingEntity entity) {
        StandPower power =
                entity != null ? StandPower.get(entity) : null;
        return power != null
                && power.hasPower()
                && power.getPowerType() == DIVER_DOWN.get();
    }

    public static DiverDownStandEntity activeStand(
            LivingEntity entity) {
        StandPower power =
                entity != null ? StandPower.get(entity) : null;
        return power != null
                && power.hasPower()
                && power.isSummoned()
                && power.getPowerType() == DIVER_DOWN.get()
                && power.getSummonedStandEntity()
                        instanceof DiverDownStandEntity stand
                && stand.isAlive()
                && !stand.isRemoved()
                && stand.getUser() == entity
                ? stand
                : null;
    }

    private static <T extends Ability>
            DeferredHolder<AbilityType<?>, AbilityType<T>> ability(
                    String name,
                    BiFunction<AbilityType<T>, AbilityId, T>
                            constructor) {
        return ABILITY_TYPES.register(
                name, key -> new AbilityType<>(key, constructor));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(
            String path) {
        return SOUND_EVENTS.register(
                path, SoundEvent::createVariableRangeEvent);
    }

    private static EntityStandType createStand(ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addHumanoidStandStuff()
                // 1.16 Anasui's punch lines are user shouts
                .addAbility("punch", DIVER_DOWN_PUNCH_TYPE, punch ->
                        punch.punchImpactSound(
                                DIVER_DOWN_PUNCH_LIGHT))
                // 1.16 diver_down_barrage had no shout
                .addAbility(
                        "barrage",
                        ModStandAbilities.BARRAGE,
                        barrage -> barrage.barrageHitSound(
                                DIVER_DOWN_PUNCH_LIGHT))
                .addAbility(
                        "heavy_punch",
                        DIVER_DOWN_HEAVY_PUNCH_TYPE,
                        punch -> punch.heavyPunchImpactSound(
                                DIVER_DOWN_PUNCH_HEAVY))
                .addAbility(
                        "heavy_charged",
                        ModStandAbilities.HEAVY_CHARGED)
                .addAbility("grab", ModStandAbilities.GRAB)
                .addAbility(
                        "grab_throw", ModStandAbilities.GRAB_THROW)
                .addAbility(
                        "grab_punch",
                        ModStandAbilities.PUNCH,
                        punch -> {
                            punch.punchImpactSound(
                                            DIVER_DOWN_PUNCH_LIGHT)
                                    .standCrySound(
                                            ANASUI_DIVER_PUNCH_LIGHT);
                            punch.initIsGrabVariation();
                        })
                .addAbility(
                        "grab_barrage",
                        ModStandAbilities.BARRAGE,
                        barrage -> {
                            barrage.barrageHitSound(
                                            DIVER_DOWN_PUNCH_LIGHT)
                                    .barrageCrySound(
                                            ANASUI_DIVER_PUNCH_LIGHT);
                            barrage.initIsGrabVariation();
                        })
                .addAbility(
                        "grab_heavy_punch",
                        ModStandAbilities.HEAVY_PUNCH,
                        punch -> {
                            punch.heavyPunchImpactSound(
                                            DIVER_DOWN_PUNCH_HEAVY)
                                    .heavyPunchCrySound(
                                            ANASUI_DIVER_PUNCH_HEAVY);
                            punch.initIsGrabVariation();
                        })
                .addAbility("guard", ModStandAbilities.GUARD)
                .addAbility(
                        DEFORM_DASH_NAME,
                        DEFORM_DASH,
                        dash -> dash.initIsFinisher(HEAVY_PUNCH))
                .addAbility(WALL_GLIDE_NAME, WALL_GLIDE)
                .addAbility(
                        INSIDE_PROTECTION_NAME, INSIDE_PROTECTION)
                .addAbility(ENTITY_PHASING_NAME, ENTITY_PHASING)
                .addAbility(KINETIC_TRAP_NAME, KINETIC_TRAP)
                .addAbility(
                        DISASSEMBLE_MOB_NAME, DISASSEMBLE_MOB)
                .addAbility(RETRACT_NAME, RETRACT)
                .addAbility(SPRING_LEGS_NAME, SPRING_LEGS)
                .addAbility(
                        BONE_SHARD_BOMB_NAME, BONE_SHARD_BOMB)
                .addAbility(STEAL_ITEM_NAME, STEAL_ITEM)
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
                            MovesetBuilder
                                    .DEFAULT_CHARGED_HEAVY_INPUT)
                    .bind(
                            "grab",
                            InputMethod.CLICK,
                            MovesetBuilder.DEFAULT_GRAB_INPUT)
                    .bind(
                            "grab_throw",
                            InputMethod.HOLD,
                            MovesetBuilder.DEFAULT_GRAB_THROW_INPUT)
                    .bind("guard", InputMethod.HOLD, InputKey.RMB)
                    .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                    .addToHotbar(
                            WALL_GLIDE_NAME, 0, InputMethod.HOLD)
                    .addToHotbar(
                            ENTITY_PHASING_NAME,
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            INSIDE_PROTECTION_NAME,
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            KINETIC_TRAP_NAME,
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            SPRING_LEGS_NAME,
                            0,
                            InputMethod.CLICK)
                    .addHotbarSlotVariation(
                            BONE_SHARD_BOMB_NAME,
                            SPRING_LEGS_NAME,
                            InputKey.Modifier.SHIFT,
                            InputMethod.CLICK)
                    .addToHotbar(
                            STEAL_ITEM_NAME, 0, InputMethod.HOLD)
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
                            MovesetBuilder
                                    .DEFAULT_CHARGED_HEAVY_INPUT)
                    .bind(
                            "grab",
                            InputMethod.CLICK,
                            MovesetBuilder.DEFAULT_GRAB_INPUT)
                    .bind(
                            "grab_throw",
                            InputMethod.HOLD,
                            MovesetBuilder.DEFAULT_GRAB_THROW_INPUT)
                    .bind("guard", InputMethod.HOLD, InputKey.RMB)
                .finalizeControlScheme()
                .addSkill(
                        StandUnlockableSkill.startingAbility("punch"))
                .addSkill(
                        StandUnlockableSkill.startingAbility("barrage"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "heavy_punch"))
                .addSkill(StandUnlockableSkill.startingAbility(
                                "heavy_charged")
                        .prerequisiteSkill("heavy_punch"))
                .addSkill(
                        StandUnlockableSkill.startingAbility("grab"))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                                "grab_throw", 100)
                        .withAbility(
                                "grab_punch",
                                "grab_barrage",
                                "grab_heavy_punch")
                        .prerequisiteSkill("grab"))
                .addSkill(
                        StandUnlockableSkill.startingAbility("guard"))
                .addSkill(StandUnlockableSkill.startingAbility(
                                DEFORM_DASH_NAME)
                        .withAbility(DISASSEMBLE_MOB_NAME))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                        WALL_GLIDE_NAME, 100))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                        KINETIC_TRAP_NAME, 200))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                        STEAL_ITEM_NAME, 200))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                                ENTITY_PHASING_NAME, 300)
                        .withAbility(
                                RETRACT_NAME,
                                INSIDE_PROTECTION_NAME))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                        SPRING_LEGS_NAME, 400))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                                BONE_SHARD_BOMB_NAME, 400)
                        .prerequisiteSkill(SPRING_LEGS_NAME))
                .addHumanoidStandSkills();

        return new EntityStandType(
                new StandStats.Builder()
                        .power(14.0, 16.0)
                        .speed(14.0, 16.0)
                        .range(2.0, 10.0)
                        .durability(9.0, 10.0)
                        .precision(13.0)
                        .build(),
                moveset,
                DIVER_DOWN_ENTITY.get(),
                id)
                .standControlType(StandControlType.CLOSE_RANGE_DIRECT)
                .manualControl(true)
                .distanceStrengthDecay(true)
                .standDimensions(0.65F, 1.95F)
                .summonShout(() -> ANASUI_DIVER_DOWN)
                .summonSound(() -> DIVER_DOWN_SUMMON)
                .unsummonSound(() -> DIVER_DOWN_UNSUMMON);
    }
}
