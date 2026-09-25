package rotp.core.impl.powers.hamon.abilities;

import javax.annotation.Nullable;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
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
		// 1.16 holdType: startedHolding raised the shield on the press, no windup.
		setDefaultPhaseLength(ActionPhase.WINDUP, 0);
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
			// 1.16 startedHolding made one shield per hold; once broken it stays down until the next press
			if (action instanceof ProjectileShieldInstance shieldAction) {
				if (shieldAction.shieldSpawned) {
					return;
				}
				shieldAction.shieldSpawned = true;
			}
			shield = new HamonProjectileShieldEntity(level, user, SHIELD_WIDTH, SHIELD_HEIGHT);
			level.addFreshEntity(shield);
		}
		else {
			shield.refresh(user, SHIELD_WIDTH, SHIELD_HEIGHT);
		}
	}

	/** The user's Hamon while it holds Projectile Shield, else null. */
	@Nullable
	public static HamonData getHoldingShieldHamon(LivingEntity user) {
		EntityActionInstance action = LivingComponentAction.getCurEntityAction(user);
		if (action instanceof ProjectileShieldInstance && action.getPhase() == ActionPhase.PERFORM
				&& action.ability instanceof HamonProjectileShieldAbility shieldAbility) {
			return shieldAbility.getHamonData(shieldAbility.getUserPower(user));
		}
		return null;
	}

	public static class ProjectileShieldInstance extends HamonActionRuntimeAbility.HamonHeldActionInstance {
		private boolean shieldSpawned;

		public ProjectileShieldInstance(EntityActionType ability) { super(ability); }
	}
}
