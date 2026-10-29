package rotp.addons.d4c.entity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.addons.d4c.D4CAddon;
import rotp.addons.d4c.D4CPhase2BSpec;
import rotp.addons.d4c.D4CRuntimeRules;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class D4CAnnihilationCloneEntity extends PathfinderMob {
    private static final EntityDataAccessor<Optional<UUID>> OWNER =
            SynchedEntityData.defineId(
                    D4CAnnihilationCloneEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> TARGET =
            SynchedEntityData.defineId(
                    D4CAnnihilationCloneEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> TARGET_ENTITY_ID =
            SynchedEntityData.defineId(
                    D4CAnnihilationCloneEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE =
            SynchedEntityData.defineId(
                    D4CAnnihilationCloneEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ANNIHILATION =
            SynchedEntityData.defineId(
                    D4CAnnihilationCloneEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> PROFILE_NAME =
            SynchedEntityData.defineId(
                    D4CAnnihilationCloneEntity.class,
                    EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Optional<UUID>> PROFILE_ID =
            SynchedEntityData.defineId(
                    D4CAnnihilationCloneEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> PLAYER_CLONE =
            SynchedEntityData.defineId(
                    D4CAnnihilationCloneEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> CURRENTLY_COLLIDING =
            SynchedEntityData.defineId(
                    D4CAnnihilationCloneEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> TARGET_WIDTH =
            SynchedEntityData.defineId(
                    D4CAnnihilationCloneEntity.class,
                    EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TARGET_HEIGHT =
            SynchedEntityData.defineId(
                    D4CAnnihilationCloneEntity.class,
                    EntityDataSerializers.FLOAT);

    private boolean playerAttractionStarted;
    private double playerAttractionStartY;
    private float targetWidth;
    private float targetHeight;

    public D4CAnnihilationCloneEntity(
            EntityType<? extends D4CAnnihilationCloneEntity> type,
            Level level) {
        super(type, level);
        xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 64.0D);
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER, Optional.empty());
        builder.define(TARGET, Optional.empty());
        builder.define(TARGET_ENTITY_ID, -1);
        builder.define(LIFE, 0);
        builder.define(ANNIHILATION, -1);
        builder.define(PROFILE_NAME, "");
        builder.define(PROFILE_ID, Optional.empty());
        builder.define(PLAYER_CLONE, false);
        builder.define(CURRENTLY_COLLIDING, false);
        builder.define(TARGET_WIDTH, 0.6F);
        builder.define(TARGET_HEIGHT, 1.8F);
    }

    @Override
    protected void registerGoals() {}

    public void initialize(LivingEntity owner, LivingEntity target) {
        setPersistenceRequired();
        entityData.set(OWNER, Optional.of(owner.getUUID()));
        entityData.set(TARGET, Optional.of(target.getUUID()));
        entityData.set(TARGET_ENTITY_ID, target.getId());
        entityData.set(
                PROFILE_NAME,
                target instanceof ServerPlayer player
                        ? player.getGameProfile().getName()
                        : target.getName().getString());
        entityData.set(
                PROFILE_ID,
                target instanceof net.minecraft.world.entity.player.Player
                        ? Optional.of(target.getUUID())
                        : Optional.empty());
        entityData.set(
                PLAYER_CLONE,
                target instanceof net.minecraft.world.entity.player.Player);
        targetWidth = target.getBbWidth();
        targetHeight = target.getBbHeight();
        entityData.set(TARGET_WIDTH, targetWidth);
        entityData.set(TARGET_HEIGHT, targetHeight);
        refreshDimensions();
        setCustomName(target.getDisplayName().copy());
        setCustomNameVisible(isPlayerClone()
                || target.isCustomNameVisible());
        setLeftHanded(target.getMainArm()
                == net.minecraft.world.entity.HumanoidArm.LEFT);
        setHealth(Math.min(getMaxHealth(), target.getHealth()));
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        if (targetWidth > 0.0F && targetHeight > 0.0F) {
            return EntityDimensions.scalable(
                    targetWidth, targetHeight);
        }
        return super.getDefaultDimensions(pose);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (TARGET_WIDTH.equals(key)) {
            targetWidth = entityData.get(TARGET_WIDTH);
        } else if (TARGET_HEIGHT.equals(key)) {
            targetHeight = entityData.get(TARGET_HEIGHT);
        } else {
            return;
        }
        if (targetWidth > 0.0F && targetHeight > 0.0F) {
            refreshDimensions();
        }
    }

    public boolean targets(UUID target) {
        return entityData.get(TARGET).filter(target::equals).isPresent();
    }

    public int targetEntityId() {
        return entityData.get(TARGET_ENTITY_ID);
    }

    public UUID profileId() {
        return entityData.get(PROFILE_ID).orElse(null);
    }

    public String profileName() {
        return entityData.get(PROFILE_NAME);
    }

    public boolean isPlayerClone() {
        return entityData.get(PLAYER_CLONE);
    }

    public boolean isCurrentlyColliding() {
        return entityData.get(CURRENTLY_COLLIDING);
    }

    public int annihilationTicks() {
        return entityData.get(ANNIHILATION);
    }

    public int lifeTicks() {
        return entityData.get(LIFE);
    }

    public static boolean shouldBeginAnnihilation(
            net.minecraft.world.phys.AABB cloneBox,
            net.minecraft.world.phys.AABB targetBox,
            double distance,
            double threshold) {
        return D4CRuntimeRules.shouldBeginAnnihilation(
                cloneBox.intersects(targetBox),
                distance,
                threshold);
    }

    public boolean tryStartAnnihilation(
            ServerLevel level, LivingEntity target) {
        double threshold = isPlayerClone() ? 1.5D : 0.3D;
        boolean colliding = shouldBeginAnnihilation(
                getBoundingBox(),
                target.getBoundingBox(),
                distanceTo(target),
                threshold);
        entityData.set(CURRENTLY_COLLIDING, colliding);
        if (!colliding || entityData.get(ANNIHILATION) >= 0) {
            return false;
        }
        entityData.set(ANNIHILATION, 0);
        getNavigation().stop();
        applyContactLock(target);
        // 1.16 ag/ab.java:214-232: contact deals no damage; first hit at tick 20
        // 1.16 plays d4c_annihilate 10 ticks later, not on contact
        startBurst(level);
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        // a dying clone has already fired its final blow
        if (!(level() instanceof ServerLevel level) || isDeadOrDying()) {
            return;
        }
        int life = entityData.get(LIFE) + 1;
        entityData.set(LIFE, life);
        LivingEntity target = resolveLiving(level, entityData.get(TARGET));
        if (target != null
                && entityData.get(TARGET_ENTITY_ID) != target.getId()) {
            entityData.set(TARGET_ENTITY_ID, target.getId());
        }
        // 1.16 ag/ab.java:125-187: only the target or lifespan ends it
        if (target == null
                || !target.isAlive()
                || life > D4CPhase2BSpec.CLONE_LIFESPAN_TICKS) {
            if (D4CAnnihilationCloneEffects.vanishesWithPoof(
                    isPlayerClone())) {
                vanishEffect(level);
            } else if (D4CAnnihilationCloneEffects.burstsOnLostTarget(
                    false,
                    entityData.get(ANNIHILATION),
                    target != null,
                    target != null && target.isAlive())) {
                deathBurst(level, target);
            }
            discard();
            return;
        }

        int annihilation = entityData.get(ANNIHILATION);
        if (annihilation < 0) {
            if (isPlayerClone()) {
                movePlayerCloneToward(target);
            } else {
                moveNonPlayerCloneToward(target);
            }
            getLookControl().setLookAt(target, 30.0F, 30.0F);
            tryStartAnnihilation(level, target);
            return;
        }

        if (isPlayerClone()) {
            movePlayerCloneToward(target);
        } else {
            moveNonPlayerCloneToward(target);
        }
        annihilation++;
        entityData.set(ANNIHILATION, annihilation);
        if (annihilation == D4CAnnihilationCloneEffects.MINION_SLOW_TICK) {
            slowMinions(level, target);
        }
        if (annihilation
                == D4CAnnihilationCloneEffects.ANNIHILATE_SOUND_TICK) {
            level.playSound(
                    null,
                    target.getX(),
                    target.getY(),
                    target.getZ(),
                    D4CAddon.D4C_ANNIHILATE.get(),
                    target.getSoundSource(),
                    D4CAnnihilationCloneEffects.annihilateVolume(
                            isPlayerClone()),
                    1.0F);
        }
        double threshold = isPlayerClone() ? 1.5D : 0.3D;
        boolean colliding = shouldBeginAnnihilation(
                getBoundingBox(),
                target.getBoundingBox(),
                distanceTo(target),
                threshold);
        entityData.set(CURRENTLY_COLLIDING, colliding);
        if (D4CRuntimeRules.clonePeriodicHitInContact(
                isPlayerClone(), colliding)) {
            applyContactLock(target);
            if (annihilation <= 100 && annihilation % 20 == 0) {
                hurtPair(target, 0.025F);
                shareWithMinions(level, target, 0.025F, true);
            } else if (annihilation > 100 && annihilation % 4 == 0) {
                hurtPair(target, 0.0025F);
                shareWithMinions(level, target, 0.0025F, false);
            }
        }
        if (annihilation % 10 == 0) {
            level.sendParticles(
                    D4CAddon.ANNIHILATE.get(),
                    target.getX(),
                    target.getY(0.5D),
                    target.getZ(),
                    12,
                    target.getBbWidth() * 0.6D,
                    target.getBbHeight() * 0.5D,
                    target.getBbWidth() * 0.6D,
                    0.02D);
        }
        // 1.16 ag/ab.java:368-378: the final blow needs no contact
        if (annihilation >= 160) {
            finalBurst(level, target);
            finalAnnihilation(target);
            discard();
        }
    }

    // 1.16 ag/ab.java:461-476
    private static void finalAnnihilation(LivingEntity target) {
        if (target.getMaxHealth() <= 50.0F
                || target.getHealth() <= 50.0F) {
            target.kill();
        } else {
            // 1.16 final blow is true damage
            target.hurt(
                    target.damageSources().fellOutOfWorld(),
                    target.getHealth() * 0.5F);
        }
    }

    // 1.16 ag/ab.java:419-432: only magic hurts the clone
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean adminKill = source.is(DamageTypes.GENERIC_KILL)
                || source.is(DamageTypes.FELL_OUT_OF_WORLD)
                        && getY() < level().getMinBuildHeight() - 64;
        if (!D4CRuntimeRules.cloneAcceptsDamage(
                source.is(DamageTypes.MAGIC), adminKill)) {
            return false;
        }
        if (D4CRuntimeRules.cloneHoldsAtOneHealth(
                adminKill,
                entityData.get(ANNIHILATION),
                getHealth(),
                amount)) {
            setHealth(1.0F);
            return false;
        }
        return super.hurt(source, amount);
    }

    // 1.16 ag/ab.java:434-443: dying after contact still annihilates
    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel level
                && !dead
                && !isRemoved()) {
            LivingEntity target = resolveLiving(
                    level, entityData.get(TARGET));
            if (D4CRuntimeRules.cloneDeathFiresFinal(
                    entityData.get(ANNIHILATION),
                    target != null && target.isAlive())) {
                // 1.16 ag/ab.java:445-455 burst before the blow
                deathBurst(level, target);
                finalAnnihilation(target);
            }
        }
        super.die(source);
    }

    private void applyContactLock(LivingEntity target) {
        target.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN,
                10,
                79,
                false,
                false,
                true));
        addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN,
                10,
                79,
                false,
                false,
                true));
    }

    private void movePlayerCloneToward(LivingEntity target) {
        double distance = distanceTo(target);
        boolean flightEnabled = distance <= 8.0D;
        if (flightEnabled && !playerAttractionStarted) {
            playerAttractionStarted = true;
            playerAttractionStartY = getY();
        }
        setNoGravity(flightEnabled);
        noPhysics = flightEnabled;
        if (!playerAttractionStarted) {
            getNavigation().moveTo(target, 1.2D);
            return;
        }
        getNavigation().stop();
        Vec3 difference = target.position().subtract(position());
        if (difference.lengthSqr() <= 0.0001D) {
            return;
        }
        double speed = distance > 0.5D ? 0.3D : 0.1D;
        Vec3 step = difference.normalize().scale(speed);
        if (flightEnabled
                && getY() < target.getY()
                && Math.abs(getY() - playerAttractionStartY) < 3.0D) {
            step = step.add(0.0D, 0.1D, 0.0D);
        }
        setPos(getX() + step.x, getY() + step.y, getZ() + step.z);
    }

    private void moveNonPlayerCloneToward(LivingEntity target) {
        double distance = distanceTo(target);
        if (shouldBeginAnnihilation(
                getBoundingBox(),
                target.getBoundingBox(),
                distance,
                0.3D)) {
            getNavigation().stop();
            setNoGravity(true);
            noPhysics = false;
            setDeltaMovement(Vec3.ZERO);
            return;
        }

        Vec3 difference = target.position().subtract(position());
        if (distance > 8.0D || difference.lengthSqr() <= 0.0001D) {
            resumeGroundNavigation(target);
            return;
        }

        Vec3 clearPath = Entity.collideBoundingBox(
                this,
                difference,
                getBoundingBox(),
                level(),
                List.of());
        if (clearPath.subtract(difference).lengthSqr() > 0.000001D) {
            resumeGroundNavigation(target);
            return;
        }

        getNavigation().stop();
        setNoGravity(true);
        noPhysics = false;
        setDeltaMovement(Vec3.ZERO);
        Vec3 requestedStep = difference.normalize()
                .scale(Math.min(distance, 0.2D));
        Vec3 clippedStep = Entity.collideBoundingBox(
                this,
                requestedStep,
                getBoundingBox(),
                level(),
                List.of());
        setPos(
                getX() + clippedStep.x,
                getY() + clippedStep.y,
                getZ() + clippedStep.z);
    }

    private void resumeGroundNavigation(LivingEntity target) {
        setNoGravity(false);
        noPhysics = false;
        getNavigation().moveTo(target, 1.2D);
    }

    // 1.16 true damage (OUT_OF_WORLD); the clone rejected its own share
    private void hurtPair(LivingEntity target, float ratio) {
        target.hurt(
                target.damageSources().fellOutOfWorld(),
                target.getMaxHealth() * ratio);
    }

    // 1.16 ag/ab.java:244-263 and ah/aa.java:177-229 summoned players
    private List<D4CMinionEntity> nearbyMinions(
            ServerLevel level, LivingEntity target) {
        UUID owner = entityData.get(OWNER).orElse(null);
        boolean playerClone = isPlayerClone();
        return level.getEntitiesOfClass(
                D4CMinionEntity.class,
                target.getBoundingBox().inflate(
                        D4CAnnihilationCloneEffects.MINION_RANGE),
                minion -> D4CAnnihilationCloneEffects.minionShares(
                        playerClone, owner, minion.profileId()));
    }

    private void slowMinions(ServerLevel level, LivingEntity target) {
        for (D4CMinionEntity minion : nearbyMinions(level, target)) {
            minion.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN,
                    D4CAnnihilationCloneEffects.MINION_SLOW_DURATION,
                    D4CAnnihilationCloneEffects.MINION_SLOW_AMPLIFIER,
                    false,
                    true,
                    true));
        }
    }

    private void shareWithMinions(
            ServerLevel level,
            LivingEntity target,
            float ratio,
            boolean earlyPhase) {
        for (D4CMinionEntity minion : nearbyMinions(level, target)) {
            float damage = minion.getMaxHealth() * ratio;
            if (D4CAnnihilationCloneEffects.minionShareHoldsAtOne(
                    isPlayerClone(),
                    earlyPhase,
                    minion.getHealth(),
                    damage)) {
                minion.setHealth(1.0F);
            } else {
                minion.hurt(
                        minion.damageSources().fellOutOfWorld(),
                        damage);
            }
        }
    }

    // 1.16 ag/ab.java:224-230 and ah/aa.java:151-157
    private void startBurst(ServerLevel level) {
        for (int i = 0;
                i < D4CAnnihilationCloneEffects.START_PARTICLES;
                i++) {
            level.sendParticles(
                    ParticleTypes.PORTAL,
                    getX() + (random.nextDouble() - 0.5D) * 3.0D,
                    getY() + random.nextDouble() * 3.0D,
                    getZ() + (random.nextDouble() - 0.5D) * 3.0D,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        level.playSound(
                null,
                getX(),
                getY(),
                getZ(),
                SoundEvents.BEACON_ACTIVATE,
                getSoundSource(),
                D4CAnnihilationCloneEffects.startVolume(isPlayerClone()),
                0.8F);
    }

    // 1.16 ag/ab.java:382-393: poof and d4c_teleport
    private void vanishEffect(ServerLevel level) {
        for (int i = 0;
                i < D4CAnnihilationCloneEffects.VANISH_PARTICLES;
                i++) {
            level.sendParticles(
                    ParticleTypes.POOF,
                    getX() + (random.nextDouble() - 0.5D) * 2.0D,
                    getY() + random.nextDouble() * 2.0D,
                    getZ() + (random.nextDouble() - 0.5D) * 2.0D,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        level.playSound(
                null,
                getX(),
                getY(),
                getZ(),
                D4CAddon.D4C_TELEPORT.get(),
                getSoundSource(),
                1.0F,
                0.8F);
    }

    // 1.16 ag/ab.java:368-375 and ah/aa.java:352-359
    private void finalBurst(ServerLevel level, LivingEntity target) {
        AABB box = target.getBoundingBox();
        portalBurst(
                level,
                target,
                D4CAnnihilationCloneEffects.finalBurstCount(
                        box.getXsize(), box.getZsize(), box.getYsize()),
                D4CAnnihilationCloneEffects.finalSpreadXZ(
                        box.getXsize(), box.getZsize()),
                D4CAnnihilationCloneEffects.finalSpreadY(box.getYsize()),
                D4CAnnihilationCloneEffects.finalExplodeVolume(
                        isPlayerClone()));
    }

    // 1.16 ag/ab.java:445-455 and ah/aa.java:366-376
    private void deathBurst(ServerLevel level, LivingEntity target) {
        portalBurst(
                level,
                target,
                D4CAnnihilationCloneEffects.DEATH_BURST_PARTICLES,
                D4CAnnihilationCloneEffects.DEATH_BURST_SPREAD,
                D4CAnnihilationCloneEffects.DEATH_BURST_SPREAD,
                D4CAnnihilationCloneEffects.DEATH_EXPLODE_VOLUME);
    }

    private void portalBurst(
            ServerLevel level,
            LivingEntity target,
            int count,
            double spreadXZ,
            double spreadY,
            float volume) {
        for (int i = 0; i < count; i++) {
            level.sendParticles(
                    ParticleTypes.PORTAL,
                    target.getX() + (random.nextDouble() - 0.5D) * spreadXZ,
                    target.getY() + random.nextDouble() * spreadY,
                    target.getZ() + (random.nextDouble() - 0.5D) * spreadXZ,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        level.playSound(
                null,
                target.getX(),
                target.getY(),
                target.getZ(),
                SoundEvents.GENERIC_EXPLODE,
                getSoundSource(),
                volume,
                1.0F);
    }

    @Nullable
    private static LivingEntity resolveLiving(
            ServerLevel level, Optional<UUID> uuid) {
        if (uuid.isEmpty()) {
            return null;
        }
        Entity entity = level.getEntity(uuid.get());
        if (entity instanceof LivingEntity living) {
            return living;
        }
        return level.getServer().getPlayerList().getPlayer(uuid.get());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        entityData.get(OWNER).ifPresent(uuid -> tag.putUUID("Owner", uuid));
        entityData.get(TARGET).ifPresent(uuid -> tag.putUUID("Target", uuid));
        tag.putInt("Life", entityData.get(LIFE));
        tag.putInt("Annihilation", entityData.get(ANNIHILATION));
        tag.putString("ProfileName", entityData.get(PROFILE_NAME));
        entityData.get(PROFILE_ID)
                .ifPresent(uuid -> tag.putUUID("ProfileId", uuid));
        tag.putBoolean("PlayerClone", entityData.get(PLAYER_CLONE));
        tag.putBoolean(
                "CurrentlyColliding",
                entityData.get(CURRENTLY_COLLIDING));
        tag.putFloat("TargetWidth", entityData.get(TARGET_WIDTH));
        tag.putFloat("TargetHeight", entityData.get(TARGET_HEIGHT));
        tag.putBoolean(
                "PlayerAttractionStarted",
                playerAttractionStarted);
        tag.putDouble(
                "PlayerAttractionStartY",
                playerAttractionStartY);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(
                OWNER,
                tag.hasUUID("Owner")
                        ? Optional.of(tag.getUUID("Owner"))
                        : Optional.empty());
        entityData.set(
                TARGET,
                tag.hasUUID("Target")
                        ? Optional.of(tag.getUUID("Target"))
                        : Optional.empty());
        entityData.set(LIFE, tag.getInt("Life"));
        entityData.set(ANNIHILATION, tag.getInt("Annihilation"));
        entityData.set(
                PROFILE_NAME, tag.getString("ProfileName"));
        entityData.set(
                PROFILE_ID,
                tag.hasUUID("ProfileId")
                        ? Optional.of(tag.getUUID("ProfileId"))
                        : Optional.empty());
        entityData.set(
                PLAYER_CLONE, tag.getBoolean("PlayerClone"));
        entityData.set(
                CURRENTLY_COLLIDING,
                tag.getBoolean("CurrentlyColliding"));
        targetWidth = tag.contains("TargetWidth")
                ? tag.getFloat("TargetWidth")
                : 0.6F;
        targetHeight = tag.contains("TargetHeight")
                ? tag.getFloat("TargetHeight")
                : 1.8F;
        entityData.set(TARGET_WIDTH, targetWidth);
        entityData.set(TARGET_HEIGHT, targetHeight);
        refreshDimensions();
        playerAttractionStarted =
                tag.getBoolean("PlayerAttractionStarted");
        playerAttractionStartY =
                tag.getDouble("PlayerAttractionStartY");
    }
}
