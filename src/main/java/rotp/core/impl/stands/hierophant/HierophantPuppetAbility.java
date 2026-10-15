package rotp.core.impl.stands.hierophant;

import java.util.List;

import javax.annotation.Nullable;

import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.effect.StandEffectInstance;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class HierophantPuppetAbility extends StandEntityAbility {

	public HierophantPuppetAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, PuppetingAction::new);
	}

	@Override
	public HeldInput onKeyPress(Level level, LivingEntity user, FriendlyByteBuf extraClientInput,
			InputMethod inputMethod, float clickHoldResolveTime, BufferingState bufferingState) {
		if (!level.isClientSide()) {
			StandPower power = PowerClass.STAND.get(user);
			if (power != null) {
				List<HierophantPuppetEffect> effects = power.userStandEffects
						.getEffectsOfType(ModStandAbilities.EFFECT_HG_PUPPET.get())
						.toList();
				if (!effects.isEmpty()) {
					effects.forEach(StandEffectInstance::remove);
					return null;
				}
			}
		}

		return super.onKeyPress(level, user, extraClientInput, inputMethod, clickHoldResolveTime, bufferingState);
	}

	public static class PuppetingAction extends EntityActionInstance {
		public LivingEntity targetEntity;

		public PuppetingAction(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onActionSet(@Nullable EntityActionInstance prevAction) {
			keepStandAimedAtTarget();
			if (standRotationTarget != null && standRotationTarget.getEntity() instanceof LivingEntity targetLiving) {
				this.targetEntity = targetLiving;
			}
		}

		@Override
		public void actionPerformEnd() {
			Level level = level();
			if (!level.isClientSide()) {
				LivingEntity standUser = getPowerUser();
				StandPower power = StandPower.get(standUser);
				if (power != null) {
					power.userStandEffects.getEffectsOfType(ModStandAbilities.EFFECT_HG_PUPPET.get())
							.forEach(StandEffectInstance::remove);

					if (targetEntity != null) {
						HierophantPuppetEffect newEffect = ModStandAbilities.EFFECT_HG_PUPPET.get().create(level);
						power.userStandEffects.addEffect(newEffect.withTarget(targetEntity));
					}
				}
			}
		}
	}

	public static boolean hasPuppetUnderControl(StandPower userPower) {
		return userPower != null && userPower.userStandEffects
				.getEffectOfType(ModStandAbilities.EFFECT_HG_PUPPET.get())
				.isPresent();
	}

	protected String releaseSpriteName;
	@Override
	protected void initVariationAssets() {
		this.releaseSpriteName = this.spriteName + "_release";
	}

	@Override
	public String getSpriteName(Power<?> context) {
		if (hasPuppetUnderControl(PowerClass.STAND.cast(context))) {
			return releaseSpriteName;
		}
		return super.getSpriteName(context);
	}

	@Override
	public Component getName(Power<?> context) {
		if (hasPuppetUnderControl(PowerClass.STAND.cast(context))) {
			return abilityName(context, ".release");
		}
		return super.getName(context);
	}
}
