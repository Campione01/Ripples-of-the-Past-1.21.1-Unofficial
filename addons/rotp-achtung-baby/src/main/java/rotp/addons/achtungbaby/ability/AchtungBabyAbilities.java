package rotp.addons.achtungbaby.ability;

import java.util.function.Function;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;

import rotp.addons.achtungbaby.AchtungBabyAddon;
import rotp.addons.achtungbaby.AchtungBabyService;
import rotp.addons.achtungbaby.entity.InvisibilityRuneEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

// Every 1.16 Achtung Baby action overrode getCooldownAdditional without calling super, so neither Resolve nor
// Creative shortened these cooldowns: they are registered as cooldown(n, 0) with the 1.16 values - item 60, blood 100,
// active 300, ghost 0, perfect 10, and grant 10 or the rest of its penalty window (GrantInvisibility.getCooldown) -
// and, as the builders had no cooldown, report no technical part (AdditionalOnlyCooldown).
// Stamina is 1.16's too (AchtungBabyService): Active a fifth of the maximum, Grant a tenth, Item a twentieth per item.
public final class AchtungBabyAbilities {
    private AchtungBabyAbilities() {}

    abstract static class AdditionalOnlyCooldown extends NoPoseStandEntityAbility {
        AdditionalOnlyCooldown(AbilityType<?> type, AbilityId id,
                Function<EntityActionType, ? extends EntityActionInstance> createActionObj) {
            super(type, id, createActionObj);
        }

        // cooldown(n, 0) keeps the total in Resolve and in Creative; 1.16's technical part was 0.
        @Override
        public int getCooldownTechnical(Power<?> context) {
            return 0;
        }
    }

    public static final class ItemInvisibility
            extends AdditionalOnlyCooldown {
        public ItemInvisibility(AbilityType<?> type, AbilityId id) {
            super(type, id, ItemAction::new);
            setDefaultPhaseLength(ActionPhase.PERFORM, 1);
            setDefaultPhaseLength(ActionPhase.RECOVERY, 20);
            cooldown(60, 0);
        }
    }

