package rotp.addons.speedking;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import rotp.core.api.RotpAddonApi;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityBlockAbility;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.util.sound.OstSoundList;

import rotp.addons.speedking.ability.SpeedKingAbility;
import rotp.addons.speedking.ability.SpeedKingAbility.Mode;
import rotp.addons.speedking.ability.SpeedKingBarrageAbility;
import rotp.addons.speedking.ability.SpeedKingHeavyAbility;
import rotp.addons.speedking.ability.SpeedKingPunchAbility;
import rotp.addons.speedking.entity.SpeedKingEntity;
import rotp.addons.speedking.entity.SpeedKingPickaxeEntity;
import rotp.addons.speedking.network.SpeedKingStatePayload;
import rotp.addons.speedking.stand.SpeedKingStandType;
import rotp.addons.speedking.state.HeatVisionTargetState;
import rotp.addons.speedking.state.SpeedKingState;
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

@Mod(SpeedKingAddon.MOD_ID)
public final class SpeedKingAddon {
    public static final String MOD_ID = "speedking";
    public static final String NETWORK_PROTOCOL_VERSION = "1";
    public static final double EFFECTIVE_RANGE = 4.0D;
    public static final double MAX_RANGE = 8.0D;

    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(
                    JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(
                    JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(
                    NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);

    public static final Supplier<AttachmentType<SpeedKingState>> STATE =
            ATTACHMENTS.register(
                    "state",
                    () -> AttachmentType.serializable(
                                    holder -> holder instanceof LivingEntity
                                            ? new SpeedKingState() : null)
                            .build());
    public static final Supplier<AttachmentType<HeatVisionTargetState>>
            HEAT_VISION_TARGET_STATE = ATTACHMENTS.register(
                    "heat_vision_target_state",
                    () -> AttachmentType.serializable(
                                    holder -> holder instanceof LivingEntity
                                            ? new HeatVisionTargetState() : null)
                            .build());

    // Donor 099 InitStands :61-64: the plain core StandEntityLightAttack,
    // StandEntityMeleeBarrage and StandEntityHeavyAttack (the finisher being
    // the heavy punch's finisher variation)
    public static final DeferredHolder<AbilityType<?>, AbilityType<SpeedKingPunchAbility>>
            PUNCH = ABILITY_TYPES.register(
                    "example_stand_punch",
                    key -> new AbilityType<>(key, SpeedKingPunchAbility::new));
    public static final DeferredHolder<AbilityType<?>, AbilityType<SpeedKingBarrageAbility>>
            BARRAGE = ABILITY_TYPES.register(
                    "example_stand_barrage",
                    key -> new AbilityType<>(key, SpeedKingBarrageAbility::new));
    public static final DeferredHolder<AbilityType<?>, AbilityType<SpeedKingHeavyAbility>>
            FINISHER = ABILITY_TYPES.register(
                    "example_stand_finisher_punch",
                    key -> new AbilityType<>(key, SpeedKingHeavyAbility::finisher));
    public static final DeferredHolder<AbilityType<?>, AbilityType<SpeedKingHeavyAbility>>
            HEAVY = ABILITY_TYPES.register(
                    "example_stand_heavy_punch",
                    key -> new AbilityType<>(key, SpeedKingHeavyAbility::heavy));
    // Donor 099 InitStands: example_stand_block was a plain StandEntityBlock,
    // so the core guard rules (front angle, stamina, knockback, guard break,
    // auto-guard, the RMB guard window) apply to it, and pressed with the
    // Stand away it brings out only the arms (StandEntityBlock :17 ARMS).
    public static final DeferredHolder<AbilityType<?>, AbilityType<StandEntityBlockAbility>>
            BLOCK = ABILITY_TYPES.register(
                    "example_stand_block",
                    key -> new AbilityType<>(key, StandEntityBlockAbility::new));
    public static final DeferredHolder<AbilityType<?>, AbilityType<SpeedKingAbility>>
            THROW_PICKAXE = ability(
                    "example_stand_throw_pickaxe", Mode.THROW_PICKAXE);
    public static final DeferredHolder<AbilityType<?>, AbilityType<SpeedKingAbility>>
            HEAT_INJECTION = ability(
                    "speed_king_heat_injection", Mode.HEAT_INJECTION);
    public static final DeferredHolder<AbilityType<?>, AbilityType<SpeedKingAbility>>
            HEAT_VISION = ability(
                    "speed_king_heat_vision", Mode.HEAT_VISION);
    public static final DeferredHolder<AbilityType<?>, AbilityType<SpeedKingAbility>>
            HEAT_COOK = ability("speed_king_heat_cook", Mode.HEAT_COOK);
    public static final DeferredHolder<AbilityType<?>, AbilityType<SpeedKingAbility>>
            HEAT_GRAB = ability("speed_king_heat_grab", Mode.HEAT_GRAB);
    public static final DeferredHolder<AbilityType<?>, AbilityType<SpeedKingAbility>>
            HEAT_TRAP_TRIGGER = ability(
                    "speed_king_heat_trap_trigger",
                    Mode.HEAT_TRAP_TRIGGER);
    public static final DeferredHolder<AbilityType<?>, AbilityType<SpeedKingAbility>>
            HEAT_TRAP = ability("speed_king_heat_trap", Mode.HEAT_TRAP);

    public static final DeferredHolder<EntityType<?>, EntityType<SpeedKingEntity>>
            SPEED_KING_ENTITY = ENTITY_TYPES.register(
                    "speedking_stand",
                    key -> EntityType.Builder.<SpeedKingEntity>of(
                                    SpeedKingEntity::new, MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.7F, 2.1F)
                            .clientTrackingRange(16)
                            .updateInterval(2)
                            .build(key.toString()));
    public static final DeferredHolder<
            EntityType<?>, EntityType<SpeedKingPickaxeEntity>>
            PICKAXE_ENTITY = ENTITY_TYPES.register(
                    "example_pickaxe",
                    key -> EntityType.Builder.<SpeedKingPickaxeEntity>of(
                                    SpeedKingPickaxeEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(1.0F, 1.0F)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<SoundEvent, SoundEvent>
            SUMMON_VOICELINE = sound("example_stand_summon_voiceline");
    public static final OstSoundList OST =
            new OstSoundList(id("example_stand_ost"), SOUND_EVENTS);

    public static final DeferredHolder<StandType, SpeedKingStandType>
            SPEED_KING = STAND_TYPES.register(
                    "speedking_stand", SpeedKingAddon::createStand);

    private static final Map<String, DeferredHolder<
            AbilityType<?>, ? extends AbilityType<?>>> ABILITIES =
            abilities();

    public SpeedKingAddon(IEventBus modEventBus) {
        if (!RotpAddonApi.supportsAbi(1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)
                // heavy-hit interrupt hooks (R146 core)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ENTITY_ACTION_HOOKS_V1)) {
            throw new IllegalStateException(
                    "Speed King requires ROTP addon ABI 1 with entity "
                            + "action hooks (a core from the same "
                            + "release).");
        }
        ABILITY_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        ATTACHMENTS.register(modEventBus);
        modEventBus.addListener(SpeedKingEvents::registerAttributes);
        modEventBus.addListener(SpeedKingAddon::registerPayloads);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static SpeedKingState state(LivingEntity entity) {
        return entity.getData(STATE);
    }

    public static HeatVisionTargetState heatVisionTargetState(
            LivingEntity entity) {
        return entity.getData(HEAT_VISION_TARGET_STATE);
    }

    public static boolean isUser(LivingEntity entity) {
        var power = rotp.core.powersystem.standpower
                .StandPower.get(entity);
        return power != null && power.getPowerType() == SPEED_KING.get();
    }

    private static DeferredHolder<AbilityType<?>, AbilityType<SpeedKingAbility>>
            ability(String name, Mode mode) {
        return ABILITY_TYPES.register(
                name,
                key -> new AbilityType<>(
                        key,
                        (type, id) -> new SpeedKingAbility(type, id, mode)));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUND_EVENTS.register(
                name,
                () -> SoundEvent.createVariableRangeEvent(id(name)));
    }

    private static void registerPayloads(
            RegisterPayloadHandlersEvent event) {
        event.registrar(NETWORK_PROTOCOL_VERSION).playToClient(
                SpeedKingStatePayload.TYPE,
                SpeedKingStatePayload.STREAM_CODEC,
                SpeedKingStatePayload::handle);
    }

    private static SpeedKingStandType createStand(ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addHumanoidStandStuff();
        ABILITIES.forEach((name, type) ->
                moveset.addAbility(name, (AbilityType<?>) type.get()));

        moveset.makeControlScheme("hotbar")
                .bind("example_stand_punch", InputMethod.CLICK, InputKey.LMB)
                .bind("example_stand_barrage", InputMethod.HOLD, InputKey.LMB)
                .bind("example_stand_heavy_punch", InputMethod.CLICK, InputKey.RMB)
                .bind("example_stand_block", InputMethod.HOLD, InputKey.RMB)
                .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                .addToHotbar("speed_king_heat_grab", 0, InputMethod.CLICK)
                .addToHotbar("speed_king_heat_injection", 0, InputMethod.CLICK)
                .addToHotbar("speed_king_heat_vision", 0, InputMethod.CLICK)
                .addToHotbar("speed_king_heat_cook", 0, InputMethod.HOLD)
                .addToHotbar("speed_king_heat_trap", 0, InputMethod.HOLD)
                .addToHotbar("speed_king_heat_trap_trigger", 0, InputMethod.CLICK)
                .addToHotbar("example_stand_throw_pickaxe", 0, InputMethod.HOLD)
                .addToHotbar("example_stand_finisher_punch", 0, InputMethod.CLICK)
                .finalizeControlScheme();

        moveset.makeControlScheme("keybinds")
                .bind("example_stand_punch", InputMethod.CLICK, InputKey.LMB)
                .bind("example_stand_barrage", InputMethod.HOLD, InputKey.LMB)
                .bind("example_stand_heavy_punch", InputMethod.CLICK, InputKey.RMB)
                .bind("example_stand_block", InputMethod.HOLD, InputKey.RMB)
                .finalizeControlScheme();

        for (String name : ABILITIES.keySet()) {
            if (name.equals("example_stand_finisher_punch")) {
                continue;
            }
            if (name.equals("example_stand_heavy_punch")) {
                moveset.addSkill(
                        StandUnlockableSkill.startingAbility(name)
                                .withAbility(
                                        "example_stand_finisher_punch"));
            }
            else {
                moveset.addSkill(
                        StandUnlockableSkill.startingAbility(name));
            }
        }

        SpeedKingStandType stand = new SpeedKingStandType(
                new StandStats.Builder()
                        .power(9.0D)
                        .speed(10.0D)
                        .range(EFFECTIVE_RANGE, MAX_RANGE)
                        .durability(9.0D)
                        .precision(12.0D)
                        .randomWeight(2.0D)
                        .build(),
                moveset,
                SPEED_KING_ENTITY.get(),
                id);
        stand.standDimensions(0.7F, 2.1F);
        stand.summonShout(() -> SUMMON_VOICELINE);
        stand.summonSound(() -> ModSoundEvents.STAND_SUMMON);
        stand.unsummonSound(() -> ModSoundEvents.STAND_UNSUMMON);
        stand.ost(() -> OST);
        return stand;
    }

    private static Map<String, DeferredHolder<
            AbilityType<?>, ? extends AbilityType<?>>> abilities() {
        Map<String, DeferredHolder<
                AbilityType<?>, ? extends AbilityType<?>>> map =
                new LinkedHashMap<>();
        map.put("example_stand_punch", PUNCH);
        map.put("example_stand_barrage", BARRAGE);
        map.put("example_stand_finisher_punch", FINISHER);
        map.put("example_stand_heavy_punch", HEAVY);
        map.put("example_stand_block", BLOCK);
        map.put("example_stand_throw_pickaxe", THROW_PICKAXE);
        map.put("speed_king_heat_injection", HEAT_INJECTION);
        map.put("speed_king_heat_vision", HEAT_VISION);
        map.put("speed_king_heat_cook", HEAT_COOK);
        map.put("speed_king_heat_grab", HEAT_GRAB);
        map.put("speed_king_heat_trap_trigger", HEAT_TRAP_TRIGGER);
        map.put("speed_king_heat_trap", HEAT_TRAP);
        return java.util.Collections.unmodifiableMap(map);
    }
}
