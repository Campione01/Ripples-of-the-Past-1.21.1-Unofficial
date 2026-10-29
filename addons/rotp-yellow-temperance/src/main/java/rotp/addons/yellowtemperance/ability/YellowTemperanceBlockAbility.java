package rotp.addons.yellowtemperance.ability;

import java.util.Map;
import java.util.WeakHashMap;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.yellowtemperance.YellowTemperanceAddon;
import rotp.addons.yellowtemperance.YellowTemperanceConfig;
import rotp.addons.yellowtemperance.YellowTemperancePhase1Spec;
import rotp.addons.yellowtemperance.entity.YellowTemperanceControllerEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class YellowTemperanceBlockAbility
        extends NoPoseStandEntityAbility {
    // server game time of each user's latest guard tick that protected it
    private static final Map<LivingEntity, Long> PROTECTED_AT =
            new WeakHashMap<>();

    public YellowTemperanceBlockAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, BlockAction::new);
        standAutoSummonMode(AutoSummonMode.DISABLED);
        // 1.16 YellowTemperanceBlock was a plain held StandAction: nothing
        // was left running once it was let go, so no recovery
        setButtonHoldPhase(ActionPhase.PERFORM);
        cooldown(10);
        standUserWalkSpeed(0.5F);
    }

    // 1.16 yellow_temperance_block (075 InitStands :57) was a StandAction its
    // user's power held, the Stand out or not, never a Stand task: it ran
    // beside the Stand's task (an unsummon too) instead of replacing it
    @Override
    public boolean performsInUsersStandPowerSlot() {
        return true;
    }

    // It had no autoSummonStand(): a press with Yellow Temperance not out
    // summoned nothing and the user's power held the guard (StandAction
    // onClick :263-267), which the core's Stand ability would refuse.
    @Override
    public HeldInput onKeyPress(Level level, LivingEntity user,
            FriendlyByteBuf extraClientInput, InputMethod inputMethod,
            float clickHoldResolveTime, BufferingState bufferingState) {
        if (!level.isClientSide()
                && StandUtil.getSummonedStand(user) == null) {
            return setOrBufferAction(level, user, user, inputMethod,
                    extraClientInput, clickHoldResolveTime, bufferingState);
        }
        return super.onKeyPress(level, user, extraClientInput,
                inputMethod, clickHoldResolveTime, bufferingState);
    }

    // 1.16 StandAction.getPerformer :191-193: the Stand while it is out,
    // else the user, whose stun the press then checked
    @Override
    protected LivingEntity getPerformer(LivingEntity user) {
        LivingEntity stand = super.getPerformer(user);
        return stand != null ? stand : user;
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
        return action == null
                        || action.getPerformer() instanceof StandEntity
                ? super.getEntityAnim(action)
                : null;
    }

    // 1.16 holdTick ran in the player's tick, after the world's entities and
    // so after the Stand's tick that took the protection off during the
    // unsummon (075 YellowTemperanceEntity.tick :44-46): a held guard
    // protected from there until the Stand's next tick. The guard ticks here
    // with the player in the entity loop, so the player's own tick puts back
    // the protection this tick's guard gave.
    public static void reapplyGuardProtection(Player player) {
        Long protectedAt = PROTECTED_AT.get(player);
        if (protectedAt != null
                && protectedAt == player.level().getGameTime()) {
            protect(player);
        }
    }

    // 1.16 holdTick :37 used the 5-argument EffectInstance, whose icon
    // follows visible (false): no HUD icon for the guard
    private static void protect(LivingEntity user) {
        user.addEffect(
                new MobEffectInstance(
                        YellowTemperanceAddon.PROTECTION,
                        2,
                        YellowTemperanceConfig
                                .BLOCK_PROTECTION_LEVEL.get(),
                        false,
                        false,
                        false),
                user);
    }

    private static final class BlockAction
            extends EntityActionInstance {
        private BlockAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionTick() {
            if (getPhase() != ActionPhase.PERFORM
                    || !(level() instanceof ServerLevel)) {
                return;
            }
            LivingEntity user = getPowerUser();
            StandPower power =
                    user != null ? StandPower.get(user) : null;
            // the controller while Yellow Temperance is out, else the user
            LivingEntity performer = getPerformer();
            if (user == null || performer == null
                    || performer != user
                            && YellowTemperanceAddon.controller(user)
                                    != performer) {
                setBlocking(false);
                startRecovery();
                return;
            }
            setBlocking(true);
            protect(user);
            PROTECTED_AT.put(user, level().getGameTime());
            // 1.16 holdTick: protect first, then pay 10; a failed payment
            // let go of the guard silently. No check before the hold.
            if (!StandAbilityStamina.consume(
                    ability,
                    power,
                    YellowTemperancePhase1Spec.BLOCK_STAMINA_PER_TICK,
                    false)) {
                setBlocking(false);
                startRecovery();
            }
        }

        @Override
        public void onButtonStopHold() {
            setBlocking(false);
            startRecovery();
        }

        @Override
        public void onActionCleared(EntityActionInstance newAction) {
            setBlocking(false);
        }

        // 1.16 YellowTemperanceBlock was a StandAction its user's power
        // held, which the Stand's summon lock never held back
        @Override
        public boolean isHeldBackBySummonLock() {
            return false;
        }

        // 1.16 EntityStandType.getStaminaRegen: a StandAction was no Stand
        // task, so the Stand out still regenerated 1.5 a tick
        @Override
        public boolean canStaminaRegen(
                StandPower standPower, StandEntity standEntity) {
            return true;
        }

        private void setBlocking(boolean active) {
            if (!level().isClientSide()
                    && getPerformer()
                            instanceof YellowTemperanceControllerEntity
                            controller) {
                controller.setYellowBlocking(active);
            }
        }
    }
}
