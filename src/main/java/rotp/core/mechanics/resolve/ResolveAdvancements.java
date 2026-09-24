package rotp.core.mechanics.resolve;

import rotp.core.core.JojoMod;
import rotp.core.init.ModCriteriaTriggers;
import rotp.core.powersystem.standpower.StandPower;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * 1.16 ModCriteriaTriggers.STAND_MAX: StandPower.setResolveLevel fired it for a level at the Stand's maximum,
 * which grants the jojo/stand_max advancement (250 xp). The trigger is registered in ModCriteriaTriggers.
 */
public final class ResolveAdvancements {
	public static final ResourceLocation STAND_MAX_ID = JojoMod.resLoc("stand_max");

	private ResolveAdvancements() {}

	/** Call after the Stand's resolve level is set; 1.16 fired the trigger for any level at the maximum. */
	public static void onResolveLevelSet(StandPower stand, int level) {
		LivingEntity user = stand.getUser();
		if (reachesStandMax(level, stand.getMaxResolveLevel(), stand.usesResolve() && stand.hasPower())
				&& user instanceof ServerPlayer player) {
			ModCriteriaTriggers.STAND_MAX.get().trigger(player);
		}
	}

	public static boolean reachesStandMax(int level, int maxLevel, boolean usesResolve) {
		return usesResolve && level >= maxLevel;
	}
}
