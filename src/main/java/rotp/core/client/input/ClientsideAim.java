package rotp.core.client.input;

import rotp.core.client.ClientGlobals;
import rotp.core.network.c2s.ClAimTargetPacket;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTargetAim;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

public class ClientsideAim {
	public static ActionTarget cameraEntityAimTarget = ActionTarget.EMPTY;
	public static ActionTargetAim playerAim = LivingComponentAction.CLIENT_PLAYER_AIM;
	public static ActionTargetAim standAim = new ActionTargetAim();
	
	public static void updateTarget(Minecraft mc, float partialTick) {
		cameraEntityAimTarget = mc.hitResult != null ? ActionTarget.fromVanilla(mc.hitResult) : ActionTarget.EMPTY;

		if (mc.level != null && mc.player != null) {
			// 1.16 had one mouse target, the camera entity's pick (InputHandler.pickMouseTarget), whatever the camera was,
			// and a power other than a Stand took it unchanged (IPowerType.clientHitResult)
			playerAim.setTarget(cameraEntityAimTarget);
			// but Action.checkTarget emptied it when it was the user
			if (cameraEntityAimTarget.getType() == ActionTarget.TargetType.ENTITY && cameraEntityAimTarget.getEntity() == mc.player) {
				playerAim.setTarget(ActionTarget.EMPTY);
			}
			
			StandEntity stand = ClientGlobals.playerStandEntity;
			if (stand != null) {
				standAim.setTarget(StandEntityPunchAbility.clientStandAim(stand, mc.player, mc.cameraEntity, cameraEntityAimTarget));
			}
			else {
				standAim.setTarget(ActionTarget.EMPTY);
			}
		}
		else {
			playerAim.setTarget(ActionTarget.EMPTY);
			standAim.setTarget(ActionTarget.EMPTY);
		}
	}
	
	public static void updateTargetWithServer(Minecraft mc) {
		if (mc.level != null) {
			if (playerAim.checkDirty()) {
				PacketDistributor.sendToServer(new ClAimTargetPacket(playerAim.getTarget(), ClAimTargetPacket.PacketType.PLAYER));
			}
			
			if (standAim.checkDirty()) {
				PacketDistributor.sendToServer(new ClAimTargetPacket(standAim.getTarget(), ClAimTargetPacket.PacketType.STAND));
			}
		}
	}
	
	public static boolean precisionAimingDisabled(Minecraft mc) {
		return mc.player == null || !StandEntityPunchAbility.aimAssist(mc.player);
	}

}
