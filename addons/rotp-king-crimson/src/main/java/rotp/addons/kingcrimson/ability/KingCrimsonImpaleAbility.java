package rotp.addons.kingcrimson.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.KingCrimsonPhase2Spec;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class KingCrimsonImpaleAbility
        extends AbstractKingCrimsonStrikeAbility {
    public KingCrimsonImpaleAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId) {
        super(
                abilityType,
                abilityId,
                KingCrimsonPhase2Spec.IMPALE_DAMAGE_MULTIPLIER,
                KingCrimsonPhase2Spec.IMPALE_STAMINA,
                KingCrimsonPhase2Spec.IMPALE_COOLDOWN_TICKS,
                KingCrimsonAddon.KINGCRIMSON_IMPALE,
                KingCrimsonAddon.DIAVOLO_IMPALE);
        // 1.16 SHIFT variation of the chop: shouts while sneaking too
        setPlaysVoiceLineOnSneak();
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity powerUser,
            LivingEntity performer) {
        super.initActionFromConfig(action, level, powerUser, performer);
        if (!level.isClientSide() && performer instanceof StandEntity stand) {
            action.phasesLength.put(
                    ActionPhase.RECOVERY,
                    StandStatFormulas.getHeavyAttackRecovery(
                            stand.getAttackSpeed(), stand.getLastHeavyFinisherValue()));
        }
    }

    // The length above is the estimate sent with the action; the strike reads it again when RECOVERY starts.
    @Override
    protected boolean heavyRecoveryAtEntry() {
        return true;
    }

    // 1.16 KCConfig impaleCooldown
    @Override
    public int getCooldown(Power<?> context, int ticksHeld) {
        return KingCrimsonConfig.configuredCooldown(
                super.getCooldown(context, ticksHeld),
                KingCrimsonConfig.impaleCooldownTicks());
    }

    // 1.16 KingcrimsonImpaleInstance is a light punch whose doAttack hurts any entity.
    @Override
    protected boolean hitsNonLivingPrimary() {
        return true;
    }

    @Override
    protected void applyHitEffects(
            StandEntity stand,
            LivingEntity target) {
        if (target instanceof StandEntity) {
            return;
        }
        target.addEffect(new MobEffectInstance(
                KingCrimsonAddon.GAPING_WOUND,
                KingCrimsonPhase2Spec.IMPALE_EFFECT_TICKS,
                KingCrimsonConfig.impaleGapingWoundAmplifier(),
                false,
                false,
                false));
        target.addEffect(new MobEffectInstance(
                KingCrimsonAddon.BLEEDING,
                KingCrimsonPhase2Spec.IMPALE_EFFECT_TICKS,
                0,
                false,
                false,
                false));
        target.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN,
                KingCrimsonPhase2Spec.IMPALE_EFFECT_TICKS,
                3,
                false,
                false,
                false));
        target.addEffect(new MobEffectInstance(
                MobEffects.WEAKNESS,
                KingCrimsonPhase2Spec.IMPALE_EFFECT_TICKS,
                2,
                false,
                false,
                false));
    }
}
