package rotp.core.impl.stands._entitybase;

import rotp.core.client.ClientProxy;
import rotp.core.client.input.InputHandler;
import rotp.core.core.JojoMod;
import rotp.core.entityattachment.ComponentUtil;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntity.StandFlag;
import rotp.core.subsystems.entity_puppetcontrol.EntityComponentController;
import rotp.core.subsystems.entity_puppetcontrol.client.ClientEntityController;
import rotp.core.subsystems.entity_puppetcontrol.client.stand.ClientStandController;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class StandEntityManualControlToggle extends Ability {

	public StandEntityManualControlToggle(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId);
		usageGroup = AbilityUsageGroup.UTILITY;
	}
	
	@Override
	public void writeExtraInput(FriendlyByteBuf serverboundBuf, LivingEntity user, boolean isClientPlayer) {
		if (isClientPlayer) {
			boolean shift = InputHandler.getInstance().isKeyHeld(InputConstants.KEY_LSHIFT);
			serverboundBuf.writeBoolean(shift);
		}
	}

	@Override
	public boolean isAbilityAvailable(Power<?> context) {
		return super.isAbilityAvailable(context) && canUseManualControl(context);
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		return canUseManualControl(context)
				? ConditionCheck.POSITIVE
				: ConditionCheck.createNegative("manual_control_disabled");
	}
	
	private static boolean canUseManualControl(Power<?> context) {
		return context instanceof StandPower standPower
				&& standPower.getPowerType() instanceof EntityStandType standType
				&& standType.canBeManuallyControlled();
	}
	
	@Override
	public void onClick(Level level, LivingEntity user, FriendlyByteBuf extraClientInput) {
		if (!level.isClientSide()) {
			boolean shift = readKeepPositionInput(extraClientInput, user);
			StandEntity stand = StandUtil.getSummonedStand(user);
			if (stand != null) {
				if (!stand.isManuallyControlled() && EntityComponentController.getControlTarget(user) != stand) {
					on(level, stand);
				}
				else {
					off(level, stand, shift);
				}
			}
		}
	}

	private static boolean readKeepPositionInput(FriendlyByteBuf extraClientInput, LivingEntity user) {
		if (extraClientInput == null || extraClientInput.readableBytes() <= 0) {
			return user.isShiftKeyDown();
		}
		try {
			return extraClientInput.readBoolean();
		}
		catch (RuntimeException e) {
			JojoMod.getLogger().warn("Ignoring malformed stand manual control input from {}.",
					user.getName().getString(), e);
			return user.isShiftKeyDown();
		}
	}
	
	public static void on(Level level, StandEntity stand) {
		LivingEntity user = stand.getUser();
		if (user == null) {
			return;
		}
		stand.setManuallyControlled(true);
		// The client-facing getter depends on an existing controller, so inspect the accepted flag.
		if (!stand.getStandFlag(StandFlag.MANUAL_CONTROL)) {
			return;
		}
		stand.setCanFollowUser(true);
		if (level.isClientSide()) {
			if (user == ClientProxy.getClientPlayer()) {
				ClientEntityController.setInstance(new ClientStandController(stand));
			}
		}
		else {
			EntityComponentController.setControlTarget(user, stand, "stand");
		}
	}
	
	public static void off(Level level, StandEntity stand, boolean keepPosition) {
		stand.setCanFollowUser(!keepPosition);
		stand.setManuallyControlled(false);
		LivingEntity user = stand.getUser();
		if (user == null) {
			return;
		}
		if (level.isClientSide()) {
			if (user == ClientProxy.getClientPlayer() && ClientEntityController.isBeingControlledByClient(stand)) {
				ClientEntityController.setInstance(null);
			}
		}
		else {
			EntityComponentController component = ComponentUtil.getExistingDataOrNull(user, ModDataAttachmentTypes.CONTROLLER);
			if (component != null && component.getControlTarget() == stand) {
				component.stopControlling();
			}
		}
	}

}
