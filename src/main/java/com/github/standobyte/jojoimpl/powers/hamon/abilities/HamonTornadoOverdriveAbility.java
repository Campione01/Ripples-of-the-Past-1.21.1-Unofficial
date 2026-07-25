package com.github.standobyte.jojoimpl.powers.hamon.abilities;

import java.util.List;

import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;
import com.github.standobyte.jojoimpl.powers.hamon.HamonUtil;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class HamonTornadoOverdriveAbility extends HamonActionRuntimeAbility {
	private static final float BASE_DAMAGE = 0.6F;

	public HamonTornadoOverdriveAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, TornadoOverdriveInstance::new);
		setDefaultPhaseLength(ActionPhase.WINDUP, 12);
		setDefaultPhaseLength(ActionPhase.PERFORM, 6);
		setButtonHoldPhase(ActionPhase.PERFORM);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 5);
	}

	@Override
	protected void onHeldTick(HamonHeldActionInstance action, LivingEntity user, Power<?> context, HamonData hamon, int ticksHeld) {
		user.fallDistance = 0.0F;
		Vec3 movement = user.getDeltaMovement();
		if (!user.level().isClientSide()) {
			AABB area = user.getBoundingBox().expandTowards(movement).inflate(1.0D);
			float damage = BASE_DAMAGE;
			double gravity = user.getAttributeValue(Attributes.GRAVITY);
			if (gravity > 0.0D && movement.y < -gravity) {
				damage *= (float) ((-movement.y / gravity) * 0.15D);
			}
			List<Entity> targets = user.level().getEntities(user, area, entity -> entity.isAlive() && entity.isPickable());
			boolean gavePoints = false;
			for (Entity entity : targets) {
				if (entity instanceof LivingEntity livingTarget && HamonAbilityHelpers.hamonHurt(livingTarget, user, damage)) {
					gavePoints = true;
				}
			}
			if (gavePoints) {
				hamon.hamonPointsFromAction(HamonData.HamonStat.STRENGTH, getHeldTickEnergyCost(context, ticksHeld));
				hamon.syncOnUpdate(user);
			}
		}
		if (user.isShiftKeyDown()) {
			user.setDeltaMovement(0.0D, movement.y < 0.0D ? movement.y * 1.05D : 0.0D, 0.0D);
		}
		HamonUtil.emitHamonSparkParticles(user.level(), user instanceof Player player ? player : null, user.position(),
				hamon.getHamonDamageMultiplier() / HamonData.MAX_HAMON_STRENGTH_MULTIPLIER * 0.25F);
	}

	public static class TornadoOverdriveInstance extends HamonActionRuntimeAbility.HamonHeldActionInstance {
		public TornadoOverdriveInstance(EntityActionType ability) { super(ability); }
	}
}
