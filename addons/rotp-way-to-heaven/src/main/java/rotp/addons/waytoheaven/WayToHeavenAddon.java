package rotp.addons.waytoheaven;

import java.util.function.Supplier;

import rotp.core.api.RotpAddonApi;
import rotp.core.api.stand.StandArrowPoolOverrides;
import rotp.core.api.stand.StandMovesetExtensions;
import rotp.core.api.stand.StandMovesetExtensions.StandSkill;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModItems;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.type.StandType;
import com.mojang.serialization.Codec;

import rotp.addons.waytoheaven.ability.CMoonHeavenRiteAbility;
import rotp.addons.waytoheaven.ability.WhitesnakeHeavenRiteAbility;
import rotp.addons.waytoheaven.item.SinnerBoneItem;
import rotp.addons.waytoheaven.state.HeavenEvolutionState;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(WayToHeavenAddon.MOD_ID)
public final class WayToHeavenAddon {
    public static final String MOD_ID = "rotp_waytoheaven";
    public static final int REQUIRED_CORE_ABI = 1;

    public static final String WHITESNAKE_RITE_NAME =
            "whitesnake_frases";
    public static final String CMOON_RITE_NAME = "cmoon_heaven";
    public static final int MOVESET_EXTENSION_ORDER = 100;

    public static final ResourceLocation WHITESNAKE_ID =
            ResourceLocation.fromNamespaceAndPath(
                    "rotp_whitesnake", "whitesnake");
    public static final ResourceLocation CMOON_ID =
            ResourceLocation.fromNamespaceAndPath(
                    "rotp_cm", "cmoon");
    public static final ResourceLocation MADE_IN_HEAVEN_ID =
            ResourceLocation.fromNamespaceAndPath(
                    "rotp_mih", "made_in_heaven");
    public static final ResourceLocation ARROW_POOL_OVERRIDE_OWNER_ID =
            id("stand_arrow_pool");
    public static final ResourceLocation WHITESNAKE_EXTENSION_ID =
            id("whitesnake_heaven_rite");
    public static final ResourceLocation CMOON_EXTENSION_ID =
            id("cmoon_heaven_rite");

    public static final TagKey<EntityType<?>> CRIMINALS =
            TagKey.create(
                    Registries.ENTITY_TYPE,
                    id("criminals"));

