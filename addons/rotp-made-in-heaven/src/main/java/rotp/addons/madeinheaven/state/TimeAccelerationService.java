package rotp.addons.madeinheaven.state;

import java.util.UUID;

import rotp.core.customobjects.AfterimageEntity;
import rotp.core.impl.stands.theworld.TimeStopAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.timestop.TimeStopLearning;
import rotp.core.subsystems.timestop.TimeStopState;

import rotp.addons.madeinheaven.MadeInHeavenAddon;
import rotp.addons.madeinheaven.ability.UniverseResetAbility;
import rotp.addons.madeinheaven.network.TimeAccelerationStatePayload;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.network.PacketDistributor;

// 1.16 GameplayEventHandler/TimeUtil: the server keeps its tick rate;
// acceleration moves the day time, projectiles, the owner and other
// users' time stops.
public final class TimeAccelerationService {
    public static final int BUFF_PHASE = 4;
    public static final int LAST_PHASE = 30;
    public static final int LEGACY_PHASE_TICKS = 150;
    public static final long DAY_TIME_STEP = 20L;

    private static final ResourceLocation MOVEMENT_MODIFIER =
            MadeInHeavenAddon.id(
                    "time_acceleration_movement");
    private static final ResourceLocation SWIM_MODIFIER =
            MadeInHeavenAddon.id(
                    "time_acceleration_swim");
    private static final ResourceLocation STEP_MODIFIER =
            MadeInHeavenAddon.id(
                    "time_acceleration_step");

    private TimeAccelerationService() {}

    public static boolean start(ServerPlayer owner) {
        ServerLevel level = owner.serverLevel();
        if (level.dimension() != Level.OVERWORLD
                || !level.getGameRules().getBoolean(
                        GameRules.RULE_DAYLIGHT)
                || !canStart(owner)) {
            return false;
        }
        TimeAccelerationState state =
                MadeInHeavenAddon.accelerationState(level);
        if (state.active()) {
            return false;
        }
        state.begin(owner.getUUID());
        playLoopSounds(level, owner);
        syncAll(level.getServer(), state);
        return true;
    }

    public static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        TimeAccelerationState state =
                MadeInHeavenAddon.accelerationState(level);
        if (!state.active()) {
            return;
        }
        ServerPlayer owner =
                server.getPlayerList().getPlayer(
                        state.ownerUuid());
        if (owner == null
                || owner.serverLevel() != level
                || !owner.isAlive()) {
            clear(level, ClearReason.OWNER_INVALID);
            return;
        }
        if (!stillOwnsStandPower(owner)) {
            clear(level, ClearReason.STAND_LOSS);
            return;
        }

        TimeStopState timeStop =
                level.getData(ModDataAttachmentTypes.TIME_STOP);
        shortenTimeStops(level, state, timeStop);
        // 1.16 onAccelerate: nothing advances while the owner stands in
        // stopped time.
        if (timeStop.isTimeStopped(owner)) {
            return;
        }

