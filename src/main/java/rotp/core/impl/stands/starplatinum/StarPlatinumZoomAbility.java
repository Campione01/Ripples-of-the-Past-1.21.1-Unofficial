package rotp.core.impl.stands.starplatinum;

import javax.annotation.Nullable;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.impl.stands.starplatinum.client.StarPlatinumZoomClient;

import net.minecraft.world.phys.Vec3;

public class StarPlatinumZoomAbility extends NoPoseStandEntityAbility {

	public StarPlatinumZoomAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, StarPlatinumZoom::new);
		usageGroup = AbilityUsageGroup.UTILITY;
		setButtonHoldPhase(ActionPhase.PERFORM);
		partsRequired(StandPart.MAIN_BODY);
	}

	public static class StarPlatinumZoom extends EntityActionInstance {

		public StarPlatinumZoom(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onActionSet(@Nullable EntityActionInstance prevAction) {
			setStandOffset(new Vec3(-0.25, -0.3, -0.25), StandOffsetFromUser.Rotations.BODY, false);
			if (level().isClientSide() && performer instanceof StandEntity stand) {
				StarPlatinumZoomClient.startZooming(stand);
				StarPlatinumZoomClient.playZoomLoop(stand, this);
			}
		}

		@Override
		public void actionTick() {
			if (level().isClientSide() && getPhase() == ActionPhase.PERFORM && performer instanceof StandEntity stand) {
				if (curPhaseTick % 16 == 3 && curPhaseTick > 32 && curPhaseTick < 80) {
					StarPlatinumZoomClient.playZoomClick(stand);
				}
			}
		}

		@Override
		public void onButtonStopHold() {
			clearClientZoom();
			startRecovery();
		}

		@Override
		public void onActionCleared(@Nullable EntityActionInstance newAction) {
			clearClientZoom();
		}

		private void clearClientZoom() {
			if (performer != null && performer.level().isClientSide() && performer instanceof StandEntity stand) {
				StarPlatinumZoomClient.clearZooming(stand);
			}
		}

		@Override
		public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
			return true;
		}
	}
}
