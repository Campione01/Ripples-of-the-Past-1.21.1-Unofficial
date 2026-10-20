package rotp.core.impl.stands.crazydiamond;

import javax.annotation.Nullable;

import rotp.core.entityattachment.syncheddata.SynchedDataBuilder;
import rotp.core.entityattachment.syncheddata.SyncedDataHolderExtended;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Crazy Diamond's finisher variation of its heavy punch, 1.16's CrazyDiamondMisshapingPunch: it records the body part of
 * its target that its user aimed at when it started, which its misshaping follow-up works on, and turns the Stand to it.
 */
public class CrazyDiamondMisshapingPunchAbility extends StandEntityHeavyPunchAbility {

	public CrazyDiamondMisshapingPunchAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId);
		this.createActionObj = CrazyDiamondMisshapingPunch::new;
	}

	public static class CrazyDiamondMisshapingPunch extends StandEntityHeavyPunch implements SyncedDataHolderExtended {
		private static final EntityDataAccessor<Integer> TARGET_HIT_PART = SynchedEntityData.defineId(
				CrazyDiamondMisshapingPunch.class, EntityDataSerializers.INT);

		public CrazyDiamondMisshapingPunch(EntityActionType ability) {
			super(ability);
		}

		// 1.16 onTaskSet: the part of a living task target, aimed at by the user, else by the Stand; a failed input that puts
		// this punch back keeps the part it took
		@Override
		public void onActionSet(EntityActionInstance prevAction) {
			super.onActionSet(prevAction);
			if (!level().isClientSide() && !isRestoringInputLifecycle() && performer instanceof StandEntity stand) {
				Entity target = getTaskTarget(level()).getEntity();
				if (target instanceof LivingEntity) {
					LivingEntity user = getPowerUser();
					setTargetHitPart(CrazyDTargetHitPart.getHitTarget(target, user != null ? user : stand));
				}
			}
		}

		@Nullable
		CrazyDTargetHitPart getTargetHitPart() {
			return CrazyDTargetHitPart.fromSyncId(synchedData.get(TARGET_HIT_PART));
		}

		private void setTargetHitPart(CrazyDTargetHitPart hitPart) {
			synchedData.set(TARGET_HIT_PART, hitPart.syncId());
		}

		// 1.16 rotateStandTowardsTarget: the Stand faces the recorded part of its living target
		@Override
		@Nullable
		public Vec3 getStandLookTargetPosition(StandEntity standEntity, ActionTarget target) {
			CrazyDTargetHitPart hitPart = getTargetHitPart();
			if (hitPart != null && target.getEntity() instanceof LivingEntity livingTarget) {
				return hitPart.getPartCenter(livingTarget);
			}
			return super.getStandLookTargetPosition(standEntity, target);
		}

		@Override
		public void defineSynchedData(SynchedDataBuilder builder) {
			builder.define(TARGET_HIT_PART, CrazyDTargetHitPart.NONE_SYNC_ID);
		}

		@Override
		public <T> void onSyncedDataUpdated(T oldValue, T newValue, EntityDataAccessor<T> dataAccessor) {}
	}
}
