package rotp.addons.killerqueen.ability;

import javax.annotation.Nullable;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;

import rotp.addons.killerqueen.state.KillerQueenStateService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/** 1.16 kq_combo_punch (KQFinisher): the heavy finisher that plants the primary bomb. */
public final class KillerQueenComboPunchAbility
        extends StandEntityHeavyPunchAbility {
    public static final float BASE_KNOCKBACK = 0.5F;
    // 1.16 had no separate combo pose; it played the heavy punch
    private static final ActionAnimIdentifier ANIMATION =
            ActionAnimIdentifier.getOrCreate("heavy_punch", false);

    public KillerQueenComboPunchAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        createActionObj = KillerQueenComboPunch::new;
        // the 1.16 kq_combo_punch icon ships as heavy_charged.png
        spriteName = "heavy_charged";
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        return ANIMATION;
    }

    /** 1.16 KQFinisher.punchEntity: a living non-Stand target becomes the primary bomb. */
    public static boolean plantPrimaryBomb(
            @Nullable LivingEntity user, @Nullable Entity target) {
        return user instanceof ServerPlayer owner
                && target instanceof LivingEntity living
                && !(living instanceof StandEntity)
                && KillerQueenStateService.markPrimaryBomb(
                        owner, living);
    }

    /** 1.16 addKnockback(0.5F + getLastHeavyFinisherValue()). */
    public static float comboKnockback(float finisherValue) {
        return BASE_KNOCKBACK + finisherValue;
    }

    public static final class KillerQueenComboPunch
            extends StandEntityHeavyPunch {
        public KillerQueenComboPunch(EntityActionType ability) {
            super(ability);
        }

        @Override
        protected void hitEntity(
                ActionTarget target,
                Level level,
                StandEntity stand,
                DamageSource damageSource,
                float damage,
                float explosionRadius) {
            // bomb first, before the punch damage (1.16 order)
            plantPrimaryBomb(getPowerUser(), target.getMainEntity());
            super.hitEntity(
                    target,
                    level,
                    stand,
                    damageSource,
                    damage,
                    explosionRadius);
        }

        @Override
        protected float getAdditionalHeavyPunchKnockback(
                StandEntity stand) {
            return comboKnockback(finisherValue);
        }
    }
}
