package rotp.addons.tickettoride.state;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.addons.tickettoride.TicketToRideAddon;
import rotp.addons.tickettoride.TicketToRideEvents;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class TicketToRideBehaviorService {
    private static final String HORSE_OWNER =
            "rotp_ttr.HorseOwner";
    private static final String SUMMON_OWNER =
            "rotp_ttr.SummonOwner";
    private static final String MISFORTUNE_LIFETIME =
            "rotp_ttr.MisfortuneLifetime";

    // 1.16 SummonTaggedMobsAction.MOB_TYPES
    private static final List<EntityType<? extends Mob>>
            MISFORTUNE_MOBS = List.of(
                    EntityType.BEE,
                    EntityType.PHANTOM,
                    EntityType.PHANTOM,
                    EntityType.CAVE_SPIDER,
                    EntityType.WOLF,
                    EntityType.SILVERFISH,
                    EntityType.SILVERFISH,
                    EntityType.SILVERFISH);
    // 1.16 MobBehaviorHandler.MAX_LIFETIME
    private static final int MAX_LIFETIME = 200;
    // 1.16 MobBehaviorHandler.PASSIVE_MOBS
    private static final Set<EntityType<?>> PASSIVE_MOBS = Set.of(
            EntityType.SLIME, EntityType.SQUID, EntityType.DOLPHIN);

    private static long markedSearches;

    private TicketToRideBehaviorService() {}

    /** How many box searches for marked entities the mob ticks have run. */
    public static long markedSearches() {
        return markedSearches;
    }

    // 1.16 MobCrash.spawnUnluckyHorse and findValidSpawnPosition: up to ten
    // tries 5 to 15 blocks from the target in a random 3D direction, on a
    // free block above a solid one, else 5 blocks along the target's look;
    // all from the level's random.
    public static Horse spawnHorse(
            ServerLevel level,
            LivingEntity owner,
            LivingEntity target) {
        Vec3 at = horseSpawnPos(level, target);
        Horse horse = EntityType.HORSE.create(level);
        if (horse == null) {
            return null;
        }
        horse.moveTo(
                at.x,
                at.y,
                at.z,
                level.getRandom().nextFloat() * 360.0F,
                0.0F);
        horse.setTamed(false);
        horse.setAge(0);
        horse.addEffect(new MobEffectInstance(
                MobEffects.UNLUCK,
                900,
                0,
                true,
                true));
        horse.setHealth(horse.getMaxHealth());
        horse.setAggressive(true);
        horse.getPersistentData().putUUID(
                HORSE_OWNER, owner.getUUID());
        level.addFreshEntity(horse);
        return horse;
    }

    private static Vec3 horseSpawnPos(
            ServerLevel level, LivingEntity target) {
        RandomSource random = level.getRandom();
        for (int i = 0; i < 10; i++) {
            double distance = 5.0D + random.nextDouble() * 10.0D;
            double yaw = random.nextDouble() * Math.PI * 2.0D;
            double pitch = random.nextDouble() * Math.PI
                    - Math.PI / 2.0D;
            double x = target.getX()
                    + distance * Math.cos(yaw) * Math.cos(pitch);
            double z = target.getZ()
                    + distance * Math.sin(yaw) * Math.cos(pitch);
            double y = target.getY() + distance * Math.sin(pitch);
            if (validHorseSpawn(level, BlockPos.containing(x, y, z))) {
                return new Vec3(x, y, z);
            }
        }
        return target.position()
                .add(target.getLookAngle().scale(5.0D))
                .add(0.0D, 0.5D, 0.0D);
    }

    private static boolean validHorseSpawn(
            ServerLevel level, BlockPos pos) {
        if (!level.isInWorldBounds(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return !state.liquid()
                && state.getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above())
                        .getCollisionShape(level, pos.above()).isEmpty()
                && !level.getBlockState(pos.below())
                        .getCollisionShape(level, pos.below()).isEmpty();
    }

    // D-06 kept: the 1.16 goal goes to Ticket to Ride's own horses only,
    // where 1.16 UnluckyHorseSpawnEvent gave it to every horse.
    public static void addUnluckyHorseGoal(Mob mob) {
        if (mob instanceof Horse horse
                && horse.getPersistentData().hasUUID(HORSE_OWNER)) {
            horse.goalSelector.addGoal(2, new UnluckyHorseGoal(horse));
        }
    }

    public static boolean isMisfortuneHorse(LivingEntity entity) {
        return entity instanceof Horse
                && entity.getPersistentData().hasUUID(HORSE_OWNER);
    }

    // 1.16 event/goal/UnluckyHorseGoal: while it has Unluck the horse runs at
    // the nearest marked entity within 64 at speed 2.4 and, within 1.7, hits
    // it for 30% of its health + 6 as magic, flings it 2.5 away and takes 60%
    // + 5 itself; in the last 20 ticks of Unluck it puffs and is removed.
    public static final class UnluckyHorseGoal extends Goal {
        private final Horse horse;
        @Nullable
        private LivingEntity target;
        private int cooldown;
        private int unluckTickCounter;

        public UnluckyHorseGoal(Horse horse) {
            this.horse = horse;
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        private int unluckDuration() {
            MobEffectInstance unluck = horse.getEffect(MobEffects.UNLUCK);
            return unluck != null ? unluck.getDuration() : 0;
        }

        @Override
        public boolean canUse() {
            if (!horse.hasEffect(MobEffects.UNLUCK)) {
                return false;
            }
            if (unluckDuration() <= 20) {
                return true;
            }
            target = horse.level().getEntitiesOfClass(
                            LivingEntity.class,
                            horse.getBoundingBox().inflate(64.0D))
                    .stream()
                    .filter(entity -> entity != horse
                            && entity.isAlive()
                            && TearsWoundService.isMarked(entity))
                    .min(Comparator.comparingDouble(horse::distanceToSqr))
                    .orElse(null);
            return target != null;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            cooldown = 0;
            unluckTickCounter = 0;
        }

        @Override
        public void stop() {
            cooldown = 0;
            unluckTickCounter = 0;
            target = null;
        }

        @Override
        public void tick() {
            if (!(horse.level() instanceof ServerLevel level)) {
                return;
            }
            if (horse.hasEffect(MobEffects.UNLUCK)
                    && unluckDuration() <= 20) {
                if (++unluckTickCounter >= 15) {
                    level.sendParticles(ParticleTypes.POOF,
                            horse.getX(),
                            horse.getY() + horse.getBbHeight() / 2.0D,
                            horse.getZ(),
                            10, 0.5D, 0.5D, 0.5D, 0.1D);
                }
                if (unluckDuration() <= 1) {
                    horse.discard();
                    return;
                }
            }
            if (target == null || !target.isAlive()
                    || !horse.hasEffect(MobEffects.UNLUCK)) {
                return;
            }
            if (cooldown > 0) {
                --cooldown;
                return;
            }
            double distance = horse.distanceTo(target);
            horse.getLookControl().setLookAt(target, 30.0F, 30.0F);
            horse.getNavigation().moveTo(target, 2.4D);
            if (distance > 1.7D) {
                return;
            }
            level.sendParticles(ParticleTypes.EXPLOSION,
                    target.getX(),
                    target.getY() + target.getBbHeight() / 2.0D,
                    target.getZ(),
                    20, 0.5D, 0.5D, 0.5D, 0.5D);
            level.playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                    SoundEvents.GENERIC_EXPLODE, horse.getSoundSource(),
                    1.0F, 1.0F);
            float damage = 0.0F;
            if (target.isAlive()) {
                damage = target.getMaxHealth() * 0.3F + 6.0F;
                target.hurt(target.damageSources().magic(), damage);
            }
            if (target.isAlive()) {
                Vec3 push = new Vec3(target.getX() - horse.getX(), 0.5D,
                        target.getZ() - horse.getZ()).normalize().scale(2.5D);
                target.setDeltaMovement(push);
                target.hurtMarked = true;
                level.playSound(null, target.getX(), target.getY(),
                        target.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK,
                        target.getSoundSource(), 1.0F, 1.0F);
            }
            if (horse.isAlive()) {
                horse.hurt(horse.damageSources().magic(),
                        damage * 0.6F + 5.0F);
                level.playSound(null, horse.getX(), horse.getY(),
                        horse.getZ(), SoundEvents.HORSE_HURT,
                        horse.getSoundSource(), 1.0F, 1.0F);
            }
            cooldown = 2;
        }
    }

    // 1.16 SummonTaggedMobsAction.standPerform and spawnTaggedMob: one
    // type for the three mobs of a marked entity, from the level's random.
    public static int spawnMisfortuneMobs(
            ServerLevel level,
            LivingEntity owner,
            LivingEntity target) {
        RandomSource random = level.getRandom();
        EntityType<? extends Mob> type = MISFORTUNE_MOBS.get(
                random.nextInt(MISFORTUNE_MOBS.size()));
        Vec3 center = target.position();
        int spawned = 0;
        for (int i = 0; i < 3; i++) {
            double x = center.x
                    + (random.nextDouble() - 0.5D) * 20.0D;
            double z = center.z
                    + (random.nextDouble() - 0.5D) * 20.0D;
            double y = center.y + spawnHeight(type);
            Mob mob = type.create(level);
            if (mob == null) {
                continue;
            }
            mob.moveTo(
                    x,
                    y,
                    z,
                    random.nextFloat() * 360.0F,
                    0.0F);
            if (mob instanceof Phantom && !level.isNight()) {
                continue;
            }
            if (mob instanceof Bee bee && random.nextBoolean()) {
                bee.setBaby(true);
            }
            mob.getPersistentData().putUUID(
                    SUMMON_OWNER, owner.getUUID());
            mob.addEffect(new MobEffectInstance(
                    TicketToRideAddon.TEARS,
                    MobEffectInstance.INFINITE_DURATION,
                    0,
                    false,
                    false,
                    true));
            if (level.addFreshEntity(mob)) {
                spawned++;
            }
            level.playSound(
                    null,
                    x,
                    y,
                    z,
                    SoundEvents.EVOKER_CAST_SPELL,
                    mob.getSoundSource(),
                    1.0F,
                    0.8F + random.nextFloat() * 0.4F);
        }
        return spawned;
    }

    // 1.16 calculateSpawnPos
    private static double spawnHeight(EntityType<?> type) {
        if (type == EntityType.PHANTOM) {
            return 10.0D;
        }
        return type == EntityType.SILVERFISH
                        || type == EntityType.CAVE_SPIDER
                        || type == EntityType.WOLF
                ? 0.1D
                : 5.0D;
    }

    // 1.16 MobBehaviorHandler.modifyMobAI, for a mob that joins the level
    // with TEARS. clearAllGoals only took getRunningGoals, and no target
    // goal is running yet when a mob joins, so the vanilla ones stay.
    public static void modifyMisfortuneAI(Mob mob) {
        mob.targetSelector.getAvailableGoals().stream()
                .filter(WrappedGoal::isRunning)
                .map(WrappedGoal::getGoal)
                .toList()
                .forEach(mob.targetSelector::removeGoal);
        if (mob instanceof Monster) {
            mob.targetSelector.addGoal(
                    2,
                    new NearestAttackableTargetGoal<>(
                            mob,
                            LivingEntity.class,
                            10,
                            true,
                            false,
                            TearsWoundService::isMarked));
        }
        mob.targetSelector.addGoal(
                3,
                new NearestAttackableTargetGoal<>(
                        mob,
                        LivingEntity.class,
                        10,
                        false,
                        false,
                        entity -> entity.getType()
                                != TicketToRideAddon
                                        .TICKET_TO_RIDE_ENTITY.get()));
    }

    // 1.16 MobBehaviorHandler.onEntitySpawn: every mob that joins the level
    // with TEARS (summoned, given it by a command, loaded from disk or
    // changing dimension) starts a fresh 200-tick lifetime; one that joins
    // without TEARS has none, even if it gets TEARS later. 1.16 kept the
    // count in memory only, so a saved count never outlives a join.
    public static void startMisfortuneLifetime(Mob mob) {
        if (mob.hasEffect(TicketToRideAddon.TEARS)) {
            mob.getPersistentData().putInt(
                    MISFORTUNE_LIFETIME, MAX_LIFETIME);
        }
        else {
            mob.getPersistentData().remove(MISFORTUNE_LIFETIME);
        }
    }

    public static void tickMob(Mob mob) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        // 1.16 TearsEffectEventHandler.onMobUpdate: every mob, every tick.
        LivingEntity marked = nearestMarked(mob, 20.0D);
        if (marked != null) {
            mob.setTarget(marked);
        }
        tickMisfortuneLifetime(mob);
    }

    // 1.16 MobBehaviorHandler.onLivingUpdate: while it has TEARS the mob
    // counts its lifetime down and is removed at 0; every 40 ticks it starts
    // again if the mob sees a marked entity within 20 blocks.
    private static boolean tickMisfortuneLifetime(Mob mob) {
        CompoundTag data = mob.getPersistentData();
        if (!mob.hasEffect(TicketToRideAddon.TEARS)
                || !data.contains(MISFORTUNE_LIFETIME)) {
            return false;
        }
        int remaining = data.getInt(MISFORTUNE_LIFETIME) - 1;
        data.putInt(MISFORTUNE_LIFETIME, remaining);
        if (remaining <= 0) {
            mob.discard();
            data.remove(MISFORTUNE_LIFETIME);
            return true;
        }
        LivingEntity seen;
        if (mob.tickCount % 40 == 0
                && (seen = findMarkedTarget(mob, 20.0D)) != null) {
            data.putInt(MISFORTUNE_LIFETIME, MAX_LIFETIME);
            // 1.16 applyPassiveEffects: of the PASSIVE_MOBS only a slime acted.
            if (PASSIVE_MOBS.contains(mob.getType())
                    && mob instanceof Slime) {
                List<MobEffectInstance> effects = new ArrayList<>(
                        TicketToRideEvents.TEARS_MOB_EFFECTS);
                Util.shuffle(effects, mob.getRandom());
                seen.addEffect(new MobEffectInstance(effects.get(0)));
            }
        }
        return false;
    }

    @Nullable
    private static LivingEntity nearestMarked(
            LivingEntity source, double radius) {
        if (!TearsWoundService.anyMarked(source.level())) {
            return null;
        }
        markedSearches++;
        AABB area = source.getBoundingBox().inflate(radius);
        return source.level()
                .getEntitiesOfClass(
                        LivingEntity.class,
                        area,
                        entity -> entity != source
                                && entity.isAlive()
                                && TearsWoundService
                                        .isMarked(entity))
                .stream()
                .min(Comparator.comparingDouble(
                        source::distanceToSqr))
                .orElse(null);
    }

    // 1.16 MobBehaviorHandler.findMarkedTarget: the first marked entity in
    // sight, the mob itself not left out.
    @Nullable
    private static LivingEntity findMarkedTarget(Mob mob, double radius) {
        if (!TearsWoundService.anyMarked(mob.level())) {
            return null;
        }
        markedSearches++;
        return mob.level()
                .getEntitiesOfClass(
                        LivingEntity.class,
                        mob.getBoundingBox().inflate(radius),
                        entity -> TearsWoundService.isMarked(entity)
                                && mob.hasLineOfSight(entity))
                .stream()
                .findFirst()
                .orElse(null);
    }

    // 1.16 StandAggroHandler.onLivingSetAttackTarget: a target that is a
    // player with Ticket to Ride, who has not hit the mob and was not last
    // hit by it, is dropped, except by an aggressive piglin, a zombified
    // piglin the player hurt, an angry wolf or a bee.
    public static boolean dropsTicketUserTarget(Mob mob, Player player) {
        return TicketToRideAddon.isTicketToRideUser(player)
                && mob.getLastHurtByMob() != player
                && player.getLastHurtMob() != mob
                && shouldCancelAggro(mob, player);
    }

    // 1.16 StandAggroHandler.shouldCancelAggro
    private static boolean shouldCancelAggro(Mob mob, Player player) {
        if (mob instanceof Piglin piglin) {
            return !piglin.isAggressive();
        }
        if (mob instanceof ZombifiedPiglin zombifiedPiglin) {
            return zombifiedPiglin.getLastHurtByMob() != player;
        }
        if (mob instanceof Wolf wolf) {
            return !wolf.isAngry();
        }
        // 1.16 read the bee's target after setTarget had already made it
        // the player, so a bee was never stopped.
        return !(mob instanceof Bee);
    }

    // 1.16 StandAggroHandler.cancelGroupAggro: piglins or zombified piglins
    // within 24 blocks, wolves within 16 and bees within 20 that target the
    // player drop it too.
    public static void cancelGroupAggro(Mob source, Player player) {
        Class<? extends Mob> group = null;
        double range = 16.0D;
        if (source instanceof Piglin || source instanceof ZombifiedPiglin) {
            group = source instanceof Piglin
                    ? Piglin.class
                    : ZombifiedPiglin.class;
            range = 24.0D;
        }
        else if (source instanceof Wolf) {
            group = Wolf.class;
        }
        else if (source instanceof Bee) {
            group = Bee.class;
            range = 20.0D;
        }
        if (group == null) {
            return;
        }
        source.level()
                .getEntitiesOfClass(
                        group,
                        source.getBoundingBox().inflate(range),
                        mob -> mob != source && mob.getTarget() == player)
                .forEach(mob -> mob.setTarget(null));
    }

    public static boolean isMisfortuneSummon(
            LivingEntity entity) {
        return entity.getPersistentData().hasUUID(SUMMON_OWNER);
    }

    @Nullable
    public static UUID summonOwner(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        return data.hasUUID(SUMMON_OWNER)
                ? data.getUUID(SUMMON_OWNER)
                : null;
    }
}
