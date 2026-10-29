package rotp.addons.blood_bottle;

import rotp.addons.blood_bottle.ability.BloodRefillAbility;
import rotp.addons.blood_bottle.item.BloodBottleItem;
import rotp.core.api.RotpAddonApi;
import rotp.core.api.playerpower.PlayerPowerMovesetExtensions;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModItems;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(BloodBottleAddon.MOD_ID)
public final class BloodBottleAddon {
    public static final String MOD_ID = "rotp_bb";
    private static final int REQUIRED_CORE_ABI = 1;
    private static final ResourceLocation VAMPIRISM_ID =
            ResourceLocation.fromNamespaceAndPath(
                    "jojo_ripples", "vampirism");

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(
                    JojoRegistries.ABILITY_TYPES_REG, MOD_ID);

    public static final DeferredHolder<Item, BloodBottleItem> BLOOD_BOTTLE =
            ITEMS.register(
                    "blood_bottle",
                    () -> new BloodBottleItem(
                            new Item.Properties().stacksTo(16)));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<BloodRefillAbility>> BLOOD_REFILL =
            ABILITY_TYPES.register(
                    "vampirism_blood_refill",
                    key -> new AbilityType<>(
                            key, BloodRefillAbility::new));

    public BloodBottleAddon(
            IEventBus modEventBus, ModContainer modContainer) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_PLAYER_POWER_MOVESET_EXTENSIONS_V1)) {
            throw new IllegalStateException(
                    "Blood Bottle requires ROTP addon ABI 1 with "
                            + "namespaced ability resources and "
                            + "PlayerPower moveset extensions.");
        }

        ITEMS.register(modEventBus);
        ABILITY_TYPES.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::buildCreativeTab);
        modContainer.registerConfig(
                ModConfig.Type.COMMON,
                BloodBottleConfig.SPEC,
                "jojo-bb-a-common.toml");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() ->
                PlayerPowerMovesetExtensions.register(
                        PlayerPowerMovesetExtensions.builder(
                                        VAMPIRISM_ID,
                                        id("vampirism_blood_refill"),
                                        100)
                                .addAbility(
                                        "vampirism_blood_refill",
                                        BLOOD_REFILL.getId(),
                                        BLOOD_REFILL)
                                .addHotbarSlotVariation(
                                        "default",
                                        0,
                                        "vampirism_blood_gift",
                                        "vampirism_blood_refill",
                                        InputKey.Modifier.SHIFT,
                                        InputMethod.HOLD)
                                .build()));
    }

    private void buildCreativeTab(
            BuildCreativeModeTabContentsEvent event) {
        if (ModItems.MAIN_TAB.getKey().equals(event.getTabKey())) {
            event.accept(BLOOD_BOTTLE.get());
        }
    }
}
