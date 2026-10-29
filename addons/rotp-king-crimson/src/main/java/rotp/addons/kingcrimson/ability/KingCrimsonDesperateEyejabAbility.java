package rotp.addons.kingcrimson.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.KingCrimsonPhase4Spec;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class KingCrimsonDesperateEyejabAbility
        extends AbstractKingCrimsonStrikeAbility {
    public KingCrimsonDesperateEyejabAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(
                abilityType,
                abilityId,
                KingCrimsonPhase4Spec.EYEJAB_DAMAGE_MULTIPLIER,
                KingCrimsonPhase4Spec.EYEJAB_STAMINA,
                KingCrimsonPhase4Spec.EYEJAB_COOLDOWN_TICKS,
                KingCrimsonAddon.KINGCRIMSON_DESPERATE_EYEJAB,
                KingCrimsonAddon.DIAVOLO_CHOP);
        setDefaultPhaseLength(
                ActionPhase.WINDUP,
                KingCrimsonPhase4Spec.EYEJAB_WINDUP_TICKS);
        armorPiercingPerStrength(
                KingCrimsonPhase4Spec
                        .EYEJAB_ARMOR_PIERCING_PER_STRENGTH);
        // 1.16 KingCrimsonDesperateEyejab.punchEntity: impactSound(KINGCRIMSON_IMPALE) on an entity; the eyejab sound
        // stays the punchSound of a block
        entityImpactSound(KingCrimsonAddon.KINGCRIMSON_IMPALE);
        // 1.16 reduceKnockback(15) clamps to full knockback; shield break uses proximity - 0.25
        consumesFinisher(true);
        isSubAbility = true;
        resolveLevelToUnlock(1);
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity powerUser,
            LivingEntity performer) {
        super.initActionFromConfig(action, level, powerUser, performer);
        action.phasesLength.put(
                ActionPhase.WINDUP,
                KingCrimsonPhase4Spec.EYEJAB_WINDUP_TICKS);
    }

    // 1.16 KCConfig desperateEyejabCooldown
    @Override
    public int getCooldown(Power<?> context, int ticksHeld) {
        return KingCrimsonConfig.configuredCooldown(
                super.getCooldown(context, ticksHeld),
                KingCrimsonConfig.eyejabCooldownTicks());
    }

    @Override
    protected boolean hitsNonLivingPrimary() {
        return true;
    }

    @Override
    protected boolean heavyAttack() {
        return true;
    }

    // 1.16 KingcrimsonEyejabInstance: EffectInstance(effect, ticks, amplifier, false, false) also hides the icon
    @Override
    protected void applyHitEffects(
            StandEntity stand, LivingEntity target) {
        target.addEffect(new MobEffectInstance(
                stand.getRandom().nextBoolean()
                        ? KingCrimsonAddon.HALF_BLINDNESS_LEFT
                        : KingCrimsonAddon.HALF_BLINDNESS_RIGHT,
                KingCrimsonPhase4Spec.HALF_BLINDNESS_TICKS,
                0,
                false,
                false,
                false));
        target.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN,
                KingCrimsonPhase4Spec.EYEJAB_SLOWNESS_TICKS,
                0,
                false,
                false,
                false));
        target.addEffect(new MobEffectInstance(
                MobEffects.BLINDNESS,
                KingCrimsonPhase4Spec.EYEJAB_BLINDNESS_TICKS,
                KingCrimsonPhase4Spec.EYEJAB_BLINDNESS_AMPLIFIER,
                false,
                false,
                false));
    }
}
