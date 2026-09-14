package rotp.core.impl.powers.pillarman.abilities;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.impl.powers.pillarman.PillarmanMode;
import rotp.core.impl.powers.pillarman.PillarmanPowerType;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class PillarmanStoneFormAbility extends PillarmanActionAbility {
	private static final ActionAnimIdentifier[] STONE_FORM_ANIMS = {
			ActionAnimIdentifier.getOrCreate("pillarman_stone_form", 0, false),
			ActionAnimIdentifier.getOrCreate("pillarman_stone_form", 1, false),
			ActionAnimIdentifier.getOrCreate("pillarman_stone_form", 2, false) };

	public PillarmanStoneFormAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, 1, PillarmanMode.NONE, true, 0.0F, StoneFormInstance::new);
		setButtonHoldPhase(ActionPhase.BUTTON_CHARGE);
		setDefaultPhaseLength(ActionPhase.BUTTON_CHARGE, 40);
		setDefaultPhaseLength(ActionPhase.PERFORM, 1);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
		setIgnoresPerformerStun();
	}

	@Override
	public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
		return STONE_FORM_ANIMS[Math.floorMod(action.id, STONE_FORM_ANIMS.length)];
	}

	public static class StoneFormInstance extends EntityActionInstance {
		public StoneFormInstance(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onSetPhase(ActionPhase newPhase) {
			userWalkSpeed = newPhase == ActionPhase.BUTTON_CHARGE ? 0.5F : 1.0F;
		}

		@Override
		public void onButtonStopHold() {
			if (getPhase() == ActionPhase.BUTTON_CHARGE && getPhaseTick() < 40) {
				forceStop();
				syncPhaseChanges();
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
			PlayerPower.getPowerData(user, PillarmanPowerType.PILLAR_MAN).ifPresent(data -> {
				boolean stoneFormEnabled = data.toggleStoneForm();
				if (stoneFormEnabled) {
					data.setStoneFormPose(user.getRandom().nextInt(STONE_FORM_ANIMS.length));
				}
				data.setBladesVisible(false);
				data.syncOnUpdate(user);
			});
		}
	}
}
