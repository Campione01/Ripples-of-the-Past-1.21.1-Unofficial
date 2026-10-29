package rotp.addons.vampire_climb;

import rotp.core.api.RotpAddonApi;
import rotp.core.api.playerpower.PlayerPowerMovesetExtensions;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputMethod;

import rotp.addons.vampire_climb.ability.VampireWallClimbAbility;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(VampireClimbAddon.MOD_ID)
public final class VampireClimbAddon {
    public static final String MOD_ID = "rotp_vamp_climb";
    private static final int REQUIRED_CORE_ABI = 1;
    private static final String ABILITY_NAME =
            "vampirism_wall_climb";
    private static final ResourceLocation VAMPIRISM_ID =
            ResourceLocation.fromNamespaceAndPath(
                    "jojo_ripples", "vampirism");

    public static final DeferredRegister<AbilityType<?>>
            ABILITY_TYPES = DeferredRegister.create(
                    JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<VampireWallClimbAbility>>
            WALL_CLIMB = ABILITY_TYPES.register(
                    ABILITY_NAME,
                    key -> new AbilityType<>(
                            key, VampireWallClimbAbility::new));

    public VampireClimbAddon(IEventBus modEventBus) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_PLAYER_POWER_MOVESET_EXTENSIONS_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_FIRST_PERSON_POST_ARM_LAYERS_V1)) {
            throw new IllegalStateException(
                    "RotP Vampire Climb requires ROTP addon ABI 1 "
                            + "with namespaced ability resources and "
                            + "PlayerPower moveset extensions plus "
                            + "first-person post-arm layers.");
        }

        ABILITY_TYPES.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() ->
                PlayerPowerMovesetExtensions.register(
                        PlayerPowerMovesetExtensions.builder(
                                        VAMPIRISM_ID,
                                        id(ABILITY_NAME),
                                        100)
                                .addAbility(
                                        ABILITY_NAME,
                                        WALL_CLIMB.getId(),
                                        WALL_CLIMB)
                                .insertAfterInHotbar(
                                        "default",
                                        0,
                                        "vampirism_claw_lacerate",
                                        ABILITY_NAME,
                                        InputMethod.HOLD)
                                .build()));
    }
}
