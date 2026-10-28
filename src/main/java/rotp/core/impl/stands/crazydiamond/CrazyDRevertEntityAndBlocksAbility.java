package rotp.core.impl.stands.crazydiamond;

import rotp.core.powersystem.standpower.StandInstance.StandPart;

import rotp.core.client.particle.CustomParticlesHelper;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.subsystems.target.AimingEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.subsystems.target.ActionTargetRange;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandEntityAbility.AutoSummonMode;
import rotp.core.util.reflection.CommonReflection;
import rotp.core.util.functions.ItemUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
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
	// 1.16's ITEM_FIX_POSE, whose clip in the official 1.21.1 port is repair_item (the port's itemFix is a copy)
	private static final ActionAnimIdentifier ITEM_FIX_ANIM = ActionAnimIdentifier.getOrCreate("repair_item", false);

	public CrazyDRevertEntityAndBlocksAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, RevertStateAction::new);
		partsRequired(StandPart.ARMS);
		setButtonHoldPhase(ActionPhase.PERFORM);
		standAutoSummonMode(AutoSummonMode.OFF_ARM);
	}

	// 1.16 CrazyDiamondPreviousState had only a stamina cost per tick (staminaCostTick), which a Stand-bearing mob
	// did not compare with its stamina (getStaminaCost 0)
	@Override
	public float getStaminaCost(Power<?> context) {
		return 0.0F;
	}

	// 1.16 CrazyDiamondPreviousState.lockOnTargetPosition: an offset never turns to the entity Revert works on
	@Override
	public boolean lockOnTargetPosition(StandPower standPower, StandEntity standEntity, EntityActionInstance action) {
		return false;
	}

	// 1.16 CrazyDiamondPreviousState.yRotForOffset: the view yaw with an entity target, the body yaw on the off-hand item
	@Override
	public float yRotForOffset(LivingEntity user, EntityActionInstance action) {
		return action.getTaskTarget(user.level()).getType() != TargetType.EMPTY ? super.yRotForOffset(user, action) : user.yBodyRot;
	}

	/*
	 * 1.16 CrazyDiamondPreviousState.checkTarget, which StandEntityTask.keepTarget asked too: an entity it reverts, no block.
	 * With TargetRequirement.NONE a Revert pressed at nothing took over such an entity the previous task held
	 * (StandEntity.setTask).
	 */
	@Override
	protected boolean keepsTaskTargetType(StandEntity standEntity, ActionTarget target) {
		LivingEntity user = standEntity.getUser();
		return target.getType() == TargetType.ENTITY && user != null && canRevertEntity(target.getEntity(), user);
	}

	// 1.16 CrazyDiamondPreviousState.checkStandTarget: primed TNT and a TNT minecart too, which are not living
	@Override
	protected boolean keepsTaskTargetEntity(StandEntity standEntity, Entity entity) {
		return entity instanceof PrimedTnt || entity instanceof MinecartTNT || super.keepsTaskTargetEntity(standEntity, entity);
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
		// 1.16 CrazyDiamondPreviousState.checkTarget refused a block, so PowerBaseImpl.checkTarget emptied the target
		// (TargetRequirement.NONE) and the press went on to the off-hand item
		ConditionCheck itemCheck = checkOffhandItem(user);
		return itemCheck.isPositive() ? checkRevertStateStartConditions(context) : itemCheck;
	}

	/*
	 * 1.16 tickHeldAction: with no entity Revert works on under the crosshair (a block, nothing, another entity, one out
	 * of range), PowerBaseImpl.checkTarget emptied the target and checkSpecificConditions asked for a convertible off-hand
	 * item; without one the hold ended with item_offhand or item_revert.
	 */
	@Override
	protected ConditionCheck checkHeldSpecificConditions(EntityActionInstance action, Power<?> context) {
		LivingEntity user = context.getUser();
		if (user == null) {
			return ConditionCheck.NEGATIVE;
		}
		return getCurrentRevertEntityTarget(user, user.level()).getType() == TargetType.ENTITY
				? ConditionCheck.POSITIVE : checkOffhandItem(user);
	}

	private static ConditionCheck checkOffhandItem(LivingEntity user) {
		ItemStack offhandItem = user.getOffhandItem();
		if (offhandItem.isEmpty()) {
			return ConditionCheck.createNegative("item_offhand");
		}
		return CrazyDPreviousStateItemConversion.convertTo(offhandItem, user.level(), null, user.getRandom(), false).isPresent()
				? ConditionCheck.POSITIVE : ConditionCheck.createNegative("item_revert");
	}

	private ConditionCheck checkRevertStateStartConditions(Power<?> context) {
		return super.checkSpecificConditions(context);
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

	// 1.16 took the target under the crosshair, as for Heal, a block without a collision shape giving way
	private static ActionTarget getCurrentRevertEntityTarget(LivingEntity user, Level level) {
		return CrazyDHealAbility.getCrosshairEntityTarget(user, level, entity -> canRevertEntity(entity, user), true);
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
		protected boolean paysTickWithoutEntityTarget() {
			return true;
		}

		/**
		 * 1.16 CrazyDiamondPreviousState.barrageVisuals (:424-427): its task's target an entity that passes checkTarget,
		 * reverting it or not, and nothing else (no Resolve rule). Asked once, in the first perform tick, as 1.16 asked it
		 * as the task was set: a target that stops passing checkTarget later leaves the pose as it was.
		 */
		@Override
		public boolean showsBarrageVisuals(ActionTarget target) {
			LivingEntity user = getPowerUser();
			return target.getType() == TargetType.ENTITY && target.getEntity() != null && user != null
					&& canRevertEntity(target.getEntity(), user);
		}

		/**
		 * 1.16 StandEntityTask.keepTarget with CrazyDiamondPreviousState.checkTarget: an entity Revert can no longer
		 * work on (a creeper no longer charged, a Wither past its spawn, a Resolve level gone) or beyond the default
		 * 8 blocks from the Stand is dropped, and the task goes on with the off-hand item.
		 */
		@Override
		protected boolean keepsEntityTarget(ActionTarget target, Level level) {
			Entity targetEntity = target.getEntity();
			LivingEntity user = getPowerUser();
			return targetEntity != null && targetEntity != performer && !targetEntity.isRemoved()
					&& !(targetEntity instanceof LivingEntity living && living.deathTime >= 20)
					&& user != null && canRevertEntity(targetEntity, user)
					&& ActionTargetRange.isEntityWithinRange(ActionTargetRange.standPerformer(user), targetEntity,
							ActionTargetRange.DEFAULT_ENTITY_RANGE_SQ);
		}

		@Override
		public HealResult restoreTarget(ActionTarget target, StandEntity crazyDiamond) {
			HealResult result = new HealResult();
			result.synched.target = target;
			LivingEntity user = getPowerUser();

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
				// 1.16 CrazyDiamondPreviousState.standTickPerform default branch: the hold runs until the key is
				// released, an empty or unconvertible off hand only converts nothing, and every tick is paid
				ItemStack heldItem = user.getOffhandItem();
				int tick = curPhaseTick;
				boolean transformTick = CrazyDRepairItemAbility.isItemTransformationTick(tick, stand);
				CrazyDRepairItemAbility.ItemRepairResult repairProgress = CrazyDRepairItemAbility.repairTick(user, stand, heldItem, tick);
				if (!repairProgress.isRepairing && transformTick && user instanceof Player) {
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
				}
			}
			if (stand != null && !hasActiveTarget) {
				CrazyDRepairItemAbility.setItemFixStandOffset(this, stand);
				standRotationTarget = ActionTarget.EMPTY;
				aimAs = AimingEntity.CAMERA_ENTITY;
			}
		}

		// 1.16 CrazyDiamondPreviousState.getOffsetFromUser without an entity: the off-hand item's offset, which the perform
		// sets (also in its tick) as soon as the entity is let go
		@Override
		protected void placeStandWithoutEntityTarget(StandEntity standEntity, boolean wasInFront) {
			if (getPhase() == ActionPhase.PERFORM) {
				CrazyDRepairItemAbility.setItemFixStandOffset(this, standEntity);
			}
			else {
				super.placeStandWithoutEntityTarget(standEntity, wasInFront);
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

	// 1.16 CrazyDiamondPreviousState.checkTarget: the stored Resolve level, not the highest reached or the Resolve effect's
	public static boolean canRevertEntity(Entity targetEntity, LivingEntity user) {
		StandPower standPower = StandPower.get(user);
		int resolveLevel = standPower != null ? standPower.getResolveLevel() : 0;
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
