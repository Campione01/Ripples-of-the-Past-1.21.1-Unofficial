package rotp.core.impl.stands.crazydiamond;

import javax.annotation.Nullable;

import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.DamageUtil;
import rotp.core.util.functions.ItemUtil;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class CrazyDiamondHeavyPunchAbility extends StandEntityHeavyPunchAbility {

	public CrazyDiamondHeavyPunchAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId);
		this.createActionObj = CrazyDiamondHeavyPunch::new;
	}

	/*
	 * 1.16 ModStandsInit: the leave-object follow-up belonged to the base heavy punch (CRAZY_DIAMOND_HEAVY_PUNCH) and the
	 * misshape follow-ups to its finisher variation (CRAZY_DIAMOND_FINISHER_PUNCH); StandAction.replaceActionKostyl gave
	 * them only while that action was the Stand's task and the one the heavy key resolved to.
	 */
	@Nullable
	static StandEntityHeavyPunch runningHeavyPunch(@Nullable StandPower standPower, boolean finisher) {
		StandEntity stand = standPower != null ? standPower.getSummonedStandEntity() : null;
		EntityActionInstance action = stand != null ? stand.getCurStandAction() : null;
		return action instanceof StandEntityHeavyPunch punch && punch.getPunchModifiers().isEmpty()
				&& punch.ability instanceof Ability punchAbility && (punchAbility.isStandFinisherOf != null) == finisher
						? punch : null;
	}

	static boolean heavyKeyResolvesTo(AvailableAbilities abilities, StandEntityHeavyPunch punch) {
		Ability key = abilities.getContextVariation("heavy_punch");
		return key != null && key.getAbilityId().equals(punch.ability.getAbilityId());
	}

	public static class CrazyDiamondHeavyPunch extends StandEntityHeavyPunchAbility.StandEntityHeavyPunch {

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
	}
}
