package rotp.addons.shadowtheworld.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;

import rotp.addons.shadowtheworld.ShadowTheWorldAddon;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class ShadowCounterAbility
        extends StandEntityAbility {
    public static final int ACTIVE_TICKS = 1200;
    private static final ActionAnimIdentifier ANIM =
            ActionAnimIdentifier.getOrCreate("block", false);

    public ShadowCounterAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, CounterAction::new);
        // 1.16 holdType(): the Stand stays in the perform while the key is down; the task ends on release.
        setButtonHoldPhase(ActionPhase.PERFORM);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        cooldown(100);
        resolveLevelToUnlock(3);
        setIgnoresPerformerStun();
    }

    // 1.16 PowerBaseImpl.stopHeldAction: a holdOnly action's cooldown starts when the hold stops.
    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        return ANIM;
    }

    private static final class CounterAction
            extends EntityActionInstance {
        private CounterAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onButtonStopHold() {
            LivingEntity user = getPowerUser();
            if (!level().isClientSide() && user != null
                    && getPhase() != ActionPhase.RECOVERY
                    && ability instanceof ShadowCounterAbility counter) {
                counter.setCooldownOnUse(
                        StandPower.get(user), (int) getPhaseTick());
            }
            startRecovery();
        }

        // 1.16 TimeStopCounter was a holdType StandEntityAction with no recovery: its task was cancelable and
        // heldAllowsOtherAction true, so every other Stand action (the stun punch clicked at a counter hit, any action
        // pressed by hand) went through the hold at once, and any other action that went off ended it.
        @Override
        public boolean isHoldTypeStandAction() {
            return true;
        }

        // The action that replaced the hold went off, and stopHeldAction(false) then ended the hold: its cooldown
        // starts then, as at the key's release.
        @Override
        public void onActionCleared(EntityActionInstance newAction) {
            LivingEntity user = getPowerUser();
            if (!level().isClientSide() && user != null
                    && newAction != null
                    && getPhase() != ActionPhase.RECOVERY
                    && ability instanceof ShadowCounterAbility counter) {
                counter.setCooldownOnUse(
                        StandPower.get(user), (int) getPhaseTick());
            }
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel level)
                    || !(getPowerUser()
                            instanceof Player player)) {
                return;
            }
            long now = level.getGameTime();
            // 1.16 TimeStopCounter.standPerform returned silently while a counter was armed: no sound either
            if (!ShadowTheWorldAddon.state(player).activateCounter(
                    now, now + ACTIVE_TICKS)) {
                return;
            }
            // 1.16 TimeStopCounter: world.playSound(null, ...), the user hears it
            level.playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    ShadowTheWorldAddon
                            .SHADOW_WORLD_TIME_STOP_BLINK
                            .get(),
                    player.getSoundSource(),
                    0.2F,
                    1.9F);
        }
    }
}
