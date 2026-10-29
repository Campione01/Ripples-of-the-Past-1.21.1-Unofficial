package rotp.addons.stand_bearing_creatures;

import rotp.core.api.RotpAddonApi;
import rotp.core.api.stand.StandDefaultRotationRules;
import rotp.core.api.stand.StandVirusMobGivers;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import rotp.addons.stand_bearing_creatures.config.MobPowerConfig;
import rotp.addons.stand_bearing_creatures.debug.DebugStandRegistry;
import rotp.addons.stand_bearing_creatures.event.MobStandRotation;
import rotp.addons.stand_bearing_creatures.mechanics.virus.GenericMobStandGiver;

@Mod(MobsWithPowersAddon.MOD_ID)
public final class MobsWithPowersAddon {
    public static final String MOD_ID = "rotp_mwp";
    public static final int REQUIRED_CORE_ABI = 1;

    public MobsWithPowersAddon(
            IEventBus modBus,
            ModContainer modContainer) {
        requireCoreApi();
        DebugStandRegistry.register(modBus);
        StandDefaultRotationRules.register(
                id("mob_stand_rotation"),
                MobStandRotation::rotate);
        StandVirusMobGivers.register(
                id("generic_mobs"),
                new GenericMobStandGiver());
        modContainer.registerConfig(
                ModConfig.Type.COMMON,
                MobPowerConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static void requireCoreApi() {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_POWER_TRANSITIONS_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_RANDOM_WEIGHTS_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ABILITY_SELECTION_VISUAL_POLICIES_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_VIRUS_MOB_GIVERS_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_DEFAULT_ROTATION_RULES_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_STAMINA_COST_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_COOLDOWN_TECHNICAL_V1)) {
            throw new IllegalStateException(
                    "RotP Stand-Bearing Creatures requires ROTP addon ABI 1 "
                            + "with public Stand transitions, random "
                            + "weights, Debug visual policies, "
                            + "Stand-virus mob givers, Stand default "
                            + "rotation rules, ability stamina costs, "
                            + "and technical ability cooldowns.");
        }
    }
}
