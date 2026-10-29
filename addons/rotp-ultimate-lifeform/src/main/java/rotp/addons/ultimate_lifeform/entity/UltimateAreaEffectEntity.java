package rotp.addons.ultimate_lifeform.entity;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.client.ClientProxy;
import rotp.core.client.sound.HamonSparksLoopSound;
import rotp.core.init.ModParticles;
import rotp.core.init.ModSoundEvents;
import rotp.core.util.functions.DamageUtil;
import rotp.addons.ultimate_lifeform.UltimateHamonCharge;
import rotp.addons.ultimate_lifeform.UltimateLifeformAddon;
import rotp.addons.ultimate_lifeform.effect.UltimateHamonSpreadEffect;
import rotp.addons.ultimate_lifeform.entity.UltimateParticleEmissionEvidence.PendingAttempt;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

public final class UltimateAreaEffectEntity extends Entity
        implements IEntityWithComplexSpawn {
    private static final int WAVE_TICKS = 15;
    private static final int WAVE_INTERVAL = 4;
    private static final double WAVE_WIDTH = 2.0D;
    private static final int CACTUS_EXPLOSION_RANGE = 4;
    private static final EntityDataAccessor<Boolean> CACTUS_EXPLOSION =
            SynchedEntityData.defineId(
                    UltimateAreaEffectEntity.class,
                    EntityDataSerializers.BOOLEAN);

    @Nullable private UUID ownerUuid;
    private int ownerNetworkId = -1;
    private float damage;
    private float radius = 0.25F;
    private int lifeTicks = 1;
    private boolean hamon = true;
    @Nullable private Direction.Axis axis;
    @Nullable private BlockPos targetedBlockPos;
    @Nullable private Direction targetedFace;
    private int wavesToAdd;
    private int addedWaves;
    private final List<Wave> waves = new LinkedList<>();
    private BlockPos chargedBlockPos = BlockPos.ZERO;
    @Nullable private UltimateHamonCharge blockCharge;
    private int blockChargeAge;
    private int elapsedLifeTicks;
    private final UltimateParticleEmissionEvidence particleEvidence =
            new UltimateParticleEmissionEvidence();

    public UltimateAreaEffectEntity(
            EntityType<? extends UltimateAreaEffectEntity> type,
            Level level) {
        super(type, level);
        setNoGravity(true);
        noPhysics = true;
    }

    public void initializeSendo(
            LivingEntity owner,
            BlockPos blockPos,
            Direction face,
            float damage,
            float radius,
            int waves) {
        elapsedLifeTicks = 0;
        setOwner(owner);
        this.targetedBlockPos = blockPos.immutable();
        this.targetedFace = face;
        this.axis = face.getAxis();
        this.damage = Math.max(damage, 0.0F);
        this.radius = Math.max(radius, 0.25F);
        this.wavesToAdd = Math.max(waves, 0);
        this.lifeTicks = this.wavesToAdd * WAVE_INTERVAL + WAVE_TICKS;
        setPos(
                blockPos.getX() + 0.5D,
                blockPos.getY() + getBbHeight() * 0.75D,
                blockPos.getZ() + 0.5D);
        setBoundingBox(makeWaveHitBox(this.radius));
    }

    public void initializeTurquoise(
            LivingEntity owner,
            float damage,
            float radius,
            int duration,
            double speed) {
        elapsedLifeTicks = 0;
        setOwner(owner);
        this.damage = Math.max(damage, 0.0F);
        this.radius = Math.max(radius, 0.25F);
        this.lifeTicks = Math.max(duration, 1);
        this.hamon = true;
        setPos(owner.getEyePosition().add(0.0D, -this.radius, 0.0D));
        setXRot(owner.getXRot());
        setYRot(owner.getYRot());
        setDeltaMovement(owner.getLookAngle().normalize().scale(speed));
        refreshSphereBoundingBox();
    }

    public void initializeBlockCharge(
            LivingEntity owner,
            BlockPos blockPos,
            float tickDamage,
            int chargeTicks,
            float energySpent) {
        elapsedLifeTicks = 0;
        setOwner(owner);
        setChargedBlock(blockPos);
        // 1.16 UltimateHamonBlockChargeEntity.setCharge: the add-on's own charge
        blockCharge = new UltimateHamonCharge(
                tickDamage,
                chargeTicks,
                owner,
                energySpent);
    }

    @Override
    public void tick() {
        elapsedLifeTicks++;
        super.tick();
        if (isSendo()) {
            tickSendo();
        }
        else if (isTurquoise()) {
            tickTurquoise();
        }
        else if (isBlockCharge()) {
            tickBlockCharge();
        }
        else if (!level().isClientSide()) {
            discard();
        }
    }

    private void tickSendo() {
        setBoundingBox(makeWaveHitBox(radius));
        if (elapsedLifeTicks % WAVE_INTERVAL == 0
                && addedWaves++ < wavesToAdd) {
            if (level().isClientSide()) {
                spawnSendoWaveParticles();
                waves.add(new Wave());
            }
            else {
                waves.add(new Wave());
                // 1.16 Sendo Overdrive tick: the spark sound for each added
                // wave but the last
                if (addedWaves < wavesToAdd) {
                    level().playSound(
                            null,
                            getX(),
                            getY(),
                            getZ(),
                            ModSoundEvents.HAMON_SPARK.get(),
                            SoundSource.AMBIENT,
                            0.25F,
                            1.0F + (random.nextFloat() - 0.5F) * 0.15F);
                }
            }
        }
        // 1.16: every client tick the spark loop at the point of the wave
        // box nearest the camera
        if (level().isClientSide()) {
            AABB box = makeWaveHitBox(radius);
            Vec3 camera = ClientProxy.getCameraPos();
            HamonSparksLoopSound.playSparkSound(this, new Vec3(
                    Mth.clamp(camera.x, box.minX, box.maxX),
                    Mth.clamp(camera.y, box.minY, box.maxY),
                    Mth.clamp(camera.z, box.minZ, box.maxZ)), 1.0F, true);
        }
        Iterator<Wave> iterator = waves.iterator();
        while (iterator.hasNext()) {
            Wave wave = iterator.next();
            if (level().isClientSide()) {
                wave.advanceClientAge();
            }
            else {
                wave.tick();
            }
            if (wave.finished()) {
                iterator.remove();
            }
        }
        if (!level().isClientSide()) {
            if (elapsedLifeTicks > lifeTicks) {
                discard();
            }
        }
    }

    private void tickTurquoise() {
        // 1.16 ModdedProjectileEntity.tick: gone once its known owner is dead
        LivingEntity owner = getOwner();
        if (!level().isClientSide() && owner != null && !owner.isAlive()) {
            discard();
            return;
        }
        Vec3 start = position();
        Vec3 end = start.add(getDeltaMovement());
        BlockHitResult blockHit = level().clip(new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                this));
        if (blockHit.getType() != HitResult.Type.MISS) {
            if (!level().isClientSide()) {
                discard();
            }
            return;
        }
        setPos(end);
        refreshSphereBoundingBox();
        if (level().isClientSide()) {
            spawnTurquoiseParticles();
            return;
        }
        if (!isInWaterOrBubble()) {
            discard();
            return;
        }
        float currentDamage = damage * damageWearOffMultiplier();
        // 1.16 checkHit: the living entities in water inside the projectile
        // box that DamagingEntity.canHitEntity let through (anyone without an
        // owner; else not the owner, and those the owner can attack)
        for (LivingEntity target : level().getEntitiesOfClass(
                LivingEntity.class,
                getBoundingBox(),
                target -> target.isAlive()
                        && !target.isSpectator()
                        && target.isInWaterOrBubble()
                        && (owner == null
                                || target != owner
                                        && owner.canAttack(target)))) {
            if (UltimateHamonCharge.dealUltimateHamonDamage(
                    target, currentDamage, this, owner)) {
                DamageUtil.knockback3d(
                        target,
                        radius * 0.1F,
                        getXRot(),
                        getYRot());
                UltimateHamonSpreadEffect.stackUltimateHamonBurn(target, 1);
            }
        }
        if (elapsedLifeTicks >= lifeTicks) {
            discard();
        }
    }

    private void tickBlockCharge() {
        blockChargeAge++;
        BlockState state = level().getBlockState(chargedBlockPos);
        if (blockCharge == null
                || blockCharge.shouldBeRemoved()
                || state.isAir()) {
            discardAndRestoreTripwire();
            return;
        }
        if (level().isClientSide()) {
            emitTrackedParticle(
                    UltimateLifeformAddon.RAINBOW_SPARK.get(),
                    UltimateLifeformAddon.id("rainbow_spark"),
                    getRandomX(0.5D),
                    getRandomY(),
                    getRandomZ(0.5D),
                    0.0D,
                    0.01D,
                    0.0D);
            return;
        }
        blockCharge.tick(
                null,
                chargedBlockPos,
                level(),
                new AABB(chargedBlockPos).inflate(0.1D));
        if (blockChargeAge == 60 && isCactusCharge(state)) {
            doCactusExplosion();
        }
    }

    private AABB makeWaveHitBox(double currentRadius) {
        Vec3 center = position();
        AABB hitBox = new AABB(center, center);
        if (axis == null) {
            return hitBox;
        }
        return switch (axis) {
            case X -> hitBox.inflate(
                    WAVE_WIDTH * 0.5D,
                    currentRadius,
                    currentRadius);
            case Y -> hitBox.inflate(
                    currentRadius,
                    WAVE_WIDTH * 0.5D,
                    currentRadius);
            case Z -> hitBox.inflate(
                    currentRadius,
                    currentRadius,
                    WAVE_WIDTH * 0.5D);
        };
    }

    private void spawnSendoWaveParticles() {
        if (axis == null || radius <= 0.0F) {
            return;
        }
        Vec3 center = position();
        Vec3 offset = Vec3.atLowerCornerOf(axisDirection(axis).getNormal())
                .scale(0.55D);
        spawnSendoCircle(center.add(offset));
        spawnSendoCircle(center.subtract(offset));
    }

    private void spawnSendoCircle(Vec3 center) {
        double step = Math.PI * 0.2D / radius;
        for (double angle = -Math.PI; angle < Math.PI; angle += step) {
            Vec3 radial = switch (axis) {
                case X -> new Vec3(0.0D, Math.sin(angle), Math.cos(angle));
                case Y -> new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
                case Z -> new Vec3(Math.sin(angle), Math.cos(angle), 0.0D);
            };
            Vec3 motion = radial.scale(radius / WAVE_TICKS);
            emitTrackedParticle(
                    UltimateLifeformAddon.RAINBOW_SENDO.get(),
                    UltimateLifeformAddon.id("rainbow_sendo"),
                    center.x,
                    center.y,
                    center.z,
                    motion.x,
                    motion.y,
                    motion.z);
        }
    }

    private static Direction axisDirection(Direction.Axis axis) {
        return switch (axis) {
            case X -> Direction.EAST;
            case Y -> Direction.UP;
            case Z -> Direction.SOUTH;
        };
    }

    private void spawnTurquoiseParticles() {
        Vec3 center = getBoundingBox().getCenter();
        int sparks = Math.max(
                (int) (radius * radius * 3.0F
                        * damageWearOffMultiplier()),
                1);
        for (int index = 0; index < sparks; index++) {
            Vec3 direction = new Vec3(
                    random.nextDouble() - 0.5D,
                    random.nextDouble() - 0.5D,
                    random.nextDouble() - 0.5D);
            if (direction.lengthSqr() < 1.0E-7D) {
                continue;
            }
            Vec3 spark = center.add(direction.normalize()
                    .scale(random.nextDouble() * radius));
            // 1.16 UltimateHamonTurquoiseBlueOverdriveEntity.tick: the core
            // light spark, which no add-on particle provider records
            if (level().getFluidState(BlockPos.containing(spark))
                    .is(FluidTags.WATER)) {
                level().addParticle(
                        ModParticles.LIGHT_SPARK.get(),
                        spark.x,
                        spark.y,
                        spark.z,
                        0.0D,
                        0.0D,
                        0.0D);
            }
        }
        level().playLocalSound(
                center.x,
                center.y,
                center.z,
                ModSoundEvents.HAMON_SPARK.get(),
                SoundSource.AMBIENT,
                Math.min(0.1F + radius * 0.15F, 1.0F),
                1.0F + (random.nextFloat() - 0.5F) * 0.15F,
                false);
    }

    private void refreshSphereBoundingBox() {
        Vec3 center = position();
        setBoundingBox(new AABB(center, center).inflate(radius));
    }

    private void emitTrackedParticle(
            ParticleOptions particle,
            ResourceLocation particleResource,
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ) {
        PendingAttempt attempt = particleEvidence.begin(
                this,
                particleResource.toString(),
                x,
                y,
                z,
                velocityX,
                velocityY,
                velocityZ);
        boolean submitted = false;
        try {
            level().addParticle(
                    particle,
                    x,
                    y,
                    z,
                    velocityX,
                    velocityY,
                    velocityZ);
            submitted = true;
        }
        finally {
            if (submitted) {
                particleEvidence.complete(attempt);
            }
            else {
                particleEvidence.abort(attempt);
            }
        }
    }

    private float damageWearOffMultiplier() {
        float ageRatio = lifeTicks > 0
                ? (float) elapsedLifeTicks / lifeTicks
                : 1.0F;
        return Mth.clamp(2.0F - ageRatio * 2.0F, 0.0F, 1.0F);
    }

    private void setChargedBlock(BlockPos blockPos) {
        chargedBlockPos = blockPos.immutable();
        setPos(
                blockPos.getX() + 0.5D,
                blockPos.getY(),
                blockPos.getZ() + 0.5D);
    }

    private boolean isCactusCharge(BlockState state) {
        return state.getBlock() instanceof CactusBlock
                || state.is(Blocks.POTTED_CACTUS);
    }

    private void doCactusExplosion() {
        Vec3 center = Vec3.atCenterOf(chargedBlockPos);
        for (Entity entity : level().getEntities(
                this,
                new AABB(chargedBlockPos).inflate(
                        CACTUS_EXPLOSION_RANGE))) {
            float explosionDamage = 0.2F * (3.0F
                    * CACTUS_EXPLOSION_RANGE
                    * CACTUS_EXPLOSION_RANGE
                    - (float) entity.distanceToSqr(center));
            if (explosionDamage > 0.0F) {
                entity.hurt(level().damageSources().cactus(), explosionDamage);
            }
        }
        entityData.set(CACTUS_EXPLOSION, true);
        level().destroyBlock(chargedBlockPos, false, this);
        if (blockCharge != null) {
            blockCharge.decreaseTicks(blockCharge.getTicks() + 1);
        }
    }

    private void discardAndRestoreTripwire() {
        if (!level().isClientSide()
                && level().getBlockState(chargedBlockPos)
                        .is(Blocks.COBWEB)) {
            level().setBlock(
                    chargedBlockPos,
                    Blocks.TRIPWIRE.defaultBlockState(),
                    3);
        }
        discard();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (!level().isClientSide()
                || !CACTUS_EXPLOSION.equals(accessor)
                || !entityData.get(CACTUS_EXPLOSION)) {
            return;
        }
        for (int index = 0; index < 12; index++) {
            level().addParticle(
                    net.minecraft.core.particles.ParticleTypes.SPLASH,
                    getRandomX(1.0D),
                    getY(0.5D),
                    getRandomZ(1.0D),
                    0.0D,
                    0.0D,
                    0.0D);
        }
        level().addParticle(
                net.minecraft.core.particles.ParticleTypes.EXPLOSION,
                getX(),
                getY(0.5D),
                getZ(),
                1.0D,
                0.0D,
                0.0D);
        level().playLocalSound(
                getX(),
                getY(0.5D),
                getZ(),
                SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.BLOCKS,
                0.5F,
                1.35F + random.nextFloat() * 0.15F,
                false);
    }

    private void setOwner(@Nullable LivingEntity owner) {
        ownerUuid = owner != null ? owner.getUUID() : null;
        ownerNetworkId = owner != null ? owner.getId() : -1;
    }

    @Nullable
    public LivingEntity getOwner() {
        Entity owner = null;
        if (ownerUuid != null && level() instanceof ServerLevel serverLevel) {
            owner = serverLevel.getEntity(ownerUuid);
        }
        else if (ownerNetworkId >= 0) {
            owner = level().getEntity(ownerNetworkId);
        }
        return owner instanceof LivingEntity living ? living : null;
    }

    public boolean isSendo() {
        return getType() == UltimateLifeformAddon
                .ULTIMATE_SENDO_HAMON_OVERDRIVE.get();
    }

    public boolean isTurquoise() {
        return getType() == UltimateLifeformAddon
                .ULTIMATE_TURQUOISE_BLUE_OVERDRIVE.get();
    }

    public boolean isBlockCharge() {
        return getType() == UltimateLifeformAddon.HAMON_BLOCK_CHARGE.get();
    }

    public float getEffectRadius() {
        return radius;
    }

    public float getEffectDamage() {
        return damage;
    }

    public int getEffectLifeTicks() {
        return lifeTicks;
    }

    public int getElapsedLifeTicks() {
        return Math.max(elapsedLifeTicks, 0);
    }

    public float getDamageWearOffMultiplier() {
        return damageWearOffMultiplier();
    }

    public int getActiveWaveCount() {
        return waves.size();
    }

    public List<Integer> getActiveWaveAges() {
        return waves.stream().map(wave -> wave.age).toList();
    }

    public int getBlockChargeAge() {
        return blockChargeAge;
    }

    @Nullable
    public UltimateParticleEmissionEvidence.EmissionBatch
    drainParticleEvidence() {
        return particleEvidence.drain();
    }

    public int getWavesToAdd() {
        return wavesToAdd;
    }

    @Nullable
    public Direction.Axis getWaveAxis() {
        return axis;
    }

    @Nullable
    public BlockPos getTargetedBlockPos() {
        return targetedBlockPos;
    }

    @Nullable
    public Direction getTargetedFace() {
        return targetedFace;
    }

    public BlockPos getChargedBlockPos() {
        return chargedBlockPos;
    }

    public float getBlockChargeDamage() {
        return blockCharge != null ? blockCharge.getDamage() : 0.0F;
    }

    public int getBlockChargeTicks() {
        return blockCharge != null ? blockCharge.getTicks() : 0;
    }

    public int getBlockChargeInitialTicks() {
        return blockCharge != null ? blockCharge.getInitialTicks() : 0;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(CACTUS_EXPLOSION, false);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        elapsedLifeTicks = Math.max(tag.getInt("Age"), 0);
        tickCount = elapsedLifeTicks;
        ownerUuid = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerNetworkId = tag.getInt("OwnerId");
        damage = Math.max(tag.getFloat("Damage"), 0.0F);
        radius = Math.max(tag.getFloat("Radius"), 0.25F);
        lifeTicks = Math.max(tag.getInt("LifeTicks"), 1);
        hamon = tag.getBoolean("Hamon");
        axis = readEnum(tag, "Axis", Direction.Axis.class);
        targetedBlockPos = tag.contains("TargetedBlock")
                ? BlockPos.of(tag.getLong("TargetedBlock"))
                : null;
        targetedFace = readEnum(tag, "TargetedFace", Direction.class);
        wavesToAdd = Math.max(tag.getInt("WavesToAdd"), 0);
        addedWaves = Math.max(tag.getInt("WavesAdded"), 0);
        waves.clear();
        if (tag.contains("WaveStates", Tag.TAG_LIST)) {
            ListTag waveStates = tag.getList(
                    "WaveStates", Tag.TAG_COMPOUND);
            for (Tag waveState : waveStates) {
                waves.add(new Wave((CompoundTag) waveState));
            }
        }
        else {
            for (int waveAge : tag.getIntArray("Waves")) {
                waves.add(new Wave(waveAge));
            }
        }
        if (tag.contains("ChargedBlock")) {
            BlockPos savedChargedBlock = BlockPos.of(
                    tag.getLong("ChargedBlock"));
            if (isBlockCharge()) {
                setChargedBlock(savedChargedBlock);
            }
            else {
                chargedBlockPos = savedChargedBlock;
            }
        }
        blockChargeAge = Math.max(tag.getInt("BlockChargeAge"), 0);
        if (tag.contains("UltimateHamonCharge", Tag.TAG_COMPOUND)) {
            blockCharge = UltimateHamonCharge.fromNBT(
                    tag.getCompound("UltimateHamonCharge"));
        }
        else if (tag.contains("BlockCharge", Tag.TAG_COMPOUND)) {
            blockCharge = fromCoreBlockCharge(
                    tag.getCompound("BlockCharge"));
        }
        else {
            blockCharge = null;
        }
        if (isTurquoise()) {
            refreshSphereBoundingBox();
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", Math.max(elapsedLifeTicks, 0));
        if (ownerUuid != null) {
            tag.putUUID("Owner", ownerUuid);
        }
        tag.putInt("OwnerId", ownerNetworkId);
        tag.putFloat("Damage", damage);
        tag.putFloat("Radius", radius);
        tag.putInt("LifeTicks", lifeTicks);
        tag.putBoolean("Hamon", hamon);
        writeEnum(tag, "Axis", axis);
        if (targetedBlockPos != null) {
            tag.putLong("TargetedBlock", targetedBlockPos.asLong());
        }
        writeEnum(tag, "TargetedFace", targetedFace);
        tag.putInt("WavesToAdd", wavesToAdd);
        tag.putInt("WavesAdded", addedWaves);
        tag.putIntArray(
                "Waves",
                waves.stream().mapToInt(wave -> wave.age).toArray());
        ListTag waveStates = new ListTag();
        waves.stream()
                .map(Wave::save)
                .forEach(waveStates::add);
        tag.put("WaveStates", waveStates);
        tag.putLong("ChargedBlock", chargedBlockPos.asLong());
        tag.putInt("BlockChargeAge", blockChargeAge);
        if (blockCharge != null && !blockCharge.shouldBeRemoved()) {
            tag.put("UltimateHamonCharge", blockCharge.toNBT());
        }
    }

    // Saves made before the block charge became the add-on's own kept a core
    // HamonCharge under "BlockCharge"; the core-only points flag is dropped.
    private static UltimateHamonCharge fromCoreBlockCharge(CompoundTag core) {
        CompoundTag charge = new CompoundTag();
        charge.putFloat("ULFCharge", core.getFloat("Charge"));
        charge.putInt("ULFChargeTicksInitial",
                core.getInt("ChargeTicksInitial"));
        charge.putInt("ULFChargeTicks", core.getInt("ChargeTicks"));
        if (core.hasUUID("HamonUser")) {
            charge.putUUID("ULFUser", core.getUUID("HamonUser"));
        }
        charge.putFloat("ULFEnergySpent", core.getFloat("EnergySpent"));
        charge.putBoolean("ULFLargeDmg",
                !core.contains("LargeDmg") || core.getBoolean("LargeDmg"));
        return UltimateHamonCharge.fromNBT(charge);
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(Math.max(elapsedLifeTicks, 0));
        buffer.writeVarInt(ownerNetworkId);
        buffer.writeFloat(damage);
        buffer.writeFloat(radius);
        buffer.writeVarInt(lifeTicks);
        buffer.writeBoolean(hamon);
        writeOptionalEnum(buffer, axis);
        buffer.writeBoolean(targetedBlockPos != null);
        if (targetedBlockPos != null) {
            buffer.writeBlockPos(targetedBlockPos);
        }
        writeOptionalEnum(buffer, targetedFace);
        buffer.writeVarInt(wavesToAdd);
        buffer.writeVarInt(addedWaves);
        buffer.writeVarInt(waves.size());
        for (Wave wave : waves) {
            buffer.writeVarInt(Math.max(wave.age, 0));
        }
        buffer.writeBlockPos(chargedBlockPos);
        buffer.writeVarInt(blockChargeAge);
        buffer.writeBoolean(blockCharge != null
                && !blockCharge.shouldBeRemoved());
        if (blockCharge != null && !blockCharge.shouldBeRemoved()) {
            buffer.writeNbt(blockCharge.toNBT());
        }
        buffer.writeDouble(getDeltaMovement().x);
        buffer.writeDouble(getDeltaMovement().y);
        buffer.writeDouble(getDeltaMovement().z);
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buffer) {
        elapsedLifeTicks = Math.max(buffer.readVarInt(), 0);
        tickCount = elapsedLifeTicks;
        ownerNetworkId = buffer.readVarInt();
        damage = buffer.readFloat();
        radius = buffer.readFloat();
        lifeTicks = buffer.readVarInt();
        hamon = buffer.readBoolean();
        axis = readOptionalEnum(buffer, Direction.Axis.class);
        targetedBlockPos = buffer.readBoolean()
                ? buffer.readBlockPos()
                : null;
        targetedFace = readOptionalEnum(buffer, Direction.class);
        wavesToAdd = buffer.readVarInt();
        addedWaves = buffer.readVarInt();
        int activeWaveCount = buffer.readVarInt();
        if (activeWaveCount < 0 || activeWaveCount > 64) {
            throw new IllegalArgumentException(
                    "Invalid Ultimate Sendo active wave count "
                            + activeWaveCount + ".");
        }
        waves.clear();
        for (int index = 0; index < activeWaveCount; index++) {
            waves.add(new Wave(buffer.readVarInt()));
        }
        BlockPos spawnedChargedBlock = buffer.readBlockPos();
        if (isBlockCharge()) {
            setChargedBlock(spawnedChargedBlock);
        }
        else {
            chargedBlockPos = spawnedChargedBlock;
        }
        blockChargeAge = buffer.readVarInt();
        if (buffer.readBoolean()) {
            CompoundTag chargeTag = buffer.readNbt();
            blockCharge = chargeTag != null
                    ? UltimateHamonCharge.fromNBT(chargeTag)
                    : null;
        }
        else {
            blockCharge = null;
        }
        setDeltaMovement(
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble());
        if (isTurquoise()) {
            refreshSphereBoundingBox();
        }
    }

    private static <T extends Enum<T>> void writeEnum(
            CompoundTag tag, String key, @Nullable T value) {
        if (value != null) {
            tag.putInt(key, value.ordinal());
        }
    }

    @Nullable
    private static <T extends Enum<T>> T readEnum(
            CompoundTag tag, String key, Class<T> type) {
        if (!tag.contains(key)) {
            return null;
        }
        T[] values = type.getEnumConstants();
        int ordinal = tag.getInt(key);
        return ordinal >= 0 && ordinal < values.length
                ? values[ordinal]
                : null;
    }

    private static <T extends Enum<T>> void writeOptionalEnum(
            RegistryFriendlyByteBuf buffer, @Nullable T value) {
        buffer.writeBoolean(value != null);
        if (value != null) {
            buffer.writeEnum(value);
        }
    }

    @Nullable
    private static <T extends Enum<T>> T readOptionalEnum(
            RegistryFriendlyByteBuf buffer, Class<T> type) {
        return buffer.readBoolean() ? buffer.readEnum(type) : null;
    }

    private final class Wave {
        private int age;
        private final Set<UUID> hitEntities = new HashSet<>();

        private Wave() {}

        private Wave(int age) {
            this.age = Math.max(age, 0);
        }

        private Wave(CompoundTag tag) {
            this(tag.getInt("Age"));
            ListTag savedHits = tag.getList(
                    "HitEntities", Tag.TAG_STRING);
            for (Tag savedHit : savedHits) {
                try {
                    hitEntities.add(UUID.fromString(
                            savedHit.getAsString()));
                }
                catch (IllegalArgumentException ignored) {
                    // Invalid edited UUIDs do not invalidate the wave.
                }
            }
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Age", age);
            ListTag savedHits = new ListTag();
            hitEntities.stream()
                    .map(UUID::toString)
                    .sorted()
                    .map(StringTag::valueOf)
                    .forEach(savedHits::add);
            tag.put("HitEntities", savedHits);
            return tag;
        }

        private void tick() {
            AABB hitBox = makeWaveHitBox(
                    radius * (age + 1.0D) / WAVE_TICKS);
            LivingEntity owner = getOwner();
            for (LivingEntity target : level().getEntitiesOfClass(
                    LivingEntity.class,
                    hitBox,
                    target -> target.isAlive()
                            && target != owner
                            && EntitySelector.NO_CREATIVE_OR_SPECTATOR
                                    .test(target))) {
                if (!hitEntities.add(target.getUUID())) {
                    continue;
                }
                // 1.16 Wave.tick: the raw wave damage with the plain
                // DamageUtil.HAMON source, which names no entity, then the
                // burn whether or not the hit landed
                DamageUtil.hurtThroughInvulTicks(
                        target,
                        UltimateHamonCharge.plainHamonSource(level()),
                        damage);
                UltimateHamonSpreadEffect.stackUltimateHamonBurn(target, 1);
            }
            age++;
        }

        private void advanceClientAge() {
            age++;
        }

        private boolean finished() {
            return age >= WAVE_TICKS;
        }
    }
}
