package rotp.addons.stickyfingers.client;

import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.subsystems.entity_puppetcontrol.client.ClientEntityController;

import rotp.addons.stickyfingers.entity.DetachedBodyPartEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.common.NeoForge;

final class DetachedHeadCameraController
        extends ClientEntityController {
    @Nullable private static DetachedHeadCameraController active;

    private final DetachedBodyPartEntity head;
    private final UUID playerUuid;
    private boolean forwardingTurn;

    private DetachedHeadCameraController(
            DetachedBodyPartEntity head,
            UUID playerUuid) {
        super(head);
        this.head = head;
        this.playerUuid = playerUuid;
    }

    static void update(
            Minecraft minecraft,
            boolean headDetached) {
        LocalPlayer player = minecraft.player;
        if (!headDetached
                || player == null
                || minecraft.level == null
                || !player.isAlive()) {
            release();
            return;
        }

        if (active != null
                && ClientEntityController.getInstance() != active) {
            active = null;
        }
        DetachedBodyPartEntity head =
                findLocalHead(minecraft, player.getUUID());
        if (head == null) {
            release();
            return;
        }
        if (active != null) {
            if (active.head == head
                    && active.playerUuid.equals(player.getUUID())) {
                return;
            }
            release();
        }

        if (ClientEntityController.getInstance() != null) {
            return;
        }
        if (minecraft.getCameraEntity() != null
                && minecraft.getCameraEntity() != player
                && minecraft.getCameraEntity() != head) {
            return;
        }

        DetachedHeadCameraController controller =
                new DetachedHeadCameraController(
                        head, player.getUUID());
        active = controller;
        ClientEntityController.setInstance(controller);
    }

    static boolean isActive() {
        Minecraft minecraft = Minecraft.getInstance();
        return active != null
                && ClientEntityController.getInstance() == active
                && minecraft.getCameraEntity() == active.head;
    }

    static void release() {
        DetachedHeadCameraController controller = active;
        active = null;
        if (controller != null
                && ClientEntityController.getInstance() == controller) {
            ClientEntityController.setInstance(null);
        }
    }

    @Nullable
    private static DetachedBodyPartEntity findLocalHead(
            Minecraft minecraft,
            UUID playerUuid) {
        for (var entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof DetachedBodyPartEntity part
                    && part.isAlive()
                    && part.getPartType().isHead()
                    && playerUuid.equals(part.getTargetUuid())) {
                return part;
            }
        }
        return null;
    }

    @Override
    public void onSet() {
        NeoForge.EVENT_BUS.register(this);
    }

    @Override
    public void onUnset() {
        NeoForge.EVENT_BUS.unregister(this);
        if (active == this) {
            active = null;
        }
    }

    @Override
    public boolean turn(double yRot, double xRot) {
        if (forwardingTurn) {
            return false;
        }
        LocalPlayer player = mc.player;
        if (player == null
                || !playerUuid.equals(player.getUUID())) {
            return true;
        }
        forwardingTurn = true;
        try {
            player.turn(yRot, xRot);
        } finally {
            forwardingTurn = false;
        }
        copyPlayerRotation(player);
        return true;
    }

    @Override
    public void tick() {
        LocalPlayer player = mc.player;
        if (player != null
                && playerUuid.equals(player.getUUID())) {
            copyPlayerRotation(player);
        }
    }

    @Override
    public boolean shouldRenderBlockOutline() {
        return true;
    }

    @Override
    public Entity getPickSource(Entity cameraEntity) {
        LocalPlayer player = mc.player;
        return player != null
                && playerUuid.equals(player.getUUID())
                        ? player
                        : cameraEntity;
    }

    @Override
    public boolean controlsLocalPlayerCamera() {
        return true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void preservePlayerMovement(
            MovementInputUpdateEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || event.getEntity() != player) {
            return;
        }
        Input input = event.getInput();
        player.xxa = input.leftImpulse;
        player.zza = input.forwardImpulse;
        player.setJumping(input.jumping);
    }

    private void copyPlayerRotation(LocalPlayer player) {
        head.yRotO = player.yRotO;
        head.xRotO = player.xRotO;
        head.setYRot(player.getYRot());
        head.setXRot(player.getXRot());
    }
}
