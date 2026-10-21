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
import rotp.core.powersystem.standpower.effect.StandEffectInstance;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class CrazyDAngeloRockPunchInput extends Ability {
	private static final float STAMINA_COST = 50.0F;

	public CrazyDAngeloRockPunchInput(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId);
		isSubAbility = true;
	}

	// 1.16 CRAZY_DIAMOND_RESTORE_TERRAIN.attackRecoveryFollowup(CRAZY_DIAMOND_ANGELO_ROCK, CRAZY_DIAMOND_FINISHER_PUNCH): a
	// follow-up of the finisher variation only, whatever finisher value made the heavy key that variation, given only while
	// its own checks passed (StandAction.canSetFollowUp)
	@Override
	public Ability replaceWithSubAbility(Power<?> context, AvailableAbilities abilities) {
		StandPower standPower = PowerClass.STAND.cast(context);
		if (abilities != null && canFollowRunningFinisher(standPower)) {
			abilities.replaceOtherAbilityWith(standPower, "restore_terrain", this);
		}
		return null;
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		if (!canFollowRunningFinisher(PowerClass.STAND.cast(context))) {
			return ConditionCheck.NEGATIVE;
		}
		return super.checkSpecificConditions(context);
	}

	@Override
	public void onClick(Level level, LivingEntity user, FriendlyByteBuf extraClientInput) {
		if (!level.isClientSide()) {
			StandPower standPower = StandPower.get(user);
			if (canFollowRunningFinisher(standPower)) {
				// 1.16 StandAction.onPerform paid the follow-up's cost on the click and ignored the result
				StandAbilityStamina.consume(this, standPower, STAMINA_COST, false);
				StandEffectInstance punchEffect = ModStandAbilities.EFFECT_CD_PUNCH_ANGELO_ROCK.get().create(level);
				standPower.userStandEffects.addEffect(punchEffect);
			}
		}
	}

	// 1.16 CrazyDiamondAngeloRock.checkSpecificConditions: abilities may break blocks (a game rule the client does not
	// receive, as in 1.16), no Angelo Rock of the user is under way (TURN_INTO_ANGELO_ROCK; the port's punch effect lasts
	// until the rock is formed), and the finisher has no follow-up yet (hasTaskWithNoModifiers)
	private static boolean canFollowRunningFinisher(@Nullable StandPower standPower) {
		LivingEntity user = standPower != null ? standPower.getUser() : null;
		return user != null && JojoModUtil.breakingBlocksEnabled(user.level())
				&& standPower.userStandEffects.getEffectOfType(ModStandAbilities.EFFECT_CD_PUNCH_ANGELO_ROCK.get()).isEmpty()
				&& CrazyDiamondHeavyPunchAbility.runningHeavyPunch(standPower, true) != null;
	}
}
