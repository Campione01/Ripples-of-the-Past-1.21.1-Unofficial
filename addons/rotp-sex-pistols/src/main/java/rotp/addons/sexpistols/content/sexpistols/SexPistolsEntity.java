package rotp.addons.sexpistols.content.sexpistols;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.init.ModEntityDataSerializers;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandLinkDamageSource;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.util.functions.DamageUtil;

import rotp.addons.sexpistols.network.RevolverVisualPayloads;
import rotp.addons.sexpistols.registry.SexPistolsAttachments;
import rotp.addons.sexpistols.registry.SexPistolsEntities;
import rotp.addons.sexpistols.registry.SexPistolsSounds;
import rotp.addons.sexpistols.registry.SexPistolsStands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class SexPistolsEntity extends StandEntity {
    public static final double COMMAND_RANGE = 10.0D;
    public static final double MOVE_SPEED = 0.36D;
    public static final int ORPHAN_GRACE_TICKS = 100;
    public static final int KICK_DURATION_TICKS = 8;
    public static final int REDIRECT_COOLDOWN_TICKS = 6;
    public static final int EAT_ANIMATION_TICKS = 54;
    public static final int SUMMON_ANIMATION_TICKS = 40;
    public static final String ITEM_RETRIEVED_TAG =
            "rotp_sex_pistols_retrieved";

    private static final EntityDataAccessor<Integer> OWNER_ENTITY_ID =
            SynchedEntityData.defineId(
                    SexPistolsEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> MEMBER_STATE =
            SynchedEntityData.defineId(
                    SexPistolsEntity.class,
                    EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> KICK_TICKS =
            SynchedEntityData.defineId(
                    SexPistolsEntity.class,
                    EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> KICK_VARIANT =
            SynchedEntityData.defineId(
                    SexPistolsEntity.class,
                    EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> EATING_TICKS =
            SynchedEntityData.defineId(
                    SexPistolsEntity.class,
                    EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> EATING_VARIANT =
            SynchedEntityData.defineId(
                    SexPistolsEntity.class,
                    EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> FOOD_BEGGING =
            SynchedEntityData.defineId(
                    SexPistolsEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte> SUMMON_TICKS =
            SynchedEntityData.defineId(
                    SexPistolsEntity.class,
                    EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> SUMMON_VARIANT =
            SynchedEntityData.defineId(
                    SexPistolsEntity.class,
                    EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Optional<ResourceLocation>>
            SELECTED_SKIN = SynchedEntityData.defineId(
                    SexPistolsEntity.class,
                    ModEntityDataSerializers
                            .RESOURCE_LOCATION_OPTIONAL.get());
    // The idle offset from the user's feet; the client follows with it too.
    private static final EntityDataAccessor<Vector3f> IDLE_OFFSET =
            SynchedEntityData.defineId(
                    SexPistolsEntity.class,
                    EntityDataSerializers.VECTOR3);
    // 1.16 synced the carried item: the move's HUD name names it.
    private static final EntityDataAccessor<ItemStack> CARRIED_ITEM =
            SynchedEntityData.defineId(
                    SexPistolsEntity.class,
                    EntityDataSerializers.ITEM_STACK);

    @Nullable
    private UUID ownerUuid;
    private Vec3 commandTarget = Vec3.ZERO;
    private int orphanTicks;
    private int redirectCooldown;
    private boolean returnAfterKick;
    @Nullable
    private UUID beggingPlayerUuid;
    @Nullable
    private UUID itemPickupTargetUuid;
    private ItemStack eatingItem = ItemStack.EMPTY;
    private int beggingVoiceCooldown;
    private int eatingParticleTicks;
    private boolean deliveredCarriedItem;

    public SexPistolsEntity(
            EntityType<? extends SexPistolsEntity> type,
            Level level) {
        super(type, level);
        withStandType(SexPistolsStands.SEX_PISTOLS.get());
        healthLinkedWithUser = false;
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(6.0D);
        setHealth(6.0F);
        setIdleOffset(slotIdleOffset(
                SexPistolsEntities.slotForNumber(getMemberNumber())));
    }

    // 1.16 SexPistolsEntities.getDefaultOffset: the six pistols on a 1.05
    // circle in three layers (a released, recalled or food-begging pistol).
    public static Vec3 slotIdleOffset(int slot) {
        int index = Mth.clamp(slot, 0, 5);
        double angle = Math.PI * 2.0D * index / 6.0D;
        return new Vec3(
                Math.cos(angle) * 1.05D,
                0.15D + index % 3 * 0.575D,
                Math.sin(angle) * 1.05D);
    }

    // 1.16 SexPistolsStandType summon: a random angle, radius and height
    // around each pistol's place.
    public static Vec3 summonIdleOffset(
            int slot, double baseAngle, RandomSource random) {
        int index = Mth.clamp(slot, 0, 5);
        double angle = baseAngle + Math.PI * 2.0D * index / 6.0D
                + (random.nextDouble() - 0.5D) * 0.45D;
        double radius = 0.85D + random.nextDouble() * 0.55D;
        double layer = index % 3 / 2.0D;
        return new Vec3(
                Math.cos(angle) * radius,
                0.15D + layer * 1.15D
                        + (random.nextDouble() - 0.5D) * 0.35D,
                Math.sin(angle) * radius);
    }

    public Vec3 getIdleOffset() {
        return new Vec3(entityData.get(IDLE_OFFSET));
    }

    public void setIdleOffset(Vec3 feetOffset) {
        entityData.set(IDLE_OFFSET, feetOffset.toVector3f());
        applyIdleOffset();
    }

    // 1.16 put the pistol's feet at the offset, turned with the view yaw;
    // its y was also the pistol's default y of task offsets without their
    // own.
    private void applyIdleOffset() {
        Vec3 feetOffset = getIdleOffset();
        boolean wasIdle = offsetFromUser.isIdle();
        Y_OFFSET = feetOffset.y;
        offsetFromUser.idleOffset = feetOffset;
        offsetFromUser.idleRotations = StandOffsetFromUser.Rotations.HEAD;
        if (wasIdle) {
            offsetFromUser.resetToIdle();
        }
    }

    // 1.16 kept only idle pistols on the user offset; the swarm flies the
    // others itself.
    @Override
    public boolean isFollowingUser() {
        return getMemberState() == SexPistolsMemberState.FORMATION
                && !isFoodBegging()
                && getEatingTicks() <= 0
                && super.isFollowingUser();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        float healthBefore = getHealth();
        boolean damaged = super.hurt(source, amount);
        if (!damaged
                || !(level() instanceof ServerLevel level)
                || source instanceof StandLinkDamageSource linkedDamage
                        && linkedDamage.standEntity
                                instanceof SexPistolsEntity) {
            return damaged;
        }
        float actualDamage = Math.max(
                0.0F, healthBefore - getHealth());
        LivingEntity owner = getSwarmOwner();
        if (actualDamage > 0.0F
                && owner != null
                && owner.isAlive()) {
            DamageUtil.hurtThroughInvulTicks(
                    owner,
                    new StandLinkDamageSource(
                            level, this, source),
                    actualDamage);
        }
        return damaged;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER_ENTITY_ID, -1);
        builder.define(
                MEMBER_STATE,
                (byte) SexPistolsMemberState.FORMATION.ordinal());
        builder.define(KICK_TICKS, (byte) 0);
        builder.define(KICK_VARIANT, (byte) 1);
        builder.define(EATING_TICKS, (byte) 0);
        builder.define(EATING_VARIANT, (byte) 1);
        builder.define(FOOD_BEGGING, false);
        builder.define(SUMMON_TICKS, (byte) 0);
        builder.define(SUMMON_VARIANT, (byte) 1);
        builder.define(SELECTED_SKIN, Optional.empty());
        builder.define(IDLE_OFFSET, new Vector3f());
        builder.define(CARRIED_ITEM, ItemStack.EMPTY);
    }

    public int getMemberNumber() {
        return SexPistolsEntities.numberFor(getType());
    }

    public SexPistolsMemberState getMemberState() {
        return SexPistolsMemberState.byId(
                entityData.get(MEMBER_STATE));
    }

    public void setMemberState(SexPistolsMemberState state) {
        entityData.set(MEMBER_STATE, (byte) state.ordinal());
    }

    public void setCommandTarget(Vec3 target) {
        commandTarget = target != null ? target : position();
    }

    public Vec3 getCommandTarget() {
        return commandTarget;
    }

    public int getKickTicks() {
        return Byte.toUnsignedInt(entityData.get(KICK_TICKS));
    }

    public int getKickVariant() {
        return Byte.toUnsignedInt(entityData.get(KICK_VARIANT));
    }

    public int getEatingTicks() {
        return Byte.toUnsignedInt(entityData.get(EATING_TICKS));
    }

    public int getEatingVariant() {
        return Byte.toUnsignedInt(entityData.get(EATING_VARIANT));
    }

    public int getSummonTicks() {
        return Byte.toUnsignedInt(entityData.get(SUMMON_TICKS));
    }

    public int getSummonVariant() {
        return Byte.toUnsignedInt(entityData.get(SUMMON_VARIANT));
    }

    public boolean isFoodBegging() {
        return entityData.get(FOOD_BEGGING);
    }

    public boolean hasCarriedItem() {
        return !getCarriedItem().isEmpty();
    }

    public ItemStack getCarriedItem() {
        return entityData.get(CARRIED_ITEM);
    }

    private void setCarriedItem(ItemStack stack) {
        entityData.set(CARRIED_ITEM, stack);
    }

    public boolean isOwnedBy(LivingEntity owner) {
        return owner != null
                && ownerUuid != null
                && ownerUuid.equals(owner.getUUID())
                && owner.hasData(
                        SexPistolsAttachments
                                .SEX_PISTOLS_COORDINATOR)
                && SexPistolsAttachments.coordinator(owner).owns(
                        getMemberNumber(), getUUID());
    }

    public boolean canRedirectBullet() {
        return isAlive()
                && getMemberState()
                        == SexPistolsMemberState.SCOUTING
                && redirectCooldown <= 0;
    }

    public void performKick(Vec3 direction) {
        performKickInternal(direction, false, false, false);
    }

    public void performKick(
            Vec3 direction, boolean returnAfterKick) {
        LivingEntity owner = getSwarmOwner();
        boolean critical = owner != null
                && owner.getRandom().nextFloat()
                        < SexPistolsJoyful.criticalChance(owner);
        performKickInternal(
                direction, returnAfterKick, true, critical);
    }

    public void performKick(
            Vec3 direction,
            boolean returnAfterKick,
            boolean critical) {
        performKickInternal(
                direction, returnAfterKick, true, critical);
    }

    private void performKickInternal(
            Vec3 direction,
            boolean returnAfterKick,
            boolean bulletAnchor,
            boolean critical) {
        if (level().isClientSide()) {
            return;
        }
        redirectCooldown = REDIRECT_COOLDOWN_TICKS;
        entityData.set(
                KICK_TICKS,
                (byte) KICK_DURATION_TICKS);
        entityData.set(
                KICK_VARIANT,
                (byte) (random.nextInt(4) + 1));
        this.returnAfterKick = returnAfterKick;
        if (returnAfterKick) {
            setCommandTarget(position());
            setMemberState(
                    SexPistolsMemberState.SCOUTING);
        }
        if (direction != null
                && direction.lengthSqr() > 1.0E-7D) {
            double horizontal = Math.sqrt(
                    direction.x * direction.x
                            + direction.z * direction.z);
            setYRot((float) (Mth.atan2(
                            direction.z,
                            direction.x)
                    * Mth.RAD_TO_DEG) - 90.0F);
            setYBodyRot(getYRot());
            setYHeadRot(getYRot());
            setXRot((float) (-(Mth.atan2(
                            direction.y,
                            horizontal)
                    * Mth.RAD_TO_DEG)));
        }
        level().playSound(
                null,
                getX(),
                getY(),
                getZ(),
                SexPistolsSounds.randomRicochet(random),
                getSoundSource(),
                0.8F,
                0.95F + random.nextFloat() * 0.1F);
        RevolverVisualPayloads.sendKickFlash(
                this,
                bulletAnchor,
                random.nextFloat(),
                critical);
    }

    public void bindOwner(LivingEntity owner) {
        boolean newlyBound = ownerUuid == null
                || !ownerUuid.equals(owner.getUUID());
        ownerUuid = owner.getUUID();
        StandPower power = StandPower.get(owner);
        if (power != null) {
            setUserAndPower(owner, power);
        }
        if (!level().isClientSide()) {
            entityData.set(OWNER_ENTITY_ID, owner.getId());
        }
        orphanTicks = 0;
        if (newlyBound) {
            playSummonAnimation();
        }
    }

    public void playSummonAnimation() {
        if (level().isClientSide()) {
            return;
        }
        int[] variants = {
            1, 2, 3, 4, 5, 6, 7, 8,
            9, 10, 11, 12, 14, 15, 16
        };
        entityData.set(
                SUMMON_VARIANT,
                (byte) variants[random.nextInt(variants.length)]);
        entityData.set(
                SUMMON_TICKS, (byte) SUMMON_ANIMATION_TICKS);
    }

    @Nullable
    public LivingEntity getSwarmOwner() {
        Entity byId = level().getEntity(entityData.get(OWNER_ENTITY_ID));
        if (byId instanceof LivingEntity living) {
            return living;
        }
        if (ownerUuid != null && level() instanceof ServerLevel serverLevel) {
            Entity byUuid = serverLevel.getEntity(ownerUuid);
            if (byUuid instanceof LivingEntity living) {
                return living;
            }
        }
        return null;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel)) {
            return;
        }

        if (redirectCooldown > 0) {
            redirectCooldown--;
        }
        tickAnimationCounters();
        int kickTicks = getKickTicks();
        if (kickTicks > 0) {
            int nextKickTicks = kickTicks - 1;
            entityData.set(
                    KICK_TICKS,
                    (byte) nextKickTicks);
            if (nextKickTicks == 0 && returnAfterKick) {
                returnAfterKick = false;
                setMemberState(
                        SexPistolsMemberState.FORMATION);
            }
        }

        LivingEntity owner = getSwarmOwner();
        if (!hasValidOwner(owner)) {
            if (++orphanTicks >= ORPHAN_GRACE_TICKS) {
                discard();
            }
            return;
        }
        orphanTicks = 0;
        refreshJoyfulAttributes(owner);

        if (tickEating(owner) || tickFoodBegging(owner)) {
            return;
        }
        if (tickItemRetrieval(owner)) {
            return;
        }

        SexPistolsMemberState state = getMemberState();
        Vec3 target = switch (state) {
            case FORMATION -> idlePosition(owner);
            case RECALLING -> owner.position().add(
                    0.0D, owner.getBbHeight() * 0.65D, 0.0D);
            case SCOUTING, ENCIRCLEMENT -> commandTarget;
        };

        if (state == SexPistolsMemberState.SCOUTING) {
            // 1.16 sent no fixed pistol back for distance: StandEntity.move
            // kept it within the Stand's max range of its user, dragged
            // along, and it stayed where that left it.
            commandTarget = withinMaxRange(owner, commandTarget);
            target = commandTarget;
        }
        if (isFollowingUser()) {
            // the core user offset has placed it this tick
            return;
        }
        moveToward(target);
    }

    private Vec3 withinMaxRange(LivingEntity owner, Vec3 point) {
        Vec3 fromOwner = point.subtract(owner.position());
        double distance = fromOwner.length();
        double range = getMaxRange();
        return distance > range
                ? owner.position().add(fromOwner.scale(range / distance))
                : point;
    }

    private Vec3 idlePosition(LivingEntity owner) {
        Vec3 offset = StandOffsetFromUser.relativeToAbsolute(
                offsetFromUser.idleOffset,
                offsetFromUser.idleRotations,
                owner,
                getUserBroadcastedSettings());
        return StandOffsetFromUser.AlignBy.BOTTOM.align(
                owner, this, offset);
    }

    private boolean hasValidOwner(@Nullable LivingEntity owner) {
        return owner != null
                && owner.isAlive()
                && owner.hasData(
                        SexPistolsAttachments
                                .SEX_PISTOLS_COORDINATOR)
                && SexPistolsAttachments.coordinator(owner).owns(
                        getMemberNumber(), getUUID());
    }

    // 1.16 moveToCommandPosition: placed on the point, facing the way it went.
    public void moveToCommandPosition(Vec3 target) {
        Vec3 movement = target.subtract(position());
        setCommandTarget(target);
        setPos(target);
        setDeltaMovement(Vec3.ZERO);
        if (movement.lengthSqr() > 1.0E-7D) {
            setYRot((float) (Mth.atan2(movement.z, movement.x)
                    * Mth.RAD_TO_DEG) - 90.0F);
            setYBodyRot(getYRot());
        }
    }

    // 1.16 setManualControl(false, true): fixed where it is until recalled.
    public void holdCommandPosition() {
        setDeltaMovement(Vec3.ZERO);
        setCommandTarget(position());
        setMemberState(SexPistolsMemberState.SCOUTING);
    }

    private void moveToward(Vec3 target) {
        Vec3 offset = target.subtract(position());
        double distance = offset.length();
        if (distance <= 1.0E-4D) {
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        Vec3 movement = offset.scale(
                Math.min(
                        MOVE_SPEED * movementMultiplier(),
                        distance) / distance);
        setPos(position().add(movement));
        setDeltaMovement(Vec3.ZERO);
        setYRot((float) (Mth.atan2(movement.z, movement.x)
                * Mth.RAD_TO_DEG) - 90.0F);
        setYBodyRot(getYRot());
    }

    public boolean canBeFedBy(
            Player player, InteractionHand hand) {
        LivingEntity owner = getSwarmOwner();
        return player != null
                && player.isAlive()
                && owner == player
                && owner.hasData(
                        SexPistolsAttachments
                                .SEX_PISTOLS_COORDINATOR)
                && SexPistolsAttachments.coordinator(owner)
                        .isHungry()
                && getEatingTicks() <= 0
                && distanceToSqr(player) <= 81.0D
                && player.getItemInHand(hand)
                        .getFoodProperties(player) != null;
    }

    public boolean tryFeedFrom(
            Player player, InteractionHand hand) {
        if (!canBeFedBy(player, hand)
                || !(level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        ItemStack stack = player.getItemInHand(hand);
        FoodProperties food = stack.getFoodProperties(player);
        if (food == null) {
            return false;
        }
        ItemStack eating = stack.copyWithCount(1);
        LivingEntity owner = getSwarmOwner();
        if (!SexPistolsAttachments.coordinator(owner)
                .feedSwarm(
                        serverLevel,
                        owner,
                        player,
                        eating)) {
            return false;
        }
        SexPistolsJoyful.addFromFood(
                owner, food.nutrition(), food.saturation());
        consumeFood(player, hand, stack);
        serverLevel.sendParticles(
                ParticleTypes.HEART,
                getX(),
                getY() + getBbHeight() * 0.65D,
                getZ(),
                4,
                0.2D,
                0.15D,
                0.2D,
                0.02D);
        return true;
    }

    public void startSharedEating(
            Player player, ItemStack food) {
        if (level().isClientSide()
                || player == null
                || food == null
                || food.isEmpty()) {
            return;
        }
        beggingPlayerUuid = player.getUUID();
        eatingItem = food.copyWithCount(1);
        entityData.set(
                EATING_TICKS, (byte) EAT_ANIMATION_TICKS);
        entityData.set(
                EATING_VARIANT,
                (byte) (random.nextInt(5) + 1));
        entityData.set(FOOD_BEGGING, true);
        setItemSlot(EquipmentSlot.MAINHAND, eatingItem.copy());
        setDeltaMovement(Vec3.ZERO);
    }

    public void startRetrievingItem(ItemEntity itemEntity) {
        if (level().isClientSide()
                || itemEntity == null
                || !itemEntity.isAlive()
                || itemEntity.getItem().isEmpty()
                || hasCarriedItem()) {
            return;
        }
        itemPickupTargetUuid = itemEntity.getUUID();
        itemEntity.getPersistentData()
                .putBoolean(ITEM_RETRIEVED_TAG, true);
        setMemberState(SexPistolsMemberState.SCOUTING);
    }

    public boolean deliverCarriedItem() {
        if (!giveCarriedItemToOwner()) {
            return false;
        }
        setMemberState(SexPistolsMemberState.FORMATION);
        return true;
    }

    public boolean isRetrievingItem() {
        return itemPickupTargetUuid != null;
    }

    public void cancelItemRetrieval() {
        if (level() instanceof ServerLevel serverLevel
                && itemPickupTargetUuid != null
                && serverLevel.getEntity(itemPickupTargetUuid)
                        instanceof ItemEntity itemEntity) {
            itemEntity.getPersistentData().remove(
                    ITEM_RETRIEVED_TAG);
        }
        itemPickupTargetUuid = null;
        if (!hasCarriedItem()) {
            setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
    }

    private boolean tickItemRetrieval(LivingEntity owner) {
        if (itemPickupTargetUuid == null || hasCarriedItem()) {
            return false;
        }
        if (!(level() instanceof ServerLevel serverLevel)) {
            itemPickupTargetUuid = null;
            return false;
        }
        Entity target = serverLevel.getEntity(itemPickupTargetUuid);
        if (!(target instanceof ItemEntity item)
                || !item.isAlive()
                || item.getItem().isEmpty()) {
            if (target instanceof ItemEntity item) {
                item.getPersistentData()
                        .putBoolean(ITEM_RETRIEVED_TAG, false);
            }
            itemPickupTargetUuid = null;
            setMemberState(SexPistolsMemberState.FORMATION);
            return false;
        }
        Vec3 pickup = item.position().add(
                0.0D,
                Math.min(0.35D, item.getBbHeight() * 0.5D),
                0.0D);
        if (distanceToSqr(pickup) <= 0.45D * 0.45D) {
            setCarriedItem(item.getItem().copy());
            deliveredCarriedItem = false;
            itemPickupTargetUuid = null;
            item.discard();
            setItemSlot(
                    EquipmentSlot.MAINHAND, getCarriedItem().copy());
            setCommandTarget(owner.position());
            setMemberState(SexPistolsMemberState.FORMATION);
            return false;
        }
        moveToward(pickup);
        return true;
    }

    private boolean tickEating(LivingEntity owner) {
        int ticks = getEatingTicks();
        if (ticks <= 0) {
            return false;
        }
        Player feeder = beggingPlayerUuid != null
                ? level().getPlayerByUUID(beggingPlayerUuid)
                : null;
        if (feeder != null) {
            moveToward(beggingPosition(feeder));
        }
        if (++eatingParticleTicks % 5 == 0
                && level() instanceof ServerLevel serverLevel
                && !eatingItem.isEmpty()) {
            serverLevel.sendParticles(
                    new ItemParticleOption(
                            ParticleTypes.ITEM, eatingItem),
                    getX(),
                    getY() + getBbHeight() * 0.62D,
                    getZ(),
                    6,
                    0.12D,
                    0.08D,
                    0.12D,
                    0.025D);
        }
        return true;
    }

    private boolean tickFoodBegging(LivingEntity owner) {
        if (!owner.hasData(
                    SexPistolsAttachments
                            .SEX_PISTOLS_COORDINATOR)
                || !SexPistolsAttachments.coordinator(owner)
                        .isHungry()) {
            entityData.set(FOOD_BEGGING, false);
            beggingPlayerUuid = null;
            return false;
        }
        Player feeder = findFoodHoldingPlayer(owner);
        if (feeder == null) {
            entityData.set(FOOD_BEGGING, false);
            beggingPlayerUuid = null;
            return false;
        }
        beggingPlayerUuid = feeder.getUUID();
        entityData.set(FOOD_BEGGING, true);
        moveToward(beggingPosition(feeder));
        if (beggingVoiceCooldown > 0) {
            beggingVoiceCooldown--;
        }
        else {
            playSound(
                    SexPistolsSounds.SEX_PISTOLS_HUNGRY.get(),
                    0.7F,
                    0.95F + random.nextFloat() * 0.12F);
            beggingVoiceCooldown = 80 + random.nextInt(91);
        }
        return true;
    }

    @Nullable
    private Player findFoodHoldingPlayer(LivingEntity owner) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return serverLevel.getEntitiesOfClass(
                        Player.class,
                        getBoundingBox().inflate(9.0D),
                        player -> player == owner
                                && player.isAlive()
                                && (player.getMainHandItem()
                                                .getFoodProperties(player)
                                        != null
                                        || player.getOffhandItem()
                                                .getFoodProperties(player)
                                        != null))
                .stream()
                .min(java.util.Comparator.comparingDouble(
                        this::distanceToSqr))
                .orElse(null);
    }

    private Vec3 beggingPosition(Player player) {
        Vec3 look = player.getLookAngle();
        Vec3 forward = new Vec3(look.x, 0.0D, look.z);
        if (forward.lengthSqr() < 1.0E-6D) {
            forward = new Vec3(0.0D, 0.0D, 1.0D);
        }
        forward = forward.normalize();
        Vec3 side = new Vec3(-forward.z, 0.0D, forward.x);
        int slot = Math.max(
                0,
                SexPistolsEntities.slotForNumber(
                        getMemberNumber()));
        double sideOffset = (Math.floorMod(slot, 3) - 1.0D)
                * 0.34D;
        double rowOffset = slot >= 3 ? 0.22D : 0.0D;
        double height = player.getBbHeight() * 0.52D
                + slot % 2 * 0.08D;
        return player.position()
                .add(forward.scale(1.15D + rowOffset))
                .add(side.scale(sideOffset))
                .add(0.0D, height, 0.0D);
    }

    private void tickAnimationCounters() {
        int summon = getSummonTicks();
        if (summon > 0) {
            entityData.set(SUMMON_TICKS, (byte) (summon - 1));
        }
        int eating = getEatingTicks();
        if (eating <= 0) {
            return;
        }
        int next = eating - 1;
        entityData.set(EATING_TICKS, (byte) next);
        if (next == 0) {
            eatingItem = ItemStack.EMPTY;
            eatingParticleTicks = 0;
            entityData.set(FOOD_BEGGING, false);
            setItemSlot(
                    EquipmentSlot.MAINHAND,
                    getCarriedItem().isEmpty()
                            ? ItemStack.EMPTY
                            : getCarriedItem().copy());
        }
    }

    private void refreshJoyfulAttributes(LivingEntity owner) {
        var maxHealth = getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            double oldMaximum = getMaxHealth();
            double healthRatio = oldMaximum > 0.0D
                    ? getHealth() / oldMaximum
                    : 1.0D;
            double newMaximum = Math.max(
                    1.0D,
                    owner.getMaxHealth() / 6.0D
                            * SexPistolsJoyful
                                    .healthMultiplier(owner));
            if (Math.abs(oldMaximum - newMaximum) > 1.0E-4D) {
                maxHealth.setBaseValue(newMaximum);
                setHealth((float) Mth.clamp(
                        newMaximum * healthRatio,
                        0.0D,
                        newMaximum));
            }
            else if (getHealth() > newMaximum) {
                setHealth((float) newMaximum);
            }
        }
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(
                    0.3D
                            * SexPistolsJoyful
                                    .movementMultiplier(owner));
        }
    }

    private double movementMultiplier() {
        LivingEntity owner = getSwarmOwner();
        return owner != null
                ? SexPistolsJoyful.movementMultiplier(owner)
                : 1.0D;
    }

    private static void consumeFood(
            Player player,
            InteractionHand hand,
            ItemStack stack) {
        if (player.getAbilities().instabuild) {
            return;
        }
        ItemStack remainder = stack.getItem()
                        .hasCraftingRemainingItem()
                ? new ItemStack(
                        stack.getItem()
                                .getCraftingRemainingItem())
                : ItemStack.EMPTY;
        stack.shrink(1);
        if (stack.isEmpty()) {
            player.setItemInHand(hand, remainder);
        }
        else if (!remainder.isEmpty()
                && !player.getInventory().add(remainder)) {
            player.drop(remainder, false);
        }
    }

    private boolean giveCarriedItemToOwner() {
        if (getCarriedItem().isEmpty() || deliveredCarriedItem) {
            return false;
        }
        ItemStack delivery = getCarriedItem();
        setCarriedItem(ItemStack.EMPTY);
        deliveredCarriedItem = true;
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        LivingEntity owner = getSwarmOwner();
        if (owner instanceof Player player) {
            if (!player.getInventory().add(delivery)) {
                player.drop(delivery, false);
            }
        }
        else if (owner != null && owner.isAlive()) {
            owner.spawnAtLocation(delivery);
        }
        else {
            spawnAtLocation(delivery);
        }
        return true;
    }

    // The slot's 1.16 place from the user's feet, before any side setting.
    public static Vec3 formationPosition(
            LivingEntity owner, int slot) {
        return owner.position().add(slotIdleOffset(slot).yRot(
                -owner.getYRot() * Mth.DEG_TO_RAD));
    }

    @Override
    public void setSelectedSkin(Optional<ResourceLocation> skin) {
        Optional<ResourceLocation> normalized =
                skin != null ? skin : Optional.empty();
        super.setSelectedSkin(normalized);
        if (!level().isClientSide()) {
            entityData.set(SELECTED_SKIN, normalized);
        }
    }

    @Override
    public void onSyncedDataUpdated(
            EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (SELECTED_SKIN.equals(accessor)) {
            super.setSelectedSkin(entityData.get(SELECTED_SKIN));
        }
        else if (IDLE_OFFSET.equals(accessor)) {
            applyIdleOffset();
        }
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public void die(DamageSource source) {
        LivingEntity owner = getSwarmOwner();
        giveCarriedItemToOwner();
        super.die(source);
        if (!level().isClientSide()
                && isDeadOrDying()
                && owner != null
                && owner.hasData(
                        SexPistolsAttachments
                                .SEX_PISTOLS_COORDINATOR)) {
            SexPistolsAttachments.coordinator(owner).onMemberDeath(
                    owner, getMemberNumber(), getUUID());
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide()) {
            giveCarriedItemToOwner();
            if (itemPickupTargetUuid != null
                    && level() instanceof ServerLevel serverLevel
                    && serverLevel.getEntity(itemPickupTargetUuid)
                            instanceof ItemEntity item) {
                item.getPersistentData()
                        .putBoolean(ITEM_RETRIEVED_TAG, false);
            }
        }
        super.remove(reason);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerUuid != null) {
            tag.putUUID("SexPistolsOwner", ownerUuid);
        }
        tag.putByte(
                "SexPistolsState",
                (byte) getMemberState().ordinal());
        tag.putDouble("SexPistolsTargetX", commandTarget.x);
        tag.putDouble("SexPistolsTargetY", commandTarget.y);
        tag.putDouble("SexPistolsTargetZ", commandTarget.z);
        Vec3 idleOffset = getIdleOffset();
        tag.putDouble("SexPistolsIdleOffsetX", idleOffset.x);
        tag.putDouble("SexPistolsIdleOffsetY", idleOffset.y);
        tag.putDouble("SexPistolsIdleOffsetZ", idleOffset.z);
        tag.putInt(
                "SexPistolsRedirectCooldown",
                redirectCooldown);
        tag.putBoolean(
                "SexPistolsReturnAfterKick",
                returnAfterKick);
        if (beggingPlayerUuid != null) {
            tag.putUUID(
                    "SexPistolsBeggingPlayer",
                    beggingPlayerUuid);
        }
        if (itemPickupTargetUuid != null) {
            tag.putUUID(
                    "SexPistolsItemPickupTarget",
                    itemPickupTargetUuid);
        }
        if (!eatingItem.isEmpty()) {
            tag.put(
                    "SexPistolsEatingItem",
                    eatingItem.save(
                            registryAccess()));
        }
        if (!getCarriedItem().isEmpty()) {
            tag.put(
                    "SexPistolsCarriedItem",
                    getCarriedItem().save(
                            registryAccess()));
        }
        tag.putByte(
                "SexPistolsEatingTicks",
                entityData.get(EATING_TICKS));
        tag.putByte(
                "SexPistolsEatingVariant",
                entityData.get(EATING_VARIANT));
        tag.putBoolean(
                "SexPistolsFoodBegging",
                entityData.get(FOOD_BEGGING));
        tag.putByte(
                "SexPistolsSummonTicks",
                entityData.get(SUMMON_TICKS));
        tag.putByte(
                "SexPistolsSummonVariant",
                entityData.get(SUMMON_VARIANT));
        tag.putByte(
                "SexPistolsKickTicks",
                entityData.get(KICK_TICKS));
        tag.putByte(
                "SexPistolsKickVariant",
                entityData.get(KICK_VARIANT));
        getStandSkin().ifPresent(skin ->
                tag.putString("SexPistolsSkin", skin.toString()));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ownerUuid = tag.hasUUID("SexPistolsOwner")
                ? tag.getUUID("SexPistolsOwner")
                : null;
        setMemberState(SexPistolsMemberState.byId(
                tag.getByte("SexPistolsState")));
        commandTarget = new Vec3(
                tag.getDouble("SexPistolsTargetX"),
                tag.getDouble("SexPistolsTargetY"),
                tag.getDouble("SexPistolsTargetZ"));
        if (tag.contains("SexPistolsIdleOffsetX")) {
            setIdleOffset(new Vec3(
                    tag.getDouble("SexPistolsIdleOffsetX"),
                    tag.getDouble("SexPistolsIdleOffsetY"),
                    tag.getDouble("SexPistolsIdleOffsetZ")));
        }
        redirectCooldown = Math.max(
                0,
                tag.getInt("SexPistolsRedirectCooldown"));
        returnAfterKick =
                tag.getBoolean("SexPistolsReturnAfterKick");
        beggingPlayerUuid =
                tag.hasUUID("SexPistolsBeggingPlayer")
                        ? tag.getUUID(
                                "SexPistolsBeggingPlayer")
                        : null;
        itemPickupTargetUuid =
                tag.hasUUID("SexPistolsItemPickupTarget")
                        ? tag.getUUID(
                                "SexPistolsItemPickupTarget")
                        : null;
        eatingItem = tag.contains("SexPistolsEatingItem")
                ? ItemStack.parseOptional(
                        registryAccess(),
                        tag.getCompound(
                                "SexPistolsEatingItem"))
                : ItemStack.EMPTY;
        setCarriedItem(tag.contains("SexPistolsCarriedItem")
                ? ItemStack.parseOptional(
                        registryAccess(),
                        tag.getCompound(
                                "SexPistolsCarriedItem"))
                : ItemStack.EMPTY);
        entityData.set(
                EATING_TICKS,
                (byte) Mth.clamp(
                        tag.getByte("SexPistolsEatingTicks"),
                        0,
                        EAT_ANIMATION_TICKS));
        entityData.set(
                EATING_VARIANT,
                (byte) Mth.clamp(
                        tag.getByte("SexPistolsEatingVariant"),
                        1,
                        5));
        entityData.set(
                FOOD_BEGGING,
                tag.getBoolean("SexPistolsFoodBegging"));
        entityData.set(
                SUMMON_TICKS,
                (byte) Mth.clamp(
                        tag.getByte("SexPistolsSummonTicks"),
                        0,
                        SUMMON_ANIMATION_TICKS));
        entityData.set(
                SUMMON_VARIANT,
                (byte) Mth.clamp(
                        tag.getByte("SexPistolsSummonVariant"),
                        1,
                        16));
        setItemSlot(
                EquipmentSlot.MAINHAND,
                !eatingItem.isEmpty()
                        ? eatingItem.copy()
                        : getCarriedItem().copy());
        entityData.set(
                KICK_TICKS,
                (byte) Mth.clamp(
                        tag.getByte("SexPistolsKickTicks"),
                        0,
                        KICK_DURATION_TICKS));
        entityData.set(
                KICK_VARIANT,
                (byte) Mth.clamp(
                        tag.getByte("SexPistolsKickVariant"),
                        1,
                        4));
        ResourceLocation skin = tag.contains("SexPistolsSkin")
                ? ResourceLocation.tryParse(
                        tag.getString("SexPistolsSkin"))
                : null;
        setSelectedSkin(Optional.ofNullable(skin));
    }
}
