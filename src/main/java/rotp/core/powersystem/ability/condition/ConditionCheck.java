package rotp.core.powersystem.ability.condition;

import javax.annotation.Nullable;

import rotp.core.powersystem.ability.Ability;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public class ConditionCheck {
	private final boolean positive;
	private final Component warning;
	private final boolean continueHold;
	
	public static final ConditionCheck POSITIVE = new ConditionCheck(true, null, false);
	public static final ConditionCheck NEGATIVE = new ConditionCheck(false, null, false);
	public static final ConditionCheck NEGATIVE_CONTINUE_HOLD = new ConditionCheck(false, null, true);
	/**
	 * The silent refusal of the running action's own ability pressed again while its power still holds that action by
	 * the key: a press is refused as by {@link #NEGATIVE}, but the slot is drawn usable
	 * ({@link AvailableAbilities.AbilityConditionCheck#drawnCondition}), as 1.16 refused that press in
	 * PowerBaseImpl.onClickAction and not by the action's checkRequirements.
	 */
	public static final ConditionCheck NEGATIVE_OWN_HELD_PRESSED_AGAIN = new ConditionCheck(false, null, false);

	public static ConditionCheck createNegative(Component warning) {
		return new ConditionCheck(false, warning, false);
	}
	
	public static ConditionCheck createNegative(String warningPostfix) {
		return new ConditionCheck(false, message(warningPostfix), false);
	}
	
	public static MutableComponent message(String warningPostfix) {
		return Component.translatable("jojo.message.action_condition." + warningPostfix);
	}
	
	public static ConditionCheck noMessage(boolean isPositive) {
		return isPositive ? POSITIVE : NEGATIVE;
	}
	
	private ConditionCheck(boolean positive, Component warning, boolean continueHold) {
		this.positive = positive;
		this.warning = warning;
		this.continueHold = continueHold;
	}
	
	public boolean isPositive() {
		return positive;
	}
	
	@Nullable
	public Component getWarning() {
		return warning;
	}

	public ConditionCheck setContinueHold() {
		return setContinueHold(true);
	}

	public ConditionCheck setContinueHold(boolean continueHold) {
		return new ConditionCheck(positive, warning, continueHold);
	}

	public boolean shouldContinueHold() {
		return !positive && continueHold;
	}
	
	public static void sendActionFailedMessage(@Nullable Ability ability, ConditionCheck result, LivingEntity user) {
		if (!user.level().isClientSide() && (ability == null || ability.sendsConditionMessage())) {
			Component message = result.getWarning();
			
			if (message != null && user instanceof ServerPlayer player) {
				player.displayClientMessage(message, true);
			}
		}
	}

}
