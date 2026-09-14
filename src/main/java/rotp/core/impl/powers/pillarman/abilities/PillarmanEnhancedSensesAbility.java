package rotp.core.impl.powers.pillarman.abilities;

import java.util.OptionalInt;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.subsystems.entityglow.EntityGlowChannel;
import rotp.core.impl.powers.pillarman.PillarmanMode;
import rotp.core.impl.powers.pillarman.PillarmanPowerType;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class PillarmanEnhancedSensesAbility extends PillarmanActionAbility {
	private static final double MAX_RADIUS = 36.0;
	private static final int GLOW_TICKS = 80;

	public PillarmanEnhancedSensesAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, 2, PillarmanMode.NONE, true, 0.0F, 0.05F, 0.5F, 0,
				EnhancedSensesInstance::new);
		setButtonHoldPhase(ActionPhase.PERFORM);
	}

	public static class EnhancedSensesInstance extends PillarmanHeldActionInstance {

		public EnhancedSensesInstance(EntityActionType ability) {
			super(ability);
		}

		@Override
		protected void heldTick(PillarmanActionAbility pillarmanAbility, LivingEntity user, Power<?> context, int ticksHeld) {
			if (user == null || !user.level().isClientSide() || user != Minecraft.getInstance().player) {
				return;
			}
			if (ticksHeld < 160 || ticksHeld % 20 == 0) {
				Level level = user.level();
				double radius = Math.min(ticksHeld * 0.5, MAX_RADIUS);
				Vec3 center = user.getBoundingBox().getCenter();
				AABB area = new AABB(center, center).inflate(radius);
				for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
						target -> target != user && target.isAlive())) {
					EntityGlowChannel.PILLARMAN_ENHANCED_SENSES.apply(
							target, OptionalInt.of(PillarmanPowerType.COLOR), GLOW_TICKS);
				}
			}
		}
	}
}
