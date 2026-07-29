package com.github.standobyte.jojoimpl.powers.pillarman.abilities;

import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanMode;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanVeinEntity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;

public class PillarmanErraticBlazeKingAbility extends PillarmanActionAbility {

	public PillarmanErraticBlazeKingAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, -1, PillarmanMode.HEAT, false, 20.0F, 0.0F, 0.1F, 0,
				ErraticBlazeKingInstance::new);
		setDefaultPhaseLength(ActionPhase.WINDUP, 0);
		setDefaultPhaseLength(ActionPhase.PERFORM, 40);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
	}

	public static void addVeinProjectile(Level level, LivingEntity user, float xRotDelta, float yRotDelta,
			double offsetX, double offsetY, double offsetZ) {
		PillarmanVeinEntity vein = new PillarmanVeinEntity(level, user, xRotDelta, yRotDelta, offsetX, offsetY, offsetZ);
		vein.setLifeSpan(25);
		vein.setShootingPosOf(user);
		level.addFreshEntity(vein);
	}

	public static class ErraticBlazeKingInstance extends EntityActionInstance {
		public ErraticBlazeKingInstance(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onSetPhase(ActionPhase newPhase) {
			if (ability instanceof PillarmanErraticBlazeKingAbility erraticAbility && newPhase == ActionPhase.PERFORM) {
				userWalkSpeed = erraticAbility.heldWalkSpeed;
			}
			else {
				userWalkSpeed = 1.0F;
			}
		}

		@Override
		public void actionPerformStart() {
			Level level = level();
			if (level.isClientSide() || !(ability instanceof PillarmanErraticBlazeKingAbility erraticAbility)) {
				return;
			}
			LivingEntity user = getPowerUser();
			if (user == null) {
				return;
			}
			Power<?> context = erraticAbility.getUserPower(user);
			if (!erraticAbility.consumeEnergy(context)) {
				forceStop();
				syncPhaseChanges();
				return;
			}
			if (context != null
					&& context.getDataForAbility(
							erraticAbility) != null) {
				context.getDataForAbility(erraticAbility)
						.syncOnUpdate(user);
			}
		}

		@Override
		public void actionTick() {
			Level level = level();
			if (level.isClientSide()) {
				return;
			}
			LivingEntity user = getPowerUser();
			int ticksHeld = (int) getPhaseTick();
			if (getPhase() == ActionPhase.PERFORM && ticksHeld == 10 && user != null) {
				int n = 5;
				for (int i = 0; i < n; i++) {
					Vec2 rotOffsets = xRotYRotOffsets((double) i / (double) n * Math.PI * 2.0D, 10.0D);
					PillarmanErraticBlazeKingAbility.addVeinProjectile(level, user,
							rotOffsets.x, rotOffsets.y, -0.4D, -0.45D, 1.0D);
					PillarmanErraticBlazeKingAbility.addVeinProjectile(level, user,
							rotOffsets.x, rotOffsets.y, 0.425D, -0.575D, 1.0D);
				}
			}
			if (ticksHeld >= 40) {
				forceStop();
				syncPhaseChanges();
			}
		}

		static Vec2 xRotYRotOffsets(double angleXYRad, double z) {
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
