package rotp.addons.littlefeet.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.DamageUtil;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;

import rotp.addons.littlefeet.LittleFeetAddon;
import rotp.addons.littlefeet.scale.LittleFeetScaleService;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class LittleFeetFinisherAbility
        extends StandEntityHeavyPunchAbility {
    public static final float LEGACY_DAMAGE_FACTOR = 1.25F;

    public LittleFeetFinisherAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        createActionObj = LittleFeetFinisher::new;
        partsRequired(StandPart.ARMS);
        resolveLevelToUnlock(1);
        // 1.16 heavy finishers shout while sneaking too
        setPlaysVoiceLineOnSneak();
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity standUser,
            LivingEntity standEntity) {
        super.initActionFromConfig(
                action, level, standUser, standEntity);
        if (!level.isClientSide() && standUser != null) {
            sayShout(standUser, LittleFeetAddon.FORMAGGIO_BASTARD);
        }
    }

    public static final class LittleFeetFinisher
            extends StandEntityHeavyPunch {
        public LittleFeetFinisher(EntityActionType ability) {
            super(ability);
        }

        @Override
        protected void hitEntity(
                ActionTarget target,
                Level level,
                StandEntity stand,
                DamageSource damageSource,
                float ignoredHeavyDamage,
                float explosionRadius) {
            float damage =
                    LittleFeetCombatSemantics.legacyDamage(
                            stand, LEGACY_DAMAGE_FACTOR);
            if (target.getMainEntity()
                    instanceof LivingEntity targetLiving) {
                LittleFeetCombatSemantics.tryBreakStandGuard(
                        stand, targetLiving);
                damage = DamageUtil.addArmorPiercing(
                        damage,
                        LittleFeetCombatSemantics.armorPiercing(
                                stand),
                        targetLiving,
                        damageSource);
            }
            super.hitEntity(
                    target,
                    level,
                    stand,
                    damageSource,
                    damage,
                    explosionRadius);
        }

        @Override
        protected void addKnockback(DamageSource damageSource) {
            ((rotp.core.customobjects
                            .DamageSourceModified) damageSource)
                    .jojo_ripples$modifyKnockback(0.0F, 1.0F);
        }

        @Override
        protected void afterHeavyPunchHit(
                StandEntity stand,
                LivingEntity target,
                DamageSource damageSource,
                float damage,
                boolean hurt) {
            if (hurt) {
                LittleFeetCombatSemantics.tryDisableShield(
                        stand, target);
            }
            if (hurt && getPowerUser() != null) {
                LittleFeetScaleService.shrinkAfterHit(
                        getPowerUser(), target);
            }
        }
    }
}
