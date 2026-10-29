package rotp.addons.stone_legacy;

import java.util.function.BiFunction;
import java.util.function.Supplier;

import rotp.core.api.RotpAddonApi;
import rotp.core.api.playerpower.PlayerPowerMovesetExtensions;
import rotp.core.api.stonemask.StoneMaskExtensions;
import rotp.core.block.StoneMaskBlock;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModArmorMaterials;
import rotp.core.init.ModItems;
import rotp.core.loot.AdditionalSingleItemLootModifier;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.impl.powers.pillarman.PillarmanMode;
import com.mojang.serialization.MapCodec;

import rotp.addons.stone_legacy.ability.PillarmanHamonSuicideAbility;
import rotp.addons.stone_legacy.ability.PillarmanPetrifyAbility;
import rotp.addons.stone_legacy.item.PredictedStoneMaskItem;
import rotp.addons.stone_legacy.item.StoneFruitItem;
import rotp.addons.stone_legacy.state.StoneLegacyState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(StoneLegacyAddon.MOD_ID)
public final class StoneLegacyAddon {
    public static final String MOD_ID = "stone_legacy";
    private static final int REQUIRED_CORE_ABI = 1;
    private static final ResourceLocation PILLARMAN_ID =
            ResourceLocation.fromNamespaceAndPath(
                    "jojo_ripples", "pillarman");

