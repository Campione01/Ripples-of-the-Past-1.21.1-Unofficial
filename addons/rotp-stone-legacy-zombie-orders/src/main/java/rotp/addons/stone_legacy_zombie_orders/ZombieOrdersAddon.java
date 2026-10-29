package rotp.addons.stone_legacy_zombie_orders;

import java.util.function.BiFunction;
import java.util.function.Supplier;

import rotp.core.api.RotpAddonApi;
import rotp.core.api.playerpower.PlayerPowerMovesetExtensions;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.impl.powers.vampirism.VampirismPowerType;
import rotp.core.impl.powers.vampirism.entity.HungryZombieEntity;

import rotp.addons.stone_legacy_zombie_orders.ability.RecallZombiesAbility;
import rotp.addons.stone_legacy_zombie_orders.ability.ZombieSummonReplacementAbility;
import rotp.addons.stone_legacy_zombie_orders.ability.WaitOrderAbility;
import rotp.addons.stone_legacy_zombie_orders.state.ZombieConversionState;
import rotp.addons.stone_legacy_zombie_orders.state.ZombieOrderState;
import rotp.addons.stone_legacy_zombie_orders.state.ZombieRosterState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(ZombieOrdersAddon.MOD_ID)
public final class ZombieOrdersAddon {
    public static final String MOD_ID =
            "stone_legacy_zombie_orders";
    public static final String RESOURCE_NAMESPACE =
            "stone_legacy";
    private static final int REQUIRED_CORE_ABI = 1;
    private static final ResourceLocation VAMPIRISM_ID =
            ResourceLocation.fromNamespaceAndPath(
                    "jojo_ripples", "vampirism");

    public static final String RECALL = "vampirism_recall";
    public static final String WAIT_ORDER =
            "vampirism_wait_order";
    public static final String SUMMON_REPLACEMENT =
            "vampirism_zombie_summon_replacement";

    public static final DeferredRegister<AbilityType<?>>
            ABILITY_TYPES = DeferredRegister.create(
                    JojoRegistries.ABILITY_TYPES_REG,
                    RESOURCE_NAMESPACE);
    public static final DeferredRegister<AbilityType<?>>
            INTERNAL_ABILITY_TYPES = DeferredRegister.create(
                    JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<AttachmentType<?>>
            ATTACHMENT_TYPES = DeferredRegister.create(
                    NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<RecallZombiesAbility>>
            RECALL_ABILITY = ability(
                    RECALL, RecallZombiesAbility::new);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<WaitOrderAbility>>
            WAIT_ORDER_ABILITY = ability(
                    WAIT_ORDER, WaitOrderAbility::new);
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<ZombieSummonReplacementAbility>>
            SUMMON_REPLACEMENT_ABILITY =
                    INTERNAL_ABILITY_TYPES.register(
                            SUMMON_REPLACEMENT,
                            key -> new AbilityType<>(
                                    key,
                                    ZombieSummonReplacementAbility
                                            ::new));

    public static final Supplier<AttachmentType<ZombieOrderState>>
            ORDER_STATE = ATTACHMENT_TYPES.register(
                    "order_state",
                    () -> AttachmentType.serializable(
                                    holder -> holder
                                                    instanceof HungryZombieEntity
                                            ? new ZombieOrderState()
                                            : null)
                            .sync(ZombieOrderState.STREAM_CODEC)
                            .build());
    public static final Supplier<
            AttachmentType<ZombieConversionState>>
            CONVERSION_STATE = ATTACHMENT_TYPES.register(
                    "conversion_state",
                    () -> AttachmentType.serializable(
                                    holder -> holder
                                                    instanceof LivingEntity
                                            ? new ZombieConversionState()
                                            : null)
                            .copyOnDeath()
                            .build());
    public static final Supplier<
            AttachmentType<ZombieRosterState>>
            ZOMBIE_ROSTER = ATTACHMENT_TYPES.register(
                    "zombie_roster",
                    () -> AttachmentType.serializable(
                                    holder -> holder
                                                    instanceof LivingEntity
                                            ? new ZombieRosterState()
                                            : null)
                            .copyOnDeath()
                            .build());

    public ZombieOrdersAddon(IEventBus modEventBus) {
        requireCoreApi();
        ABILITY_TYPES.register(modEventBus);
        INTERNAL_ABILITY_TYPES.register(modEventBus);
        ATTACHMENT_TYPES.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
    }

    public static ResourceLocation canonicalId(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                RESOURCE_NAMESPACE, path);
    }

    public static ResourceLocation internalId(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static ZombieOrderState state(
            HungryZombieEntity zombie) {
        return zombie.getData(ORDER_STATE);
    }

    public static ZombieConversionState conversion(
            LivingEntity entity) {
        return entity.getData(CONVERSION_STATE);
    }

    public static ZombieRosterState roster(
            LivingEntity owner) {
        return owner.getData(ZOMBIE_ROSTER);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() ->
                PlayerPowerMovesetExtensions.register(
                        PlayerPowerMovesetExtensions.builder(
                                        VAMPIRISM_ID,
                                        canonicalId("zombie_orders"),
                                        100)
                                .addAbility(
                                        RECALL,
                                        RECALL_ABILITY.getId(),
                                        RECALL_ABILITY)
                                .addAbility(
                                        WAIT_ORDER,
                                        WAIT_ORDER_ABILITY.getId(),
                                        WAIT_ORDER_ABILITY)
                                .replaceAbility(
                                        "vampirism_zombie_summon",
                                        VampirismPowerType
                                                .VAMPIRE_ZOMBIE_SUMMON
                                                .getId(),
                                        SUMMON_REPLACEMENT_ABILITY
                                                .getId(),
                                        SUMMON_REPLACEMENT_ABILITY)
                                .addHotbarSlotVariation(
                                        "default",
                                        0,
                                        "vampirism_zombie_summon",
                                        RECALL,
                                        InputKey.Modifier.SHIFT,
                                        InputMethod.CLICK)
                                .addHotbarSlotVariation(
                                        "default",
                                        0,
                                        "vampirism_dark_aura",
                                        WAIT_ORDER,
                                        InputKey.Modifier.SHIFT,
                                        InputMethod.CLICK)
                                .build()));
    }

    private static <
                    A extends Ability>
            DeferredHolder<AbilityType<?>, AbilityType<A>> ability(
                    String name,
                    BiFunction<AbilityType<A>, AbilityId, A> factory) {
        return ABILITY_TYPES.register(
                name, key -> new AbilityType<>(key, factory));
    }

    private static void requireCoreApi() {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_PLAYER_POWER_MOVESET_EXTENSIONS_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_PLAYER_POWER_MOVESET_REPLACEMENTS_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_PLAYER_POWER_TEMPORARY_TRANSITIONS_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_HUNGRY_ZOMBIE_POSE_PROVIDERS_V1)) {
            throw new IllegalStateException(
                    "Stone Legacy Zombie Orders requires ROTP addon "
                            + "ABI 1 with namespaced ability resources "
                            + "and PlayerPower moveset replacement, "
                            + "temporary transition, and Hungry Zombie "
                            + "pose extensions.");
        }
    }
}
