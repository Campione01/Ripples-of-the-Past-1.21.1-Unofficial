package rotp.addons.whitesnake.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.whitesnake.WhitesnakeAddon;
import rotp.addons.whitesnake.entity.projectile.MeltHeartProjectile;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class WhitesnakeMeltYourHeartAbility
        extends NoPoseStandEntityAbility {
    public static final float REQUIRED_STAMINA = 100.0F;
    public static final float STAMINA_COST = 10.0F;
    public static final int MAX_HOLD_TICKS = 100;
    private static final String ABILITY_NAME = "melt_your_heart";

    public WhitesnakeMeltYourHeartAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, MeltYourHeartAction::new);
        setButtonHoldPhase(ActionPhase.PERFORM);
        cooldown(20, 100, 0.0F);
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck check = super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        return checkRequiredStamina(context);
    }

    // 1.16 checkStandConditions asked for 100 stamina on every held tick
    // too, so the stream stops once stamina falls below it, silently.
    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        ConditionCheck base =
                super.checkHeldSpecificConditions(action, context);
        if (!base.isPositive()) {
            return base;
        }
        return checkRequiredStamina(context);
    }

    private static ConditionCheck checkRequiredStamina(Power<?> context) {
        if (!(context instanceof StandPower power)
                || !power.isStaminaInfinite()
                        && power.getStamina() < REQUIRED_STAMINA) {
            return ConditionCheck.NEGATIVE;
        }
        return ConditionCheck.POSITIVE;
    }

    @Override
    public boolean canContinueAction(EntityActionInstance action) {
        return WhitesnakeAddon.isActiveWhitesnakeUser(
                action.getPowerUser(), ABILITY_NAME);
    }

    public static final class MeltYourHeartAction
            extends EntityActionInstance {
        private boolean paid;

        public MeltYourHeartAction(EntityActionType ability) {
            super(ability);
        }

        // 1.16 holdType(100) with no recovery: another Stand action pressed
        // during the stream replaced it at once (isCancelable), and any
        // other action of the power ended the hold (stopHeldAction(false))
        @Override
        public boolean isHoldTypeStandAction() {
            return true;
        }

        @Override
        public void actionTick() {
            if (getPhase() != ActionPhase.PERFORM) {
                return;
            }
            if (!level().isClientSide() && paid) {
                spawnProjectile();
            }
            if (curPhaseTick >= MAX_HOLD_TICKS - 1) {
                startRecovery();
            }
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()) {
                return;
            }
            LivingEntity user = getPowerUser();
            StandPower power =
                    user != null ? StandPower.get(user) : null;
            if (user == null
                    || power == null
                    || !power.isStaminaInfinite()
                            && power.getStamina()
                                    < REQUIRED_STAMINA) {
                forceStop();
                return;
            }
            paid = true;
            if (getPerformer() instanceof StandEntity stand) {
                StandUtil.playStandEntitySound(
                        stand,
                        WhitesnakeAddon.WHITESNAKE_MELT_HEART.get(),
                        1.0F,
                        1.0F);
            }
            // 1.16 StandEntityTask: the 10 is paid at perform start with the
            // result ignored; only the 100 floor stops the stream.
            StandAbilityStamina.consume(
                    ability, power, STAMINA_COST, false);
            spawnProjectile();
        }

        @Override
        public void onButtonStopHold() {
            startRecovery();
        }

        private void spawnProjectile() {
            if (!(getPerformer() instanceof StandEntity stand)
                    || !(level()
                            instanceof net.minecraft.server.level.ServerLevel
                            serverLevel)) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user == null) {
                return;
            }

            MeltHeartProjectile projectile =
                    new MeltHeartProjectile(stand, serverLevel);
            projectile.setWhitesnakeUser(user.getUUID());
            projectile.setPos(
                    stand.getX(),
                    stand.getY() + stand.getBbHeight() * 0.5D,
                    stand.getZ());
            // 1.16 standEntity.shootProjectile(proj, 0.1F, 0.5F): turned
            // along the Stand's look, its damage and speed scaled by the
            // Stand's stats (addProjectile); the drift replaced its velocity.
            projectile.shootFromRotation(stand, 0.1F,
                    stand.getProjectileInaccuracy(0.5F));
            double motionX =
                    (stand.getRandom().nextDouble() - 0.5D) * 0.4D;
            double motionZ =
                    (stand.getRandom().nextDouble() - 0.5D) * 0.4D;
            projectile.setDeltaMovement(
                    new Vec3(motionX, 0.0D, motionZ));
            addProjectileWithStandStats(projectile);
        }
    }
}
