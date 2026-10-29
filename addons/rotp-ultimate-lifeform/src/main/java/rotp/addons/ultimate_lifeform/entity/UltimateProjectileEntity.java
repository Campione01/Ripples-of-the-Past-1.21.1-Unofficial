package rotp.addons.ultimate_lifeform.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.customobjects.DamageSourceModified;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.util.functions.DamageUtil;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;
import rotp.addons.ultimate_lifeform.UltimateLifeformAddon;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

public final class UltimateProjectileEntity
        extends ThrowableItemProjectile
        implements IEntityWithComplexSpawn {
    private static final EntityDataAccessor<Float> DAMAGE =
            SynchedEntityData.defineId(
                    UltimateProjectileEntity.class,
                    EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> HAMON =
            SynchedEntityData.defineId(
                    UltimateProjectileEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> LIFE_TICKS =
            SynchedEntityData.defineId(
                    UltimateProjectileEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> TENTACLE_RIGHT =
            SynchedEntityData.defineId(
                    UltimateProjectileEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> ORIGIN_X =
            SynchedEntityData.defineId(
                    UltimateProjectileEntity.class,
                    EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ORIGIN_Y =
            SynchedEntityData.defineId(
                    UltimateProjectileEntity.class,
                    EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> ATTACHED_ENTITY_ID =
            SynchedEntityData.defineId(
                    UltimateProjectileEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> GUARDIAN_LIFESPAN =
            SynchedEntityData.defineId(
                    UltimateProjectileEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> BEAM_END_X =
            SynchedEntityData.defineId(
                    UltimateProjectileEntity.class,
                    EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BEAM_END_Y =
            SynchedEntityData.defineId(
                    UltimateProjectileEntity.class,
                    EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BEAM_END_Z =
            SynchedEntityData.defineId(
                    UltimateProjectileEntity.class,
                    EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TENTACLE_X_ROT =
            SynchedEntityData.defineId(
                    UltimateProjectileEntity.class,
                    EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TENTACLE_Y_ROT =
            SynchedEntityData.defineId(
                    UltimateProjectileEntity.class,
                    EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> MOVING_FORWARD =
            SynchedEntityData.defineId(
                    UltimateProjectileEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> RETRACTING =
            SynchedEntityData.defineId(
                    UltimateProjectileEntity.class,
                    EntityDataSerializers.BOOLEAN);

    private boolean dealtDamage;
    private int elapsedLifeTicks;
    private double tentacleDistance;
    @Nullable private UUID attachedEntityUuid;
    // 1.16 OwnerBoundProjectileEntity.getEntityAttachedTo kept the entity it
    // found: a tentacle on a mob that died stayed where the body was
    @Nullable private LivingEntity attachedEntity;

    public UltimateProjectileEntity(
            EntityType<? extends UltimateProjectileEntity> type,
            Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DAMAGE, 6.0F);
        builder.define(HAMON, false);
        builder.define(LIFE_TICKS, 100);
        builder.define(TENTACLE_RIGHT, false);
        builder.define(ORIGIN_X, 0.0F);
        builder.define(ORIGIN_Y, 0.0F);
        builder.define(ATTACHED_ENTITY_ID, -1);
        builder.define(GUARDIAN_LIFESPAN, 0);
        builder.define(BEAM_END_X, 0.0F);
        builder.define(BEAM_END_Y, 0.0F);
        builder.define(BEAM_END_Z, 0.0F);
        builder.define(TENTACLE_X_ROT, 0.0F);
        builder.define(TENTACLE_Y_ROT, 0.0F);
        builder.define(MOVING_FORWARD, true);
        builder.define(RETRACTING, false);
    }

    public void initialize(
            LivingEntity owner,
            float damage,
            boolean hamon,
            float speed,
            float inaccuracy) {
        initialize(
                owner,
                damage,
                hamon,
                speed,
                inaccuracy,
                100,
                0.0F,
                0.0F,
                0.0D,
                0.0D,
                0.0D);
    }

    public void initialize(
            LivingEntity owner,
            float damage,
            boolean hamon,
            float speed,
            float inaccuracy,
            int lifeTicks,
            float xRotDelta,
            float yRotDelta,
            double offsetX,
            double offsetY,
            double offsetZ) {
        elapsedLifeTicks = 0;
        setOwner(owner);
        Vec3 localOffset = new Vec3(offsetX, offsetY, offsetZ)
                .yRot(-owner.getYRot()
                        * net.minecraft.util.Mth.DEG_TO_RAD);
        setPos(owner.getEyePosition().add(localOffset));
        entityData.set(DAMAGE, Math.max(damage, 0.0F));
        entityData.set(HAMON, hamon);
        entityData.set(LIFE_TICKS, Math.max(lifeTicks, 1));
        shootFromRotation(
                owner,
                owner.getXRot() + xRotDelta,
                owner.getYRot() + yRotDelta,
                0.0F,
                speed,
                inaccuracy);
    }

    /*
     * 1.16 SquidTentacleProjectileEntity (an OwnerBoundProjectileEntity with
     * lifespan 42): bound to its owner, it extends 16 / lifespan a tick along
     * the owner's look turned by the deltas, then retracts and is gone at the
     * owner; attached to a target it stays there for 20 more ticks.
     */
    public void initializeTentacle(
            LivingEntity owner,
            boolean right,
            float xRotDelta,
            float yRotDelta,
            double offsetX,
            double offsetY) {
        elapsedLifeTicks = 0;
        tentacleDistance = 0.0D;
        setOwner(owner);
        entityData.set(DAMAGE, 0.5F);
        entityData.set(HAMON, false);
        entityData.set(LIFE_TICKS, 42);
        entityData.set(TENTACLE_RIGHT, right);
        entityData.set(ORIGIN_X, (float) offsetX);
        entityData.set(ORIGIN_Y, (float) offsetY);
        entityData.set(TENTACLE_X_ROT, xRotDelta);
        entityData.set(TENTACLE_Y_ROT, yRotDelta);
        Vec3 origin = tentacleOwnerPosition(owner, 1.0F, false);
        setPos(origin.x, origin.y, origin.z);
        setRot(owner.getYRot(), owner.getXRot());
        setDeltaMovement(Vec3.ZERO);
        noPhysics = true;
    }

    public void initializeGuardian(LivingEntity owner) {
        elapsedLifeTicks = 0;
        setOwner(owner);
        setPos(owner.getEyePosition().add(0.0D, 0.2D, 0.0D));
        setXRot(owner.getXRot());
        setYRot(owner.getYRot());
        setDeltaMovement(Vec3.ZERO);
        entityData.set(DAMAGE, 0.25F);
        entityData.set(HAMON, false);
        entityData.set(LIFE_TICKS, 600);
        entityData.set(GUARDIAN_LIFESPAN, 2);
        setBeamEnd(position().add(owner.getLookAngle().scale(24.0D)));
        noPhysics = true;
    }

    public void refreshGuardian(LivingEntity owner) {
        if (!isGuardian() || level().isClientSide()) {
            return;
        }
        setOwner(owner);
        setPos(owner.getEyePosition().add(0.0D, 0.2D, 0.0D));
        setXRot(owner.getXRot());
        setYRot(owner.getYRot());
        entityData.set(GUARDIAN_LIFESPAN, 2);
        Vec3 start = position();
        Vec3 unclippedEnd = start.add(
                Vec3.directionFromRotation(getXRot(), getYRot())
                        .scale(24.0D));
        BlockHitResult blockHit = level().clip(new ClipContext(
                start,
                unclippedEnd,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                this));
        Vec3 clipEnd = blockHit.getType() == HitResult.Type.MISS
                ? unclippedEnd
                : blockHit.getLocation();
        AABB search = getBoundingBox()
                .expandTowards(clipEnd.subtract(start))
                .inflate(1.25D);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                level(),
                this,
                start,
                clipEnd,
                search,
                entity -> entity != owner
                        && entity.isAlive());
        Vec3 end = entityHit != null
                ? entityHit.getLocation()
                : clipEnd;
        setBeamEnd(end);
        if (entityHit != null) {
            Entity target = entityHit.getEntity();
            DamageUtil.hurtThroughInvulTicks(
                    target,
                    DamageUtil.make(
                            level(),
                            DamageTypes.INDIRECT_MAGIC,
                            this,
                            owner),
                    0.25F);
        }
        // 1.16 UltimateLifeformGuardianLaser.onHoldTick spawned its beam
        // particles with addAlwaysVisibleParticle on the server, a no-op:
        // only GuardianLaserRenderer drew the beam
        entityData.set(GUARDIAN_LIFESPAN, 1);
    }

    @Override
    public void tick() {
        elapsedLifeTicks++;
        if (isTentacle()) {
            tickTentacle();
            return;
        }
        super.tick();
        if (isGuardian()) {
            tickGuardian();
            return;
        }
        if (!level().isClientSide()
                && elapsedLifeTicks >= entityData.get(LIFE_TICKS)) {
            discard();
        }
    }

    // 1.16 OwnerBoundProjectileEntity / ModdedProjectileEntity /
    // DamagingEntity.tick, in that order: no owner, past the lifespan or a
    // dead owner removes it; then the hit test from the owner to the tip,
    // then the move
    private void tickTentacle() {
        baseTick();
        if (!(getOwner() instanceof LivingEntity owner)) {
            if (!level().isClientSide()) {
                discard();
            }
            return;
        }
        if (!level().isClientSide()
                && (elapsedLifeTicks > entityData.get(LIFE_TICKS)
                        || !owner.isAlive())) {
            discard();
            return;
        }
        checkTentacleHit(owner);
        if (isRemoved()) {
            return;
        }
        if (getAttachedEntity() instanceof LivingEntity attached) {
            moveTo(attached.getX(), attached.getY(0.5D), attached.getZ(),
                    attached.getYRot(), attached.getXRot());
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        moveTentacleBoundToOwner(owner);
    }

    private void moveTentacleBoundToOwner(LivingEntity owner) {
        setRot(owner.getYRot(), owner.getXRot());
        Vec3 originPoint = tentacleOwnerPosition(owner, 1.0F, false);
        float speed = tentacleSpeed();
        float distance = isTentacleRetracting()
                ? (float) (tentacleDistance - speed)
                : entityData.get(MOVING_FORWARD)
                        ? (float) (tentacleDistance + speed)
                        : (float) tentacleDistance;
        // 1.16 updateMotionFlags: maxDistance = speed * speed * lifespan /
        // (speed + speed), the forward motion stops after maxDistance / speed
        int lifespan = entityData.get(LIFE_TICKS);
        double maxDistance = speed * speed * lifespan / (speed + speed);
        int stopForwardMotionMark = (int) (maxDistance / speed);
        if (entityData.get(MOVING_FORWARD)
                && elapsedLifeTicks >= stopForwardMotionMark) {
            entityData.set(MOVING_FORWARD, false);
        }
        if (!isTentacleRetracting()
                && elapsedLifeTicks >= stopForwardMotionMark) {
            entityData.set(RETRACTING, true);
        }
        if (isTentacleRetracting() && distance <= 0.0F) {
            if (!level().isClientSide()) {
                discard();
            }
            return;
        }
        tentacleDistance = distance;
        Vec3 next = originPoint.add(Vec3.directionFromRotation(
                        owner.getXRot() + entityData.get(TENTACLE_X_ROT),
                        owner.getYRot() + entityData.get(TENTACLE_Y_ROT))
                .scale(distance));
        double x = getX();
        double y = getY();
        double z = getZ();
        setDeltaMovement(next.subtract(x, y, z));
        xo = x;
        yo = y;
        zo = z;
        xOld = x;
        yOld = y;
        zOld = z;
        setPos(next.x, next.y, next.z);
    }

    // 1.16 SquidTentacleProjectileEntity.movementSpeed: 16 / lifespan
    private float tentacleSpeed() {
        return 16.0F / (float) entityData.get(LIFE_TICKS);
    }

    public boolean isTentacleRetracting() {
        return entityData.get(RETRACTING);
    }

    public double getTentacleDistance() {
        return tentacleDistance;
    }

    /*
     * 1.16 OwnerBoundProjectileEntity.rayTrace: every entity the line from the
     * owner to the tip meets (JojoModUtil.rayTraceMultipleEntities), else the
     * block on that line, which makes the tentacle retract (afterBlockHit, it
     * breaks nothing).
     */
    private void checkTentacleHit(LivingEntity owner) {
        Vec3 start = getTentacleOrigin(1.0F);
        Vec3 end = position().add(getDeltaMovement());
        Vec3 ray = start.subtract(end);
        AABB box = getBoundingBox().expandTowards(ray).inflate(1.0D);
        double minDistanceSqr = ray.lengthSqr();
        double inflate = getBbWidth() / 2.0D;
        Map<Entity, Double> hits = new HashMap<>();
        for (Entity target : level().getEntities(this, box,
                entity -> !entity.isSpectator() && entity.isPickable()
                        && canTentacleHit(entity, owner))) {
            AABB targetBox = target.getBoundingBox().inflate(
                    target.getPickRadius() + inflate);
            Optional<Vec3> clip = targetBox.clip(start, end);
            if (targetBox.contains(start)) {
                hits.put(target, 0.0D);
            }
            else if (clip.isPresent()) {
                double distanceSqr = start.distanceToSqr(clip.get());
                if (distanceSqr < minDistanceSqr || minDistanceSqr == 0.0D) {
                    hits.put(target, distanceSqr);
                }
            }
        }
        if (hits.isEmpty()) {
            BlockHitResult block = level().clip(new ClipContext(start, end,
                    ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, this));
            if (block.getType() != HitResult.Type.MISS) {
                entityData.set(RETRACTING, true);
            }
            return;
        }
        if (level().isClientSide()) {
            return;
        }
        hits.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .toList()
                .forEach(target -> hitTentacleTarget(target, owner));
    }

    // 1.16 DamagingEntity.canHitEntity: not the owner, a living entity the
    // owner can attack, no projectile of the same owner
    private static boolean canTentacleHit(Entity entity, LivingEntity owner) {
        if (!entity.isAlive() || entity == owner) {
            return false;
        }
        if (entity instanceof LivingEntity living) {
            return owner.canAttack(living);
        }
        if (entity instanceof Projectile projectile
                && projectile.getOwner() != null) {
            return !owner.getUUID().equals(projectile.getOwner().getUUID());
        }
        return !(owner instanceof Player ownerPlayer
                && entity instanceof Player targetPlayer
                && !ownerPlayer.canHarmPlayer(targetPlayer));
    }

    /*
     * 1.16 SquidTentacleProjectileEntity: one hit per tentacle, 0.5 damage
     * through the hurt cooldown with no knockback (knockbackMultiplier 0);
     * a hit target not blocking gets the tentacle attached and is stunned,
     * with particles, for the rest of the lifespan.
     */
    private void hitTentacleTarget(Entity target, LivingEntity owner) {
        if (dealtDamage || !isAlive()) {
            return;
        }
        DamageSource source = new DamageSource(
                DamageUtil.type(level(), ModDamageTypes.MOD_PROJECTILE),
                this,
                owner);
        ((DamageSourceModified) source).jojo_ripples$modifyKnockback(0, 0);
        dealtDamage = DamageUtil.hurtThroughInvulTicks(
                target, source, entityData.get(DAMAGE));
        if (dealtDamage
                && target instanceof LivingEntity living
                && !JojoModUtil.isTargetBlocking(living)) {
            attachTo(living);
            living.addEffect(new MobEffectInstance(
                    ModStatusEffects.STUN,
                    Math.max(entityData.get(LIFE_TICKS)
                            - elapsedLifeTicks, 1)));
        }
    }

    private void tickGuardian() {
        setDeltaMovement(Vec3.ZERO);
        noPhysics = true;
        Entity owner = getOwner();
        if (owner instanceof LivingEntity livingOwner
                && livingOwner.isAlive()
                && elapsedLifeTicks < entityData.get(LIFE_TICKS)) {
            setPos(livingOwner.getEyePosition().add(0.0D, 0.2D, 0.0D));
            setXRot(livingOwner.getXRot());
            setYRot(livingOwner.getYRot());
        }
        if (!level().isClientSide()) {
            int lifespan = entityData.get(GUARDIAN_LIFESPAN);
            if (elapsedLifeTicks >= entityData.get(LIFE_TICKS)
                    || lifespan < 0
                    || owner == null
                    || !owner.isAlive()) {
                discard();
            }
            else {
                entityData.set(GUARDIAN_LIFESPAN, lifespan - 1);
            }
        }
    }

    @Override
    protected Item getDefaultItem() {
        return Items.IRON_NUGGET;
    }

    @Override
    protected double getDefaultGravity() {
        return isGuardian() || isTentacle() ? 0.0D : 0.03D;
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (isGuardian() || isTentacle()) {
            return false;
        }
        return super.canHitEntity(target);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (level().isClientSide()) {
            return;
        }
        Entity target = result.getEntity();
        Entity owner = getOwner();
        super.onHitEntity(result);
        if (target instanceof LivingEntity living
                && entityData.get(HAMON)) {
            // 1.16 ProjectileUltimateHamonChargeCap used the plain DamageUtil.HAMON source, which names no entity.
            HamonAbilityHelpers.hamonHurt(
                    living,
                    entityData.get(DAMAGE),
                    null,
                    null);
        }
        else {
            target.hurt(
                    damageSources().thrown(this, owner),
                    entityData.get(DAMAGE));
        }
        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!level().isClientSide() && !isGuardian()) {
            discard();
        }
    }

    private void attachTo(LivingEntity target) {
        attachedEntity = target;
        attachedEntityUuid = target.getUUID();
        entityData.set(ATTACHED_ENTITY_ID, target.getId());
        entityData.set(
                LIFE_TICKS,
                entityData.get(LIFE_TICKS) + 20);
    }

    @Nullable
    public Entity getAttachedEntity() {
        if (attachedEntity != null) {
            return attachedEntity;
        }
        int networkId = entityData.get(ATTACHED_ENTITY_ID);
        Entity attached = networkId >= 0
                ? level().getEntity(networkId)
                : null;
        if (attached == null
                && attachedEntityUuid != null
                && level() instanceof ServerLevel serverLevel) {
            attached = serverLevel.getEntity(attachedEntityUuid);
        }
        if (attached instanceof LivingEntity living) {
            attachedEntity = living;
        }
        return attached;
    }

    public boolean isAttached() {
        return entityData.get(ATTACHED_ENTITY_ID) >= 0
                || attachedEntityUuid != null;
    }

    public boolean isTentacle() {
        return getType() == UltimateLifeformAddon.SQUID_TENTACLE.get();
    }

    public boolean isGuardian() {
        return getType() == UltimateLifeformAddon.LASER_END.get();
    }

    public boolean isTentacleRight() {
        return entityData.get(TENTACLE_RIGHT);
    }

    // 1.16 OwnerBoundProjectileEntity.getOriginPoint: a body part starts at
    // the owner's eyes with its offset turned by the body yaw
    public Vec3 getTentacleOrigin(float partialTick) {
        Entity owner = getOwner();
        if (!(owner instanceof LivingEntity living)) {
            return getPosition(partialTick);
        }
        return tentacleOwnerPosition(living, partialTick, true);
    }

    // 1.16 DamagingEntity.getPos: the eyes plus the owner-relative offset
    // turned by the given yaw
    private Vec3 tentacleOwnerPosition(
            LivingEntity owner, float partialTick, boolean bodyRotation) {
        float yRot = bodyRotation
                ? Mth.lerp(partialTick, owner.yBodyRotO, owner.yBodyRot)
                : Mth.lerp(partialTick, owner.yRotO, owner.getYRot());
        return owner.getEyePosition(partialTick).add(new Vec3(
                        entityData.get(ORIGIN_X),
                        entityData.get(ORIGIN_Y),
                        0.0D)
                .yRot(-yRot * Mth.DEG_TO_RAD));
    }

    // the tip; attached, it sits at half the target's height
    public Vec3 getTentacleEnd(float partialTick) {
        return getPosition(partialTick);
    }

    public Vec3 getBeamEnd() {
        return new Vec3(
                entityData.get(BEAM_END_X),
                entityData.get(BEAM_END_Y),
                entityData.get(BEAM_END_Z));
    }

    public double getBeamLength() {
        return getBeamEnd().distanceTo(position());
    }

    private void setBeamEnd(Vec3 end) {
        entityData.set(BEAM_END_X, (float) end.x);
        entityData.set(BEAM_END_Y, (float) end.y);
        entityData.set(BEAM_END_Z, (float) end.z);
    }

    public float getProjectileDamage() {
        return entityData.get(DAMAGE);
    }

    public int getLifeTicks() {
        return entityData.get(LIFE_TICKS);
    }

    public int getElapsedLifeTicks() {
        return Math.max(elapsedLifeTicks, 0);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Age", Math.max(elapsedLifeTicks, 0));
        tag.putFloat("Damage", entityData.get(DAMAGE));
        tag.putBoolean("Hamon", entityData.get(HAMON));
        tag.putInt("LifeTicks", entityData.get(LIFE_TICKS));
        tag.putBoolean("TentacleRight", entityData.get(TENTACLE_RIGHT));
        tag.putFloat("OriginX", entityData.get(ORIGIN_X));
        tag.putFloat("OriginY", entityData.get(ORIGIN_Y));
        tag.putBoolean("DealtDamage", dealtDamage);
        tag.putFloat("XRotOffset", entityData.get(TENTACLE_X_ROT));
        tag.putFloat("YRotOffset", entityData.get(TENTACLE_Y_ROT));
        tag.putDouble("Distance", tentacleDistance);
        tag.putBoolean("IsMovingForward", entityData.get(MOVING_FORWARD));
        tag.putBoolean("IsRetracting", entityData.get(RETRACTING));
        if (attachedEntityUuid != null) {
            tag.putUUID("AttachedEntity", attachedEntityUuid);
        }
        tag.putInt("GuardianLifespan", entityData.get(GUARDIAN_LIFESPAN));
        tag.putFloat("BeamEndX", entityData.get(BEAM_END_X));
        tag.putFloat("BeamEndY", entityData.get(BEAM_END_Y));
        tag.putFloat("BeamEndZ", entityData.get(BEAM_END_Z));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        elapsedLifeTicks = tag.contains("Age", Tag.TAG_INT)
                ? Math.max(tag.getInt("Age"), 0)
                : 0;
        tickCount = elapsedLifeTicks;
        entityData.set(DAMAGE, Math.max(tag.getFloat("Damage"), 0.0F));
        entityData.set(HAMON, tag.getBoolean("Hamon"));
        entityData.set(LIFE_TICKS, Math.max(tag.getInt("LifeTicks"), 1));
        entityData.set(TENTACLE_RIGHT, tag.getBoolean("TentacleRight"));
        entityData.set(ORIGIN_X, tag.getFloat("OriginX"));
        entityData.set(ORIGIN_Y, tag.getFloat("OriginY"));
        dealtDamage = tag.getBoolean("DealtDamage");
        entityData.set(TENTACLE_X_ROT, tag.getFloat("XRotOffset"));
        entityData.set(TENTACLE_Y_ROT, tag.getFloat("YRotOffset"));
        tentacleDistance = tag.getDouble("Distance");
        entityData.set(MOVING_FORWARD, !tag.contains("IsMovingForward")
                || tag.getBoolean("IsMovingForward"));
        entityData.set(RETRACTING, tag.getBoolean("IsRetracting"));
        attachedEntityUuid = tag.hasUUID("AttachedEntity")
                ? tag.getUUID("AttachedEntity")
                : null;
        if (attachedEntityUuid != null
                && level() instanceof ServerLevel serverLevel) {
            Entity attached = serverLevel.getEntity(attachedEntityUuid);
            entityData.set(
                    ATTACHED_ENTITY_ID,
                    attached != null ? attached.getId() : -1);
        }
        entityData.set(
                GUARDIAN_LIFESPAN,
                tag.getInt("GuardianLifespan"));
        entityData.set(BEAM_END_X, tag.getFloat("BeamEndX"));
        entityData.set(BEAM_END_Y, tag.getFloat("BeamEndY"));
        entityData.set(BEAM_END_Z, tag.getFloat("BeamEndZ"));
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(Math.max(elapsedLifeTicks, 0));
        buffer.writeDouble(tentacleDistance);
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buffer) {
        elapsedLifeTicks = Math.max(buffer.readVarInt(), 0);
        tickCount = elapsedLifeTicks;
        tentacleDistance = buffer.readDouble();
    }
}
