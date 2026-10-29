package rotp.addons.achtungbaby;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import java.util.function.Supplier;

import rotp.core.api.RotpAddonApi;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.type.StandType;

import rotp.addons.achtungbaby.ability.AchtungBabyAbilities;
import rotp.addons.achtungbaby.entity.AchtungBabyStandEntity;
import rotp.addons.achtungbaby.entity.InvisibilityRuneEntity;
import rotp.addons.achtungbaby.network.AchtungBabyStatePayload;
import rotp.addons.achtungbaby.stand.AchtungBabyStandType;
import rotp.addons.achtungbaby.state.AchtungBabyState;
import rotp.addons.achtungbaby.state.InvisibleBlockState;
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

@Mod(AchtungBabyAddon.MOD_ID)
public final class AchtungBabyAddon {
    public static final String MOD_ID = "rotp_ab";
    public static final String NETWORK_PROTOCOL_VERSION = "1";

    public static final String ITEM_INVISIBILITY_NAME =
            "item_invisibility";
    public static final String BLOOD_SPLASH_NAME = "blood_splash";
    public static final String ACTIVE_INVISIBILITY_NAME =
            "active_invisibility";
    public static final String GHOST_MODE_NAME = "ghost_mode";
    public static final String PERFECT_INVISIBILITY_NAME =
            "perfect_invisibility";
    public static final String GRANT_INVISIBILITY_NAME =
            "grant_invisibility";

    // Phenomenon Stand: the body has no independent control distance.
    public static final double EFFECTIVE_RANGE = 1.0D;
    public static final double MAX_RANGE = 1.0D;

    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(
                    NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);

    /**
     * Level-wide record of blocks placed from an invisible item. 1.16 kept the same set in a {@code WorldSavedData};
     * a level attachment persists with the level the same way.
     */
    public static final Supplier<AttachmentType<InvisibleBlockState>>
            INVISIBLE_BLOCKS = ATTACHMENTS.register(
                    "invisible_blocks",
                    () -> AttachmentType.serializable(
                                    InvisibleBlockState::new)
                            .build());

    public static final Supplier<AttachmentType<AchtungBabyState>> STATE =
            ATTACHMENTS.register(
                    "state",
                    () -> AttachmentType.serializable(
                                    holder -> holder instanceof LivingEntity
                                            ? new AchtungBabyState()
                                            : null)
                            .build());

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<AchtungBabyAbilities.ItemInvisibility>>
            ITEM_INVISIBILITY = registerAbility(
                    ITEM_INVISIBILITY_NAME,
                    AchtungBabyAbilities.ItemInvisibility::new);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<AchtungBabyAbilities.BloodSplash>>
            BLOOD_SPLASH = registerAbility(
                    BLOOD_SPLASH_NAME,
                    AchtungBabyAbilities.BloodSplash::new);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<AchtungBabyAbilities.ActiveInvisibility>>
            ACTIVE_INVISIBILITY = registerAbility(
                    ACTIVE_INVISIBILITY_NAME,
                    AchtungBabyAbilities.ActiveInvisibility::new);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<AchtungBabyAbilities.GhostMode>>
            GHOST_MODE = registerAbility(
                    GHOST_MODE_NAME,
                    AchtungBabyAbilities.GhostMode::new);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<AchtungBabyAbilities.PerfectInvisibility>>
            PERFECT_INVISIBILITY = registerAbility(
                    PERFECT_INVISIBILITY_NAME,
                    AchtungBabyAbilities.PerfectInvisibility::new);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<AchtungBabyAbilities.GrantInvisibility>>
            GRANT_INVISIBILITY = registerAbility(
                    GRANT_INVISIBILITY_NAME,
                    AchtungBabyAbilities.GrantInvisibility::new);

