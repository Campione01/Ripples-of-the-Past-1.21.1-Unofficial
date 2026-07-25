package com.github.standobyte.jojoimpl.stands._entitybase;

import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.init.ModSpecialActions;
import com.github.standobyte.jojo.network.s2c.StandEntitySoundPacket;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.entityaction.type.SpecialEntityActionType;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandOffsetFromUser;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public class StandEntityUnsummonAction extends SpecialEntityActionType {

	public StandEntityUnsummonAction(ResourceLocation id) {
		super(null, id);
	}

	@Override
	public EntityActionInstance createActionObj() {
		return new StandUnsummonInstance(this);
	}

	public static class StandUnsummonInstance extends EntityActionInstance {
		protected boolean playedSound = false;
		private boolean startedUnsummoning = false;
		private Vec3 unsummonOffset;
		private StandOffsetFromUser.Rotations unsummonRotations;

		public StandUnsummonInstance() {
			this(ModSpecialActions.STAND_UNSUMMON.get());
			this.setStartingPhase();
		}

		protected StandUnsummonInstance(EntityActionType ability) {
			super(ability);
			phasesLength.put(ActionPhase.PERFORM, 10f);
		}
		
		@Override
		public void actionPerformStart() {
			if (performer instanceof StandEntity standEntity) {
				beginOrContinueUnsummoning(standEntity);
			}
		}
		
		@Override
		public void actionTick() {
			if (performer instanceof StandEntity standEntity && beginOrContinueUnsummoning(standEntity)) {
				applyUnsummonOffset(standEntity);
				if (standEntity.level().isClientSide()) {
					applyUnsummonTranslucency(standEntity);
				}
			}
		}

		private boolean beginOrContinueUnsummoning(StandEntity standEntity) {
			if (!canContinueUnsummoning(standEntity)) {
				return false;
			}
			ensureUnsummoningStarted(standEntity);
			return true;
		}

		private void ensureUnsummoningStarted(StandEntity standEntity) {
			if (!startedUnsummoning) {
				float duration = standEntity.getUnsummonDuration();
				phasesLength.put(ActionPhase.PERFORM, duration);
				curPhaseLength = duration;
				startedUnsummoning = true;
			}
			if (!standEntity.isArmsOnlyMode() && unsummonOffset == null) {
				captureUnsummonOffset(standEntity);
			}
		}

		private void captureUnsummonOffset(StandEntity standEntity) {
			if (!standEntity.isArmsOnlyMode()) {
				unsummonOffset = standEntity.offsetFromUser.getRelativeOffset();
				unsummonRotations = standEntity.offsetFromUser.getRotations();
			}
		}

		private void applyUnsummonOffset(StandEntity standEntity) {
			if (!standEntity.isArmsOnlyMode() && unsummonOffset != null && unsummonRotations != null) {
				float length = getCurPhaseLength();
				if (length > 0) {
					double scale = Mth.clamp(1 - getElapsedUnsummonTicks(length) / length, 0, 1);
					standEntity.offsetFromUser.setOffset(unsummonOffset.scale(scale), unsummonRotations);
					standEntity.offsetFromUser.standAbility = this.ability;
				}
			}
		}

		private void applyUnsummonTranslucency(StandEntity standEntity) {
			float length = getCurPhaseLength();
			if (length > 0) {
				float ratio = 1 - getElapsedUnsummonTicks(length) / length;
				standEntity.multiplyTranslucency(Mth.clamp(ratio, 0, 1));
			}
		}

		private float getElapsedUnsummonTicks(float length) {
			return Mth.clamp(getPhaseTick() + 1F, 0F, length);
		}
		
		@Override
		protected void _incPhaseTick() {
			if (performer instanceof StandEntity standEntity && canContinueUnsummoning(standEntity)) {
				if (!performer.level().isClientSide() && !playedSound) {
					LivingEntity user = getPowerUser();
					if (user != null) {
						StandPower userPower = StandPower.get(user);
						PacketDistributor.sendToPlayersTrackingEntityAndSelf(user, new StandEntitySoundPacket(standEntity,
								userPower != null && userPower.hasPower() ? userPower.getPowerType().getUnsummonSound() : ModSoundEvents.STAND_UNSUMMON, 1, 1));
						playedSound = true;
					}
				}
				super._incPhaseTick();
			}
		}
		
		private boolean canContinueUnsummoning(StandEntity standEntity) {
			return standEntity.isCloseToUser() || standEntity.isFollowingUser() || startedUnsummoning;
		}

		@Override
		public void actionPerformEnd() {
			LivingEntity user = getPowerUser();
			if (user != null && !user.level().isClientSide()) {
				StandPower userPower = StandPower.get(getPowerUser());
				if (userPower != null && userPower.hasPower()) {
					userPower.getPowerType().forceUnsummon(user, userPower);
				}
			}
		}
		
		@Override
		public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
			if (performer instanceof StandEntity standEntity) {
				return !standEntity.isArmsOnlyMode() && cancellingAbility != this.ability;
			}
			return true;
		}

		@Override
		public boolean canStaminaRegen(StandPower standPower, StandEntity standEntity) {
			return true;
		}

		@Override
		public void onActionCleared(EntityActionInstance newAction) {
			if (performer instanceof StandEntity standEntity && !standEntity.isRemoved()) {
				standEntity.offsetFromUser.resetToIdle();
			}
		}
		
		@Override
		public void toBuf(FriendlyByteBuf buf) {
			buf.writeBoolean(startedUnsummoning);
			buf.writeBoolean(unsummonOffset != null && unsummonRotations != null);
			if (unsummonOffset != null && unsummonRotations != null) {
				buf.writeDouble(unsummonOffset.x);
				buf.writeDouble(unsummonOffset.y);
				buf.writeDouble(unsummonOffset.z);
				buf.writeEnum(unsummonRotations);
			}
		}
		
		@Override
		public void fromBuf(FriendlyByteBuf buf) {
			startedUnsummoning = buf.readBoolean();
			if (buf.readBoolean()) {
				unsummonOffset = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
				unsummonRotations = buf.readEnum(StandOffsetFromUser.Rotations.class);
			}
			else {
				unsummonOffset = null;
				unsummonRotations = null;
			}
		}
		
	}
	
}
