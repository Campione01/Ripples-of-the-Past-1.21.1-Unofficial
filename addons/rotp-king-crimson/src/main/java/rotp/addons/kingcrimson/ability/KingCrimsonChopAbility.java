package rotp.addons.kingcrimson.ability;

import rotp.core.init.ModStatusEffects;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class KingCrimsonChopAbility
        extends AbstractKingCrimsonStrikeAbility {
    public KingCrimsonChopAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId) {
        super(
                abilityType,
                abilityId,
                KingCrimsonPhase2Spec.CHOP_DAMAGE_MULTIPLIER,
                KingCrimsonPhase2Spec.CHOP_STAMINA,
                KingCrimsonPhase2Spec.CHOP_COOLDOWN_TICKS,
                KingCrimsonAddon.KINGCRIMSON_CHOP,
                KingCrimsonAddon.DIAVOLO_CHOP);
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

    // 1.16 KCConfig chopCooldown
    @Override
    public int getCooldown(Power<?> context, int ticksHeld) {
        return KingCrimsonConfig.configuredCooldown(
                super.getCooldown(context, ticksHeld),
                KingCrimsonConfig.chopCooldownTicks());
    }

    @Override
    protected boolean hitsNonLivingPrimary() {
        return true;
    }

    @Override
    protected void applyHitEffects(
            StandEntity stand,
            LivingEntity target) {
        target.addEffect(new MobEffectInstance(
                ModStatusEffects.BLEEDING,
                KingCrimsonPhase2Spec.CHOP_EFFECT_TICKS,
                1,
                false,
                false,
                false));
        target.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN,
                KingCrimsonPhase2Spec.CHOP_EFFECT_TICKS,
                3,
                false,
                false,
                false));
        if (target instanceof Player) {
            target.addEffect(new MobEffectInstance(
                    KingCrimsonAddon.MANGLED_BODY,
                    KingCrimsonPhase2Spec.CHOP_EFFECT_TICKS,
                    0,
                    false,
                    false,
                    false));
        }
    }
}
