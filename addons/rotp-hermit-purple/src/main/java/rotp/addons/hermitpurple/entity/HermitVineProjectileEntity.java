package rotp.addons.hermitpurple.entity;

import javax.annotation.Nullable;

import rotp.core.customobjects.entity_projectile.OwnerBoundProjectileEntity;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.HamonUtil;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.entity.HamonBlockChargeEntity;

import rotp.addons.hermitpurple.HermitHamon;
import rotp.addons.hermitpurple.HermitPurpleAddon;
import rotp.addons.hermitpurple.HermitPurpleSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class HermitVineProjectileEntity
        extends OwnerBoundProjectileEntity {
    // 1.16 HPVineEntity.RIGHT_OFFSET and LEFT_OFFSET, chosen by the user's main arm.
    private static final Vec3 RIGHT_OFFSET = new Vec3(-0.3D, -0.2D, 0.0D);
    private static final Vec3 LEFT_OFFSET = new Vec3(0.3D, -0.2D, 0.0D);

    private static final EntityDataAccessor<Boolean> BINDING =
            SynchedEntityData.defineId(
                    HermitVineProjectileEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> HAMON_CHARGED =
            SynchedEntityData.defineId(
                    HermitVineProjectileEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> ATTACHED_ENTITY =
            SynchedEntityData.defineId(
                    HermitVineProjectileEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> RETRACTING =
            SynchedEntityData.defineId(
                    HermitVineProjectileEntity.class,
                    EntityDataSerializers.BOOLEAN);

    private boolean dealtDamage;
    private Vec3 ordinaryBaseOffset = Vec3.ZERO;
    private float ordinaryYawOffset;
    private float ordinaryPitchOffset;
    private float ordinaryDistance;
    private boolean ordinaryFrameInitialized;

    public HermitVineProjectileEntity(
            LivingEntity shooter, Level level) {
        super(
                HermitPurpleAddon.HP_VINE_ENTITY_TYPE.get(),
                shooter,
                level);
    }

    public HermitVineProjectileEntity(
            EntityType<? extends HermitVineProjectileEntity> type,
            Level level) {
        super(type, level);
    }

    public void setBinding(boolean binding) {
        entityData.set(BINDING, binding);
    }

    public boolean isBinding() {
        return entityData.get(BINDING);
    }

    public void setHamonCharged(boolean charged) {
        entityData.set(HAMON_CHARGED, charged);
    }

    public boolean isRetracting() {
        return entityData.get(RETRACTING);
    }

    private void setRetracting(boolean retracting) {
        entityData.set(RETRACTING, retracting);
    }

    // 1.16 HPVineEntity kept yRotOffset and xRotOffset and added them to its owner's rotation every tick.
    public void initializeLashFrame(float yawOffset, float pitchOffset) {
        ordinaryBaseOffset = getOwnerRelativeOffset();
        ordinaryYawOffset = yawOffset;
        ordinaryPitchOffset = pitchOffset;
        ordinaryDistance = 0.0F;
        setRetracting(false);
        ordinaryFrameInitialized = true;
    }

    @Nullable
    public LivingEntity getEntityAttachedTo() {
        int id = entityData.get(ATTACHED_ENTITY);
        Entity entity = id >= 0 ? level().getEntity(id) : null;
        return entity instanceof LivingEntity living
                ? living
                : null;
    }

    @Override
    public int ticksLifespan() {
        return HermitPurpleSpec.VINE_ATTACK_LIFETIME_TICKS
                + (isBinding() && getEntityAttachedTo() != null
                        ? HermitPurpleSpec
                                .VINE_BIND_EXTRA_LIFETIME_TICKS
                        : 0);
    }

    @Override
    protected float getBaseDamage() {
        return isBinding()
                ? HermitPurpleSpec.VINE_BIND_DAMAGE
                : HermitPurpleSpec.VINE_ATTACK_DAMAGE;
    }

    @Override
    protected float getMaxHardnessBreakable() {
        return 0.0F;
    }

    // 1.16 HPVineEntity.knockbackMultiplier: the damage itself pushes nothing.
    @Override
    protected float knockbackMultiplier() {
        return 0.0F;
    }

    @Override
    public boolean standDamage() {
        return true;
    }

    @Override
    protected boolean shouldFollowOwner() {
        return false;
    }

    @Override
    public boolean isBodyPart() {
        return true;
    }

    @Override
    public boolean ownerInvisibility() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Nullable
    private LivingEntity physicalUser() {
        LivingEntity owner = getOwner();
        return owner instanceof StandEntity stand ? stand.getUser() : owner;
    }

    @Override
    protected Vec3 getOwnerRelativeOffset() {
        LivingEntity user = physicalUser();
        return user != null && user.getMainArm() == HumanoidArm.RIGHT
                ? RIGHT_OFFSET
                : LEFT_OFFSET;
    }

    // 1.16 made the Stand's user the vine's owner, so the vine and its hit ray
    // start at the user's eyes. Here the controller owns it for authority only.
    @Override
    protected Vec3 ownerPosition(float partialTick, boolean useBodyRotation) {
        LivingEntity user = physicalUser();
        if (user == null) {
            return super.ownerPosition(partialTick, useBodyRotation);
        }
        float yRot = useBodyRotation
                ? Mth.lerp(partialTick, user.yBodyRotO, user.yBodyRot)
                : Mth.lerp(partialTick, user.yRotO, user.getYRot());
        return getPos(
                user,
                partialTick,
                yRot,
                Mth.lerp(partialTick, user.xRotO, user.getXRot()));
    }

    @Override
    protected void moveProjectile() {
        LivingEntity attached = getEntityAttachedTo();
        if (attached != null) {
            setPos(
                    attached.getX(),
                    attached.getY(0.5D),
                    attached.getZ());
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        if (ordinaryFrameInitialized) {
            moveOrdinaryLash();
            return;
        }
        super.moveProjectile();
    }

    private void moveOrdinaryLash() {
        float speed = 8.0F / ticksLifespan();
        double maximum = speed * speed * ticksLifespan()
                / (speed + speed);
        // The donor advances once more before switching to retraction.
        boolean retracting = isRetracting();
        ordinaryDistance += (retracting ? -speed : speed)
                * getSpeedFactor();
        if (!retracting && tickCount >= (int) (maximum / speed)) {
            setRetracting(true);
            retracting = true;
        }
        if (retracting && ordinaryDistance <= 0.0F) {
            setDeltaMovement(Vec3.ZERO);
            if (!level().isClientSide()) {
                discard();
            }
            return;
        }

        LivingEntity user = physicalUser();
        if (user == null) {
            if (!level().isClientSide()) {
                discard();
            }
            return;
        }
        float yaw = (float) Math.toRadians(-user.getYRot());
        Vec3 next = user.getEyePosition()
                .add(ordinaryBaseOffset.yRot(yaw))
                .add(Vec3.directionFromRotation(user.getXRot() + ordinaryPitchOffset,
                                user.getYRot() + ordinaryYawOffset)
                        .scale(ordinaryDistance));
        setDeltaMovement(next.subtract(position()));
        xo = xOld = getX();
        yo = yOld = getY();
        zo = zOld = getZ();
        setPos(next);
        setRot(user.getYRot(), user.getXRot());
    }

    @Override
    public void tick() {
        super.tick();
        if (!isAlive()) {
            return;
        }
        LivingEntity attached = getEntityAttachedTo();
        if (attached != null) {
            if (!level().isClientSide()) {
                if (!attached.isAlive()) {
                    discard();
                    return;
                }
                attached.addEffect(new MobEffectInstance(
                        ModStatusEffects.STUN,
                        HermitPurpleSpec
                                .VINE_BIND_STUN_REFRESH_TICKS,
                        0,
                        false,
                        false,
                        false));
            }
            return;
        }
        if (!level().isClientSide()) {
            LivingEntity owner = getOwner();
            HermitPurpleStandEntity controller =
                    owner instanceof HermitPurpleStandEntity stand
                            ? stand
                            : null;
            if (controller == null
                    || controller.getUser() == null
                    || !HermitPurpleAddon.isHermitPurpleUser(
                            controller.getUser())
                    || !level().hasChunkAt(blockPosition())
                    || getOriginPoint(1.0F).distanceToSqr(position())
                            > HermitPurpleSpec.VINE_ATTACK_RANGE
                            * HermitPurpleSpec
                                    .VINE_ATTACK_RANGE) {
                discard();
            }
        }
    }

    @Override
    protected boolean hurtTarget(
            Entity target, @Nullable LivingEntity owner) {
        if (dealtDamage) {
            return false;
        }
        boolean hurt = super.hurtTarget(target, owner);
        LivingEntity user =
                owner instanceof StandEntity stand
                        ? stand.getUser()
                        : owner;
        if (entityData.get(HAMON_CHARGED)
                && target instanceof LivingEntity living
                && user != null) {
            HermitHamon.data(user).ifPresent(hamon -> {
                float cost =
                        HermitPurpleSpec
                                .VINE_HAMON_POINTS_COST
                                + hamon.getHamonStrengthLevel();
                hamon.hamonPointsFromAction(
                        HamonData.HamonStat.STRENGTH, cost);
                hamon.hamonPointsFromAction(
                        HamonData.HamonStat.CONTROL, cost);
                HermitHamon.hurt(
                        living,
                        user,
                        HermitPurpleSpec.VINE_HAMON_DAMAGE
                                * (isBinding() ? 0.3F : 1.0F),
                        false);
            });
        }
        return hurt;
    }

    @Override
    protected void afterEntityHit(
            EntityHitResult hit, boolean entityHurt) {
        if (!entityHurt || dealtDamage || level().isClientSide()) {
            return;
        }
        dealtDamage = true;
        Entity target = hit.getEntity();
        playSound(
                HermitPurpleAddon.HP_GRAB_CATCH.get(),
                0.25F,
                1.0F);
        // 1.16 HPVineEntity.afterEntityHit: the binding lash only attaches;
        // the knockback and the finisher gain belong to the ordinary lash.
        if (isBinding()) {
            if (target instanceof LivingEntity living
                    && !JojoModUtil.isTargetBlocking(living)) {
                entityData.set(ATTACHED_ENTITY, living.getId());
                setDeltaMovement(Vec3.ZERO);
                living.addEffect(new MobEffectInstance(
                        ModStatusEffects.STUN,
                        Math.max(
                                1,
                                ticksLifespan() - tickCount),
                        0,
                        false,
                        false,
                        false));
            }
            return;
        }
        if (target instanceof LivingEntity living) {
            LivingEntity owner = getOwner();
            LivingEntity facing = physicalUser();
            // 1.16 DamageUtil.knockback(target, 0.15F, yRot): along the
            // facing of the vine's owner, which was the user.
            float yaw = (facing != null ? facing.getYRot() : getYRot())
                    * Mth.DEG_TO_RAD;
            living.knockback(
                    HermitPurpleSpec.VINE_ATTACK_KNOCKBACK,
                    Mth.sin(yaw),
                    -Mth.cos(yaw));
            if (owner instanceof HermitPurpleStandEntity stand) {
                LivingEntity user = stand.getUser();
                StandPower power =
                        user != null ? StandPower.get(user) : null;
                if (power != null && power.getResolveLevel() > 0) {
                    stand.addFinisherMeter(
                            HermitPurpleSpec
                                    .VINE_FINISHER_GAIN,
                            HermitPurpleSpec
                                    .VINE_FINISHER_NO_DECAY_TICKS);
                }
            }
        }
        setRetracting(true);
    }

    @Override
    protected void afterBlockHit(
            BlockHitResult hit, boolean blockDestroyed) {
        if (level().isClientSide()) {
            return;
        }
        applyBlockHitEffects(this, getOwner(), hit.getBlockPos(), 20);
    }

    // 1.16 vine block hits: light a lamp, charge an iron block (Metal Silver
    // Overdrive) or a living block (Plant Infusion + Resolve). The lash
    // charges iron for 20 ticks, the grapple for 200.
    static void applyBlockHitEffects(
            Entity vine,
            @Nullable LivingEntity owner,
            BlockPos pos,
            int ironChargeTicks) {
        Level level = vine.level();
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.REDSTONE_LAMP)
                && state.hasProperty(RedstoneLampBlock.LIT)
                && !state.getValue(RedstoneLampBlock.LIT)) {
            level.setBlockAndUpdate(
                    pos,
                    state.setValue(
                            RedstoneLampBlock.LIT, true));
            vine.playSound(
                    HermitPurpleAddon.HP_SPARK.get(),
                    0.5F,
                    1.0F);
        }

        LivingEntity user =
                owner instanceof StandEntity stand
                        ? stand.getUser()
                        : owner;
        HermitHamon.data(user).ifPresent(hamon -> {
            if (state.is(Blocks.IRON_BLOCK)
                    && hamon.isSkillLearned(
                            ModHamonSkills
                                    .METAL_SILVER_OVERDRIVE
                                    .get())) {
                createBlockCharge(
                        level,
                        pos,
                        user,
                        1.2F * hamon.getHamonDamageMultiplier(),
                        ironChargeTicks);
            }
            else if (HamonUtil.isBlockLiving(state)
                    && user.hasEffect(ModStatusEffects.RESOLVE)
                    && hamon.isSkillLearned(
                            ModHamonSkills
                                    .PLANT_BLOCK_INFUSION
                                    .get())) {
                float efficiency =
                        hamon.getActionEfficiency(
                                HermitPurpleSpec
                                        .VINE_HAMON_POINTS_COST,
                                true);
                int chargeTicks = 100
                        + (int) (1100.0F
                                * hamon.getHamonStrengthLevel()
                                / 60.0F
                                * efficiency
                                * efficiency);
                createBlockCharge(
                        level,
                        pos,
                        user,
                        0.02F
                                * hamon.getHamonDamageMultiplier()
                                * efficiency,
                        chargeTicks);
            }
        });
    }

    private static void createBlockCharge(
            Level level,
            BlockPos pos,
            LivingEntity user,
            float tickDamage,
            int ticks) {
        level.getEntitiesOfClass(
                        HamonBlockChargeEntity.class,
                        new AABB(pos))
                .forEach(Entity::discard);
        HamonBlockChargeEntity charge =
                new HamonBlockChargeEntity(level, pos);
        charge.setCharge(
                tickDamage,
                ticks,
                user,
                HermitPurpleSpec
                        .VINE_HAMON_POINTS_COST);
        level.addFreshEntity(charge);
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        if (getEntityAttachedTo() != null
                || !super.canHitEntity(entity)) {
            return false;
        }
        LivingEntity owner = getOwner();
        if (entity == owner) {
            return false;
        }
        if (owner instanceof StandEntity stand) {
            LivingEntity user = stand.getUser();
            return entity != user && stand.canAttackEntity(entity);
        }
        return true;
    }

    @Override
    protected void breakProjectile(
            TargetType targetType, @Nullable HitResult target) {
        // 1.16 OwnerBoundProjectileEntity.breakProjectile is empty: contact never
        // removes a lash. Only a frameless one still in free flight ends here.
        if (!ordinaryFrameInitialized
                && getEntityAttachedTo() == null
                && !level().isClientSide()) {
            discard();
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (getOwner()
                instanceof HermitPurpleStandEntity controller) {
            controller.forgetLash(this);
        }
        super.remove(reason);
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        super.writeSpawnData(buffer);
        buffer.writeBoolean(ordinaryFrameInitialized);
        buffer.writeDouble(ordinaryBaseOffset.x);
        buffer.writeDouble(ordinaryBaseOffset.y);
        buffer.writeDouble(ordinaryBaseOffset.z);
        buffer.writeFloat(ordinaryYawOffset);
        buffer.writeFloat(ordinaryPitchOffset);
        buffer.writeFloat(ordinaryDistance);
        buffer.writeBoolean(isRetracting());
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buffer) {
        super.readSpawnData(buffer);
        ordinaryFrameInitialized = buffer.readBoolean();
        ordinaryBaseOffset = new Vec3(
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        ordinaryYawOffset = buffer.readFloat();
        ordinaryPitchOffset = buffer.readFloat();
        ordinaryDistance = buffer.readFloat();
        setRetracting(buffer.readBoolean());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("OrdinaryFrameInitialized", ordinaryFrameInitialized);
        tag.putDouble("OrdinaryBaseX", ordinaryBaseOffset.x);
        tag.putDouble("OrdinaryBaseY", ordinaryBaseOffset.y);
        tag.putDouble("OrdinaryBaseZ", ordinaryBaseOffset.z);
        tag.putFloat("OrdinaryYawOffset", ordinaryYawOffset);
        tag.putFloat("OrdinaryPitchOffset", ordinaryPitchOffset);
        tag.putFloat("OrdinaryDistance", ordinaryDistance);
        tag.putBoolean("OrdinaryRetracting", isRetracting());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // Pre-frame saves finish their short legacy flight rather than snapping to the eye.
        ordinaryFrameInitialized = tag.getBoolean("OrdinaryFrameInitialized");
        ordinaryBaseOffset = new Vec3(
                tag.getDouble("OrdinaryBaseX"),
                tag.getDouble("OrdinaryBaseY"),
                tag.getDouble("OrdinaryBaseZ"));
        ordinaryYawOffset = tag.getFloat("OrdinaryYawOffset");
        ordinaryPitchOffset = tag.getFloat("OrdinaryPitchOffset");
        ordinaryDistance = tag.getFloat("OrdinaryDistance");
        setRetracting(tag.getBoolean("OrdinaryRetracting"));
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BINDING, false);
        builder.define(HAMON_CHARGED, false);
        builder.define(ATTACHED_ENTITY, -1);
        builder.define(RETRACTING, false);
    }
}
