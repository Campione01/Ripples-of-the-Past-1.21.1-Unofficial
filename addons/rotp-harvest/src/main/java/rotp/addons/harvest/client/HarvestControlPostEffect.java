package rotp.addons.harvest.client;

import java.util.Map;
import java.util.Set;

import rotp.core.api.client.render.AddonPostEffect;

import rotp.addons.harvest.HarvestAddon;
import rotp.addons.harvest.entity.HarvestMemberEntity;
import rotp.addons.harvest.entity.HarvestStandEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@OnlyIn(Dist.CLIENT)
public final class HarvestControlPostEffect {
    static final ResourceLocation DEFAULT_ROUTE =
            HarvestAddon.id("shaders/post/harvest.json");
    private static final Map<String, ResourceLocation>
            SKIN_ROUTES = Map.ofEntries(
                    Map.entry("harvest_stand", DEFAULT_ROUTE),
                    Map.entry(
                            "blue",
                            HarvestAddon.id(
                                    "stand_skins/blue/assets/rotp_harvest/shaders/post/harvest.json")),
                    Map.entry(
                            "purple",
                            HarvestAddon.id(
                                    "stand_skins/purple/assets/rotp_harvest/shaders/post/harvest.json")),
                    Map.entry(
                            "rabbit",
                            HarvestAddon.id(
                                    "stand_skins/rabbit/assets/rotp_harvest/shaders/post/harvest.json")),
                    Map.entry(
                            "red",
                            HarvestAddon.id(
                                    "stand_skins/red/assets/rotp_harvest/shaders/post/harvest.json")),
                    Map.entry(
                            "ryomen_sukuna",
                            HarvestAddon.id(
                                    "stand_skins/ryomen_sukuna/assets/rotp_harvest/shaders/post/harvest.json")),
                    Map.entry(
                            "sukuna_itadori",
                            HarvestAddon.id(
                                    "stand_skins/sukuna_itadori/assets/rotp_harvest/shaders/post/harvest.json")),
                    Map.entry(
                            "sukuna_megumi",
                            HarvestAddon.id(
                                    "stand_skins/sukuna_megumi/assets/rotp_harvest/shaders/post/harvest.json")));
    private static final AddonPostEffect EFFECT =
            AddonPostEffect.registerRoutes(
                    HarvestAddon.id("control_camera_color"),
                    Set.copyOf(SKIN_ROUTES.values()),
                    DEFAULT_ROUTE,
                    RenderLevelStageEvent.Stage.AFTER_LEVEL);

    private static ResourceLocation currentRoute = DEFAULT_ROUTE;

    private HarvestControlPostEffect() {}

    public static void syncValidatedControl(
            LocalPlayer player,
            HarvestStandEntity coordinator,
            HarvestMemberEntity member) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean active = minecraft.level != null
                && player != null
                && player.isAlive()
                && coordinator != null
                && coordinator.isAlive()
                && coordinator.isOwnedBy(player.getUUID())
                && member != null
                && member.isAlive()
                && member.level() == minecraft.level
                && member.isOwnedBy(player.getUUID())
                && member.isBoundTo(coordinator.getUUID())
                && coordinator.controlledMemberId()
                        == member.getId()
                && coordinator.controlledMemberUuid()
                        .filter(member.getUUID()::equals)
                        .isPresent()
                && player.distanceToSqr(member)
                        <= HarvestStandEntity.MAX_OWNER_DISTANCE
                                * HarvestStandEntity
                                        .MAX_OWNER_DISTANCE
                && minecraft.getCameraEntity() == member;
        if (!active) {
            deactivate();
            return;
        }

        ResourceLocation route = member.getStandSkin()
                .filter(skin -> HarvestAddon.MOD_ID.equals(
                        skin.getNamespace()))
                .map(ResourceLocation::getPath)
                .map(SKIN_ROUTES::get)
                .orElse(DEFAULT_ROUTE);
        if (!route.equals(currentRoute)) {
            EFFECT.setPostChainLocation(route);
            currentRoute = route;
        }
        EFFECT.setActive(true);
    }

    public static void deactivate() {
        EFFECT.setActive(false);
    }
}
