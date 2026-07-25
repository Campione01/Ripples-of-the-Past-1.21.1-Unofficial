package com.github.standobyte.jojoimpl.stands.crazydiamond;

import com.github.standobyte.jojo.powersystem.standpower.StandInstance.StandPart;

import java.util.Collections;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.client.ClientGlobals;
import com.github.standobyte.jojo.client.particle.CustomParticlesHelper;
import com.github.standobyte.jojo.client.sound.ClientsideSoundsHelper;
import com.github.standobyte.jojo.client.sound.sounds.EntityLingeringSoundInstance;
import com.github.standobyte.jojo.client.sound.sounds.EntityStoppableSoundInstance;
import com.github.standobyte.jojo.init.ModItemDataComponents;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.StandUtil;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntityAbility.AutoSummonMode;
import com.github.standobyte.jojo.powersystem.standpower.entity.NoPoseStandEntityAbility;
import com.github.standobyte.jojo.subsystems.itemtracking.OriginalItemPosComponent;
import com.github.standobyte.jojo.util.functions_network.PacketDistributor2;
import com.github.standobyte.jojoimpl.stands.crazydiamond.brokenblocks.BrokenBlocksChunkData;
import com.github.standobyte.jojoimpl.stands.crazydiamond.brokenblocks.CDBlocksRestoredPacket;
import com.github.standobyte.jojoimpl.stands.crazydiamond.brokenblocks.PrevBlockInfo;
import com.github.standobyte.jojoimpl.stands._entitybase.StandAbilityStamina;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class CrazyDAnchorBlockAbility extends NoPoseStandEntityAbility {
	private static final float STAMINA_COST_TICK = 1.0F;

	public CrazyDAnchorBlockAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, AnchorBlockMove::new);
		partsRequired(StandPart.ARMS);
		setButtonHoldPhase(ActionPhase.PERFORM);
		standAutoSummonMode(AutoSummonMode.OFF_ARM);
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		FoundAnchor anchor = getItemToUseAsAnchor(PowerClass.STAND.cast(context));
		if (anchor == null) {
			return ConditionCheck.createNegative("item_block_origin");
		}
		ConditionCheck check = super.checkSpecificConditions(context);
		return check.isPositive() ? StandAbilityStamina.check(context, STAMINA_COST_TICK) : check;
	}

	@Override
	public Component getName(Power<?> context) {
		StandPower standPower = PowerClass.STAND.cast(context);
		FoundAnchor anchor = standPower != null ? getItemToUseAsAnchor(standPower) : null;
		if (anchor == null) {
			return abilityName(context, ".empty");
		}
		BlockPos pos = anchor.itemComponent.blockPos();
		return abilityName(context, ".pos", pos.getX(), pos.getY(), pos.getZ());
	}
	
	public static record FoundAnchor(LivingEntity entity, InteractionHand hand, 
			ItemStack item, OriginalItemPosComponent itemComponent) {}
	
	@Nullable
	public static FoundAnchor getItemToUseAsAnchor(StandPower standPower) {
		FoundAnchor anchor;
		
		StandEntity standEntity = standPower.getSummonedStandEntity();
		if (standEntity != null && standEntity.isManuallyControlled()) {
			anchor = getOriginalPosFromItemInHand(standEntity);
			if (anchor != null) return anchor;
		}
		
		LivingEntity user = standPower.getUser();
		if (user != null) {
			anchor = getOriginalPosFromItemInHand(user);
			if (anchor != null) return anchor;
		}
		
		if (standEntity != null) {
			anchor = getOriginalPosFromItemInHand(standEntity);
			if (anchor != null) return anchor;
		}
		
		return null;
	}
	
	@Nullable
	public static FoundAnchor getOriginalPosFromItemInHand(LivingEntity entity) {
		ItemStack item = entity.getOffhandItem();
		OriginalItemPosComponent component = item.get(ModItemDataComponents.ORIGINAL_POS);
		if (component != null && component.matchesDimension(entity.level())) return new FoundAnchor(entity, InteractionHand.OFF_HAND, item, component);
		item = entity.getMainHandItem();
		component = item.get(ModItemDataComponents.ORIGINAL_POS);
		if (component != null && component.matchesDimension(entity.level())) return new FoundAnchor(entity, InteractionHand.MAIN_HAND, item, component);
		return null;
	}
	
	
	public static class AnchorBlockMove extends EntityActionInstance {
		private boolean clientMoveEffectsStarted;

		public AnchorBlockMove(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onButtonStopHold() {
			startRecovery();
		}

		@Override
		public void onSetPhase(ActionPhase newPhase) {
			if (level().isClientSide()) {
				if (newPhase == ActionPhase.PERFORM) {
					startClientMoveEffects();
				}
				else {
					clientMoveEffectsStarted = false;
				}
			}
		}

		@Override
		public void onActionCleared(@Nullable EntityActionInstance newAction) {
			if (level().isClientSide()) {
				clientMoveEffectsStarted = false;
			}
		}

		private void startClientMoveEffects() {
			if (clientMoveEffectsStarted || !(performer instanceof StandEntity standEntity)
					|| !ClientGlobals.canHearStand(standEntity)) {
				return;
			}
			Level level = level();
			clientMoveEffectsStarted = true;
			ClientsideSoundsHelper.playNonVanillaClassSound(new EntityLingeringSoundInstance(ClientsideSoundsHelper.withStandSkin(
					ModSoundEvents.CRAZY_DIAMOND_FIX_STARTED.get(), standEntity),
					standEntity.getSoundSource(), 1, 1, standEntity, level));
			ClientsideSoundsHelper.playNonVanillaClassSound(new EntityStoppableSoundInstance(ClientsideSoundsHelper.withStandSkin(
					ModSoundEvents.CRAZY_DIAMOND_FIX_LOOP.get(), standEntity),
					standEntity.getSoundSource(), 1, 1, true, standEntity, level.random.nextLong(),
					() -> this.isOver() || this.phase != ActionPhase.PERFORM));
		}
		
		@Override
		public void actionTick() {
			LivingEntity user = getPowerUser();
			Level level = user.level();
			if (!level.isClientSide()) {
				StandPower standPower = StandPower.get(user);
				if (standPower != null) {
					if (!StandAbilityStamina.consume(ability, standPower, STAMINA_COST_TICK, true)) {
						setPhaseStart(ActionPhase.RECOVERY);
						syncPhaseChanges();
						return;
					}
					FoundAnchor anchor = getItemToUseAsAnchor(standPower);
					if (anchor != null) {
						Entity entity = entityToMove(anchor.entity);
						BlockPos blockPos = anchor.itemComponent.blockPos();
						Vec3 pos = Vec3.atCenterOf(blockPos);
		                boolean isCloseToAnchorPos = isCloseToAnchorPos(entity, pos);
		                if (!isCloseToAnchorPos) {
		                	moveWithAnchor(entity, pos);
		                	standPower.addExp(0.04f);
		                }
		                else {
		                	ItemStack heldItem = anchor.item;
		                	
		                	BlockState blockStateToPlace = null;
		                	if (heldItem.getItem() instanceof BlockItem blockItem) {
		                		blockStateToPlace = blockItem.getBlock().defaultBlockState();
		                	}
		                	var brokenBlocks = BrokenBlocksChunkData.getExistingData(level, blockPos);
		                	if (brokenBlocks != null) {
		                		PrevBlockInfo block = brokenBlocks.getBrokenBlockAt(blockPos);
		                		if (block != null && block.drops.size() == 1 && ItemStack.matches(block.drops.get(0), heldItem)) {
		                			blockStateToPlace = block.state;
		                		}
		                	}
		                	
		                	boolean willRestore = blockStateToPlace == null || canReplaceBlock(level, blockPos, blockStateToPlace);

		                	if (willRestore) {
		                		heldItem.shrink(1);
		                		if (blockStateToPlace != null) {
			                		if (!level.getBlockState(blockPos).isAir()) {
			                			level.destroyBlock(blockPos, true);
			                		}
			                		level.setBlockAndUpdate(blockPos, blockStateToPlace);
		                		}
		                		if (performer instanceof StandEntity stand) {
		                			StandUtil.playStandEntitySound(stand, ModSoundEvents.CRAZY_DIAMOND_FIX_ENDED, 1.0F, 1.0F);
		                		}
		                		else {
		                			performer.playSound(ModSoundEvents.CRAZY_DIAMOND_FIX_ENDED.get(), 1.0F, 1.0F);
		                		}
		                		PacketDistributor2.sendToPlayersTrackingEntity(performer, player -> StandUtil.entityCanSeeStands(player), true, 
		                				new CDBlocksRestoredPacket(Collections.singletonList(blockPos), Collections.emptyList()));
		                	}
		                }
					}
					else {
						setPhaseStart(ActionPhase.RECOVERY);
						syncPhaseChanges();
					}
				}
			}
			else if (ClientGlobals.canSeeStands) {
				StandPower standPower = StandPower.get(user);
				if (standPower != null) {
					FoundAnchor anchor = getItemToUseAsAnchor(standPower);
					if (anchor != null) {
						CustomParticlesHelper.createCDRestorationParticle(anchor.entity, anchor.hand);
					}
				}
			}
		}
		
	}
	
	public static Entity entityToMove(Entity itemHolderEntity) {
		return itemHolderEntity.getRootVehicle();
	}
	
	public static boolean isCloseToAnchorPos(Entity entity, Vec3 pos) {
		return entity.distanceToSqr(pos) <= 16;
	}
	
	public static void moveWithAnchor(Entity entity, Vec3 pos) {
    	entity.setDeltaMovement(pos.subtract(entity.position()).normalize().scale(0.75));
        entity.fallDistance = 0;
        entity.hurtMarked = true;
	}
	
	private static boolean canReplaceBlock(Level level, BlockPos blockPos, BlockState newBlockState) {
		BlockState currentBlockState = level.getBlockState(blockPos);
		float hardness = currentBlockState.getDestroySpeed(level, blockPos);
		return currentBlockState.canBeReplaced() || hardness >= 0 && hardness < newBlockState.getDestroySpeed(level, blockPos);
	}
}
