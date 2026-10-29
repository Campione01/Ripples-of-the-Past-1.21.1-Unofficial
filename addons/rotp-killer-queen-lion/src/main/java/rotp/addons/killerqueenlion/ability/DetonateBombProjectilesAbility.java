package rotp.addons.killerqueenlion.ability;

import net.minecraft.world.level.Level;
import net.minecraft.world.entity.LivingEntity;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.subsystems.target.ActionTarget;

import rotp.addons.killerqueenlion.KillerQueenLionAddon;
import rotp.addons.killerqueenlion.KillerQueenPhase1Spec;
import rotp.addons.killerqueenlion.entity.KillerQueenBombProjectile;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public final class DetonateBombProjectilesAbility
        extends StandEntityAbility {
    public static final int PERFORM_TICKS = 20;
    public static final double LEGACY_RANGE = 20.0D;

    public DetonateBombProjectilesAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, DetonateAction::new);
        usageGroup = AbilityUsageGroup.COMBAT;
        setDefaultPhaseLength(
                ActionPhase.PERFORM, PERFORM_TICKS);
        partsRequired(StandPart.ARMS);
        // 1.16 StandEntityLightAttack.Builder :193: pressed with Killer Queen
        // away, only its main arm comes out
        standAutoSummonMode(AutoSummonMode.MAIN_ARM);
    }

    @Override
    public boolean isAbilityAvailable(Power<?> context) {
        return super.isAbilityAvailable(context)
                && context.getUser() instanceof Player player
                && hasProjectile(player);
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        if (BitesTheDustLock.applies(context)) {
            return ConditionCheck.NEGATIVE;
        }
        ConditionCheck check =
                super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        if (!(context.getUser() instanceof Player player)
                || !hasProjectile(player)) {
            return ConditionCheck.createNegative(
                    "killer_queen_no_projectile_bomb");
        }
        return ConditionCheck.POSITIVE;
    }

    private static boolean hasProjectile(Player owner) {
        return !owner.level().getEntitiesOfClass(
                        KillerQueenBombProjectile.class,
                        owner.getBoundingBox().inflate(
                                LEGACY_RANGE),
                        projectile -> projectile.isOwnedBy(
                                owner.getUUID()))
                .isEmpty();
    }

    // 1.16 StandEntityLightAttack.standKeepsTarget: its task held what it
    // was pressed at, of any kind
    @Override
    protected boolean keepsTaskTargetType(
            StandEntity standEntity, ActionTarget target) {
        return true;
    }

    // 1.16 StandEntityLightAttack.lockOnTargetPosition
    @Override
    public boolean lockOnTargetPosition(StandPower standPower,
            StandEntity standEntity, EntityActionInstance action) {
        return false;
    }

    // 1.16 ExplodeSnow (snow_explode): a StandEntityLightAttack (KillerQueenLightAttack)
    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity powerUser,
            LivingEntity performer) {
        super.initActionFromConfig(
                action, level, powerUser, performer);
        KillerQueenLightAttack.setPhases(this, action, level, performer,
                KillerQueenLightAttack.Windup.LIGHT);
    }

    public static final class DetonateAction
            extends EntityActionInstance {
        public DetonateAction(EntityActionType ability) {
            super(ability);
        }

        private final KillerQueenLightAttack.SwingSound swingSound =
                new KillerQueenLightAttack.SwingSound();

        @Override
        public void actionTick() {
            super.actionTick();
            swingSound.tick(this);
        }

        // 1.16 StandEntityLightAttack: any action, this one too, replaced it
        // in its recovery
        @Override
        public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
            return KillerQueenLightAttack.givesWay(this);
        }

        // 1.16 StandEntityLightAttack: the task held the pressed target,
        // which the Stand turned to and stood before its user toward
        @Override
        public void onActionSet(EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            if (!level().isClientSide()
                    && getPerformer() instanceof StandEntity stand) {
                KillerQueenLightAttack.alternateHands(this, stand);
                ActionTarget target = captureActionTargetFromAim(stand);
                KillerQueenLightAttack.standAtFront(this, stand, target);
                keepStandAimedAtTarget(target);
            }
        }

        @Override
        protected void onTaskTargetInherited(ActionTarget target) {
            super.onTaskTargetInherited(target);
            if (getPerformer() instanceof StandEntity stand) {
                KillerQueenLightAttack.standAtFront(this, stand, target);
            }
        }

        @Override
        public void updateStandOffset(StandEntity stand) {
            KillerQueenLightAttack.refreshAtFront(this, stand);
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(getPerformer() instanceof StandEntity stand)
                    || !(getPowerUser()
                            instanceof ServerPlayer user)) {
                return;
            }
            // 1.16 snow_explode standSound(KQ_BOMB) (InitStands :95): at
            // the Stand as the perform began, whatever the perform found
            if (KillerQueenLightAttack.playsStandSound(this, stand)) {
                StandUtil.playStandEntitySound(
                        stand,
                        KillerQueenLionAddon.KILLER_QUEEN_BOOM,
                        1.0F,
                        1.0F);
            }
            var projectiles = level().getEntitiesOfClass(
                    KillerQueenBombProjectile.class,
                    user.getBoundingBox().inflate(LEGACY_RANGE),
                    projectile -> projectile.isOwnedBy(
                            user.getUUID()));
            for (KillerQueenBombProjectile projectile : projectiles) {
                projectile.manualDetonate();
            }
            // 1.16 ExplodeSnow had no stamina condition: the Stand task
            // pays the cost after standPerform and a short bar drops to 0
            StandAbilityStamina.consume(
                    ability,
                    StandPower.get(user),
                    KillerQueenPhase1Spec.PROJECTILE_DETONATE_STAMINA,
                    false);
        }
    }
}
