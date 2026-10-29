package rotp.addons.whitealbum.ability;

import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.util.functions.DamageUtil;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.whitealbum.WhiteAlbumAddon;
import rotp.addons.whitealbum.WhiteAlbumIce;
import rotp.addons.whitealbum.WhiteAlbumSpec;
import rotp.addons.whitealbum.entity.WhiteAlbumControllerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

public final class WhiteAlbumFreezeAbility
        extends StandEntityAbility {
    public WhiteAlbumFreezeAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId) {
        super(
                abilityType,
                abilityId,
                FreezeAction::new);
        setButtonHoldPhase(ActionPhase.PERFORM);
        // 1.16 wa_freeze set no standRecoveryTicks: a released Freeze ended
        // at once
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        cooldown(WhiteAlbumSpec.FREEZE_COOLDOWN_TICKS);
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck base =
                super.checkSpecificConditions(context);
        if (!base.isPositive()) {
            return base;
        }
        LivingEntity user = context.getUser();
        if (user == null) {
            return ConditionCheck.NEGATIVE;
        }
        if (user.level().dimensionType().ultraWarm()) {
            return ConditionCheck.createNegative("ultrawarm");
        }
        return base;
    }

    // 1.16 tickHeldAction reran FreezeProjectile.checkSpecificConditions
    // every held tick: an ultrawarm dimension ended the hold.
    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        return checkSpecificConditions(context);
    }

    private static final class FreezeAction
            extends EntityActionInstance {
        private boolean soundPlayed;

        private FreezeAction(EntityActionType ability) {
            super(ability);
            userWalkSpeed = 1.0F;
        }

        @Override
        public void actionTick() {
            if (getPhase() != ActionPhase.PERFORM
                    || !(level() instanceof ServerLevel level)
                    || !(getPerformer()
                            instanceof WhiteAlbumControllerEntity
                            controller)) {
                return;
            }

            LivingEntity user = getPowerUser();
            StandPower power =
                    user != null ? StandPower.get(user) : null;
            if (user == null
                    || power == null
                    || WhiteAlbumAddon.controller(user)
                            != controller) {
                startRecovery();
                return;
            }

            if (!soundPlayed) {
                StandUtil.playStandEntitySound(
                        controller,
                        WhiteAlbumAddon.WA_FREEZE,
                        1.0F,
                        1.0F);
                user.playSound(
                        WhiteAlbumAddon.USER_FREEZE.get(),
                        1.0F,
                        1.0F);
                soundPlayed = true;
            }

            freezeAura(level, user, controller);
            // No aimed-target freeze: 1.16 holdTick froze its target, but
            // PowerBaseImpl.checkTarget emptied the held target of this
            // action (TargetRequirement NONE, no standKeepsTarget) first.
            WhiteAlbumIce.freezeHeldWater(user, level);
            // 1.16 StandEntityTask paid staminaCostTick(8) and ignored the
            // result; GameplayHandler then let go of the freeze, silently,
            // once the bar read exactly 0.
            StandAbilityStamina.consume(
                    ability,
                    power,
                    WhiteAlbumSpec.FREEZE_STAMINA_PER_TICK,
                    true);
            if (power.getStamina() == 0.0F) {
                startRecovery();
            }
        }

        @Override
        public void onButtonStopHold() {
            startRecovery();
        }

        @Override
        public boolean canBeCancelledInto(
                EntityActionType cancellingAbility) {
            return true;
        }

        // 1.16 FreezeProjectile (StandEntityAction, no isChainable) refused its own press via canClickDuringTask
        @Override
        public boolean refusesOwnAbilityPressedAgain() {
            return true;
        }

        // 1.16 wa_freeze was a holdType Stand action: any other action of
        // the power that went off ended its hold (PowerBaseImpl.onClickAction).
        @Override
        public boolean isHoldTypeStandAction() {
            return true;
        }

        private static void freezeAura(
                ServerLevel level,
                LivingEntity user,
                WhiteAlbumControllerEntity controller) {
            for (LivingEntity target :
                    level.getEntitiesOfClass(
                            LivingEntity.class,
                            user.getBoundingBox().inflate(
                                    WhiteAlbumSpec.FREEZE_RADIUS),
                            target -> target.isAlive()
                                    && !target.isRemoved())) {
                float distance = target.distanceTo(controller);
                if (distance >= WhiteAlbumSpec.FREEZE_RADIUS
                        || target == user
                        || target == controller) {
                    continue;
                }
                float attack = (float) controller.getAttackDamage();
                float spread = (float) (
                        controller.getMaxRange() * 2.6D / 10.0D);
                float damage = (float) (
                        attack
                                * Math.exp(
                                        -(distance * distance)
                                                / (2.0F
                                                        * spread
                                                        * spread))
                                / 2.0D);

                if (target instanceof StandEntity standTarget) {
                    LivingEntity targetUser =
                            standTarget.getUser();
                    if (targetUser != null
                            && targetUser.distanceTo(controller)
                                    > WhiteAlbumSpec
                                            .FREEZE_RADIUS) {
                        DamageUtil.dealColdDamage(
                                targetUser,
                                damage,
                                user,
                                null);
                        targetUser.addEffect(
                                new MobEffectInstance(
                                        ModStatusEffects.FREEZE,
                                        100));
                    }
                }
                else if (!target.isOnFire()) {
                    DamageUtil.dealColdDamage(
                            target,
                            damage,
                            user,
                            null);
                }
            }
        }
    }
}
