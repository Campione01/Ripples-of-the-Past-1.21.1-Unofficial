package rotp.addons.littlefeet.ability;

import rotp.core.customobjects.DamageSourceModified;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.DamageUtil;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;

import rotp.addons.littlefeet.LittleFeetAddon;
import rotp.addons.littlefeet.scale.LittleFeetScaleService;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class LittleFeetShrinkSlashAbility
        extends StandEntityPunchAbility {
    public static final float LEGACY_DAMAGE_FACTOR = 1.0F;

    public LittleFeetShrinkSlashAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        createActionObj = ShrinkSlash::new;
        partsRequired(StandPart.ARMS);
        resolveLevelToUnlock(1);
        setDefaultPhaseLength(ActionPhase.WINDUP, 12);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 12);
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity standUser,
            LivingEntity standEntity) {
        super.initActionFromConfig(
                action, level, standUser, standEntity);
        // 1.16 .shout(FORMAGGIO_BASTARD): a user line, silent while sneaking
        if (!level.isClientSide() && standUser != null) {
            sayShout(standUser, LittleFeetAddon.FORMAGGIO_BASTARD);
        }
        if (!level.isClientSide()
                && standEntity instanceof StandEntity stand) {
            action.phasesLength.put(
                    ActionPhase.WINDUP,
                    StandStatFormulas.getHeavyAttackWindup(
                            stand.getAttackSpeed(),
                            stand.getFinisherMeter()));
            action.phasesLength.put(ActionPhase.PERFORM, 1.0F);
            action.phasesLength.put(
                    ActionPhase.RECOVERY,
                    StandStatFormulas.getHeavyAttackRecovery(
                            stand.getAttackSpeed(),
                            stand.getLastHeavyFinisherValue()));
        }
    }

    public static final class ShrinkSlash extends StandEntityPunch {
        public ShrinkSlash(EntityActionType ability) {
            super(ability);
        }

        @Override
        protected void hitEntity(
                ActionTarget target, Level level, StandEntity stand) {
            if (!(target.getMainEntity()
                    instanceof LivingEntity targetLiving)) {
                return;
            }
            DamageSource damageSource = makePunchDamageSource();
            ((DamageSourceModified) damageSource)
                    .jojo_ripples$modifyKnockback(0.0F, 1.0F);
            float damage =
                    LittleFeetCombatSemantics.legacyDamage(
                            stand, LEGACY_DAMAGE_FACTOR);
            damage = DamageUtil.addArmorPiercing(
                    damage,
                    LittleFeetCombatSemantics.armorPiercing(stand),
                    targetLiving,
                    damageSource);
            LittleFeetCombatSemantics.tryBreakStandGuard(
                    stand, targetLiving);
            // 1.16 StandEntity.attackTarget: the impact sound only for a hit that landed
            impactSoundDue = standEntityAttack(
                    stand, targetLiving, damageSource, damage);
            if (impactSoundDue) {
                stand.addFinisherMeter(0.1F);
                LittleFeetCombatSemantics.tryDisableShield(
                        stand, targetLiving);
                LivingEntity owner = getPowerUser();
                if (owner != null) {
                    LittleFeetScaleService.shrinkAfterHit(
                            owner, targetLiving);
                }
            }
        }
    }
}
