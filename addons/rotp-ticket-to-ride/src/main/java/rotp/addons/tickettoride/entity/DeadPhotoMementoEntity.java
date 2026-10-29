package rotp.addons.tickettoride.entity;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.tickettoride.TicketToRideAddon;
import rotp.addons.tickettoride.state.TearsWoundService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class DeadPhotoMementoEntity
        extends PathfinderMob {
    public static final int ORPHAN_GRACE_TICKS = 100;
    public static final int MAX_LIFETIME_TICKS = 600;
    public static final float EXPLOSION_RADIUS = 9.0F;
    public static final float IMPACT_EXPLOSION_RADIUS = 6.0F;

    private static final EntityDataAccessor<Optional<UUID>>
            OWNER_UUID = SynchedEntityData.defineId(
                    DeadPhotoMementoEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>>
            CONTROLLER_UUID = SynchedEntityData.defineId(
                    DeadPhotoMementoEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer>
            CONTROLLER_ID = SynchedEntityData.defineId(
                    DeadPhotoMementoEntity.class,
                    EntityDataSerializers.INT);

    private int orphanTicks;

    public DeadPhotoMementoEntity(
            EntityType<? extends DeadPhotoMementoEntity> type,
            Level level) {
        super(type, level);
        xpReward = 0;
        setNoGravity(true);
        noPhysics = false;
        // 1.16 ignoreFrustumCheck: the rock is drawn far off its small box
        noCulling = true;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 999.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER_UUID, Optional.empty());
        builder.define(CONTROLLER_UUID, Optional.empty());
        builder.define(CONTROLLER_ID, -1);
    }

    public void initialize(
            LivingEntity owner,
            TicketToRideStandEntity controller) {
        if (level().isClientSide()) {
            return;
        }
        entityData.set(
                OWNER_UUID, Optional.of(owner.getUUID()));
        entityData.set(
                CONTROLLER_UUID,
                Optional.of(controller.getUUID()));
        entityData.set(CONTROLLER_ID, controller.getId());
        orphanTicks = 0;
    }

    @Nullable
    public TicketToRideStandEntity resolveController() {
        Entity byId = level().getEntity(
                entityData.get(CONTROLLER_ID));
        if (byId instanceof TicketToRideStandEntity controller
                && controller.isAlive()) {
            return controller;
        }
        Optional<UUID> uuid = entityData.get(CONTROLLER_UUID);
        if (uuid.isEmpty()) {
            return null;
        }
        if (level() instanceof ServerLevel server
                && server.getEntity(uuid.get())
                        instanceof TicketToRideStandEntity controller) {
            entityData.set(CONTROLLER_ID, controller.getId());
            return controller;
        }
        for (TicketToRideStandEntity controller
                : level().getEntitiesOfClass(
                        TicketToRideStandEntity.class,
                        getBoundingBox().inflate(256.0D),
                        entity -> uuid.get().equals(
                                entity.getUUID()))) {
            entityData.set(CONTROLLER_ID, controller.getId());
            return controller;
        }
        return null;
    }

    @Nullable
    public LivingEntity resolveOwner() {
        TicketToRideStandEntity controller =
                resolveController();
        if (controller != null && controller.getUser() != null) {
            return controller.getUser();
        }
        Optional<UUID> uuid = entityData.get(OWNER_UUID);
        return uuid.isPresent()
                && level() instanceof ServerLevel server
                && server.getEntity(uuid.get())
                        instanceof LivingEntity living
                ? living
                : null;
    }

    // 1.16 ran ProjectileAndEntityEventHandler.onLivingUpdate (homing and the
    // shockwave) from the LivingUpdateEvent before the move, then
    // Deadphotomemtor.tick after it (the collision blast and the fire tail).
    @Override
    public void tick() {
        setNoGravity(true);
        if (level() instanceof ServerLevel server) {
            if (tickCount > MAX_LIFETIME_TICKS
                    || !validClosure()) {
                if (++orphanTicks >= ORPHAN_GRACE_TICKS
                        || tickCount > MAX_LIFETIME_TICKS) {
                    discard();
                }
                return;
            }
            orphanTicks = 0;
            homeOnMarked();
            if (touchesGroundOrMarked()) {
                explodeWithShockwave(server);
                return;
            }
        }
        else {
            spawnBackFlames();
        }
        super.tick();
        hurtTime = 0;
        hurtDuration = 0;
        if (level() instanceof ServerLevel server) {
            if (isAlive()) {
                if (onGround() || horizontalCollision
                        || verticalCollision) {
                    explodeOnImpact(server);
                }
                spawnFireTail(server);
            }
        }
        else {
            spawnClientFlames();
        }
    }

    // 1.16 findAndMoveToTarget: towards the eyes of the nearest marked entity
    // in the box of its block grown by 50, else slowly down.
    private void homeOnMarked() {
        LivingEntity target = nearestMarked();
        if (target != null) {
            Vec3 direction = target.getEyePosition()
                    .subtract(position())
                    .normalize();
            setDeltaMovement(
                    direction.x * 1.2D,
                    -0.8D,
                    direction.z * 1.2D);
            setYRot((float) Math.toDegrees(
                    Math.atan2(direction.x, direction.z))
                    + 180.0F);
            setXRot((float) -Math.toDegrees(
                    Math.atan2(
                            direction.y,
                            Math.sqrt(
                                    direction.x * direction.x
                                            + direction.z
                                                    * direction.z))));
        }
        else {
            setDeltaMovement(
                    (getRandom().nextDouble() - 0.5D) * 0.1D,
                    -0.5D,
                    (getRandom().nextDouble() - 0.5D) * 0.1D);
        }
    }

    // 1.16 shouldExplode: a non-air block one below, or a marked entity
    // touching the box grown by 1.1.
    private boolean touchesGroundOrMarked() {
        if (!level().getBlockState(BlockPos.containing(
                getX(), getY() - 1.0D, getZ())).isAir()) {
            return true;
        }
        return !level().getEntitiesOfClass(
                LivingEntity.class,
                getBoundingBox().inflate(1.1D),
                target -> TearsWoundService.isMarked(target)
                        && target != this
                        && target.isAlive()).isEmpty();
    }

    private boolean validClosure() {
        LivingEntity owner = resolveOwner();
        TicketToRideStandEntity controller =
                resolveController();
        if (owner == null
                || controller == null
                || !owner.isAlive()
                || controller.getUser() != owner) {
            return false;
        }
        StandPower power = StandPower.get(owner);
        return power != null
                && power.getPowerType()
                        == TicketToRideAddon.TICKET_TO_RIDE.get()
                && power.getSummonedStandEntity() == controller;
    }

    @Nullable
    private LivingEntity nearestMarked() {
        return level().getEntitiesOfClass(
                        LivingEntity.class,
                        new AABB(blockPosition()).inflate(50.0D),
                        target -> target != this
                                && target.isAlive()
                                && TearsWoundService
                                        .isMarked(target))
                .stream()
                .min(Comparator.comparingDouble(
                        this::distanceToSqr))
                .orElse(null);
    }

    // 1.16 explodeWithShockwave: a ring of flames and campfire smoke at 6,
    // explosions in the middle and a column of flames, then a 9 BREAK blast
    // (whatever mobGriefing says, as TNT) and bleeding for the marked within 6.
    private void explodeWithShockwave(ServerLevel server) {
        if (!isAlive()) {
            return;
        }
        Vec3 center = position();
        for (int i = 0; i < 360; i += 10) {
            double angle = Math.toRadians(i);
            double x = center.x + Math.cos(angle) * 6.0D;
            double z = center.z + Math.sin(angle) * 6.0D;
            server.sendParticles(ParticleTypes.FLAME,
                    x, center.y + 1.0D, z, 1, 0.0D, 0.1D, 0.0D, 0.05D);
            server.sendParticles(ParticleTypes.EXPLOSION,
                    center.x, center.y + 1.0D, center.z,
                    5, 1.0D, 1.0D, 1.0D, 0.1D);
            server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    x, center.y + 1.5D, z, 5, 0.0D, 0.6D, 0.0D, 0.02D);
        }
        for (int y = 0; y <= 10; y++) {
            server.sendParticles(ParticleTypes.FLAME,
                    center.x, center.y + y * 0.5D, center.z,
                    6, 2.0D, 0.0D, 1.0D, 0.1D);
        }
        server.explode(
                this,
                getX(),
                getY(),
                getZ(),
                EXPLOSION_RADIUS,
                false,
                Level.ExplosionInteraction.TNT);
        for (Entity nearby : server.getEntities(
                this, getBoundingBox().inflate(6.0D))) {
            if (nearby instanceof LivingEntity target
                    && TearsWoundService.isMarked(target)) {
                target.addEffect(new MobEffectInstance(
                        ModStatusEffects.BLEEDING,
                        200,
                        0));
            }
        }
        discard();
    }

    // 1.16 Deadphotomemtor.explodeD: on hitting the ground or a wall, a 6
    // blast that breaks blocks only with mobGriefing, and its particles.
    private void explodeOnImpact(ServerLevel server) {
        Level.ExplosionInteraction interaction =
                server.getGameRules().getBoolean(
                        GameRules.RULE_MOBGRIEFING)
                        ? Level.ExplosionInteraction.MOB
                        : Level.ExplosionInteraction.NONE;
        server.explode(
                this,
                getX(),
                getY(),
                getZ(),
                IMPACT_EXPLOSION_RADIUS,
                interaction);
        server.sendParticles(ParticleTypes.FLAME,
                getX(), getY(), getZ(), 200, 3.0D, 3.0D, 3.0D, 0.1D);
        server.sendParticles(ParticleTypes.SMOKE,
                getX(), getY(), getZ(), 100, 3.0D, 3.0D, 3.0D, 0.1D);
        server.sendParticles(ParticleTypes.LAVA,
                getX(), getY(), getZ(), 50, 2.0D, 2.0D, 2.0D, 0.05D);
        discard();
    }

    // 1.16 spawnFireTail: four points around the middle, each flames and
    // smoke, every tick.
    private void spawnFireTail(ServerLevel server) {
        double y = getY() + getBbHeight() * 0.5D;
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                double px = getX() + dx * 0.5D - 0.25D;
                double pz = getZ() + dz * 0.5D - 0.25D;
                server.sendParticles(ParticleTypes.FLAME,
                        px, y, pz, 2, 0.1D, 0.1D, 0.1D, 0.05D);
                server.sendParticles(ParticleTypes.SMOKE,
                        px, y, pz, 1, 0.05D, 0.05D, 0.05D, 0.02D);
            }
        }
    }

    // 1.16 Deadphotomemtor.spawnClientFireParticles
    private void spawnClientFlames() {
        if (random.nextFloat() < 0.3F) {
            for (int i = 0; i < 2; i++) {
                double ox = (random.nextDouble() - 0.5D) * 0.5D;
                double oy = random.nextDouble() * getBbHeight();
                double oz = (random.nextDouble() - 0.5D) * 0.5D;
                level().addParticle(ParticleTypes.FLAME,
                        getX() + ox, getY() + oy, getZ() + oz,
                        0.0D, 0.0D, 0.0D);
            }
        }
    }

    // 1.16 ProjectileAndEntityEventHandler.spawn3x3FireParticles: flames
    // shot up from a point 0.9 behind the rock, smoke every third tick.
    private void spawnBackFlames() {
        double yaw = Math.toRadians(getYRot() + 180.0F);
        double pitch = Math.toRadians(getXRot());
        Vec3 back = new Vec3(
                getX() + Math.sin(yaw) * -0.9D * Math.cos(pitch),
                getY() + Math.sin(pitch) * -0.9D,
                getZ() + Math.cos(yaw) * -0.9D * Math.cos(pitch));
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                double ox = x * 0.3D;
                double oz = z * 0.3D;
                level().addParticle(ParticleTypes.FLAME,
                        back.x + ox, back.y, back.z + oz,
                        (random.nextDouble() - 0.5D) * 0.1D,
                        3.1D,
                        (random.nextDouble() - 0.5D) * 0.1D);
                if (tickCount % 3 == 0) {
                    level().addParticle(ParticleTypes.SMOKE,
                            back.x + ox, back.y + 0.2D, back.z + oz,
                            0.0D, 0.05D, 0.0D);
                }
            }
        }
        level().addParticle(ParticleTypes.FLAME,
                back.x, back.y + 3.3D, back.z,
                (random.nextDouble() - 0.5D) * 0.2D,
                0.2D,
                (random.nextDouble() - 0.5D) * 0.2D);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public void addAdditionalSaveData(
            CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        entityData.get(OWNER_UUID)
                .ifPresent(uuid -> tag.putUUID("Owner", uuid));
        entityData.get(CONTROLLER_UUID)
                .ifPresent(uuid ->
                        tag.putUUID("Controller", uuid));
        tag.putInt("OrphanTicks", orphanTicks);
    }

    @Override
    public void readAdditionalSaveData(
            CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) {
            entityData.set(
                    OWNER_UUID,
                    Optional.of(tag.getUUID("Owner")));
        }
        if (tag.hasUUID("Controller")) {
            entityData.set(
                    CONTROLLER_UUID,
                    Optional.of(tag.getUUID("Controller")));
        }
        entityData.set(CONTROLLER_ID, -1);
        orphanTicks = Math.max(
                0, tag.getInt("OrphanTicks"));
    }
}
