package rotp.addons.emperor.ability;

import java.util.Optional;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.util.functions.JojoModUtil;

import rotp.addons.emperor.EmperorAddon;
import rotp.addons.emperor.entity.EmperorStandEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class EmperorSetTargetAbility extends Ability {
    public static final int RESOLVE_LEVEL = 3;
    public static final double TARGET_RANGE = 12.0D;
    public static final double TARGET_INFLATION = 2.0D;

    public EmperorSetTargetAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        resolveLevelToUnlock(RESOLVE_LEVEL);
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        // 1.16 stand_target did not ignore a stunned performer.
        ConditionCheck stun = checkPerformerStun(context);
        if (!stun.isPositive()) {
            return stun;
        }
        return EmperorTargetModeAbility.activePlayer(context) != null
                ? ConditionCheck.POSITIVE
                : ConditionCheck.createNegative(
                        "emperor_not_summoned");
    }

    @Override
    public Ability replaceWithSubAbility(
            Power<?> context, AvailableAbilities abilities) {
        Player player = EmperorTargetModeAbility.activePlayer(context);
        EmperorStandEntity controller =
                player != null ? EmperorAddon.controller(player) : null;
        if (controller != null
                && controller.hasTarget()
                && abilities != null) {
            Ability clear = abilities.getContextVariation(
                    EmperorAddon.CLEAR_TARGET_NAME);
            if (clear != null && clear.isAbilityAvailable(context)) {
                return clear;
            }
        }
        return super.replaceWithSubAbility(context, abilities);
    }

    @Override
    public void onClick(
            Level level,
            LivingEntity user,
            FriendlyByteBuf extraClientInput) {
        if (level.isClientSide()
                || !(user instanceof Player player)) {
            return;
        }
        EmperorStandEntity controller =
                EmperorAddon.controller(player);
        if (controller == null) {
            return;
        }
        LivingEntity target = raytraceTarget(player);
        if (target != null) {
            controller.setTargetEntity(target);
        }
    }

    public static LivingEntity raytraceTarget(Player player) {
        Level level = player.level();
        Vec3 start = player.getEyePosition();
        Vec3 direction = player.getLookAngle();
        // The donor selects entities on the full ray before checking blocks.
        Vec3 end = start.add(direction.scale(TARGET_RANGE));

        AABB search = player.getBoundingBox()
                .expandTowards(end.subtract(start))
                .inflate(TARGET_INFLATION);
        LivingEntity best = null;
        double bestDistance = start.distanceToSqr(end);
        for (Entity candidate : level.getEntities(
                player, search, entity -> canTarget(player, entity))) {
            LivingEntity living = (LivingEntity) candidate;
            if (!JojoModUtil.canHarm(player, living)) {
                continue;
            }
            AABB hitbox = living.getBoundingBox().inflate(
                    living.getPickRadius() + TARGET_INFLATION);
            Optional<Vec3> intersection = hitbox.contains(start)
                    ? Optional.of(start)
                    : hitbox.clip(start, end);
            if (intersection.isEmpty()) {
                continue;
            }
            double distance =
                    start.distanceToSqr(intersection.get());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = living;
            }
        }
        return best;
    }

    private static boolean canTarget(Player user, Entity entity) {
        // 1.16 JojoModUtil.rayTrace picked only !isSpectator() && isPickable() entities, and StandTarget's own filter
        // was isAlive() && !isAlliedTo(user)
        return entity instanceof LivingEntity living
                && living.isAlive()
                && !living.isAlliedTo(user)
                && !living.isSpectator()
                && living.isPickable()
                && !(living instanceof EmperorStandEntity)
                && (!(living instanceof StandEntity)
                        || ((StandEntity) living).getUser() != null);
    }
}