        // 1.16 accelerateTime: 20 * phase extra day time every other tick,
        // a new phase every 150 ticks, and the reset once phase 31 ends.
        int phase = state.phase();
        int counter = state.tickCounter();
        long extraDayTime = counter % 2 == 0
                ? DAY_TIME_STEP * phase
                : 0L;
        int nextPhase = phase;
        if (counter % LEGACY_PHASE_TICKS == 0) {
            if (phase > LAST_PHASE) {
                level.setDayTime(level.getDayTime() + extraDayTime);
                broadcastDayTime(level);
                UniverseResetAbility.execute(owner);
                clear(level, ClearReason.UNIVERSE_RESET);
                return;
            }
            nextPhase = phase + 1;
        }
        state.advance(nextPhase);
        level.setDayTime(level.getDayTime() + extraDayTime);
        broadcastDayTime(level);
        StandPower power = StandPower.get(owner);
        if (power != null
                && !owner.getDeltaMovement().equals(Vec3.ZERO)) {
            power.consumeStamina(owner.getSpeed() * 5.0F);
        }
        applyMovement(owner, power, phase);
        if (nextPhase != phase) {
            syncAll(server, state);
        }
    }

    // 1.16 onEntityJoin: a projectile entering the accelerated level starts
    // at the phase multiplier's speed.
    public static void onEntityJoin(Entity entity, Level level) {
        if (level.isClientSide() || !(entity instanceof Projectile)) {
            return;
        }
        TimeAccelerationState state =
                MadeInHeavenAddon.accelerationState(level);
        if (state.active()) {
            entity.setDeltaMovement(entity.getDeltaMovement().scale(
                    calculatedMultiplier(state.phase())));
        }
    }

    // 1.16 onLivingUpdate: a time stopper who spends
    // 2 * max / multiplier ticks in stopped time during the acceleration
    // gets their own time stop resumed, as a time-resume press would.
    private static void shortenTimeStops(
            ServerLevel level,
            TimeAccelerationState state,
            TimeStopState timeStop) {
        if (timeStop.getInstances().isEmpty()) {
            return;
        }
        int multiplier = calculatedMultiplier(state.phase());
        for (Entity entity : level.getAllEntities()) {
            // Only entities that actually tick, as with 1.16's update event.
            if (!(entity instanceof LivingEntity living)
                    || !timeStop.isTimeStopped(living)
                    || timeStop.shouldFreeze(living)
                    || !level.isPositionEntityTicking(
                            living.blockPosition())) {
                continue;
            }
            StandPower power = StandPower.get(living);
            if (power == null
                    || !power.hasPower()
                    || !hasUnlockedTimeStop(power)) {
                continue;
            }
            // maxTicks is the user's non-creative maximum, like 1.16's
            // getMaxTimeStopTicks.
            int ticks = state.countTimeStopTick(living);
            int maxTicks = TimeStopLearning.getMaxTrainingPoints(power)
                    + TimeStopLearning.MIN_TIME_STOP_TICKS;
            long limit = 2L * maxTicks / multiplier;
            if (ticks >= limit) {
                timeStop.requestManualResume(living.getId());
                state.resetTimeStopTicks(living);
            }
        }
    }

    private static boolean hasUnlockedTimeStop(StandPower power) {
        for (Ability ability :
                power.getMoveset().abilities.values()) {
            if (ability instanceof TimeStopAbility
                    && ability.isAbilityUnlocked(power)) {
                return true;
            }
        }
        return false;
    }

    // 1.16 shipped with a required time mod that resent the day time every
    // tick; without it clients would see the sky jump once a second.
    private static void broadcastDayTime(ServerLevel level) {
        ClientboundSetTimePacket packet =
                new ClientboundSetTimePacket(
                        level.getGameTime(),
                        level.getDayTime(),
                        level.getGameRules().getBoolean(
                                GameRules.RULE_DAYLIGHT));
        for (ServerPlayer player : level.players()) {
            player.connection.send(packet);
        }
    }

    public static boolean isActiveOwner(
            LivingEntity candidate) {
        if (candidate == null) {
            return false;
        }
        TimeAccelerationState state =
                MadeInHeavenAddon.accelerationState(
                        candidate.level());
        return state.isOwner(candidate.getUUID());
    }

    public static int phase(Level level) {
        return MadeInHeavenAddon
                .accelerationState(level)
                .phase();
    }

    // 1.16 TimeUtil.getCalculatedPhase: 0-5 -> 2, 6-10 -> 3, 11-15 -> 4,
    // 16-20 -> 5, 21-25 -> 6, 26-30 -> 7, any other phase -> 1.
    public static int calculatedMultiplier(int phase) {
        if (phase < 0 || phase > LAST_PHASE) {
            return 1;
        }
        return phase <= 5 ? 2 : (phase - 1) / 5 + 2;
    }

    public static void clearIfOwner(
            LivingEntity owner, ClearReason reason) {
        MinecraftServer server = owner.getServer();
        if (server == null) {
            return;
        }
        ServerLevel level = server.overworld();
        TimeAccelerationState state =
                MadeInHeavenAddon.accelerationState(level);
        if (state.isOwner(owner.getUUID())) {
            clear(level, reason);
        }
    }

    public static void clear(
            ServerLevel level, ClearReason reason) {
        TimeAccelerationState state =
                MadeInHeavenAddon.accelerationState(level);
        if (!state.active()) {
            return;
        }
        MinecraftServer server = level.getServer();
        UUID ownerUuid = state.ownerUuid();
        ServerPlayer owner = ownerUuid != null
                ? server.getPlayerList().getPlayer(ownerUuid)
                : null;
        if (owner != null) {
            removeMovement(owner);
        }
        stopLoopSounds(server);
        state.clear();
        syncAll(server, state);
    }

    public static void syncTo(ServerPlayer player) {
        TimeAccelerationState state =
                MadeInHeavenAddon.accelerationState(
                        player.getServer().overworld());
        PacketDistributor.sendToPlayer(
                player,
                TimeAccelerationStatePayload.from(state));
    }

    private static boolean canStart(ServerPlayer owner) {
        StandPower power = StandPower.get(owner);
        StandEntity stand =
                power != null
                        ? power.getSummonedStandEntity()
                        : null;
        return stillOwnsStandPower(owner)
                && stand != null
                && !stand.isArmsOnlyMode()
                && stand.isAlive();
    }

    private static boolean stillOwnsStandPower(
            ServerPlayer owner) {
        StandPower power = StandPower.get(owner);
        return power != null
                && power.getPowerType()
                        == MadeInHeavenAddon
                                .MADE_IN_HEAVEN.get();
    }

    // 1.16 boostOnAcceleration, with the phase from before this tick's
    // advance. Unlike 1.16 it keeps working while the Stand is retracted,
    // because the acceleration itself now outlives a retraction.
    private static void applyMovement(
            ServerPlayer owner,
            StandPower power,
            int phase) {
        boolean movingFast = owner.isSprinting()
                || (owner.isSwimming()
                        && owner.isInWater());
        if (!movingFast) {
            removeMovement(owner);
            return;
        }
        if (phase >= BUFF_PHASE) {
            AfterimageEntity.addAfterimages(owner, 3, 75);
            StandEntity stand = power != null
                    ? power.getSummonedStandEntity()
                    : null;
            if (stand != null) {
                AfterimageEntity.addAfterimages(stand, 3, 75);
            }
            updateModifier(
                    owner,
                    Attributes.STEP_HEIGHT,
                    STEP_MODIFIER,
                    0.4D
                            + calculatedMultiplier(phase)
                                    / 10.0D,
                    AttributeModifier.Operation.ADD_VALUE);
            liquidSurfaceTick(owner);
        }
        updateModifier(
                owner,
                Attributes.MOVEMENT_SPEED,
                MOVEMENT_MODIFIER,
                0.4D * phase,
                AttributeModifier.Operation
                        .ADD_MULTIPLIED_TOTAL);
        updateModifier(
                owner,
                NeoForgeMod.SWIM_SPEED,
                SWIM_MODIFIER,
                0.4D * phase,
                AttributeModifier.Operation
                        .ADD_MULTIPLIED_TOTAL);
    }

    private static void liquidSurfaceTick(
            ServerPlayer owner) {
        if ((!owner.isInWater() && !owner.isInLava())
                || !owner.isSprinting()
                || owner.isShiftKeyDown()) {
            return;
        }
        StandPower power = StandPower.get(owner);
        if (power == null
                || power.getStamina() <= 0.0F
                || !power.consumeStamina(2.0F, true)) {
            return;
        }
        Vec3 motion = owner.getDeltaMovement();
        owner.setDeltaMovement(
                motion.x,
                Math.max(motion.y, 0.0D),
                motion.z);
        owner.resetFallDistance();
        owner.setOnGround(true);
        owner.hasImpulse = true;
        if (owner.isInLava() && !owner.fireImmune()) {
            owner.hurt(owner.damageSources().lava(), 1.0F);
        }
    }

    private static void removeMovement(
            LivingEntity owner) {
        removeModifier(
                owner,
                Attributes.MOVEMENT_SPEED,
                MOVEMENT_MODIFIER);
        removeModifier(
                owner,
                NeoForgeMod.SWIM_SPEED,
                SWIM_MODIFIER);
        removeModifier(
                owner,
                Attributes.STEP_HEIGHT,
                STEP_MODIFIER);
    }

    private static void updateModifier(
            LivingEntity owner,
            net.minecraft.core.Holder<Attribute> attribute,
            ResourceLocation id,
            double amount,
            AttributeModifier.Operation operation) {
        AttributeInstance instance =
                owner.getAttribute(attribute);
        if (instance != null) {
            instance.addOrUpdateTransientModifier(
                    new AttributeModifier(
                            id, amount, operation));
        }
    }

    private static void removeModifier(
            LivingEntity owner,
            net.minecraft.core.Holder<Attribute> attribute,
            ResourceLocation id) {
        AttributeInstance instance =
                owner.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(id);
        }
    }

    private static void playLoopSounds(
            ServerLevel level,
            ServerPlayer owner) {
        StandPower power = StandPower.get(owner);
        StandEntity stand = power != null
                ? power.getSummonedStandEntity()
                : null;
        if (stand != null) {
            level.playSound(
                    null,
                    stand.getX(),
                    stand.getY(),
                    stand.getZ(),
                    MadeInHeavenAddon
                            .MIH_TIME_ACCELERATION.get(),
                    SoundSource.VOICE,
                    1.0F,
                    1.0F);
        }
        level.playSound(
                null,
                owner.getX(),
                owner.getY(),
                owner.getZ(),
                MadeInHeavenAddon
                        .MIH_TIME_ACCELERATION_USER.get(),
                SoundSource.PLAYERS,
                1.0F,
                1.0F);
    }

    private static void stopLoopSounds(
            MinecraftServer server) {
        ClientboundStopSoundPacket standStop =
                new ClientboundStopSoundPacket(
                        MadeInHeavenAddon.id(
                                "mih_time_acceleration"),
                        SoundSource.VOICE);
        ClientboundStopSoundPacket userStop =
                new ClientboundStopSoundPacket(
                        MadeInHeavenAddon.id(
                                "mih_time_acceleration_user"),
                        SoundSource.PLAYERS);
        for (ServerPlayer player :
                server.getPlayerList().getPlayers()) {
            player.connection.send(standStop);
            player.connection.send(userStop);
        }
    }

    private static void syncAll(
            MinecraftServer server,
            TimeAccelerationState state) {
        TimeAccelerationStatePayload payload =
                TimeAccelerationStatePayload.from(state);
        for (ServerPlayer player :
                server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public enum ClearReason {
        MANUAL,
        OWNER_DEATH,
        OWNER_LOGOUT,
        DIMENSION_CHANGE,
        STAND_LOSS,
        OWNER_INVALID,
        SERVER_STOP,
        UNIVERSE_RESET
    }
}
