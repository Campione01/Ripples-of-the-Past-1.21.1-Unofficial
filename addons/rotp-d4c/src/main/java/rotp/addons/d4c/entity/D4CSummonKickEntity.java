package rotp.addons.d4c.entity;

import java.util.Optional;
import java.util.UUID;

import rotp.addons.d4c.D4CAddon;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class D4CSummonKickEntity extends PathfinderMob {
    public static final int WINDUP = 0;
    public static final int FLIGHT = 1;
    public static final int IMPACT = 2;
    public static final int EXIT = 3;

    private static final EntityDataAccessor<Optional<UUID>> OWNER =
            SynchedEntityData.defineId(
                    D4CSummonKickEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> TARGET =
            SynchedEntityData.defineId(
                    D4CSummonKickEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> PROFILE_NAME =
            SynchedEntityData.defineId(
                    D4CSummonKickEntity.class,
                    EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> PHASE =
            SynchedEntityData.defineId(
                    D4CSummonKickEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE =
            SynchedEntityData.defineId(
                    D4CSummonKickEntity.class,
                    EntityDataSerializers.INT);

    private int phaseTicks;
    private boolean attacked;

    public D4CSummonKickEntity(
            EntityType<? extends D4CSummonKickEntity> type,
            Level level) {
        super(type, level);
        setNoGravity(true);
        noPhysics = true;
        xpReward = 0;
        // 1.16: the phantom cannot be hurt
        setInvulnerable(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.6D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void registerGoals() {}

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER, Optional.empty());
        builder.define(TARGET, Optional.empty());
        builder.define(PROFILE_NAME, "");
        builder.define(PHASE, WINDUP);
        builder.define(LIFE, 0);
    }

    public void initialize(ServerPlayer owner, LivingEntity target) {
        entityData.set(OWNER, Optional.of(owner.getUUID()));
        entityData.set(TARGET, Optional.of(target.getUUID()));
        entityData.set(
                PROFILE_NAME, owner.getGameProfile().getName());
        entityData.set(PHASE, WINDUP);
        entityData.set(LIFE, 0);
        Vec3 direction = owner.getLookAngle();
        Vec3 spawn = owner.position()
                .add(direction.x * 2.0D, 0.0D, direction.z * 2.0D)
                .add(0.0D, 3.0D, 0.0D);
        moveTo(
                spawn.x,
                spawn.y,
                spawn.z,
                owner.getYRot(),
                owner.getXRot());
        while (!level().noCollision(this)
                && getBlockY() < level().getMaxBuildHeight() - 1) {
            setPos(getX(), getY() + 1.0D, getZ());
        }
        setCustomName(owner.getDisplayName().copy());
        setCustomNameVisible(false);
    }

    public UUID profileId() {
        return entityData.get(OWNER).orElse(null);
    }

    public String profileName() {
        return entityData.get(PROFILE_NAME);
    }

    public int phase() {
        return entityData.get(PHASE);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        int life = entityData.get(LIFE) + 1;
        entityData.set(LIFE, life);
        phaseTicks++;
        ServerPlayer owner = entityData.get(OWNER)
                .map(level.getServer().getPlayerList()::getPlayer)
                .orElse(null);
        Entity rawTarget = entityData.get(TARGET)
                .map(level::getEntity)
                .orElse(null);
        if (owner == null
                || !(rawTarget instanceof LivingEntity target)
                || !target.isAlive()
                || life > 60) {
            entityData.set(PHASE, EXIT);
            vanish(level); // target gone or timed out
            return;
        }

        if (phase() == WINDUP) {
            if (phaseTicks >= 3) {
                entityData.set(PHASE, FLIGHT);
                phaseTicks = 0;
                level.playSound(
                        null,
                        blockPosition(),
                        SoundEvents.PLAYER_ATTACK_SWEEP,
                        getSoundSource(),
                        1.0F,
                        1.2F);
            }
            return;
        }
        if (phase() == IMPACT) {
            if (phaseTicks >= 3) {
                entityData.set(PHASE, EXIT);
                vanish(level); // after the impact
            }
            return;
        }

        Vec3 difference = target.position().subtract(position());
        if (difference.length() < 2.0D) {
            hit(level, owner, target);
            return;
        }
        Vec3 step = difference.normalize().scale(0.6D);
        setPos(getX() + step.x, getY() + step.y, getZ() + step.z);
        setYRot((float) (Math.toDegrees(
                Math.atan2(step.z, step.x)) - 90.0D));
        double horizontal = Math.sqrt(step.x * step.x + step.z * step.z);
        setXRot((float) -Math.toDegrees(
                Math.atan2(step.y, horizontal)));
        if (level.random.nextInt(3) == 0) {
            level.sendParticles(
                    ParticleTypes.CLOUD,
                    getX(),
                    getY(0.5D),
                    getZ(),
                    2,
                    0.2D,
                    0.2D,
                    0.2D,
                    0.02D);
        }
    }

    private void hit(
            ServerLevel level,
            ServerPlayer owner,
            LivingEntity target) {
        if (attacked) {
            return;
        }
        attacked = true;
        entityData.set(PHASE, IMPACT);
        phaseTicks = 0;
        target.hurt(damageSources().mobAttack(this), 12.0F);
        Vec3 push = target.position()
                .subtract(position())
                .normalize()
                .scale(2.0D);
        target.setDeltaMovement(target.getDeltaMovement().add(push));
        target.addEffect(new MobEffectInstance(
                MobEffects.CONFUSION, 60, 0, false, true, true));
        target.addEffect(new MobEffectInstance(
                MobEffects.WEAKNESS, 60, 0, false, true, true));
        target.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN,
                60,
                0,
                false,
                true,
                true));
        level.playSound(
                null,
                target.blockPosition(),
                SoundEvents.PLAYER_ATTACK_KNOCKBACK,
                target.getSoundSource(),
                1.5F,
                0.8F);
        level.sendParticles(
                ParticleTypes.CRIT,
                target.getX(),
                target.getY(0.5D),
                target.getZ(),
                20,
                0.5D,
                0.5D,
                0.5D,
                0.1D);
        level.sendParticles(
                D4CAddon.ANNIHILATE.get(),
                target.getX(),
                target.getY(0.5D),
                target.getZ(),
                12,
                0.35D,
                0.5D,
                0.35D,
                0.03D);
    }

    // 1.16 bf(): poof burst and teleport sound, then remove
    private void vanish(ServerLevel level) {
        double centerY = getY() + getBbHeight() / 2.0D;
        for (int i = 0; i < 15; i++) {
            level.sendParticles(
                    ParticleTypes.POOF,
                    getX() + (level.random.nextDouble() - 0.5D) * 1.5D,
                    centerY + (level.random.nextDouble() - 0.5D) * 1.5D,
                    getZ() + (level.random.nextDouble() - 0.5D) * 1.5D,
                    1,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.1D);
        }
        level.playSound(
                null,
                getX(),
                getY(),
                getZ(),
                D4CAddon.D4C_TELEPORT.get(),
                getSoundSource(),
                0.8F,
                1.5F);
        discard();
    }

    // 1.16: not hurt, pushed, collided with or picked
    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {}

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        entityData.get(OWNER)
                .ifPresent(uuid -> tag.putUUID("Owner", uuid));
        entityData.get(TARGET)
                .ifPresent(uuid -> tag.putUUID("Target", uuid));
        tag.putString("ProfileName", entityData.get(PROFILE_NAME));
        tag.putInt("Phase", entityData.get(PHASE));
        tag.putInt("Life", entityData.get(LIFE));
        tag.putInt("PhaseTicks", phaseTicks);
        tag.putBoolean("Attacked", attacked);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(
                OWNER,
                tag.hasUUID("Owner")
                        ? Optional.of(tag.getUUID("Owner"))
                        : Optional.empty());
        entityData.set(
                TARGET,
                tag.hasUUID("Target")
                        ? Optional.of(tag.getUUID("Target"))
                        : Optional.empty());
        entityData.set(PROFILE_NAME, tag.getString("ProfileName"));
        entityData.set(PHASE, tag.getInt("Phase"));
        entityData.set(LIFE, tag.getInt("Life"));
        phaseTicks = tag.getInt("PhaseTicks");
        attacked = tag.getBoolean("Attacked");
    }
}
