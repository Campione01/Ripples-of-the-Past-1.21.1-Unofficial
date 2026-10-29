package rotp.addons.scr;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import java.util.function.Supplier;

import rotp.core.api.RotpAddonApi;
import rotp.core.core.JojoRegistries;
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

import rotp.addons.scr.ability.ChariotDarkAuraAbility;
import rotp.addons.scr.ability.ChariotVirusAbility;
import rotp.addons.scr.ability.ChariotVirusAbility.Mode;
import rotp.addons.scr.effect.ChariotVirusEffect;
import rotp.addons.scr.entity.SilverChariotRequiemEntity;
import rotp.addons.scr.stand.SilverChariotRequiemStandType;
import rotp.addons.scr.state.RequiemEvolutionState;
import rotp.addons.scr.state.RequiemSoulState;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(SilverChariotRequiemAddon.MOD_ID)
public final class SilverChariotRequiemAddon {
    public static final String MOD_ID =
            "rotp_silver_chariot_requiem";
    public static final String LEGACY_NAMESPACE =
            "rotp_nextalbum";
    public static final String ROW80_NAMESPACE = "rotp_cr";
    public static final String STAND_PATH =
            "silver_chariot_requiem";

    public static final String PUNCH =
            "chariot_requiem_punch";
    public static final String BARRAGE =
            "chariot_requiem_barrage";
    public static final String FINISHER_PUNCH =
            "chariot_requiem_finisher_punch";
    public static final String HEAVY_PUNCH =
            "chariot_requiem_heavy_punch";
    public static final String BLOCK =
            "chariot_requiem_block";
    public static final String AURA =
            "chariot_requiem_aura";
    public static final String VIRUS =
            "chariot_requiem_virus";
    public static final String STAND_VIRUS =
            "chariot_requiem_stand_virus";

    /*
     * Preserve row 80's raw legacy numeric values without inferring
     * canonical ranks from an incompatible numeric scale.
     */
    public static final double POWER = 4.0D;
    public static final double SPEED = 4.0D;
    public static final double EFFECTIVE_RANGE = 100.0D;
    public static final double MAX_RANGE = 100.0D;
    public static final double DURABILITY = 20.0D;
    public static final double PRECISION = 4.0D;
    public static final float STAND_WIDTH = 0.7F;
    public static final float STAND_HEIGHT = 2.1F;