    public static final DeferredHolder<
            EntityType<?>, EntityType<AchtungBabyStandEntity>>
            ACHTUNG_BABY_ENTITY = ENTITY_TYPES.register(
                    "achtung_baby",
                    key -> EntityType.Builder.<AchtungBabyStandEntity>of(
                                    AchtungBabyStandEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.1F, 0.1F)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));
    public static final DeferredHolder<
            EntityType<?>, EntityType<InvisibilityRuneEntity>>
            INVISIBILITY_RUNE = ENTITY_TYPES.register(
                    "invisibility_rune",
                    key -> EntityType.Builder.<InvisibilityRuneEntity>of(
                                    InvisibilityRuneEntity::new,
                                    MobCategory.MISC)
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(32)
                            .updateInterval(1)
                            .build(key.toString()));

    public static final DeferredHolder<SoundEvent, SoundEvent> VOID =
            SOUND_EVENTS.register("void", SoundEvent::createVariableRangeEvent);
    // Same void.ogg under its own id: the core client treats any sound equal to the
    // Stand's unsummon sound as an unsummon sound and cuts it while the Stand is out.
    public static final DeferredHolder<SoundEvent, SoundEvent> VOID_SUMMON =
            SOUND_EVENTS.register("void_summon", SoundEvent::createVariableRangeEvent);

    public static final DeferredHolder<StandType, AchtungBabyStandType>
            ACHTUNG_BABY = STAND_TYPES.register(
                    "achtung_baby", AchtungBabyAddon::createStand);

    public AchtungBabyAddon(IEventBus modEventBus) {
        if (!RotpAddonApi.supportsAbi(1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)) {
            throw new IllegalStateException(
                    "RotP Achtung Baby requires ROTP addon ABI 1.");
        }
        ABILITY_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        ATTACHMENTS.register(modEventBus);
        modEventBus.addListener(AchtungBabyGameEvents::registerAttributes);
        modEventBus.addListener(AchtungBabyAddon::registerPayloads);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static AchtungBabyState state(LivingEntity entity) {
        return entity.getData(STATE);
    }

    public static boolean isUser(LivingEntity entity) {
        StandPower power = StandPower.get(entity);
        return power != null && power.getPowerType() == ACHTUNG_BABY.get();
    }

    private static <T extends rotp.core.powersystem.ability.Ability>
            DeferredHolder<AbilityType<?>, AbilityType<T>> registerAbility(
                    String name,
                    java.util.function.BiFunction<
                            AbilityType<T>,
                            rotp.core.powersystem.ability.AbilityId,
                            T> factory) {
        return ABILITY_TYPES.register(
                name, key -> new AbilityType<>(key, factory));
    }

    private static void registerPayloads(
            RegisterPayloadHandlersEvent event) {
        event.registrar(NETWORK_PROTOCOL_VERSION).playToClient(
                AchtungBabyStatePayload.TYPE,
                AchtungBabyStatePayload.STREAM_CODEC,
                AchtungBabyStatePayload::handle);
    }

    private static AchtungBabyStandType createStand(ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addAbility(ITEM_INVISIBILITY_NAME, ITEM_INVISIBILITY)
                .addAbility(BLOOD_SPLASH_NAME, BLOOD_SPLASH)
                .addAbility(ACTIVE_INVISIBILITY_NAME, ACTIVE_INVISIBILITY)
                .addAbility(GHOST_MODE_NAME, GHOST_MODE)
                .addAbility(PERFECT_INVISIBILITY_NAME, PERFECT_INVISIBILITY)
                .addAbility(GRANT_INVISIBILITY_NAME, GRANT_INVISIBILITY);

        moveset.makeControlScheme("hotbar")
                .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                .addToHotbar(ITEM_INVISIBILITY_NAME, 0, InputMethod.CLICK)
                .addToHotbar(BLOOD_SPLASH_NAME, 0, InputMethod.CLICK)
                .addToHotbar(GHOST_MODE_NAME, 0, InputMethod.HOLD)
                .addToHotbar(PERFECT_INVISIBILITY_NAME, 0, InputMethod.CLICK)
                .addToHotbar(GRANT_INVISIBILITY_NAME, 0, InputMethod.CLICK)
                .addToHotbar(ACTIVE_INVISIBILITY_NAME, 0, InputMethod.CLICK)
                .finalizeControlScheme();
        moveset.makeControlScheme("keybinds").finalizeControlScheme();

        for (String ability : new String[] {
                ITEM_INVISIBILITY_NAME,
                BLOOD_SPLASH_NAME,
                ACTIVE_INVISIBILITY_NAME,
                GHOST_MODE_NAME,
                PERFECT_INVISIBILITY_NAME,
                GRANT_INVISIBILITY_NAME
        }) {
            moveset.addSkill(StandUnlockableSkill.startingAbility(ability));
        }

        AchtungBabyStandType stand = new AchtungBabyStandType(
                new StandStats.Builder()
                        .power(2.0D)
                        .speed(2.0D)
                        .range(EFFECTIVE_RANGE, MAX_RANGE)
                        .durability(14.0D)
                        .precision(2.0D)
                        .randomWeight(3.0D)
                        .build(),
                moveset,
                ACHTUNG_BABY_ENTITY.get(),
                id);
        stand.standControlType(StandControlType.PHENOMENON);
        stand.manualControl(false);
        stand.distanceStrengthDecay(false);
        // 1.16 InitStands: summonSound(VOID).unsummonSound(VOID), heard by the user and other Stand users.
        // Summon uses VOID_SUMMON (same audio) so the core does not take it for the unsummon sound.
        stand.summonSound(() -> VOID_SUMMON);
        stand.unsummonSound(() -> VOID);
        return stand.standDimensions(0.1F, 0.1F);
    }
}
