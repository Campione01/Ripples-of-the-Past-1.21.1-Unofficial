package com.github.standobyte.jojoimpl.powers.hamon.abilities;

import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojoimpl.powers.hamon.HamonUtil;
import com.github.standobyte.jojoimpl.powers.hamon.entity.HamonBubbleCutterEntity;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class HamonBubbleCutterAbility extends HamonActionRuntimeAbility {
	private final boolean gliding;

	public HamonBubbleCutterAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		this(abilityType, abilityId, false);
	}

	public HamonBubbleCutterAbility(AbilityType<?> abilityType, AbilityId abilityId, boolean gliding) {
		super(abilityType, abilityId, BubbleCutterInstance::new);
		this.gliding = gliding;
		setDefaultPhaseLength(ActionPhase.WINDUP, 6);
		setDefaultPhaseLength(ActionPhase.PERFORM, 4);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 3);
	}

	public boolean isGliding() {
		return gliding;
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		if (!check.isPositive()) {
			return check;
		}
		return context.getUser() instanceof LivingEntity user ? HamonSoapHelper.checkSoap(user) : ConditionCheck.NEGATIVE;
	}

	public static class BubbleCutterInstance extends HamonActionRuntimeAbility.HamonRuntimeActionInstance {
		public BubbleCutterInstance(EntityActionType ability) { super(ability); }

		@Override
		public void actionPerformStart() {
			Level level = level();
			LivingEntity user = getPowerUser();
			if (user == null) return;
			if (level.isClientSide()) {
				user.swing(InteractionHand.MAIN_HAND, true);
				return;
			}
			boolean gliding = ability instanceof HamonBubbleCutterAbility bubbleCutter && bubbleCutter.isGliding();
			if (HamonSoapHelper.consumeSoap(user, 20) == HamonSoapHelper.TookSoapFrom.NONE) {
				return;
			}
			int cutters = gliding ? 4 : 8;
			float inaccuracy = gliding ? 2.0F : 8.0F;
			float hamonPoints = (gliding ? 600.0F : 500.0F) / 10.0F;
			Vec3 shootingPos = null;
			for (int i = 0; i < cutters; i++) {
				HamonBubbleCutterEntity cutter = new HamonBubbleCutterEntity(user, level).setGliding(gliding);
				cutter.setHamonStatPoints(hamonPoints);
				cutter.shootFromRotation(user, 1.35F + user.getRandom().nextFloat() * 0.3F, inaccuracy);
				if (i == 0) {
					shootingPos = cutter.position();
				}
				level.addFreshEntity(cutter);
			}
			if (shootingPos != null) {
				HamonUtil.emitHamonSparkParticles(level, user instanceof Player player ? player : null, shootingPos, 0.75F);
			}
		}
	}
}

