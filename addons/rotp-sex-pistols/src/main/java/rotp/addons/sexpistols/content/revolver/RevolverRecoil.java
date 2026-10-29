package rotp.addons.sexpistols.content.revolver;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public final class RevolverRecoil {
    public static final float PITCH_DEGREES = 5.35F;
    public static final float YAW_DEGREES = 1.75F;
    public static final float AIM_PITCH_SCALE = 0.74F;
    public static final float AIM_YAW_SCALE = 0.55F;

    private static long serverApplications;
    private static float lastServerPitch;
    private static float lastServerYaw;
    private static boolean lastServerAiming;

    private RevolverRecoil() {}

    public static Recoil values(
            boolean aiming, float randomValue) {
        float pitch = PITCH_DEGREES
                * (aiming ? AIM_PITCH_SCALE : 1.0F);
        float yaw = (Mth.clamp(randomValue, 0.0F, 1.0F) - 0.5F)
                * YAW_DEGREES
                * (aiming ? AIM_YAW_SCALE : 1.0F);
        return new Recoil(pitch, yaw);
    }

    public static void applyServer(
            Player player, boolean aiming, float randomValue) {
        Recoil recoil = values(aiming, randomValue);
        player.setXRot(Mth.clamp(
                player.getXRot() - recoil.pitch(),
                -90.0F,
                90.0F));
        player.setYRot(player.getYRot() + recoil.yaw());
        synchronized (RevolverRecoil.class) {
            serverApplications++;
            lastServerPitch = recoil.pitch();
            lastServerYaw = recoil.yaw();
            lastServerAiming = aiming;
        }
    }

    public static synchronized Snapshot snapshot() {
        return new Snapshot(
                serverApplications,
                lastServerPitch,
                lastServerYaw,
                lastServerAiming);
    }

    public static synchronized void clearReceipts() {
        serverApplications = 0L;
        lastServerPitch = 0.0F;
        lastServerYaw = 0.0F;
        lastServerAiming = false;
    }

    public record Recoil(float pitch, float yaw) {}

    public record Snapshot(
            long serverApplications,
            float lastServerPitch,
            float lastServerYaw,
            boolean lastServerAiming) {}
}
