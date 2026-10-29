package rotp.addons.yellowtemperance.ability;

import java.util.Map;
import java.util.WeakHashMap;

import rotp.core.client.ClientProxy;
import rotp.core.client.input.ClientsideAim;
import rotp.core.client.sound.ClientsideSoundsHelper;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTargetRange;
import rotp.core.subsystems.target.StandPrecisionRay;
import rotp.core.util.functions.DamageUtil;
import rotp.core.impl.powers.hamon.abilities.HamonProtectionAbility;

import rotp.addons.yellowtemperance.YellowTemperanceAddon;
import rotp.addons.yellowtemperance.YellowTemperanceGameEvents;
import rotp.addons.yellowtemperance.YellowTemperancePhase1Spec;
import rotp.addons.yellowtemperance.effect.ErosionService;
import rotp.addons.yellowtemperance.entity.YellowTemperanceControllerEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.core.Holder;

public final class YellowTemperanceAbsorptionAbility
        extends NoPoseStandEntityAbility {
    public static final String CANNOT_ABSORB =
            "action.rotp_yellowtemperance.absorption.cannot_absorb";

    private final Map<Player, Integer> absorptionProgress = new WeakHashMap<>();

    public YellowTemperanceAbsorptionAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, AbsorptionAction::new);
        standAutoSummonMode(AutoSummonMode.DISABLED);
        // 1.16 YellowTemperanceAbsorption was a holdType StandAction whose
        // heldAllowsOtherAction was true: a pressed action ran at once and
        // ended it, so no recovery holds a press back
        setButtonHoldPhase(ActionPhase.PERFORM);
        cooldown(10);
        resolveLevelToUnlock(1);
    }

    // 1.16 yellow_temperance_absorption (075 InitStands :58) was a
    // StandAction its user's power held, the Stand out or not, never a Stand
    // task: it ran beside the Stand's task (an unsummon too)
    @Override
    public boolean performsInUsersStandPowerSlot() {
        return true;
    }

    // It had no autoSummonStand(): a press with Yellow Temperance not out
    // summoned nothing and the user's power held it (StandAction onClick
    // :263-267), which the core's Stand ability would refuse.
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

    // 1.16 PowerBaseImpl.checkRequirements: the crosshair target first
    // (ENTITY requirement, checkRangeAndTarget with the 7.5 range,
    // YellowTemperanceAbsorption.checkTarget living), then 'peaceful'. A
    // target refusal is NEGATIVE_CONTINUE_HOLD, which refused the press
    // (no pre-charge) but only paused a running hold.
    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        LivingEntity user = context.getUser();
        if (user == null) {
            return ConditionCheck.NEGATIVE;
        }
        YellowTemperanceControllerEntity controller =
                YellowTemperanceAddon.controller(user);
        Entity target = AbsorptionAction.heldTarget(
                user, controller, user.level()).getEntity();
        if (target == null) {
            return ConditionCheck.NEGATIVE;
        }
        if (!ActionTargetRange.isEntityWithinRange(
                ActionTargetRange.standPerformer(user), target,
                YellowTemperancePhase1Spec.ABSORPTION_RANGE_SQR)) {
            return ConditionCheck.createNegative("target_too_far");
        }
        if (!(target instanceof LivingEntity) || target == user) {
            return ConditionCheck.NEGATIVE;
        }
        ConditionCheck check = super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        return checkPeaceful(user);
    }

    // 1.16 tickHeldAction: 'peaceful' ended the hold with its message.
    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        LivingEntity user = context.getUser();
        return user != null
                ? checkPeaceful(user)
                : ConditionCheck.NEGATIVE;
    }

    private static ConditionCheck checkPeaceful(LivingEntity user) {
        return user.level().getDifficulty() == Difficulty.PEACEFUL
                ? ConditionCheck.createNegative("peaceful")
                : ConditionCheck.POSITIVE;
    }

    private boolean recordSuccessfulAbsorption(Player owner) {
        int hits = absorptionProgress.getOrDefault(owner, 0) + 1;
        if (hits >= 3) {
            absorptionProgress.remove(owner);
            return true;
        }
        absorptionProgress.put(owner, hits);
        return false;
    }

    private static final class AbsorptionAction
            extends EntityActionInstance {
        private AbsorptionAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onSetPhase(ActionPhase newPhase) {
            super.onSetPhase(newPhase);
            if (newPhase == ActionPhase.PERFORM
                    && level().isClientSide()) {
                LivingEntity user = getPowerUser();
                if (user != null) {
                    ClientsideSoundsHelper.playLoopingActionSound(
                            ModSoundEvents
                                    .PILLAR_MAN_ABSORPTION.get(),
                            user,
                            this,
                            ActionPhase.PERFORM,
                            1.25F,
                            0.8F);
                }
            }
        }

        @Override
        public void actionTick() {
            if (getPhase() != ActionPhase.PERFORM
                    || !(level() instanceof ServerLevel level)) {
                return;
            }
            LivingEntity user = getPowerUser();
            // 1.16 StandAction.getPerformer: the Stand while it is out, its
            // user otherwise (a hold pressed with the Stand not out)
            YellowTemperanceControllerEntity controller =
                    YellowTemperanceAddon.controller(user);
            ActionTarget fresh = heldTarget(user, controller, level);
            LivingEntity target = fresh.getMainEntity()
                    instanceof LivingEntity living
                            ? living
                            : null;
            if (level.getDifficulty() == Difficulty.PEACEFUL) {
                startRecovery();
                return;
            }
            if (!inReach(user, target, controller)) {
                return;
            }
            // 1.16: an unabsorbable target shows a message; the hold goes on.
            if (!canAbsorb(target)) {
                if (user instanceof Player player && target != user) {
                    player.displayClientMessage(
                            Component.translatable(CANNOT_ABSORB),
                            true);
                }
                return;
            }
            ErosionService.applyAbsorption(target, user);
            // 1.16: Hamon energy (dmg * 0.5) or a Hamon charge blocks the
            // tick; the helper sends the sparks and spark sound on the
            // user's aim. The hold keeps running.
            if (HamonProtectionAbility.preventBlockDamage(
                    target,
                    level.damageSources().indirectMagic(user, user),
                    YellowTemperancePhase1Spec.ABSORPTION_DAMAGE)) {
                return;
            }
            if (DamageUtil.hurtThroughInvulTicks(
                    target,
                    level.damageSources().magic(),
                    YellowTemperancePhase1Spec
                            .ABSORPTION_DAMAGE)) {
                extendDebuff(
                        target,
                        user,
                        MobEffects.MOVEMENT_SLOWDOWN);
                extendDebuff(
                        target,
                        user,
                        MobEffects.WEAKNESS);
                if (user instanceof Player player
                        && ability instanceof YellowTemperanceAbsorptionAbility absorption
                        && absorption.recordSuccessfulAbsorption(player)) {
                    player.getFoodData().eat(1, 0.1F);
                }
            }
        }

        @Override
        public void onButtonStopHold() {
            startRecovery();
        }

        @Override
        public boolean isHoldTypeStandAction() {
            return true;
        }

        // 1.16 YellowTemperanceAbsorption was a StandAction its user's power
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

        // 1.16 held StandAction: each tick the client's pick, the vanilla
        // pick (the user's synced aim) or, when that missed with the Stand
        // out, the Stand's precision ray at the user's 5 blocks
        // (EntityStandType.clientHitResult). No Stand reach cuts it; only
        // the range in inReach does.
        static ActionTarget heldTarget(
                LivingEntity user,
                YellowTemperanceControllerEntity controller,
                Level level) {
            if (user == null) {
                return ActionTarget.EMPTY;
            }
            ActionTarget target;
            // The user's aim only goes from its client to the server.
            if (level.isClientSide()
                    && user == ClientProxy.getClientPlayer()) {
                target = ClientsideAim.playerAim.getTarget()
                        .resolveEntityId(level);
            }
            else {
                var aim = LivingComponentAction.getAim(user);
                target = aim != null && aim.getTarget() != null
                        ? aim.getTarget().resolveEntityId(level)
                        : ActionTarget.EMPTY;
            }
            if (target.isEmpty(level) && controller != null) {
                target = StandPrecisionRay.trace(user, controller);
            }
            return target;
        }

        private static boolean inReach(
                LivingEntity user,
                LivingEntity target,
                YellowTemperanceControllerEntity controller) {
            return user != null && target != null
                    && ActionTargetRange.isEntityWithinRange(
                            controller != null ? controller : user, target,
                            YellowTemperancePhase1Spec
                                    .ABSORPTION_RANGE_SQR)
                    && YellowTemperanceGameEvents.canAffect(
                            user, target, controller);
        }

        private static boolean canAbsorb(LivingEntity target) {
            if (target instanceof Player player) {
                return !player.isCreative() && !player.isSpectator();
            }
            MobCategory category =
                    target.getType().getCategory();
            return category == MobCategory.CREATURE
                    || category == MobCategory.MONSTER
                    || target instanceof Villager;
        }

        private static void extendDebuff(
                LivingEntity target,
                LivingEntity user,
                Holder<MobEffect> effect) {
            MobEffectInstance current = target.getEffect(effect);
            target.addEffect(
                    new MobEffectInstance(
                            effect,
                            5 + (current != null
                                    ? current.getDuration()
                                    : 0),
                            1,
                            false,
                            false,
                            true),
                    user);
        }
    }
}
