package rotp.core.impl.stands.magiciansred;

import rotp.core.client.ClientGlobals;
import rotp.core.client.sound.ClientsideSoundsHelper;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.standpower.StandInstance.StandPart;

import rotp.core.mechanics.resolve.ResolveModeEffect;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class MagiciansRedFlameBurstAbility extends StandEntityAbility {

	private static final ActionAnimIdentifier FLAME_BURST_ANIM = ActionAnimIdentifier.getOrCreate("flame_burst", false);
	private static final float STAMINA_COST_TICK = 3F;

	public MagiciansRedFlameBurstAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, FlameBurstShot::new);
		partsRequired(StandPart.MAIN_BODY);
		setButtonHoldPhase(ActionPhase.PERFORM);
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		return check.isPositive() ? StandAbilityStamina.check(context, STAMINA_COST_TICK) : check;
	}

	@Override
	public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
		return FLAME_BURST_ANIM;
	}

	public static class FlameBurstShot extends EntityActionInstance {

		public FlameBurstShot(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onActionSet(EntityActionInstance prevAction) {
			setStandOffset(0.0, 0.5, StandOffsetFromUser.Rotations.BODY, false);
		}

		@Override
		public void actionTick() {
			if (getPhase() != ActionPhase.PERFORM) {
				return;
			}
			Level level = level();
			if (level.isClientSide()) {
				if (getPerformer() instanceof StandEntity stand && ClientGlobals.canHearStand(stand)) {
					level.playLocalSound(stand, ClientsideSoundsHelper.withStandSkin(
							ModSoundEvents.MAGICIANS_RED_FIRE_BLAST.get(), stand),
							stand.getSoundSource(), 0.5F, 0.3F + stand.getRandom().nextFloat() * 0.4F);
				}
				return;
			}
			LivingEntity user = getPowerUser();
			if (user == null || !(getPerformer() instanceof StandEntity stand)) {
				return;
			}
			StandPower standPower = StandPower.get(user);
			if (!StandAbilityStamina.consume(ability, standPower, STAMINA_COST_TICK, true)) {
				startRecovery();
				return;
			}
			MRFlameEntity flame = new MRFlameEntity(stand, level);
			flame.setShootingPosOf(stand);
			flame.shootFromRotation(stand,
					stand.getXRot() + (stand.getRandom().nextFloat() - 0.5F) * 10F,
					stand.getYRot() + (stand.getRandom().nextFloat() - 0.5F) * 10F,
					0, flameVelocity(stand, user), 0.0F);
			addProjectileWithStandStats(flame);
		}

		private static float flameVelocity(StandEntity stand, LivingEntity user) {
			float velocity = (float) stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) / 5F;
			if (ResolveModeEffect.getEffectiveResolveLevel(user) >= 3) {
				velocity *= 2F;
			}
			return velocity;
		}

		@Override
		public void onButtonStopHold() {
			if (getPhase() != ActionPhase.RECOVERY) {
				startRecovery();
			}
		}
	}
}
