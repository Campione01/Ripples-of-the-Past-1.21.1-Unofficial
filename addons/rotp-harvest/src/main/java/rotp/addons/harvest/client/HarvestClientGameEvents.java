package rotp.addons.harvest.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.UUID;

import rotp.core.client.ClientGlobals;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.util.functions.ClientUtil;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.entityglow.EntityGlowChannel;

import rotp.addons.harvest.HarvestAddon;
import rotp.addons.harvest.entity.HarvestMemberEntity;
import rotp.addons.harvest.entity.HarvestStandEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;

@EventBusSubscriber(modid = HarvestAddon.MOD_ID, value = Dist.CLIENT)
public final class HarvestClientGameEvents {
    public static final double GLOW_SCAN_RADIUS = 20.0D;
    public static final int GLOW_SCAN_LIMIT = 32;
    public static final int GLOW_SCAN_INTERVAL_TICKS = 4;
    public static final int GLOW_DURATION_TICKS = 8;
    public static final int CONTROLLED_GLOW_COLOR = 0xEACC74;
    public static final double ZERO_MOUSE_SENSITIVITY =
            -1.0D / 3.0D;
    private static UUID lastServerSelection;
    private static UUID locallySuppressedSelection;
    private static Entity ownedCameraEntity;

    private HarvestClientGameEvents() {}

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (isControlledCameraActive(Minecraft.getInstance())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onCalculatePlayerTurn(
            CalculatePlayerTurnEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player != null
                && !ModStatusEffects.isStunned(player)
                && isControlledCameraActive(minecraft)) {
            event.setMouseSensitivity(
                    ZERO_MOUSE_SENSITIVITY);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        HarvestStandEntity coordinator =
                resolveCoordinator(player);
        UUID serverSelection = coordinator != null
                ? coordinator.controlledMemberUuid().orElse(null)
                : null;
        refreshMemberGlow(
                minecraft,
                player,
                coordinator,
                serverSelection);

        if (!Objects.equals(
                lastServerSelection, serverSelection)) {
            restoreOwnedCamera(minecraft);
            locallySuppressedSelection = null;
            lastServerSelection = serverSelection;
        }
        if (player == null
                || coordinator == null
                || serverSelection == null
                || minecraft.level == null) {
            restoreOwnedCamera(minecraft);
            if (serverSelection == null) {
                lastServerSelection = null;
                locallySuppressedSelection = null;
            }
            return;
        }

        Entity selected = minecraft.level.getEntity(
                coordinator.controlledMemberId());
        if (!(selected instanceof HarvestMemberEntity member)
                || !member.isAlive()
                || !serverSelection.equals(member.getUUID())
                || !member.isOwnedBy(player.getUUID())
                || !member.isBoundTo(coordinator.getUUID())
                || player.distanceToSqr(member)
                        > HarvestStandEntity.MAX_OWNER_DISTANCE
                                * HarvestStandEntity
                                        .MAX_OWNER_DISTANCE) {
            restoreOwnedCamera(minecraft);
            return;
        }

        if (player.isShiftKeyDown()
                && minecraft.getCameraEntity() == member) {
            locallySuppressedSelection = serverSelection;
            restoreOwnedCamera(minecraft);
            return;
        }
        if (serverSelection.equals(
                locallySuppressedSelection)) {
            restoreOwnedCamera(minecraft);
            return;
        }

        ownedCameraEntity = member;
        if (minecraft.getCameraEntity() != member) {
            ClientUtil.setCameraEntityPreventShaderSwitch(member);
        }
        HarvestControlPostEffect.syncValidatedControl(
                player, coordinator, member);
        player.displayClientMessage(
                Component.translatable(
                        "message.rotp_harvest.harvesting"),
                true);
    }

    private static void refreshMemberGlow(
            Minecraft minecraft,
            LocalPlayer player,
            HarvestStandEntity coordinator,
            UUID controlledMemberUuid) {
        if (player == null
                || coordinator == null
                || minecraft.level == null
                || player.tickCount % GLOW_SCAN_INTERVAL_TICKS
                        != 0
                || !ClientGlobals.canSeeStands
                || coordinator.clientStuff == null
                || StandVisualContext.world(
                                coordinator,
                                coordinator.clientStuff.getAlpha(
                                        coordinator, 1.0F))
                        .effectiveAlpha() <= 0.0F) {
            return;
        }
        List<HarvestMemberEntity> members =
                new ArrayList<>(GLOW_SCAN_LIMIT);
        AABB searchBox = player.getBoundingBox()
                .inflate(GLOW_SCAN_RADIUS);
        minecraft.level.getEntities(
                EntityTypeTest.forClass(
                        HarvestMemberEntity.class),
                searchBox,
                member ->
                        member.isAlive()
                            && member.isOwnedBy(player.getUUID())
                            && member.isBoundTo(
                                    coordinator.getUUID()),
                members,
                GLOW_SCAN_LIMIT);
        for (HarvestMemberEntity member : members) {
            int color = controlledMemberUuid != null
                            && controlledMemberUuid.equals(
                                    member.getUUID())
                    ? CONTROLLED_GLOW_COLOR
                    : member.potionGlowColor();
            if (color >= 0) {
                EntityGlowChannel.STAND_VIEW_OBSTRUCTION
                        .apply(
                                member,
                                OptionalInt.of(color),
                                GLOW_DURATION_TICKS);
            }
        }
    }

    private static HarvestStandEntity resolveCoordinator(
            LocalPlayer player) {
        if (player == null || !player.isAlive()) {
            return null;
        }
        StandPower power = StandPower.get(player);
        if (power == null
                || !power.hasPower()
                || !power.isSummoned()
                || power.getPowerType()
                        != HarvestAddon.HARVEST_STAND.get()
                || !(power.getSummonedStandEntity()
                        instanceof HarvestStandEntity coordinator)
                || !coordinator.isOwnedBy(player.getUUID())) {
            return null;
        }
        return coordinator;
    }

    private static boolean isControlledCameraActive(
            Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (minecraft.level == null
                || player == null
                || !player.isAlive()
                || !(ownedCameraEntity
                        instanceof HarvestMemberEntity member)
                || minecraft.getCameraEntity() != member
                || !member.isAlive()
                || member.level() != minecraft.level) {
            return false;
        }
        HarvestStandEntity coordinator =
                resolveCoordinator(player);
        return coordinator != null
                && coordinator.controlledMemberId()
                        == member.getId()
                && coordinator.controlledMemberUuid()
                        .filter(member.getUUID()::equals)
                        .isPresent()
                && member.isOwnedBy(player.getUUID())
                && member.isBoundTo(coordinator.getUUID())
                && player.distanceToSqr(member)
                        <= HarvestStandEntity.MAX_OWNER_DISTANCE
                                * HarvestStandEntity
                                        .MAX_OWNER_DISTANCE;
    }

    private static void restoreOwnedCamera(Minecraft minecraft) {
        HarvestControlPostEffect.deactivate();
        if (ownedCameraEntity != null
                && minecraft.getCameraEntity()
                        == ownedCameraEntity
                && minecraft.player != null) {
            ClientUtil.setCameraEntityPreventShaderSwitch(
                    minecraft.player);
        }
        ownedCameraEntity = null;
    }
}
