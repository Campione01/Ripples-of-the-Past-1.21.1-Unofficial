package rotp.addons.diverdown.entity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import rotp.core.JojoModConfig;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.util.functions.DamageUtil;

import rotp.addons.diverdown.DiverDownAddon;
import rotp.addons.diverdown.DiverDownDamageTypes;
import rotp.addons.diverdown.state.DiverDownAuthority;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;

public final class KineticTrapEntity extends Entity {
    // 1.16 checked tickCount > 1200, which the world loop and tick() each raised once a game tick: 600 game ticks.
    public static final int IDLE_LIFETIME = 600;
    public static final int ACTIVE_LIFETIME = 15;
    public static final float DAMAGE = 7.0F;
    public static final double TRIGGER_RANGE = 1.1D;

    private static final EntityDataAccessor<Optional<UUID>>
            OWNER_UUID = SynchedEntityData.defineId(
                    KineticTrapEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> TRIGGERED =
            SynchedEntityData.defineId(
                    KineticTrapEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Long> EXPIRES_AT =
            SynchedEntityData.defineId(
                    KineticTrapEntity.class,
                    EntityDataSerializers.LONG);
    private int armTicks;

    public KineticTrapEntity(
            EntityType<? extends KineticTrapEntity> type,
            Level level) {
        super(type, level);
        noPhysics = true;
    }

    public KineticTrapEntity(
            LivingEntity owner,
            DiverDownStandEntity stand,
            Level level) {
        this(DiverDownAddon.KINETIC_TRAP_ENTITY.get(), level);
        bind(owner, stand);
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        builder.define(OWNER_UUID, Optional.empty());
        builder.define(TRIGGERED, false);
        builder.define(EXPIRES_AT, 0L);
    }

    public UUID getOwnerUuid() {
        return entityData.get(OWNER_UUID).orElse(null);
    }

    public boolean isTriggered() {
        return entityData.get(TRIGGERED);
    }

    public long expiresAt() {
        return entityData.get(EXPIRES_AT);
    }

    // The Stand sets the trap; from then on the trap answers to its owner only.
    public void bind(
            LivingEntity owner, DiverDownStandEntity stand) {
        if (!(level() instanceof ServerLevel level)
                || !DiverDownAuthority.validController(
                        owner,
                        stand,
                        DiverDownAddon.KINETIC_TRAP_NAME)) {
            discard();
            return;
        }
        entityData.set(
                OWNER_UUID,
                Optional.of(owner.getUUID()),
                true);
        entityData.set(
                EXPIRES_AT,
                level.getGameTime() + IDLE_LIFETIME,
                true);
    }

    public int armTicks() {
        return armTicks;
    }

    /**
     * How far the arm of a trap that fired {@code armTicks} ticks ago is thrust out, in model pixels: 1.16
     * KineticTrapEntity.updateAttackTick moved attackTime down 1.5 a tick to -8 or past it, then up 1.2 a tick to 0
     * or past it, and so on, and the arm model was set off by -attackTime.
     */
    public static float armThrust(int armTicks) {
        float attackTime = 0.0F;
        boolean attacking = false;
        for (int tick = 0; tick < armTicks; tick++) {
            if (attacking) {
                attackTime += 1.2F;
                attacking = attackTime < 0.0F;
            } else {
                attackTime -= 1.5F;
                attacking = attackTime <= -8.0F;
            }
        }
        return -attackTime;
    }

    @Override
    public void tick() {
        super.tick();
        noPhysics = true;
        setDeltaMovement(0.0D, 0.0D, 0.0D);
        if (isTriggered()) {
            armTicks++;
        }
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        // 1.16 KineticTrapEntity.tick: the trap goes with the block it hides in
        if (serverLevel.getBlockState(blockPosition()).isAir()
                || serverLevel.getGameTime() >= expiresAt()) {
            discard();
            return;
        }
        if (isTriggered()) {
            return;
        }
        // 1.16 getOwner: an owner away from this level leaves the trap waiting
        LivingEntity owner = owner(serverLevel);
        if (owner == null) {
            return;
        }
        // 1.16 KineticTrapEntity.tick (049 :143-166): the trap turned to the first living entity near it, its owner too,
        // and fired only if it might hit that one (canDamage, then activate's invulnerability check)
        List<LivingEntity> near = serverLevel.getEntitiesOfClass(
                LivingEntity.class, getBoundingBox().inflate(TRIGGER_RANGE));
        if (!near.isEmpty()) {
            LivingEntity target = near.get(0);
            lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
            if (canDamage(owner, target) && !target.isInvulnerable()) {
                activate(serverLevel, owner, target);
            }
        }
    }

    // 1.16 KineticTrapEntity.canDamage
    private static boolean canDamage(
            LivingEntity owner, LivingEntity entity) {
        if (entity == owner
                || entity instanceof TamableAnimal pet
                        && pet.getOwner() == owner
                || entity instanceof ArmorStand
                || entity.isAlliedTo(owner)) {
            return false;
        }
        StandPower power = StandPower.get(owner);
        return power == null
                || entity != power.getSummonedStandEntity();
    }

    // 1.16 KineticTrapEntity.activate: the trap fires whether or not the
    // hit lands, and the hit goes through the target's hurt cooldown.
    private void activate(
            ServerLevel level, LivingEntity owner, LivingEntity target) {
        // the arm thrusts the way the trap faces; the rotation reaches
        // clients with the trigger
        lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        entityData.set(TRIGGERED, true, true);
        // Entity.playSound, heard by every player near the trap
        playSound(DiverDownAddon.DIVER_DOWN_PUNCH_LIGHT.get(), 1.0F, 1.0F);
        entityData.set(
                EXPIRES_AT,
                level.getGameTime() + ACTIVE_LIFETIME,
                true);
        DamageUtil.hurtThroughInvulTicks(
                target,
                DamageUtil.make(
                        level,
                        DiverDownDamageTypes.KINETIC_TRAP,
                        this,
                        owner),
                DAMAGE * JojoModConfig.getCommonConfigInstance(false)
                        .standDamageMultiplier.get().floatValue());
    }

    private LivingEntity owner(ServerLevel level) {
        UUID ownerUuid = getOwnerUuid();
        return ownerUuid != null
                        && level.getEntity(ownerUuid)
                                instanceof LivingEntity owner
                ? owner
                : null;
    }

    public static int countOwned(
            ServerLevel level, UUID ownerUuid) {
        int count = 0;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof KineticTrapEntity trap
                    && !trap.isRemoved()
                    && ownerUuid.equals(trap.getOwnerUuid())) {
                count++;
            }
        }
        return count;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        UUID owner = getOwnerUuid();
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
    }

    // 1.16 saved the owner only and tickCount began at 0 again: a trap loaded from its chunk waits with a whole life
    // ahead of it, so no absolute end time is kept (it would be past for a trap whose chunk slept long).
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(
                OWNER_UUID,
                tag.hasUUID("Owner")
                        ? Optional.of(tag.getUUID("Owner"))
                        : Optional.empty());
        entityData.set(TRIGGERED, false);
        entityData.set(
                EXPIRES_AT, level().getGameTime() + IDLE_LIFETIME);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return false;
    }
}
