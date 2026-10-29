package rotp.addons.diegosworld;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import rotp.core.api.RotpAddonApi;
import rotp.core.api.timestop.TimeStopAudioContext;
import rotp.core.api.timestop.TimeStopAudioDecision;
import rotp.core.api.timestop.TimeStopBehaviorPolicies;
import rotp.core.api.timestop.TimeStopBehaviorPolicy;
import rotp.core.api.timestop.TimeStopProgressionPolicy;
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

import rotp.addons.diegosworld.ability.DiegoBarrageAbility;
import rotp.addons.diegosworld.ability.DiegoBarbedWireTrapAbility;
import rotp.addons.diegosworld.ability.DiegoHeavyPunchAbility;
import rotp.addons.diegosworld.ability.DiegoKnifeBarrageAbility;
import rotp.addons.diegosworld.ability.DiegoKnockbackPunchAbility;
import rotp.addons.diegosworld.ability.DiegoTimeStopAbility;
import rotp.addons.diegosworld.block.BarbedWireBlock;
import rotp.addons.diegosworld.block.BarbedWireBlockEntity;
import rotp.addons.diegosworld.effect.DiegoBleedingEffect;
import rotp.addons.diegosworld.entity.DiegoRevolverBulletEntity;
import rotp.addons.diegosworld.entity.DiegosWorldStandEntity;
import rotp.addons.diegosworld.item.RevolverItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(DiegosTheWorldAddon.MOD_ID)
public final class DiegosTheWorldAddon {
    public static final String MOD_ID = "rotp_theworld";
    private static final int REQUIRED_CORE_ABI = 1;