    public static final DeferredRegister<AbilityType<?>>
            ABILITY_TYPES = DeferredRegister.create(
                    JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<AttachmentType<?>>
            ATTACHMENT_TYPES = DeferredRegister.create(
                    NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);
    public static final DeferredRegister.DataComponents
            DATA_COMPONENT_TYPES =
                    DeferredRegister.createDataComponents(
                            Registries.DATA_COMPONENT_TYPE,
                            MOD_ID);
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<SoundEvent>
            SOUND_EVENTS = DeferredRegister.create(
                    Registries.SOUND_EVENT, MOD_ID);

    public static final Supplier<DataComponentType<Integer>>
            SINNERS = DATA_COMPONENT_TYPES.registerComponentType(
                    "sinners",
                    builder -> builder
                            .persistent(Codec.intRange(
                                    0,
                                    SinnerBoneItem.MAX_SINNERS))
                            .networkSynchronized(
                                    ByteBufCodecs.VAR_INT));

    public static final DeferredItem<SinnerBoneItem> BONE =
            ITEMS.registerItem(
                    "bone",
                    SinnerBoneItem::new,
                    new Item.Properties());

    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<WhitesnakeHeavenRiteAbility>>
            WHITESNAKE_RITE = ABILITY_TYPES.register(
                    WHITESNAKE_RITE_NAME,
                    key -> new AbilityType<>(
                            key,
                            WhitesnakeHeavenRiteAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<CMoonHeavenRiteAbility>>
            CMOON_RITE = ABILITY_TYPES.register(
                    CMOON_RITE_NAME,
                    key -> new AbilityType<>(
                            key,
                            CMoonHeavenRiteAbility::new));

    public static final DeferredHolder<SoundEvent, SoundEvent>
            PUCCI_FRASES = SOUND_EVENTS.register(
                    "pucci_frases",
                    SoundEvent::createVariableRangeEvent);
    public static final Supplier<AttachmentType<HeavenEvolutionState>>
            EVOLUTION_STATE = ATTACHMENT_TYPES.register(
                    "evolution_state",
                    () -> AttachmentType.builder(
                                    HeavenEvolutionState::new)
                            .build());

    public WayToHeavenAddon(IEventBus modEventBus) {
        requireCoreApi();
        ABILITY_TYPES.register(modEventBus);
        ATTACHMENT_TYPES.register(modEventBus);
        DATA_COMPONENT_TYPES.register(modEventBus);
        ITEMS.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        modEventBus.addListener(
                WayToHeavenAddon::commonSetup);
        modEventBus.addListener(
                WayToHeavenAddon::addCreativeTabContents);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                MOD_ID, path);
    }

    private static void commonSetup(
            FMLCommonSetupEvent event) {
        event.enqueueWork(
                () -> {
                    registerMovesetExtensions();
                    registerArrowPoolExclusions();
                    verifyEvolutionChainDependencies();
                });
    }

    static void registerMovesetExtensions() {
        StandMovesetExtensions.register(
                StandMovesetExtensions.builder(
                                WHITESNAKE_ID,
                                WHITESNAKE_EXTENSION_ID,
                                MOVESET_EXTENSION_ORDER)
                        .addAbility(
                                WHITESNAKE_RITE_NAME,
                                id(WHITESNAKE_RITE_NAME),
                                WHITESNAKE_RITE)
                        .addSkill(StandSkill.startingAbility(
                                WHITESNAKE_RITE_NAME))
                        .appendToHotbar(
                                "hotbar",
                                0,
                                WHITESNAKE_RITE_NAME,
                                InputMethod.HOLD)
                        .build());
        StandMovesetExtensions.register(
                StandMovesetExtensions.builder(
                                CMOON_ID,
                                CMOON_EXTENSION_ID,
                                MOVESET_EXTENSION_ORDER)
                        .addAbility(
                                CMOON_RITE_NAME,
                                id(CMOON_RITE_NAME),
                                CMOON_RITE)
                        .addSkill(StandSkill.startingAbility(
                                CMOON_RITE_NAME))
                        .appendToHotbar(
                                "hotbar",
                                0,
                                CMOON_RITE_NAME,
                                InputMethod.HOLD)
                        .build());
    }

    static void registerArrowPoolExclusions() {
        StandArrowPoolOverrides.exclude(
                ARROW_POOL_OVERRIDE_OWNER_ID, CMOON_ID);
        StandArrowPoolOverrides.exclude(
                ARROW_POOL_OVERRIDE_OWNER_ID, MADE_IN_HEAVEN_ID);
    }

    static void verifyEvolutionChainDependencies() {
        requireRegisteredStand(WHITESNAKE_ID);
        requireRegisteredStand(CMOON_ID);
        requireRegisteredStand(MADE_IN_HEAVEN_ID);
    }

    private static StandType requireRegisteredStand(
            ResourceLocation standId) {
        StandType stand =
                JojoRegistries.DEFAULT_STANDS_REG.get(standId);
        if (stand == null) {
            throw new IllegalStateException(
                    "Missing required registered Stand "
                            + standId);
        }
        return stand;
    }

    private static void addCreativeTabContents(
            BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == ModItems.MAIN_TAB.getKey()) {
            event.accept(BONE.get());
        }
    }

    private static void requireCoreApi() {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_VISUAL_CONTEXT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_POWER_TRANSITIONS_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_DIRECTIONAL_GRAVITY_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_TIME_STOP_LIFECYCLE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ADDON_POST_EFFECT_LIFECYCLE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_MOVESET_EXTENSIONS_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_ARROW_POOL_OVERRIDES_V1)) {
            throw new IllegalStateException(
                    "RotP Way to Heaven requires addon ABI 1 "
                            + "with namespaced ability resources, "
                            + "Stand visual contexts, Stand power "
                            + "transitions, directional gravity, "
                            + "time-stop lifecycle events, and addon "
                            + "post-effect lifecycle ownership, and "
                            + "ordered Stand moveset extensions, and "
                            + "owner-scoped Stand Arrow pool "
                            + "overrides.");
        }
    }
}