    private static final int REQUIRED_CORE_ABI = 1;

    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(
                    JojoRegistries.ABILITY_TYPES_REG,
                    ROW80_NAMESPACE);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(
                    Registries.ENTITY_TYPE, LEGACY_NAMESPACE);
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(
                    Registries.MOB_EFFECT, ROW80_NAMESPACE);
    public static final DeferredRegister<ParticleType<?>>
            PARTICLE_TYPES = DeferredRegister.create(
                    Registries.PARTICLE_TYPE, ROW80_NAMESPACE);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(
                    Registries.SOUND_EVENT, ROW80_NAMESPACE);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(
                    JojoRegistries.DEFAULT_STANDS_REG,
                    LEGACY_NAMESPACE);
    public static final DeferredRegister<AttachmentType<?>>
            ATTACHMENT_TYPES = DeferredRegister.create(
                    NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<ChariotDarkAuraAbility>>
            CHARIOT_DARK_AURA = ABILITY_TYPES.register(
                    AURA,
                    key -> new AbilityType<>(
                            key, ChariotDarkAuraAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<ChariotVirusAbility>>
            CHARIOT_VIRUS = virus(VIRUS, Mode.CHARIOT);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<ChariotVirusAbility>>
            CHARIOT_STAND_VIRUS =
                    virus(STAND_VIRUS, Mode.STAND);

    public static final DeferredHolder<MobEffect, ChariotVirusEffect>
            CHARIOT_VIRUS_EFFECT = MOB_EFFECTS.register(
                    "chariot_virus_effect",
                    ChariotVirusEffect::new);

    public static final DeferredHolder<
            ParticleType<?>, SimpleParticleType>
            CHARIOT_VIRUS_PARTICLE = PARTICLE_TYPES.register(
                    "chariot_virus_particle",
                    () -> new SimpleParticleType(false));

    public static final DeferredHolder<SoundEvent, SoundEvent>
            SCR_SUMMON = sound("scr_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SCR_UNSUMMON = sound("scr_unsummon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SCR_PUNCH = sound("scr_punch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SCR_PUNCH_HEAVY = sound("scr_punch_heavy");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            CHARIOT_REQUIEM_AURA_SOUND = sound(AURA);
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SCR_ABILITY_1 = sound("scr_ability_1");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SCR_ABILITY_2 = sound("scr_ability_2");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SCR_YELLOW_VIRUS = sound("scr_yellow_virus");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            CHARIOT_REQUIEM_OST = sound("chariot_requiem_ost");
    public static final OstSoundList CHARIOT_REQUIEM_OST_LIST =
            new OstSoundList(
                    row80Id("chariot_requiem_ost"),
                    SOUND_EVENTS);

    public static final Supplier<
            AttachmentType<RequiemEvolutionState>>
            EVOLUTION_STATE = ATTACHMENT_TYPES.register(
                    "requiem_evolution_state",
                    () -> AttachmentType.serializable(
                                    holder -> holder
                                                    instanceof ServerPlayer
                                            ? new RequiemEvolutionState()
                                            : null)
                            .build());

    public static final Supplier<AttachmentType<RequiemSoulState>>
            SOUL_STATE = ATTACHMENT_TYPES.register(
                    "requiem_soul_state",
                    () -> AttachmentType.serializable(
                                    holder -> holder
                                                    instanceof LivingEntity
                                            && !(holder
                                                    instanceof StandEntity)
                                            ? new RequiemSoulState()
                                            : null)
                            .build());

    public static final DeferredHolder<
            EntityType<?>, EntityType<SilverChariotRequiemEntity>>
            SILVER_CHARIOT_REQUIEM_ENTITY =
            ENTITY_TYPES.register(
                    STAND_PATH,
                    key -> EntityType.Builder
                            .<SilverChariotRequiemEntity>of(
                                    SilverChariotRequiemEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(STAND_WIDTH, STAND_HEIGHT)
                            .clientTrackingRange(96)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<
            StandType, SilverChariotRequiemStandType>
            SILVER_CHARIOT_REQUIEM = STAND_TYPES.register(
                    STAND_PATH,
                    SilverChariotRequiemAddon::createStand);

    public SilverChariotRequiemAddon(
            IEventBus modEventBus, ModContainer modContainer) {
        requireCoreApi();
        ABILITY_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        MOB_EFFECTS.register(modEventBus);
        PARTICLE_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        ATTACHMENT_TYPES.register(modEventBus);
        modEventBus.addListener(
                SilverChariotRequiemEvents::registerAttributes);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                LEGACY_NAMESPACE, path);
    }

    public static ResourceLocation row80Id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                ROW80_NAMESPACE, path);
    }

    public static RequiemEvolutionState evolutionState(
            ServerPlayer player) {
        return player.getData(EVOLUTION_STATE);
    }

    public static RequiemSoulState soulState(
            LivingEntity entity) {
        return entity.getData(SOUL_STATE);
    }

    public static SilverChariotRequiemEntity activeStand(
            LivingEntity entity) {
        StandPower power = entity != null
                ? StandPower.get(entity)
                : null;
        return power != null
                && power.hasPower()
                && power.isSummoned()
                && power.getPowerType()
                        == SILVER_CHARIOT_REQUIEM.get()
                && power.getSummonedStandEntity()
                        instanceof SilverChariotRequiemEntity stand
                && stand.isAlive()
                && !stand.isRemoved()
                ? stand
                : null;
    }

    private static DeferredHolder<
            AbilityType<?>, AbilityType<ChariotVirusAbility>>
            virus(String path, Mode mode) {
        return ABILITY_TYPES.register(
                path,
                key -> new AbilityType<>(
                        key,
                        (type, id) -> new ChariotVirusAbility(
                                type, id, mode)));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(
            String path) {
        return SOUND_EVENTS.register(
                path, SoundEvent::createVariableRangeEvent);
    }

    private static SilverChariotRequiemStandType createStand(
            ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addAbility(
                        PUNCH,
                        ModStandAbilities.PUNCH,
                        punch -> punch.punchImpactSound(SCR_PUNCH))
                .addAbility(
                        BARRAGE,
                        ModStandAbilities.BARRAGE,
                        barrage -> barrage.barrageHitSound(SCR_PUNCH))
                .addAbility(
                        HEAVY_PUNCH,
                        ModStandAbilities.HEAVY_PUNCH,
                        punch -> punch.heavyPunchImpactSound(
                                SCR_PUNCH_HEAVY))
                .addAbility(
                        "heavy_charged",
                        ModStandAbilities.HEAVY_CHARGED)
                // 1.16 (071 InitStands :59-60): no Resolve gate, so the
                // finisher and its meter open at Resolve 0
                .addAbility(
                        FINISHER_PUNCH,
                        ModStandAbilities.HEAVY_PUNCH,
                        punch -> {
                            punch.heavyPunchImpactSound(
                                    SCR_PUNCH_HEAVY);
                            punch.initIsFinisher(HEAVY_PUNCH);
                        })
                .addAbility(BLOCK, ModStandAbilities.GUARD)
                .addAbility(AURA, CHARIOT_DARK_AURA)
                .addAbility(VIRUS, CHARIOT_VIRUS)
                .addAbility(STAND_VIRUS, CHARIOT_STAND_VIRUS)
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
                    .bind(BLOCK, InputMethod.HOLD, InputKey.RMB)
                    .makeHotbar(
                            0, USE_SPECIAL, SWITCH_SPECIAL)
                    .addToHotbar(
                            AURA, 0, InputMethod.HOLD)
                    .addToHotbar(
                            VIRUS, 0, InputMethod.HOLD)
                    .addHotbarSlotVariation(
                            STAND_VIRUS,
                            VIRUS,
                            InputKey.Modifier.SHIFT,
                            InputMethod.HOLD)
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
                    .bind(BLOCK, InputMethod.HOLD, InputKey.RMB)
                .finalizeControlScheme()
                .addSkill(StandUnlockableSkill.startingAbility(PUNCH))
                .addSkill(StandUnlockableSkill.startingAbility(BARRAGE))
                .addSkill(StandUnlockableSkill.startingAbility(
                        HEAVY_PUNCH))
                .addSkill(StandUnlockableSkill.startingAbility(
                                "heavy_charged")
                        .prerequisiteSkill(HEAVY_PUNCH))
                .addSkill(StandUnlockableSkill.startingAbility(
                        FINISHER_PUNCH))
                .addSkill(StandUnlockableSkill.startingAbility(BLOCK))
                .addSkill(StandUnlockableSkill.startingAbility(AURA))
                .addSkill(StandUnlockableSkill.startingAbility(VIRUS))
                .addSkill(StandUnlockableSkill.startingAbility(
                                STAND_VIRUS)
                        .prerequisiteSkill(VIRUS));

        SilverChariotRequiemStandType stand =
                new SilverChariotRequiemStandType(
                        new StandStats.Builder()
                                .power(POWER)
                                .speed(SPEED)
                                .range(
                                        EFFECTIVE_RANGE,
                                        MAX_RANGE)
                                .durability(DURABILITY)
                                .precision(PRECISION)
                                .randomWeight(0.0D)
                                .build(),
                        moveset,
                        SILVER_CHARIOT_REQUIEM_ENTITY.get(),
                        id);
        stand.standDimensions(STAND_WIDTH, STAND_HEIGHT)
                .summonSound(() -> SCR_SUMMON)
                .unsummonSound(() -> SCR_UNSUMMON)
                .ost(() -> CHARIOT_REQUIEM_OST_LIST);
        return stand;
    }

    private static void requireCoreApi() {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_POWER_TRANSITIONS_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_VISUAL_CONTEXT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ACTION_SUMMON_LOCK_HOOK_V1)
                // the slumber interrupts the Stand-power slot too
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_POWER_ACTION_SLOT_V1)) {
            throw new IllegalStateException(
                    "RotP Silver Chariot Requiem requires ROTP "
                            + "addon ABI 1 with namespaced ability "
                            + "resources, Stand transitions, "
                            + "Stand visual contexts, the summon lock "
                            + "hold count and the Stand-power action slot.");
        }
    }
}
