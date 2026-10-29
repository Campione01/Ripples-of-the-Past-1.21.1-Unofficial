package rotp.addons.thehand.ability;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import rotp.core.customobjects.DamageSourceModified;
import rotp.core.customobjects.RoadRollerEntity;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.util.functions.DamageUtil;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;

import rotp.addons.thehand.TheHandAddon;
import rotp.addons.thehand.TheHandConfig;
import rotp.addons.thehand.state.ErasedHealthService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class TheHandCombat {
    public static final double TELEPORT_RANGE = 12.0D;
    public static final double LARGE_TARGET_THRESHOLD = 1.5D;
    public static final double SIZE_CURVE_THRESHOLD = 1.09D;
    public static final double MIN_ERASE_SPACE = 0.05D;
    public static final ResourceKey<DamageType> ERASURE_DAMAGE =
            ResourceKey.create(
                    Registries.DAMAGE_TYPE,
                    TheHandAddon.id("erasure"));

    private static final ResourceLocation AQUA_NECKLACE =
            ResourceLocation.fromNamespaceAndPath(
                    "rotp_aqua_necklace", "aqua_necklace");
    private static final ResourceLocation SPACE_RIPPER_STINGY_EYES =
            ResourceLocation.fromNamespaceAndPath(
                    "jojo_ripples", "space_ripper_stingy_eyes");

    private TheHandCombat() {}

    public static float eraseDamage(
            Entity target, StandEntity stand, float preparation) {
        // 1.16 TheHandErase.getEraseDamage multiplied by the unclamped preparation: a charge fired by a maximum that
        // shrank mid-hold has more than 1
        float appliedPreparation = Math.max(preparation, 0.0F);
        if (TheHandConfig.PERCENT_DAMAGE.get()
                && target instanceof LivingEntity living) {
            float factor = averageSize(living) > LARGE_TARGET_THRESHOLD
                    ? TheHandEraseAbility.LARGE_TARGET_FACTOR
                    : TheHandEraseAbility.SMALL_TARGET_FACTOR;
            return living.getMaxHealth()
                    * factor
                    * eraseSpace(living)
                    * appliedPreparation;
        }
        return StandStatFormulas.getHeavyAttackDamage(16.0D)
                * appliedPreparation;
    }

    public static float barrageDamage(
            Entity target, StandEntity stand, int hits) {
        int appliedHits = Math.max(hits, 1);
        if (TheHandConfig.PERCENT_DAMAGE.get()
                && target instanceof LivingEntity living) {
            float factor = averageSize(living) > LARGE_TARGET_THRESHOLD
                    ? TheHandErasureBarrageAbility
                            .LARGE_TARGET_FACTOR_PER_HIT
                    : TheHandErasureBarrageAbility
                            .SMALL_TARGET_FACTOR_PER_HIT;
            return living.getMaxHealth()
                    * factor
                    * eraseSpace(living)
                    * appliedHits;
        }
        return StandStatFormulas.getBarrageHitDamage(
                        16.0D, stand.getPrecision())
                * appliedHits;
    }

    public static boolean attack(
            StandEntity stand,
            Entity target,
            float damage,
            float preparation,
            boolean barrage) {
        if (!(stand.level() instanceof ServerLevel)
                || target == null
                || target == stand
                || target == stand.getUser()
                || !Float.isFinite(damage)
                || damage <= 0.0F) {
            return false;
        }
        DamageSource source = makeErasureDamageSource(stand);
        float absorptionBefore = target instanceof LivingEntity living
                ? ErasedHealthService.absorptionBeforeHit(living)
                : 0.0F;
        boolean hurt = EntityActionInstance.standEntityAttack(
                stand, target, source, damage);
        if (!hurt) {
            return false;
        }
        if (target instanceof LivingEntity living) {
            ErasedHealthService.applyAfterSuccessfulHit(
                    stand, living, damage, absorptionBefore);
            erodeArmor(
                    living,
                    barrage ? 1.0F : Math.max(preparation, 0.0F),
                    barrage);
        } else if (target instanceof RoadRollerEntity roadRoller) {
            ErasedHealthService.applyRoadRollerAfterSuccessfulHit(
                    stand, roadRoller, damage);
        }
        return true;
    }

    public static int eraseSweep(
            StandEntity stand,
            float preparation,
            int barrageHits) {
        if (stand.level().isClientSide()) {
            return 0;
        }
        double reach = Math.max(stand.getEffectiveRange(), 1.0D);
        List<Entity> targets = stand.level().getEntities(
                stand,
                stand.getBoundingBox().inflate(reach),
                target -> canSweepErase(stand, target));
        int erased = 0;
        for (Entity target : targets) {
            if (isOptionalVaporStand(target)) {
                float damage = barrageHits > 0
                        ? barrageDamage(target, stand, barrageHits)
                        : eraseDamage(target, stand, preparation);
                if (attack(
                        stand,
                        target,
                        damage,
                        preparation,
                        barrageHits > 0)) {
                    erased++;
                }
                continue;
            }
            Vec3 direction = stand.getLookAngle();
            double speed = target.getDeltaMovement().length();
            if (direction.lengthSqr() > 1.0E-8D) {
                target.setDeltaMovement(
                        direction.normalize().scale(speed));
            }
            target.discard();
            erased++;
        }
        return erased;
    }

    // 1.16 TheHandEntity.isErasing, which TheHandModel.prepareMobModel drew
    // as the erase effect on the right forearm: set by an Erase hold on the
    // tick it reached getHoldDurationToFire (TheHandErase.onHoldTick :130-135)
    // and by Erase Item and the Erasure Barrage as their task began, cleared
    // when their task stopped, the barrage's at its first recovery tick.
    public static boolean isErasing(@Nullable EntityActionInstance action) {
        if (action == null || action.getPhase() == null || action.isOver()) {
            return false;
        }
        // 1.16 set the flag in onHoldTick at tick getHoldDurationToFire: an
        // Erase with a maximum below 2 never held that long
        if (action instanceof TheHandEraseAbility.EraseAction) {
            int ticksToFire = TheHandEraseAbility.holdTicksToFire(action);
            return ticksToFire > 0
                    && (action.getPhase() != ActionPhase.BUTTON_CHARGE
                            || action.getPhaseTick() >= ticksToFire);
        }
        if (action instanceof TheHandErasureBarrageAbility
                .ErasureBarrageAction) {
            return action.getPhase() != ActionPhase.RECOVERY;
        }
        return action instanceof TheHandEraseItemAbility.EraseItemAction;
    }

    // 1.16 isCancelable in RECOVERY: a light attack or a heavy one
    // (canFollowUpBarrage) cuts the Erase recovery short.
    public static boolean followsUpErase(EntityActionType next) {
        return next instanceof StandEntityPunchAbility
                || next instanceof StandEntityHeavyPunchAbility
                || next instanceof TheHandEraseAbility;
    }

    // recoveryCompleted: 1.16 recoveryCount == 8 when the pull ran.
    // wallsHideEntities false: 1.16 TheHandErase.Teleport's rayTrace, an
    // entity behind a block within 12 blocks is pulled or teleported to.
    public static void teleportAfterMiss(
            LivingEntity user,
            StandEntity stand,
            boolean attract,
            boolean recoveryCompleted,
            boolean wallsHideEntities) {
        if (user == null
                || stand == null
                || user.level().isClientSide()) {
            return;
        }
        LivingEntity source = stand.isManuallyControlled()
                ? stand
                : user;
        RayTrace trace = rayTrace(source, stand, user, wallsHideEntities);
        if (attract) {
            attractTarget(user, stand, source, trace.entity(),
                    recoveryCompleted);
        } else {
            teleportSource(user, stand, source, trace.location());
        }
    }

    private static DamageSource makeErasureDamageSource(
            StandEntity stand) {
        DamageSource source = DamageUtil.make(
                stand.level(),
                ERASURE_DAMAGE,
                stand,
                stand.getUser());
        if (source instanceof DamageSourceModified modified) {
            modified.jojo_ripples$setStandPower(
                    StandPower.get(stand.getUser()));
            modified.jojo_ripples$modifyKnockback(0.0F, 0.0F);
        }
        return source;
    }

    private static double averageSize(Entity entity) {
        return entity.getBoundingBox().getSize();
    }

    private static float eraseSpace(Entity entity) {
        double size = averageSize(entity);
        double value = size > SIZE_CURVE_THRESHOLD
                ? 1.0D - size / 5.0D
                : 1.0D - (size - 1.0D);
        return (float) Math.max(value, MIN_ERASE_SPACE);
    }

    private static boolean canSweepErase(
            StandEntity stand, Entity target) {
        if (target == stand
                || target == stand.getUser()
                || target.isInvulnerable()
                || target.is(
                        stand.getUser() != null
                                ? stand.getUser().getVehicle()
                                : null)
                || (stand.getUser() != null
                        && target.hasPassenger(stand.getUser()))
                || SPACE_RIPPER_STINGY_EYES.equals(
                        BuiltInRegistries.ENTITY_TYPE.getKey(
                                target.getType()))) {
            return false;
        }
        return target instanceof Boat
                || target instanceof AbstractMinecart
                || target instanceof PrimedTnt
                || target instanceof FallingBlockEntity
                || target instanceof EyeOfEnder
                || target instanceof ArmorStand
                || target instanceof Projectile
                || target instanceof EndCrystal
                || target instanceof ItemFrame
                || isOptionalVaporStand(target);
    }

    private static boolean isOptionalVaporStand(Entity target) {
        return target instanceof StandEntity
                && AQUA_NECKLACE.equals(
                        BuiltInRegistries.ENTITY_TYPE.getKey(
                                target.getType()))
                && !target.isPickable();
    }

    private static void erodeArmor(
            LivingEntity target, float preparation, boolean barrage) {
        damageArmor(
                target,
                EquipmentSlot.HEAD,
                barrage ? 0.02F : 0.10F * preparation);
        damageArmor(
                target,
                EquipmentSlot.CHEST,
                barrage ? 0.02F : 0.15F * preparation);
        damageArmor(
                target,
                EquipmentSlot.LEGS,
                barrage ? 0.02F : 0.10F * preparation);
        damageArmor(
                target,
                EquipmentSlot.FEET,
                barrage ? 0.02F : 0.05F * preparation);
    }

    private static void damageArmor(
            LivingEntity target,
            EquipmentSlot slot,
            float fraction) {
        ItemStack armor = target.getItemBySlot(slot);
        if (armor.isEmpty()) {
            return;
        }
        if (!armor.isDamageableItem()) {
            armor.shrink(1);
            return;
        }
        int damage = armor.getDamageValue()
                + (int) (armor.getMaxDamage() * fraction);
        if (damage < armor.getMaxDamage()) {
            armor.setDamageValue(damage);
        } else {
            armor.shrink(1);
        }
    }

    // 1.16 JojoModUtil.rayTrace(eyes, look, 12, ..., 0.0, 0.0): hitboxes
    // grown by the pick radius only, blocks by their outline shape, and an
    // entity in the source's own root vehicle skipped unless the rider may
    // interact with it.
    private static RayTrace rayTrace(
            LivingEntity source,
            StandEntity stand,
            LivingEntity user,
            boolean wallsHideEntities) {
        Level level = source.level();
        Vec3 start = source.getEyePosition(1.0F);
        Vec3 direction = source.getLookAngle();
        Vec3 fullEnd = start.add(direction.scale(TELEPORT_RANGE));
        BlockHitResult blockHit = level.clip(new ClipContext(
                start,
                fullEnd,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                source));
        Vec3 end = blockHit.getType() == HitResult.Type.BLOCK
                ? blockHit.getLocation()
                : fullEnd;
        Vec3 entityEnd = wallsHideEntities ? end : fullEnd;
        AABB search = source.getBoundingBox()
                .expandTowards(entityEnd.subtract(start))
                .inflate(1.0D);
        Entity closest = null;
        Vec3 closestPoint = end;
        double closestDistance = start.distanceToSqr(entityEnd);
        for (Entity candidate : level.getEntities(
                source,
                search,
                candidate -> candidate != stand
                        && candidate != user
                        && candidate.isAlive()
                        && candidate.isPickable()
                        && (candidate.getRootVehicle()
                                        != source.getRootVehicle()
                                || candidate.canRiderInteract()))) {
            AABB hitbox = candidate.getBoundingBox().inflate(
                    candidate.getPickRadius());
            Optional<Vec3> intersection = hitbox.contains(start)
                    ? Optional.of(start)
                    : hitbox.clip(start, entityEnd);
            if (intersection.isEmpty()) {
                continue;
            }
            double distance = start.distanceToSqr(intersection.get());
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = candidate;
                closestPoint = intersection.get();
            }
        }
        return new RayTrace(closest, closestPoint);
    }

    private static void attractTarget(
            LivingEntity user,
            StandEntity stand,
            LivingEntity source,
            Entity target,
            boolean recoveryCompleted) {
        if (target == null || !source.isAlive()) {
            return;
        }
        Entity attracted = target;
        // 1.16: the held item is pulled out only after a full recovery.
        if (recoveryCompleted && user.isShiftKeyDown()) {
            Entity disarmed = disarmOne(user.level(), target);
            if (disarmed != null) {
                attracted = disarmed;
            }
        }
        if (attracted.isPassenger()) {
            attracted.stopRiding();
        }
        if (attracted instanceof LivingEntity living) {
            // 1.16 loseTarget only when the recovery was cut short.
            if (!recoveryCompleted
                    && living instanceof Mob mob
                    && mob.getTarget() == user) {
                mob.setTarget(null);
            }
            living.addEffect(
                    new MobEffectInstance(
                            TheHandAddon.SURPRISE,
                            10,
                            0,
                            false,
                            false,
                            true),
                    user);
            if (stand.getFinisherMeter() < 0.45F) {
                stand.addFinisherMeter(
                        0.45F - stand.getFinisherMeter(), 40);
            }
        }
        Vec3 destination = source.getEyePosition(1.0F).add(
                source.getLookAngle().scale(source.getBbWidth()));
        Vec3 movement = destination.subtract(attracted.position());
        if (movement.lengthSqr() > TELEPORT_RANGE * TELEPORT_RANGE) {
            movement = movement.normalize().scale(TELEPORT_RANGE);
        }
        Vec3 result = attracted.position().add(movement);
        attracted.teleportTo(result.x, result.y, result.z);
    }

    private static Entity disarmOne(Level level, Entity target) {
        ItemStack dropped = ItemStack.EMPTY;
        if (target instanceof LivingEntity living) {
            InteractionHand hand = !living.getMainHandItem().isEmpty()
                    ? InteractionHand.MAIN_HAND
                    : !living.getOffhandItem().isEmpty()
                            ? InteractionHand.OFF_HAND
                            : null;
            if (hand != null) {
                ItemStack held = living.getItemInHand(hand);
                dropped = held.copyWithCount(1);
                held.shrink(1);
            }
        } else if (target instanceof ItemFrame frame
                && !frame.getItem().isEmpty()) {
            dropped = frame.getItem().copy();
            frame.setItem(ItemStack.EMPTY);
        }
        if (dropped.isEmpty()) {
            return null;
        }
        ItemEntity item = new ItemEntity(
                level,
                target.getX(),
                target.getY() - 0.4D,
                target.getZ(),
                dropped);
        item.setPickUpDelay(40);
        level.addFreshEntity(item);
        return item;
    }

    private static void teleportSource(
            LivingEntity user,
            StandEntity stand,
            LivingEntity source,
            Vec3 destination) {
        if (source.isPassenger()) {
            source.stopRiding();
        }
        for (Mob mob : user.level().getEntitiesOfClass(
                Mob.class,
                user.getBoundingBox().inflate(8.0D),
                mob -> mob.getTarget() == user)) {
            Vec3 towardDestination =
                    destination.subtract(mob.getEyePosition());
            if (towardDestination.lengthSqr() <= 1.0E-8D
                    || mob.getLookAngle().dot(
                            towardDestination.normalize()) >= 0.0D) {
                mob.setTarget(null);
            }
        }
        source.teleportTo(
                destination.x, destination.y, destination.z);
        AABB zone = new AABB(destination, destination).inflate(3.0D);
        boolean threatened = !source.level().getEntitiesOfClass(
                LivingEntity.class,
                zone,
                living -> living != stand
                        && living != user
                        && living.isAlive()
                        && (living instanceof Player
                                || living instanceof StandEntity
                                || living instanceof Enemy))
                .isEmpty();
        if (threatened) {
            float amount = stand.getFinisherMeter() < 0.45F
                    ? 0.45F - stand.getFinisherMeter()
                    : 0.1F;
            stand.addFinisherMeter(amount, 40);
        }
    }

    private record RayTrace(Entity entity, Vec3 location) {}
}
