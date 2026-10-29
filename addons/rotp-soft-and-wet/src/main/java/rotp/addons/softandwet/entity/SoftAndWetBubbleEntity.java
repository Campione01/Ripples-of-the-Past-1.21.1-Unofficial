package rotp.addons.softandwet.entity;

import rotp.core.JojoModConfig;
import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;
import rotp.core.init.ModStatusEffects;

import rotp.addons.softandwet.SoftAndWetAddon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

public final class SoftAndWetBubbleEntity
        extends ModdedProjectileEntity {
    public static final float BASE_DAMAGE = 0.75F;
    public static final float EFFECT_BASE_DAMAGE = 0.5F;
    public static final float MAX_HARDNESS = 0.0F;
    public static final int LIFESPAN_TICKS = 100;
    public static final int BLIND_DURATION_TICKS = 200;
    public static final int BLIND_AMPLIFIER = 1;
    public static final int NAUSEA_DURATION_TICKS = 200;
    public static final int NAUSEA_AMPLIFIER = 1;
    public static final int STUN_DURATION_TICKS = 80;
    public static final int STUN_AMPLIFIER = 1;
    public static final float EXPLOSION_ENTITY_RADIUS = 1.0F;
    public static final float EXPLOSION_BLOCK_RADIUS = 2.0F;
    public static final float EXPLOSION_DIRECT_DAMAGE = 14.0F;

    private static final String KIND_TAG = "SoftAndWetBubbleKind";
    private static final EntityDataAccessor<Integer> KIND =
            SynchedEntityData.defineId(
                    SoftAndWetBubbleEntity.class,
                    EntityDataSerializers.INT);

    public SoftAndWetBubbleEntity(
            EntityType<? extends SoftAndWetBubbleEntity> type,
            Level level) {
        super(type, level);
    }

    public SoftAndWetBubbleEntity(
            LivingEntity shooter, Level level) {
        this(shooter, level, Kind.DAMAGING);
    }

    public SoftAndWetBubbleEntity(
            LivingEntity shooter, Level level, Kind kind) {
        super(SoftAndWetAddon.BUBBLE_ENTITY.get(), shooter, level);
        setKind(kind);
    }

    public Kind getKind() {
        return Kind.byId(entityData.get(KIND));
    }

    public void setKind(Kind kind) {
        entityData.set(KIND, kind.id());
    }

    @Override
    protected float getBaseDamage() {
        return getKind().effectProjectile()
                ? EFFECT_BASE_DAMAGE
                : BASE_DAMAGE;
    }

    @Override
    protected float getMaxHardnessBreakable() {
        return MAX_HARDNESS;
    }

    @Override
    public boolean standDamage() {
        return true;
    }

    @Override
    public int ticksLifespan() {
        return LIFESPAN_TICKS;
    }

    @Override
    protected boolean hurtTarget(
            Entity target, LivingEntity owner) {
        if (getKind() != Kind.EXPLOSION) {
            return super.hurtTarget(target, owner);
        }
        explode(EXPLOSION_ENTITY_RADIUS);
        if (owner == null) {
            return false;
        }
        float multiplier = JojoModConfig
                .getCommonConfigInstance(false)
                .standDamageMultiplier
                .get()
                .floatValue();
        return target.hurt(
                level().damageSources().mobAttack(owner),
                EXPLOSION_DIRECT_DAMAGE * multiplier);
    }

    @Override
    protected void afterEntityHit(
            EntityHitResult hitResult, boolean entityHurt) {
        super.afterEntityHit(hitResult, entityHurt);
        if (!(hitResult.getEntity()
                instanceof LivingEntity target)) {
            return;
        }
        switch (getKind()) {
            case BLIND -> {
                target.addEffect(new MobEffectInstance(
                        MobEffects.BLINDNESS,
                        BLIND_DURATION_TICKS,
                        BLIND_AMPLIFIER));
                target.addEffect(new MobEffectInstance(
                        MobEffects.CONFUSION,
                        NAUSEA_DURATION_TICKS,
                        NAUSEA_AMPLIFIER));
            }
            case STUN -> target.addEffect(
                    new MobEffectInstance(
                            ModStatusEffects.STUN,
                            STUN_DURATION_TICKS,
                            STUN_AMPLIFIER));
            default -> {
            }
        }
    }

    @Override
    protected void afterBlockHit(
            BlockHitResult hitResult, boolean blockDestroyed) {
        super.afterBlockHit(hitResult, blockDestroyed);
        if (getKind() == Kind.EXPLOSION) {
            explode(EXPLOSION_BLOCK_RADIUS);
        }
    }

    private void explode(float radius) {
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.explode(
                    this,
                    getX(),
                    getY(),
                    getZ(),
                    radius,
                    Level.ExplosionInteraction.NONE);
        }
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(KIND, Kind.DAMAGING.id());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt(KIND_TAG, getKind().id());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setKind(Kind.byId(tag.getInt(KIND_TAG)));
    }

    public enum Kind {
        DAMAGING(0, false),
        BLIND(1, true),
        STUN(2, true),
        EXPLOSION(3, true),
        WALL(4, false);

        private static final Kind[] VALUES = values();

        private final int id;
        private final boolean effectProjectile;

        Kind(int id, boolean effectProjectile) {
            this.id = id;
            this.effectProjectile = effectProjectile;
        }

        public int id() {
            return id;
        }

        public boolean effectProjectile() {
            return effectProjectile;
        }

        public static Kind byId(int id) {
            return id >= 0 && id < VALUES.length
                    ? VALUES[id]
                    : DAMAGING;
        }
    }
}
