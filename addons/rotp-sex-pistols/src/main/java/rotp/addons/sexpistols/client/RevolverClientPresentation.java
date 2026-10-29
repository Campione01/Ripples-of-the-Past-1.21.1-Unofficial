package rotp.addons.sexpistols.client;

import rotp.addons.sexpistols.SexPistolsIds;
import rotp.addons.sexpistols.content.revolver.RevolverRecoil;
import rotp.addons.sexpistols.content.revolver.RevolverRecoil.Recoil;
import rotp.addons.sexpistols.content.revolver.item.RevolverItem;
import rotp.addons.sexpistols.content.revolver.state.RevolverState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber(
        modid = SexPistolsIds.MOD_ID,
        value = Dist.CLIENT)
public final class RevolverClientPresentation {
    public static final float AIM_ZOOM_MODIFIER = 1.85F;
    public static final float AIM_ZOOM_IN_SPEED = 0.22F;
    public static final float AIM_ZOOM_OUT_SPEED = 0.30F;
    public static final float RECOIL_KICK_FACTOR = 0.82F;
    public static final float RECOIL_RETURN_PITCH = 0.085F;
    public static final float RECOIL_RETURN_YAW = 0.055F;
    public static final float RECOIL_EPSILON = 0.01F;

    private static float pendingPitch;
    private static float pendingYaw;
    private static float recoveryPitch;
    private static float recoveryYaw;
    private static float aimZoomModifier = 1.0F;

    private RevolverClientPresentation() {}

    public static void addLocalRecoil(boolean aiming) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        Recoil recoil = RevolverRecoil.values(
                aiming, player.getRandom().nextFloat());
        pendingPitch = Mth.clamp(
                pendingPitch + recoil.pitch(), 0.0F, 32.0F);
        pendingYaw = Mth.clamp(
                pendingYaw + recoil.yaw(), -16.0F, 16.0F);
        SexPistolsPresentationDiagnostics.recordClientRecoil(
                recoil.pitch(), recoil.yaw());
    }

    @SubscribeEvent
    public static void onComputeFov(
            ViewportEvent.ComputeFov event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        float targetZoom = player != null && isActivelyAiming(player)
                ? AIM_ZOOM_MODIFIER
                : 1.0F;
        float speed = targetZoom > aimZoomModifier
                ? AIM_ZOOM_IN_SPEED
                : AIM_ZOOM_OUT_SPEED;
        float frameTicks = minecraft.getTimer()
                .getRealtimeDeltaTicks();
        float factor = Mth.clamp(frameTicks * speed, 0.0F, 1.0F);
        aimZoomModifier = Mth.lerp(
                factor, aimZoomModifier, targetZoom);
        if (Math.abs(aimZoomModifier - targetZoom) < 0.002F) {
            aimZoomModifier = targetZoom;
        }
        double fov = event.getFOV();
        if (aimZoomModifier > 1.001F) {
            fov /= aimZoomModifier;
            event.setFOV(fov);
        }
        SexPistolsPresentationDiagnostics.recordFov(
                aimZoomModifier, fov);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            resetTransientState();
            return;
        }
        tickRecoil(player);
    }

    @SubscribeEvent
    public static void onLogout(
            ClientPlayerNetworkEvent.LoggingOut event) {
        resetTransientState();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            resetTransientState();
        }
    }

    public static float currentAimZoomModifier() {
        return aimZoomModifier;
    }

    private static boolean isActivelyAiming(LocalPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof RevolverItem)
                || !player.isUsingItem()
                || player.getUsedItemHand() != InteractionHand.MAIN_HAND) {
            return false;
        }
        RevolverState state = RevolverItem.state(stack);
        return state.aiming() && !state.reloadMode();
    }

    private static void tickRecoil(LocalPlayer player) {
        float kickPitch = pendingPitch * RECOIL_KICK_FACTOR;
        float kickYaw = pendingYaw * RECOIL_KICK_FACTOR;
        pendingPitch -= kickPitch;
        pendingYaw -= kickYaw;
        recoveryPitch += kickPitch;
        recoveryYaw += kickYaw;
        if (Math.abs(kickPitch) > RECOIL_EPSILON
                || Math.abs(kickYaw) > RECOIL_EPSILON) {
            player.setXRot(Mth.clamp(
                    player.getXRot() - kickPitch,
                    -90.0F,
                    90.0F));
            player.setYRot(player.getYRot() + kickYaw);
        }

        float returnPitch = recoveryPitch * RECOIL_RETURN_PITCH;
        float returnYaw = recoveryYaw * RECOIL_RETURN_YAW;
        recoveryPitch -= returnPitch;
        recoveryYaw -= returnYaw;
        if (Math.abs(returnPitch) > RECOIL_EPSILON
                || Math.abs(returnYaw) > RECOIL_EPSILON) {
            player.setXRot(Mth.clamp(
                    player.getXRot() + returnPitch,
                    -90.0F,
                    90.0F));
            player.setYRot(player.getYRot() - returnYaw);
        }
        pendingPitch = clearNearZero(pendingPitch);
        pendingYaw = clearNearZero(pendingYaw);
        recoveryPitch = clearNearZero(recoveryPitch);
        recoveryYaw = clearNearZero(recoveryYaw);
        if (Math.abs(kickPitch) > RECOIL_EPSILON
                || Math.abs(kickYaw) > RECOIL_EPSILON
                || Math.abs(returnPitch) > RECOIL_EPSILON
                || Math.abs(returnYaw) > RECOIL_EPSILON) {
            SexPistolsPresentationDiagnostics.recordClientRecoilTick(
                    kickPitch - returnPitch,
                    kickYaw - returnYaw);
        }
    }

    private static float clearNearZero(float value) {
        return Math.abs(value) < RECOIL_EPSILON ? 0.0F : value;
    }

    private static void resetTransientState() {
        pendingPitch = 0.0F;
        pendingYaw = 0.0F;
        recoveryPitch = 0.0F;
        recoveryYaw = 0.0F;
        aimZoomModifier = 1.0F;
    }
}
