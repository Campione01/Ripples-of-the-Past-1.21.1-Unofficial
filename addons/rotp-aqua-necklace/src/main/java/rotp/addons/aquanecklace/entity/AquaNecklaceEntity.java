package rotp.addons.aquanecklace.entity;

import rotp.addons.aquanecklace.AquaNecklaceAddon;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.mojang.datafixers.util.Either;

import rotp.addons.aquanecklace.ability.AquaNecklaceAbilities;
import rotp.addons.aquanecklace.stand.AquaNecklaceTrainingData;
import rotp.core.client.ClientGlobals;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.mechanics.JojoDefinitions;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.util.functions.DamageUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.horse.SkeletonHorse;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EndRodBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class AquaNecklaceEntity extends StandEntity {
    private boolean suppressPossessionAutoRetraction;
    private static final ResourceLocation THE_HAND_STAND =
            id("rotp_the_hand", "the_hand");
    private static final ResourceLocation THE_HAND_ERASE =
            id("rotp_the_hand", "the_hand_erase");
    private static final ResourceLocation THE_HAND_ERASURE_BARRAGE =
            id("rotp_the_hand", "the_hand_erasure_barrage");
    private static final ResourceLocation SILVER_CHARIOT_STAND =
            id("jojo_ripples", "silver_chariot");
    private static final ResourceLocation SILVER_CHARIOT_BARRAGE =
            id("jojo_ripples", "silver_chariot_barrage");
    private static final ResourceLocation SILVER_CHARIOT_RAPIER =
            id("jojo_ripples", "sc_rapier");
    private static final Set<ResourceLocation> MAGICIANS_RED_PROJECTILES =
            Set.of(
                    id("jojo_ripples", "mr_fireball"),
                    id("jojo_ripples", "mr_flame"),
                    id("jojo_ripples", "mr_crossfire_hurricane"),
                    id(
                            "jojo_ripples",
                            "mr_crossfire_hurricane_special"),
                    id("jojo_ripples", "mr_red_bind"),
                    id("jojo_ripples", "mr_detector"));
    // 1.16 attackEntity: 1.5x against water-sensitive targets
    private static final float WATER_SENSITIVE_MULTIPLIER = 1.5F;
    // 1.16 bypassArmor from inside the host (it skipped shields too)
    private static final Set<TagKey<DamageType>> INSIDE_HOST_TAGS =
            Set.of(
                    DamageTypeTags.BYPASSES_ARMOR,
                    DamageTypeTags.BYPASSES_WOLF_ARMOR,
                    DamageTypeTags.BYPASSES_SHIELD);

    private static final EntityDataAccessor<Integer> MATERIAL_STATE =
            SynchedEntityData.defineId(
                    AquaNecklaceEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> POSSESSED_TARGET_ID =
            SynchedEntityData.defineId(
                    AquaNecklaceEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FLUID_GUARDING =
            SynchedEntityData.defineId(
                    AquaNecklaceEntity.class,
                    EntityDataSerializers.BOOLEAN);

    public AquaNecklaceEntity(
            EntityType<? extends AquaNecklaceEntity> type,
            Level level) {
        super(type, level);
        // 1.16 AquaNecklaceEntity.getDefaultOffsetFromUser: behind the
        // user's right shoulder and arms-only both 0.8 up, turned with the
        // view yaw. The 0.8 is also the default y of task offsets without
        // their own.
        Y_OFFSET = 0.8D;
        offsetFromUser.idleOffset = new Vec3(-0.75D, Y_OFFSET, -0.75D);
        offsetFromUser.idleRotations = StandOffsetFromUser.Rotations.HEAD;
        offsetFromUser.armsOnlyIdleOffset = new Vec3(0.0D, 0.8D, 0.15D);
        offsetFromUser.armsOnlyIdleRotations =
                StandOffsetFromUser.Rotations.HEAD;
        offsetFromUser.grabIdleOffset = new Vec3(-1.0D, Y_OFFSET, 1.5D);
        offsetFromUser.resetToIdle();
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MATERIAL_STATE, MaterialState.SOLID.id());
        builder.define(POSSESSED_TARGET_ID, -1);
        builder.define(FLUID_GUARDING, false);
    }

    @Override
    public void tick() {
        super.tick();

        LivingEntity target = getPossessedTarget();
        LivingEntity owner = getUser();
        if (!level().isClientSide()
                && target != null
                && !canPossessTarget(owner, target)) {
            clearPossession();
            target = null;
        }

        MaterialState state = getMaterialState();
        if (!level().isClientSide()
                && state == MaterialState.VAPOR
                && isInWater()) {
            setMaterialState(MaterialState.LIQUID);
            state = MaterialState.LIQUID;
            StandPower power = getUserPower();
            if (power != null) {
                power.consumeStamina(40.0F);
            }
        }

        boolean possessed = target != null;
        noPhysics = possessed || state == MaterialState.VAPOR
                || isBeingRetracted();
        setNoGravity(
                possessed
                        || state == MaterialState.VAPOR
                        || state == MaterialState.LIQUID
                                && (isInWater()
                                        || touchesTraversableBlock(
                                                Vec3.ZERO)));
        if (isOnFire()) {
            boolean playExtinguish =
                    !possessed || isSkeletonHost(target);
            clearFire();
            if (playExtinguish
                    && level()
                            instanceof ServerLevel serverLevel) {
                StandUtil.broadcastSound(
                        serverLevel,
                        position(),
                        BuiltInRegistries.SOUND_EVENT
                                .wrapAsHolder(
                                        SoundEvents
                                                .FIRE_EXTINGUISH),
                        true,
                        getUserPower(),
                        getSoundSource(),
                        1.0F,
                        1.0F);
                sendExtinguishSmoke(serverLevel);
            }
        }

        if (!level().isClientSide()) {
            if (state == MaterialState.LIQUID && owner != null && !isRemoved()) {
                StandPower power = getUserPower();
                var data = power != null ? power.getCurTypeData() : null;
                if (data instanceof AquaNecklaceTrainingData training) {
                    training.addLiquidTrainingTick(power);
                }
                else if (data != null) {
                    data.addAbilityLearningProgressPoints(
                            AquaNecklaceAddon.CHANGE_TO_LIQUID_NAME,
                            AquaNecklaceAbilities.LIQUID_TRAINING_PER_TICK,
                            AquaNecklaceAbilities.LIQUID_MAX_TRAINING,
                            power);
                }
            }
            else {
                flushLiquidTraining();
            }
            applyFluidStealth(state, owner);
        }

        if (!level().isClientSide()
                && target != null
                && target.isAlive()) {
            applySmallHostPressure(target);
        }
    }

    // 1.16 tick: liquid in water or rain is fully invisible,
    // and seeping through doors or bars blinds the user.
    private void applyFluidStealth(
            MaterialState state, @Nullable LivingEntity owner) {
        if (state == MaterialState.LIQUID
                && isInWaterOrRain()
                && !isEnteringEntity()) {
            addEffect(new MobEffectInstance(
                    ModStatusEffects.FULL_INVISIBILITY,
                    2, 0, false, false, false));
        }
        if (owner != null
                && state != MaterialState.SOLID
                && touchesTraversableBlock(Vec3.ZERO)) {
            owner.addEffect(new MobEffectInstance(
                    MobEffects.BLINDNESS,
                    10, 0, false, false, false));
        }
    }

    private boolean isEnteringEntity() {
        EntityActionInstance action = getCurStandAction();
        return action != null
                && action.ability
                        instanceof AquaNecklaceAbilities.EnterEntityAbility;
    }

    // 1.16 tick: the fizz also puffs 8 large smoke for Stand viewers
    private void sendExtinguishSmoke(ServerLevel serverLevel) {
        double halfWidth = getBbWidth() / 2.0D;
        double halfHeight = getBbHeight() / 2.0D;
        for (ServerPlayer viewer : serverLevel.players()) {
            if (StandUtil.entityCanSeeStands(viewer)) {
                serverLevel.sendParticles(
                        viewer, ParticleTypes.LARGE_SMOKE, false,
                        getX(), getY(), getZ(), 8,
                        halfWidth, halfHeight, halfWidth, 0.0D);
            }
        }
    }

    @Override
    public void move(MoverType type, Vec3 movement) {
        if (getMaterialState() != MaterialState.LIQUID
                || noPhysics
                || movement.equals(Vec3.ZERO)
                || !touchesTraversableBlock(movement)) {
            super.move(type, movement);
            return;
        }

        Vec3 requested = isManuallyControlled()
                        && touchesTraversableBlock(Vec3.ZERO)
                ? movement.scale(0.5D)
                : movement;
        Vec3 clipped = clipLiquidMovement(requested);
        boolean previousNoPhysics = noPhysics;
        noPhysics = true;
        try {
            super.move(type, clipped);
        }
        finally {
            noPhysics = previousNoPhysics;
        }

        boolean clippedX = !Mth.equal(requested.x, clipped.x);
        boolean clippedY = !Mth.equal(requested.y, clipped.y);
        boolean clippedZ = !Mth.equal(requested.z, clipped.z);
        horizontalCollision = clippedX || clippedZ;
        verticalCollision = clippedY;
        verticalCollisionBelow = clippedY && requested.y < 0.0D;
        if (clippedY) {
            setOnGround(verticalCollisionBelow);
        }
        if (clippedX || clippedY || clippedZ) {
            Vec3 velocity = getDeltaMovement();
            setDeltaMovement(
                    clippedX ? 0.0D : velocity.x,
                    clippedY ? 0.0D : velocity.y,
                    clippedZ ? 0.0D : velocity.z);
        }
    }

    @Override
    public void updatePosition(LivingEntity user) {
        if (!level().isClientSide() && isBeingRetracted()) {
            clearPossession();
        }
        LivingEntity target = getPossessedTarget();
        if (target == null) {
            super.updatePosition(user);
            return;
        }

        Vec3 targetPos = target.position();
        double verticalOffset =
                Math.max(0.0D, target.getBbHeight() * 0.42D);
        setPos(
                targetPos.x,
                targetPos.y + verticalOffset,
                targetPos.z);
        setDeltaMovement(Vec3.ZERO);
        setYRot(target.getYRot());
        setXRot(target.getXRot());
        yBodyRot = target.yBodyRot;
    }

    @Override
    public boolean isFollowingUser() {
        return !isPossessing() && super.isFollowingUser();
    }

    @Override
    public boolean onActionSet(@Nullable EntityActionInstance action) {
        boolean previous = suppressPossessionAutoRetraction;
        // Finishing a host action is not an explicit request to leave the host.
        suppressPossessionAutoRetraction = previous
                || action == null && isPossessing();
        try {
            return super.onActionSet(action);
        }
        finally {
            suppressPossessionAutoRetraction = previous;
        }
    }

    @Override
    public void retract() {
        if (!suppressPossessionAutoRetraction) {
            super.retract();
        }
    }

    @Override
    public boolean isPickable() {
        return (getMaterialState() != MaterialState.VAPOR
                        || isPossessing())
                && super.isPickable();
    }

    @Override
    public boolean isInvisible() {
        LivingEntity target = getPossessedTarget();
        return super.isInvisible()
                || target != null && !isSkeletonHost(target);
    }

    @Override
    public boolean hurt(
            DamageSource damageSource, float amount) {
        LivingEntity target = getPossessedTarget();
        if (level().isClientSide()
                && ClientGlobals.canSeeStands
                && getMaterialState()
                        == MaterialState.LIQUID
                && (target == null
                        || isSkeletonHost(target))) {
            doWaterSplashEffect();
        }
        if (!damageSource.is(
                DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            if (target != null) {
                StandEntity attackingStand =
                        hostOwnedAttackingStand(
                                damageSource, target);
                float aquaMultiplier = 0.25F;
                if (attackingStand != null
                        && target.isAlive()
                        && damageSource.is(
                                ModDamageTypes.CAN_HURT_STANDS)) {
                    HostDamageMapping mapping =
                            mapHostOwnedDamage(
                                    damageSource,
                                    amount,
                                    target,
                                    attackingStand);
                    aquaMultiplier = mapping.aquaMultiplier();
                    if (!level().isClientSide()
                            && mapping.hostDamage() > 0.0F) {
                        DamageUtil.hurtThroughInvulTicks(
                                target,
                                damageSource,
                                mapping.hostDamage());
                    }
                }
                amount *= aquaMultiplier;
            }

            if (isMagiciansRedProjectile(damageSource)) {
                amount *= 0.5F;
            }

            amount *= switch (getMaterialState()) {
                case VAPOR -> 0.0F;
                case LIQUID -> 0.1F;
                case SOLID -> 1.0F;
            };
        }
        return amount > 0.0F
                && super.hurt(damageSource, amount);
    }

    @Override
    public boolean canBlockDamage(DamageSource damageSource) {
        return (!isPossessing() || isFluidGuarding())
                && super.canBlockDamage(damageSource);
    }

    // 1.16 attackEntity: every Aqua Necklace hit, host actions included
    @Override
    public boolean hurtWithStandAttack(
            Entity target, DamageSource dmgSource, float dmgAmount) {
        if (target instanceof LivingEntity living
                && living.isSensitiveToWater()) {
            dmgAmount *= WATER_SENSITIVE_MULTIPLIER;
        }
        return super.hurtWithStandAttack(target, dmgSource, dmgAmount);
    }

    // 1.16 attackEntity: a hit from inside the host ignores armor.
    // Only for a fresh punch source: just the Stand power is copied.
    public static DamageSource insideHostSource(DamageSource punch) {
        DamageSource inside = new DamageSource(
                new InsideHostType(punch.typeHolder()),
                punch.getDirectEntity(),
                punch.getEntity());
        if (punch instanceof DamageSourceModified from
                && inside instanceof DamageSourceModified to) {
            to.jojo_ripples$setStandPower(from.jojo_ripples$standPower());
        }
        return inside;
    }

    // the punch damage type plus the armor-bypass tags
    private record InsideHostType(Holder<DamageType> base)
            implements Holder<DamageType> {
        @Override
        public DamageType value() {
            return base.value();
        }

        @Override
        public boolean isBound() {
            return base.isBound();
        }

        @Override
        public boolean is(ResourceLocation location) {
            return base.is(location);
        }

        @Override
        public boolean is(ResourceKey<DamageType> key) {
            return base.is(key);
        }

        @Override
        public boolean is(Predicate<ResourceKey<DamageType>> predicate) {
            return base.is(predicate);
        }

        @Override
        public boolean is(TagKey<DamageType> tag) {
            return INSIDE_HOST_TAGS.contains(tag) || base.is(tag);
        }

        @Override
        public boolean is(Holder<DamageType> holder) {
            return base.is(holder);
        }

        @Override
        public Stream<TagKey<DamageType>> tags() {
            return Stream.concat(base.tags(), INSIDE_HOST_TAGS.stream())
                    .distinct();
        }

        @Override
        public Either<ResourceKey<DamageType>, DamageType> unwrap() {
            return base.unwrap();
        }

        @Override
        public Optional<ResourceKey<DamageType>> unwrapKey() {
            return base.unwrapKey();
        }

        @Override
        public Holder.Kind kind() {
            return base.kind();
        }

        @Override
        public boolean canSerializeIn(HolderOwner<DamageType> owner) {
            return base.canSerializeIn(owner);
        }

        @Override
        public HolderLookup.RegistryLookup<DamageType> unwrapLookup() {
            return base.unwrapLookup();
        }
    }

    @Override
    public boolean isStandBlocking() {
        return isFluidGuarding()
                || super.isStandBlocking();
    }

    @Override
    public void breakStandBlocking(int lockTicks) {
        super.breakStandBlocking(lockTicks);
        if (!level().isClientSide()) {
            setFluidGuarding(false);
        }
    }

    // 1.16 canBreakBlock: hardness <= 1 and at most a wooden tool tier
    private static final StandStatFormulas.BlockMiningTier BREAK_TIER =
            new StandStatFormulas.BlockMiningTier.VanillaTierWrapper(
                    Tiers.WOOD);

    @Override
    public float getBlockHardnessForStandBreak(
            BlockState blockState, Level level, BlockPos blockPos) {
        float hardness = blockState.getDestroySpeed(level, blockPos);
        if (!blockState.requiresCorrectToolForDrops()) {
            hardness *= 0.6F;
        }
        if (hardness < 0.0F || hardness > 1.0F
                || !BREAK_TIER.canMine(blockState)) {
            return -1.0F;
        }
        return super.getBlockHardnessForStandBreak(
                blockState, level, blockPos);
    }

    @Override
    public void onUnsummonUserInput() {
        if (!level().isClientSide()) {
            clearPossession();
            setFluidGuarding(false);
        }
        super.onUnsummonUserInput();
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!level().isClientSide()) {
            flushLiquidTraining();
            clearPossession();
            setFluidGuarding(false);
        }
        super.remove(reason);
    }

    public MaterialState getMaterialState() {
        return MaterialState.fromId(entityData.get(MATERIAL_STATE));
    }

    public void setMaterialState(MaterialState state) {
        if (!level().isClientSide() && getMaterialState() == MaterialState.LIQUID
                && state != MaterialState.LIQUID) {
            flushLiquidTraining();
        }
        entityData.set(
                MATERIAL_STATE,
                state != null
                        ? state.id()
                        : MaterialState.SOLID.id());
    }

    private void flushLiquidTraining() {
        StandPower power = getUserPower();
        if (power != null && power.getCurTypeData()
                instanceof AquaNecklaceTrainingData training) {
            training.flushLiquidTraining(power);
        }
    }

    public LivingEntity getPossessedTarget() {
        int targetId = entityData.get(POSSESSED_TARGET_ID);
        Entity target = targetId >= 0
                ? level().getEntity(targetId)
                : null;
        return target instanceof LivingEntity living
                ? living
                : null;
    }

    public boolean isPossessing() {
        return getPossessedTarget() != null;
    }

    public boolean attachTo(LivingEntity target) {
        LivingEntity owner = getUser();
        if (!canPossessTarget(owner, target)
                || isPossessing()) {
            return false;
        }
        entityData.set(POSSESSED_TARGET_ID, target.getId());
        return true;
    }

    public void clearPossession() {
        entityData.set(POSSESSED_TARGET_ID, -1);
    }

    public boolean isFluidGuarding() {
        return entityData.get(FLUID_GUARDING);
    }

    public void setFluidGuarding(boolean guarding) {
        entityData.set(FLUID_GUARDING, guarding);
    }

    public boolean canDrownPossessedTarget() {
        LivingEntity target = getPossessedTarget();
        return target != null
                && !JojoDefinitions.isAffectedByHamon(target)
                && !(target instanceof AbstractGolem);
    }

    public void drownPossessedTargetTick() {
        LivingEntity target = getPossessedTarget();
        if (target == null || !canDrownPossessedTarget()) {
            return;
        }

        boolean submergedOutsideBubble =
                target.isEyeInFluid(FluidTags.WATER)
                        && !target.level()
                                .getBlockState(BlockPos.containing(
                                        target.getX(), target.getEyeY(),
                                        target.getZ()))
                                .is(Blocks.BUBBLE_COLUMN);
        int reduction = submergedOutsideBubble ? 1 : 6;
        if (!submergedOutsideBubble
                && PlayerPower.getPowerData(
                                target,
                                ModPlayerPowers.HAMON)
                        .isPresent()) {
            reduction += 2;
        }
        target.setAirSupply(
                target.getAirSupply() - reduction);
        if (target.getAirSupply() <= -20) {
            target.setAirSupply(0);
            target.hurt(target.damageSources().drown(), 2.0F);
        }
    }

    public static boolean isSkeletonHost(LivingEntity target) {
        return target instanceof AbstractSkeleton
                || target instanceof SkeletonHorse
                || target instanceof WitherBoss;
    }

    public static boolean hasBrain(LivingEntity target) {
        return target != null
                && !isSkeletonHost(target)
                && !(target instanceof AbstractGolem);
    }

    public boolean canPossessTarget(
            LivingEntity owner, LivingEntity target) {
        return owner != null
                && target != null
                && owner.level() == target.level()
                && target != owner
                && !(target instanceof StandEntity)
                && target.isAlive()
                && !target.isRemoved()
                && owner.distanceToSqr(target)
                        <= getMaxRange() * getMaxRange();
    }

    private void applySmallHostPressure(LivingEntity target) {
        double standSize = getBoundingBox().getSize();
        double targetSize = target.getBoundingBox().getSize();
        if (targetSize > standSize * 1.5D) {
            return;
        }
        if (target.isSleeping()) {
            target.stopSleeping();
        }
        else {
            // 1.16 tick: Stand source, bypassArmor, no knockback,
            // max health through invul ticks
            DamageSource punch = new DamageSource(
                    DamageUtil.type(level(), ModDamageTypes.STAND_ATTACK),
                    this);
            ((DamageSourceModified) punch)
                    .jojo_ripples$setStandPower(getUserPower());
            // knockback set on the wrapped source: insideHostSource
            // copies only the Stand power
            DamageSource inside = insideHostSource(punch);
            ((DamageSourceModified) inside)
                    .jojo_ripples$modifyKnockback(0.0F, 0.0F);
            DamageUtil.hurtThroughInvulTicks(
                    target, inside, target.getMaxHealth());
        }
    }

    private HostDamageMapping mapHostOwnedDamage(
            DamageSource damageSource,
            float amount,
            LivingEntity host,
            StandEntity attackingStand) {
        ResourceLocation standId = attackingStand.getStandType();
        ResourceLocation actionId =
                currentAbilityTypeId(attackingStand);
        if (THE_HAND_STAND.equals(standId)
                && (THE_HAND_ERASE.equals(actionId)
                        || THE_HAND_ERASURE_BARRAGE.equals(
                                actionId))) {
            return new HostDamageMapping(
                    scaleTheHandErasureDamage(
                            amount,
                            host,
                            THE_HAND_ERASURE_BARRAGE.equals(
                                    actionId)),
                    1.0F);
        }

        Entity direct = damageSource.getDirectEntity();
        boolean silverChariotRapier =
                entityTypeId(direct).equals(
                        SILVER_CHARIOT_RAPIER);
        if (silverChariotRapier) {
            return new HostDamageMapping(
                    0.0F,
                    isSkeletonHost(host) ? 1.0F : 0.25F);
        }

        if (SILVER_CHARIOT_STAND.equals(standId)
                && isSkeletonHost(host)) {
            if (SILVER_CHARIOT_BARRAGE.equals(actionId)) {
                return new HostDamageMapping(
                        amount * 0.25F, 0.75F);
            }
            return new HostDamageMapping(0.0F, 0.25F);
        }

        return new HostDamageMapping(amount, 0.25F);
    }

    private float scaleTheHandErasureDamage(
            float amount,
            LivingEntity host,
            boolean barrage) {
        float aquaFactor =
                theHandTargetFactor(this, barrage);
        float hostFactor =
                theHandTargetFactor(host, barrage);
        float aquaBase = getMaxHealth() * aquaFactor;
        return aquaBase > 0.0F
                ? amount
                        * host.getMaxHealth()
                        * hostFactor
                        / aquaBase
                : amount;
    }

    private boolean touchesTraversableBlock(Vec3 movement) {
        AABB area = getBoundingBox().expandTowards(movement);
        BlockCollisions<Boolean> collisions =
                new BlockCollisions<>(
                        level(),
                        this,
                        area,
                        false,
                        (pos, shape) -> isTraversableBlock(
                                level().getBlockState(pos).getBlock()));
        while (collisions.hasNext()) {
            if (collisions.next()) {
                return true;
            }
        }
        return false;
    }

    private Vec3 clipLiquidMovement(Vec3 movement) {
        AABB box = getBoundingBox();
        AABB area = box.expandTowards(movement);
        List<VoxelShape> shapes = new ArrayList<>(
                level().getEntityCollisions(this, area));

        WorldBorder border = level().getWorldBorder();
        if (border.isInsideCloseToBorder(this, area)) {
            shapes.add(border.getCollisionShape());
        }

        BlockCollisions<VoxelShape> blockCollisions =
                new BlockCollisions<>(
                        level(),
                        this,
                        area,
                        false,
                        (pos, shape) -> isTraversableBlock(
                                level().getBlockState(pos).getBlock())
                                        ? Shapes.empty()
                                        : shape);
        while (blockCollisions.hasNext()) {
            VoxelShape shape = blockCollisions.next();
            if (!shape.isEmpty()) {
                shapes.add(shape);
            }
        }
        return collideWithShapes(movement, box, shapes);
    }

    private static Vec3 collideWithShapes(
            Vec3 movement,
            AABB box,
            List<VoxelShape> shapes) {
        double x = movement.x;
        double y = movement.y;
        double z = movement.z;
        if (y != 0.0D) {
            y = Shapes.collide(
                    net.minecraft.core.Direction.Axis.Y,
                    box,
                    shapes,
                    y);
            if (y != 0.0D) {
                box = box.move(0.0D, y, 0.0D);
            }
        }

        boolean zFirst = Math.abs(x) < Math.abs(z);
        if (zFirst && z != 0.0D) {
            z = Shapes.collide(
                    net.minecraft.core.Direction.Axis.Z,
                    box,
                    shapes,
                    z);
            if (z != 0.0D) {
                box = box.move(0.0D, 0.0D, z);
            }
        }
        if (x != 0.0D) {
            x = Shapes.collide(
                    net.minecraft.core.Direction.Axis.X,
                    box,
                    shapes,
                    x);
            if (!zFirst && x != 0.0D) {
                box = box.move(x, 0.0D, 0.0D);
            }
        }
        if (!zFirst && z != 0.0D) {
            z = Shapes.collide(
                    net.minecraft.core.Direction.Axis.Z,
                    box,
                    shapes,
                    z);
        }
        return new Vec3(x, y, z);
    }

    private static boolean isTraversableBlock(Block block) {
        return block instanceof DoorBlock
                || block instanceof TrapDoorBlock
                || block instanceof FenceBlock
                || block instanceof FenceGateBlock
                || block == Blocks.IRON_BARS
                || block instanceof EndRodBlock
                || block instanceof ChainBlock;
    }

    private static StandEntity hostOwnedAttackingStand(
            DamageSource damageSource,
            LivingEntity host) {
        Entity causing = damageSource.getEntity();
        if (causing instanceof StandEntity stand
                && stand.getUser() == host) {
            return stand;
        }
        Entity direct = damageSource.getDirectEntity();
        return direct instanceof StandEntity stand
                        && stand.getUser() == host
                ? stand
                : null;
    }

    private static ResourceLocation currentAbilityTypeId(
            StandEntity stand) {
        EntityActionInstance action =
                stand.getCurStandAction();
        return action != null
                        && action.ability instanceof Ability ability
                ? ability.abilityType.registryKey
                : null;
    }

    private static boolean isMagiciansRedProjectile(
            DamageSource damageSource) {
        ResourceLocation directId =
                entityTypeId(damageSource.getDirectEntity());
        return MAGICIANS_RED_PROJECTILES.contains(directId)
                && (damageSource.is(DamageTypeTags.IS_PROJECTILE)
                        || damageSource.is(
                                DamageTypeTags.IS_EXPLOSION));
    }

    private static ResourceLocation entityTypeId(Entity entity) {
        return entity != null
                ? BuiltInRegistries.ENTITY_TYPE.getKey(
                        entity.getType())
                : ResourceLocation.withDefaultNamespace(
                        "missing");
    }

    private static float theHandTargetFactor(
            LivingEntity entity, boolean barrage) {
        boolean large = Math.max(
                        entity.getBbWidth(),
                        entity.getBbHeight())
                > 1.5F;
        if (barrage) {
            return large ? 0.0071875F : 0.0115F;
        }
        return large ? 0.5F : 0.8F;
    }

    private static ResourceLocation id(
            String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(
                namespace, path);
    }

    private record HostDamageMapping(
            float hostDamage, float aquaMultiplier) {}

    public enum MaterialState {
        VAPOR(0),
        LIQUID(1),
        SOLID(2);

        private final int id;

        MaterialState(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }

        public static MaterialState fromId(int id) {
            return switch (id) {
                case 0 -> VAPOR;
                case 1 -> LIQUID;
                default -> SOLID;
            };
        }
    }
}