    public static final class ItemAction extends EntityActionInstance {
        public ItemAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user == null) {
                return;
            }
            InteractionHand hand = user.getMainHandItem().isEmpty()
                    ? InteractionHand.OFF_HAND
                    : InteractionHand.MAIN_HAND;
            ItemStack stack = user.getItemInHand(hand);
            if (stack.isEmpty()) {
                user.sendSystemMessage(Component.translatable(
                        "rotp_ab.message.no_item"));
                return;
            }
            // 1.16: making an item visible again is free; making it invisible is paid per item.
            if (AchtungBabyService.isInvisible(stack)) {
                AchtungBabyService.toggleItem(stack);
            } else if (!AchtungBabyService.makeInvisible(user, hand)) {
                return;
            }
            user.swing(hand, true);
            // Port-only feedback. A player's own playSound skips that player,
            // so the sound goes out from the level for the user to hear too.
            user.level().playSound(null, user.getX(), user.getY(), user.getZ(),
                    AchtungBabyAddon.VOID.get(), user.getSoundSource(), 0.65F, 1.25F);
        }
    }

    public static final class BloodSplash
            extends AdditionalOnlyCooldown {
        public BloodSplash(AbilityType<?> type, AbilityId id) {
            super(type, id, BloodAction::new);
            setDefaultPhaseLength(ActionPhase.PERFORM, 1);
            setDefaultPhaseLength(ActionPhase.RECOVERY, 20);
            cooldown(100, 0);
        }
    }

    public static final class BloodAction extends EntityActionInstance {
        public BloodAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel server)) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user != null) {
                server.sendParticles(
                        ParticleTypes.FALLING_LAVA,
                        user.getX(),
                        user.getY() + user.getBbHeight() * 0.75D,
                        user.getZ(),
                        24,
                        1.5D,
                        0.8D,
                        1.5D,
                        0.01D);
            }
        }
    }

    public static final class ActiveInvisibility
            extends AdditionalOnlyCooldown {
        public ActiveInvisibility(AbilityType<?> type, AbilityId id) {
            super(type, id, ActiveAction::new);
            setDefaultPhaseLength(ActionPhase.PERFORM, 1);
            setDefaultPhaseLength(ActionPhase.RECOVERY, 20);
            cooldown(300, 0);
        }

        // 1.16 checkSpecificConditions: unusable below a fifth of max stamina.
        @Override
        public ConditionCheck checkSpecificConditions(Power<?> context) {
            ConditionCheck base = super.checkSpecificConditions(context);
            if (base.isPositive()
                    && AchtungBabyService.lacksStamina(
                            context,
                            AchtungBabyService.ACTIVE_STAMINA_FRACTION)) {
                return ConditionCheck.NEGATIVE;
            }
            return base;
        }
    }

    public static final class ActiveAction extends EntityActionInstance {
        public ActiveAction(EntityActionType ability) {
            super(ability);
        }

        // 1.16 standPerform: the stamina is paid when the rune is fired, and without it no rune.
        @Override
        public void actionPerformStart() {
            if (!level().isClientSide()) {
                LivingEntity user = getPowerUser();
                if (user != null
                        && AchtungBabyService.payStamina(
                                user,
                                AchtungBabyService.ACTIVE_STAMINA_FRACTION)) {
                    InvisibilityRuneEntity.launch(user, true);
                }
            }
        }
    }

    public static final class GhostMode
            extends NoPoseStandEntityAbility {
        public GhostMode(AbilityType<?> type, AbilityId id) {
            super(type, id, GhostAction::new);
            setButtonHoldPhase(ActionPhase.PERFORM);
            setDefaultPhaseLength(ActionPhase.RECOVERY, 10);
            cooldown(0, 0);
        }

        // 1.16 GhostModeAction.checkSpecificConditions: refused during the unpaid-upkeep lockout or below an eighth
        // of max stamina, so a refused press does not start the action at all.
        @Override
        public ConditionCheck checkSpecificConditions(Power<?> context) {
            ConditionCheck base = super.checkSpecificConditions(context);
            if (base.isPositive() && AchtungBabyService.ghostRefused(context)) {
                return ConditionCheck.NEGATIVE;
            }
            return base;
        }
    }

    public static final class GhostAction extends EntityActionInstance {
        public GhostAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (getPowerUser() instanceof ServerPlayer player) {
                AchtungBabyService.toggleGhost(player);
            }
        }

        @Override
        public void onButtonStopHold() {
            startRecovery();
        }

        // Deliberately no onActionCleared teardown. 1.16 made ghost mode a toggle whose lifetime was owned by a
        // server tick - stamina upkeep, a one-minute cap, a lockout if the upkeep went unpaid. The port ended it
        // from here instead, and since entering spectator unsummons the Stand, the action cleared and ghost mode
        // switched itself off one to three ticks after it started. AchtungBabyService.tickGhost owns it now;
        // releasing the button must not touch it.
    }

    public static final class PerfectInvisibility
            extends AdditionalOnlyCooldown {
        public PerfectInvisibility(AbilityType<?> type, AbilityId id) {
            super(type, id, PerfectAction::new);
            setDefaultPhaseLength(ActionPhase.PERFORM, 1);
            setDefaultPhaseLength(ActionPhase.RECOVERY, 10);
            cooldown(10, 0);
        }

        // The sneak branch is a kept port addition (no donor action to follow): it hides whatever living thing the
        // crosshair is on, a teammate or the user's own pet too, which the Stand's attack check would refuse.
        @Override
        public boolean takesPressedTarget(StandEntity standEntity, ActionTarget target) {
            return true;
        }
    }

    public static final class PerfectAction extends EntityActionInstance {
        public PerfectAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(EntityActionInstance previousAction) {
            LivingEntity user = getPowerUser();
            if (user != null) {
                captureActionTargetFromAim(user);
            }
        }

        @Override
        public void actionPerformStart() {
            if (!(getPowerUser() instanceof ServerPlayer player)) {
                return;
            }
            if (player.isCrouching()) {
                Entity target = getActionTargetSnapshot(level())
                        .getMainEntity();
                if (target instanceof LivingEntity living
                        && living != player
                        && player.distanceToSqr(living) <= 36.0D) {
                    living.addEffect(new MobEffectInstance(
                            MobEffects.INVISIBILITY,
                            240,
                            0,
                            false,
                            false,
                            true));
                } else {
                    player.sendSystemMessage(Component.translatable(
                            "rotp_ab.message.no_target"));
                }
            } else {
                AchtungBabyService.togglePerfect(player);
            }
        }
    }

    public static final class GrantInvisibility
            extends AdditionalOnlyCooldown {
        public GrantInvisibility(AbilityType<?> type, AbilityId id) {
            super(type, id, GrantAction::new);
            setDefaultPhaseLength(ActionPhase.PERFORM, 10);
            setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
            cooldown(10, 0);
        }

        // 1.16 GrantInvisibilityAction.getCooldownAdditional: the rest of the
        // penalty window while one runs, 10 otherwise.
        @Override
        public int getCooldown(Power<?> context, int ticksHeld) {
            int cooldown = super.getCooldown(context, ticksHeld);
            if (cooldown > 0
                    && context != null
                    && context.getUser() instanceof Player player) {
                int penalty = AchtungBabyService.grantPenaltyTicksLeft(player);
                if (penalty > 0) {
                    return penalty;
                }
            }
            return cooldown;
        }

        // 1.16 checkSpecificConditions: unusable below a tenth of max stamina
        // or while the penalty runs.
        @Override
        public ConditionCheck checkSpecificConditions(Power<?> context) {
            ConditionCheck base = super.checkSpecificConditions(context);
            if (base.isPositive()
                    && AchtungBabyService.lacksStamina(
                            context,
                            AchtungBabyService.GRANT_STAMINA_FRACTION)) {
                return ConditionCheck.NEGATIVE;
            }
            if (base.isPositive()
                    && context != null
                    && context.getUser() instanceof Player player
                    && AchtungBabyService.grantPenaltyTicksLeft(player) > 0) {
                return ConditionCheck.NEGATIVE;
            }
            return base;
        }
    }

    public static final class GrantAction extends EntityActionInstance {
        public GrantAction(EntityActionType ability) {
            super(ability);
        }

        // 1.16 standPerform: players only; the stamina is paid when the rune is fired, and without it no rune.
        @Override
        public void actionPerformStart() {
            if (!level().isClientSide()
                    && getPowerUser() instanceof Player player
                    && AchtungBabyService.payStamina(
                            player,
                            AchtungBabyService.GRANT_STAMINA_FRACTION)) {
                AchtungBabyService.countGrantUse(player);
                InvisibilityRuneEntity.launch(player, false);
            }
        }
    }
}
