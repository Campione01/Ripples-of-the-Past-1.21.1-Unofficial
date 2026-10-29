package rotp.addons.harvest.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.customobjects.EntityWithStandSkin;
import rotp.core.init.ModEntityDataSerializers;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.util.functions.DamageUtil;
import rotp.core.util.functions.JojoModUtil;
import com.mojang.datafixers.util.Pair;

import rotp.addons.harvest.HarvestAddon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class HarvestMemberEntity extends PathfinderMob
        implements EntityWithStandSkin {
    private boolean goingToDestination;
    public static final int ORPHAN_GRACE_TICKS = 40;
    public static final int PATH_REPLAN_INTERVAL_TICKS = 10;
    public static final int ATTACK_INTERVAL_TICKS = 10;
    public static final int PUNCH_COOLDOWN_TICKS = 15;
    public static final int STAB_COOLDOWN_TICKS = 100;
    public static final int BITE_COOLDOWN_TICKS = 45;
    public static final int POTION_EFFECT_CAP = 8;
    public static final float CARRY_UP_RIDING_SPEED = 2.0F;
    public static final double ATTACK_REACH = 1.25D;
    public static final float ATTACK_DAMAGE = 0.2F;
    public static final float STAB_DAMAGE = 0.3F;
    public static final float BITE_DAMAGE = 0.5F;
    // 1.16 HarvestUtil punch/stab/bite sources (bypass armor)
    public static final ResourceKey<DamageType> PUNCH_DAMAGE_TYPE =
            ResourceKey.create(Registries.DAMAGE_TYPE,
                    HarvestAddon.id("rotp_harvest_punch"));
    public static final ResourceKey<DamageType> STAB_DAMAGE_TYPE =
            ResourceKey.create(Registries.DAMAGE_TYPE,
                    HarvestAddon.id("rotp_harvest_stab"));
    public static final ResourceKey<DamageType> BITE_DAMAGE_TYPE =
            ResourceKey.create(Registries.DAMAGE_TYPE,
                    HarvestAddon.id("rotp_harvest_bite"));
    public static final String RETURNED_ITEM_TAG =
            "RotpHarvestReturnedItem";

    private static final EntityDataAccessor<Optional<UUID>>
            OWNER_UUID = SynchedEntityData.defineId(
                    HarvestMemberEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>>
            COORDINATOR_UUID = SynchedEntityData.defineId(
                    HarvestMemberEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer>
            COORDINATOR_ID = SynchedEntityData.defineId(
                    HarvestMemberEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer>
            SLOT = SynchedEntityData.defineId(
                    HarvestMemberEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte>
            MODE = SynchedEntityData.defineId(
                    HarvestMemberEntity.class,
                    EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean>
            CLOSE_FORMATION = SynchedEntityData.defineId(
                    HarvestMemberEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Optional<UUID>>
            TARGET_UUID = SynchedEntityData.defineId(
                    HarvestMemberEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<BlockPos>
            DESTINATION = SynchedEntityData.defineId(
                    HarvestMemberEntity.class,
                    EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Optional<UUID>>
            ASSIGNED_ITEM_UUID = SynchedEntityData.defineId(
                    HarvestMemberEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<ResourceLocation>>
            SELECTED_SKIN = SynchedEntityData.defineId(
                    HarvestMemberEntity.class,
                    ModEntityDataSerializers
                            .RESOURCE_LOCATION_OPTIONAL.get());
    private static final EntityDataAccessor<Byte>
            ATTACK_TICKS = SynchedEntityData.defineId(
                    HarvestMemberEntity.class,
                    EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer>
            POTION_PAYLOAD_COUNT = SynchedEntityData.defineId(
                    HarvestMemberEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer>
            POTION_GLOW_COLOR = SynchedEntityData.defineId(
                    HarvestMemberEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean>
            CLIMBING = SynchedEntityData.defineId(
                    HarvestMemberEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean>
            CARRYING_UP = SynchedEntityData.defineId(
                    HarvestMemberEntity.class,
                    EntityDataSerializers.BOOLEAN);

    private int orphanTicks;
    private int attackCooldown;
    private int punchCooldown;
    private int stabCooldown;
    private int biteCooldown;
    private final List<MobEffectInstance> potionPayload =
            new ArrayList<>(POTION_EFFECT_CAP);
    private boolean carriedItemReleased = true;

    public HarvestMemberEntity(
            EntityType<? extends HarvestMemberEntity> type,
            Level level) {
        super(type, level);
        xpReward = 0;
        setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 12.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.4D)
                .add(
                        Attributes.FOLLOW_RANGE,
                        HarvestOwnerLeash.PATH_RANGE);
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER_UUID, Optional.empty());
        builder.define(COORDINATOR_UUID, Optional.empty());
        builder.define(COORDINATOR_ID, -1);
        builder.define(SLOT, -1);
        builder.define(
                MODE, (byte) HarvestMemberMode.FOLLOW.ordinal());
        builder.define(CLOSE_FORMATION, false);
        builder.define(TARGET_UUID, Optional.empty());
        builder.define(DESTINATION, BlockPos.ZERO);
        builder.define(ASSIGNED_ITEM_UUID, Optional.empty());
        builder.define(SELECTED_SKIN, Optional.empty());
        builder.define(ATTACK_TICKS, (byte) 0);
        builder.define(POTION_PAYLOAD_COUNT, 0);
        builder.define(POTION_GLOW_COLOR, -1);
        builder.define(CLIMBING, false);
        builder.define(CARRYING_UP, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(
                7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WallClimberNavigation(this, level);
    }

    public void bindClosure(
            UUID ownerUuid,
            HarvestStandEntity coordinator,
            int slot,
            Optional<ResourceLocation> skin) {
        if (level().isClientSide()
                || ownerUuid == null
                || coordinator == null) {
            return;
        }
        entityData.set(OWNER_UUID, Optional.of(ownerUuid));
        entityData.set(
                COORDINATOR_UUID,
                Optional.of(coordinator.getUUID()));
        entityData.set(COORDINATOR_ID, coordinator.getId());
        entityData.set(
                SLOT,
                Mth.clamp(
                        slot, 0, HarvestStandEntity.MEMBER_CAP - 1));
        entityData.set(
                SELECTED_SKIN,
                skin != null ? skin : Optional.empty());
        attackCooldown = Math.floorMod(
                slot, ATTACK_INTERVAL_TICKS);
        orphanTicks = 0;
    }

    public boolean isOwnedBy(@Nullable UUID ownerUuid) {
        return ownerUuid != null
                && entityData.get(OWNER_UUID)
                        .filter(ownerUuid::equals)
                        .isPresent();
    }

    public boolean isBoundTo(@Nullable UUID coordinatorUuid) {
        return coordinatorUuid != null
                && entityData.get(COORDINATOR_UUID)
                        .filter(coordinatorUuid::equals)
                        .isPresent();
    }

    public int slot() {
        return entityData.get(SLOT);
    }

    public HarvestMemberMode getMode() {
        return HarvestMemberMode.byId(entityData.get(MODE));
    }

    public Optional<UUID> assignedItemUuid() {
        return entityData.get(ASSIGNED_ITEM_UUID);
    }

    public int attackTicks() {
        return Byte.toUnsignedInt(entityData.get(ATTACK_TICKS));
    }

    public int potionPayloadCount() {
        return entityData.get(POTION_PAYLOAD_COUNT);
    }

    public int potionGlowColor() {
        return entityData.get(POTION_GLOW_COLOR);
    }

    public boolean isCarryingUp() {
        return entityData.get(CARRYING_UP);
    }

    public void setCarryingUp(boolean carryingUp) {
        if (!level().isClientSide()) {
            entityData.set(CARRYING_UP, carryingUp);
        }
    }

    public boolean canUseCarryUpBehavior() {
        HarvestMemberMode mode = getMode();
        return mode != HarvestMemberMode.ATTACK
                && mode != HarvestMemberMode.SEARCH
                && mode != HarvestMemberMode.RETURN_ITEM;
    }

    public boolean canAcceptPotionPayload(int effectCount) {
        return effectCount > 0
                && effectCount <= POTION_EFFECT_CAP
                && potionPayload.size() + effectCount
                        <= POTION_EFFECT_CAP;
    }

    public boolean tryAddPotionPayload(
            List<MobEffectInstance> effects) {
        if (level().isClientSide()
                || effects == null
                || effects.isEmpty()
                || !canAcceptPotionPayload(effects.size())) {
            return false;
        }
        for (MobEffectInstance effect : effects) {
            if (effect == null) {
                return false;
            }
        }
        effects.forEach(effect ->
                potionPayload.add(new MobEffectInstance(effect)));
        syncPotionPayloadCount();
        return true;
    }

    public void clearPotionPayload() {
        if (!level().isClientSide()) {
            potionPayload.clear();
            syncPotionPayloadCount();
        }
    }

    public void attackTarget(UUID targetUuid) {
        setMode(
                HarvestMemberMode.ATTACK,
                Optional.ofNullable(targetUuid),
                BlockPos.ZERO);
    }

    public void recall() {
        setMode(
                HarvestMemberMode.FOLLOW,
                Optional.empty(),
                BlockPos.ZERO);
        clearSearchAssignment();
    }

    public void goTo(BlockPos destination) {
        setMode(
                HarvestMemberMode.GO_TO,
                Optional.empty(),
                destination);
        clearSearchAssignment();
    }

    public void stayAtDestination(BlockPos destination) {
        goTo(destination);
        goingToDestination = false;
    }

    public void stayWith(UUID targetUuid) {
        setMode(
                HarvestMemberMode.STAY_WITH,
                Optional.ofNullable(targetUuid),
                BlockPos.ZERO);
        clearSearchAssignment();
    }

    public void search() {
        setMode(
                HarvestMemberMode.SEARCH,
                Optional.empty(),
                BlockPos.ZERO);
    }

    public void setCloseFormation(boolean close) {
        if (!level().isClientSide()) {
            entityData.set(CLOSE_FORMATION, close);
        }
    }

    public void assignSearchTarget(UUID itemUuid) {
        if (level().isClientSide()
                || getMode() != HarvestMemberMode.SEARCH
                || !getMainHandItem().isEmpty()) {
            return;
        }
        entityData.set(
                ASSIGNED_ITEM_UUID,
                Optional.ofNullable(itemUuid));
    }

    private void setMode(
            HarvestMemberMode mode,
            Optional<UUID> target,
            BlockPos destination) {
        if (level().isClientSide()) {
            return;
        }
        entityData.set(MODE, (byte) mode.ordinal());
        goingToDestination = mode == HarvestMemberMode.GO_TO;
        entityData.set(
                TARGET_UUID,
                target != null ? target : Optional.empty());
        entityData.set(
                DESTINATION,
                destination != null ? destination : BlockPos.ZERO);
        getNavigation().stop();
    }

    @Override
    public void tick() {
        super.tick();
        if (entityData.get(ATTACK_TICKS) > 0) {
            entityData.set(
                    ATTACK_TICKS,
                    (byte) (entityData.get(ATTACK_TICKS) - 1));
        }
        if (level().isClientSide()) {
            return;
        }
        entityData.set(CLIMBING, horizontalCollision);
        if (!potionPayload.isEmpty()
                && tickCount % 20 == 0) {
            syncPotionPayloadCount();
        }
        if (attackCooldown > 0) {
            attackCooldown--;
        }
        if (punchCooldown > 0) {
            punchCooldown--;
        }
        if (stabCooldown > 0) {
            stabCooldown--;
        }
        if (biteCooldown > 0) {
            biteCooldown--;
        }

        LivingEntity owner = resolveOwner();
        HarvestStandEntity coordinator = resolveCoordinator();
        if (!hasValidClosure(owner, coordinator)) {
            getNavigation().stop();
            setCarryingUp(false);
            if (++orphanTicks >= ORPHAN_GRACE_TICKS) {
                releaseCarriedItem();
                discard();
            }
            return;
        }
        orphanTicks = 0;
        boolean carryUpBehavior = canUseCarryUpBehavior();
        boolean selectedForCarryUp =
                coordinator.isCarryUpMember(this);
        setCarryingUp(
                coordinator.isCarryUpActive()
                        && carryUpBehavior);
        if (coordinator.isCarryUpActive()
                && carryUpBehavior) {
            if (selectedForCarryUp) {
                getNavigation().stop();
            }
            else if (distanceToSqr(owner)
                    <= HarvestStandEntity.CARRY_UP_RANGE
                            * HarvestStandEntity.CARRY_UP_RANGE) {
                replanToward(owner.position(), 1.0D);
            }
            else {
                getNavigation().stop();
                teleportTo(
                        owner.getX(),
                        owner.getY(),
                        owner.getZ());
            }
            return;
        }
        // Leashed modes use the 1.16 leash; an assigned searcher may
        // roam the whole item radius.
        HarvestMemberMode mode = getMode();
        boolean roamingSearch = mode == HarvestMemberMode.SEARCH
                && assignedItemUuid().isPresent();
        if (!HarvestOwnerLeash.applies(mode)
                && !roamingSearch
                && distanceToSqr(owner)
                > HarvestStandEntity.MAX_OWNER_DISTANCE
                        * HarvestStandEntity.MAX_OWNER_DISTANCE) {
            replanToward(
                    formationPosition(
                            owner,
                            slot(),
                            entityData.get(CLOSE_FORMATION)),
                    1.15D);
            return;
        }

        switch (mode) {
            case FOLLOW -> follow(owner, coordinator);
            case ATTACK -> attack(owner);
            case GO_TO -> goToDestination(owner);
            case STAY_WITH -> stayWithTarget(owner);
            case SEARCH -> searchForItem(owner);
            case RETURN_ITEM -> returnItem(owner);
        }
    }

    private void follow(
            LivingEntity owner,
            HarvestStandEntity coordinator) {
        LivingEntity automaticTarget =
                coordinator.resolveAutomaticTarget(owner);
        if (automaticTarget != null) {
            engageTarget(owner, automaticTarget);
            return;
        }
        if (applyOwnerLeash(owner)) {
            return;
        }
        replanToward(
                formationPosition(
                        owner,
                        slot(),
                        entityData.get(CLOSE_FORMATION)),
                1.0D);
    }

    private void attack(LivingEntity owner) {
        LivingEntity target = resolveLiving(
                entityData.get(TARGET_UUID).orElse(null));
        if (!isValidTarget(owner, target)) {
            if (target != null && !target.isAlive()) {
                HarvestStandEntity coordinator =
                        resolveCoordinator();
                if (coordinator != null) {
                    coordinator.onTargetDefeated(
                            target.getUUID());
                }
            }
            recall();
            return;
        }
        engageTarget(owner, target);
    }

    private void engageTarget(
            LivingEntity owner, LivingEntity target) {
        getLookControl().setLookAt(target, 30.0F, 30.0F);
        double distance = distanceToSqr(target);
        if (distance > ATTACK_REACH * ATTACK_REACH) {
            replanToward(target.position(), 1.15D);
            return;
        }
        getNavigation().stop();
        if (attackCooldown <= 0 && hasLineOfSight(target)) {
            performCanonicalAttack(owner, target);
            attackCooldown = ATTACK_INTERVAL_TICKS;
        }
    }

    private void performCanonicalAttack(
            LivingEntity owner, LivingEntity target) {
        if (biteCooldown <= 0) {
            target.hurt(harvestDamage(owner, BITE_DAMAGE_TYPE), BITE_DAMAGE);
            int duration = 60;
            int amplifier = 0;
            MobEffectInstance existing =
                    target.getEffect(ModStatusEffects.BLEEDING);
            if (existing != null) {
                duration += existing.getDuration();
                amplifier = Math.min(
                        existing.getAmplifier() + 1, 3);
            }
            target.addEffect(new MobEffectInstance(
                    ModStatusEffects.BLEEDING,
                    duration,
                    amplifier,
                    false,
                    false,
                    true));
            biteCooldown = BITE_COOLDOWN_TICKS;
        }
        else if (stabCooldown <= 0
                && !potionPayload.isEmpty()) {
            for (MobEffectInstance effect
                    : List.copyOf(potionPayload)) {
                int duration = effect.getDuration();
                int amplifier = effect.getAmplifier();
                MobEffectInstance existing =
                        target.getEffect(effect.getEffect());
                if (existing != null) {
                    duration += existing.getDuration();
                    amplifier = Math.min(
                            existing.getAmplifier() + 1, 5);
                }
                target.addEffect(new MobEffectInstance(
                        effect.getEffect(),
                        duration,
                        amplifier,
                        false,
                        false,
                        true));
            }
            clearPotionPayload();
            target.hurt(harvestDamage(owner, STAB_DAMAGE_TYPE), STAB_DAMAGE);
            stabCooldown = STAB_COOLDOWN_TICKS;
        }
        else if (punchCooldown <= 0) {
            target.hurt(harvestDamage(owner, PUNCH_DAMAGE_TYPE), ATTACK_DAMAGE);
            punchCooldown = PUNCH_COOLDOWN_TICKS;
        }
        else {
            return;
        }
        entityData.set(ATTACK_TICKS, (byte) 5);
        swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        StandPower power = StandPower.get(owner);
        if (power != null && power.hasPower()) {
            power.getResolveCounter().addResolveValue(
                    power, 5.0F);
        }
        if (!target.isAlive()) {
            HarvestStandEntity coordinator =
                    resolveCoordinator();
            if (coordinator != null) {
                coordinator.onTargetDefeated(
                        target.getUUID());
            }
        }
    }

    /* 1.16 plain punch/stab/bite source: the type json/tags ignore armor,
     * shield and knockback and keep hurt cooldown; owner kept for aggro. */
    private DamageSource harvestDamage(
            LivingEntity owner, ResourceKey<DamageType> typeKey) {
        return new DamageSource(
                DamageUtil.type(level(), typeKey), this, owner) {
            // 1.16 message: ".player" names the kill credit
            @Override
            public Component getLocalizedDeathMessage(
                    LivingEntity dead) {
                String key = "death.attack." + type().msgId();
                LivingEntity killCredit = dead.getKillCredit();
                return killCredit != null
                        ? Component.translatable(key + ".player",
                                dead.getDisplayName(),
                                killCredit.getDisplayName())
                        : Component.translatable(key,
                                dead.getDisplayName());
            }
        };
    }

    private void syncPotionPayloadCount() {
        entityData.set(
                POTION_PAYLOAD_COUNT, potionPayload.size());
        int color = potionPayload.isEmpty()
                ? -1
                : potionPayload.get(Math.floorMod(
                                tickCount / 20,
                                potionPayload.size()))
                        .getEffect()
                        .value()
                        .getColor();
        entityData.set(POTION_GLOW_COLOR, color);
    }

    private void goToDestination(LivingEntity owner) {
        Vec3 destination = Vec3.atBottomCenterOf(
                entityData.get(DESTINATION));
        if (owner.position().distanceToSqr(destination)
                > HarvestStandEntity.MAX_OWNER_DISTANCE
                        * HarvestStandEntity.MAX_OWNER_DISTANCE) {
            recall();
            return;
        }
        if (goingToDestination && position().distanceToSqr(destination) < 3.0D) {
            goingToDestination = false;
            getNavigation().stop();
        }
        if (!goingToDestination) {
            if (!applyOwnerLeash(owner)) {
                getNavigation().stop();
            }
            return;
        }
        replanToward(offsetDestination(destination), 1.0D);
    }

    private void stayWithTarget(LivingEntity owner) {
        LivingEntity target = resolveLiving(
                entityData.get(TARGET_UUID).orElse(null));
        if (target == null
                || !target.isAlive()
                || target.level() != level()
                || owner.distanceToSqr(target)
                        > HarvestStandEntity.MAX_OWNER_DISTANCE
                                * HarvestStandEntity
                                        .MAX_OWNER_DISTANCE) {
            recall();
            return;
        }
        if (target instanceof Mob anchor) {
            LivingEntity anchorTarget = anchor.getTarget();
            if (isValidTarget(owner, anchorTarget)) {
                engageTarget(owner, anchorTarget);
                return;
            }
        }
        // 1.16 leashed to the stay-with entity (getOriginalOwner).
        if (applyOwnerLeash(target)) {
            return;
        }
        replanToward(
                formationPosition(
                        target,
                        slot(),
                        entityData.get(CLOSE_FORMATION)),
                1.0D);
    }

    private void searchForItem(LivingEntity owner) {
        if (!getMainHandItem().isEmpty()) {
            entityData.set(
                    MODE,
                    (byte) HarvestMemberMode.RETURN_ITEM.ordinal());
            clearSearchAssignment();
            returnItem(owner);
            return;
        }
        ItemEntity item = resolveItem(
                assignedItemUuid().orElse(null));
        if (item == null
                || !item.isAlive()
                || item.getItem().isEmpty()
                || owner.distanceToSqr(item)
                        > HarvestStandEntity.ITEM_SEARCH_RADIUS
                                * HarvestStandEntity
                                        .ITEM_SEARCH_RADIUS) {
            clearSearchAssignment();
            getNavigation().stop();
            return;
        }
        getLookControl().setLookAt(item, 30.0F, 30.0F);
        if (distanceToSqr(item) > 0.8D * 0.8D) {
            replanToward(item.position(), 1.1D);
            return;
        }
        ItemStack collected = item.getItem().copy();
        item.discard();
        setItemSlot(EquipmentSlot.MAINHAND, collected);
        setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        carriedItemReleased = false;
        clearSearchAssignment();
        entityData.set(
                MODE,
                (byte) HarvestMemberMode.RETURN_ITEM.ordinal());
    }

    private void returnItem(LivingEntity owner) {
        ItemStack carried = getMainHandItem();
        if (carried.isEmpty()) {
            carriedItemReleased = true;
            entityData.set(
                    MODE,
                    (byte) HarvestMemberMode.SEARCH.ordinal());
            return;
        }
        if (distanceToSqr(owner) > 1.5D * 1.5D) {
            replanToward(owner.position(), 1.15D);
            return;
        }
        ItemStack delivery = carried.copy();
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        carriedItemReleased = true;
        if (!delivery.isEmpty()) {
            ItemEntity returned = new ItemEntity(
                    level(),
                    owner.getX(),
                    owner.getY() + 0.5D,
                    owner.getZ(),
                    delivery);
            returned.getPersistentData()
                    .putBoolean(RETURNED_ITEM_TAG, true);
            if (!level().addFreshEntity(returned)) {
                ItemEntity fallback =
                        owner.spawnAtLocation(delivery);
                if (fallback != null) {
                    fallback.getPersistentData().putBoolean(
                            RETURNED_ITEM_TAG, true);
                }
            }
        }
        entityData.set(
                MODE,
                (byte) HarvestMemberMode.SEARCH.ordinal());
    }

    // 1.16 HarvestTeleportGoal: path back past half the leash,
    // teleport beside the anchor past leash + 10 blocks.
    private boolean applyOwnerLeash(LivingEntity anchor) {
        HarvestOwnerLeash.Action action = HarvestOwnerLeash.action(
                distanceTo(anchor),
                entityData.get(CLOSE_FORMATION));
        switch (action) {
            case TELEPORT -> teleportBeside(anchor);
            case PATH -> replanToward(anchor.position(), 1.0D);
            case NONE -> {
                return false;
            }
        }
        return true;
    }

    private void teleportBeside(LivingEntity anchor) {
        getNavigation().stop();
        double x = HarvestOwnerLeash.spread(
                anchor.getX(), random.nextDouble());
        double y = anchor.getY();
        double z = HarvestOwnerLeash.spread(
                anchor.getZ(), random.nextDouble());
        // Blocked landing falls back to the anchor itself.
        if (!level().noCollision(this, getBoundingBox().move(
                x - getX(), y - getY(), z - getZ()))) {
            x = anchor.getX();
            z = anchor.getZ();
        }
        resetFallDistance();
        teleportTo(x, y, z);
    }

    private void replanToward(Vec3 destination, double speed) {
        if (destination == null) {
            return;
        }
        double distance = position().distanceToSqr(destination);
        if (distance <= 0.12D) {
            getNavigation().stop();
            return;
        }
        if (mayReplan()) {
            getNavigation().moveTo(
                    destination.x,
                    destination.y,
                    destination.z,
                    speed);
        }
    }

    private boolean mayReplan() {
        return Math.floorMod(
                level().getGameTime() + slot(),
                PATH_REPLAN_INTERVAL_TICKS) == 0L;
    }

    private Vec3 offsetDestination(Vec3 destination) {
        int safeSlot = Mth.clamp(
                slot(), 0, HarvestStandEntity.MEMBER_CAP - 1);
        int row = safeSlot / 9;
        double angle = (safeSlot % 9)
                * (Math.PI * 2.0D / 9.0D);
        double radius = (entityData.get(CLOSE_FORMATION)
                ? 0.35D
                : 0.65D) + row * 0.22D;
        return destination.add(
                Math.cos(angle) * radius,
                0.0D,
                Math.sin(angle) * radius);
    }

    public static Vec3 formationPosition(
            LivingEntity anchor,
            int slot,
            boolean closeFormation) {
        int safeSlot = Mth.clamp(
                slot, 0, HarvestStandEntity.MEMBER_CAP - 1);
        int ring = safeSlot / 9;
        int index = safeSlot % 9;
        double radius = (closeFormation ? 0.75D : 1.4D)
                + ring * (closeFormation ? 0.35D : 0.65D);
        double angle = Math.toRadians(anchor.getYRot())
                + index * (Math.PI * 2.0D / 9.0D);
        return anchor.position().add(
                Math.cos(angle) * radius,
                0.0D,
                Math.sin(angle) * radius);
    }

    private boolean isValidTarget(
            LivingEntity owner,
            @Nullable LivingEntity target) {
        return target != null
                && target.isAlive()
                && target.level() == level()
                && owner.distanceToSqr(target)
                        <= HarvestStandEntity.MAX_OWNER_DISTANCE
                                * HarvestStandEntity
                                        .MAX_OWNER_DISTANCE
                && !target.isAlliedTo(owner)
                && JojoModUtil.canHarm(owner, target);
    }

    private boolean hasValidClosure(
            @Nullable LivingEntity owner,
            @Nullable HarvestStandEntity coordinator) {
        if (owner == null
                || coordinator == null
                || !coordinator.isAlive()
                || !isOwnedBy(owner.getUUID())
                || !isBoundTo(coordinator.getUUID())
                || !coordinator.isActiveFor(owner)) {
            return false;
        }
        StandPower power = StandPower.get(owner);
        return power != null
                && power.getPowerType()
                        == HarvestAddon.HARVEST_STAND.get()
                && power.getSummonedStandEntity() == coordinator;
    }

    @Nullable
    private LivingEntity resolveOwner() {
        UUID uuid = entityData.get(OWNER_UUID).orElse(null);
        if (uuid == null) {
            return null;
        }
        if (level() instanceof ServerLevel serverLevel) {
            Entity entity = serverLevel.getEntity(uuid);
            return entity instanceof LivingEntity living
                    ? living
                    : null;
        }
        Player player = level().getPlayerByUUID(uuid);
        return player;
    }

    @Nullable
    public HarvestStandEntity resolveCoordinator() {
        if (level() instanceof ServerLevel serverLevel) {
            UUID uuid = entityData.get(COORDINATOR_UUID)
                    .orElse(null);
            Entity entity = uuid != null
                    ? serverLevel.getEntity(uuid)
                    : null;
            return entity instanceof HarvestStandEntity coordinator
                    ? coordinator
                    : null;
        }
        Entity entity = level().getEntity(
                entityData.get(COORDINATOR_ID));
        return entity instanceof HarvestStandEntity coordinator
                && isBoundTo(coordinator.getUUID())
                ? coordinator
                : null;
    }

    @Nullable
    private LivingEntity resolveLiving(@Nullable UUID uuid) {
        if (!(level() instanceof ServerLevel serverLevel)
                || uuid == null) {
            return null;
        }
        Entity entity = serverLevel.getEntity(uuid);
        return entity instanceof LivingEntity living
                ? living
                : null;
    }

    @Nullable
    private ItemEntity resolveItem(@Nullable UUID uuid) {
        if (!(level() instanceof ServerLevel serverLevel)
                || uuid == null) {
            return null;
        }
        Entity entity = serverLevel.getEntity(uuid);
        return entity instanceof ItemEntity item ? item : null;
    }

    private void clearSearchAssignment() {
        if (!level().isClientSide()) {
            entityData.set(
                    ASSIGNED_ITEM_UUID, Optional.empty());
        }
    }

    public void releaseCarriedItem() {
        if (level().isClientSide() || carriedItemReleased) {
            return;
        }
        ItemStack carried = getMainHandItem();
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        carriedItemReleased = true;
        if (!carried.isEmpty()) {
            spawnAtLocation(carried.copy());
        }
    }

    public void setSelectedSkin(
            Optional<ResourceLocation> skin) {
        if (!level().isClientSide()) {
            entityData.set(
                    SELECTED_SKIN,
                    skin != null ? skin : Optional.empty());
        }
    }

    @Override
    public ResourceLocation getStandType() {
        return HarvestAddon.id("harvest_stand");
    }

    @Override
    public Optional<ResourceLocation> getStandSkin() {
        return entityData.get(SELECTED_SKIN);
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        Entity passenger = getFirstPassenger();
        return passenger instanceof Player player
                        && isOwnedBy(player.getUUID())
                ? player
                : super.getControllingPassenger();
    }

    @Override
    protected void tickRidden(
            Player player, Vec3 travelVector) {
        super.tickRidden(player, travelVector);
        setRot(player.getYRot(), player.getXRot() * 0.5F);
        yRotO = yBodyRot = yHeadRot = getYRot();
    }

    @Override
    protected Vec3 getRiddenInput(
            Player player, Vec3 travelVector) {
        return new Vec3(0.0D, 0.0D, 1.0D);
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        return CARRY_UP_RIDING_SPEED;
    }

    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    @Override
    public boolean dismountsUnderwater() {
        return true;
    }

    @Override
    protected void playStepSound(
            BlockPos pos, BlockState state) {
        level().playSound(
                null,
                blockPosition(),
                HarvestAddon.HARVEST_STEP.get(),
                SoundSource.PLAYERS,
                0.35F,
                0.9F + random.nextFloat() * 0.2F);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() != null
                && isAlliedTo(source.getEntity())) {
            return false;
        }
        return DamageUtil.canHurtStands(source)
                ? super.hurt(source, amount)
                : false;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return false;
    }

    @Override
    public boolean onClimbable() {
        return entityData.get(CLIMBING);
    }

    @Override
    public boolean isAlliedTo(Entity entity) {
        UUID ownerUuid = entityData.get(OWNER_UUID).orElse(null);
        if (ownerUuid != null) {
            if (ownerUuid.equals(entity.getUUID())) {
                return true;
            }
            if (entity instanceof HarvestMemberEntity member
                    && member.isOwnedBy(ownerUuid)) {
                return true;
            }
            if (entity instanceof HarvestStandEntity coordinator
                    && coordinator.isOwnedBy(ownerUuid)) {
                return true;
            }
        }
        return super.isAlliedTo(entity);
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide()) {
            releaseCarriedItem();
            HarvestStandEntity coordinator = resolveCoordinator();
            if (coordinator != null) {
                coordinator.onMemberDeath(slot(), getUUID());
            }
        }
        super.die(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide()) {
            releaseCarriedItem();
        }
        super.remove(reason);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        entityData.get(OWNER_UUID).ifPresent(uuid ->
                tag.putUUID("HarvestOwner", uuid));
        entityData.get(COORDINATOR_UUID).ifPresent(uuid ->
                tag.putUUID("HarvestCoordinator", uuid));
        tag.putInt("HarvestCoordinatorId", entityData.get(
                COORDINATOR_ID));
        tag.putInt("HarvestSlot", slot());
        tag.putByte("HarvestMode", entityData.get(MODE));
        tag.putBoolean("HarvestGoingTo", goingToDestination);
        tag.putBoolean(
                "HarvestCloseFormation",
                entityData.get(CLOSE_FORMATION));
        tag.putBoolean("HarvestCarryingUp", isCarryingUp());
        entityData.get(TARGET_UUID).ifPresent(uuid ->
                tag.putUUID("HarvestTarget", uuid));
        tag.putLong(
                "HarvestDestination",
                entityData.get(DESTINATION).asLong());
        entityData.get(ASSIGNED_ITEM_UUID).ifPresent(uuid ->
                tag.putUUID("HarvestAssignedItem", uuid));
        getStandSkin().ifPresent(skin ->
                tag.putString(
                        "HarvestSelectedSkin", skin.toString()));
        if (!potionPayload.isEmpty()) {
            ListTag effects = new ListTag();
            potionPayload.stream()
                    .limit(POTION_EFFECT_CAP)
                    .forEach(effect ->
                            MobEffectInstance.CODEC
                                    .encodeStart(
                                            NbtOps.INSTANCE,
                                            effect)
                                    .ifSuccess(effects::add));
            tag.put("HarvestPotionPayload", effects);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(
                OWNER_UUID,
                tag.hasUUID("HarvestOwner")
                        ? Optional.of(tag.getUUID("HarvestOwner"))
                        : Optional.empty());
        entityData.set(
                COORDINATOR_UUID,
                tag.hasUUID("HarvestCoordinator")
                        ? Optional.of(tag.getUUID(
                                "HarvestCoordinator"))
                        : Optional.empty());
        entityData.set(
                COORDINATOR_ID,
                tag.getInt("HarvestCoordinatorId"));
        entityData.set(
                SLOT,
                Mth.clamp(
                        tag.getInt("HarvestSlot"),
                        -1,
                        HarvestStandEntity.MEMBER_CAP - 1));
        entityData.set(
                MODE,
                (byte) Mth.clamp(
                        tag.getByte("HarvestMode"),
                        0,
                        HarvestMemberMode.values().length - 1));
        goingToDestination = tag.contains("HarvestGoingTo", Tag.TAG_BYTE)
                ? tag.getBoolean("HarvestGoingTo")
                : getMode() == HarvestMemberMode.GO_TO;
        entityData.set(
                CLOSE_FORMATION,
                tag.getBoolean("HarvestCloseFormation"));
        entityData.set(
                CARRYING_UP,
                tag.getBoolean("HarvestCarryingUp"));
        entityData.set(
                TARGET_UUID,
                tag.hasUUID("HarvestTarget")
                        ? Optional.of(tag.getUUID("HarvestTarget"))
                        : Optional.empty());
        entityData.set(
                DESTINATION,
                BlockPos.of(tag.getLong("HarvestDestination")));
        entityData.set(
                ASSIGNED_ITEM_UUID,
                tag.hasUUID("HarvestAssignedItem")
                        ? Optional.of(tag.getUUID(
                                "HarvestAssignedItem"))
                        : Optional.empty());
        ResourceLocation skin = tag.contains(
                "HarvestSelectedSkin")
                ? ResourceLocation.tryParse(tag.getString(
                        "HarvestSelectedSkin"))
                : null;
        entityData.set(
                SELECTED_SKIN, Optional.ofNullable(skin));
        potionPayload.clear();
        if (tag.contains(
                "HarvestPotionPayload", Tag.TAG_LIST)) {
            ListTag effects = tag.getList(
                    "HarvestPotionPayload",
                    Tag.TAG_COMPOUND);
            for (int i = 0;
                    i < Math.min(
                            effects.size(), POTION_EFFECT_CAP);
                    i++) {
                MobEffectInstance.CODEC
                        .decode(NbtOps.INSTANCE, effects.get(i))
                        .result()
                        .map(Pair::getFirst)
                        .ifPresent(potionPayload::add);
            }
        }
        syncPotionPayloadCount();
        carriedItemReleased = getMainHandItem().isEmpty();
    }
}
