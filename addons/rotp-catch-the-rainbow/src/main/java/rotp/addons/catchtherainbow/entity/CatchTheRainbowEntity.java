package rotp.addons.catchtherainbow.entity;

import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.catchtherainbow.CatchTheRainbowAddon;
import rotp.addons.catchtherainbow.CatchTheRainbowCleanup;
import rotp.addons.catchtherainbow.ability.TempHealthAbility;
import rotp.addons.catchtherainbow.item.CatchTheRainbowMaskItem;
import rotp.addons.catchtherainbow.util.RainExposure;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CatchTheRainbowEntity extends StandEntity {
    public static final float MERGE_STAMINA_PER_TICK = 4.0F;
    public static final float RAIN_WALK_STAMINA_PER_TICK = 1.0F;
    public static final float FALLING_BLADE_STAMINA = 20.0F;
    public static final double FALLING_BLADE_RADIUS = 25.0D;
    private static final EntityDimensions RENDER_DIMENSIONS =
            EntityDimensions.scalable(
                    CatchTheRainbowAddon.RENDER_WIDTH,
                    CatchTheRainbowAddon.RENDER_HEIGHT);

    private static final ResourceLocation ENTITY_REACH_MODIFIER =
            CatchTheRainbowAddon.id("rain_entity_reach");
    private static final ResourceLocation BLOCK_REACH_MODIFIER =
            CatchTheRainbowAddon.id("rain_block_reach");
    public static final ResourceLocation RAIN_WALK_GRAVITY_MODIFIER =
            CatchTheRainbowAddon.id("rain_walk_gravity");
    private static final AttributeModifier RAIN_WALK_NO_GRAVITY =
            new AttributeModifier(
                    RAIN_WALK_GRAVITY_MODIFIER,
                    -1.0D,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

    private static final EntityDataAccessor<Boolean>
            BLADE_RAIN_ACTIVE = SynchedEntityData.defineId(
                    CatchTheRainbowEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean>
            RAIN_WALK_ACTIVE = SynchedEntityData.defineId(
                    CatchTheRainbowEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> MERGE_ACTIVE =
            SynchedEntityData.defineId(
                    CatchTheRainbowEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean>
            TEMP_HEALTH_ACTIVE = SynchedEntityData.defineId(
                    CatchTheRainbowEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> PLAYER_MAX_HP =
            SynchedEntityData.defineId(
                    CatchTheRainbowEntity.class,
                    EntityDataSerializers.FLOAT);

    private int bladeRainTicks;
    private int rainStepTicks;

    public CatchTheRainbowEntity(
            EntityType<? extends CatchTheRainbowEntity> entityType,
            Level level) {
        super(entityType, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public boolean isBladeRainActive() {
        return entityData.get(BLADE_RAIN_ACTIVE);
    }

    public void setBladeRainActive(boolean active) {
        if (!level().isClientSide()) {
            entityData.set(BLADE_RAIN_ACTIVE, active);
            if (!active) {
                bladeRainTicks = 0;
            }
        }
    }

    public boolean isRainWalkingActive() {
        return entityData.get(RAIN_WALK_ACTIVE);
    }

    private void setRainWalkingActive(boolean active) {
        if (!level().isClientSide()) {
            entityData.set(RAIN_WALK_ACTIVE, active);
        }
    }

    public boolean isMergeActive() {
        return entityData.get(MERGE_ACTIVE);
    }

    public void setMergeActive(boolean active) {
        if (!level().isClientSide()) {
            entityData.set(MERGE_ACTIVE, active);
        }
    }

    public boolean isTempHealthActive() {
        return entityData.get(TEMP_HEALTH_ACTIVE);
    }

    public void setTempHealthActive(boolean active) {
        if (!level().isClientSide()) {
            entityData.set(TEMP_HEALTH_ACTIVE, active);
        }
    }

    public float getStoredMaxHealth() {
        return entityData.get(PLAYER_MAX_HP);
    }

    public void setStoredMaxHealth(float maxHealth) {
        if (!level().isClientSide()) {
            entityData.set(PLAYER_MAX_HP, maxHealth);
        }
    }

    @Override
    public void tick() {
        super.tick();
        noPhysics = true;
        setNoGravity(true);

        LivingEntity user = getUser();
        if (user == null || user.isRemoved() || !user.isAlive()) {
            if (!level().isClientSide()) {
                CatchTheRainbowCleanup.clearTransientState(user, this);
            }
            return;
        }

        followUser(user);
        if (level().isClientSide()) {
            return;
        }

        CatchTheRainbowMaskItem.enforceEquipped(user);
        StandPower power = getUserPower();
        boolean creative = CatchTheRainbowAddon.isCreative(user);
        boolean exposed = RainExposure.isExposedToRain(user);
        updateReach(user, exposed);

        if (!exposed && !creative) {
            clearDryWeatherState(user);
            return;
        }

        tickFallingBlades(user, power, creative);
        tickRainMerge(user, power, creative);
        tickRainStepSound(user);
        // Players are refilled from the player tick, as in 1.16, so an
        // unsummoned Stand does not stop it.
        if (!(user instanceof Player)) {
            tickRedemptionResolve(power, user);
        }
    }

    private void followUser(LivingEntity user) {
        setPos(user.getX(), user.getY(), user.getZ());
        setYRot(user.getYRot());
        setXRot(user.getXRot());
        yBodyRot = user.yBodyRot;
        yBodyRotO = user.yBodyRotO;
        yHeadRot = user.yHeadRot;
        yHeadRotO = user.yHeadRotO;
    }

    // 1.16 dry weather only took the Rain Merge effect off; Merge
    // itself stayed on and came back in the rain. Falling Rain Blades
    // spawned only inside the rain block, so they pause and stay on.
    private void clearDryWeatherState(LivingEntity user) {
        clearRainWalkingSupport(user);
        user.removeEffect(CatchTheRainbowAddon.RAIN_MERGE_EFFECT);
        TempHealthAbility.clear(user);
        if (user.hasEffect(
                CatchTheRainbowAddon.RAIN_REDEMPTION_EFFECT)) {
            user.removeEffect(
                    CatchTheRainbowAddon.RAIN_REDEMPTION_EFFECT);
        }
    }

    public void updateRainWalking(LivingEntity user) {
        if (level().isClientSide()
                || user == null
                || getUser() != user) {
            return;
        }
        StandPower power = getUserPower();
        boolean creative = CatchTheRainbowAddon.isCreative(user);
        BlockPos below = user.blockPosition().below();
        Vec3 movement = user.getDeltaMovement();
        boolean playerFlight = user instanceof Player player
                && (player.isSpectator()
                        || player.getAbilities().flying);
        boolean eligible = (creative
                    || RainExposure.isExposedToRain(user))
                && level().getBlockState(below).isAir()
                && !user.isShiftKeyDown()
                && !user.isInWaterOrBubble()
                && !user.isFallFlying()
                && !user.isPassenger()
                && !playerFlight
                && movement.y <= 0.0D;
        if (!eligible) {
            clearRainWalkingSupport(user);
            return;
        }
        if (!creative
                && (power == null
                        || !power.consumeStamina(
                                RAIN_WALK_STAMINA_PER_TICK))) {
            clearRainWalkingSupport(user);
            return;
        }
        AttributeInstance gravity =
                user.getAttribute(Attributes.GRAVITY);
        if (gravity == null) {
            clearRainWalkingSupport(user);
            return;
        }
        gravity.addOrUpdateTransientModifier(
                RAIN_WALK_NO_GRAVITY);
        setRainWalkingActive(true);
        user.setDeltaMovement(
                movement.x, 0.0D, movement.z);
        user.setOnGround(true);
        user.resetFallDistance();
        user.hasImpulse = true;
    }

    public void clearRainWalkingSupport(LivingEntity user) {
        boolean wasActive = isRainWalkingActive();
        setRainWalkingActive(false);
        clearRainWalking(user);
        if (wasActive
                && user != null
                && level().getBlockState(
                        user.blockPosition().below()).isAir()) {
            user.setOnGround(false);
        }
    }

    public static void clearRainWalking(LivingEntity user) {
        if (user == null) {
            return;
        }
        AttributeInstance gravity =
                user.getAttribute(Attributes.GRAVITY);
        if (gravity != null) {
            boolean hadSupport = gravity.hasModifier(
                    RAIN_WALK_GRAVITY_MODIFIER);
            gravity.removeModifier(RAIN_WALK_GRAVITY_MODIFIER);
            if (hadSupport
                    && user.level().getBlockState(
                            user.blockPosition().below()).isAir()) {
                user.setOnGround(false);
            }
        }
    }

    private void tickFallingBlades(
            LivingEntity user,
            StandPower power,
            boolean creative) {
        if (!isBladeRainActive()) {
            return;
        }
        if (++bladeRainTicks < bladeFrequency(power)) {
            return;
        }
        bladeRainTicks = 0;

        AABB area = user.getBoundingBox().inflate(
                FALLING_BLADE_RADIUS);
        for (LivingEntity target : level().getEntitiesOfClass(
                LivingEntity.class,
                area,
                candidate -> candidate != user
                        && candidate.isAlive()
                        && !(candidate instanceof StandEntity)
                        && RainExposure.isExposedToRain(candidate))) {
            // 1.16 SpawnBlade charged 20 without checking the result: at 0
            // stamina the blades keep falling and the toggle stays on.
            if (!creative && power != null) {
                power.consumeStamina(FALLING_BLADE_STAMINA);
            }
            int variant = getRandom().nextInt(3) + 1;
            RainBladeEntity blade = new RainBladeEntity(
                    user, level(), variant);
            blade.setPos(
                    target.getX(),
                    target.getY() + 5.0D,
                    target.getZ());
            blade.setDeltaMovement(0.0D, -1.0D, 0.0D);
            level().addFreshEntity(blade);
            level().playSound(
                    null,
                    target.blockPosition(),
                    CatchTheRainbowAddon.RAIN_BLADE_SOUND.get(),
                    user.getSoundSource(),
                    1.0F,
                    1.0F);
        }
    }

    private static int bladeFrequency(StandPower power) {
        if (power == null || power.getMaxStamina() <= 0.0F) {
            return 50;
        }
        float ratio = power.getStamina() / power.getMaxStamina();
        if (ratio >= 0.75F) {
            return 30;
        }
        return ratio >= 0.5F ? 40 : 50;
    }

    private void tickRainMerge(
            LivingEntity user,
            StandPower power,
            boolean creative) {
        if (!isMergeActive()) {
            user.removeEffect(
                    CatchTheRainbowAddon.RAIN_MERGE_EFFECT);
            return;
        }
        if (user.hasEffect(
                    CatchTheRainbowAddon.RAIN_REDEMPTION_EFFECT)
                || isTempHealthActive()) {
            setMergeActive(false);
            user.removeEffect(
                    CatchTheRainbowAddon.RAIN_MERGE_EFFECT);
            return;
        }
        // 1.16 drained 4 stamina a tick but never ended Merge for the
        // lack of it, and kept the effect's icon hidden.
        if (!creative && power != null) {
            power.consumeStamina(MERGE_STAMINA_PER_TICK);
        }

        user.addEffect(new MobEffectInstance(
                CatchTheRainbowAddon.RAIN_MERGE_EFFECT,
                40,
                0,
                false,
                false,
                false));
        Vec3 motion = user.getDeltaMovement();
        user.setDeltaMovement(
                0.0D, Math.min(0.0D, motion.y), 0.0D);
        user.setSprinting(false);
        user.resetFallDistance();
        user.hasImpulse = true;
    }

    private void tickRainStepSound(LivingEntity user) {
        if (!isRainWalkingActive()
                || user.getDeltaMovement()
                        .horizontalDistanceSqr() < 1.0E-4D) {
            rainStepTicks = 0;
            return;
        }
        int interval = isMergeActive() ? 12 : 6;
        if (++rainStepTicks >= interval) {
            rainStepTicks = 0;
            level().playSound(
                    null,
                    user.blockPosition(),
                    CatchTheRainbowAddon.RAIN_STEP_SOUND.get(),
                    user.getSoundSource(),
                    1.0F,
                    1.0F);
        }
    }

    public static void tickRedemptionResolve(
            StandPower power, LivingEntity user) {
        if (power == null
                || !user.hasEffect(
                        CatchTheRainbowAddon
                                .RAIN_REDEMPTION_EFFECT)) {
            return;
        }
        float maximum = power.getResolveCounter()
                .getMaxResolveValue(power);
        power.getResolveCounter().addResolveValue(
                power, maximum);
    }

    private static void updateReach(
            LivingEntity user, boolean enabled) {
        updateReach(
                user,
                Attributes.ENTITY_INTERACTION_RANGE,
                ENTITY_REACH_MODIFIER,
                enabled);
        updateReach(
                user,
                Attributes.BLOCK_INTERACTION_RANGE,
                BLOCK_REACH_MODIFIER,
                enabled);
    }

    private static void updateReach(
            LivingEntity user,
            net.minecraft.core.Holder<
                    net.minecraft.world.entity.ai.attributes.Attribute>
                    attribute,
            ResourceLocation id,
            boolean enabled) {
        AttributeInstance instance = user.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        if (enabled) {
            instance.addOrUpdateTransientModifier(
                    new AttributeModifier(
                            id,
                            3.0D,
                            AttributeModifier.Operation
                                    .ADD_MULTIPLIED_BASE));
        }
        else {
            instance.removeModifier(id);
        }
    }

    public static void clearReach(LivingEntity user) {
        if (user != null) {
            updateReach(user, false);
        }
    }

    public static boolean hasTransientReach(LivingEntity user) {
        return hasModifier(
                        user,
                        Attributes.ENTITY_INTERACTION_RANGE,
                        ENTITY_REACH_MODIFIER)
                || hasModifier(
                        user,
                        Attributes.BLOCK_INTERACTION_RANGE,
                        BLOCK_REACH_MODIFIER);
    }

    public static boolean hasRainWalkingSupport(LivingEntity user) {
        return hasModifier(
                user,
                Attributes.GRAVITY,
                RAIN_WALK_GRAVITY_MODIFIER);
    }

    private static boolean hasModifier(
            LivingEntity user,
            net.minecraft.core.Holder<
                    net.minecraft.world.entity.ai.attributes.Attribute>
                    attribute,
            ResourceLocation modifierId) {
        AttributeInstance instance = user != null
                ? user.getAttribute(attribute) : null;
        return instance != null && instance.hasModifier(modifierId);
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BLADE_RAIN_ACTIVE, false);
        builder.define(RAIN_WALK_ACTIVE, false);
        builder.define(MERGE_ACTIVE, false);
        builder.define(TEMP_HEALTH_ACTIVE, false);
        builder.define(PLAYER_MAX_HP, 20.0F);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    // The 0x0 hitbox would also cull the body (render distance 0), which
    // the port draws; rendering keeps the 0.6x1.8 box.
    @Override
    public AABB getBoundingBoxForCulling() {
        return RENDER_DIMENSIONS.makeBoundingBox(position());
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double size = getBoundingBoxForCulling().getSize()
                * 64.0D * getViewScale();
        return distance < size * size;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return false;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide()) {
            CatchTheRainbowCleanup.onControllerRemoved(getUser(), this);
        }
        super.remove(reason);
    }
}
