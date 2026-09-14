package rotp.core.client.input;

import rotp.core.client.ClientGlobals;
import rotp.core.network.c2s.ClAimTargetPacket;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTargetAim;
import rotp.core.subsystems.target.HitResultUtil;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.function.Predicate;

public class ClientsideAim {
	public static ActionTarget cameraEntityAimTarget = ActionTarget.EMPTY;
	public static ActionTargetAim playerAim = new ActionTargetAim();
	public static ActionTargetAim standAim = new ActionTargetAim();
	
	public static void updateTarget(Minecraft mc, float partialTick) {
		cameraEntityAimTarget = mc.hitResult != null ? ActionTarget.fromVanilla(mc.hitResult) : ActionTarget.EMPTY;

		if (mc.level != null && mc.player != null) {
			boolean isPlayerCameraEntity = mc.player == mc.cameraEntity || mc.cameraEntity == null;
			if (isPlayerCameraEntity) {
				playerAim.setTarget(cameraEntityAimTarget);
			}
			else {
				playerAim.setTarget(ActionTarget.EMPTY);
			}
			
			StandEntity stand = ClientGlobals.playerStandEntity;
			if (stand != null) {
				EntityActionInstance curAction = LivingComponentAction.getCurEntityAction(stand);
				if (StandEntityBarrageAbility.isDirectionalBarrage(stand, curAction)) {
					standAim.setTarget(StandEntityBarrageAbility.clipDirectionalBarrageTarget(stand, curAction, partialTick));
					return;
				}

				LivingEntity aiming;
				if (isPlayerCameraEntity && curAction == null) {
					aiming = mc.player;
				}
				else if (curAction != null) {
					aiming = switch (curAction.aimAs) {
						case PLAYER -> mc.player;
						case STAND -> stand;
						case CAMERA_ENTITY -> isPlayerCameraEntity ? mc.player : stand;
					};
				}
				else {
					aiming = stand;
				}

				/*
				 * A crutch that patches aiming for abilities like Crazy Diamond's healing (hold and aim at an entity).
				 * Due to stand user offset interpolation, when I tell the stand to move in front of me, 
				 * on the next tick it's still a tiny bit behind me, which may cause the target that is just in the range for the player 
				 * to still be out of reach for the stand, until it's completely in front of me
				 */
				boolean standOffsetLerping = aiming == stand && isPlayerCameraEntity && stand.isFollowingUser() && stand.offsetFromUser.getLerpAmount() < 1;
				if (!standOffsetLerping) {
					Vec3 lookPos = aiming.getEyePosition(partialTick);
					Vec3 lookVec = aiming.getViewVector(partialTick);
					Predicate<Entity> targetFilter = curAction != null && curAction.ability instanceof StandEntityAbility standAbility
							? entity -> standAbility.canTargetEntityForAiming(stand, entity)
							: entity -> StandEntityAbility.canDefaultTargetEntityForAiming(stand, entity);
					ActionTarget target = HitResultUtil.clip(lookPos, lookVec, 
							stand.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE), stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE), 
							aiming.level(), targetFilter, aiming, precisionAimingDisabled(mc) ? 0 : stand.getPrecision());
					standAim.setTarget(target);
				}
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
		return mc.player == null || mc.player.isShiftKeyDown();
	}

}
