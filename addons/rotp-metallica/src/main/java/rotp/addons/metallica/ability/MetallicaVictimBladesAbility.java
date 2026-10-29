package rotp.addons.metallica.ability;

import java.util.List;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.metallica.MetallicaAddon;
import rotp.addons.metallica.MetallicaPhase1Spec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.horse.SkeletonHorse;
import net.minecraft.world.entity.animal.horse.ZombieHorse;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Illusioner;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class MetallicaVictimBladesAbility
        extends StandEntityAbility {
    public static final float DAMAGE_PER_TICK =
            (float) (MetallicaPhase1Spec.POWER / 12.0D);

    public MetallicaVictimBladesAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId) {
        super(
                abilityType,
                abilityId,
                VictimBladesAction::new);
        // 1.16 holdType(100) with cooldown(300) and no recovery: the
        // cooldown is taken as the hold ends (PowerBaseImpl.stopHeldAction).
        setButtonHoldPhase(ActionPhase.PERFORM);
        cooldown(
                MetallicaPhase1Spec
                        .VICTIM_BLADES_COOLDOWN_TICKS);
    }

    private static final class VictimBladesAction
            extends EntityActionInstance {
        private boolean started;

        private VictimBladesAction(
                EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionTick() {
            if (getPhase() != ActionPhase.PERFORM
                    || !(level()
                            instanceof ServerLevel level)
                    || !(ability
                            instanceof MetallicaVictimBladesAbility)) {
                return;
            }
            if (getPhaseTick()
                    >= MetallicaPhase1Spec
                            .VICTIM_BLADES_MAX_HOLD_TICKS) {
                startRecovery();
                return;
            }

            LivingEntity user = getPowerUser();
            StandPower power =
                    user != null ? StandPower.get(user) : null;
            if (user == null || power == null) {
                startRecovery();
                return;
            }
            if (!started) {
                JojoModUtil.sayVoiceLine(
                        user,
                        MetallicaAddon
                                .USER_CREATE_BLADES);
                started = true;
            }
            // 1.16: Stand attack damage / 12; POWER / 12 without a Stand body
            float damage = getPerformer()
                            instanceof StandEntity stand
                    ? (float) stand.getAttackDamage() / 12.0F
                    : DAMAGE_PER_TICK;

            // 1.16 hit every eligible living entity in the box, no count limit.
            List<LivingEntity> targets =
                    level.getEntitiesOfClass(
                            LivingEntity.class,
                            user.getBoundingBox().inflate(
                                    MetallicaPhase1Spec
                                            .MAX_RANGE),
                            target -> isCanonicalTarget(
                                    user, target));
            for (LivingEntity target : targets) {
                applyToTarget(level, user, target, damage);
            }
            // 1.16 ticked targets before the debit; exhaustion did not end the hold.
            StandAbilityStamina.consume(
                    ability,
                    power,
                    MetallicaPhase1Spec
                            .VICTIM_BLADES_STAMINA_PER_TICK,
                    true);
        }

        @Override
        public void onButtonStopHold() {
            startRecovery();
        }

        // 1.16 create_blades_in_victim was a holdType Stand action: another
        // Stand action pressed during the hold replaced it at once.
        @Override
        public boolean isHoldTypeStandAction() {
            return true;
        }

        private static void applyToTarget(
                ServerLevel level,
                LivingEntity user,
                LivingEntity target,
                float damage) {
            target.addEffect(
                    new MobEffectInstance(
                            MetallicaAddon.LACK_OF_IRON,
                            MetallicaPhase1Spec
                                    .LACK_OF_IRON_DURATION_TICKS,
                            0,
                            false,
                            false,
                            true),
                    user);
            MetallicaAddon.ironState(target)
                    .mark(target, user);
            // 1.16: plain GENERIC hurt, so the 10-tick hurt cooldown
            // and Resistance apply (generic ignores armor in both)
            target.hurt(
                    level.damageSources().generic(),
                    damage);

            if (!target.getMainHandItem().isEmpty()
                    || target.tickCount % 20 != 0) {
                return;
            }
            float random = target.getRandom().nextFloat();
            if (random > 0.90F && random < 0.95F) {
                target.setItemInHand(
                        net.minecraft.world.InteractionHand.MAIN_HAND,
                        new ItemStack(Items.SHEARS));
            } else if (random > 0.95F) {
                target.setItemInHand(
                        net.minecraft.world.InteractionHand.MAIN_HAND,
                        new ItemStack(
                                MetallicaAddon.SCALPEL.get()));
            }
        }

        private static boolean isCanonicalTarget(
                LivingEntity user, LivingEntity target) {
            // 1.16 VictimBladeCreation filtered the box with IS_ALIVE alone:
            // Creative and spectator players took the effect too
            if (target == user
                    || !target.isAlive()
                    || target instanceof StandEntity
                    || target instanceof Slime
                    || target instanceof Phantom
                    || target instanceof SkeletonHorse
                    || target instanceof ZombieHorse
                    || target instanceof AbstractGolem
                    || target instanceof Ghast
                    || target instanceof ArmorStand) {
                return false;
            }
            return !(target instanceof Monster)
                    || target instanceof Creeper
                    || target instanceof Evoker
                    || target instanceof Guardian
                    || target instanceof Pillager
                    || target instanceof Ravager
                    || target instanceof Silverfish
                    || target instanceof Spider
                    || target instanceof Vindicator
                    || target instanceof Witch
                    || target instanceof AbstractPiglin
                    || target instanceof Illusioner;
        }
    }
}
