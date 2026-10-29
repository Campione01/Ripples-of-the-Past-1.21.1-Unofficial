package rotp.addons.rhcp;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import rotp.core.api.RotpAddonApi;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityBlockAbility;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.entity.StandEntityAbility.AutoSummonMode;
import rotp.core.powersystem.standpower.type.StandType;

import rotp.addons.rhcp.ability.RhcpAbility;
import rotp.addons.rhcp.ability.RhcpAbility.Mode;
import rotp.addons.rhcp.ability.RhcpBarrageAbility;
import rotp.addons.rhcp.ability.RhcpHeavyAbility;
import rotp.addons.rhcp.ability.RhcpPunchAbility;
import rotp.addons.rhcp.entity.RhcpStandEntity;
import rotp.addons.rhcp.network.RhcpStatePayload;
import rotp.addons.rhcp.stand.RhcpStandType;
import rotp.addons.rhcp.state.RhcpState;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(RhcpAddon.MOD_ID)
public final class RhcpAddon {
    public static final String MOD_ID = "rotp_rhcp";
    public static final String NETWORK_PROTOCOL_VERSION = "2";

    public static final double EFFECTIVE_RANGE = 150.0D;
    public static final double MAX_RANGE = 300.0D;
    public static final float STAND_WIDTH = 0.4F;
    public static final float STAND_HEIGHT = 1.15F;

    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(
                    NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);

    public static final Supplier<AttachmentType<RhcpState>> STATE =
            ATTACHMENTS.register(
                    "state",
                    () -> AttachmentType.serializable(
                                    holder -> holder instanceof LivingEntity
                                            ? new RhcpState() : null)
                            .build());

