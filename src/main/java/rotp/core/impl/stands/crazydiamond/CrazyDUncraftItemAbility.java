package rotp.core.impl.stands.crazydiamond;

import java.util.Optional;

import javax.annotation.Nullable;

import rotp.core.client.input.AbilityInputState;
import rotp.core.client.input.InputHandler;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandEntityAbility.AutoSummonMode;
import rotp.core.util.functions.ItemUtil;
import rotp.core.util.functions_network.NetworkUtil;
import rotp.core.util.objects_mc.ContainerSlotInput;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.impl.stands.crazydiamond.CrazyDRepairItemAbility.ItemRepairResult;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class CrazyDUncraftItemAbility extends StandEntityAbility {
	private static final ActionAnimIdentifier ITEM_FIX_ANIM = ActionAnimIdentifier.getOrCreate("uncraft", false);

	public CrazyDUncraftItemAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, ItemUncraft::new);
		usageGroup = AbilityUsageGroup.INVENTORY;
		partsRequired(StandPart.ARMS);
		setButtonHoldPhase(ActionPhase.PERFORM);
		standAutoSummonMode(AutoSummonMode.OFF_ARM);
	}

	@Override
	public AbilityInputState cl_abilityInputState(Power<?> context) {
		AbilityInputState state = AbilityInputState.init();
		state.setFlag(AbilityInputState.ONLY_IN_CONTAINER, true);
		return state;
	}

	@Override
	public void writeExtraInput(FriendlyByteBuf serverboundBuf, LivingEntity user, boolean isClientPlayer) {
		if (isClientPlayer) {
			NetworkUtil.writeOptionally(ContainerSlotInput.cl_HoveredSlot(), serverboundBuf, ContainerSlotInput.STREAM_CODEC);
		}
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		LivingEntity user = context != null ? context.getUser() : null;
		if (user == null) {
			return ConditionCheck.NEGATIVE;
		}
		if (user.level().isClientSide()) {
			Screen screen = Minecraft.getInstance().screen;
			if (screen instanceof AbstractContainerScreen invScreen) {
				boolean active = false;
				if (!InputHandler.inputsDisabled) {
					Slot hovered = invScreen.getSlotUnderMouse();
					active = hovered != null && canBeUncrafted(hovered.getItem(), user.level(), user);
				}
				if (!active) {
					return ConditionCheck.NEGATIVE;
				}
			}
		}

		ConditionCheck check = super.checkSpecificConditions(context);
		return check.isPositive() ? StandAbilityStamina.check(context, CrazyDRepairItemAbility.REPAIR_STAMINA_COST_TICK) : check;
	}

	@Override
	public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
		return ITEM_FIX_ANIM;
	}

	public static class ItemUncraft extends EntityActionInstance {
		private Optional<ContainerSlotInput> inputInvSlot = Optional.empty();
		private ItemStack targetStack = ItemStack.EMPTY;

		public ItemUncraft(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void extraClientInput(FriendlyByteBuf input) {
			inputInvSlot = input != null ? NetworkUtil.readOptional(input, ContainerSlotInput.STREAM_CODEC) : Optional.empty();
		}

		@Override
		public void onActionSet(@Nullable EntityActionInstance prevAction) {
			if (inputInvSlot.isPresent() && powerUser.getEntity(level()) instanceof Player player) {
				targetStack = inputInvSlot.get().getItem(player);
			}
			if (performer instanceof StandEntity stand) {
				CrazyDRepairItemAbility.setItemFixStandOffset(this, stand);
			}
		}

		@Override
		public void actionTick() {
			if (getPhase() != ActionPhase.PERFORM) {
				return;
			}
			if (performer instanceof StandEntity stand) {
				CrazyDRepairItemAbility.setItemFixStandOffset(this, stand);
			}
			Level level = level();
			if (level.isClientSide()) {
				return;
			}
			if (!(performer instanceof StandEntity stand)) {
				startRecovery();
				return;
			}
			LivingEntity user = getPowerUser();
			if (user == null || !canBeUncrafted(targetStack, level, performer)) {
				setPhase(ActionPhase.RECOVERY, 0);
				syncPhaseChanges();
				return;
			}
			if (!CrazyDRepairItemAbility.consumeRepairStaminaTick(user)) {
				startRecovery();
				return;
			}

			int tick = curPhaseTick;
			ItemRepairResult repairProgress = CrazyDRepairItemAbility.repairTick(user, stand, targetStack, tick, false);
			if (!repairProgress.isRepairing && CrazyDRepairItemAbility.isItemTransformationTick(tick, stand)) {
				CrazyDPreviousStateItemConversion.convertTo(targetStack, level, null, performer.getRandom(), true).ifPresent(itemsAndCount -> {
					boolean gaveIngredients = false;
					for (ItemStack ingredient : itemsAndCount.getFirst()) {
						if (!ingredient.isEmpty()) {
							ItemUtil.giveItemTo(user, ingredient, true);
							gaveIngredients = true;
						}
					}
					if (gaveIngredients) {
						targetStack.shrink(itemsAndCount.getSecond());
					}
				});
				StandPower userPower = stand.getUserPower();
				if (userPower != null) {
					userPower.addExp(0.02f);
				}
			}
		}

		@Override
		public void applyStandUserRotation(StandEntity standEntity, LivingEntity user) {
			CrazyDRepairItemAbility.applyItemFixStandRotation(standEntity, user);
		}

		@Override
		public void onButtonStopHold() {
			startRecovery();
		}

		@Override
		public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
			return true;
		}
	}

	private static boolean canBeUncrafted(ItemStack itemStack, Level level, LivingEntity user) {
		return itemStack != null && !itemStack.isEmpty()
				&& CrazyDPreviousStateItemConversion.convertTo(itemStack, level, null, user.getRandom(), false).isPresent();
	}
}
