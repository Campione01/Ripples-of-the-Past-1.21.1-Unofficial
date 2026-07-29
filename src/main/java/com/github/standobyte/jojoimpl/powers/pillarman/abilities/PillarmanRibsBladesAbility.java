package com.github.standobyte.jojoimpl.powers.pillarman.abilities;

import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanMode;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanRibEntity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;

public class PillarmanRibsBladesAbility extends PillarmanActionAbility {

	public PillarmanRibsBladesAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, 2, PillarmanMode.NONE, false, 60.0F, RibsBladesInstance::new);
		setDefaultPhaseLength(ActionPhase.WINDUP, 0);
		setDefaultPhaseLength(ActionPhase.PERFORM, 1);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
	}

	public static class RibsBladesInstance extends EntityActionInstance {
		public RibsBladesInstance(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void actionPerformStart() {
			Level level = level();
			if (level.isClientSide() || !(ability instanceof PillarmanRibsBladesAbility ribsAbility)) {
				return;
			}
			LivingEntity user = getPowerUser();
			if (user == null) {
				return;
			}
			Power<?> context = ribsAbility.getUserPower(user);
			if (!ribsAbility.consumeEnergy(context)) {
				return;
			}
			Vec2 rotOffsets = xRotYRotOffsets(Math.PI * 2, 10);
			addRibProjectile(level, user, rotOffsets.x, rotOffsets.y, -0.18D, -0.50D);
			addRibProjectile(level, user, rotOffsets.x, rotOffsets.y, -0.22D, -0.60D);
			addRibProjectile(level, user, rotOffsets.x, rotOffsets.y, -0.22D, -0.70D);
			addRibProjectile(level, user, rotOffsets.x, rotOffsets.y, -0.18D, -0.80D);
			addRibProjectile(level, user, rotOffsets.x, rotOffsets.y, 0.18D, -0.50D);
			addRibProjectile(level, user, rotOffsets.x, rotOffsets.y, 0.22D, -0.65D);
			addRibProjectile(level, user, rotOffsets.x, rotOffsets.y, 0.22D, -0.85D);
			addRibProjectile(level, user, rotOffsets.x, rotOffsets.y, 0.18D, -0.95D);
			ribsAbility.setPillarmanFixedCooldown(context, 80);
			if (context != null
					&& context.getDataForAbility(
							ribsAbility) != null) {
				context.getDataForAbility(ribsAbility)
						.syncOnUpdate(user);
			}
		}

		private static void addRibProjectile(Level level, LivingEntity user, float xRotDelta, float yRotDelta,
				double offsetX, double offsetY) {
			PillarmanRibEntity rib = new PillarmanRibEntity(user, level);
			rib.setRibProperties(xRotDelta, yRotDelta, offsetX, offsetY);
			rib.setLifeSpan(21);
			rib.setShootingPosOf(user);
			level.addFreshEntity(rib);
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
