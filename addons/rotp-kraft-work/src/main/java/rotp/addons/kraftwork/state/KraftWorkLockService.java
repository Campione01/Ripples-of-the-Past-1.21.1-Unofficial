package rotp.addons.kraftwork.state;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.customobjects.RoadRollerEntity;
import rotp.core.customobjects.entity_projectile.BladeHatEntity;
import rotp.core.customobjects.entity_projectile.ClackersEntity;
import rotp.core.customobjects.entity_projectile.KnifeEntity;
import rotp.core.customobjects.entity_projectile.OwnerBoundProjectileEntity;
import rotp.core.customobjects.entity_projectile.SpaceRipperStingyEyesEntity;
import rotp.core.customobjects.entity_projectile.TommyGunBulletEntity;
import rotp.core.impl.stands.crazydiamond.CrazyDBlockBulletEntity;
import rotp.core.init.ModSoundEvents;
import rotp.core.item.GlovesItem;
import rotp.core.item.SatiporojaScarfItem;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.kraftwork.KraftWorkAddon;
import rotp.addons.kraftwork.KraftWorkSpec;
import rotp.addons.kraftwork.entity.KraftWorkItemProjectile;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Saddleable;
import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class KraftWorkLockService {
    private static final String EFFECT_PHYSICS = "rotp_kraft_work:effect_physics";
    private static final String ORIGINAL_NO_GRAVITY = "NoGravity";
    private static final String ORIGINAL_NO_AI = "NoAi";
    public static final int MAIN_HAND = 1;
    public static final int OFF_HAND = 1 << 1;
    public static final int HELMET = 1 << 2;
    public static final int CHESTPLATE = 1 << 3;
    public static final int LEGGINGS = 1 << 4;
    public static final int BOOTS = 1 << 5;
    public static final int TRANSPORT = 1 << 6;
    public static final int FULL_TRANSPORT = 1 << 7;

    public enum ReleaseFilter {
        // 1.16 KraftWorkReleaseProjectile: what is locked within a block
        // of the user's box
        NEARBY,
        ALL,
        READY,
        NOT_READY,
        ENDER_PEARL,
        BENEFICIAL
    }

    private KraftWorkLockService() {}

    public static KraftWorkLockState state(Entity entity) {
        return entity.getData(KraftWorkAddon.LOCK_STATE);
    }

    public static boolean lock(
            LivingEntity owner, Entity target) {
        if (target instanceof LivingEntity living
                && !(living instanceof StandEntity)) {
            return lockEquipment(owner, living) > 0;
        }
        return lockWhole(owner, target);
    }

    public static boolean lockWhole(
            LivingEntity owner, Entity target) {
        if (!canLock(owner, target)
                || !isWholeLockable(target)) {
            return false;
        }
        KraftWorkLockState state = state(target);
        if (state.locked()) {
            return false;
        }
        state.lock(
                owner.getUUID(),
                owner.level().dimension().location().toString(),
                target.position(),
                target.getDeltaMovement(),
                target.isNoGravity(),
                target instanceof Mob mob && mob.isNoAi(),
                0);
        freeze(target, state);
        // a road roller's rider can get off and a Stingy Eyes beam can
        // detach while it is held
        if (target instanceof RoadRollerEntity
                || target instanceof SpaceRipperStingyEyesEntity
                || !ticksOnItsOwn(target)) {
            target.setData(KraftWorkAddon.HELD_STILL, true);
        }
        return true;
    }

    // 1.16 Place Projectile's knives thrown in flight (flightTicks 1,
    // KraftWorkPlaceProjectile :294-302) moved on their own ticks before
    // they held where those left them (KnifeEntity.tick, canUpdate)
    public static boolean lockWholeAfterFlight(
            LivingEntity owner, Entity target, int flightTicks) {
        if (!lockWhole(owner, target)) {
            return false;
        }
        KraftWorkLockState state = state(target);
        state.delayHold(flightTicks);
        if (state.holdDelay() > 0) {
            target.setNoGravity(state.hadNoGravity());
            target.setDeltaMovement(state.storedMotion());
        }
        return true;
    }

    // an entity met in that flight stopped the knife where it began, its
    // motion kept (1.16 KnifeEntity.onHit with timeStop)
    public static boolean stopsHeldFlight(Entity projectile) {
        if (!projectile.hasData(KraftWorkAddon.LOCK_STATE)) {
            return false;
        }
        KraftWorkLockState state = state(projectile);
        if (!state.locked() || state.holdDelay() == 0) {
            return false;
        }
        state.endHoldDelayInPlace();
        return true;
    }

    public static int lockEquipment(
            LivingEntity owner, LivingEntity target) {
        if (!canLockDying(owner, target)
                || target instanceof StandEntity
                || target instanceof Player player
                        && player.getAbilities().instabuild) {
            return 0;
        }
        int mask = equipmentMask(target);
        return mask == 0
                ? 0
                : lockEquipmentMask(owner, target, mask);
    }

    public static boolean hasEquipmentToLock(
            LivingEntity target) {
        return equipmentMask(target) != 0;
    }

    public static int lockMainHand(
            LivingEntity owner, LivingEntity target) {
        if (!canLock(owner, target)
                || target instanceof StandEntity
                || target instanceof Player player
                        && player.getAbilities().instabuild) {
            return 0;
        }
        return lockEquipmentMask(owner, target, MAIN_HAND);
    }

    // 1.16 KraftWorkLockArmor.checkSpecificConditions: no Stand, no
    // creative player, nothing already locked, something to lock.
    public static boolean canLockArmorAfterHeavy(Entity target) {
        if (!(target instanceof LivingEntity living)
                || !living.isAlive()
                || living instanceof StandEntity
                || living instanceof Player player
                        && player.getAbilities().instabuild
                || state(living).locked()) {
            return false;
        }
        return living.isBlocking()
                || armorMask(living) != 0
                || transportMask(living) != 0;
    }

    // 1.16 KraftWorkLockArmor.standTickRecovery: a raised shield that took
    // no damage locks that hand, else every armor piece and the saddle.
    public static int lockArmorAfterHeavy(
            LivingEntity owner,
            LivingEntity target,
            float damageDealt) {
        if (!canLock(owner, target)
                || !canLockArmorAfterHeavy(target)) {
            return 0;
        }
        if (target.isBlocking() && damageDealt <= 0.0F) {
            int hand = target.getUsedItemHand()
                    == net.minecraft.world.InteractionHand.OFF_HAND
                            ? OFF_HAND
                            : MAIN_HAND;
            return lockEquipmentMask(owner, target, hand);
        }
        int mask = armorMask(target) | transportMask(target);
        return mask == 0
                ? 0
                : lockEquipmentMask(owner, target, mask);
    }

    private static int armorMask(LivingEntity target) {
        int mask = 0;
        if (!target.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            mask |= HELMET;
        }
        if (!target.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
            mask |= CHESTPLATE;
        }
        if (!target.getItemBySlot(EquipmentSlot.LEGS).isEmpty()) {
            mask |= LEGGINGS;
        }
        if (!target.getItemBySlot(EquipmentSlot.FEET).isEmpty()) {
            mask |= BOOTS;
        }
        return mask;
    }

    public static int selfLockMask(LivingEntity target) {
        int armor = 0;
        if (!target.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            armor |= HELMET;
        }
        if (!target.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
            armor |= CHESTPLATE;
        }
        if (!target.getItemBySlot(EquipmentSlot.LEGS).isEmpty()) {
            armor |= LEGGINGS;
        }
        if (!target.getItemBySlot(EquipmentSlot.FEET).isEmpty()) {
            armor |= BOOTS;
        }
        if (armor != 0) {
            return armor;
        }
        return target.getMainHandItem().isEmpty()
                ? 0
                : MAIN_HAND;
    }

    public static int lockSelf(
            LivingEntity owner, int mask) {
        return mask == 0
                ? 0
                : lockEquipmentMask(owner, owner, mask);
    }

    // 1.16 KraftWorkEnergyAccumulation.standPerform (:80-91) charged every
    // projectile whose PositionLocking was set, whoever had locked it.
    public static boolean addEnergy(Entity target) {
        KraftWorkLockState state = state(target);
        if (!state.locked()
                || !(target instanceof Projectile)) {
            return false;
        }
        if (target instanceof AbstractArrow arrow
                && !(target instanceof KraftWorkItemProjectile)
                && state.energy() > 200) {
            arrow.setCritArrow(true);
        }
        state.addEnergy();
        return true;
    }

    public static boolean releaseTarget(
            LivingEntity owner, Entity target) {
        KraftWorkLockState state = state(target);
        if (!state.locked()
                || !owner.getUUID().equals(state.owner())) {
            return false;
        }
        release(owner, target, state);
        return true;
    }

    public static int releaseProjectiles(
            LivingEntity owner, ReleaseFilter filter) {
        if (!(owner.level() instanceof ServerLevel level)) {
            return 0;
        }
        AABB search = owner.getBoundingBox().inflate(
                filter == ReleaseFilter.NEARBY
                        ? KraftWorkSpec.RELEASE_PROJECTILE_REACH
                        : KraftWorkSpec.LOCK_RANGE);
        List<Entity> locked = new ArrayList<>();
        for (Entity projectile :
                level.getEntities(
                        (Entity) null,
                        search,
                        entity -> entity instanceof Projectile
                                || entity instanceof EyeOfEnder)) {
            KraftWorkLockState state = state(projectile);
            if (state.locked()
                    && owner.getUUID().equals(state.owner())
                    && matches(
                            owner, projectile, state, filter)) {
                locked.add(projectile);
            }
        }
        locked.sort(Comparator.comparingDouble(owner::distanceToSqr));
        for (Entity entity : locked) {
            release(owner, entity, state(entity));
        }
        return locked.size();
    }

    public static void preTick(Entity entity) {
        if (entity.level().isClientSide()
                || !entity.hasData(KraftWorkAddon.LOCK_STATE)) {
            return;
        }
        KraftWorkLockState state = state(entity);
        if (state.locked() && state.holdDelay() == 0) {
            freeze(entity, state);
        }
    }

    public static void postTick(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        if (entity instanceof LivingEntity living) {
            restoreAfterEffects(living);
        }
        if (!entity.hasData(KraftWorkAddon.LOCK_STATE)) {
            return;
        }
        KraftWorkLockState state = state(entity);
        if (!state.locked()) {
            tickReleaseFlight(entity, state);
            return;
        }
        state.tickHoldDelay(entity.position(), entity.getDeltaMovement());
        LivingEntity owner = resolveOwner(level, state.owner());
        // A whole-locked armor stand holds no lock effect to lose.
        if (owner == null
                || !state.dimension().equals(
                        level.dimension().location().toString())
                || owner.distanceToSqr(entity)
                        > KraftWorkSpec.LOCK_RANGE_SQR
                || !KraftWorkAddon.isKraftWorkUser(owner)
                || entity instanceof LivingEntity living
                        && !(living instanceof ArmorStand
                                && state.equipmentMask() == 0)
                        && !hasAnyLockEffect(living)) {
            release(null, entity, state);
            return;
        }
        freeze(entity, state);
        if (entity.isVehicle()) {
            for (Entity passenger : entity.getPassengers()) {
                if (passenger instanceof LivingEntity living
                        && !KraftWorkAddon.isKraftWorkUser(living)) {
                    addEffect(
                            living,
                            KraftWorkAddon
                                    .TRANSPORTATION_LOCKED);
                }
            }
        }
    }

    // 1.16 stopped the update (canUpdate false) of whatever it held whole, on
    // the server and on the clients tracking it (KraftWorkStandType.tickUser
    // :143-144 every projectile, :157-160 everything else, KraftWorkLockTarget
    // :247, KraftWorkBlock :199), though not of a mob it held piece by piece:
    // a held TNT kept its fuse and a falling block its time until released,
    // and no client drew a held arrow's crits or a fireball's smoke. Run
    // release checks before deciding whether to skip the tick.
    public static boolean suspendsTick(Entity entity) {
        if (entity.level().isClientSide()) {
            return heldStillOnClients(entity);
        }
        if (!entity.hasData(KraftWorkAddon.LOCK_STATE)
                || !holdsWithoutTicking(entity, state(entity))) {
            return false;
        }
        postTick(entity);
        return state(entity).locked();
    }

    // a knife thrown in flight first flew its own tick (1.16 KnifeEntity.tick,
    // tsFlightTicks); tickUser's living branch (:114) left an armor stand out,
    // so one held by its armor stopped as well (:157-160)
    private static boolean holdsWithoutTicking(
            Entity entity, KraftWorkLockState state) {
        return state.locked()
                && (state.equipmentMask() == 0
                        || entity instanceof ArmorStand)
                && state.holdDelay() == 0
                && !ticksOnItsOwn(entity);
    }

    // 1.16's canUpdate overrides kept these updating however they were held:
    // RoadRollerEntity (:80-82) while its rider could, an
    // OwnerBoundProjectileEntity (:424-427) bound to its owner, the Stingy
    // Eyes beam among them, and HGBarrierEntity (:236-239) always, as the
    // core's TimeStopState copies them
    private static boolean ticksOnItsOwn(Entity entity) {
        return entity instanceof RoadRollerEntity && entity.isVehicle()
                || entity instanceof OwnerBoundProjectileEntity bound
                        && (bound.canTickInStoppedTime()
                                || bound.getOwner() != null)
                || entity instanceof SpaceRipperStingyEyesEntity beam
                        && beam.getOwnerBoundTo() != null;
    }

    // the server syncs the held flag to the clients tracking the entity
    public static boolean heldStillOnClients(Entity entity) {
        return entity.getExistingData(KraftWorkAddon.HELD_STILL)
                        .orElse(false)
                && !ticksOnItsOwn(entity);
    }

    public static void releaseOwned(
            @Nullable ServerLevel level, UUID ownerUuid) {
        if (level == null) {
            return;
        }
        // getAllEntities is a live view of the level's entity map, and a
        // release can add a fresh bullet and discard the held one
        List<Entity> owned = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity.hasData(KraftWorkAddon.LOCK_STATE)
                    && state(entity).locked()
                    && ownerUuid.equals(state(entity).owner())) {
                owned.add(entity);
            }
        }
        for (Entity entity : owned) {
            if (!entity.isRemoved()) {
                release(null, entity, state(entity));
            }
        }
    }

    @Nullable
    public static LivingEntity resolveOwner(
            ServerLevel level, @Nullable UUID ownerUuid) {
        if (ownerUuid == null) {
            return null;
        }
        Entity entity = level.getEntity(ownerUuid);
        return entity instanceof LivingEntity living
                && living.isAlive()
                        ? living
                        : null;
    }

    public static boolean hasLockedMainHand(
            LivingEntity living) {
        return living.hasEffect(KraftWorkAddon.LOCKED_MAIN_HAND);
    }

    public static boolean hasLockedOffHand(
            LivingEntity living) {
        return living.hasEffect(KraftWorkAddon.LOCKED_OFF_HAND);
    }

    public static boolean hasAnyLockEffect(
            LivingEntity living) {
        return hasLockedMainHand(living)
                || hasLockedOffHand(living)
                || living.hasEffect(KraftWorkAddon.LOCKED_HELMET)
                || living.hasEffect(
                        KraftWorkAddon.LOCKED_CHESTPLATE)
                || living.hasEffect(KraftWorkAddon.LOCKED_LEGGINGS)
                || living.hasEffect(KraftWorkAddon.LOCKED_POSITION)
                || living.hasEffect(
                        KraftWorkAddon.TRANSPORTATION_LOCKED)
                || living.hasEffect(
                        KraftWorkAddon
                                .FULL_TRANSPORTATION_LOCKED);
    }

    public static void clearLockEffects(LivingEntity living) {
        living.removeEffect(KraftWorkAddon.LOCKED_MAIN_HAND);
        living.removeEffect(KraftWorkAddon.LOCKED_OFF_HAND);
        living.removeEffect(KraftWorkAddon.LOCKED_HELMET);
        living.removeEffect(KraftWorkAddon.LOCKED_CHESTPLATE);
        living.removeEffect(KraftWorkAddon.LOCKED_LEGGINGS);
        living.removeEffect(KraftWorkAddon.LOCKED_POSITION);
        living.removeEffect(KraftWorkAddon.TRANSPORTATION_LOCKED);
        living.removeEffect(
                KraftWorkAddon.FULL_TRANSPORTATION_LOCKED);
    }

    public static void captureEffectPhysics(LivingEntity living) {
        CompoundTag persistent = living.getPersistentData();
        if (living.level().isClientSide()
                || persistent.contains(EFFECT_PHYSICS, Tag.TAG_COMPOUND)) {
            return;
        }
        KraftWorkLockState lockState = living.hasData(KraftWorkAddon.LOCK_STATE)
                ? state(living) : null;
        boolean lockOwnsPhysics = lockState != null
                && (lockState.locked() || lockState.flightTicks() > 0);
        CompoundTag snapshot = new CompoundTag();
        snapshot.putBoolean(ORIGINAL_NO_GRAVITY,
                lockOwnsPhysics ? lockState.hadNoGravity() : living.isNoGravity());
        snapshot.putBoolean(ORIGINAL_NO_AI,
                lockOwnsPhysics ? lockState.hadNoAi() : living instanceof Mob mob && mob.isNoAi());
        persistent.put(EFFECT_PHYSICS, snapshot);
    }

    public static void restoreAfterEffects(
            LivingEntity living) {
        CompoundTag persistent = living.getPersistentData();
        if (living.level().isClientSide()
                || !persistent.contains(EFFECT_PHYSICS, Tag.TAG_COMPOUND)
                || hasAnyLockEffect(living)) {
            return;
        }
        KraftWorkLockState lockState =
                living.hasData(KraftWorkAddon.LOCK_STATE)
                        ? state(living)
                        : null;
        if (lockState != null
                && (lockState.locked()
                        || lockState.flightTicks() > 0)) {
            return;
        }
        CompoundTag snapshot = persistent.getCompound(EFFECT_PHYSICS);
        persistent.remove(EFFECT_PHYSICS);
        living.setNoGravity(snapshot.getBoolean(ORIGINAL_NO_GRAVITY));
        living.fallDistance = 0.0F;
        if (living instanceof Mob mob) {
            mob.setNoAi(snapshot.getBoolean(ORIGINAL_NO_AI));
        }
    }

    private static int lockEquipmentMask(
            LivingEntity owner,
            LivingEntity target,
            int mask) {
        KraftWorkLockState state = state(target);
        if (state.locked()
                && !owner.getUUID().equals(state.owner())) {
            return 0;
        }
        int newMask = mask & ~state.equipmentMask();
        if (newMask == 0) {
            return 0;
        }
        if (!state.locked()) {
            CompoundTag persistent = target.getPersistentData();
            boolean effectOwnsPhysics = persistent.contains(EFFECT_PHYSICS, Tag.TAG_COMPOUND);
            CompoundTag snapshot = persistent.getCompound(EFFECT_PHYSICS);
            state.lock(
                    owner.getUUID(),
                    owner.level().dimension().location().toString(),
                    target.position(),
                    target.getDeltaMovement(),
                    effectOwnsPhysics ? snapshot.getBoolean(ORIGINAL_NO_GRAVITY) : target.isNoGravity(),
                    effectOwnsPhysics ? snapshot.getBoolean(ORIGINAL_NO_AI)
                            : target instanceof Mob mob && mob.isNoAi(),
                    newMask);
        }
        else {
            state.addEquipmentMask(newMask);
        }
        applyMask(target, newMask);
        freeze(target, state);
        if (target instanceof ArmorStand) {
            target.setData(KraftWorkAddon.HELD_STILL, true);
        }
        return Integer.bitCount(newMask);
    }

    private static int equipmentMask(LivingEntity target) {
        ItemStack main = target.getMainHandItem();
        ItemStack off = target.getOffhandItem();
        int mask = 0;
        if (isTwoHanded(main.getItem())
                || isTwoHanded(off.getItem())) {
            if (!main.isEmpty() || !off.isEmpty()) {
                mask |= MAIN_HAND | OFF_HAND;
            }
        }
        else {
            if (!main.isEmpty() && !isStandItem(main)) {
                mask |= MAIN_HAND;
            }
            if (!off.isEmpty() && !isStandItem(off)) {
                mask |= OFF_HAND;
            }
        }
        if (!target.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            mask |= HELMET;
        }
        if (!target.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
            mask |= CHESTPLATE;
        }
        if (!target.getItemBySlot(EquipmentSlot.LEGS).isEmpty()) {
            mask |= LEGGINGS;
        }
        if (!target.getItemBySlot(EquipmentSlot.FEET).isEmpty()) {
            mask |= BOOTS;
        }
        return mask | transportMask(target);
    }

    private static int transportMask(LivingEntity target) {
        int mask = 0;
        if (target instanceof Ravager) {
            mask |= TRANSPORT;
        }
        if (target instanceof AbstractChestedHorse horse
                && horse.hasChest()) {
            mask |= TRANSPORT;
        }
        if (target instanceof Saddleable saddle
                && saddle.isSaddled()) {
            mask |= target instanceof AbstractHorse
                    ? FULL_TRANSPORT
                    : TRANSPORT;
        }
        return mask;
    }

    private static void applyMask(
            LivingEntity target, int mask) {
        if ((mask & MAIN_HAND) != 0) {
            addEffect(target, KraftWorkAddon.LOCKED_MAIN_HAND);
        }
        if ((mask & OFF_HAND) != 0) {
            addEffect(target, KraftWorkAddon.LOCKED_OFF_HAND);
        }
        if ((mask & HELMET) != 0) {
            addEffect(target, KraftWorkAddon.LOCKED_HELMET);
        }
        if ((mask & CHESTPLATE) != 0) {
            addEffect(target, KraftWorkAddon.LOCKED_CHESTPLATE);
        }
        if ((mask & LEGGINGS) != 0) {
            addEffect(target, KraftWorkAddon.LOCKED_LEGGINGS);
        }
        if ((mask & BOOTS) != 0) {
            addEffect(target, KraftWorkAddon.LOCKED_POSITION);
        }
        if ((mask & TRANSPORT) != 0) {
            addEffect(
                    target,
                    KraftWorkAddon.TRANSPORTATION_LOCKED);
        }
        if ((mask & FULL_TRANSPORT) != 0) {
            addEffect(
                    target,
                    KraftWorkAddon.FULL_TRANSPORTATION_LOCKED);
        }
    }

    public static void addPositionLockEffect(
            LivingEntity target, int duration) {
        target.addEffect(new MobEffectInstance(
                KraftWorkAddon.LOCKED_POSITION,
                duration,
                0,
                false,
                false,
                true));
    }

    private static void addEffect(
            LivingEntity target,
            net.minecraft.core.Holder<
                    net.minecraft.world.effect.MobEffect> effect) {
        target.addEffect(new MobEffectInstance(
                effect,
                KraftWorkSpec.LOCK_EFFECT_DURATION,
                0,
                false,
                false,
                true));
    }

    private static void freeze(
            Entity entity, KraftWorkLockState state) {
        entity.setNoGravity(true);
        entity.setPos(state.origin());
        entity.setDeltaMovement(Vec3.ZERO);
        entity.fallDistance = 0.0F;
        entity.hasImpulse = true;
        if (entity instanceof LivingEntity living) {
            living.hurtMarked = true;
        }
    }

    private static void release(
            @Nullable LivingEntity owner,
            Entity entity,
            KraftWorkLockState state) {
        Vec3 motion = state.storedMotion();
        int energy = state.energy();
        UUID lockOwner = state.owner();
        int flightTicks = 0;
        if (entity instanceof Projectile projectile) {
            Vec3 direction = motion.lengthSqr() > 1.0E-8D
                    ? motion.normalize()
                    : owner != null
                            ? owner.getLookAngle().normalize()
                            : new Vec3(0.0D, 0.0D, 1.0D);
            double speed = projectile
                    instanceof FireworkRocketEntity
                            ? KraftWorkSpec.MAX_RELEASE_SPEED
                            : Math.min(
                                    KraftWorkSpec
                                            .ENERGY_SPEED_PER_CHARGE
                                            * energy,
                                    KraftWorkSpec
                                            .MAX_RELEASE_SPEED);
            motion = direction.scale(speed);
            flightTicks = energy / 20;
            if (projectile instanceof AbstractArrow arrow) {
                double multiplier = projectile
                        instanceof KraftWorkItemProjectile item
                                ? item.kineticDamageMultiplier()
                                : 1.0D;
                arrow.setBaseDamage(
                        arrow.getBaseDamage()
                                + KraftWorkSpec
                                        .ARROW_DAMAGE_PER_CHARGE
                                        * energy
                                        * multiplier);
            }
            if (projectile
                    instanceof KraftWorkItemProjectile item) {
                item.setReleasedEnergy(energy);
            }
            playReleaseSound(projectile, energy);
            if (owner != null) {
                StandPower power = StandPower.get(owner);
                if (power != null) {
                    power.consumeStamina(
                            KraftWorkSpec.RELEASE_STAMINA);
                }
            }
        }

        if (entity instanceof LivingEntity living) {
            clearLockEffects(living);
            if (living instanceof Mob mob) {
                mob.setNoAi(state.hadNoAi());
            }
        }
        entity.setDeltaMovement(motion);
        entity.hasImpulse = true;
        state.beginRelease(flightTicks);
        if (entity.hasData(KraftWorkAddon.HELD_STILL)) {
            entity.removeData(KraftWorkAddon.HELD_STILL);
        }
        if (flightTicks > 0) {
            entity.setNoGravity(true);
        }
        else {
            entity.setNoGravity(state.hadNoGravity());
            state.finishFlight();
            entity.getPersistentData().remove(EFFECT_PHYSICS);
        }
        LivingEntity shooter = owner != null
                ? owner
                : releasingUser(entity, lockOwner);
        if (entity instanceof TommyGunBulletEntity bullet
                && shooter != null
                && entity.level() instanceof ServerLevel level) {
            replaceBullet(level, shooter, bullet, energy);
        }
        if (entity instanceof CrazyDBlockBulletEntity bullet
                && shooter != null
                && entity.level() instanceof ServerLevel level) {
            replaceBlockBullet(level, shooter, bullet, energy);
        }
    }

    // 1.16 handed every release the Kraft Work user that held the lock, the
    // automatic ones too (tickUser :149 out of range, GameplayEventHandler
    // :269 on logout or death, a dying user included), and replaceProjectile
    // (:256-277) made the fresh bullet that user's
    @Nullable
    private static LivingEntity releasingUser(
            Entity entity, @Nullable UUID lockOwner) {
        if (lockOwner != null
                && entity.level() instanceof ServerLevel level) {
            if (level.getEntity(lockOwner) instanceof LivingEntity user) {
                return user;
            }
            for (ServerLevel other : level.getServer().getAllLevels()) {
                if (other.getEntity(lockOwner) instanceof LivingEntity user) {
                    return user;
                }
            }
        }
        return entity instanceof Projectile projectile
                && projectile.getOwner() instanceof LivingEntity user
                        ? user
                        : null;
    }

    // 1.16 KraftWorkStandType.ReleaseProjectile (:230-245), which every
    // release ran (tickUser :149, GameplayEventHandler :269): a sound by kind
    // at the projectile for every player, its pitch factors integer, none
    // for a projectile charged too little
    private static void playReleaseSound(Projectile projectile, int energy) {
        if (!(projectile.level() instanceof ServerLevel level)) {
            return;
        }
        SoundEvent sound;
        float volume = 1.0F;
        float pitch;
        if (projectile instanceof BladeHatEntity && energy > 3) {
            int f = energy > 5 ? 1 : energy / 5;
            sound = ModSoundEvents.BLADE_HAT_THROW.get();
            pitch = 0.75F + level.random.nextFloat() * (0.5F * f);
        }
        else if (projectile instanceof ThrownTrident && energy > 4) {
            int f = energy > 8 ? 1 : energy / 8;
            sound = SoundEvents.TRIDENT_THROW.value();
            pitch = f * 1.0F;
        }
        else if (projectile instanceof KnifeEntity && energy > 5) {
            sound = ModSoundEvents.KNIFE_THROW.get();
            volume = 0.5F;
            pitch = 0.4F / (level.random.nextFloat() * 0.4F + 0.8F);
        }
        else if (wasArrowIn116(projectile) && energy > 5) {
            int f = energy > 20 ? 1 : energy / 20;
            sound = SoundEvents.ARROW_SHOOT;
            pitch = 1.0F / (level.random.nextFloat() * 0.4F + 1.2F)
                    + f * 0.5F;
        }
        else if (energy > 20) {
            int f = energy > 139 ? 1 : energy / 139;
            sound = KraftWorkAddon.KRAFT_WORK_RELEASED_PROJECTILE.get();
            pitch = 1.0F / (level.random.nextFloat() * 0.4F + 1.2F)
                    + f * 0.5F;
        }
        else {
            return;
        }
        level.playSound(null, projectile, sound, SoundSource.PLAYERS,
                volume, pitch);
    }

    // an AbstractArrowEntity in 1.16 other than KWItemEntity; the core's
    // clackers, an ItemProjectileEntity there, is not an arrow here
    private static boolean wasArrowIn116(Projectile projectile) {
        return projectile instanceof AbstractArrow
                        && !(projectile instanceof KraftWorkItemProjectile)
                || projectile instanceof ClackersEntity;
    }

    // 1.16 KraftWorkStandType.ReleaseProjectile (:217-220) and
    // replaceProjectile (:267-277): a held bullet went as a fresh one, shot
    // from the old one's facing at the energy's speed, its base damage 0.05
    // an energy, and the old one was removed
    private static void replaceBullet(ServerLevel level, LivingEntity shooter,
            TommyGunBulletEntity held, int energy) {
        float damage = 0.05F * energy;
        TommyGunBulletEntity bullet =
                new TommyGunBulletEntity(shooter, level) {
                    @Override
                    protected float getBaseDamage() {
                        return damage;
                    }
                };
        bullet.copyPosition(held);
        bullet.shootFromRotation(held,
                (float) Math.min(
                        KraftWorkSpec.ENERGY_SPEED_PER_CHARGE * energy,
                        KraftWorkSpec.MAX_RELEASE_SPEED),
                0.0F);
        level.addFreshEntity(bullet);
        held.discard();
    }

    // 1.16 replaceProjectile (:256-266) did the same for a held Crazy Diamond
    // block bullet: a fresh one with no block, the releasing user's
    private static void replaceBlockBullet(ServerLevel level,
            LivingEntity shooter, CrazyDBlockBulletEntity held, int energy) {
        float damage = 0.05F * energy;
        CrazyDBlockBulletEntity bullet =
                new CrazyDBlockBulletEntity(shooter, level) {
                    @Override
                    protected float getBaseDamage() {
                        return damage;
                    }
                };
        bullet.copyPosition(held);
        bullet.shootFromRotation(held,
                (float) Math.min(
                        KraftWorkSpec.ENERGY_SPEED_PER_CHARGE * energy,
                        KraftWorkSpec.MAX_RELEASE_SPEED),
                0.0F);
        level.addFreshEntity(bullet);
        held.discard();
    }

    private static void tickReleaseFlight(
            Entity entity, KraftWorkLockState state) {
        if (state.flightTicks() <= 0) {
            return;
        }
        entity.setNoGravity(true);
        state.tickFlight();
        if (state.flightTicks() == 0) {
            entity.setNoGravity(state.hadNoGravity());
            state.finishFlight();
            entity.getPersistentData().remove(EFFECT_PHYSICS);
        }
    }

    private static boolean matches(
            LivingEntity owner,
            Entity entity,
            KraftWorkLockState state,
            ReleaseFilter filter) {
        // 1.16 freed a held eye of ender with Release Projectile, charged
        // or not, and with Release All; no other filter took it.
        if (entity instanceof EyeOfEnder) {
            return filter == ReleaseFilter.ALL
                    || filter == ReleaseFilter.NEARBY;
        }
        // 1.16 counted a firework or any fireball (AbstractFireballEntity)
        // as charged.
        boolean specialReady =
                entity instanceof FireworkRocketEntity
                        || entity instanceof Fireball;
        boolean pearl =
                entity instanceof ThrownEnderpearl
                        || heldItem(entity).is(Items.ENDER_PEARL);
        boolean beneficial = isBeneficial(entity);
        return switch (filter) {
            case NEARBY -> state.ready() || specialReady;
            case ALL -> true;
            case READY ->
                    (state.ready() || specialReady)
                            && !pearl
                            && !beneficial;
            case NOT_READY ->
                    !state.ready()
                            && !specialReady
                            && !pearl
                            && !beneficial;
            case ENDER_PEARL -> pearl;
            case BENEFICIAL -> beneficial;
        };
    }

    // 1.16 KraftWorkReleaseBeneficial.BeneficialItems (:69-85): a thrown
    // potion by its first effect, none (water, awkward) not beneficial
    private static boolean isBeneficial(Entity entity) {
        if (entity instanceof ThrownPotion potion) {
            for (MobEffectInstance effect : potion.getItem().getOrDefault(
                            DataComponents.POTION_CONTENTS,
                            PotionContents.EMPTY)
                    .getAllEffects()) {
                return effect.getEffect().value().isBeneficial();
            }
            return false;
        }
        return entity instanceof KraftWorkItemProjectile item
                && item.isBeneficial();
    }

    private static ItemStack heldItem(Entity entity) {
        return entity instanceof ItemSupplier supplier
                ? supplier.getItem()
                : ItemStack.EMPTY;
    }

    private static boolean canLock(
            LivingEntity owner, Entity target) {
        return target.isAlive() && canLockDying(owner, target);
    }

    // 1.16 Lock Target (KraftWorkLockTarget :184-244, no alive check) also
    // locked and charged for a dying mob, which its crosshair still picked
    private static boolean canLockDying(
            LivingEntity owner, Entity target) {
        return owner.level() instanceof ServerLevel
                && !target.isRemoved()
                && target.level() == owner.level()
                && owner.distanceToSqr(target)
                        <= KraftWorkSpec.LOCK_RANGE_SQR;
    }

    /**
     * Central non-living whitelist shared by preflight and execution. It
     * keeps the donor target kinds and the port's projectile support, while
     * rejecting dropped {@code ItemEntity} targets. Place Projectile creates
     * the dedicated lockable item representation.
     */
    public static boolean isLockableNonLiving(Entity target) {
        return target instanceof Projectile
                || target instanceof Boat
                || target instanceof AbstractMinecart
                || target instanceof PrimedTnt
                || target instanceof FallingBlockEntity
                || target instanceof RoadRollerEntity;
    }

    // 1.16 KraftWorkBlock also stopped an eye of ender or an armor stand
    // that came at the guard, beside the Lock Target vehicles.
    public static boolean isGuardVehicle(Entity target) {
        return target instanceof EyeOfEnder
                || target instanceof ArmorStand
                || !(target instanceof Projectile)
                        && !(target instanceof LivingEntity)
                        && isLockableNonLiving(target);
    }

    // 1.16 froze these whole (canUpdate false): the Lock Target kinds, an
    // armor stand, which it never locked piece by piece, and an eye of
    // ender the guard stopped.
    private static boolean isWholeLockable(Entity target) {
        return target instanceof ArmorStand
                || target instanceof EyeOfEnder
                || !(target instanceof LivingEntity)
                        && isLockableNonLiving(target);
    }

    private static boolean isTwoHanded(Item item) {
        return item instanceof GlovesItem
                || item instanceof BowItem
                || item instanceof CrossbowItem
                || item instanceof SatiporojaScarfItem;
    }

    private static boolean isStandItem(ItemStack stack) {
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem())
                .getPath();
        return path.contains("stand_disc")
                || path.contains("stand_arrow");
    }
}