    public static final String PUNCH = "punch";
    public static final String BARRAGE = "barrage";
    public static final String HEAVY_PUNCH = "heavy_punch";
    public static final String HEAVY_CHARGED = "heavy_charged";
    public static final String KNOCKBACK_PUNCH = "kick";
    public static final String GRAB = "grab";
    public static final String GRAB_THROW = "grab_throw";
    public static final String GUARD = "guard";
    public static final String TIME_STOP = "time_stop";
    public static final String TIME_STOP_BLINK = "time_stop_blink";
    // Donor time stop builder: staminaCost(225), staminaCostTick(7.5).
    public static final float DONOR_TIME_STOP_STAMINA_COST = 225.0F;
    public static final float DONOR_TIME_STOP_STAMINA_COST_TICK = 7.5F;
    public static final String KNIFE_BARRAGE = "knife_barrage";
    public static final String TIME_STOP_PUNCH = "ts_punch";
    // Donor THEWORLDTSHeavyAttack builder: staminaCost(200).
    public static final float DONOR_TS_PUNCH_STAMINA_COST = 200.0F;
    public static final String BARBED_WIRE_TRAP = "barbed_wire_trap";

    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(
                    JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>>
            BLOCK_ENTITY_TYPES =
                    DeferredRegister.create(
                            Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(
                    JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<DiegoBarrageAbility>>
            THE_WORLD_BARRAGE = ABILITY_TYPES.register(
                    "the_world_barrage",
                    key -> new AbilityType<>(
                            key, DiegoBarrageAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<DiegoHeavyPunchAbility>>
            THE_WORLD_HEAVY_PUNCH = ABILITY_TYPES.register(
                    "the_world_heavy_punch",
                    key -> new AbilityType<>(
                            key, DiegoHeavyPunchAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<DiegoKnockbackPunchAbility>>
            THE_WORLD_KNOCKBACK_PUNCH = ABILITY_TYPES.register(
                    "the_world_knockback_punch",
                    key -> new AbilityType<>(
                            key, DiegoKnockbackPunchAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<DiegoTimeStopAbility>>
            THE_WORLD_TIME_STOP_ABILITY = ABILITY_TYPES.register(
                    "the_world_time_stop",
                    key -> new AbilityType<>(
                            key, DiegoTimeStopAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<DiegoKnifeBarrageAbility>>
            THE_WORLD_KNIFE_BARRAGE = ABILITY_TYPES.register(
                    "the_world_knife_barrage",
                    key -> new AbilityType<>(
                            key, DiegoKnifeBarrageAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<DiegoBarbedWireTrapAbility>>
            THE_WORLD_BARBED_WIRE_TRAP = ABILITY_TYPES.register(
                    "the_world_barbed_wire_trap",
                    key -> new AbilityType<>(
                            key, DiegoBarbedWireTrapAbility::new));

    public static final DeferredBlock<BarbedWireBlock>
            BARBED_WIRE = BLOCKS.registerBlock(
                    "barbed_wire",
                    BarbedWireBlock::new,
                    BlockBehaviour.Properties
                            .ofFullCopy(Blocks.IRON_BARS)
                            .noOcclusion());
    public static final DeferredHolder<
            BlockEntityType<?>, BlockEntityType<BarbedWireBlockEntity>>
            BARBED_WIRE_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
                    "barbed_wire",
                    key -> BlockEntityType.Builder.of(
                                    BarbedWireBlockEntity::new,
                                    BARBED_WIRE.get())
                            .build(null));

    public static final DeferredHolder<
            EntityType<?>, EntityType<DiegosWorldStandEntity>>
            THE_WORLD_ENTITY = ENTITY_TYPES.register(
                    "the_world",
                    key -> EntityType.Builder
                            .<DiegosWorldStandEntity>of(
                                    DiegosWorldStandEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.7F, 2.15F)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));
    public static final DeferredHolder<
            EntityType<?>, EntityType<DiegoRevolverBulletEntity>>
            REVOLVER_BULLET = ENTITY_TYPES.register(
                    "revolver_bullet",
                    key -> EntityType.Builder
                            .<DiegoRevolverBulletEntity>of(
                                    DiegoRevolverBulletEntity::new,
                                    MobCategory.MISC)
                            .fireImmune()
                            .sized(0.0625F, 0.0625F)
                            .clientTrackingRange(16)
                            .updateInterval(1)
                            .build(key.toString()));
    public static final DeferredHolder<Item, RevolverItem> REVOLVER =
            ITEMS.register(
                    "revolver",
                    key -> new RevolverItem(
                            new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<Item, BlockItem> BARBED_WIRE_ITEM =
            ITEMS.registerSimpleBlockItem(
                    "barbed_wire",
                    BARBED_WIRE,
                    new Item.Properties().stacksTo(8));
    public static final DeferredHolder<MobEffect, DiegoBleedingEffect>
            BLEEDING = MOB_EFFECTS.register(
                    "bleeding", DiegoBleedingEffect::new);

    public static final DeferredHolder<SoundEvent, SoundEvent>
            THE_WORLD_SUMMON = sound("the_world_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            THE_WORLD_MUDA = sound("the_world_muda");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            THE_WORLD_MUDA_LONG = sound("the_world_muda_long");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            THE_WORLD_MUDA_MUDA_MUDA =
                    sound("the_world_muda_muda_muda");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            THE_WORLD_KNIFE_BARRAGE_VOICE =
                    sound("the_world_knife_barrage_voice");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            THE_WORLD_TIME_STOP = sound("the_world_time_stop");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            THE_WORLD_TIME_RESUME = sound("the_world_time_resume");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            THE_WORLD_TIME_STOP_BLINK =
                    sound("the_world_time_stop_blink");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIEGO_THE_WORLD = sound("diego_the_world");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIEGO_MUDA = sound("diego_muda");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIEGO_MUDA_MUDA = sound("diego_muda_muda");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIEGO_WRY = sound("diego_wry");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIEGO_DIE = sound("diego_die");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIEGO_THIS_IS_THE_WORLD =
                    sound("diego_this_is_the_world");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIEGO_TIME_STOP = sound("diego_time_stop");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIEGO_TOMARE_TOKI_YO = sound("diego_tomare_toki_yo");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIEGO_TIME_RESUMES = sound("diego_time_resumes");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIEGO_TIMES_UP = sound("diego_times_up");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIEGO_5_SECONDS = sound("diego_5_seconds");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIEGO_ONE_MORE = sound("diego_one_more");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIEGO_CANT_MOVE = sound("diego_cant_move");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            REVOLVER_SHOT = sound("revolver_shot");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            REVOLVER_NO_AMMO = sound("revolver_no_ammo");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIEGO_AND_NOW_THE_FUN_BEGINS =
                    sound("diego_and_now_the_fun_begins");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            THE_WORLD_KNIVES_THROW =
                    sound("the_world_knives_throw");
    public static final OstSoundList THE_WORLD_OST =
            new OstSoundList(id("the_world_ost"), SOUND_EVENTS);
    // Donor timeStopMaxTicks(100, 120): humans 100; high-blood vampires,
    // Pillar Men and high-saturation zombies 120 (the core default is 180).
    // Learning, decay and cooldown keep the TimeStop.Builder defaults.
    private static final TimeStopProgressionPolicy TIME_STOP_PROGRESSION =
            new TimeStopProgressionPolicy(100, 120, 120, 0.1F, 0.0F, 3.0F);
    private static final TimeStopBehaviorPolicy TIME_STOP_AUDIO =
            new TimeStopBehaviorPolicy() {
                @Override
                public TimeStopAudioDecision audio(
                        TimeStopAudioContext context) {
                    return switch (context.cue()) {
                        case START_VOICE -> TimeStopAudioDecision.sound(
                                context.standAlreadySummoned()
                                        ? context.user().getRandom()
                                                .nextBoolean()
                                                        ? DIEGO_TIME_STOP
                                                        : DIEGO_TOMARE_TOKI_YO
                                        : DIEGO_THE_WORLD);
                        case START_SOUND -> TimeStopAudioDecision.sound(
                                THE_WORLD_TIME_STOP);
                        case RESUME_VOICE -> TimeStopAudioDecision.sound(
                                context.isManualResume()
                                        ? DIEGO_TIME_RESUMES
                                        : DIEGO_TIMES_UP);
                        case RESUME_SOUND -> TimeStopAudioDecision.sound(
                                THE_WORLD_TIME_RESUME);
                    };
                }

                @Override
                public TimeStopProgressionPolicy progression() {
                    return TIME_STOP_PROGRESSION;
                }
            };

    public static final DeferredHolder<StandType, EntityStandType>
            THE_WORLD = STAND_TYPES.register(
                    "the_world", DiegosTheWorldAddon::createStand);

    public static final ResourceKey<DamageType> WIRE_DAMAGE =
            ResourceKey.create(
                    Registries.DAMAGE_TYPE, id("wire_damage"));

    public DiegosTheWorldAddon(IEventBus modEventBus) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_RANDOM_WEIGHTS_V1)
                // blink base costs (R146 core)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_TIME_STOP_ABILITY_TUNING_V1)
                // TS punch cost and training (R147 core)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_TIME_STOP_PUNCH_TUNING_V1)
                // 1.16 action shouts follow the sneak rule
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_SHOUTS_V1)
                // TS punch donor clip and blink-capped skip (R148 core)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_TIME_STOP_PUNCH_DONOR_OPTIONS_V1)) {
            throw new IllegalStateException(
                    "Diego's The World requires ROTP addon ABI 1 "
                            + "with namespaced abilities and Stand "
                            + "visual contexts and random weights and "
                            + "time-stop ability and TS punch tuning "
                            + "and ability shouts and TS punch donor "
                            + "options "
                            + "(a core from the same release).");
        }

        ABILITY_TYPES.register(modEventBus);
        BLOCKS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        ITEMS.register(modEventBus);
        MOB_EFFECTS.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        TimeStopBehaviorPolicies.register(
                id("time_stop_audio"), id("the_world"), TIME_STOP_AUDIO);
        modEventBus.addListener(
                DiegoModEvents::registerAttributes);
        modEventBus.addListener(
                DiegoModEvents::buildCreativeTab);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(
            String path) {
        return SOUND_EVENTS.register(
                path, SoundEvent::createVariableRangeEvent);
    }

    private static EntityStandType createStand(ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addHumanoidStandStuff()
                .addAbility(
                        PUNCH,
                        ModStandAbilities.PUNCH,
                        punch -> punch
                                .punchImpactSound(
                                        ModSoundEvents
                                                .STAND_PUNCH_LIGHT)
                                .standCrySound(DIEGO_MUDA))
                .addAbility(
                        BARRAGE,
                        THE_WORLD_BARRAGE,
                        // 1.16 standSound(THE_WORLD_MUDA_MUDA_MUDA) also
                        // played while the Stand was arms-only.
                        barrage -> barrage
                                .barrageHitSound(
                                        ModSoundEvents
                                                .STAND_PUNCH_LIGHT)
                                .barrageCrySound(
                                        THE_WORLD_MUDA_MUDA_MUDA)
                                .barrageCryInArmsOnly())
                .addAbility(
                        HEAVY_PUNCH,
                        THE_WORLD_HEAVY_PUNCH)
                .addAbility(
                        "heavy_charged",
                        ModStandAbilities.HEAVY_CHARGED)
                .addAbility(
                        KNOCKBACK_PUNCH,
                        THE_WORLD_KNOCKBACK_PUNCH,
                        punch -> {
                            punch.heavyPunchImpactSound(
                                    ModSoundEvents
                                            .STAND_PUNCH_HEAVY);
                            punch.heavyPunchCrySound(
                                    THE_WORLD_MUDA_LONG);
                            punch.initIsFinisher(HEAVY_PUNCH);
                            punch.resolveLevelToUnlock(1);
                        })
                .addAbility("grab", ModStandAbilities.GRAB)
                .addAbility(
                        "grab_throw",
                        ModStandAbilities.GRAB_THROW)
                .addAbility(
                        "grab_punch",
                        ModStandAbilities.PUNCH,
                        punch -> {
                            punch.punchImpactSound(
                                    ModSoundEvents
                                            .STAND_PUNCH_LIGHT);
                            punch.standCrySound(DIEGO_MUDA);
                            punch.initIsGrabVariation();
                        })
                .addAbility(
                        "grab_barrage",
                        THE_WORLD_BARRAGE,
                        barrage -> {
                            barrage.barrageHitSound(
                                    ModSoundEvents
                                            .STAND_PUNCH_LIGHT);
                            barrage.barrageCrySound(
                                    THE_WORLD_MUDA_MUDA_MUDA);
                            barrage.barrageCryInArmsOnly();
                            barrage.initIsGrabVariation();
                        })
                .addAbility(
                        "grab_heavy_punch",
                        THE_WORLD_HEAVY_PUNCH,
                        punch -> {
                            punch.initIsGrabVariation();
                        })
                .addAbility(GUARD, ModStandAbilities.GUARD)
                .addAbility(
                        TIME_STOP,
                        THE_WORLD_TIME_STOP_ABILITY,
                        ability ->
                                ability.setIgnoresPerformerStun())
                // Donor TimeStopInstant(..., false): the blink stops in
                // front of the target, at 0.8 of the donor 225 / 7.5.
                .addAbility(
                        TIME_STOP_BLINK,
                        ModStandAbilities.TIME_STOP_BLINK,
                        ability ->
                                ability.setBaseTimeStopStaminaCosts(
                                        DONOR_TIME_STOP_STAMINA_COST,
                                        DONOR_TIME_STOP_STAMINA_COST_TICK))
                .addAbility(
                        KNIFE_BARRAGE,
                        THE_WORLD_KNIFE_BARRAGE)
                // Donor THEWORLDTSHeavyAttack: staminaCost(200) and no time-stop
                // training; its time skip is priced from the blink above.
                // Donor standPose(HEAVY_ATTACK_FINISHER): the knockback punch clip.
                // Donor preTaskInit started the skip from the blink's
                // getMaxImpliedTicks (full blink start reserved, min 5).
                // Donor world.playSound(null, blink): every player in
                // range hears the blink, no unrevealed sound.
                .addAbility(
                        TIME_STOP_PUNCH,
                        ModStandAbilities.TW_TS_PUNCH,
                        punch -> punch
                                .setStaminaCost(DONOR_TS_PUNCH_STAMINA_COST)
                                .setTrainsTimeStop(false)
                                .setEntityAnim(KNOCKBACK_PUNCH)
                                .setSkipCappedByBlinkImpliedTicks(true)
                                .setTimeSkipSoundHeardByAll(true))
                .addAbility(
                        BARBED_WIRE_TRAP,
                        THE_WORLD_BARBED_WIRE_TRAP)
                .makeControlScheme("hotbar")
                    .bind(PUNCH, InputMethod.CLICK, InputKey.LMB)
                    .bind(BARRAGE, InputMethod.HOLD, InputKey.LMB)
                    .bind(
                            HEAVY_PUNCH,
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
                    .bind(GUARD, InputMethod.HOLD, InputKey.RMB)
                    .makeHotbar(
                            0, USE_SPECIAL, SWITCH_SPECIAL)
                    .addToHotbar(
                            KNIFE_BARRAGE, 0, InputMethod.CLICK)
                    .addToHotbar(
                            TIME_STOP_PUNCH, 0, InputMethod.CLICK)
                    // The donor blink (the_world_time_resume's SHIFT
                    // variation) is its own slot after the time stop.
                    .addToHotbar(
                            TIME_STOP, 0, InputMethod.HOLD)
                    .addToHotbar(
                            TIME_STOP_BLINK, 0, InputMethod.CLICK)
                    .addToHotbar(
                            BARBED_WIRE_TRAP, 0, InputMethod.CLICK)
                .finalizeControlScheme()
                .makeControlScheme("keybinds")
                    .bind(PUNCH, InputMethod.CLICK, InputKey.LMB)
                    .bind(BARRAGE, InputMethod.HOLD, InputKey.LMB)
                    .bind(
                            HEAVY_PUNCH,
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
                    .bind(GUARD, InputMethod.HOLD, InputKey.RMB)
                .finalizeControlScheme()
                .addSkill(
                        StandUnlockableSkill.startingAbility(PUNCH))
                .addSkill(
                        StandUnlockableSkill.startingAbility(BARRAGE))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                HEAVY_PUNCH))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                        "heavy_charged")
                                .prerequisiteSkill(HEAVY_PUNCH))
                .addSkill(
                        StandUnlockableSkill.unlockableAbility(
                                        KNOCKBACK_PUNCH, 150)
                                .prerequisiteSkill(HEAVY_PUNCH))
                .addSkill(
                        StandUnlockableSkill.startingAbility(GUARD))
                .addSkill(
                        StandUnlockableSkill.startingAbility("grab"))
                .addSkill(
                        StandUnlockableSkill.unlockableAbility(
                                        "grab_throw", 100)
                                .withAbility(
                                        "grab_punch",
                                        "grab_barrage",
                                        "grab_heavy_punch")
                                .prerequisiteSkill("grab"))
                .addSkill(
                        StandUnlockableSkill.unlockableAbility(
                                TIME_STOP, 5000))
                .addSkill(
                        StandUnlockableSkill.unlockableAbility(
                                        KNIFE_BARRAGE, 2500)
                                .prerequisiteSkill(BARRAGE))
                .addSkill(
                        StandUnlockableSkill.unlockableAbility(
                                        TIME_STOP_PUNCH, 6000)
                                .prerequisiteSkill(TIME_STOP))
                .addSkill(
                        StandUnlockableSkill.unlockableAbility(
                                        BARBED_WIRE_TRAP, 1000)
                                .prerequisiteSkill(TIME_STOP))
                .addHumanoidStandSkills();

        return new EntityStandType(
                new StandStats.Builder()
                        .power(16.0D)
                        .speed(16.0D)
                        .range(2.0D, 10.0D)
                        .durability(16.0D)
                        .precision(12.0D)
                        .randomWeight(2.0D)
                        .build(),
                moveset,
                THE_WORLD_ENTITY.get(),
                id)
                .standControlType(StandControlType.CLOSE_RANGE_DIRECT)
                .manualControl(true)
                .distanceStrengthDecay(true)
                .standDimensions(0.7F, 2.15F)
                .summonShout(() -> DIEGO_THE_WORLD)
                .summonSound(() -> THE_WORLD_SUMMON)
                .unsummonSound(() -> ModSoundEvents.STAND_UNSUMMON)
                .ost(() -> THE_WORLD_OST);
    }
}
