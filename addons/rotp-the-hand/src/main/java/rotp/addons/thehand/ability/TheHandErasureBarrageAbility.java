package rotp.addons.thehand.ability;

import javax.annotation.Nullable;

import rotp.core.api.stand.StandSkillWalls;
import rotp.core.config.client.PlayerClientBroadcastedSettings;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;

import rotp.addons.thehand.TheHandAddon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class TheHandErasureBarrageAbility
        extends StandEntityBarrageAbility {
    public static final float STAMINA_PER_TICK = 12.0F;
    public static final float BASE_BARRAGE_STAMINA_PER_TICK = 4.0F;
    public static final int COOLDOWN_TICKS = 43;
    public static final int TAP_COOLDOWN_TICKS = 24;
    public static final int RECOVERY_TICKS = 8;
    public static final float LARGE_TARGET_THRESHOLD = 1.5F;
    public static final float SMALL_TARGET_FACTOR_PER_HIT = 0.0115F;
    public static final float LARGE_TARGET_FACTOR_PER_HIT = 0.0071875F;
    public static final float BLOCK_STRENGTH_THRESHOLD = 20.0F;

    public TheHandErasureBarrageAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, ErasureBarrageAction::new);
        cooldown(COOLDOWN_TICKS);
    }

    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
    }

    @Override
    public int getCooldown(Power<?> context, int ticksHeld) {
        if (context instanceof StandPower power
                && PlayerClientBroadcastedSettings
                        .isNoStandAbilityCooldownEnabled(power)) {
            return 0;
        }
        // 1.16 returned the tap cooldown without looking at Resolve or Creative; only the
        // held cooldown scales super's value, which Resolve and Creative empty.
        if (ticksHeld < 5) {
            return TAP_COOLDOWN_TICKS;
        }
        int base = super.getCooldown(context, ticksHeld);
        int maximum = barrageDuration(context);
        return base
                * Mth.clamp(ticksHeld, 0, maximum)
                / maximum;
    }

    @Override
    public String getSpriteName(Power<?> context) {
        if (context instanceof StandPower standPower) {
            StandEntity stand = standPower.getSummonedStandEntity();
            if (stand != null
                    && stand.getCurStandAction()
                            instanceof ErasureBarrageAction action
                    && action.isAttracting()) {
                return super.getSpriteName(context) + "_alt";
            }
            LivingEntity user = standPower.getUser();
            if (user != null && user.isShiftKeyDown()) {
                return super.getSpriteName(context) + "_alt";
            }
        }
        return super.getSpriteName(context);
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity powerUser,
            LivingEntity performer) {
        super.initActionFromConfig(
                action, level, powerUser, performer);
        if (!level.isClientSide()
                && performer instanceof StandEntity stand) {
            action.phasesLength.put(
                    ActionPhase.PERFORM,
                    StandStatFormulas.getBarrageMaxDuration(
                            stand.getDurability() * 0.3D));
            action.phasesLength.put(
                    ActionPhase.RECOVERY, RECOVERY_TICKS);
        }
    }

    private static int barrageDuration(Power<?> context) {
        if (context instanceof StandPower standPower) {
            StandEntity stand = standPower.getSummonedStandEntity();
            if (stand != null) {
                return Math.max(
                        StandStatFormulas.getBarrageMaxDuration(
                                stand.getDurability() * 0.3D),
                        1);
            }
        }
        return StandStatFormulas.getBarrageMaxDuration(10.0D * 0.3D);
    }

    public static final class ErasureBarrageAction
            extends StandEntityBarrage {
        private boolean attract;
        private boolean performed;
        private boolean targetSelected;
        private boolean cooldownSet;
        private boolean missTeleportResolved;
        private boolean missTeleportFromPerform;
        private boolean actionCleared;
        // 1.16 recoveryCount: server ticks spent in RECOVERY.
        private int recoveryTicks;
        private int performTicks;
        private int erasedTargets;

        public ErasureBarrageAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(EntityActionInstance previousAction) {
            LivingEntity user = getPowerUser();
            attract = user != null && user.isShiftKeyDown();
            super.onActionSet(previousAction);
        }

        @Override
        public void actionTick() {
            if (!level().isClientSide()
                    && getPhase() == ActionPhase.RECOVERY) {
                recoveryTicks++;
            }
            if (getPhase() != ActionPhase.PERFORM) {
                super.actionTick();
                return;
            }
            performed = true;
            performTicks++;
            if (!level().isClientSide()) {
                LivingEntity user = getPowerUser();
                StandPower standPower =
                        user != null ? StandPower.get(user) : null;
                super.actionTick();
                // 1.16 staminaCostTick(12) replaces the base barrage's 4; the
                // result is ignored as in StandEntityTask.
                StandAbilityStamina.consume(
                        ability,
                        standPower,
                        STAMINA_PER_TICK
                                - BASE_BARRAGE_STAMINA_PER_TICK,
                        true);
                if (hitsThisTick > 0
                        && getPerformer()
                                instanceof StandEntity stand) {
                    erasedTargets += TheHandCombat.eraseSweep(
                            stand, 1.0F, hitsThisTick);
                }
                return;
            }
            super.actionTick();
        }

        @Override
        protected void hitEntity(
                ActionTarget target, Level level, StandEntity stand) {
            targetSelected = true;
            Entity entity = target.getMainEntity();
            if (entity == null
                    || entity.isRemoved()
                    || entity == stand
                    || entity == getPowerUser()) {
                return;
            }
            float damage = TheHandCombat.barrageDamage(
                    entity, stand, hitsThisTick);
            impactSoundDue = TheHandCombat.attack(
                            stand, entity, damage, 1.0F, true)
                    || !(entity instanceof LivingEntity);
        }

        @Override
        protected void hitBlock(
                ActionTarget target, Level level, StandEntity stand) {
            targetSelected = true;
            BlockPos pos = target.getBlockPos();
            if (level.isClientSide()
                    || pos == null
                    || !JojoModUtil.breakingBlocksEnabled(level)) {
                return;
            }
            float hardness = stand.getBlockHardnessForStandBreak(
                    level.getBlockState(pos), level, pos);
            if (hardness >= 0.0F
                    && hardness <= BLOCK_STRENGTH_THRESHOLD) {
                level.removeBlock(pos, false);
            }
        }

        @Override
        public void onSetPhase(ActionPhase newPhase) {
            super.onSetPhase(newPhase);
            if (newPhase == ActionPhase.RECOVERY) {
                setCooldownFromHeldTicks();
                if (getPhase() == ActionPhase.PERFORM) {
                    teleportAfterMissIfEligible();
                }
            }
        }

        // 1.16 isCancelable: a punch or an Erase also cuts the recovery.
        @Override
        public boolean canBeCancelledInto(
                EntityActionType cancellingAbility) {
            return super.canBeCancelledInto(cancellingAbility)
                    || getPhase() == ActionPhase.RECOVERY
                            && TheHandCombat.followsUpErase(
                                    cancellingAbility);
        }

        @Override
        public void onActionCleared(EntityActionInstance newAction) {
            setCooldownFromHeldTicks();
            super.onActionCleared(newAction);
            actionCleared = true;
            teleportAfterMissIfEligible();
        }

        private void teleportAfterMissIfEligible() {
            if (missTeleportResolved
                    || level().isClientSide()
                    || !performed
                    || targetSelected
                    || erasedTargets > 0
                    || !(getPerformer() instanceof StandEntity stand)) {
                return;
            }
            // 1.16 onTaskStopped: the pull waits for the action to end.
            if (attract && !actionCleared) {
                return;
            }
            missTeleportResolved = true;
            missTeleportFromPerform =
                    getPhase() == ActionPhase.PERFORM;
            TheHandCombat.teleportAfterMiss(
                    getPowerUser(), stand, attract,
                    recoveryTicks >= RECOVERY_TICKS,
                    StandSkillWalls.wallsHideEntities(level(), this));
        }

        boolean missTeleportOccurredDuringPerform() {
            return missTeleportResolved
                    && missTeleportFromPerform;
        }

        boolean hasPerformedServerTick() {
            return performed;
        }

        private void setCooldownFromHeldTicks() {
            if (cooldownSet || level().isClientSide()) {
                return;
            }
            LivingEntity user = getPowerUser();
            StandPower standPower =
                    user != null ? StandPower.get(user) : null;
            if (standPower != null
                    && ability
                            instanceof TheHandErasureBarrageAbility
                                    barrageAbility) {
                barrageAbility.setCooldownOnUse(
                        standPower, performTicks);
                cooldownSet = true;
            }
        }

        public boolean isAttracting() {
            return attract;
        }

        @Override
        protected SoundEvent getBarrageSwingSound() {
            return TheHandAddon.THE_HAND_ERASURE_BARRAGE_SOUND.get();
        }

        @Override
        @Nullable
        protected Holder<SoundEvent> getBarrageHitSound() {
            return null;
        }

        @Override
        protected boolean shouldPlayBarrageCry(
                Level level, StandEntity stand) {
            return false;
        }

        @Override
        public void toBuf(FriendlyByteBuf buffer) {
            buffer.writeBoolean(attract);
        }

        @Override
        public void fromBuf(FriendlyByteBuf buffer) {
            attract = buffer.readBoolean();
        }
    }
}
