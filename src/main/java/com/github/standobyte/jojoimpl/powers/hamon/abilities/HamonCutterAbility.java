package com.github.standobyte.jojoimpl.powers.hamon.abilities;

import com.github.standobyte.jojo.init.ModItems;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojoimpl.powers.hamon.entity.HamonCutterEntity;
import com.github.standobyte.jojoimpl.powers.hamon.HamonUtil;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class HamonCutterAbility extends HamonActionRuntimeAbility {

	public HamonCutterAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, CutterInstance::new);
		setDefaultPhaseLength(ActionPhase.WINDUP, 4);
		setDefaultPhaseLength(ActionPhase.PERFORM, 4);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 3);
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		if (!check.isPositive()) {
			return check;
		}
		if (!(context.getUser() instanceof LivingEntity user) || getUsableItem(user).isEmpty()) {
			return ConditionCheck.createNegative("potion");
		}
		return ConditionCheck.POSITIVE;
	}

	private static ItemStack getUsableItem(LivingEntity user) {
		ItemStack item = user.getMainHandItem();
		if (!canUse(item)) {
			item = user.getOffhandItem();
		}
		return canUse(item) ? item : ItemStack.EMPTY;
	}

	public static boolean canUse(ItemStack item) {
		return !item.isEmpty() && (item.getItem() instanceof PotionItem || item.is(ModItems.SOAP.get()));
	}

	public static class CutterInstance extends HamonActionRuntimeAbility.HamonRuntimeActionInstance {
		public CutterInstance(EntityActionType ability) { super(ability); }

		@Override
		public void actionPerformStart() {
			Level level = level();
			LivingEntity user = getPowerUser();
			if (user == null) return;
			if (level.isClientSide()) {
				user.swing(InteractionHand.MAIN_HAND, true);
				return;
			}
			ItemStack sourceItem = getUsableItem(user);
			if (sourceItem.isEmpty()) {
				return;
			}
			Vec3 shootingPos = null;
			for (int i = 0; i < 8; i++) {
				HamonCutterEntity cutter = new HamonCutterEntity(user, level, sourceItem);
				if (sourceItem.is(ModItems.SOAP.get())) {
					cutter.setColor(0x98DAC0);
				}
				cutter.setHamonStatPoints(400.0F / 8.0F);
				cutter.shootFromRotation(user, 1.35F + user.getRandom().nextFloat() * 0.3F, 10.0F);
				if (i == 0) {
					shootingPos = cutter.position();
				}
				level.addFreshEntity(cutter);
			}
			if (shootingPos != null) {
				HamonUtil.emitHamonSparkParticles(level, user instanceof Player player ? player : null, shootingPos, 0.75F);
			}
			if (!(user instanceof Player player) || !player.getAbilities().instabuild) {
				sourceItem.shrink(1);
				ItemStack glassBottle = new ItemStack(Items.GLASS_BOTTLE);
				if (user instanceof Player player) {
					if (!player.addItem(glassBottle)) {
						player.spawnAtLocation(glassBottle);
					}
				}
				else {
					user.spawnAtLocation(glassBottle);
				}
			}
		}
	}
}

