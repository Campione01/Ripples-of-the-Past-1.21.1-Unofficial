package com.github.standobyte.jojoimpl.stands.hierophant;

import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance.StandPart;

import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.subsystems.target.AimingEntity;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.StandUtil;
import com.github.standobyte.jojo.powersystem.standpower.entity.NoPoseStandEntityAbility;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojoimpl.stands._entitybase.StandAbilityStamina;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;

public class HierophantStringAttackAbility extends NoPoseStandEntityAbility {

	private static final int NORMAL_STRING_COUNT = 7;
	private static final int BIND_STRING_COUNT = 3;
	private static final int NORMAL_LIFESPAN = 16;
	private static final int BIND_LIFESPAN = 24;
	private static final float STAMINA_COST = 75F;
	private static final int BIND_COOLDOWN_TECHNICAL = 25;
	private static final int BIND_COOLDOWN_ADDITIONAL = 100;
	private static final float BIND_RESOLVE_COOLDOWN_MULTIPLIER = 0.5F;
	private final boolean binding;

	public HierophantStringAttackAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, type -> {
			boolean binding = isStringBind(abilityId);
			return new StringAttackShot(type, binding);
		});
		partsRequired(StandPart.MAIN_BODY);
		binding = isStringBind(abilityId);
		setDefaultPhaseLength(ActionPhase.PERFORM, binding ? BIND_LIFESPAN : NORMAL_LIFESPAN);
		if (binding) {
			cooldown(BIND_COOLDOWN_TECHNICAL, BIND_COOLDOWN_ADDITIONAL, BIND_RESOLVE_COOLDOWN_MULTIPLIER);
		}
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		return check.isPositive() ? StandAbilityStamina.check(context, STAMINA_COST) : check;
	}

	private static boolean isStringBind(AbilityId abilityId) {
		return abilityId.nameInMoveset().equals("string_bind");
	}

	@Override
	public void initActionFromConfig(EntityActionInstance action, Level level, LivingEntity powerUser, LivingEntity performer) {
		super.initActionFromConfig(action, level, powerUser, performer);
		if (action instanceof StringAttackShot stringAttack && performer instanceof StandEntity stand) {
			int actionTicks = stringActionTicks(stand, stringAttack.binding);
			action.phasesLength.put(ActionPhase.PERFORM, actionTicks);
		}
	}

	private static int stringActionTicks(StandEntity stand, boolean binding) {
		int baseTicks = binding ? BIND_LIFESPAN : NORMAL_LIFESPAN;
		double speed = stand.getAttackSpeed() / 8;
		return Mth.ceil(baseTicks / Math.max(speed, 0.125));
	}

	public static class StringAttackShot extends EntityActionInstance {
		private final boolean binding;

		public StringAttackShot(EntityActionType ability, boolean binding) {
			super(ability);
			this.binding = binding;
		}

		@Override
		public void onActionSet(EntityActionInstance prevAction) {
			if (performer instanceof StandEntity stand) {
				ActionTarget target = captureActionTargetFromAim(stand);
				keepStandAimedAtTarget(target);
			}
			else {
				keepStandAimedAtTarget();
			}
			aimAs = AimingEntity.STAND;
		}

		@Override
		public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
			return !binding
					&& cancellingAbility.getAbilityId() != null
					&& ("emerald_splash".equals(cancellingAbility.getAbilityId().nameInMoveset())
							|| "emerald_splash_concentrated".equals(cancellingAbility.getAbilityId().nameInMoveset()))
					|| super.canBeCancelledInto(cancellingAbility);
		}

		@Override
		public void actionPerformStart() {
			Level level = level();
			if (level.isClientSide()) {
				return;
			}
			LivingEntity user = getPowerUser();
			if (user == null) {
				return;
			}
			if (!(getPerformer() instanceof StandEntity stand)) {
				return;
			}
			StandPower standPower = StandPower.get(user);
			if (!StandAbilityStamina.consumeOrMessage(ability, standPower, user, STAMINA_COST)) {
				startRecovery();
				return;
			}
			int stringCount = binding ? BIND_STRING_COUNT : NORMAL_STRING_COUNT;
			int lifespan = stringActionTicks(stand, binding);
			for (int i = 0; i < stringCount; i++) {
				Vec2 rotOffsets = xRotYRotOffsets((double) i / (double) stringCount * Math.PI * 2, 10);
				spawnString(stand, rotOffsets.y, rotOffsets.x, binding, lifespan);
			}
			spawnString(stand, 0.0F, 0.0F, binding, lifespan);
			StandUtil.playStandEntitySound(stand, ModSoundEvents.HIEROPHANT_GREEN_TENTACLES, 1.0F, 1.0F);
		}

		private void spawnString(StandEntity stand, float yRotDelta, float xRotDelta, boolean binding, int lifespan) {
			HGStringEntity string = new HGStringEntity(stand, level());
			string.setStringProperties(yRotDelta, xRotDelta, binding, lifespan);
			if (!binding) {
				string.addKnockback(stand.guardCounter());
			}
			string.setShootingPosOf(stand);
			string.shootFromRotation(stand,
					stand.getXRot() + xRotDelta,
					stand.getYRot() + yRotDelta,
					0,
					16F / lifespan,
					0);
			addProjectileWithStandStats(string);
		}

		private static Vec2 xRotYRotOffsets(double angleXYRad, double z) {
			double xSq = -Math.cos(angleXYRad);
			double ySq = Math.sin(angleXYRad);
			xSq *= xSq;
			ySq *= ySq;
			double zSq = z * z;
			double angleXZ = Math.acos(Math.sqrt((xSq + zSq) / (xSq + ySq + zSq)));
			double angleYZ = Math.acos(Math.sqrt((ySq + zSq) / (xSq + ySq + zSq)));
			if (angleXYRad > Math.PI) {
				angleXZ *= -1;
			}
			if (angleXYRad > Math.PI / 2 && angleXYRad < Math.PI * 3 / 2) {
				angleYZ *= -1;
			}
			return new Vec2((float) Math.toDegrees(angleYZ), (float) Math.toDegrees(angleXZ));
		}
	}
}
