package com.github.standobyte.jojoimpl.stands.crazydiamond;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.entityattachment.syncheddata.SynchedDataBuilder;
import com.github.standobyte.jojo.entityattachment.syncheddata.SyncedDataHolderExtended;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojo.util.functions.DamageUtil;
import com.github.standobyte.jojo.util.functions.ItemUtil;
import com.github.standobyte.jojoimpl.stands._entitybase.StandEntityHeavyPunchAbility;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;

public class CrazyDiamondHeavyPunchAbility extends StandEntityHeavyPunchAbility {

	public CrazyDiamondHeavyPunchAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId);
		this.createActionObj = CrazyDiamondHeavyPunch::new;
	}

	public static class CrazyDiamondHeavyPunch extends StandEntityHeavyPunchAbility.StandEntityHeavyPunch implements SyncedDataHolderExtended {
		private static final EntityDataAccessor<Integer> MISSHAPING_TARGET_HIT_PART = SynchedEntityData.defineId(
				CrazyDiamondHeavyPunch.class, EntityDataSerializers.INT);

		public CrazyDiamondHeavyPunch(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onActionSet(EntityActionInstance prevAction) {
			super.onActionSet(prevAction);
			if (!level().isClientSide() && getPerformer() instanceof StandEntity stand) {
				LivingEntity user = getPowerUser();
				if (user != null) {
					ItemStack item = user.getOffhandItem();
					if (!item.isEmpty() && CrazyDLeaveObjectPunchEffect.canUseItem(item)) {
						takeStandItem(stand, item.split(1), user);
					}
				}
			}
		}

		public void updateMisshapingTargetPart(StandEntity stand, LivingEntity user) {
			ActionTarget target = standRotationTarget != null ? standRotationTarget.resolveEntityId(stand.level()) : getPunchTarget(stand);
			Entity targetEntity = target.getMainEntity();
			if (targetEntity instanceof LivingEntity livingTarget) {
				LivingEntity aimingEntity = user != null ? user : stand;
				setMisshapingTargetPart(CrazyDTargetHitPart.getHitTarget(livingTarget, aimingEntity));
			}
			else {
				setMisshapingTargetPart(null);
			}
		}

		@Nullable
		public CrazyDTargetHitPart getMisshapingTargetPart() {
			return CrazyDTargetHitPart.fromSyncId(synchedData.get(MISSHAPING_TARGET_HIT_PART));
		}

		private void setMisshapingTargetPart(@Nullable CrazyDTargetHitPart hitPart) {
			synchedData.set(MISSHAPING_TARGET_HIT_PART,
					hitPart != null ? hitPart.syncId() : CrazyDTargetHitPart.NONE_SYNC_ID);
		}

		@Override
		@Nullable
		public Vec3 getStandLookTargetPosition(StandEntity standEntity, ActionTarget target) {
			CrazyDTargetHitPart hitPart = getMisshapingTargetPart();
			Entity targetEntity = target.getMainEntity();
			if (hitPart != null && targetEntity instanceof LivingEntity livingTarget) {
				return hitPart.getPartCenter(livingTarget);
			}
			return super.getStandLookTargetPosition(standEntity, target);
		}

		@Override
		public void onActionCleared(@Nullable EntityActionInstance newAction) {
			if (!level().isClientSide() && getPerformer() instanceof StandEntity stand) {
				returnStandItem(stand, getPowerUser());
			}
			super.onActionCleared(newAction);
		}

		@Override
		protected void hitEntity(ActionTarget target, Level level, StandEntity stand,
				DamageSource dmgSource, float dmgAmount, float explRadius) {
			if (target.getMainEntity() instanceof LivingEntity targetLiving) {
				float armorPiercing = (float) stand.getAttackDamage() * 0.01F;
				dmgAmount = DamageUtil.addArmorPiercing(dmgAmount, armorPiercing, targetLiving, dmgSource);
			}
			super.hitEntity(target, level, stand, dmgSource, dmgAmount, explRadius);
		}

		private static void takeStandItem(StandEntity stand, ItemStack item, LivingEntity user) {
			if (item.isEmpty()) {
				return;
			}
			ItemStack heldItem = stand.getItemBySlot(EquipmentSlot.MAINHAND);
			if (!heldItem.isEmpty()) {
				if (ItemStack.isSameItemSameComponents(heldItem, item)) {
					int toMove = Math.min(item.getCount(), heldItem.getMaxStackSize() - heldItem.getCount());
					item.shrink(toMove);
					heldItem.grow(toMove);
					if (!item.isEmpty()) {
						ItemUtil.giveItemTo(user, item, true);
					}
				}
				else {
					returnStandItem(stand, user);
					stand.setItemSlot(EquipmentSlot.MAINHAND, item);
				}
			}
			else {
				stand.setItemSlot(EquipmentSlot.MAINHAND, item);
			}
		}

		private static void returnStandItem(StandEntity stand, @Nullable LivingEntity user) {
			ItemStack heldItem = stand.getItemBySlot(EquipmentSlot.MAINHAND);
			if (!heldItem.isEmpty()) {
				stand.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
				ItemUtil.giveItemTo(user != null ? user : stand, heldItem, true);
			}
		}

		@Override
		public void defineSynchedData(SynchedDataBuilder builder) {
			builder.define(MISSHAPING_TARGET_HIT_PART, CrazyDTargetHitPart.NONE_SYNC_ID);
		}

		@Override
		public <T> void onSyncedDataUpdated(T oldValue, T newValue, EntityDataAccessor<T> dataAccessor) {}
	}
}
