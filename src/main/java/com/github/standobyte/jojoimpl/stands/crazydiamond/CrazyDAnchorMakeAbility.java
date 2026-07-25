package com.github.standobyte.jojoimpl.stands.crazydiamond;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.github.standobyte.jojo.init.ModItemDataComponents;
import com.github.standobyte.jojo.init.ModItems;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.ActionAnimIdentifier;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance.StandPart;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.StandUtil;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntityAbility;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandStatFormulas;
import com.github.standobyte.jojo.subsystems.itemtracking.ItemTracker;
import com.github.standobyte.jojo.subsystems.itemtracking.ItemTracking;
import com.github.standobyte.jojo.subsystems.itemtracking.OriginalItemPosComponent;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojo.subsystems.target.AimingEntity;
import com.github.standobyte.jojo.subsystems.target.HitResultUtil;
import com.github.standobyte.jojo.subsystems.target.ActionTarget.TargetType;
import com.github.standobyte.jojo.util.functions.ItemUtil;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojoimpl.stands._entitybase.StandAbilityStamina;
import com.github.standobyte.jojoimpl.stands._entitybase.StandEntityPunchAbility;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class CrazyDAnchorMakeAbility extends StandEntityAbility {
	private static final float STAMINA_COST = 25F;
	private static final double BLOCK_TARGET_RANGE = 10.0D;
	private static final ActionAnimIdentifier FINISHER_ANIM = ActionAnimIdentifier.getOrCreate("finisher", false);

	public CrazyDAnchorMakeAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, AnchorMake::new);
		partsRequired(StandPart.ARMS);
		setDefaultPhaseLength(ActionPhase.WINDUP, 10);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 5);
	}

	@Override
	public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
		return FINISHER_ANIM;
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		LivingEntity user = context.getUser();
		if (user == null) {
			return ConditionCheck.NEGATIVE;
		}
		LivingEntity aimingEntity = getAnchorAimingEntity(user);
		ActionTarget aimedTarget = getSyncedLookTarget(aimingEntity);
		if (!aimedTarget.isEmpty(aimingEntity.level())
				&& !HitResultUtil.isTargetWithinRange(aimedTarget, aimingEntity, aimingEntity.level(),
						BLOCK_TARGET_RANGE, BLOCK_TARGET_RANGE)) {
			return ConditionCheck.createNegative("target_too_far");
		}
		ActionTarget target = findAnchorMakeTarget(user);
		if (target.getType() != TargetType.BLOCK) {
			return ConditionCheck.createNegative("block_target");
		}
		BlockPos blockPos = target.getBlockPos();
		BlockState blockState = user.level().getBlockState(blockPos);
		if (blockState.isAir()) {
			return ConditionCheck.createNegative("block_target");
		}
		StandPower standPower = StandPower.get(user);
		StandEntity stand = standPower != null ? standPower.getSummonedStandEntity() : null;
		if (stand != null && !StandStatFormulas.isBlockBreakable(stand.getAttackDamage(), blockState, user.level(), blockPos)) {
			return ConditionCheck.createNegative("stand_cant_break_block");
		}
		ConditionCheck check = super.checkSpecificConditions(context);
		return check.isPositive() ? StandAbilityStamina.check(context, STAMINA_COST) : check;
	}

	@Override
	public void initActionFromConfig(EntityActionInstance action, Level level, LivingEntity powerUser, LivingEntity performer) {
		super.initActionFromConfig(action, level, powerUser, performer);
		if (action instanceof AnchorMake anchorMake && powerUser != null) {
			ActionTarget target = findAnchorMakeTarget(powerUser);
			anchorMake.setActionTarget(target);
			if (target.getType() == TargetType.BLOCK && !target.isEmpty(level)) {
				action.standRotationTarget = target.copy();
				action.aimAs = AimingEntity.STAND;
			}
		}
	}

	private static ActionTarget findAnchorMakeTarget(LivingEntity user) {
		LivingEntity aimingEntity = getAnchorAimingEntity(user);
		StandPower standPower = StandPower.get(user);
		StandEntity stand = standPower != null ? standPower.getSummonedStandEntity() : null;
		ActionTarget syncedTarget = getSyncedLookTarget(aimingEntity);
		if (!syncedTarget.isEmpty(aimingEntity.level())
				&& HitResultUtil.isTargetWithinRange(syncedTarget, aimingEntity, aimingEntity.level(),
						BLOCK_TARGET_RANGE, BLOCK_TARGET_RANGE)) {
			return syncedTarget;
		}
		return HitResultUtil.clip(aimingEntity.getEyePosition(), aimingEntity.getLookAngle(),
				BLOCK_TARGET_RANGE, BLOCK_TARGET_RANGE, aimingEntity.level(),
				entity -> stand != null ? StandEntityPunchAbility.canStandHit(stand, entity) : StandEntityAbility.canPickEntityForAiming(entity),
				aimingEntity, 0);
	}

	private static LivingEntity getAnchorAimingEntity(LivingEntity user) {
		StandPower standPower = StandPower.get(user);
		StandEntity stand = standPower != null ? standPower.getSummonedStandEntity() : null;
		return stand != null && stand.isManuallyControlled() ? stand : user;
	}

	private static ActionTarget getSyncedLookTarget(LivingEntity aimingEntity) {
		var aim = LivingComponentAction.getAim(aimingEntity);
		if (aim == null) {
			return ActionTarget.EMPTY;
		}
		ActionTarget target = aim.getTarget();
		return target != null ? target.resolveEntityId(aimingEntity.level()) : ActionTarget.EMPTY;
	}

	public static class AnchorMake extends EntityActionInstance {
		private ActionTarget actionTarget = ActionTarget.EMPTY;

		public AnchorMake(EntityActionType ability) {
			super(ability);
		}

		private void setActionTarget(ActionTarget target) {
			this.actionTarget = target != null ? target.copy() : ActionTarget.EMPTY;
		}

		private ActionTarget getActionTarget(Level level) {
			return actionTarget.resolveEntityId(level);
		}

		@Override
		public void onActionSet(EntityActionInstance prevAction) {
			keepStandAimedAtTarget(getActionTarget(level()));
			aimAs = AimingEntity.STAND;
		}

		@Override
		public void actionPerformStart() {
			Level level = level();
			if (!(performer instanceof StandEntity stand)) {
				return;
			}

			ActionTarget target = getActionTarget(level);
			if (target.getType() == TargetType.BLOCK) {
				aimAs = AimingEntity.CAMERA_ENTITY;
			}
			if (level.isClientSide()) {
				return;
			}
			if (!(level instanceof ServerLevel serverLevel) || target.getType() != TargetType.BLOCK) {
				return;
			}

			LivingEntity user = getPowerUser();
			if (user == null) {
				return;
			}

			BlockPos blockPos = target.getBlockPos();
			BlockState blockState = level.getBlockState(blockPos);
			if (blockState.isAir()) {
				return;
			}
			if (!StandStatFormulas.isBlockBreakable(stand.getAttackDamage(), blockState, level, blockPos)) {
				user.sendSystemMessage(ConditionCheck.message("stand_cant_break_block"));
				return;
			}

			StandPower standPower = StandPower.get(user);
			if (!StandAbilityStamina.consumeOrMessage(ability, standPower, user, STAMINA_COST)) {
				return;
			}

			if (JojoModUtil.breakingBlocksEnabled(level)) {
				if (!JojoModUtil.canEntityDestroy(serverLevel, blockPos, blockState, stand)) {
					user.sendSystemMessage(ConditionCheck.message("stand_cant_break_block"));
					return;
				}

				List<ItemStack> drops = ItemUtil.turnBlockToItem(blockState, serverLevel, blockPos, stand);
				ItemStack anchorItem = drops.isEmpty() ? ItemStack.EMPTY : drops.get(0);
				if (!anchorItem.isEmpty()) {
					addOriginalPos(anchorItem, blockPos, serverLevel);
					ItemTracker itemTracker = ItemTracking.getItemTracking(level).startTracking(anchorItem, serverLevel);
					if (itemTracker != null) {
						itemTracker.context = "cd_anchor_block";
					}
				}

				Optional<BlockEntity> blockEntity = Optional.ofNullable(level.getBlockEntity(blockPos));
				List<ItemStack> recordedDrops = anchorItem.isEmpty() ? Collections.emptyList() : Collections.singletonList(anchorItem.copy());
				level.removeBlock(blockPos, false);
				CrazyDRestoreTerrainAbility.rememberBrokenBlock(level, blockPos, blockState, blockEntity, recordedDrops);
				makeAnchorItem(user, level, blockPos, blockState, anchorItem);
			}
			else {
				ItemStack anchorItem = new ItemStack(ModItems.CRAZY_DIAMOND_NON_BLOCK_ANCHOR.get());
				addOriginalPos(anchorItem, blockPos, serverLevel);
				ItemTracker itemTracker = ItemTracking.getItemTracking(level).startTracking(anchorItem, serverLevel);
				if (itemTracker != null) {
					itemTracker.context = "cd_anchor_non_block";
				}
				makeAnchorItem(user, level, blockPos, null, anchorItem);
			}

			StandUtil.playStandEntitySound(stand, ModSoundEvents.CRAZY_DIAMOND_PUNCH_HEAVY, 1.0F, 1.0F);
			standPower.addExp(0.04F);
		}

		@Override
		public void toBuf(FriendlyByteBuf buf) {
			ActionTarget.STREAM_CODEC_UNRESOLVED_ENTITY_ID.encode(buf, actionTarget);
		}

		@Override
		public void fromBuf(FriendlyByteBuf buf) {
			actionTarget = ActionTarget.STREAM_CODEC_UNRESOLVED_ENTITY_ID.decode(buf);
		}

		private void makeAnchorItem(LivingEntity user, Level level, BlockPos blockPos, BlockState blockState,
				ItemStack anchorItem) {
			if (anchorItem.isEmpty()) {
				return;
			}

			boolean dropItem = true;
			if (blockState == null || anchorItem.getItem() instanceof BlockItem) {
				dropItem = !(user instanceof Player player && player.getInventory().add(anchorItem) && anchorItem.isEmpty());
			}
			if (dropItem) {
				Block.popResource(level, blockPos, anchorItem);
			}
		}

		private static void addOriginalPos(ItemStack item, BlockPos blockPos, ServerLevel level) {
			item.set(ModItemDataComponents.ORIGINAL_POS, new OriginalItemPosComponent(blockPos, level.dimension()));
			item.set(DataComponents.ITEM_NAME, Component.translatable("jojo_ripples.item.crazy_diamond_anchor.name",
					item.getHoverName(),
					ComponentUtils.wrapInSquareBrackets(Component.translatable("chat.coordinates", blockPos.getX(), blockPos.getY(), blockPos.getZ()))
							.withStyle(ChatFormatting.BOLD, ChatFormatting.GREEN)));
		}
	}
}
