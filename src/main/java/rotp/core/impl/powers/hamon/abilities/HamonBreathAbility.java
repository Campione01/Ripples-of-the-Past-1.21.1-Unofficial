package rotp.core.impl.powers.hamon.abilities;

import javax.annotation.Nullable;

import rotp.core.client.sound.ClientsideSoundsHelper;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.util.functions.JojoModUtil;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

public class HamonBreathAbility extends HamonActionRuntimeAbility {

	public HamonBreathAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, BreathInstance::new);
		setDefaultPhaseLength(ActionPhase.WINDUP, 0);
		setButtonHoldPhase(ActionPhase.PERFORM);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
		// 1.16 HamonBreath#playVoiceLine overrode the sneak rule away
		setPlaysVoiceLineOnSneak();
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		if (!check.isPositive()) {
			return check;
		}
		if (context == null || !(context.getUser() instanceof LivingEntity user)) {
			return ConditionCheck.NEGATIVE;
		}
		return user.getAirSupply() >= user.getMaxAirSupply() ? ConditionCheck.POSITIVE : ConditionCheck.createNegative("no_air");
	}

	@Override
	protected boolean changesAuraColor() {
		return false;
	}

	// 1.16 HamonBreath.checkSpecificConditions ran on every held tick too: losing air ends the breath.
	@Override
	protected ConditionCheck checkHeldSpecificConditions(EntityActionInstance action, Power<?> context) {
		ConditionCheck check = super.checkHeldSpecificConditions(action, context);
		if (!check.isPositive()) {
			return check;
		}
		LivingEntity user = context.getUser();
		return user != null && user.getAirSupply() >= user.getMaxAirSupply()
				? ConditionCheck.POSITIVE : ConditionCheck.createNegative("no_air");
	}

	// 1.16 HamonBreath#playVoiceLine: the line only at 0 Hamon energy, with no repeat guard, cutting off the last one.
	// The energy is the one seen on the press: a breath tick may already have added some by now.
	@Override
	protected void playHamonShout(LivingEntity user, HamonData hamon) {
		if (user.level().isClientSide()
				|| !(LivingComponentAction.getCurEntityAction(user) instanceof BreathInstance breath)
				|| breath.hamonAbility() != this || !breath.pressedAtNoEnergy) {
			return;
		}
		SoundEvent shout = getHamonShout(hamon);
		if (shout != null) {
			// 1.16 volume 1 - 0.5 * energy / max energy, at 0 energy
			JojoModUtil.sayVoiceLine(user, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(shout), 1.0F, 1.0F, 0, true);
		}
	}

	// 1.16 HamonEnergySound: after release the volume dropped by 0.1 per tick
	public static final float BREATH_SOUND_FADE_STEP = 0.1F;

	// 1.16 HamonEnergySound: while breathing, volume = energy / max energy (at least 1); 1 with no Hamon data
	public static float breathSoundVolume(@Nullable HamonData hamon) {
		if (hamon == null) {
			return 1.0F;
		}
		return Mth.clamp(hamon.getEnergy() / Math.max(hamon.getMaxEnergy(), 1.0F), 0.0F, 1.0F);
	}

	public static class BreathInstance extends HamonActionRuntimeAbility.HamonHeldActionInstance {
		public BreathInstance(EntityActionType ability) { super(ability); }

		private boolean pressEnergyChecked;
		private boolean pressedAtNoEnergy;

		// 1.16 checked the energy on the press, before any breath tick; once, so a restored action keeps it
		@Override
		public void onActionSet(@Nullable EntityActionInstance prevAction) {
			super.onActionSet(prevAction);
			if (pressEnergyChecked || level().isClientSide()) {
				return;
			}
			pressEnergyChecked = true;
			HamonActionRuntimeAbility hamonAbility = hamonAbility();
			LivingEntity user = getPowerUser();
			HamonData hamon = hamonAbility != null && user != null
					? hamonAbility.getHamonData(hamonAbility.getUserPower(user)) : null;
			pressedAtNoEnergy = hamon != null && hamon.getEnergy() <= 0.0F;
		}

		@Override
		public void onSetPhase(ActionPhase newPhase) {
			super.onSetPhase(newPhase);
			if (newPhase == ActionPhase.PERFORM && level().isClientSide()) {
				LivingEntity user = getPowerUser();
				if (user != null) {
					ClientsideSoundsHelper.playLoopingActionSound(ModSoundEvents.HAMON_CONCENTRATION.get(), user, this,
							ActionPhase.PERFORM, 1.0F, 1.0F, 0, BREATH_SOUND_FADE_STEP, () -> breathSoundVolume());
				}
			}
		}

		// Read each tick by the breath sound, so it swells with the energy bar
		public float breathSoundVolume() {
			HamonActionRuntimeAbility hamonAbility = hamonAbility();
			LivingEntity user = getPowerUser();
			HamonData hamon = hamonAbility != null && user != null
					? hamonAbility.getHamonData(hamonAbility.getUserPower(user)) : null;
			return HamonBreathAbility.breathSoundVolume(hamon);
		}
	}
}
