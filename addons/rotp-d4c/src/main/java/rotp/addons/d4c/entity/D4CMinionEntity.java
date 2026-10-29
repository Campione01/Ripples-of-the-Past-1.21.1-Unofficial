package rotp.addons.d4c.entity;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.addons.d4c.D4CAddon;
import rotp.addons.d4c.D4CPhase2BSpec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class D4CMinionEntity extends PathfinderMob {
    private static final EntityDataAccessor<Optional<UUID>> OWNER =
            SynchedEntityData.defineId(
                    D4CMinionEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Byte> AI_MODE =
            SynchedEntityData.defineId(
                    D4CMinionEntity.class,
                    EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<String> PROFILE_NAME =
            SynchedEntityData.defineId(
                    D4CMinionEntity.class,
                    EntityDataSerializers.STRING);
    // 1.16 ag: RangedAttackGoal(1.0, 20, 15), smart shot every 15 ticks
    private static final double RANGED_RANGE = 15.0D;
    private static final int RANGED_INTERVAL = 20;
    private static final int SMART_RANGED_INTERVAL = 15;
    // 1.16 ag.func_82196_d: 6 shots, then 90 ticks
    private static final int COLT_RELOAD_TICKS = 90;
    // 1.16 ag.ab: monsters within 40 blocks of the owner
    private static final double HUNT_RADIUS = 40.0D;
    private int attackCooldown;
    private int orphanTicks;
    private int coltShots;
    private int coltReload;
    private int smartTactic = D4CMinionAiMode.TACTIC_RANGED;
    private int tacticCooldown;
    private int selfHurtStamp;
    private int ownerHurtByStamp;
    private int ownerHurtStamp;
    private final D4CMinionLifetime lifetime = new D4CMinionLifetime();

    public D4CMinionEntity(
            EntityType<? extends D4CMinionEntity> type,
            Level level) {
        super(type, level);
        xpReward = 0;
        setCanPickUpLoot(false);
        // mode weapons never drop
        setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                // 1.16 ag.java:363; mode weapons add their own damage
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.FOLLOW_RANGE, D4CPhase2BSpec.MINION_RADIUS);
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER, Optional.empty());
        builder.define(AI_MODE, (byte) D4CMinionAiMode.DEFAULT);
        builder.define(PROFILE_NAME, "");
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(
                7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    public void setOwner(ServerPlayer owner) {
        entityData.set(OWNER, Optional.of(owner.getUUID()));
        entityData.set(
                PROFILE_NAME, owner.getGameProfile().getName());
        setCustomName(owner.getDisplayName().copy());
        setCustomNameVisible(false);
    }

    public UUID profileId() {
        return entityData.get(OWNER).orElse(null);
    }

    public String profileName() {
        return entityData.get(PROFILE_NAME);
    }

    public boolean isOwnedBy(UUID owner) {
        return entityData.get(OWNER).filter(owner::equals).isPresent();
    }

    public int aiMode() {
        return D4CMinionAiMode.sanitize(
                Byte.toUnsignedInt(entityData.get(AI_MODE)));
    }

    // 1.16 ag.ae(int): reset goals, then equip the mode weapon
    public void setAiMode(int mode) {
        int applied = D4CMinionAiMode.sanitize(mode);
        entityData.set(AI_MODE, (byte) applied);
        getNavigation().stop();
        setTarget(null);
        smartTactic = D4CMinionAiMode.TACTIC_RANGED;
        tacticCooldown = 0;
        coltShots = 0;
        equip(D4CMinionAiMode.weapon(applied));
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        if (lifetime.tickAndShouldExpire()) {
            discard();
            return;
        }
        if (attackCooldown > 0) {
            attackCooldown--;
        }
        if (coltReload > 0) {
            coltReload--;
        }
        if (tacticCooldown > 0) {
            tacticCooldown--;
        }
        LivingEntity owner = resolveOwner(level);
        if (owner == null || !owner.isAlive()) {
            if (++orphanTicks >= 40) {
                discard();
            }
            return;
        }
        orphanTicks = 0;
        int mode = aiMode();
        if (!D4CMinionAiMode.followsOwner(mode)) {
            // 1.16 wait: no target, no movement
            setTarget(null);
            getNavigation().stop();
            return;
        }
        if (distanceToSqr(owner)
                > D4CPhase2BSpec.MINION_RADIUS
                        * D4CPhase2BSpec.MINION_RADIUS) {
            getNavigation().moveTo(owner, 1.2D);
            return;
        }

        LivingEntity target = D4CMinionAiMode.fights(mode)
                ? pickTarget(owner)
                : null;
        if (target == null) {
            setTarget(null);
            if (distanceToSqr(owner) > 9.0D) {
                getNavigation().moveTo(owner, 1.05D);
            }
            return;
        }
        getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (mode == D4CMinionAiMode.SMART) {
            smartAttack(target);
            return;
        }
        equip(D4CMinionAiMode.weapon(mode));
        if (mode == D4CMinionAiMode.RANGED) {
            rangedAttack(target, RANGED_INTERVAL);
        } else {
            attack(target);
        }
    }

    // 1.16 HurtByTarget, OwnerHurtByTarget, OwnerHurtTarget, then monsters
    @Nullable
    private LivingEntity pickTarget(LivingEntity owner) {
        LivingEntity current = getTarget();
        if (validTarget(current, owner)) {
            return current;
        }
        LivingEntity picked = null;
        if (getLastHurtByMobTimestamp() != selfHurtStamp) {
            selfHurtStamp = getLastHurtByMobTimestamp();
            picked = accept(getLastHurtByMob(), owner);
        }
        if (picked == null
                && owner.getLastHurtByMobTimestamp() != ownerHurtByStamp) {
            ownerHurtByStamp = owner.getLastHurtByMobTimestamp();
            picked = accept(owner.getLastHurtByMob(), owner);
        }
        if (picked == null
                && owner.getLastHurtMobTimestamp() != ownerHurtStamp) {
            ownerHurtStamp = owner.getLastHurtMobTimestamp();
            picked = accept(owner.getLastHurtMob(), owner);
        }
        if (picked == null && (tickCount + getId()) % 10 == 0) {
            picked = nearestMonster(owner);
        }
        setTarget(picked);
        return picked;
    }

    @Nullable
    private LivingEntity accept(
            @Nullable LivingEntity candidate, LivingEntity owner) {
        return validTarget(candidate, owner) ? candidate : null;
    }

    private boolean validTarget(
            @Nullable LivingEntity target, LivingEntity owner) {
        return target != null
                && target.isAlive()
                && target != owner
                && target != this
                && !target.isAlliedTo(owner)
                && !(target instanceof D4CMinionEntity other
                        && other.isOwnedBy(owner.getUUID()))
                && canAttack(target)
                && distanceToSqr(target)
                        <= D4CPhase2BSpec.MINION_RADIUS
                                * D4CPhase2BSpec.MINION_RADIUS;
    }

    // 1.16 ag.ad: Colt at range, sword up close
    private void smartAttack(LivingEntity target) {
        int tactic = D4CMinionAiMode.smartTactic(
                smartTactic, distanceTo(target));
        if (tactic != smartTactic && tacticCooldown <= 0) {
            smartTactic = tactic;
            tacticCooldown = 20;
        }
        if (smartTactic == D4CMinionAiMode.TACTIC_MELEE) {
            equip(D4CMinionAiMode.Weapon.SWORD);
            attack(target);
        } else {
            equip(D4CMinionAiMode.Weapon.COLT);
            rangedAttack(target, SMART_RANGED_INTERVAL);
        }
    }

    private void rangedAttack(LivingEntity target, int interval) {
        boolean inRange = distanceToSqr(target)
                <= RANGED_RANGE * RANGED_RANGE;
        boolean sees = getSensing().hasLineOfSight(target);
        if (inRange && sees) {
            getNavigation().stop();
        } else {
            getNavigation().moveTo(target, 1.0D);
        }
        if (inRange && sees && attackCooldown <= 0 && coltReload <= 0) {
            shootColt(target);
            attackCooldown = interval;
        }
    }

    // 1.16 ag.func_82196_d
    private void shootColt(LivingEntity target) {
        Vec3 look = getLookAngle();
        Vec3 side = look.cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize();
        Vec3 up = side.cross(look).normalize();
        Vec3 muzzle = getEyePosition()
                .add(look.scale(0.8D))
                .add(side.scale(0.1D))
                .add(up.scale(-0.1D));
        Vec3 aim = target.getEyePosition().subtract(muzzle).normalize()
                .add((random.nextDouble() - 0.5D) * 0.02D,
                        (random.nextDouble() - 0.5D) * 0.02D,
                        (random.nextDouble() - 0.5D) * 0.02D)
                .normalize();
        D4CColtBulletEntity bullet =
                new D4CColtBulletEntity(this, level());
        bullet.setPos(muzzle);
        bullet.setDeltaMovement(aim.scale(2.5D));
        level().addFreshEntity(bullet);
        level().playSound(
                null,
                getX(),
                getY(),
                getZ(),
                D4CAddon.COLT_SHOOT.get(),
                SoundSource.HOSTILE,
                0.4F,
                0.6F + random.nextFloat() * 0.4F);
        if (++coltShots >= D4CPhase2BSpec.COLT_AMMO) {
            coltShots = 0;
            coltReload = COLT_RELOAD_TICKS;
        }
    }

    private void equip(D4CMinionAiMode.Weapon weapon) {
        ItemStack held = getMainHandItem();
        switch (weapon) {
            case COLT -> {
                if (!held.is(D4CAddon.COLT_CUSTOM.get())) {
                    setItemSlot(EquipmentSlot.MAINHAND,
                            new ItemStack(D4CAddon.COLT_CUSTOM.get()));
                }
            }
            case SWORD -> {
                if (!held.is(Items.DIAMOND_SWORD)) {
                    setItemSlot(EquipmentSlot.MAINHAND,
                            new ItemStack(Items.DIAMOND_SWORD));
                }
            }
            case EMPTY -> {
                if (!held.isEmpty()) {
                    setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                }
            }
            case KEEP -> {
            }
        }
    }

    private void attack(LivingEntity target) {
        if (distanceToSqr(target) > 2.25D) {
            getNavigation().moveTo(target, 1.15D);
            return;
        }
        getNavigation().stop();
        if (attackCooldown <= 0 && hasLineOfSight(target)) {
            doHurtTarget(target);
            attackCooldown = 15;
        }
    }

    @Nullable
    private LivingEntity nearestMonster(LivingEntity owner) {
        AABB bounds = owner.getBoundingBox().inflate(HUNT_RADIUS);
        return level().getEntitiesOfClass(
                        Monster.class,
                        bounds,
                        monster -> monster.isAlive()
                                && monster.distanceToSqr(owner)
                                        <= HUNT_RADIUS * HUNT_RADIUS
                                && validTarget(monster, owner)
                                && getSensing().hasLineOfSight(monster))
                .stream()
                .min(Comparator.comparingDouble(owner::distanceToSqr))
                .orElse(null);
    }

    @Nullable
    private LivingEntity resolveOwner(ServerLevel level) {
        UUID uuid = entityData.get(OWNER).orElse(null);
        Entity entity = uuid != null ? level.getEntity(uuid) : null;
        if (entity instanceof LivingEntity living) {
            return living;
        }
        return uuid != null
                ? level.getServer().getPlayerList().getPlayer(uuid)
                : null;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        lifetime.save(tag);
        entityData.get(OWNER).ifPresent(uuid -> tag.putUUID("Owner", uuid));
        tag.putByte("AiMode", entityData.get(AI_MODE));
        tag.putString("ProfileName", entityData.get(PROFILE_NAME));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        lifetime.load(tag);
        entityData.set(
                OWNER,
                tag.hasUUID("Owner")
                        ? Optional.of(tag.getUUID("Owner"))
                        : Optional.empty());
        entityData.set(AI_MODE, (byte) (tag.contains("AiMode")
                ? D4CMinionAiMode.sanitize(tag.getByte("AiMode"))
                : D4CMinionAiMode.DEFAULT));
        entityData.set(
                PROFILE_NAME, tag.getString("ProfileName"));
    }
}
