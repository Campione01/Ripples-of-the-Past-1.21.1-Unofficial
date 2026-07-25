package com.github.standobyte.jojoimpl.powers.pillarman.abilities;

import com.github.standobyte.jojo.init.ModParticles;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanMode;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;

public class PillarmanGiantCarthwheelPrisonAbility extends PillarmanActionAbility {
	private static final int HOLD_TO_FIRE_TICKS = 30;

	public PillarmanGiantCarthwheelPrisonAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, -1, PillarmanMode.HEAT, false, 125.0F, 0.0F, 0.0F, 100,
				GiantCarthwheelPrisonInstance::new);
		setButtonHoldPhase(ActionPhase.BUTTON_CHARGE);
		setDefaultPhaseLength(ActionPhase.BUTTON_CHARGE, HOLD_TO_FIRE_TICKS);
		setDefaultPhaseLength(ActionPhase.PERFORM, 1);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
	}

	public static class GiantCarthwheelPrisonInstance extends EntityActionInstance {
		public GiantCarthwheelPrisonInstance(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onSetPhase(ActionPhase newPhase) {
			if (ability instanceof PillarmanGiantCarthwheelPrisonAbility prisonAbility
					&& (newPhase == ActionPhase.BUTTON_CHARGE || newPhase == ActionPhase.PERFORM)) {
				userWalkSpeed = prisonAbility.heldWalkSpeed;
			}
			else {
				userWalkSpeed = 1.0F;
			}
		}

		@Override
		public void onButtonStopHold() {
			if (getPhase() == ActionPhase.BUTTON_CHARGE && getPhaseTick() < HOLD_TO_FIRE_TICKS) {
				forceStop();
				syncPhaseChanges();
			}
		}

		@Override
		public void actionTick() {
			if (getPhase() == ActionPhase.BUTTON_CHARGE && level().isClientSide()) {
				LivingEntity user = getPowerUser();
				if (user != null) {
					PillarmanActionAbility.auraEffect(user, ModParticles.HAMON_AURA_RED.get(), 6);
				}
			}
		}

		@Override
		public void actionPerformStart() {
			Level level = level();
			if (level.isClientSide() || !(ability instanceof PillarmanGiantCarthwheelPrisonAbility prisonAbility)) {
				return;
			}
			LivingEntity user = getPowerUser();
			if (user == null) {
				return;
			}
			Power<?> context = prisonAbility.getUserPower(user);
			if (!prisonAbility.consumeEnergy(context)) {
				return;
			}
			int n = 8;
			for (int i = 0; i < n; i++) {
				Vec2 rotOffsets = PillarmanErraticBlazeKingAbility.ErraticBlazeKingInstance
						.xRotYRotOffsets((double) i / (double) n * Math.PI * 2.0D, 2.0D);
				PillarmanErraticBlazeKingAbility.addVeinProjectile(level, user, rotOffsets.x, rotOffsets.y, 0.0D, -0.5D, 0.0D);
				PillarmanErraticBlazeKingAbility.addVeinProjectile(level, user, rotOffsets.x, rotOffsets.y + 180.0F, 0.0D, -0.5D, 0.0D);
				PillarmanErraticBlazeKingAbility.addVeinProjectile(level, user, rotOffsets.x, rotOffsets.y + 90.0F, 0.0D, -0.5D, 0.0D);
				PillarmanErraticBlazeKingAbility.addVeinProjectile(level, user, rotOffsets.x, rotOffsets.y - 90.0F, 0.0D, -0.5D, 0.0D);
			}
			user.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 600, 0, false, false, false));
			prisonAbility.setPillarmanFixedCooldown(context, 100);
			if (context != null && context.getCurTypeData() != null) {
				context.getCurTypeData().syncOnUpdate(user);
			}
		}
	}
}
