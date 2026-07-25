package com.github.standobyte.jojoimpl.powers.hamon.abilities;

import java.util.List;
import java.util.OptionalInt;

import com.github.standobyte.jojo.client.sound.ClientsideSoundsHelper;
import com.github.standobyte.jojo.client.sound.HamonSparksLoopSound;
import com.github.standobyte.jojo.init.ModParticles;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.standpower.StandUtil;
import com.github.standobyte.jojo.subsystems.entityglow.EntityGlowChannel;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;
import com.github.standobyte.jojoimpl.powers.hamon.ModHamonSkills;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.phys.Vec3;

public class HamonDetectorAbility extends HamonActionRuntimeAbility {
	private static final int HAMON_COLOR = 0xFFFF00;
	private static final OptionalInt COLOR = OptionalInt.of(HAMON_COLOR);

	public HamonDetectorAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, DetectorInstance::new);
		setDefaultPhaseLength(ActionPhase.WINDUP, 8);
		setDefaultPhaseLength(ActionPhase.PERFORM, 8);
		setButtonHoldPhase(ActionPhase.PERFORM);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 4);
	}

	public static class DetectorInstance extends HamonActionRuntimeAbility.HamonHeldActionInstance {
		public DetectorInstance(EntityActionType ability) { super(ability); }

		@Override
		public void onSetPhase(ActionPhase newPhase) {
			super.onSetPhase(newPhase);
			if (newPhase == ActionPhase.PERFORM && level().isClientSide()) {
				LivingEntity user = getPowerUser();
				if (user != null) {
					ClientsideSoundsHelper.playLoopingActionSound(ModSoundEvents.HAMON_DETECTOR.get(), user, this,
							ActionPhase.PERFORM, 1.0F, 1.0F, 15);
				}
			}
		}

		@Override
		public void actionPerformStart() {}
	}

	@Override
	protected void onHeldTick(HamonHeldActionInstance action, LivingEntity user, Power<?> context, HamonData hamon, int ticksHeld) {
		if (!(ticksHeld < 160 || ticksHeld % 20 == 0)) {
			return;
		}
		float tickEnergyCost = getHeldTickEnergyCost(context, ticksHeld);
		double controlRatio = (double) hamon.getHamonControlLevel() / (double) HamonData.MAX_STAT_LEVEL
				* hamon.getActionEfficiency(tickEnergyCost, false, ModHamonSkills.DETECTOR.get(), user);
		double radius = ticksHeld * (controlRatio * 0.8D + 0.2D);
		double maxRadius = 8.0D + controlRatio * 24.0D;
		List<LivingEntity> entitiesAround = user.level().getEntitiesOfClass(LivingEntity.class,
				user.getBoundingBox().inflate(Math.min(radius, maxRadius)),
				entity -> isDetectorTarget(user, entity));
		if (user.level().isClientSide()) {
			applyClientDetectorFeedback(action, user, entitiesAround);
		}
		else if (!entitiesAround.isEmpty()) {
			hamon.hamonPointsFromAction(HamonData.HamonStat.CONTROL, Math.min(tickEnergyCost, hamon.getEnergy()));
		}
	}

	private static boolean isDetectorTarget(LivingEntity user, LivingEntity entity) {
		if (entity == user || !entity.isAlive() || entity instanceof ArmorStand || entity instanceof AbstractGolem) {
			return false;
		}
		return StandUtil.getStandUser(entity) != user;
	}

	private static void applyClientDetectorFeedback(HamonHeldActionInstance action, LivingEntity user, List<LivingEntity> entitiesAround) {
		if (user == Minecraft.getInstance().player) {
			for (Entity entity : entitiesAround) {
				EntityGlowChannel.HAMON_DETECTOR.apply(entity, COLOR, 80);
			}
		}
		Vec3 pos = user.position().add(0.0D, user.getBbHeight() * 0.5D, 0.0D);
		HamonSparksLoopSound.playSparkSound(user, pos, 1.0F);
		user.level().addParticle(ModParticles.HAMON_SPARK.get(), user.getX(), user.getY(0.5D), user.getZ(),
				user.getRandom().nextGaussian() * 0.02D,
				user.getRandom().nextGaussian() * 0.02D,
				user.getRandom().nextGaussian() * 0.02D);
	}
}
