package rotp.addons.nextalbum.effect;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

public final class CollapsePoseState {
    private static final Set<UUID> LOCKED =
            ConcurrentHashMap.newKeySet();

    private CollapsePoseState() {}

    public static void markLocked(Player player) {
        LOCKED.add(player.getUUID());
    }

    public static void releaseIfOwned(
            net.minecraft.world.entity.LivingEntity entity) {
        if (entity instanceof Player player
                && LOCKED.remove(player.getUUID())
                && player.getForcedPose() == Pose.SWIMMING) {
            player.setForcedPose(null);
        }
    }
}
