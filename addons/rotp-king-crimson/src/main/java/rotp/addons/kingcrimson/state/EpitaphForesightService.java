package rotp.addons.kingcrimson.state;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonEpitaphForesightSpec;
import rotp.addons.kingcrimson.entity.KingCrimsonTimeEraseDecoyEntity;
import rotp.addons.kingcrimson.network.EpitaphForesightPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server side of Epitaph's vision. While Epitaph is armed, the future of nearby creatures and the flight of nearby
 * projectiles are foreseen on the server and sent to the Epitaph user alone. How far ahead depends on the user's
 * resolve level (see {@link KingCrimsonEpitaphForesightSpec#foresightTicks(int)}).
 *
 * <p>What Epitaph foresees for a creature is its fate, as in the manga: a creature following a navigation path is
 * walked along that path at its speed, the point it reaches is shown, and from then on it keeps to that exact path
 * whatever its AI decides until the foreseen moment arrives. A creature moving without a path keeps its momentum for
 * a short while. Knocking a creature far off its course releases it; erasing time does not undo other creatures' outcomes.
 * Other players and their Stands keep their own will: their future is shown but not bound. Projectiles are stepped with their own
 * gravity and air drag until they hit a block.</p>
 */
public final class EpitaphForesightService {
    private static final double PROJECTILE_DRAG = 0.99D;
    private static final double MIN_PROJECTILE_SPEED_SQR = 0.01D;
    private static final double MIN_WALK_SPEED = 1.0E-3D;
    /**
     * Ground walking speed per tick of a mob whose move control speed is s is about s * s * this factor (Mob.setSpeed
     * also sets the forward input to s, and ground friction settles the motion). Used when a mob is only starting off.
     */
    private static final double GROUND_WALK_SPEED_FACTOR = 2.92D;
    private static final double MIN_FATE_STEER_PER_TICK = 0.15D;

    private EpitaphForesightService() {}

    /** Keeps retained fates bounded even when the vision is closed or their target no longer ticks. */
    public static void prune(ServerPlayer player) {
        if (!player.hasData(KingCrimsonAddon.EPITAPH_STATE)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long gameTime = level.getGameTime();
        EpitaphState state = KingCrimsonAddon.epitaphState(player);
        state.redeciding().values().removeIf(until -> until <= gameTime);
        for (Iterator<Map.Entry<UUID, EpitaphFate>> it = state.fates().entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, EpitaphFate> entry = it.next();
            Entity target = level.getEntity(entry.getKey());
            if (target == null || !entry.getValue().holds(target, gameTime)) {
                it.remove();
                state.redeciding().put(entry.getKey(), gameTime + KingCrimsonEpitaphForesightSpec.REDECIDE_TICKS);
            }
        }
    }

    public static void tick(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        long gameTime = level.getGameTime();
        if (gameTime % KingCrimsonEpitaphForesightSpec.REFRESH_TICKS != 0) {
            return;
        }
        StandPower power = StandPower.get(player);
        int ticksAhead = KingCrimsonEpitaphForesightSpec.foresightTicks(
                power != null ? power.getResolveLevel() : 0);
        Comparator<Entity> nearest = Comparator.comparingDouble(player::distanceToSqr);
        List<EpitaphForesightPayload.Creature> creatures = new ArrayList<>();
        Set<UUID> shown = new HashSet<>();

        // Fates this user already foresaw stay exactly as they were foreseen.
        EpitaphState state = KingCrimsonAddon.epitaphState(player);
        Map<UUID, EpitaphFate> fates = state.fates();
        Map<UUID, Long> redeciding = state.redeciding();
        redeciding.values().removeIf(until -> until <= gameTime);
        List<LivingEntity> bound = new ArrayList<>();
        for (Iterator<Map.Entry<UUID, EpitaphFate>> it = fates.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, EpitaphFate> entry = it.next();
            EpitaphFate fate = entry.getValue();
            LivingEntity target = level.getEntity(entry.getKey()) instanceof LivingEntity living ? living : null;
            if (target == null
                    || !isForeseenCreature(player, target)
                    || player.distanceToSqr(target) > KingCrimsonEpitaphForesightSpec.FATE_KEEP_RADIUS
                            * KingCrimsonEpitaphForesightSpec.FATE_KEEP_RADIUS
                    || !fate.holds(target, gameTime)) {
                it.remove();
                redeciding.put(entry.getKey(), gameTime + KingCrimsonEpitaphForesightSpec.REDECIDE_TICKS);
                continue;
            }
            bound.add(target);
        }
        bound.sort(nearest);
        for (LivingEntity target : bound) {
            if (creatures.size() >= KingCrimsonEpitaphForesightSpec.MAX_CREATURES) {
                fates.remove(target.getUUID());
                continue;
            }
            creatures.add(new EpitaphForesightPayload.Creature(
                    target.getId(), target.getUUID(), fates.get(target.getUUID()).foreseen));
            shown.add(target.getUUID());
        }

        AABB area = player.getBoundingBox().inflate(KingCrimsonEpitaphForesightSpec.RADIUS);
        List<LivingEntity> living = level.getEntitiesOfClass(LivingEntity.class, area,
                target -> !shown.contains(target.getUUID())
                        && !redeciding.containsKey(target.getUUID())
                        && isForeseenCreature(player, target));
        living.sort(nearest);
        for (LivingEntity target : living) {
            if (creatures.size() >= KingCrimsonEpitaphForesightSpec.MAX_CREATURES) {
                break;
            }
            EpitaphFate other = findFate(level, target.getUUID());
            Vec3 foreseen;
            if (other != null && other.holds(target, gameTime)) {
                // Another Epitaph user already foresaw this creature: it is the same future.
                foreseen = other.foreseen;
            }
            else if (target instanceof Mob mob) {
                EpitaphFate fate = commit(level, mob, ticksAhead, gameTime);
                if (fate == null) {
                    continue;
                }
                fates.put(mob.getUUID(), fate);
                foreseen = fate.foreseen;
            }
            else {
                foreseen = foreseeUnbound(level, target, ticksAhead);
                if (!isVisibleShift(target, foreseen)) {
                    continue;
                }
            }
            creatures.add(new EpitaphForesightPayload.Creature(target.getId(), target.getUUID(), foreseen));
        }

        List<EpitaphForesightPayload.Flight> flights = new ArrayList<>();
        List<Projectile> projectiles = level.getEntitiesOfClass(Projectile.class, area,
                projectile -> isForeseenProjectile(player, projectile));
        projectiles.sort(nearest);
        AABB userBox = player.getBoundingBox().inflate(0.3D);
        for (Projectile projectile : projectiles) {
            if (flights.size() >= KingCrimsonEpitaphForesightSpec.MAX_PROJECTILES) {
                break;
            }
            flights.add(foreseeFlight(level, projectile, ticksAhead, userBox));
        }

        PacketDistributor.sendToPlayer(player, new EpitaphForesightPayload(creatures, flights));
    }

    /** Called after every entity tick: a fated creature keeps to its foreseen course whatever its AI decided. */
    public static void enforce(Entity entity) {
        if (!(entity instanceof Mob mob) || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        EpitaphState binding = bindingState(level, mob.getUUID());
        if (binding == null) {
            return;
        }
        Map<UUID, EpitaphFate> fates = binding.fates();
        EpitaphFate fate = fates.get(mob.getUUID());
        if (!fate.holds(mob, level.getGameTime())) {
            fates.remove(mob.getUUID());
            binding.redeciding().put(mob.getUUID(),
                    level.getGameTime() + KingCrimsonEpitaphForesightSpec.REDECIDE_TICKS);
            return;
        }
        if (fate.path != null) {
            PathNavigation navigation = mob.getNavigation();
            if (navigation.getPath() != fate.path) {
                navigation.stop();
                navigation.moveTo(fate.path, fate.speedModifier);
            }
        }
        Vec3 course = fate.courseAt(mob.level().getGameTime());
        Vec3 steer = course.subtract(mob.position());
        if (fate.path == null && !mob.isNoGravity()) {
            steer = new Vec3(steer.x, 0.0D, steer.z);
        }
        double maxSteer = Math.max(MIN_FATE_STEER_PER_TICK, fate.speed * 1.5D);
        if (steer.lengthSqr() > maxSteer * maxSteer) {
            steer = steer.normalize().scale(maxSteer);
        }
        if (steer.lengthSqr() > 1.0E-6D) {
            mob.move(MoverType.SELF, steer);
        }
        if (fate.isFulfilled(mob, level.getGameTime())) {
            fates.remove(mob.getUUID());
            binding.redeciding().put(mob.getUUID(),
                    level.getGameTime() + KingCrimsonEpitaphForesightSpec.REDECIDE_TICKS);
        }
    }

    /** Exceptional owner cleanup: ordinary vision expiry and Time Erase do not undo a committed future. */
    public static void releaseAll(ServerPlayer player) {
        if (!player.hasData(KingCrimsonAddon.EPITAPH_STATE)) {
            return;
        }
        EpitaphState state = KingCrimsonAddon.epitaphState(player);
        if (state.fates().isEmpty()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long until = level.getGameTime() + KingCrimsonEpitaphForesightSpec.REDECIDE_TICKS;
        state.fates().forEach((creature, fate) -> {
            // A released creature drops the course it was bound to, so its own AI chooses where to go next.
            if (fate.path != null
                    && level.getEntity(creature) instanceof Mob mob
                    && mob.getNavigation().getPath() == fate.path) {
                mob.getNavigation().stop();
            }
            state.redeciding().put(creature, until);
        });
        state.fates().clear();
    }

    /** The Epitaph state of the user in this level who foresaw the creature, or null when it is not bound. */
    @Nullable
    private static EpitaphState bindingState(ServerLevel level, UUID creature) {
        for (ServerPlayer player : level.players()) {
            if (player.hasData(KingCrimsonAddon.EPITAPH_STATE)) {
                EpitaphState state = KingCrimsonAddon.epitaphState(player);
                if (!state.fates().isEmpty() && state.fates().containsKey(creature)) {
                    return state;
                }
            }
        }
        return null;
    }

    @Nullable
    private static EpitaphFate findFate(ServerLevel level, UUID creature) {
        EpitaphState binding = bindingState(level, creature);
        return binding != null ? binding.fates().get(creature) : null;
    }

    private static boolean isForeseenCreature(ServerPlayer player, LivingEntity target) {
        return target != player
                && target.isAlive()
                && !target.isSpectator()
                && !(target instanceof KingCrimsonTimeEraseDecoyEntity)
                && StandUtil.getStandUser(target) != player;
    }

    private static boolean isForeseenProjectile(ServerPlayer player, Projectile projectile) {
        return projectile.isAlive()
                && projectile.getOwner() != player
                && projectile.getDeltaMovement().lengthSqr() >= MIN_PROJECTILE_SPEED_SQR;
    }

    private static boolean isVisibleShift(Entity target, Vec3 foreseen) {
        return foreseen.distanceToSqr(target.position())
                >= KingCrimsonEpitaphForesightSpec.MIN_VISIBLE_SHIFT
                        * KingCrimsonEpitaphForesightSpec.MIN_VISIBLE_SHIFT;
    }

    /** Foresees a mob and binds it to what was foreseen, or returns null when it is not going anywhere. */
    @Nullable
    static EpitaphFate commit(ServerLevel level, Mob mob, int ticksAhead, long gameTime) {
        Vec3 position = mob.position();
        Vec3 step = step(mob);
        Path path = mob.getNavigation().getPath();
        if (path != null && !path.isDone()) {
            double speed = step.horizontalDistance();
            double speedModifier = mob.getMoveControl().getSpeedModifier();
            if (speedModifier <= 0.0D) {
                speedModifier = 1.0D;
            }
            if (speed <= MIN_WALK_SPEED) {
                double moveSpeed = speedModifier * mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
                speed = moveSpeed * moveSpeed * GROUND_WALK_SPEED_FACTOR;
            }
            if (speed > MIN_WALK_SPEED) {
                double remaining = speed * ticksAhead;
                double walked = 0.0D;
                Vec3 foreseen = position;
                List<Vec3> course = new ArrayList<>();
                course.add(position);
                for (int index = path.getNextNodeIndex(); index < path.getNodeCount() && remaining > 0.0D; index++) {
                    Vec3 node = path.getEntityPosAtNode(mob, index);
                    double distance = foreseen.distanceTo(node);
                    if (distance >= remaining) {
                        foreseen = foreseen.add(node.subtract(foreseen).scale(remaining / distance));
                        walked += remaining;
                        remaining = 0.0D;
                    }
                    else {
                        remaining -= distance;
                        walked += distance;
                        foreseen = node;
                    }
                    course.add(foreseen);
                }
                if (!isVisibleShift(mob, foreseen)) {
                    return null;
                }
                int ticksToArrive = Math.max(1, Math.min(ticksAhead, (int) Math.ceil(walked / speed)));
                return new EpitaphFate(level.dimension(), course, speed, path, speedModifier,
                        gameTime, gameTime + ticksToArrive);
            }
        }
        int ticks = Math.min(ticksAhead, KingCrimsonEpitaphForesightSpec.UNGUIDED_FATE_TICKS);
        Vec3 foreseen = foreseeByMomentum(level, mob, step, ticks);
        if (!isVisibleShift(mob, foreseen)) {
            return null;
        }
        return new EpitaphFate(level.dimension(), List.of(position, foreseen), step.length(), null, 1.0D,
                gameTime, gameTime + ticks);
    }

    /** A creature that is not bound (another player): where its current motion takes it. */
    static Vec3 foreseeUnbound(Level level, LivingEntity target, int ticksAhead) {
        Vec3 step = step(target);
        int ticks = Math.min(ticksAhead, KingCrimsonEpitaphForesightSpec.UNGUIDED_FATE_TICKS);
        return foreseeByMomentum(level, target, step, ticks);
    }

    private static Vec3 step(Entity target) {
        return new Vec3(target.getX() - target.xo, target.getY() - target.yo, target.getZ() - target.zo);
    }

    private static Vec3 foreseeByMomentum(Level level, Entity target, Vec3 step, int ticks) {
        Vec3 position = target.position();
        double vertical = target.onGround() ? 0.0D : step.y;
        Vec3 foreseen = position.add(step.x * ticks, vertical * ticks, step.z * ticks);
        return stopAtBlocks(level, target, position, foreseen);
    }

    private static Vec3 stopAtBlocks(Level level, Entity target, Vec3 from, Vec3 to) {
        double lift = target.getBbHeight() * 0.5D;
        HitResult hit = level.clip(new ClipContext(
                from.add(0.0D, lift, 0.0D),
                to.add(0.0D, lift, 0.0D),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                target));
        if (hit.getType() == HitResult.Type.MISS) {
            return to;
        }
        Vec3 stop = hit.getLocation().subtract(0.0D, lift, 0.0D);
        Vec3 back = stop.subtract(from);
        double length = back.length();
        return length > target.getBbWidth() * 0.5D
                ? from.add(back.scale((length - target.getBbWidth() * 0.5D) / length))
                : from;
    }

    /** Steps a projectile with its own gravity and air drag until it strikes a block or the vision ends. */
    static EpitaphForesightPayload.Flight foreseeFlight(
            Level level, Projectile projectile, int ticksAhead, AABB userBox) {
        int interval = Math.max(KingCrimsonEpitaphForesightSpec.PATH_POINT_INTERVAL_TICKS,
                (int) Math.ceil(ticksAhead / (double) (KingCrimsonEpitaphForesightSpec.MAX_FLIGHT_POINTS - 2)));
        List<Vec3> points = new ArrayList<>();
        Vec3 position = projectile.position();
        Vec3 motion = projectile.getDeltaMovement();
        double gravity = projectile.getGravity();
        boolean hitsUser = false;
        points.add(position);
        for (int tick = 1; tick <= ticksAhead; tick++) {
            Vec3 next = position.add(motion);
            HitResult hit = level.clip(new ClipContext(
                    position, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, projectile));
            Vec3 reachableEnd = hit.getType() == HitResult.Type.MISS ? next : hit.getLocation();
            if (!hitsUser && (userBox.contains(position) || userBox.clip(position, reachableEnd).isPresent())) {
                hitsUser = true;
            }
            if (hit.getType() != HitResult.Type.MISS) {
                points.add(hit.getLocation());
                break;
            }
            position = next;
            motion = motion.scale(PROJECTILE_DRAG).add(0.0D, -gravity, 0.0D);
            if (tick % interval == 0 || tick == ticksAhead) {
                points.add(position);
            }
        }
        return new EpitaphForesightPayload.Flight(points, hitsUser);
    }
}
