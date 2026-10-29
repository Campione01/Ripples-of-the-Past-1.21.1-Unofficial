package rotp.addons.kingcrimson.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.KingCrimsonPhase4Spec;
import net.minecraft.world.entity.LivingEntity;

public final class KingCrimsonGroundPunchAbility
        extends AbstractKingCrimsonStrikeAbility {
    public KingCrimsonGroundPunchAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(
                abilityType,
                abilityId,
                KingCrimsonPhase4Spec.GROUND_PUNCH_DAMAGE_MULTIPLIER,
                0.0F,
                0,
                KingCrimsonAddon.KINGCRIMSON_GROUNDPUNCH,
                null);
        windupStandSound(KingCrimsonAddon.DIAVOLO_COMBOFINISHER);
        performStaminaDrain(KingCrimsonPhase4Spec.GROUND_PUNCH_STAMINA);
        armorPiercingPerStrength(0.0F);
        knockback(
                KingCrimsonPhase4Spec.GROUND_PUNCH_KNOCKBACK,
                1.0F);
        fixedDisableBlockingChance(1.0F);
        areaImpact(
                KingCrimsonPhase4Spec.GROUND_PUNCH_RANGE,
                KingCrimsonPhase4Spec
                        .GROUND_PUNCH_VERTICAL_KNOCKBACK);
        consumesFinisher(true);
        initIsFinisher("heavy_punch");
        resolveLevelToUnlock(1);
    }

    @Override
    public Ability replaceWithSubAbility(
            Power<?> context, AvailableAbilities abilities) {
        LivingEntity user = context != null ? context.getUser() : null;
        StandPower standPower =
                context instanceof StandPower power
                        ? power
                        : null;
        // 1.16 KingCrimsonHeavyPunch.replaceAction sends an absent or arms-only Stand to Ground Slam
        // before the finisher variation (and its Eye Jab check) is consulted.
        StandEntity stand = standPower != null
                ? standPower.getSummonedStandEntity()
                : null;
        if (stand == null || stand.isArmsOnlyMode()) {
            return abilities.getContextVariationOrDisable(
                    name(), "kingcrimson_groundslam");
        }
        if (user != null
                && user.getHealth() / user.getMaxHealth()
                        < KingCrimsonConfig.eyejabHealthThreshold()
                && !standPower.isAbilityOnCooldown(
                        "kingcrimson_desperate_eyejab")) {
            Ability eyejab = abilities.getContextVariationOrDisable(
                    name(), "kingcrimson_desperate_eyejab");
            if (eyejab != null) {
                return eyejab;
            }
        }
        return this;
    }

    @Override
    protected boolean hitsNonLivingPrimary() {
        return true;
    }

    @Override
    protected boolean usesDetachedPrimaryKnockback() {
        return true;
    }

    @Override
    protected boolean areaBeforePrimary() {
        return true;
    }

    @Override
    protected boolean heavyAttack() {
        return true;
    }

    @Override
    protected boolean heavyDamageFloor() {
        return true;
    }

    @Override
    protected void applyHitEffects(
            StandEntity stand, LivingEntity target) {
    }
}
