package com.github.standobyte.jojoimpl.stands.crazydiamond;

import com.github.standobyte.jojo.powersystem.standpower.StandInstance.StandPart;

import com.github.standobyte.jojo.client.particle.CustomParticlesHelper;
import com.github.standobyte.jojo.mechanics.resolve.ResolveModeEffect;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.ActionAnimIdentifier;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.subsystems.target.AimingEntity;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojo.subsystems.target.ActionTarget.TargetType;
import com.github.standobyte.jojo.subsystems.target.ActionTargetAim;
import com.github.standobyte.jojo.subsystems.target.HitResultUtil;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntityAbility;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntityAbility.AutoSummonMode;
import com.github.standobyte.jojo.util.reflection.CommonReflection;
import com.github.standobyte.jojo.util.functions.ItemUtil;
import com.github.standobyte.jojoimpl.stands._entitybase.StandAbilityStamina;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.vehicle.MinecartTNT;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class CrazyDRevertEntityAndBlocksAbility extends StandEntityAbility {
	private static final double REVERT_ENTITY_TARGET_RANGE = 8.0D;
	private static final ActionAnimIdentifier ITEM_FIX_ANIM = ActionAnimIdentifier.getOrCreate("itemFix", false);

	public CrazyDRevertEntityAndBlocksAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, RevertStateAction::new);
		partsRequired(StandPart.ARMS);
		setButtonHoldPhase(ActionPhase.PERFORM);
		standAutoSummonMode(AutoSummonMode.OFF_ARM);
	}

	@Override
	public void initActionFromConfig(EntityActionInstance action, Level level, LivingEntity powerUser, LivingEntity performer) {
		super.initActionFromConfig(action, level, powerUser, performer);
		if (action instanceof RevertStateAction revertAction) {
			ActionTarget target = getCurrentRevertEntityTarget(powerUser, level);
			if (!target.isEmpty(level)) {
				revertAction.setActionTarget(target);
				action.standRotationTarget = target.copy();
				action.aimAs = AimingEntity.STAND;
			}
		}
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		LivingEntity user = context.getUser();
		if (user == null) {
			return ConditionCheck.NEGATIVE;
		}
		
		ActionTarget entityTarget = getCurrentRevertEntityTarget(user, user.level());
		if (entityTarget.getType() == TargetType.ENTITY) {
			Entity targetEntity = entityTarget.getEntity();
			return targetEntity != null && canRevertEntity(targetEntity, user) ? checkRevertStateStartConditions(context) : ConditionCheck.createNegative("entity_revert");
		}
		ActionTarget aimTarget = LivingComponentAction.getAim(user) != null ? LivingComponentAction.getAim(user).getTarget() : ActionTarget.EMPTY;
		if (aimTarget.getType() == TargetType.BLOCK) {
			return ConditionCheck.NEGATIVE;
		}
		
		ItemStack offhandItem = user.getOffhandItem();
		if (offhandItem.isEmpty()) {
			return ConditionCheck.createNegative("item_offhand");
		}
		return CrazyDPreviousStateItemConversion.convertTo(offhandItem, user.level(), null, user.getRandom(), false).isPresent()
				? checkRevertStateStartConditions(context) : ConditionCheck.createNegative("item_revert");
	}

	private ConditionCheck checkRevertStateStartConditions(Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		return check.isPositive() ? StandAbilityStamina.check(context, CrazyDRepairItemAbility.REPAIR_STAMINA_COST_TICK) : check;
	}

	@Override
	public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
		return CrazyDHealAbility.getHealingAnim(action, ITEM_FIX_ANIM);
	}

	@Override
	public boolean canTargetEntityForAiming(StandEntity standEntity, Entity target) {
		LivingEntity user = standEntity != null ? standEntity.getUser() : null;
		return user != null && canPickEntityForAiming(target) && canRevertEntity(target, user);
	}

	private static ActionTarget getCurrentRevertEntityTarget(LivingEntity user, Level level) {
		if (user == null) {
			return ActionTarget.EMPTY;
		}
		ActionTargetAim aim = LivingComponentAction.getAim(user);
		ActionTarget target = aim != null ? aim.getTarget().resolveEntityId(level) : ActionTarget.EMPTY;
		if (isValidRevertEntityTarget(user, target, level)) {
			return target;
		}
		target = HitResultUtil.clip(
				user.getEyePosition(),
				user.getLookAngle(),
				REVERT_ENTITY_TARGET_RANGE,
				REVERT_ENTITY_TARGET_RANGE,
				level,
				entity -> canPickEntityForAiming(entity) && canRevertEntity(entity, user),
				user,
				0);
		return target.getType() == TargetType.ENTITY ? target : ActionTarget.EMPTY;
	}

	private static boolean isValidRevertEntityTarget(LivingEntity user, ActionTarget target, Level level) {
		if (target.getType() != TargetType.ENTITY || target.isEmpty(level)) {
			return false;
		}
		Entity targetEntity = target.getEntity();
		return targetEntity != null
				&& canPickEntityForAiming(targetEntity)
				&& canRevertEntity(targetEntity, user)
				&& targetEntity.getBoundingBox().distanceToSqr(user.getEyePosition()) <= REVERT_ENTITY_TARGET_RANGE * REVERT_ENTITY_TARGET_RANGE;
	}

	public static class RevertStateAction extends CrazyDHealAbility.HealingAction {

		public RevertStateAction(EntityActionType ability) {
			super(ability);
		}

		@Override
		protected float staminaCostTick() {
			return CrazyDRepairItemAbility.REPAIR_STAMINA_COST_TICK;
		}

		@Override
		public HealResult restoreTarget(ActionTarget target, StandEntity crazyDiamond) {
			HealResult result = new HealResult();
			result.synched.target = target;
			LivingEntity user = getPowerUser();
			result.synched.barrageVisuals = user != null && ResolveModeEffect.getResolveEffectLvl(user) >= 0;
			
			if (target.getType() == TargetType.ENTITY && user != null) {
				Entity targetEntity = target.getEntity();
				if (targetEntity != null && targetEntity.isAlive() && canRevertEntity(targetEntity, user)) {
					if (revertEntityTick(targetEntity, crazyDiamond, result)) {
						return result;
					}
				}
			}
			return super.restoreTarget(target, crazyDiamond);
		}

		@Override
		public void actionTick() {
			super.actionTick();
			if (getPhase() != ActionPhase.PERFORM) {
				return;
			}
			Level level = level();
			StandEntity stand = performer instanceof StandEntity __ ? __ : null;
			ActionTarget actionTarget = getActionTarget(level);
			boolean hasActiveTarget = actionTarget.getType() == TargetType.ENTITY && !actionTarget.isEmpty(level);
			if (!hasActiveTarget && stand != null) {
				LivingEntity user = getPowerUser();
				if (user == null) {
					return;
				}
				if (level.isClientSide()) {
					CustomParticlesHelper.createCDRestorationParticle(user, InteractionHand.OFF_HAND);
					CrazyDRepairItemAbility.setItemFixStandOffset(this, stand);
					standRotationTarget = ActionTarget.EMPTY;
					aimAs = AimingEntity.CAMERA_ENTITY;
					return;
				}
				ItemStack heldItem = user.getOffhandItem();
				if (heldItem.isEmpty() || CrazyDPreviousStateItemConversion.convertTo(heldItem, level, null, performer.getRandom(), false).isEmpty()) {
					startRecovery();
					return;
				}
				int tick = curPhaseTick;
				boolean transformTick = CrazyDRepairItemAbility.isItemTransformationTick(tick, stand);
				CrazyDRepairItemAbility.ItemRepairResult repairProgress = CrazyDRepairItemAbility.repairTick(user, stand, heldItem, tick);
				if (!repairProgress.isRepairing && transformTick) {
					CrazyDPreviousStateItemConversion.convertTo(heldItem, level, null, performer.getRandom(), true).ifPresent(itemsAndCount -> {
						boolean gaveIngredients = false;
						for (ItemStack ingredient : itemsAndCount.getFirst()) {
							if (!ingredient.isEmpty()) {
								ItemUtil.giveItemTo(user, ingredient, true);
								gaveIngredients = true;
							}
						}
						if (gaveIngredients) {
							heldItem.shrink(itemsAndCount.getSecond());
						}
					});
					startRecovery();
				}
			}
			if (stand != null && !hasActiveTarget) {
				CrazyDRepairItemAbility.setItemFixStandOffset(this, stand);
				standRotationTarget = ActionTarget.EMPTY;
				aimAs = AimingEntity.CAMERA_ENTITY;
			}
		}

		@Override
		public void applyStandUserRotation(StandEntity standEntity, LivingEntity user) {
			ActionTarget actionTarget = getActionTarget(standEntity.level());
			if (actionTarget.getType() == TargetType.EMPTY || actionTarget.isEmpty(standEntity.level())) {
				CrazyDRepairItemAbility.applyItemFixStandRotation(standEntity, user);
			}
		}

		private boolean revertEntityTick(Entity targetEntity, StandEntity crazyDiamond, HealResult result) {
			Level level = targetEntity.level();
			boolean serverSide = !level.isClientSide();
			
			if (targetEntity instanceof PrimedTnt tnt) {
				if (curPhaseTick == 0 || tnt.getFuse() < 80) {
					if (serverSide) {
						tnt.setFuse(tnt.getFuse() + 2);
					}
				}
				else if (serverSide) {
					BlockPos blockPos = tnt.blockPosition();
					BlockState tntBlock = tnt.getBlockState();
					tnt.discard();
					if (tnt.isRemoved()) {
						replaceOrDropBlock(level, blockPos, tntBlock);
					}
				}
				markReverting(result);
				return true;
			}
			
			if (targetEntity instanceof MinecartTNT tntMinecart) {
				int fuse = tntMinecart.getFuse();
				if (fuse >= 80) {
					if (serverSide) {
						CommonReflection.setTntMinecartFuse(tntMinecart, -1);
					}
					markReverting(result);
				}
				else if (fuse > -1) {
					if (serverSide) {
						CommonReflection.setTntMinecartFuse(tntMinecart, fuse + 2);
					}
					markReverting(result);
				}
				return true;
			}
			
			if (targetEntity instanceof SnowGolem snowGolem) {
				HealResult heal = healLivingEntity(level, snowGolem, crazyDiamond, result);
				if (heal.synched.isHealing) {
					return true;
				}
				if (serverSide && entityRandom(crazyDiamond, targetEntity) < 0.1F) {
					BlockPos blockPos = targetEntity.blockPosition();
					targetEntity.discard();
					if (targetEntity.isRemoved()) {
						replaceOrDropBlock(level, blockPos.offset(0, 2, 0), Blocks.CARVED_PUMPKIN.defaultBlockState());
						replaceOrDropBlock(level, blockPos, Blocks.SNOW_BLOCK.defaultBlockState());
						replaceOrDropBlock(level, blockPos.offset(0, 1, 0), Blocks.SNOW_BLOCK.defaultBlockState());
					}
				}
				markReverting(result);
				return true;
			}
			
			if (targetEntity instanceof Creeper creeper) {
				if (creeper.isPowered()) {
					HealResult heal = healLivingEntity(level, creeper, crazyDiamond, result);
					if (heal.synched.isHealing) {
						return true;
					}
					if (serverSide && entityRandom(crazyDiamond, targetEntity) < 0.05F) {
						CommonReflection.setCreeperPowered(creeper, false);
					}
					markReverting(result);
				}
				return true;
			}
			
			if (targetEntity instanceof IronGolem ironGolem) {
				HealResult heal = healLivingEntity(level, ironGolem, crazyDiamond, result);
				if (heal.synched.isHealing) {
					return true;
				}
				if (serverSide && entityRandom(crazyDiamond, targetEntity) < 0.05F) {
					BlockPos blockPos = targetEntity.blockPosition();
					targetEntity.discard();
					if (targetEntity.isRemoved()) {
						replaceOrDropBlock(level, blockPos, Blocks.IRON_BLOCK.defaultBlockState());
						replaceOrDropBlock(level, blockPos.offset(0, 2, 0), Blocks.CARVED_PUMPKIN.defaultBlockState());
						replaceOrDropBlock(level, blockPos.offset(0, 1, 0), Blocks.IRON_BLOCK.defaultBlockState());
						replaceOrDropBlock(level, blockPos.offset(1, 1, 0), Blocks.IRON_BLOCK.defaultBlockState());
						replaceOrDropBlock(level, blockPos.offset(-1, 1, 0), Blocks.IRON_BLOCK.defaultBlockState());
					}
				}
				markReverting(result);
				return true;
			}
			
			if (targetEntity instanceof WitherBoss wither) {
				int spawnTicks = wither.getInvulnerableTicks();
				if (spawnTicks > 0) {
					if (serverSide) {
						wither.setInvulnerableTicks(Math.min(spawnTicks + 5, 220));
						if (spawnTicks >= 215 && entityRandom(crazyDiamond, targetEntity) < 0.005F) {
							BlockPos blockPos = targetEntity.blockPosition();
							targetEntity.discard();
							if (targetEntity.isRemoved()) {
								replaceOrDropBlock(level, blockPos.offset(0, 2, 0), Blocks.WITHER_SKELETON_SKULL.defaultBlockState());
								replaceOrDropBlock(level, blockPos.offset(1, 2, 0), Blocks.WITHER_SKELETON_SKULL.defaultBlockState());
								replaceOrDropBlock(level, blockPos.offset(-1, 2, 0), Blocks.WITHER_SKELETON_SKULL.defaultBlockState());
								replaceOrDropBlock(level, blockPos, Blocks.SOUL_SAND.defaultBlockState());
								replaceOrDropBlock(level, blockPos.offset(0, 1, 0), Blocks.SOUL_SAND.defaultBlockState());
								replaceOrDropBlock(level, blockPos.offset(1, 1, 0), Blocks.SOUL_SAND.defaultBlockState());
								replaceOrDropBlock(level, blockPos.offset(-1, 1, 0), Blocks.SOUL_SAND.defaultBlockState());
							}
						}
					}
					markReverting(result);
				}
				return true;
			}
			
			return false;
		}

		@Override
		public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
			return true;
		}

	}

	public static boolean canRevertEntity(Entity targetEntity, LivingEntity user) {
		int resolveLevel = ResolveModeEffect.getEffectiveResolveLevel(user);
		if (resolveLevel >= 3) {
			if (targetEntity instanceof PrimedTnt || targetEntity instanceof MinecartTNT || targetEntity instanceof SnowGolem) {
				return true;
			}
			if (targetEntity instanceof Creeper creeper && creeper.isPowered()) {
				return true;
			}
			if (resolveLevel >= 4) {
				return targetEntity instanceof IronGolem 
						|| targetEntity instanceof WitherBoss wither && wither.getInvulnerableTicks() > 0;
			}
		}
		return false;
	}

	private static void markReverting(CrazyDHealAbility.HealingAction.HealResult result) {
		result.synched.isHealing = true;
	}

	private static float entityRandom(StandEntity crazyDiamond, Entity targetEntity) {
		return (crazyDiamond != null ? crazyDiamond.getRandom() : targetEntity.getRandom()).nextFloat();
	}

	private static boolean canReplaceBlock(Level level, BlockPos blockPos, BlockState newBlockState) {
		BlockState currentBlockState = level.getBlockState(blockPos);
		float hardness = currentBlockState.getDestroySpeed(level, blockPos);
		return currentBlockState.canBeReplaced() || hardness >= 0 && hardness < newBlockState.getDestroySpeed(level, blockPos);
	}

	private static void replaceOrDropBlock(Level level, BlockPos blockPos, BlockState newBlockState) {
		if (!level.isClientSide()) {
			if (canReplaceBlock(level, blockPos, newBlockState)) {
				level.destroyBlock(blockPos, true);
				level.setBlockAndUpdate(blockPos, newBlockState);
			}
			else {
				Item blockItem = newBlockState.getBlock().asItem();
				if (blockItem != Items.AIR) {
					Block.popResource(level, blockPos, new ItemStack(blockItem));
				}
			}
		}
	}

}
