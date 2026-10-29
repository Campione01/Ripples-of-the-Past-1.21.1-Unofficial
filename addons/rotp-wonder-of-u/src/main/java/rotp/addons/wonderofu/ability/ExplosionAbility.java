package rotp.addons.wonderofu.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.wonderofu.WonderOfUAddon;
import rotp.addons.wonderofu.entity.WonderOfUStandEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class ExplosionAbility extends StandEntityAbility {
    public static final int HOLD_TO_FIRE_TICKS = 20;
    public static final float STAMINA_COST = 80.0F;
    public static final int COOLDOWN_TICKS = 0;
    public static final int TNT_FUSE_TICKS = 10;
    public static final float EXPLOSION_RADIUS = 2.0F;

    public ExplosionAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, ExplosionAction::new);
        setDefaultPhaseLength(
                ActionPhase.BUTTON_CHARGE, HOLD_TO_FIRE_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        partsRequired(StandPart.MAIN_BODY);
    }

    public static final class ExplosionAction
            extends EntityActionInstance {
        public ExplosionAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onButtonStopHold() {
            if (getPhase() == ActionPhase.BUTTON_CHARGE) {
                forceStop();
                syncPhaseChanges();
            }
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel serverLevel)
                    || !(getPerformer()
                            instanceof WonderOfUStandEntity stand)
                    || !stand.isAlive()
                    || stand.isRemoved()) {
                return;
            }

            LivingEntity user = getPowerUser();
            StandPower power = user != null
                    ? StandPower.get(user)
                    : null;
            if (power == null
                    || power.getSummonedStandEntity() != stand) {
                return;
            }
            explodeAtTarget(serverLevel, stand, user, ability,
                    getTaskTarget(serverLevel));
            // 1.16 StandEntityTask: the cost is paid after the perform, hit
            // or miss; a short bar just drops to 0.
            StandAbilityStamina.consume(
                    ability, power, STAMINA_COST, false);
        }

        private static void explodeAtTarget(
                ServerLevel serverLevel,
                WonderOfUStandEntity stand,
                LivingEntity user,
                EntityActionType skill,
                ActionTarget kept) {
            // 1.16 ExplodeVariations: aimWithStandOrUser(100,
            // task.getTarget()), the Stand's own turn and ray; only an
            // entity it meets is struck
            Vec3 target =
                    CalamityRangedTargeting.resolveEntityHitPosition(
                            stand, user, skill, kept);
            if (target == null) {
                return;
            }
            StandUtil.playStandEntitySound(
                    stand,
                    WonderOfUAddon.CALAMITY4.get(),
                    1.0F,
                    1.0F);

            boolean blockDamageAllowed = serverLevel.getGameRules()
                    .getBoolean(GameRules.RULE_MOBGRIEFING);
            switch (stand.getRandom().nextInt(3)) {
                case 0 -> spawnTntOrFallback(
                        serverLevel,
                        stand,
                        user,
                        target,
                        blockDamageAllowed);
                case 1 -> explode(
                        serverLevel,
                        stand,
                        target,
                        blockDamageAllowed);
                default -> spawnCreeperOrFallback(
                        serverLevel,
                        stand,
                        target,
                        blockDamageAllowed);
            }
        }

        private static void spawnTntOrFallback(
                ServerLevel level,
                WonderOfUStandEntity stand,
                LivingEntity user,
                Vec3 target,
                boolean blockDamageAllowed) {
            if (!blockDamageAllowed) {
                explode(level, stand, target, false);
                return;
            }
            PrimedTnt tnt = new PrimedTnt(
                    level, target.x, target.y, target.z, user);
            tnt.setFuse(TNT_FUSE_TICKS);
            stand.bindExplosionHazard(tnt);
            if (level.addFreshEntity(tnt)) {
                stand.onExplosionHazardAdded(tnt);
            }
        }

        private static void spawnCreeperOrFallback(
                ServerLevel level,
                WonderOfUStandEntity stand,
                Vec3 target,
                boolean blockDamageAllowed) {
            if (!blockDamageAllowed) {
                explode(level, stand, target, false);
                return;
            }
            Creeper creeper = EntityType.CREEPER.create(level);
            if (creeper == null) {
                explode(level, stand, target, true);
                return;
            }
            creeper.moveTo(target.x, target.y, target.z);
            creeper.ignite();
            stand.bindExplosionHazard(creeper);
            if (level.addFreshEntity(creeper)) {
                stand.onExplosionHazardAdded(creeper);
            }
        }

        private static void explode(
                ServerLevel level,
                WonderOfUStandEntity stand,
                Vec3 target,
                boolean blockDamageAllowed) {
            level.explode(
                    stand,
                    target.x,
                    target.y,
                    target.z,
                    EXPLOSION_RADIUS,
                    blockDamageAllowed
                            ? Level.ExplosionInteraction.MOB
                            : Level.ExplosionInteraction.NONE);
        }
    }
}
