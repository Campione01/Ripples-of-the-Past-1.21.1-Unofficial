package com.github.standobyte.jojoimpl.powers.vampirism.abilities;

import com.github.standobyte.jojo.client.sound.ClientsideSoundsHelper;
import com.github.standobyte.jojo.customobjects.explosion.CustomExplosion;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojoimpl.powers.hamon.HamonBlastExplosion;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonAbilityHelpers;
import com.github.standobyte.jojoimpl.powers.vampirism.VampirismData;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredHolder;

public class VampirismHamonSuicideAbility extends VampirismActionAbility {
	private static final int HOLD_TO_FIRE_TICKS = 100;

	public VampirismHamonSuicideAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, Integer.MAX_VALUE, 0.0F, HamonSuicideInstance::new);
		setDefaultPhaseLength(ActionPhase.WINDUP, HOLD_TO_FIRE_TICKS);
		setDefaultPhaseLength(ActionPhase.PERFORM, 1);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
		setIgnoresPerformerStun();
	}

	@Override
	protected float getWindupHoldToFireIndicatorLength() {
		return HOLD_TO_FIRE_TICKS;
	}

	@Override
	protected boolean requiresVampireFullPower() {
		return false;
	}

	@Override
	public boolean isAbilityAvailable(Power<?> context) {
		return super.isAbilityAvailable(context) && isVampireHamonUser(context);
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		if (!check.isPositive()) {
			return check;
		}
		return isVampireHamonUser(context) ? ConditionCheck.POSITIVE : ConditionCheck.NEGATIVE;
	}

	private static boolean isVampireHamonUser(Power<?> context) {
		VampirismData data = getVampirismData(context);
		return data != null && data.isVampireHamonUser();
	}

	public static class HamonSuicideInstance extends EntityActionInstance {
		public HamonSuicideInstance(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onSetPhase(ActionPhase newPhase) {
			super.onSetPhase(newPhase);
			if (newPhase == ActionPhase.WINDUP && level().isClientSide()) {
				LivingEntity user = getPowerUser();
				if (user != null) {
					ClientsideSoundsHelper.playLoopingActionSound(ModSoundEvents.HAMON_CONCENTRATION.get(), user,
							this, ActionPhase.WINDUP, 1.0F, 1.0F);
				}
			}
		}

		@Override
		public void actionTick() {
			if (getPhase() != ActionPhase.WINDUP) {
				return;
			}
			LivingEntity user = getPowerUser();
			if (user == null || level().isClientSide()) {
				return;
			}
			int tick = (int) getPhaseTick();
			if (tick == 0) {
				VampirismData data = getVampirismData(user, this);
				String character = data != null ? data.getPrevHamonCharacter() : "";
				DeferredHolder<SoundEvent, SoundEvent> breath = breathSound(character);
				if (breath != null) {
					JojoModUtil.sayVoiceLine(user, breath.get());
				}
			}
			if (tick % 10 == 5) {
				HamonAbilityHelpers.hamonHurt(user, 4.0F, user, user);
			}
			if (tick == 30) {
				user.addEffect(new MobEffectInstance(ModStatusEffects.HAMON_SPREAD, 100, 1, false, true));
			}
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
			VampirismData data = getVampirismData(user, this);
			float hamonStrength = data != null ? data.getHamonStrengthLevel() : 0.0F;
			HamonAbilityHelpers.hamonHurt(user, 200.0F, user, user);
			if (hamonStrength > 0) {
				Vec3 center = user.getBoundingBox().getCenter();
				HamonBlastExplosion explosion = new HamonBlastExplosion(level, user,
						HamonAbilityHelpers.hamonDamageSource(level, user, user),
						center.x, center.y, center.z, 6.0F);
				explosion.setHamonDamage(hamonStrength * 0.1F);
				CustomExplosion.explode(explosion);
			}
		}

		@Override
		public void onButtonStopHold() {
			if (getPhase() == ActionPhase.WINDUP) {
				forceStop();
			}
		}
	}

	private static DeferredHolder<SoundEvent, SoundEvent> breathSound(String character) {
		return switch (character) {
		case "jonathan" -> ModSoundEvents.BREATH_JONATHAN;
		case "zeppeli" -> ModSoundEvents.BREATH_ZEPPELI;
		case "joseph" -> ModSoundEvents.BREATH_JOSEPH;
		case "caesar" -> ModSoundEvents.BREATH_CAESAR;
		case "lisa_lisa" -> ModSoundEvents.BREATH_LISA_LISA;
		default -> null;
		};
	}
}
