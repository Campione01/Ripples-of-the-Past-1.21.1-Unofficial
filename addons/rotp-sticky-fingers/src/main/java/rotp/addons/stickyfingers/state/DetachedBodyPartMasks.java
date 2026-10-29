package rotp.addons.stickyfingers.state;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

/**
 * Common-side view of detached-part presentation state synchronized to a
 * client. Server decisions continue to use {@link ZipperCombatData}.
 */
public final class DetachedBodyPartMasks {
    private static final Map<UUID, Integer> CLIENT_MASKS =
            new ConcurrentHashMap<>();

    private DetachedBodyPartMasks() {}

    public static void updateClientMask(
            UUID targetId, int partMask) {
        int sanitized =
                partMask & DetachedPartType.allPartsMask();
        if (sanitized == 0) {
            CLIENT_MASKS.remove(targetId);
        } else {
            CLIENT_MASKS.put(targetId, sanitized);
        }
    }

    public static boolean isClientHandBlocked(
            LivingEntity entity, InteractionHand hand) {
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND
                ? entity.getMainArm()
                : entity.getMainArm().getOpposite();
        DetachedPartType part = arm == HumanoidArm.RIGHT
                ? DetachedPartType.RIGHT_ARM
                : DetachedPartType.LEFT_ARM;
        return (CLIENT_MASKS.getOrDefault(
                entity.getUUID(), 0) & part.mask()) != 0;
    }

    public static boolean isClientHeadDetached(UUID entityId) {
        return (CLIENT_MASKS.getOrDefault(entityId, 0)
                & DetachedPartType.HEAD.mask()) != 0;
    }

    public static void clearClientMasks() {
        CLIENT_MASKS.clear();
    }
}
