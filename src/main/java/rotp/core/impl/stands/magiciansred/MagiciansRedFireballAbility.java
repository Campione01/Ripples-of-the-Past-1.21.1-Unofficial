package rotp.core.impl.stands.magiciansred;

import rotp.core.powersystem.standpower.StandInstance.StandPart;

import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class MagiciansRedFireballAbility extends StandEntityAbility {

	private static final ActionAnimIdentifier FIREBALL_ANIM = ActionAnimIdentifier.getOrCreate("fireball", false);
	private static final int STAMINA_COST = 75;
	private static final float SHOT_VELOCITY = 2.0F;
	private static final float SHOT_INACCURACY = 2.0F;

	public MagiciansRedFireballAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, FireballShot::new);
		partsRequired(StandPart.MAIN_BODY);
		setDefaultPhaseLength(ActionPhase.PERFORM, 3);
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		return check.isPositive() ? StandAbilityStamina.check(context, STAMINA_COST) : check;
	}

	@Override
	public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
		return FIREBALL_ANIM;
	}

	public static class FireballShot extends EntityActionInstance {

		public FireballShot(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void actionPerformStart() {
			Level level = level();
			if (level.isClientSide()) {
				return;
			}
			LivingEntity user = getPowerUser();
			if (user == null || !(getPerformer() instanceof StandEntity stand)) {
				return;
			}
			StandPower standPower = StandPower.get(user);
			if (!StandAbilityStamina.consumeOrMessage(ability, standPower, user, STAMINA_COST)) {
				return;
			}
			MRFireballEntity fireball = new MRFireballEntity(stand, level);
			fireball.setShootingPosOf(stand);
			fireball.shootFromRotation(stand, stand.getXRot(), stand.getYRot(), 0, SHOT_VELOCITY, SHOT_INACCURACY);
			addProjectileWithStandStats(fireball);
			StandUtil.playStandEntitySound(stand, ModSoundEvents.MAGICIANS_RED_FIREBALL.get(), 1.0F, 1.0F);
		}
	}
}
