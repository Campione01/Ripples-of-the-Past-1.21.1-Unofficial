package rotp.addons.metallica;

import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import java.util.function.Supplier;

import rotp.core.api.RotpAddonApi;
import rotp.core.core.JojoRegistries;
import rotp.core.item.KnifeItem;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.util.sound.OstSoundList;

import rotp.addons.metallica.ability.MetallicaHealAbility;
import rotp.addons.metallica.ability.MetallicaInvisibilityAbility;
import rotp.addons.metallica.ability.MetallicaKnifeThrowAbility;
import rotp.addons.metallica.ability.MetallicaMultipleKnifeThrowAbility;
import rotp.addons.metallica.ability.MetallicaVictimBladesAbility;
import rotp.addons.metallica.effect.LackOfIronEffect;
import rotp.addons.metallica.entity.MetallicaStandEntity;
import rotp.addons.metallica.stand.MetallicaStandType;
import rotp.addons.metallica.state.MetallicaIronState;
import rotp.addons.metallica.state.MetallicaInvisibilityState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(MetallicaAddon.MOD_ID)
public final class MetallicaAddon {
    public static final String MOD_ID = "rotp_metallica";
    private static final int REQUIRED_CORE_ABI = 1;

    public static final String SINGLE_SCALPEL_NAME =
            "metallica_single_knife_throw";
    public static final String MULTIPLE_SCALPEL_NAME =
            "metallica_multiple_knives_throw";
    public static final String VICTIM_BLADES_NAME =
            "create_blades_in_victim";
    public static final String INVISIBILITY_NAME =
            "invisibility";
    public static final String HEAL_NAME = "heal";

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
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<SoundEvent>
            SOUND_EVENTS = DeferredRegister.create(
                    Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<StandType>
            STAND_TYPES = DeferredRegister.create(
                    JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);

    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<MetallicaKnifeThrowAbility>>
            SINGLE_SCALPEL = ABILITY_TYPES.register(
                    SINGLE_SCALPEL_NAME,
                    key -> new AbilityType<>(
                            key,
                            MetallicaKnifeThrowAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<MetallicaMultipleKnifeThrowAbility>>
            MULTIPLE_SCALPEL = ABILITY_TYPES.register(
                    MULTIPLE_SCALPEL_NAME,
                    key -> new AbilityType<>(
                            key,
                            MetallicaMultipleKnifeThrowAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<MetallicaVictimBladesAbility>>
            VICTIM_BLADES = ABILITY_TYPES.register(
                    VICTIM_BLADES_NAME,
                    key -> new AbilityType<>(
                            key,
                            MetallicaVictimBladesAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<MetallicaInvisibilityAbility>>
            INVISIBILITY = ABILITY_TYPES.register(
                    INVISIBILITY_NAME,
                    key -> new AbilityType<>(
                            key,
                            MetallicaInvisibilityAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<MetallicaHealAbility>>
            HEAL_ABILITY = ABILITY_TYPES.register(
                    HEAL_NAME,
                    key -> new AbilityType<>(
                            key,
                            MetallicaHealAbility::new));

    public static final Supplier<
            AttachmentType<MetallicaInvisibilityState>>
            INVISIBILITY_STATE = ATTACHMENT_TYPES.register(
                    "invisibility_state",
                    () -> AttachmentType.builder(
                                    MetallicaInvisibilityState::new)
                            .sync(MetallicaInvisibilityState.STREAM_CODEC)
                            .build());
    public static final Supplier<AttachmentType<MetallicaIronState>>
            IRON_STATE = ATTACHMENT_TYPES.register(
                    "iron_state",
                    () -> AttachmentType.serializable(
                                    holder ->
                                            holder
                                                    instanceof LivingEntity
                                            ? new MetallicaIronState()
                                            : null)
                            .sync(MetallicaIronState.STREAM_CODEC)
                            .build());

    public static final DeferredHolder<
            EntityType<?>, EntityType<MetallicaStandEntity>>
            METALLICA_ENTITY = ENTITY_TYPES.register(
                    "metallica",
                    key -> EntityType.Builder
                            .<MetallicaStandEntity>of(
                                    MetallicaStandEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.0F, 0.0F)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<
            MobEffect, LackOfIronEffect> LACK_OF_IRON =
            MOB_EFFECTS.register(
                    "lack_of_iron",
                    LackOfIronEffect::new);

    public static final DeferredItem<KnifeItem> SCALPEL =
            ITEMS.registerItem(
                    "scalpel",
                    KnifeItem::new,
                    new Item.Properties().stacksTo(16));

    public static final TagKey<Item> IRON_ITEMS =
            TagKey.create(
                    Registries.ITEM, id("iron_items"));

    public static final DeferredHolder<SoundEvent, SoundEvent>
            USER_METALLICA = sound("risotto_nero_metallica");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            USER_KNIFE_THROW =
                    sound("risotto_nero_knife_throw");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            USER_KNIVES_THROW =
                    sound("risotto_nero_knives_throw");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            USER_INVISIBILITY =
                    sound("risotto_nero_invisibility");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            INVISIBILITY_ON =
                    sound("metallica_invisibility_on");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            INVISIBILITY_OFF =
                    sound("metallica_invisibility_off");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            USER_CREATE_BLADES =
                    sound("risotto_nero_create_blades_in_victim");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            STAND_SUMMON =
                    sound("metallica_summon_stand");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            STAND_UNSUMMON =
                    sound("metallica_unsummon_stand");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            HEAL = sound("heal");
    public static final OstSoundList METALLICA_OST =
            new OstSoundList(id("metallica_ost"), SOUND_EVENTS);

    public static final DeferredHolder<
            StandType, MetallicaStandType> METALLICA =
            STAND_TYPES.register(
                    "metallica", MetallicaAddon::createStand);

    public MetallicaAddon(IEventBus modEventBus) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_VISUAL_CONTEXT_V1)
                // canFireReleasedHold (R146 core held-action rechecks)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ENTITY_ACTION_HOOKS_V1)) {
            throw new IllegalStateException(
                    "RotP Metallica requires addon ABI 1 with namespaced "
                            + "ability resources, Stand visual contexts "
                            + "and entity action hooks.");
        }

        ABILITY_TYPES.register(modEventBus);
        ATTACHMENT_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        MOB_EFFECTS.register(modEventBus);
        ITEMS.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        modEventBus.addListener(
                MetallicaGameEvents::registerAttributes);
        modEventBus.addListener(
                MetallicaAddon::addCreativeTabItems);
    }

    /** 1.16 listed the scalpel in the ROTP main tab. */
    private static void addCreativeTabItems(
            BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == rotp.core.init.ModItems.MAIN_TAB.getKey()) {
            event.accept(SCALPEL.get());
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                MOD_ID, path);
    }

    public static MetallicaInvisibilityState invisibility(
            LivingEntity entity) {
        return entity.getData(INVISIBILITY_STATE);
    }

    public static MetallicaIronState ironState(
            LivingEntity entity) {
        return entity.getData(IRON_STATE);
    }

    public static boolean isMetallicaUser(
            LivingEntity entity) {
        StandPower power = StandPower.get(entity);
        return power != null
                && power.getPowerType() == METALLICA.get();
    }

    public static MetallicaStandEntity controller(
            LivingEntity entity) {
        StandPower power = StandPower.get(entity);
        return power != null
                && power.getPowerType() == METALLICA.get()
                && power.getSummonedStandEntity()
                        instanceof MetallicaStandEntity controller
                ? controller
                : null;
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(
            String path) {
        return SOUND_EVENTS.register(
                path, SoundEvent::createVariableRangeEvent);
    }

    private static MetallicaStandType createStand(
            ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addAbility(
                        SINGLE_SCALPEL_NAME,
                        SINGLE_SCALPEL)
                .addAbility(
                        MULTIPLE_SCALPEL_NAME,
                        MULTIPLE_SCALPEL,
                        ability -> ability.resolveLevelToUnlock(1))
                .addAbility(
                        VICTIM_BLADES_NAME,
                        VICTIM_BLADES,
                        ability -> ability.resolveLevelToUnlock(2))
                .addAbility(
                        INVISIBILITY_NAME,
                        INVISIBILITY,
                        ability -> ability.resolveLevelToUnlock(2))
                .addAbility(
                        HEAL_NAME,
                        HEAL_ABILITY,
                        ability -> ability.resolveLevelToUnlock(4))
                .makeControlScheme("hotbar")
                    .makeHotbar(
                            0, USE_SPECIAL, SWITCH_SPECIAL)
                    .addToHotbar(
                            SINGLE_SCALPEL_NAME,
                            0,
                            InputMethod.CLICK)
                    .addHotbarSlotVariation(
                            MULTIPLE_SCALPEL_NAME,
                            SINGLE_SCALPEL_NAME,
                            InputKey.Modifier.SHIFT,
                            InputMethod.CLICK)
                    .addToHotbar(
                            VICTIM_BLADES_NAME,
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            INVISIBILITY_NAME,
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            HEAL_NAME,
                            0,
                            InputMethod.CLICK)
                .finalizeControlScheme()
                .makeControlScheme("keybinds")
                .finalizeControlScheme()
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                SINGLE_SCALPEL_NAME))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                MULTIPLE_SCALPEL_NAME))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                VICTIM_BLADES_NAME))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                INVISIBILITY_NAME))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                HEAL_NAME));

        return new MetallicaStandType(
                new StandStats.Builder()
                        .power(MetallicaPhase1Spec.POWER)
                        .speed(MetallicaPhase1Spec.SPEED)
                        .range(
                                MetallicaPhase1Spec
                                        .EFFECTIVE_RANGE,
                                MetallicaPhase1Spec.MAX_RANGE)
                        .durability(
                                MetallicaPhase1Spec.DURABILITY)
                        .precision(
                                MetallicaPhase1Spec.PRECISION)
                        .randomWeight(
                                MetallicaPhase1Spec.RANDOM_WEIGHT)
                        .build(),
                moveset,
                METALLICA_ENTITY.get(),
                id)
                .standDimensions(0.0F, 0.0F)
                .summonShout(() -> USER_METALLICA)
                .summonSound(() -> STAND_SUMMON)
                .unsummonSound(() -> STAND_UNSUMMON)
                .ost(() -> METALLICA_OST);
    }
}
