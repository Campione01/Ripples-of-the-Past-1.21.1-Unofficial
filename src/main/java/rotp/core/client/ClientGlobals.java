package rotp.core.client;

import rotp.core.powersystem.PowerClass;
import rotp.core.init.ModSpecialActions;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.client.Minecraft;

public class ClientGlobals {
	public static boolean canSeeStands;
	public static boolean canHearStands;
	public static StandEntity playerStandEntity;
	public static double standPrecision;

	public static void tick(Minecraft mc) {
		if (mc.player != null) {
			StandPower stand = ClientPowerCache.getPower(PowerClass.STAND);
			playerStandEntity = stand != null ? stand.getSummonedStandEntity() : null;
			canSeeStands = StandUtil.entityCanSeeStands(mc.player);
			canHearStands = canSeeStands;
		}
		else {
			playerStandEntity = null;
		}
		standPrecision = playerStandEntity != null ? playerStandEntity.getPrecision() : 0;
	}
	
	public static boolean isPlayerStandFullBodyUnsummoning() {
		if (playerStandEntity == null || playerStandEntity.isArmsOnlyMode()) {
			return false;
		}
		EntityActionInstance action = playerStandEntity.getCurStandAction();
		return action != null && action.ability == ModSpecialActions.STAND_UNSUMMON.get();
	}

	public static boolean canHearStand(StandEntity stand) {
		return stand.isVisibleForAll() || canHearStands;
	}
}
