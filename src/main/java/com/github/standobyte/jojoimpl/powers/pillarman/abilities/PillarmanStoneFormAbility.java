package com.github.standobyte.jojoimpl.powers.pillarman.abilities;

import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.entityaction.ActionAnimIdentifier;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanMode;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanPowerType;

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
