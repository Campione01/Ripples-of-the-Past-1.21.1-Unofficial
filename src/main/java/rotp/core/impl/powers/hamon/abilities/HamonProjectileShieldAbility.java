package rotp.core.impl.powers.hamon.abilities;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.entity.HamonProjectileShieldEntity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class HamonProjectileShieldAbility extends HamonActionRuntimeAbility {
	private static final float SHIELD_WIDTH = 8.0F;
	private static final float SHIELD_HEIGHT = 4.0F;

	public HamonProjectileShieldAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, ProjectileShieldInstance::new);
		setDefaultPhaseLength(ActionPhase.WINDUP, 4);
		setDefaultPhaseLength(ActionPhase.PERFORM, 8);
		setButtonHoldPhase(ActionPhase.PERFORM);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 4);
	}

	@Override
	protected void onHeldTick(HamonHeldActionInstance action, LivingEntity user, Power<?> context, HamonData hamon, int ticksHeld) {
		Level level = user.level();
		if (level.isClientSide()) {
			return;
		}
		HamonProjectileShieldEntity shield = level.getEntitiesOfClass(HamonProjectileShieldEntity.class,
				user.getBoundingBox().inflate(6.0D), entity -> entity.getOwnerEntity() == user)
				.stream()
				.findFirst()
				.orElse(null);
		if (shield == null) {
			shield = new HamonProjectileShieldEntity(level, user, SHIELD_WIDTH, SHIELD_HEIGHT);
			level.addFreshEntity(shield);
		}
		else {
			shield.refresh(user, SHIELD_WIDTH, SHIELD_HEIGHT);
		}
	}

	public static class ProjectileShieldInstance extends HamonActionRuntimeAbility.HamonHeldActionInstance {
		public ProjectileShieldInstance(EntityActionType ability) { super(ability); }
	}
}
