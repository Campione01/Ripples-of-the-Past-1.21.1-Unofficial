package rotp.core.customobjects;

import rotp.core.client.ClientGlobals;
import rotp.core.client.ClientProxy;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.util.functions.JojoModUtil;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public interface EntityStandVisibility {
	boolean onlyVisibleToStandUsers();
	
	default boolean clientCantSeeThisStand() {
		return onlyVisibleToStandUsers() && level().isClientSide() && !ClientGlobals.canSeeStands;
	}

	default boolean standVisibleTo(Player player) {
		if (player == null) {
			return false;
		}
		if (level().isClientSide() && player == ClientProxy.getClientPlayer()) {
			return ClientGlobals.canSeeStands;
		}
		return StandUtil.entityCanSeeStands(player);
	}

	default boolean isInvisibleToStandViewer(Player player, boolean normallyInvisible) {
		if (player == null) {
			return onlyVisibleToStandUsers() || normallyInvisible;
		}
		return onlyVisibleToStandUsers() && !standVisibleTo(player)
				|| !JojoModUtil.seesInvisibleAsSpectator(player) && normallyInvisible;
	}
	
	Level level();
}
