package rotp.addons.kingcrimson.state;

import java.util.UUID;
import java.util.List;

import javax.annotation.Nullable;

import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.ability.Ability;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.movement_input_sync.PlayerMovementInputData;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.KingCrimsonDonorGameplaySpec;
import rotp.addons.kingcrimson.KingCrimsonPhase5Spec;
import rotp.addons.kingcrimson.network.TimeEraseTransitionPayload;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalState.Mode;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public final class KingCrimsonTemporalStateService {
    private KingCrimsonTemporalStateService() {}

    public static boolean canStartTimeErase(
            ServerPlayer player) {
        return canStartTemporalAction(player)
                && !hasActiveTimeErase(player.server);
    }

    public static boolean canStartTimeSkip(
            ServerPlayer player) {
        return canStartTemporalAction(player)
                && !hasActiveTimeErase(player.server);
    }

    public static boolean beginTimeErase(
            ServerPlayer player) {
        if (!canStartTimeErase(player)) {
            return false;
        }

        long startedAt = player.level().getGameTime();
        // 1.16 KCConfig timeEraseDuration
        long expiresAt = startedAt
                + KingCrimsonConfig.timeEraseDurationTicks();
        MobEffectInstance previous =
                player.getEffect(MobEffects.INVISIBILITY);
        MobEffectInstance previousFullInvisibility =
                player.getEffect(
                        ModStatusEffects.FULL_INVISIBILITY);
        StandEntity hiddenStand = summonedStand(player);
        MobEffectInstance previousStandInvisibility =
                hiddenStand != null
                        ? hiddenStand.getEffect(
                                MobEffects.INVISIBILITY)
                        : null;
        KingCrimsonTemporalState state =
                KingCrimsonAddon.temporalState(player);
        if (!state.beginTimeErase(
                startedAt,
                expiresAt,
                previous,
                previousFullInvisibility,
                hiddenStand != null
                        ? hiddenStand.getUUID()
                        : null,
                previousStandInvisibility)) {
            return false;
        }

        player.removeEffect(MobEffects.INVISIBILITY);
        player.removeEffect(ModStatusEffects.FULL_INVISIBILITY);
        int effectTicks =
                KingCrimsonConfig.timeEraseDurationTicks() + 2;
        player.addEffect(new MobEffectInstance(
                MobEffects.INVISIBILITY,
                effectTicks,
                0,
                false,
                false,
                false));
        player.addEffect(new MobEffectInstance(
                ModStatusEffects.FULL_INVISIBILITY,
                effectTicks,
                999999,
                false,
                false,
                false));
        player.addEffect(new MobEffectInstance(
                KingCrimsonAddon.TIME_ERASE,
                effectTicks,
                0,
                false,
                false,
                true));
        if (hiddenStand != null) {
            hiddenStand.removeEffect(MobEffects.INVISIBILITY);
            hiddenStand.addEffect(new MobEffectInstance(
                    MobEffects.INVISIBILITY,
                    effectTicks,
                    0,
                    false,
                    false,
                    false));
        }
        player.playNotifySound(
                KingCrimsonAddon.TIME_ERASE_START.get(),
                SoundSource.PLAYERS,
                1.0F,
                1.0F);
        KingCrimsonAddon.syncTemporalState(player);
        PacketDistributor.sendToPlayer(
                player, TimeEraseTransitionPayload.INSTANCE);
        KingCrimsonTimeEraseProjectionService.begin(player);
        return true;
    }

    public static boolean beginTimeSkip(
            ServerPlayer player) {
        if (!canStartTimeSkip(player)) {
            return false;
        }

        Vec3 destination = findTimeSkipDestination(player);
        SoundEvent sound = chooseTimeSkipSound(
                player.getRandom());
        return beginTimeSkip(
                player,
                destination,
                // 1.16 KCConfig timeSkipDuration
                KingCrimsonConfig.timeSkipDurationTicks(),
                sound);
    }

    public static boolean beginEpitaphTimeSkip(
            ServerPlayer player, LivingEntity attacker) {
        if (!canStartTimeSkip(player)
                || !attacker.isAlive()
                || attacker.level() != player.level()) {
            return false;
        }

        Vec3 destination = findEpitaphTimeSkipDestination(
                player, attacker);
        if (!beginTimeSkip(
                player,
                destination,
                KingCrimsonDonorGameplaySpec
                        .EPITAPH_FUTURE_IMPLIED_TICKS,
                KingCrimsonAddon.EPITAPH_TIMESKIP.get())) {
            return false;
        }
        player.lookAt(
                EntityAnchorArgument.Anchor.FEET,
                attacker.position());
        return true;
    }

    private static boolean beginTimeSkip(
            ServerPlayer player,
            Vec3 destination,
            int impliedTicks,
            SoundEvent sound) {
        List<Mob> nearbyMobs = collectTimeSkipMobs(player);
        long startedAt = player.level().getGameTime();
        long expiresAt = startedAt
                + KingCrimsonPhase5Spec
                        .TIME_SKIP_INVULNERABLE_TICKS;
        KingCrimsonTemporalState state =
                KingCrimsonAddon.temporalState(player);
        if (!state.beginTimeSkip(startedAt, expiresAt)) {
            return false;
        }

        makeNearbyMobsLoseTarget(player, destination);
        player.teleportTo(
                destination.x,
                destination.y,
                destination.z);
        advanceImpliedTickCounters(player, impliedTicks);
        advanceImpliedMobTicks(nearbyMobs, impliedTicks);
        player.level().playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                sound,
                SoundSource.PLAYERS,
                1.0F,
                1.0F);
        KingCrimsonAddon.syncTemporalState(player);
        return true;
    }

    public static void tick(ServerPlayer player) {
        KingCrimsonTemporalState state =
                KingCrimsonAddon.temporalState(player);
        if (state.isIdle()) {
            return;
        }

        long gameTime = player.level().getGameTime();
        if (!player.isAlive()
                || !KingCrimsonAddon.isKingCrimsonUser(player)
                || gameTime >= state.expiresAt()) {
            terminate(player);
            return;
        }

        if (state.mode() == Mode.TIME_ERASE) {
            if (!player.hasEffect(KingCrimsonAddon.TIME_ERASE)
                    || player.isUsingItem()) {
                terminate(player);
            } else {
                StandPower power = StandPower.get(player);
                Ability erase = power != null ? power.getAbility("kingcrimson_timeerase") : null;
                // 1.16 KingCrimsonTimeErase.standTickPerform: a failed upkeep ends the erase without a message.
                if (erase == null || !StandAbilityStamina.consume(
                        erase, power, KingCrimsonConfig.timeEraseStaminaPerTick(), true)) {
                    terminate(player);
                    return;
                }
                KingCrimsonTimeEraseProjectionService.tick(player);
                player.resetFallDistance();
            }
        }
    }

    public static boolean blocksDamage(
            ServerPlayer player, DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        KingCrimsonTemporalState state =
                KingCrimsonAddon.temporalState(player);
        long gameTime = player.level().getGameTime();
        return state.isErasing(gameTime)
                || state.isSkipping(gameTime);
    }

    public static void terminateOnInteraction(
            ServerPlayer player) {
        if (!player.hasData(KingCrimsonAddon.TEMPORAL_STATE)) {
            return;
        }
        KingCrimsonTemporalState state =
                KingCrimsonAddon.temporalState(player);
        if (state.mode() == Mode.TIME_ERASE) {
            terminate(player);
        }
    }

    public static void terminate(ServerPlayer player) {
        KingCrimsonTemporalState state =
                KingCrimsonAddon.temporalState(player);
        Mode previousMode = state.mode();
        if (previousMode == Mode.IDLE) {
            return;
        }

        long gameTime = player.level().getGameTime();
        int elapsedEraseTicks = previousMode == Mode.TIME_ERASE
                ? state.elapsedTicks(
                        gameTime,
                        KingCrimsonConfig.timeEraseDurationTicks())
                : 0;
        MobEffectInstance previousInvisibility =
                previousMode == Mode.TIME_ERASE
                        ? state.previousInvisibility(gameTime)
                        : null;
        MobEffectInstance previousFullInvisibility =
                previousMode == Mode.TIME_ERASE
                        ? state.previousFullInvisibility(gameTime)
                        : null;
        UUID hiddenStandUuid = previousMode == Mode.TIME_ERASE
                ? state.hiddenStandUuid()
                : null;
        MobEffectInstance previousStandInvisibility =
                previousMode == Mode.TIME_ERASE
                        ? state.previousStandInvisibility(gameTime)
                        : null;
        StandEntity hiddenStand = previousMode == Mode.TIME_ERASE
                ? findStand(player, hiddenStandUuid)
                : null;
        if (previousMode == Mode.TIME_ERASE) {
            KingCrimsonTimeEraseProjectionService.finish(player);
        }
        state.clear();
        // Time Erase removes the caster from danger, not other entities' outcomes.

        if (previousMode == Mode.TIME_ERASE) {
            player.removeEffect(KingCrimsonAddon.TIME_ERASE);
            player.removeEffect(MobEffects.INVISIBILITY);
            player.removeEffect(ModStatusEffects.FULL_INVISIBILITY);
            if (previousInvisibility != null) {
                player.addEffect(previousInvisibility);
            }
            if (previousFullInvisibility != null) {
                player.addEffect(previousFullInvisibility);
            }
            if (hiddenStand != null) {
                hiddenStand.removeEffect(MobEffects.INVISIBILITY);
                if (previousStandInvisibility != null) {
                    hiddenStand.addEffect(
                            previousStandInvisibility);
                }
            }
            setTimeEraseCooldown(player, elapsedEraseTicks);
            sendTimeEraseEndPresentation(player);
        }
        KingCrimsonAddon.syncTemporalState(player);
    }

    private static boolean canStartTemporalAction(
            ServerPlayer player) {
        return player.isAlive()
                && KingCrimsonAddon.isKingCrimsonUser(player)
                && KingCrimsonAddon.temporalState(player).isIdle();
    }

    private static boolean hasActiveTimeErase(
            MinecraftServer server) {
        long ignoredGameTime;
        for (ServerPlayer candidate :
                server.getPlayerList().getPlayers()) {
            ignoredGameTime = candidate.level().getGameTime();
            if (candidate.hasData(
                            KingCrimsonAddon.TEMPORAL_STATE)
                    && KingCrimsonAddon.temporalState(candidate)
                            .isErasing(ignoredGameTime)) {
                return true;
            }
        }
        return false;
    }

    private static Vec3 findTimeSkipDestination(
            ServerPlayer player) {
        boolean grounded = player.onGround();
        PlayerMovementInputData input = grounded
                ? PlayerMovementInputData.get(player)
                : null;
        return findDirectionalDestination(
                player,
                timeSkipDirection(
                        grounded,
                        player.getYRot(),
                        input != null ? input.left : 0.0F,
                        input != null ? input.forward : 0.0F,
                        player.getLookAngle()),
                KingCrimsonPhase5Spec.TIME_SKIP_MAX_DISTANCE);
    }

    static Vec3 timeSkipDirection(
            boolean grounded,
            float yaw,
            float left,
            float forward,
            Vec3 airborneDirection) {
        if (!grounded) {
            return airborneDirection;
        }
        if (!Float.isFinite(yaw)
                || !Float.isFinite(left)
                || !Float.isFinite(forward)
                || Math.abs(left) > 1.0F
                || Math.abs(forward) > 1.0F) {
            return Vec3.ZERO;
        }
        double lengthSqr = (double) left * left
                + (double) forward * forward;
        if (lengthSqr < 1.0E-8D) {
            return Vec3.ZERO;
        }
        // Grounded time skip advances movement input, not the camera's pitch.
        double radians = Math.toRadians(yaw);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        double length = Math.sqrt(lengthSqr);
        return new Vec3(
                (left * cos - forward * sin) / length,
                0.0D,
                (left * sin + forward * cos) / length);
    }

    private static Vec3 findEpitaphTimeSkipDestination(
            ServerPlayer player, LivingEntity attacker) {
        Vec3 origin = player.position();
        Vec3 target = attacker.position();
        Vec3 toTarget = target.subtract(origin);
        if (toTarget.length()
                > KingCrimsonDonorGameplaySpec
                        .EPITAPH_FUTURE_MAX_APPROACH) {
            return findDirectionalDestination(
                    player,
                    toTarget,
                    KingCrimsonDonorGameplaySpec
                            .EPITAPH_FUTURE_MAX_APPROACH);
        }

        Vec3 facing = horizontalUnit(
                attacker.getLookAngle(), toTarget);
        Vec3 right = new Vec3(-facing.z, 0.0D, facing.x);
        double offset = KingCrimsonDonorGameplaySpec
                .EPITAPH_FUTURE_OFFSET;
        for (Vec3 candidate : new Vec3[] {
                target.subtract(facing.scale(offset)),
                target.add(right.scale(offset)),
                target.subtract(right.scale(offset)),
                target.add(facing.scale(offset))}) {
            if (isLoadedCollisionFree(player, candidate)) {
                return candidate;
            }
        }

        double approach = Math.max(
                0.0D, toTarget.length() - offset);
        return findDirectionalDestination(
                player, toTarget, approach);
    }

    private static Vec3 findDirectionalDestination(
            ServerPlayer player,
            Vec3 direction,
            double maximumDistance) {
        Vec3 origin = player.position();
        if (direction.lengthSqr() < 1.0E-8D) {
            return origin;
        }
        direction = direction.normalize();
        Vec3 best = origin;
        for (double distance = KingCrimsonPhase5Spec.TIME_SKIP_STEP;
                distance <= maximumDistance;
                distance += KingCrimsonPhase5Spec.TIME_SKIP_STEP) {
            Vec3 candidate =
                    origin.add(direction.scale(distance));
            if (!isLoadedCollisionFree(player, candidate)) {
                break;
            }
            best = candidate;
        }
        return best;
    }

    private static Vec3 horizontalUnit(
            Vec3 primary, Vec3 fallback) {
        Vec3 horizontal = new Vec3(
                primary.x, 0.0D, primary.z);
        if (horizontal.lengthSqr() < 1.0E-8D) {
            horizontal = new Vec3(
                    fallback.x, 0.0D, fallback.z);
        }
        return horizontal.lengthSqr() < 1.0E-8D
                ? new Vec3(0.0D, 0.0D, 1.0D)
                : horizontal.normalize();
    }

    private static boolean isLoadedCollisionFree(
            ServerPlayer player, Vec3 candidate) {
        BlockPos blockPos = BlockPos.containing(candidate);
        return player.level().hasChunkAt(blockPos)
                && blockPos.getY()
                        >= player.level().getMinBuildHeight()
                && blockPos.getY() + 2
                        < player.level().getMaxBuildHeight()
                && player.level().noCollision(
                        player,
                        player.getBoundingBox().move(
                                candidate.subtract(
                                        player.position())));
    }

    private static void makeNearbyMobsLoseTarget(
            ServerPlayer player, Vec3 destination) {
        player.level().getEntitiesOfClass(
                        Mob.class,
                        player.getBoundingBox().inflate(8.0D),
                        mob -> mob.getTarget() == player)
                .forEach(mob -> {
                    if (mob.distanceToSqr(destination)
                            > mob.distanceToSqr(player)) {
                        mob.setTarget(null);
                    }
                });
    }

    private static void advanceImpliedTickCounters(
            ServerPlayer player, int impliedTicks) {
        int ticks = Math.max(impliedTicks, 0);
        player.tickCount = saturatingAdd(player.tickCount, ticks);
        StandPower power = StandPower.get(player);
        StandEntity stand = power != null
                ? power.getSummonedStandEntity()
                : null;
        if (stand != null) {
            stand.tickCount = saturatingAdd(
                    stand.tickCount, ticks);
            stand.overlayTickCount = saturatingAdd(
                    stand.overlayTickCount, ticks);
        }
    }

    private static List<Mob> collectTimeSkipMobs(
            ServerPlayer player) {
        return player.level().getEntitiesOfClass(
                Mob.class,
                player.getBoundingBox().inflate(32.0D),
                mob -> mob.isAlive() && !mob.isRemoved());
    }

    private static void advanceImpliedMobTicks(
            List<Mob> mobs, int impliedTicks) {
        int ticks = Math.max(impliedTicks, 0);
        if (ticks == 0) {
            return;
        }
        for (Mob mob : mobs) {
            for (int i = 0; i < ticks; i++) {
                if (!mob.isAlive() || mob.isRemoved()) {
                    break;
                }
                // The 1.16.5 ability advanced nearby entities through their
                // real tick path. Keep the scope to mobs, but preserve that
                // behavior instead of approximating it with velocity-only
                // movement and a synthetic tick counter.
                mob.tick();
            }
            mob.hurtMarked = true;
            mob.hasImpulse = true;
        }
    }

    @Nullable
    private static StandEntity summonedStand(ServerPlayer player) {
        StandPower power = StandPower.get(player);
        return power != null
                ? power.getSummonedStandEntity()
                : null;
    }

    @Nullable
    private static StandEntity findStand(
            ServerPlayer player, @Nullable UUID standUuid) {
        if (standUuid == null) {
            return null;
        }
        for (ServerLevel level : player.server.getAllLevels()) {
            Entity entity = level.getEntity(standUuid);
            if (entity instanceof StandEntity stand) {
                return stand;
            }
        }
        return null;
    }

    private static void sendTimeEraseEndPresentation(
            ServerPlayer source) {
        source.connection.send(new ClientboundStopSoundPacket(
                KingCrimsonAddon.id("time_erase_start"),
                SoundSource.PLAYERS));

        double radius = KingCrimsonPhase5Spec
                .TIME_ERASE_PRESENTATION_RADIUS;
        double radiusSqr = radius * radius;
        ClientboundSoundPacket sound = new ClientboundSoundPacket(
                KingCrimsonAddon.TIME_ERASE_END,
                SoundSource.PLAYERS,
                source.getX(),
                source.getY(),
                source.getZ(),
                1.0F,
                1.0F,
                source.getRandom().nextLong());
        TimeEraseTransitionPayload transition =
                TimeEraseTransitionPayload.INSTANCE;
        for (ServerPlayer observer :
                source.serverLevel().players()) {
            if (observer.distanceToSqr(source) <= radiusSqr) {
                observer.connection.send(sound);
                PacketDistributor.sendToPlayer(
                        observer, transition);
            }
        }
    }

    private static int saturatingAdd(int value, int amount) {
        return value > Integer.MAX_VALUE - amount
                ? Integer.MAX_VALUE
                : value + amount;
    }

    private static SoundEvent chooseTimeSkipSound(
            RandomSource random) {
        return random.nextBoolean()
                ? KingCrimsonAddon.KINGCRIMSON_TIMESKIP.get()
                : KingCrimsonAddon.KINGCRIMSON_TIMESKIP2.get();
    }

    private static void setTimeEraseCooldown(
            ServerPlayer player, int elapsedTicks) {
        StandPower power = StandPower.get(player);
        if (power == null) {
            return;
        }
        @Nullable
        Ability ability = power.getAbility(
                "kingcrimson_timeerase");
        if (ability != null) {
            ability.setCooldownOnUse(power, elapsedTicks);
        }
    }
}
