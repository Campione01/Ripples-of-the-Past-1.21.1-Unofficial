package rotp.core.powersystem.standpower.entity;

import net.minecraft.world.entity.LivingEntity;

/**
 * 1.16 StandEntityRenderer.idlePoseSwaying moved a Java-model Stand's idle up and down (doIdlePoseSwaying) only while the
 * Stand was not visible for all, in the IDLE pose with no vanilla arm swing (model.attackTime 0), following its user, and
 * its user did not sneak; otherwise the model stood still. The port's idles converted from those models key that sway on
 * their root bone, {@value #SWAYED_BONE}, and mark it in their timeline with {@value #CLIP_FIELD} = {@value #CLIP_VALUE};
 * the client takes the root bone's offset out of such an idle's pose while {@link #swaysAsIn116} is false. An add-on idle
 * converted the same way can carry the same mark.
 */
public final class StandIdleSway {
	public static final String CLIP_FIELD = "idleSway";
	public static final String CLIP_VALUE = "legacy";
	public static final String SWAYED_BONE = "stand_pos";

	private StandIdleSway() {}

	public static boolean swaysAsIn116(StandEntity stand, float partialTick) {
		if (stand.isVisibleForAll() || stand.getAttackAnim(partialTick) != 0 || !stand.isFollowingUser()) {
			return false;
		}
		LivingEntity user = stand.getUser();
		return user == null || !user.isShiftKeyDown();
	}
}
