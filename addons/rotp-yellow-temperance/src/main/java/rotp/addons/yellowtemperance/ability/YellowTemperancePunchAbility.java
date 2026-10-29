package rotp.addons.yellowtemperance.ability;

import rotp.core.customobjects.DamageSourceModified;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;

import rotp.addons.yellowtemperance.effect.ErosionService;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class YellowTemperancePunchAbility
        extends StandEntityPunchAbility {
    public YellowTemperancePunchAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, YellowPunchAction::new);
        // 1.16 yellow_temperance_punch was swingHand() without withUserPunch:
        // performing it reset the user's attack strength.
        setResetsAttackStrengthOnPerform();
    }

    public static final class YellowPunchAction
            extends StandEntityPunch {
        public YellowPunchAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        protected void hitEntity(
                ActionTarget target, Level level, StandEntity stand) {
            if (!(target.getMainEntity()
                    instanceof LivingEntity living)) {
                super.hitEntity(target, level, stand);
                return;
            }
            // 1.16 punchPerform hurt the target directly, so the Stand
            // damage multiplier never scaled it, and sounded only on a hit.
            DamageSource source = makePunchDamageSource();
            ((DamageSourceModified) source)
                    .jojo_ripples$setFixedDamage(true);
            ((DamageSourceModified) source)
                    .jojo_ripples$modifyKnockback(
                            stand.guardCounter(), 1.0F);
            float damage = StandStatFormulas.getLightAttackDamage(
                    stand.getAttackDamage());
            boolean hurt = standEntityAttack(stand, living, source, damage);
            impactSoundDue = hurt;
            if (hurt) {
                stand.addFinisherMeter(0.1F);
                ErosionService.applyPunch(
                        living, getPowerUser());
            }
        }
    }
}
