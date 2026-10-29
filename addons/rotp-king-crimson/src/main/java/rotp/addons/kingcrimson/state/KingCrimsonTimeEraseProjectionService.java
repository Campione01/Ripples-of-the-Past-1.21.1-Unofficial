package rotp.addons.kingcrimson.state;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import rotp.core.powersystem.standpower.StandUtil;
import com.mojang.logging.LogUtils;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.KingCrimsonPhase5Spec;
import rotp.addons.kingcrimson.entity.KingCrimsonAfterimageEntity;
import rotp.addons.kingcrimson.entity.KingCrimsonTimeEraseDecoyEntity;
import rotp.addons.kingcrimson.network.TimeEraseAfterimageRemovePayload;
import rotp.addons.kingcrimson.network.TimeEraseAfterimageSpawnPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.network.PacketDistributor;

public final class KingCrimsonTimeEraseProjectionService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private KingCrimsonTimeEraseProjectionService() {
    }

    public static void begin(ServerPlayer player) {
        KingCrimsonTemporalState state =
                KingCrimsonAddon.temporalState(player);
        Session stale = state.takeProjectionSession();
        if (stale != null) {
            stale.close(player);
        }

        Session session = new Session(player.serverLevel());
        state.setProjectionSession(session);
        try {
            spawnAfterimages(player, session);
            spawnDecoy(player, session);
        } catch (RuntimeException error) {
            state.takeProjectionSession();
            session.close(player);
            LOGGER.error(
                    "Failed to initialize King Crimson Time Erase "
                            + "projection for {}.",
                    player.getGameProfile().getName(),
                    error);
        }
    }

    public static void tick(ServerPlayer player) {
        Session session = KingCrimsonAddon.temporalState(player)
                .projectionSession();
        if (session != null
                && session.level != player.serverLevel()) {
            finish(player);
        }
    }

    public static void finish(ServerPlayer player) {
        Session session = KingCrimsonAddon.temporalState(player)
                .takeProjectionSession();
        if (session != null) {
            session.close(player);
        }
    }

    // One dark-red afterimage trailing each entity by half a second, as in 1.16. The donor's second,
    // full-color afterimage stood still for three seconds to mark where erase end would rewind the entity,
    // and its glow gave the caster an outline of every tracked creature through walls. Owner decision
    // 2026-09-23 (no rewind, manga logic) drops that copy; by the owner's follow-up the same day its red
    // outline moves onto the dark-red afterimage, which the caster's client alone receives.
    private static void spawnAfterimages(
            ServerPlayer player, Session session) {
        List<LivingEntity> targets =
                session.level.getEntitiesOfClass(
                        LivingEntity.class,
                        player.getBoundingBox().inflate(
                                KingCrimsonPhase5Spec
                                        .TIME_ERASE_AFTERIMAGE_RADIUS),
                        target -> isAfterimageTarget(player, target));
        for (LivingEntity target : targets) {
            spawnAfterimage(
                    player,
                    session.afterimageIds,
                    target,
                    KingCrimsonPhase5Spec
                            .TIME_ERASE_MOVING_AFTERIMAGE_DELAY_TICKS);
        }
    }

    private static boolean isAfterimageTarget(
            ServerPlayer player, LivingEntity target) {
        return target.isAlive()
                && target != player
                && !(target
                        instanceof KingCrimsonTimeEraseDecoyEntity)
                && StandUtil.getStandUser(target) != player;
    }

    private static void spawnAfterimage(
            ServerPlayer observer,
            List<Integer> entityIds,
            LivingEntity target,
            int delayTicks) {
        KingCrimsonAfterimageEntity afterimage =
                KingCrimsonAddon.KC_AFTERIMAGE_ENTITY.get()
                        .create(target.level());
        if (afterimage == null) {
            return;
        }
        afterimage.initialize(
                target,
                delayTicks,
                KingCrimsonConfig.timeEraseDurationTicks());
        entityIds.add(afterimage.getId());
        PacketDistributor.sendToPlayer(
                observer,
                TimeEraseAfterimageSpawnPayload.from(afterimage));
    }

    private static void spawnDecoy(
            ServerPlayer player, Session session) {
        KingCrimsonTimeEraseDecoyEntity decoy =
                KingCrimsonAddon.TIME_ERASE_DECOY_ENTITY.get()
                        .create(session.level);
        if (decoy == null) {
            return;
        }
        decoy.setOwner(player);
        if (!session.level.addFreshEntity(decoy)) {
            decoy.discard();
            return;
        }
        session.decoy = decoy;
        session.retargetedMobs.addAll(
                session.level.getEntitiesOfClass(
                        Mob.class,
                        player.getBoundingBox().inflate(
                                KingCrimsonPhase5Spec
                                        .TIME_ERASE_DECOY_TARGET_RADIUS),
                        mob -> mob.getTarget() == player));
        session.retargetedMobs.forEach(
                mob -> mob.setTarget(decoy));
    }

    static final class Session {
        private final ServerLevel level;
        private final List<Integer> afterimageIds =
                new ArrayList<>();
        private final List<Mob> retargetedMobs =
                new ArrayList<>();
        @Nullable
        private KingCrimsonTimeEraseDecoyEntity decoy;
        private boolean closed;

        private Session(ServerLevel level) {
            this.level = level;
        }

        private void close(ServerPlayer observer) {
            if (closed) {
                return;
            }
            closed = true;
            sendRemoval(observer, afterimageIds);

            if (decoy != null) {
                for (Mob mob : retargetedMobs) {
                    if (mob.isAlive() && mob.getTarget() == decoy) {
                        mob.setTarget(null);
                    }
                }
                decoy.discard();
            }
            afterimageIds.clear();
            retargetedMobs.clear();
            decoy = null;
        }

        private static void sendRemoval(
                ServerPlayer observer, List<Integer> entityIds) {
            if (entityIds.isEmpty()
                    || observer.connection == null
                    || observer.hasDisconnected()) {
                return;
            }
            try {
                PacketDistributor.sendToPlayer(
                        observer,
                        new TimeEraseAfterimageRemovePayload(
                                entityIds.stream()
                                        .mapToInt(Integer::intValue)
                                        .toArray()));
            } catch (RuntimeException error) {
                LOGGER.debug(
                        "Time Erase observer disconnected before "
                                + "afterimage cleanup.",
                        error);
            }
        }
    }

}