    public static final String PETRIFY = "pillarman_petrify";
    public static final String HAMON_SUICIDE =
            "pillarman_hamon_suicide";
    public static final String PREDICTED_HEAT =
            "aja_stone_mask_predicted_heat";
    public static final String PREDICTED_LIGHT =
            "aja_stone_mask_predicted_light";
    public static final String PREDICTED_WIND =
            "aja_stone_mask_predicted_wind";

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, MOD_ID);
    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(
                    JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<AttachmentType<?>>
            ATTACHMENT_TYPES = DeferredRegister.create(
                    NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);
    public static final DeferredRegister<
            MapCodec<? extends IGlobalLootModifier>>
            LOOT_MODIFIER_SERIALIZERS = DeferredRegister.create(
                    NeoForgeRegistries.Keys
                            .GLOBAL_LOOT_MODIFIER_SERIALIZERS,
                    MOD_ID);

    public static final DeferredHolder<Block, StoneMaskBlock>
            PREDICTED_HEAT_BLOCK = predictedMaskBlock(
                    PREDICTED_HEAT);
    public static final DeferredHolder<Block, StoneMaskBlock>
            PREDICTED_LIGHT_BLOCK = predictedMaskBlock(
                    PREDICTED_LIGHT);
    public static final DeferredHolder<Block, StoneMaskBlock>
            PREDICTED_WIND_BLOCK = predictedMaskBlock(
                    PREDICTED_WIND);

    public static final DeferredHolder<Item, StoneFruitItem> FRUIT =
            ITEMS.register(
                    "fruit",
                    () -> new StoneFruitItem(
                            new Item.Properties()
                                    .stacksTo(1)
                                    .rarity(Rarity.RARE)
                                    .food(new FoodProperties.Builder()
                                            .nutrition(4)
                                            .saturationModifier(6.0F)
                                            .alwaysEdible()
                                            .build())));
    public static final DeferredHolder<
            Item, PredictedStoneMaskItem>
            PREDICTED_HEAT_MASK = predictedMaskItem(
                    PREDICTED_HEAT,
                    PREDICTED_HEAT_BLOCK,
                    PillarmanMode.HEAT);
    public static final DeferredHolder<
            Item, PredictedStoneMaskItem>
            PREDICTED_LIGHT_MASK = predictedMaskItem(
                    PREDICTED_LIGHT,
                    PREDICTED_LIGHT_BLOCK,
                    PillarmanMode.LIGHT);
    public static final DeferredHolder<
            Item, PredictedStoneMaskItem>
            PREDICTED_WIND_MASK = predictedMaskItem(
                    PREDICTED_WIND,
                    PREDICTED_WIND_BLOCK,
                    PillarmanMode.WIND);

    public static final DeferredHolder<
            MapCodec<? extends IGlobalLootModifier>,
            MapCodec<AdditionalSingleItemLootModifier>>
            ADDITIONAL_SINGLE_ITEM =
                    LOOT_MODIFIER_SERIALIZERS.register(
                            "additional_single_item",
                            () -> AdditionalSingleItemLootModifier
                                    .CODEC.xmap(
                                            modifier -> modifier,
                                            modifier -> modifier));

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<PillarmanPetrifyAbility>>
            PETRIFY_ABILITY = ability(
                    PETRIFY, PillarmanPetrifyAbility::new);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<PillarmanHamonSuicideAbility>>
            HAMON_SUICIDE_ABILITY = ability(
                    HAMON_SUICIDE,
                    PillarmanHamonSuicideAbility::new);

    public static final Supplier<AttachmentType<StoneLegacyState>>
            PLAYER_STATE = ATTACHMENT_TYPES.register(
                    "player_state",
                    () -> AttachmentType.serializable(
                                    holder -> holder instanceof Player
                                            ? new StoneLegacyState()
                                            : null)
                            .sync(StoneLegacyState.STREAM_CODEC)
                            .copyOnDeath()
                            .build());

    public static final ResourceKey<DamageType> ASPHYXIA =
            ResourceKey.create(
                    Registries.DAMAGE_TYPE, id("asphyxia"));

    public StoneLegacyAddon(IEventBus modEventBus) {
        requireCoreApi();
        StoneMaskExtensions.registerBlock(
                id(PREDICTED_HEAT), PREDICTED_HEAT_BLOCK);
        StoneMaskExtensions.registerBlock(
                id(PREDICTED_LIGHT), PREDICTED_LIGHT_BLOCK);
        StoneMaskExtensions.registerBlock(
                id(PREDICTED_WIND), PREDICTED_WIND_BLOCK);
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        ABILITY_TYPES.register(modEventBus);
        ATTACHMENT_TYPES.register(modEventBus);
        LOOT_MODIFIER_SERIALIZERS.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::buildCreativeTab);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static StoneLegacyState state(LivingEntity entity) {
        return entity.getData(PLAYER_STATE);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() ->
                PlayerPowerMovesetExtensions.register(
                        PlayerPowerMovesetExtensions.builder(
                                        PILLARMAN_ID,
                                        id("pillarman_rites"),
                                        100)
                                .addAbility(
                                        PETRIFY,
                                        PETRIFY_ABILITY.getId(),
                                        PETRIFY_ABILITY)
                                .addAbility(
                                        HAMON_SUICIDE,
                                        HAMON_SUICIDE_ABILITY.getId(),
                                        HAMON_SUICIDE_ABILITY)
                                .bindInExistingGroup(
                                        "default",
                                        "moveset_group.pillarman.combat",
                                        PETRIFY,
                                        InputMethod.HOLD,
                                        InputKey.RMB.withModifier(
                                                InputKey.Modifier.SHIFT))
                                .bindInExistingGroup(
                                        "default",
                                        "moveset_group.pillarman.utility",
                                        HAMON_SUICIDE,
                                        InputMethod.HOLD,
                                        InputKey.RMB)
                                .build()));
    }

    private void buildCreativeTab(
            BuildCreativeModeTabContentsEvent event) {
        if (ModItems.MAIN_TAB.getKey().equals(event.getTabKey())) {
            event.accept(FRUIT.get());
            event.accept(PREDICTED_HEAT_MASK.get());
            event.accept(PREDICTED_LIGHT_MASK.get());
            event.accept(PREDICTED_WIND_MASK.get());
        }
    }

    private static DeferredHolder<Block, StoneMaskBlock>
            predictedMaskBlock(String name) {
        return BLOCKS.register(
                name,
                () -> new StoneMaskBlock(
                        Block.Properties.ofFullCopy(Blocks.STONE)
                                .strength(1.5F)
                                .noOcclusion()
                                .noCollission()));
    }

    private static DeferredHolder<Item, PredictedStoneMaskItem>
            predictedMaskItem(
                    String name,
                    Supplier<? extends StoneMaskBlock> block,
                    PillarmanMode mode) {
        return ITEMS.register(
                name,
                () -> new PredictedStoneMaskItem(
                        new Item.Properties()
                                .durability(ModArmorMaterials.STONE_MASK_DURABILITY)
                                .rarity(Rarity.RARE),
                        block.get(),
                        mode));
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
                                .FEATURE_PLAYER_POWER_MOVESET_GROUP_BINDINGS_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STONE_MASK_EXTENSIONS_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ITEM_USE_PARTICLE_PROVIDER_V1)) {
            throw new IllegalStateException(
                    "Stone Legacy requires ROTP addon ABI 1 with "
                            + "namespaced ability resources and "
                            + "PlayerPower moveset/group binding, "
                            + "Stone Mask, and item-use particle "
                            + "extensions.");
        }
    }
}
