package rotp.addons.kiss.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.HitResultUtil;
import rotp.core.subsystems.target.AimingEntity;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.kiss.service.KissService;
import rotp.addons.kiss.service.KissTokenService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class KissSpecialAbility extends StandEntityAbility {
    public enum Mode {
        ATTACH,
        LAUNCH,
        PULL,
        BOMB
    }

    private final Mode mode;

    public KissSpecialAbility(
            AbilityType<?> abilityType, AbilityId abilityId, Mode mode) {
        super(
                abilityType,
                abilityId,
                actionType -> new KissAction(actionType, mode));
        this.mode = mode;
        usageGroup = AbilityUsageGroup.COMBAT;
        partsRequired(StandPart.ARMS);
        // 1.16 095 InitStands:64-67: kiss_sticker_place, kiss_sticker,
        // kiss_pull and kiss_bomb set no standRecoveryTicks
        if (mode == Mode.PULL) {
            setButtonHoldPhase(ActionPhase.PERFORM);
        } else {
            setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        }
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        // 1.16 cooldown(30, 0) and cooldown(20, 0): technical only, so Resolve keeps them.
        cooldown(mode == Mode.LAUNCH ? 30 : mode == Mode.BOMB ? 20 : 0, 0);
    }

    public Mode mode() {
        return mode;
    }

    @Override
    public String getSpriteName(Power<?> context) {
        if (mode == Mode.PULL
                && context != null
                && context.getUser() != null) {
            KissTokenService.Token token =
                    KissTokenService.heldToken(context.getUser());
            if (token != null && !token.isEntity()) {
                return "kiss_pull_block";
            }
        }
        return super.getSpriteName(context);
    }

    // 1.16 095 KissStickerAttachAction and the other sticker actions kept no
    // target (standKeepsTarget false, TargetRequirement NONE): the pressed one
    // was emptied and the Stand never turned to it; the sticker took the
    // user's own ray at the perform
    @Override
    public boolean takesPressedTarget(
            StandEntity standEntity, ActionTarget target) {
        return false;
    }

    public static float staminaCost(Mode mode) {
        return switch (mode) {
            case ATTACH, PULL -> 10.0F;
            case LAUNCH -> 15.0F;
            case BOMB -> 20.0F;
        };
    }

    public static final class KissAction extends EntityActionInstance {
        private final Mode mode;
        private boolean performStarted;

        public KissAction(EntityActionType ability, Mode mode) {
            super(ability);
            this.mode = mode;
        }

        @Override
        public void onActionSet(EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            if (mode == Mode.ATTACH
                    && getPerformer() instanceof StandEntity stand
                    && getPowerUser() != null) {
                ActionTarget target = stickerTarget(getPowerUser());
                setActionTargetSnapshot(target);
                keepStandAimedAtTarget(target);
                aimAs = AimingEntity.STAND;
            }
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(getPowerUser() instanceof ServerPlayer user)
                    || !(getPerformer() instanceof StandEntity stand)) {
                return;
            }
            performStarted = true;
            switch (mode) {
                case ATTACH -> {
                    ActionTarget target = stickerTarget(user);
                    setActionTargetSnapshot(target);
                    keepStandAimedAtTarget(target);
                    KissService.attachFromAim(user, stand, target);
                }
                case LAUNCH -> KissService.launchSticker(user, stand);
                case PULL -> KissService.pullLatest(user);
                case BOMB -> KissService.launchBomb(user, stand);
            }
            // 1.16 StandEntityTask paid the staminaCost after standPerform and ignored the result
            StandAbilityStamina.consume(
                    ability, StandPower.get(user), staminaCost(mode), false);
        }

        @Override
        public void actionTick() {
            if (mode == Mode.PULL
                    && performStarted
                    && getPhase() == ActionPhase.PERFORM
                    && !level().isClientSide()
                    && getPowerUser() instanceof ServerPlayer user
                    && KissService.pullLatest(user)) {
                startRecovery();
            }
        }

        @Override
        public void onButtonStopHold() {
            if (mode == Mode.PULL) {
                startRecovery();
            }
        }

        @Override
        public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
            return true;
        }

        // 1.16 095 sticker actions (StandEntityAction, no isChainable) refused their own press via canClickDuringTask
        @Override
        public boolean refusesOwnAbilityPressedAgain() {
            return true;
        }

        // 1.16 kiss_pull (KissStickerPullAction) was a holdType Stand action; the other modes were not held
        @Override
        public boolean isHoldTypeStandAction() {
            return mode == Mode.PULL;
        }

        private static ActionTarget stickerTarget(LivingEntity user) {
            return HitResultUtil.clip(
                    user.getEyePosition(), user.getLookAngle(),
                    KissService.DIRECT_STICKER_RANGE, KissService.DIRECT_STICKER_RANGE,
                    user.level(),
                    entity -> entity instanceof LivingEntity && entity.isAlive()
                            && entity.isPickable() && !(entity instanceof StandEntity),
                    user, 0.0D);
        }
    }
}
