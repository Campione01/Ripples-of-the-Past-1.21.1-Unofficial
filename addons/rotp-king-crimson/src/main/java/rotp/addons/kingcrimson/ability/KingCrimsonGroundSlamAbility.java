package rotp.addons.kingcrimson.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility.AutoSummonMode;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonPhase4Spec;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class KingCrimsonGroundSlamAbility
        extends AbstractKingCrimsonStrikeAbility {
    public KingCrimsonGroundSlamAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(
                abilityType,
                abilityId,
                KingCrimsonPhase4Spec.GROUND_SLAM_DAMAGE_MULTIPLIER,
                0.0F,
                0,
                KingCrimsonAddon.KINGCRIMSON_GROUNDPUNCH,
                KingCrimsonAddon.DOPPIO_TAKE_THIS);
        performStaminaDrain(KingCrimsonPhase4Spec.GROUND_SLAM_STAMINA);
        partsRequired(StandPart.ARMS);
        standAutoSummonMode(AutoSummonMode.ARMS);
        setDefaultPhaseLength(
                ActionPhase.WINDUP,
                KingCrimsonPhase4Spec.GROUND_SLAM_WINDUP_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(
                ActionPhase.RECOVERY,
                KingCrimsonPhase4Spec.GROUND_SLAM_RECOVERY_TICKS);
        armorPiercingPerStrength(0.0F);
        knockback(
                KingCrimsonPhase4Spec.GROUND_SLAM_KNOCKBACK,
                1.0F);
        fixedDisableBlockingChance(0.75F);
        areaImpact(
                KingCrimsonPhase4Spec.GROUND_SLAM_RANGE,
                KingCrimsonPhase4Spec
                        .GROUND_SLAM_VERTICAL_KNOCKBACK);
        isSubAbility = true;
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
                KingCrimsonPhase4Spec.GROUND_SLAM_WINDUP_TICKS);
        action.phasesLength.put(
                ActionPhase.RECOVERY,
                KingCrimsonPhase4Spec.GROUND_SLAM_RECOVERY_TICKS);
    }

    @Override
    protected boolean hitsNonLivingPrimary() {
        return true;
    }

    // 1.16 KingCrimsonGroundSlam: addKnockback(0.75f) gives the light punch's two detached half pulses.
    @Override
    protected boolean usesDetachedPrimaryKnockback() {
        return true;
    }

    // 1.16 KingCrimsonGroundSlam.punchEntity/punchBlock run the area before the hit itself.
    @Override
    protected boolean areaBeforePrimary() {
        return true;
    }

    @Override
    protected void applyHitEffects(
            StandEntity stand, LivingEntity target) {
    }
}
