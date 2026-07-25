package com.github.standobyte.jojoimpl.powers.hamon.abilities;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojo.subsystems.target.ActionTarget.TargetType;
import com.github.standobyte.jojoimpl.powers.hamon.HamonPowerType;
import com.github.standobyte.jojoimpl.powers.hamon.HamonUtil;
import com.github.standobyte.jojoimpl.powers.hamon.entity.LeavesGliderEntity;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class HamonLifeMagnetismAbility extends HamonActionRuntimeAbility {

	public HamonLifeMagnetismAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, LifeMagnetismInstance::new);
		setDefaultPhaseLength(ActionPhase.WINDUP, 6);
		setDefaultPhaseLength(ActionPhase.PERFORM, 6);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 4);
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		if (!check.isPositive()) {
			return check;
		}
		if (!(context.getUser() instanceof LivingEntity user) || !hasLeavesTargetOrItem(user)) {
			return ConditionCheck.createNegative("leaves");
		}
		if (!canUseWithHeldItems(user)) {
			return ConditionCheck.createNegative("hand");
		}
		return ConditionCheck.POSITIVE;
	}

	private static boolean hasLeavesTargetOrItem(LivingEntity user) {
		ActionTarget target = getAimTarget(user);
		if (target.getType() == TargetType.BLOCK && isLeavesBlock(user.level(), target.getBlockPos())) {
			return true;
		}
		return !findLeavesItem(user).isEmpty();
	}

	private static boolean canUseWithHeldItems(LivingEntity user) {
		return user.getMainHandItem().isEmpty() || isLeavesItem(user.getMainHandItem()) || isLeavesItem(user.getOffhandItem());
	}

	private static boolean isLeavesBlock(Level level, BlockPos pos) {
		return level.getBlockState(pos).getBlock() instanceof LeavesBlock;
	}

	private static ItemStack findLeavesItem(LivingEntity user) {
		ItemStack item = user.getMainHandItem();
		if (isLeavesItem(item)) {
			return item;
		}
		item = user.getOffhandItem();
		if (isLeavesItem(item)) {
			return item;
		}
		if (user instanceof Player player) {
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				item = player.getInventory().getItem(i);
				if (isLeavesItem(item)) {
					return item;
				}
			}
		}
		return ItemStack.EMPTY;
	}

	public static boolean isLeavesItem(ItemStack item) {
		return !item.isEmpty() && item.getItem() instanceof BlockItem blockItem
				&& blockItem.getBlock() instanceof LeavesBlock;
	}

	private static ActionTarget getAimTarget(LivingEntity user) {
		var aim = LivingComponentAction.getAim(user);
		return aim != null ? aim.getTarget() : ActionTarget.EMPTY;
	}

	public static class LifeMagnetismInstance extends HamonActionRuntimeAbility.HamonRuntimeActionInstance {
		public LifeMagnetismInstance(EntityActionType ability) { super(ability); }

		@Override
		public void actionPerformStart() {
			Level level = level();
			if (level.isClientSide()) return;
			LivingEntity user = getPowerUser();
			if (user == null) return;
			ActionTarget target = getAimTarget(user);
			if (target.getType() == TargetType.BLOCK && isLeavesBlock(level, target.getBlockPos())) {
				BlockPos blockPos = target.getBlockPos();
				BlockState leavesBlock = level.getBlockState(blockPos);
				level.destroyBlock(blockPos, false, user);
				summonGlider(level, user, Vec3.atBottomCenterOf(blockPos), false, leavesBlock);
				return;
			}
			ItemStack leavesItem = findLeavesItem(user);
			if (!leavesItem.isEmpty() && leavesItem.getItem() instanceof BlockItem blockItem) {
				summonGlider(level, user, user.position(), true, blockItem.getBlock().defaultBlockState());
				if (!(user instanceof Player player) || !player.getAbilities().instabuild) {
					leavesItem.shrink(1);
				}
			}
		}

		private void summonGlider(Level level, LivingEntity user, Vec3 pos, boolean mount, BlockState leavesBlock) {
			LeavesGliderEntity glider = new LeavesGliderEntity(level);
			glider.moveTo(pos.x, pos.y, pos.z, user.getYRot(), user.getXRot());
			glider.setLeavesBlock(leavesBlock);
			float energy = PlayerPower.getPowerData(user, HamonPowerType.HAMON)
					.map(hamon -> Math.min(hamon.getEnergy(), LeavesGliderEntity.MAX_ENERGY))
					.orElse(LeavesGliderEntity.MAX_ENERGY);
			glider.setEnergy(energy);
			level.addFreshEntity(glider);
			if (mount) {
				user.startRiding(glider);
			}
			HamonUtil.emitHamonSparkParticles(level, null, new Vec3(pos.x, glider.getY(1.0D), pos.z), 0.1F);
		}
	}
}

