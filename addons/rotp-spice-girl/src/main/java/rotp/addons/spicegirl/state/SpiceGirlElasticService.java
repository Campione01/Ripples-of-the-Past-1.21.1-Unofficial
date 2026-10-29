package rotp.addons.spicegirl.state;

import java.util.UUID;

import javax.annotation.Nullable;

import rotp.addons.spicegirl.SpiceGirlAddon;
import rotp.addons.spicegirl.entity.SpiceGirlStandEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class SpiceGirlElasticService {
    public static final double APPLICATION_RANGE = 10.0D;
    public static final int MAX_DURATION_TICKS = 750;
    public static final int MAX_AMPLIFIER = 1;
    public static final double MAX_WATER_ASCENT = 0.25D;

    private SpiceGirlElasticService() {}

    public static boolean apply(
            ServerPlayer owner,
            SpiceGirlStandEntity stand,
            LivingEntity target,
            int requestedDuration,
            int requestedAmplifier) {
        if (!(owner.level() instanceof ServerLevel level)
                || stand != SpiceGirlAddon.activeStand(owner)
                || target == null
                || !target.isAlive()
                || target.isRemoved()
                || target.level() != level
                || target instanceof Player player
                        && player.getAbilities().instabuild
                || stand.distanceToSqr(target)
                        > APPLICATION_RANGE * APPLICATION_RANGE) {
            return false;
        }

        clearOwner(owner);
        clearTarget(target);

        int duration = Mth.clamp(
                requestedDuration, 1, MAX_DURATION_TICKS);
        int amplifier = Mth.clamp(
                requestedAmplifier, 0, MAX_AMPLIFIER);
        UUID generation = UUID.randomUUID();
        long expiresAt = level.getGameTime() + duration;

        SpiceGirlAddon.sourceState(owner).link(
                target.getUUID(),
                stand.getUUID(),
                generation,
                level.dimension().location(),
                expiresAt);
        SpiceGirlAddon.targetState(target).apply(
                owner.getUUID(),
                stand.getUUID(),
                generation,
                level.dimension().location(),
                expiresAt,
                amplifier);
        if (!target.addEffect(new MobEffectInstance(
                SpiceGirlAddon.BOUNCE,
                duration,
                amplifier,
                false,
                true,
                true))) {
            clearTarget(target);
            return false;
        }
        return true;
    }

    public static boolean isElastic(LivingEntity target) {
        return validate(target) != null;
    }

    public static ElasticTargetState validate(
            LivingEntity target) {
        if (target == null
                || target.level().isClientSide()
                || !target.hasData(SpiceGirlAddon.ELASTIC_TARGET)) {
            return null;
        }
        ElasticTargetState targetState =
                SpiceGirlAddon.targetState(target);
        if (!(target.level() instanceof ServerLevel level)
                || !targetState.isActive()
                || !target.isAlive()
                || target.isRemoved()
                || !target.hasEffect(SpiceGirlAddon.BOUNCE)
                || level.getGameTime() >= targetState.expiresAt()
                || !level.dimension().location().equals(
                        targetState.dimension())) {
            clearTarget(target);
            return null;
        }

        ServerPlayer owner = level.getServer()
                .getPlayerList()
                .getPlayer(targetState.ownerUuid());
        ElasticSourceState source =
                owner != null
                        && owner.hasData(
                                SpiceGirlAddon.ELASTIC_SOURCE)
                        ? SpiceGirlAddon.sourceState(owner)
                        : null;
        if (owner == null
                || owner.level() != level
                || !owner.isAlive()
                || owner.isRemoved()
                || source == null
                || !source.matchesTarget(
                        target.getUUID(),
                        targetState.generationUuid())
                || source.expiresAt() != targetState.expiresAt()
                || !level.dimension().location().equals(
                        source.dimension())) {
            clearTarget(target);
            return null;
        }
        return targetState;
    }

    public static void tickTarget(LivingEntity target) {
        if (validate(target) == null || !target.isInWater()) {
            return;
        }
        Vec3 movement = target.getDeltaMovement();
        if (movement.y < MAX_WATER_ASCENT) {
            target.setDeltaMovement(
                    movement.x,
                    Math.min(
                            MAX_WATER_ASCENT,
                            movement.y + 0.08D),
                    movement.z);
            target.hurtMarked = true;
        }
        target.fallDistance = 0.0F;
    }

    public static void validateOwner(ServerPlayer owner) {
        if (!owner.hasData(SpiceGirlAddon.ELASTIC_SOURCE)) {
            return;
        }
        ElasticSourceState source =
                SpiceGirlAddon.sourceState(owner);
        if (!source.isActive()) {
            return;
        }
        if (!(owner.level() instanceof ServerLevel level)
                || !owner.isAlive()
                || owner.isRemoved()
                || owner.isSpectator()
                || !level.dimension().location().equals(
                        source.dimension())
                || level.getGameTime() >= source.expiresAt()) {
            clearOwner(owner);
            return;
        }
        Entity target = level.getEntity(source.targetUuid());
        if (!(target instanceof LivingEntity living)
                || validate(living) == null) {
            clearOwner(owner);
        }
    }

    public static boolean clearOwner(ServerPlayer owner) {
        if (owner == null
                || !owner.hasData(
                        SpiceGirlAddon.ELASTIC_SOURCE)) {
            return false;
        }
        ElasticSourceState source =
                SpiceGirlAddon.sourceState(owner);
        UUID targetUuid = source.targetUuid();
        UUID standUuid = source.standUuid();
        UUID generationUuid = source.generationUuid();
        ResourceLocation dimension = source.dimension();
        if (!source.clear()) {
            return false;
        }

        LivingEntity target = findLiving(
                owner.getServer(), dimension, targetUuid);
        if (target != null
                && target.hasData(
                        SpiceGirlAddon.ELASTIC_TARGET)) {
            ElasticTargetState targetState =
                    SpiceGirlAddon.targetState(target);
            if (targetState.matches(
                    owner.getUUID(),
                    standUuid,
                    generationUuid)) {
                clearTargetOnly(target);
            }
        }
        return true;
    }

    public static boolean clearTarget(LivingEntity target) {
        if (target == null
                || !target.hasData(
                        SpiceGirlAddon.ELASTIC_TARGET)) {
            return false;
        }
        ElasticTargetState targetState =
                SpiceGirlAddon.targetState(target);
        UUID ownerUuid = targetState.ownerUuid();
        UUID generationUuid = targetState.generationUuid();
        boolean changed = clearTargetOnly(target);
        MinecraftServer server = target.getServer();
        ServerPlayer owner = server != null
                ? server.getPlayerList().getPlayer(ownerUuid)
                : null;
        if (owner != null
                && owner.hasData(
                        SpiceGirlAddon.ELASTIC_SOURCE)
                && SpiceGirlAddon.sourceState(owner)
                        .matchesTarget(
                                target.getUUID(),
                                generationUuid)) {
            SpiceGirlAddon.sourceState(owner).clear();
        }
        return changed;
    }

    private static boolean clearTargetOnly(
            LivingEntity target) {
        ElasticTargetState state =
                SpiceGirlAddon.targetState(target);
        boolean changed = state.clear();
        target.removeEffect(SpiceGirlAddon.BOUNCE);
        return changed;
    }

    @Nullable
    private static LivingEntity findLiving(
            MinecraftServer server,
            @Nullable ResourceLocation dimension,
            UUID uuid) {
        if (server == null
                || dimension == null
                || uuid == null) {
            return null;
        }
        ResourceKey<Level> key = ResourceKey.create(
                Registries.DIMENSION, dimension);
        ServerLevel level = server.getLevel(key);
        Entity entity =
                level != null ? level.getEntity(uuid) : null;
        return entity instanceof LivingEntity living
                ? living
                : null;
    }
}
