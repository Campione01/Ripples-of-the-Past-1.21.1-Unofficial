package rotp.addons.whitealbum.ability;

import rotp.core.customobjects.DamageSourceModified;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * 1.16 wa_punch (WhiteAlbumPunch.punchPerform): a landed hit on a living
 * target also calls knockback(2.0, user - target), shoving it away.
 */
public final class WhiteAlbumPunchAbility
        extends StandEntityPunchAbility {
    public static final double KNOCKBACK_STRENGTH = 2.0D;

    public WhiteAlbumPunchAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, WhiteAlbumPunch::new);
    }

    public static final class WhiteAlbumPunch
            extends StandEntityPunch {
        public WhiteAlbumPunch(EntityActionType ability) {
            super(ability);
        }

        // Core light punch hit plus the 1.16 knockback on a landed hit.
        // 1.16 punchPerform hurt the target directly, so the Stand damage
        // multiplier never scaled it, and played its sound only on a hit.
        @Override
        protected void hitEntity(
                ActionTarget target, Level level, StandEntity stand) {
            Entity targetEntity = target.getMainEntity();
            float damage = StandStatFormulas.getLightAttackDamage(
                    stand.getAttackDamage());
            if (targetEntity instanceof LivingEntity targetLiving) {
                DamageSource source = fixedDamageSource();
                ((DamageSourceModified) source)
                        .jojo_ripples$modifyKnockback(
                                stand.guardCounter(), 1.0F);
                boolean hurt = standEntityAttack(
                        stand, targetLiving, source, damage);
                impactSoundDue = hurt;
                if (hurt) {
                    stand.addFinisherMeter(0.1F);
                    knockBackFromUser(stand, targetLiving);
                }
            }
            else if (targetEntity != null) {
                if (standEntityAttack(
                        stand,
                        targetEntity,
                        fixedDamageSource(),
                        damage)) {
                    stand.addFinisherMeter(0.1F);
                }
                impactSoundDue = true;
            }
        }

        private DamageSource fixedDamageSource() {
            DamageSource source = makePunchDamageSource();
            ((DamageSourceModified) source)
                    .jojo_ripples$setFixedDamage(true);
            return source;
        }
    }

    // 1.16 knockback(2.0f, user.x - target.x, user.z - target.z)
    static void knockBackFromUser(
            StandEntity stand, LivingEntity target) {
        LivingEntity user = stand.getUser();
        Entity from = user != null ? user : stand;
        target.knockback(
                KNOCKBACK_STRENGTH,
                from.getX() - target.getX(),
                from.getZ() - target.getZ());
        target.hurtMarked = true;
    }
}
