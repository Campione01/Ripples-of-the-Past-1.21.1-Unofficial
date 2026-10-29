package rotp.addons.ultimate_lifeform.entity;

import javax.annotation.Nullable;

import rotp.core.customobjects.entity_projectile.IProjectileEvasion;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;
import rotp.core.util.functions.DamageUtil;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.util.functions.MathUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;
import net.neoforged.neoforge.event.EventHooks;

public final class UltimateScaleProjectileEntity extends Projectile
        implements IEntityWithComplexSpawn, IProjectileEvasion {
    private static final EntityDataAccessor<Float> DAMAGE =
            SynchedEntityData.defineId(UltimateScaleProjectileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> HAMON =
            SynchedEntityData.defineId(UltimateScaleProjectileEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> LIFE_TICKS =
            SynchedEntityData.defineId(UltimateScaleProjectileEntity.class, EntityDataSerializers.INT);

    // 1.16 UltimateLifeformScaleProjectileEntity: getBaseDamage, ticksLifespan, getMaxHardnessBreakable
    public static final float DONOR_DAMAGE = 5.0F;
    public static final int DONOR_LIFE_TICKS = 360;
    private static final float MAX_HARDNESS_BREAKABLE = 1.0F;

    private int elapsedLifeTicks;
    private boolean deflected;
    // The core's Hamon shield and Silver Chariot turn a plain Projectile without marking it (1.16 JojoModUtil.deflectProjectile
    // set IS_DEFLECTED), so a motion turned back between two of this scale's own ticks is read as that deflection.
    @Nullable
    private Vec3 lastOwnMotion;
    // 1.16 kept the first resolved owner, so a dead or departed owner still ends the scale
    @Nullable
    private LivingEntity knownOwner;

    public UltimateScaleProjectileEntity(
            EntityType<? extends UltimateScaleProjectileEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DAMAGE, DONOR_DAMAGE);
        builder.define(HAMON, false);
        builder.define(LIFE_TICKS, DONOR_LIFE_TICKS);
    }

    public void initialize(LivingEntity owner, float damage, boolean hamon, float speed, float inaccuracy) {
        initialize(owner, damage, hamon, speed, inaccuracy, DONOR_LIFE_TICKS);
    }

    public void initialize(
            LivingEntity owner, float damage, boolean hamon, float speed, float inaccuracy, int lifeTicks) {
        elapsedLifeTicks = 0;
        setOwner(owner);
        setPos(owner.getEyePosition().add(0.0D, -0.3D, 0.0D));
        entityData.set(DAMAGE, Math.max(damage, 0.0F));
        entityData.set(HAMON, hamon);
        entityData.set(LIFE_TICKS, Math.max(lifeTicks, 1));
        Vec3 aim = Vec3.directionFromRotation(owner.getXRot(), owner.getYRot());
        shoot(aim.x, aim.y, aim.z, speed, inaccuracy);
    }

    @Override
    public void shoot(double x, double y, double z, float speed, float inaccuracy) {
        super.shoot(x, y, z, speed, inaccuracy);
        Vec3 motion = getDeltaMovement();
        setYRot(MathUtil.yRotDegFromVec(motion));
        setXRot(MathUtil.xRotDegFromVec(motion));
        yRotO = getYRot();
        xRotO = getXRot();
    }

    @Override
    public void setOwner(@Nullable Entity owner) {
        super.setOwner(owner);
        if (owner != null) {
            knownOwner = owner instanceof LivingEntity living ? living : null;
        }
    }

    private boolean ownerIsGone() {
        if (knownOwner == null && getOwner() instanceof LivingEntity living) {
            knownOwner = living;
        }
        return knownOwner != null && !knownOwner.isAlive();
    }

    @Override
    public void tick() {
        if (!level().isClientSide() && ownerIsGone()) {
            discard();
            return;
        }
        if (!level().isClientSide() && lastOwnMotion != null && getDeltaMovement().dot(lastOwnMotion) < 0.0D) {
            deflected = true;
        }
        elapsedLifeTicks++;
        super.tick();
        checkHit();
        checkInsideBlocks();
        // Use the post-impact motion; a legitimate deflection must survive this step.
        Vec3 next = position().add(getDeltaMovement());
        setPos(next.x, next.y, next.z);
        lastOwnMotion = getDeltaMovement();
        if (!level().isClientSide() && elapsedLifeTicks >= getLifeTicks()) {
            discard();
        }
    }

    private void checkHit() {
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS && !EventHooks.onProjectileImpact(this, hit)) {
            hitTargetOrDeflectSelf(hit);
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        if (entity.isSpectator() || !super.canHitEntity(entity)) {
            return false;
        }
        Entity owner = getOwner();
        if (owner == null) {
            return true;
        }
        if (entity instanceof LivingEntity) {
            return !entity.is(owner) || deflected;
        }
        if (entity instanceof Projectile other) {
            Entity otherOwner = other.getOwner();
            return otherOwner == null || !owner.getUUID().equals(otherOwner.getUUID());
        }
        return true;
    }

    @Override
    protected void onDeflection(@Nullable Entity entity, boolean deflectedByPlayer) {
        deflected = true;
    }

    @Override
    public void onExplosionHit(@Nullable Entity source) {
        super.onExplosionHit(source);
        // A blast that throws the scale back is no deflection (1.16 left IS_DEFLECTED alone).
        if (lastOwnMotion != null) {
            lastOwnMotion = getDeltaMovement();
        }
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0D;
    }

    @Override
    public boolean canBeEvaded(@Nullable Entity context) {
        return true;
    }

    @Override
    public boolean standDamage() {
        return false;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (level().isClientSide() || !isAlive()) {
            return;
        }
        super.onHitEntity(result);
        Entity target = result.getEntity();
        Entity owner = getOwner();
        if (target instanceof LivingEntity living && entityData.get(HAMON)) {
            // 1.16 ProjectileUltimateHamonChargeCap used the plain DamageUtil.HAMON source, which names no entity.
            HamonAbilityHelpers.hamonHurt(living, getProjectileDamage(), null, null);
        }
        else {
            DamageUtil.hurtThroughInvulTicks(target, damageSources().thrown(this, owner), getProjectileDamage());
        }
        discard();
        if (random.nextDouble() > 0.66D && target instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 1));
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!(level() instanceof ServerLevel level) || !isAlive()) {
            return;
        }
        BlockPos blockPos = result.getBlockPos();
        BlockState blockState = level.getBlockState(blockPos);
        LivingEntity owner = getOwner() instanceof LivingEntity living ? living : null;
        boolean broken = (owner == null || JojoModUtil.canEntityDestroy(level, blockPos, blockState, owner))
                && breakSoftBlock(level, blockPos, blockState, owner);
        if (broken) {
            setDeltaMovement(getDeltaMovement().scale(0.9D));
            checkHit();
        }
        else {
            SoundType sound = blockState.getSoundType(level, blockPos, this);
            level.playSound(null, blockPos, sound.getHitSound(), SoundSource.BLOCKS, 1.0F, sound.getPitch() * 0.5F);
        }
        // 1.16 afterBlockHit removed the scale in both branches: it only carries on inside this tick's re-check.
        discard();
    }

    private boolean breakSoftBlock(
            ServerLevel level, BlockPos blockPos, BlockState blockState, @Nullable LivingEntity owner) {
        float hardness = blockState.getDestroySpeed(level, blockPos);
        if (hardness < 0.0F || hardness > MAX_HARDNESS_BREAKABLE) {
            return false;
        }
        return JojoModUtil.destroyBlock(level, blockPos, JojoModUtil.dropBrokenBlock(owner), owner);
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
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        yRotO = getYRot();
        xRotO = getXRot();
    }

    @Override
    public void lerpMotion(double x, double y, double z) {
        // Tracking packets own orientation, including a legitimately zero launch angle.
        setDeltaMovement(x, y, z);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Age", getElapsedLifeTicks());
        tag.putFloat("Damage", getProjectileDamage());
        tag.putBoolean("Hamon", entityData.get(HAMON));
        tag.putInt("LifeTicks", getLifeTicks());
        tag.putBoolean("IsDeflected", deflected);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        elapsedLifeTicks = tag.contains("Age", Tag.TAG_INT) ? Math.max(tag.getInt("Age"), 0) : 0;
        tickCount = elapsedLifeTicks;
        entityData.set(DAMAGE, Math.max(tag.getFloat("Damage"), 0.0F));
        entityData.set(HAMON, tag.getBoolean("Hamon"));
        entityData.set(LIFE_TICKS, Math.max(tag.getInt("LifeTicks"), 1));
        deflected = tag.getBoolean("IsDeflected");
        lastOwnMotion = null;
        knownOwner = null;
        // The former ThrowableItemProjectile saved Item and used vanilla launch angles.
        if (tag.contains("Item", Tag.TAG_COMPOUND) && getDeltaMovement().lengthSqr() > 0.0D) {
            setYRot(MathUtil.yRotDegFromVec(getDeltaMovement()));
            setXRot(MathUtil.xRotDegFromVec(getDeltaMovement()));
            yRotO = getYRot();
            xRotO = getXRot();
        }
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(getElapsedLifeTicks());
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buffer) {
        elapsedLifeTicks = Math.max(buffer.readVarInt(), 0);
        tickCount = elapsedLifeTicks;
    }
}
