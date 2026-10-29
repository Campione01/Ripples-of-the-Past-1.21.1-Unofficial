package rotp.addons.sexpistols;

import rotp.core.api.RotpAddonApi;
import rotp.core.api.timestop.TimeStopEntityMovementAuthorizers;
import rotp.core.init.ModItems;

import rotp.addons.sexpistols.client.SexPistolsClientEvents;
import rotp.addons.sexpistols.config.SexPistolsConfig;
import rotp.addons.sexpistols.content.revolver.entity.RevolverBulletEntity;
import rotp.addons.sexpistols.network.MistaHatAmmoPayload;
import rotp.addons.sexpistols.network.RevolverChamberControlPayload;
import rotp.addons.sexpistols.network.RevolverVisualPayloads;
import rotp.addons.sexpistols.network.SexPistolsConfigSync;
import rotp.addons.sexpistols.registry.SexPistolsAbilities;
import rotp.addons.sexpistols.registry.SexPistolsArmorMaterials;
import rotp.addons.sexpistols.registry.SexPistolsAttachments;
import rotp.addons.sexpistols.registry.SexPistolsDataComponents;
import rotp.addons.sexpistols.registry.SexPistolsEntities;
import rotp.addons.sexpistols.registry.SexPistolsEffects;
import rotp.addons.sexpistols.registry.SexPistolsItems;
import rotp.addons.sexpistols.registry.SexPistolsParticles;
import rotp.addons.sexpistols.registry.SexPistolsSounds;
import rotp.addons.sexpistols.registry.SexPistolsStands;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@Mod(SexPistolsIds.MOD_ID)
public final class SexPistolsAddon {
    public static final int REQUIRED_CORE_ABI = 1;

    public SexPistolsAddon(
            IEventBus modEventBus, ModContainer modContainer) {
        requireCoreApi();
        modEventBus.addListener(
                SexPistolsConfigSync::onConfigLoading);
        modEventBus.addListener(
                SexPistolsConfigSync::onConfigReloading);
        modEventBus.addListener(
                SexPistolsConfigSync::onConfigUnloading);
        modContainer.registerConfig(
                ModConfig.Type.COMMON, SexPistolsConfig.SPEC);
        if (FMLEnvironment.dist.isClient()) {
            SexPistolsClientEvents.registerConfigScreen(modContainer);
        }
        TimeStopEntityMovementAuthorizers.register(
                SexPistolsIds.id(
                        "revolver_bullet_time_stop_deceleration"),
                entity -> entity instanceof RevolverBulletEntity bullet
                        && bullet.canMoveInStoppedTime());
        SexPistolsDataComponents.register(modEventBus);
        SexPistolsArmorMaterials.register(modEventBus);
        SexPistolsItems.register(modEventBus);
        SexPistolsParticles.register(modEventBus);
        SexPistolsAbilities.register(modEventBus);
        SexPistolsEntities.register(modEventBus);
        SexPistolsEffects.register(modEventBus);
        SexPistolsSounds.register(modEventBus);
        SexPistolsStands.register(modEventBus);
        SexPistolsAttachments.register(modEventBus);
        modEventBus.addListener(MistaHatAmmoPayload::register);
        modEventBus.addListener(
                RevolverChamberControlPayload::register);
        modEventBus.addListener(RevolverVisualPayloads::register);
        modEventBus.addListener(SexPistolsConfigSync::register);
        modEventBus.addListener(this::addCreativeTabContents);
    }

    private void addCreativeTabContents(
            BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != ModItems.MAIN_TAB.getKey()) {
            return;
        }
        event.accept(SexPistolsItems.REVOLVER.get());
        event.accept(SexPistolsItems.REVOLVER_AMMO.get());
        event.accept(SexPistolsItems.REVOLVER_CASING.get());
        event.accept(SexPistolsItems.MISTA_SUIT_HAT.get());
    }

    private static void requireCoreApi() {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_PLAYER_ARM_POSE_PROVIDERS_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_FIRST_PERSON_STAND_RENDER_POLICIES_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_TIME_STOP_LIFECYCLE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_TIME_STOP_ENTITY_MOVEMENT_AUTHORIZERS_V1)) {
            throw new IllegalStateException(
                    "RotP Sex Pistols requires ROTP addon ABI 1 with namespaced "
                            + "ability resources, Stand visual contexts, "
                            + "player arm-pose providers, and first-person "
                            + "Stand render policies, the time-stop lifecycle, "
                            + "and entity movement authorizers.");
        }
        // the plain abilities call Ability.checkPerformerStun (R148 core)
        if (!RotpAddonApi.supportsFeature(
                RotpAddonApi.FEATURE_ABILITY_PERFORMER_STUN_V1)) {
            throw new IllegalStateException(
                    "RotP Sex Pistols requires the core performer stun "
                            + "check (a core from the same release).");
        }
        // Move No. N is a held action: the core refuses the power's other
        // presses and sets its cooldown as the hold ends
        if (!RotpAddonApi.supportsFeature(
                RotpAddonApi.FEATURE_ENTITY_ACTION_HOOKS_V1)) {
            throw new IllegalStateException(
                    "RotP Sex Pistols requires the core held-action "
                            + "rules (a core from the same release).");
        }
    }
}