    // 1.16 InitStands: the punches were StandEntityLightAttacks, the barrage
    // a StandEntityMeleeBarrage and the heavy a StandEntityHeavyAttack, so
    // they are the core punch, barrage and heavy punch here.
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<RhcpPunchAbility>>
            RHCP_PUNCH_OFF = punch("rhcp_punch_off", Mode.PUNCH_OFF);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<RhcpPunchAbility>>
            RHCP_PUNCH = punch("rhcp_punch", Mode.PUNCH);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<RhcpBarrageAbility>>
            RHCP_BARRAGE = ABILITY_TYPES.register(
                    "rhcp_barrage",
                    key -> new AbilityType<>(key, RhcpBarrageAbility::new));
    // 1.16 RHCPComboPunch extended RHCPHeavyPunch: the heavy punch's
    // finisher variation, with no wheel slot or skill of its own.
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<RhcpHeavyAbility>>
            RHCP_COMBO_PUNCH =
                    heavy("rhcp_combo_punch", Mode.COMBO_PUNCH);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<RhcpAbility>>
            RHCP_CHASE_TARGET =
                    ability("rhcp_chase_target", Mode.CHASE_TARGET);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<RhcpHeavyAbility>>
            RHCP_HEAVY_PUNCH = ABILITY_TYPES.register(
                    "rhcp_heavy_punch",
                    key -> new AbilityType<>(key, RhcpHeavyAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<RhcpAbility>>
            RHCP_ELECTROCUTE_TARGET = ability(
                    "rhcp_electrocute_target", Mode.ELECTROCUTE_TARGET);
    // 1.16 InitStands: rhcp_block was a plain StandEntityBlock, so the core
    // guard rules (front angle, stamina, knockback, guard break, auto-guard,
    // the RMB guard window) apply to it.
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<StandEntityBlockAbility>>
            RHCP_BLOCK = ABILITY_TYPES.register(
                    "rhcp_block",
                    key -> new AbilityType<>(key, RhcpAddon::block));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<RhcpAbility>>
            RHCP_GETTING_INTO_THE_MECHANISM = ability(
                    "rhcp_getting_into_the_mechanism",
                    Mode.ENTER_CONDUCTOR);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<RhcpAbility>>
            RHCP_EXIT = ability("rhcp_exit", Mode.EXIT_CONDUCTOR);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<RhcpAbility>>
            RHCP_RECHARGE = ability("rhcp_recharge", Mode.RECHARGE);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<RhcpAbility>>
            RHCP_BOOST = ability("rhcp_boost", Mode.BOOST);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<RhcpAbility>>
            RHCP_REDUCE_CONSUMPTION = ability(
                    "rhcp_reduce_consumption",
                    Mode.REDUCE_CONSUMPTION);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<RhcpAbility>>
            RHCP_DODGE = ability("rhcp_dodge", Mode.DODGE);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<RhcpPunchAbility>>
            RHCP_COUNTERATTACK =
                    punch("rhcp_counterattack", Mode.COUNTERATTACK);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<RhcpPunchAbility>>
            RHCP_BLITZ_ATTACK =
                    punch("rhcp_blitz_attack", Mode.BLITZ_ATTACK);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<RhcpAbility>>
            RHCP_ELECTRIC_DASH =
                    ability("rhcp_electric_dash", Mode.ELECTRIC_DASH);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<RhcpAbility>>
            RHCP_DRAG_INTO_LINE =
                    ability("rhcp_drag_into_line", Mode.DRAG_INTO_LINE);

    public static final DeferredHolder<
            EntityType<?>, EntityType<RhcpStandEntity>>
            RHCP_ENTITY = ENTITY_TYPES.register(
                    "rhcp",
                    key -> EntityType.Builder.<RhcpStandEntity>of(
                                    RhcpStandEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(STAND_WIDTH, STAND_HEIGHT)
                            .clientTrackingRange(320)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<StandType, RhcpStandType> RHCP =
            STAND_TYPES.register("rhcp", RhcpAddon::createStand);

    public static final DeferredHolder<
            ParticleType<?>, SimpleParticleType> RHCP_PARTICLE =
            PARTICLE_TYPES.register(
                    "rhcp_particle",
                    () -> new SimpleParticleType(false));
    public static final DeferredHolder<
            ParticleType<?>, SimpleParticleType> RHCP_FLASH =
            PARTICLE_TYPES.register(
                    "rhcp_flash",
                    () -> new SimpleParticleType(false));

    public static final DeferredHolder<SoundEvent, SoundEvent> AKIRA_RHCP =
            sound("akira_rhcp");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            RHCP_PUNCH_LIGHT = sound("rhcp_punch_light");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            RHCP_PUNCH_HEAVY = sound("rhcp_punch_heavy");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            RHCP_PUNCH_HEAVY_ENTITY =
                    sound("rhcp_punch_heavy_entity");
    public static final DeferredHolder<SoundEvent, SoundEvent> RHCP_CUT =
            sound("rhcp_cut");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            RHCP_THIS_IS_THE_END = sound("rhcp_this_is_the_end");
    public static final DeferredHolder<SoundEvent, SoundEvent> RHCP_NOISE =
            sound("rhcp_noise");
    public static final DeferredHolder<SoundEvent, SoundEvent> RHCP_ENTERING =
            sound("rhcp_entering");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            RHCP_ELECTRIC_DASH_SOUND = sound("rhcp_electric_dash");

    private static final Map<String, DeferredHolder<
            AbilityType<?>, ? extends AbilityType<?>>> ABILITIES =
            abilities();

    public RhcpAddon(IEventBus modEventBus) {
        if (!RotpAddonApi.supportsAbi(1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)
                // heavy-hit interrupt hooks (R146 core)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ENTITY_ACTION_HOOKS_V1)) {
            throw new IllegalStateException(
                    "RotP RHCP requires ROTP addon ABI 1 with entity "
                            + "action hooks (a core from the same "
                            + "release).");
        }
        ABILITY_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        PARTICLE_TYPES.register(modEventBus);
        ATTACHMENTS.register(modEventBus);
        modEventBus.addListener(RhcpEvents::registerAttributes);
        modEventBus.addListener(RhcpAddon::registerPayloads);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static RhcpState state(LivingEntity entity) {
        return entity.getData(STATE);
    }

    public static boolean isUser(LivingEntity entity) {
        var power = rotp.core.powersystem
                .standpower.StandPower.get(entity);
        return power != null && power.getPowerType() == RHCP.get();
    }

    private static DeferredHolder<
            AbilityType<?>, AbilityType<RhcpAbility>> ability(
                    String name, Mode mode) {
        return ABILITY_TYPES.register(
                name,
                key -> new AbilityType<>(
                        key,
                        (type, id) -> new RhcpAbility(type, id, mode)));
    }

    private static DeferredHolder<
            AbilityType<?>, AbilityType<RhcpPunchAbility>> punch(
                    String name, Mode mode) {
        return ABILITY_TYPES.register(
                name,
                key -> new AbilityType<>(
                        key,
                        (type, id) -> new RhcpPunchAbility(type, id, mode)));
    }

    private static DeferredHolder<
            AbilityType<?>, AbilityType<RhcpHeavyAbility>> heavy(
                    String name, Mode mode) {
        return ABILITY_TYPES.register(
                name,
                key -> new AbilityType<>(
                        key,
                        (type, id) -> new RhcpHeavyAbility(type, id, mode)));
    }

    // Every RHCP move summons the whole Stand in the port; the guard too.
    private static StandEntityBlockAbility block(
            AbilityType<StandEntityBlockAbility> type, AbilityId id) {
        StandEntityBlockAbility block = new StandEntityBlockAbility(type, id);
        block.standAutoSummonMode(AutoSummonMode.FULL);
        return block;
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(
            String name) {
        return SOUND_EVENTS.register(
                name,
                () -> SoundEvent.createVariableRangeEvent(id(name)));
    }

    private static void registerPayloads(
            RegisterPayloadHandlersEvent event) {
        event.registrar(NETWORK_PROTOCOL_VERSION).playToClient(
                RhcpStatePayload.TYPE,
                RhcpStatePayload.STREAM_CODEC,
                RhcpStatePayload::handle);
    }

    private static RhcpStandType createStand(ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addManualControl();
        ABILITIES.forEach((name, type) ->
                moveset.addAbility(name, (AbilityType<?>) type.get()));

        moveset.makeControlScheme("hotbar")
                .bind("rhcp_punch", InputMethod.CLICK, InputKey.LMB)
                .bind("rhcp_barrage", InputMethod.HOLD, InputKey.LMB)
                .bind(
                        "rhcp_heavy_punch",
                        InputMethod.CLICK,
                        InputKey.RMB)
                .bind("rhcp_block", InputMethod.HOLD, InputKey.RMB)
                .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                .addToHotbar(
                        "rhcp_getting_into_the_mechanism",
                        0,
                        InputMethod.CLICK)
                .addToHotbar("rhcp_exit", 0, InputMethod.CLICK)
                .addToHotbar("rhcp_recharge", 0, InputMethod.HOLD)
                .addToHotbar("rhcp_boost", 0, InputMethod.HOLD)
                .addHotbarSlotVariation(
                        "rhcp_reduce_consumption",
                        "rhcp_boost",
                        InputKey.Modifier.SHIFT,
                        InputMethod.CLICK)
                .addToHotbar("rhcp_dodge", 0, InputMethod.HOLD)
                .addToHotbar(
                        "rhcp_electric_dash",
                        0,
                        InputMethod.CLICK)
                .addToHotbar(
                        "rhcp_drag_into_line",
                        0,
                        InputMethod.CLICK)
                .finalizeControlScheme();

        moveset.makeControlScheme("keybinds")
                .bind("rhcp_punch", InputMethod.CLICK, InputKey.LMB)
                .bind("rhcp_barrage", InputMethod.HOLD, InputKey.LMB)
                .bind(
                        "rhcp_heavy_punch",
                        InputMethod.CLICK,
                        InputKey.RMB)
                .bind("rhcp_block", InputMethod.HOLD, InputKey.RMB)
                .finalizeControlScheme();

        moveset.addSkill(StandUnlockableSkill
                .startingAbility("rhcp_punch")
                .withAbility("rhcp_punch_off")
                .withAbility("rhcp_heavy_punch")
                // the heavy's finisher (Resolve 1), as in 1.16
                .withAbility("rhcp_combo_punch")
                .withAbility("rhcp_chase_target")
                .withAbility("rhcp_electrocute_target"));
        moveset.addSkill(StandUnlockableSkill
                .startingAbility("rhcp_barrage"));
        moveset.addSkill(StandUnlockableSkill
                .startingAbility("rhcp_block"));
        moveset.addSkill(StandUnlockableSkill
                .startingAbility("rhcp_getting_into_the_mechanism")
                .withAbility("rhcp_exit"));
        moveset.addSkill(StandUnlockableSkill
                .startingAbility("rhcp_recharge"));
        moveset.addSkill(StandUnlockableSkill
                .startingAbility("rhcp_boost")
                .withAbility("rhcp_reduce_consumption"));
        moveset.addSkill(StandUnlockableSkill
                .startingAbility("rhcp_dodge")
                .withAbility("rhcp_counterattack")
                .withAbility("rhcp_blitz_attack"));
        moveset.addSkill(StandUnlockableSkill
                .startingAbility("rhcp_electric_dash"));
        moveset.addSkill(StandUnlockableSkill
                .startingAbility("rhcp_drag_into_line"));

        RhcpStandType stand = new RhcpStandType(
                new StandStats.Builder()
                        .power(14.0D, 19.0D)
                        .speed(14.0D, 19.0D)
                        .range(EFFECTIVE_RANGE, MAX_RANGE)
                        .durability(14.0D)
                        .precision(8.5D, 9.0D)
                        .randomWeight(2.0D)
                        .build(),
                moveset,
                RHCP_ENTITY.get(),
                id);
        stand.standDimensions(STAND_WIDTH, STAND_HEIGHT);
        stand.summonShout(() -> AKIRA_RHCP);
        return stand;
    }

    private static Map<String, DeferredHolder<
            AbilityType<?>, ? extends AbilityType<?>>> abilities() {
        Map<String, DeferredHolder<
                AbilityType<?>, ? extends AbilityType<?>>> map =
                new LinkedHashMap<>();
        map.put("rhcp_punch_off", RHCP_PUNCH_OFF);
        map.put("rhcp_punch", RHCP_PUNCH);
        map.put("rhcp_barrage", RHCP_BARRAGE);
        map.put("rhcp_combo_punch", RHCP_COMBO_PUNCH);
        map.put("rhcp_chase_target", RHCP_CHASE_TARGET);
        map.put("rhcp_heavy_punch", RHCP_HEAVY_PUNCH);
        map.put("rhcp_electrocute_target", RHCP_ELECTROCUTE_TARGET);
        map.put("rhcp_block", RHCP_BLOCK);
        map.put(
                "rhcp_getting_into_the_mechanism",
                RHCP_GETTING_INTO_THE_MECHANISM);
        map.put("rhcp_exit", RHCP_EXIT);
        map.put("rhcp_recharge", RHCP_RECHARGE);
        map.put("rhcp_boost", RHCP_BOOST);
        map.put("rhcp_reduce_consumption", RHCP_REDUCE_CONSUMPTION);
        map.put("rhcp_dodge", RHCP_DODGE);
        map.put("rhcp_counterattack", RHCP_COUNTERATTACK);
        map.put("rhcp_blitz_attack", RHCP_BLITZ_ATTACK);
        map.put("rhcp_electric_dash", RHCP_ELECTRIC_DASH);
        map.put("rhcp_drag_into_line", RHCP_DRAG_INTO_LINE);
        return Map.copyOf(map);
    }
}
