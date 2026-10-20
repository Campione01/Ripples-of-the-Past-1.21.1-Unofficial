package rotp.core.impl.stands.crazydiamond;

import javax.annotation.Nullable;

import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.effect.StandEffectInstance;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.subsystems.target.ActionTargetRange;
import rotp.core.impl.stands.crazydiamond.CrazyDiamondHeavyPunchAbility.CrazyDiamondHeavyPunch;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility.StandEntityHeavyPunch;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class CrazyDMisshapingPunchInput extends Ability {
	private static final float STAMINA_COST = 50.0F;

	public CrazyDMisshapingPunchInput(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId);
		isSubAbility = true;
	}

	// 1.16 CRAZY_DIAMOND_FINISHER_PUNCH.attackRecoveryFollowup(CRAZY_DIAMOND_MISSHAPE_FACE, _ARMS, _LEGS): follow-ups of
	// the finisher variation only, whatever finisher value made the heavy key that variation
	@Override
	public Ability replaceWithSubAbility(Power<?> context, AvailableAbilities abilities) {
		StandPower standPower = PowerClass.STAND.cast(context);
		StandEntityHeavyPunch punch = CrazyDiamondHeavyPunchAbility.runningHeavyPunch(standPower, true);
		if (abilities != null && punch != null && CrazyDiamondHeavyPunchAbility.heavyKeyResolvesTo(abilities, punch)
				&& takesTaskTarget(punch, context.getUser())) {
			abilities.replaceOtherAbilityWith(standPower, "heavy_punch", this);
		}
		return null;
	}

	// 1.16 refused the click unless the crosshair was on such a target too (the checks ran on the clicked mouse target)
	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		StandPower standPower = PowerClass.STAND.cast(context);
		LivingEntity user = context.getUser();
		if (CrazyDiamondHeavyPunchAbility.runningHeavyPunch(standPower, true) == null || user == null
				|| CrazyDHealAbility.getCrosshairEntityTarget(user, user.level(), entity -> canMisshape(entity, user)).getType()
						!= TargetType.ENTITY) {
			return ConditionCheck.NEGATIVE;
		}
		return super.checkSpecificConditions(context);
	}

	// 1.16 CrazyDiamondMisshapeBodyPart.checkSpecificConditions: a living target whose Stand user is a player (mobs were
	// left as a TODO), not the user itself (Action.checkTarget)
	static boolean canMisshape(Entity target, LivingEntity user) {
		return target instanceof LivingEntity living && !target.is(user) && StandUtil.getStandUser(living) instanceof Player;
	}

	// 1.16 StandAction.canSetFollowUp ran those checks on the finisher's task target, within the action's range of the Stand
	private static boolean takesTaskTarget(StandEntityHeavyPunch punch, @Nullable LivingEntity user) {
		Entity target = user != null ? punch.getTaskTarget(user.level()).getEntity() : null;
		return target != null && canMisshape(target, user) && ActionTargetRange.isEntityWithinRange(
				ActionTargetRange.standPerformer(user), target, ActionTargetRange.DEFAULT_ENTITY_RANGE_SQ);
	}

	@Override
	public void onClick(Level level, LivingEntity user, FriendlyByteBuf extraClientInput) {
		if (!level.isClientSide()) {
			StandPower standPower = StandPower.get(user);
			StandEntityHeavyPunch punch = CrazyDiamondHeavyPunchAbility.runningHeavyPunch(standPower, true);
			if (punch != null) {
				// 1.16 StandAction.onPerform paid the follow-up's cost on the click and ignored the result
				StandAbilityStamina.consume(this, standPower, STAMINA_COST, false);
				if (punch instanceof CrazyDiamondHeavyPunch cdPunch && punch.getPerformer() instanceof StandEntity stand) {
					cdPunch.updateMisshapingTargetPart(stand, user);
				}
				StandEffectInstance punchEffect = ModStandAbilities.EFFECT_CD_PUNCH_MISSHAPING.get().create(level);
				standPower.userStandEffects.addEffect(punchEffect);
			}
		}
	}

}
