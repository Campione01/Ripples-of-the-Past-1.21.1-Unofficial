package rotp.addons.echoes.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.HitResultUtil;

import rotp.addons.echoes.init.EchoesStands;
import rotp.addons.echoes.state.EchoesGravityService;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

public final class EchoesFinisherAbility extends StandEntityAbility {
    public enum Mode {
        ACT2_BRAINROT,
        ACT3_KNOCKBACK
    }

    private static final double RANGE = 8.0D;
    private static final ResourceKey<MobEffect> BRAINROT = ResourceKey.create(
            Registries.MOB_EFFECT,
            ResourceLocation.fromNamespaceAndPath("brainrotp", "brainrot"));

    private final Mode mode;

    public EchoesFinisherAbility(
            AbilityType<?> type, AbilityId id, Mode mode) {
        super(type, id, action -> new FinisherAction(action, mode));
        this.mode = mode;
        setDefaultPhaseLength(ActionPhase.WINDUP, 6);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 20);
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive() || !(context instanceof StandPower power)) {
            return base.isPositive() ? ConditionCheck.NEGATIVE : base;
        }
        boolean correctAct = mode == Mode.ACT2_BRAINROT
                ? power.getPowerType() == EchoesStands.ECHOES_ACT2.get()
                : power.getPowerType() == EchoesStands.ECHOES_ACT3.get();
        return correctAct ? ConditionCheck.POSITIVE : ConditionCheck.NEGATIVE;
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
        return ActionAnimIdentifier.getOrCreate("finisher", false);
    }

    private static final class FinisherAction extends EntityActionInstance {
        private final Mode mode;

        private FinisherAction(EntityActionType ability, Mode mode) {
            super(ability);
            this.mode = mode;
        }

        @Override
        public void actionPerformStart() {
            if (!(getPowerUser() instanceof ServerPlayer player)) {
                return;
            }
            ActionTarget aimed = HitResultUtil.clip(
                    player.getEyePosition(),
                    player.getLookAngle(),
                    RANGE,
                    RANGE,
                    player.level(),
                    entity -> entity instanceof LivingEntity
                            && entity.isAlive()
                            && entity != player
                            && entity != getPerformer(),
                    player,
                    0.0D);
            if (!(aimed.getMainEntity() instanceof LivingEntity target)) {
                return;
            }
            // 092: the ACT3 finisher ends self-freeze or gravity infuse
            if (mode == Mode.ACT3_KNOCKBACK) {
                EchoesGravityService.onAct3FinisherHit(player);
            }
            for (int hit = 0; hit < 6 && target.isAlive(); hit++) {
                target.hurt(
                        player.damageSources().playerAttack(player),
                        3.0F);
            }
            if (mode == Mode.ACT3_KNOCKBACK) {
                target.knockback(
                        1.5D,
                        player.getX() - target.getX(),
                        player.getZ() - target.getZ());
            } else {
                target.registryAccess()
                        .lookupOrThrow(Registries.MOB_EFFECT)
                        .get(BRAINROT)
                        .ifPresent(effect -> target.addEffect(
                                new MobEffectInstance(
                                        effect, 100, 1, false, false)));
                target.knockback(
                        0.3D,
                        player.getX() - target.getX(),
                        player.getZ() - target.getZ());
            }
        }
    }
}
