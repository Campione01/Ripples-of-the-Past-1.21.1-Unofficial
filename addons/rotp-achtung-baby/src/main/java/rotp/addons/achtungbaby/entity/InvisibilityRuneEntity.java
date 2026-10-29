package rotp.addons.achtungbaby.entity;

import java.util.ArrayList;
import java.util.List;

import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.achtungbaby.AchtungBabyAddon;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/**
 * The rune Active and Grant Invisibility fire, restored to 1.16's RuneSnowballEntity: it flies straight at its launch
 * speed (no gravity, no drag) for up to 100 ticks, leaves a particle trail, passes through its thrower, and looks and
 * hits like the snowball it was before applying its effect.
 */
public final class InvisibilityRuneEntity
        extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Boolean> HARMFUL =
            SynchedEntityData.defineId(
                    InvisibilityRuneEntity.class,
                    EntityDataSerializers.BOOLEAN);
    // The vanilla snowball's impact event.
    private static final byte SNOWBALL_BURST_EVENT = 3;

    public InvisibilityRuneEntity(
            EntityType<? extends InvisibilityRuneEntity> type,
            Level level) {
        super(type, level);
    }

    public static InvisibilityRuneEntity launch(
            LivingEntity owner, boolean harmful) {
        InvisibilityRuneEntity rune = new InvisibilityRuneEntity(
                AchtungBabyAddon.INVISIBILITY_RUNE.get(),
                owner.level());
        rune.setOwner(owner);
        rune.entityData.set(HARMFUL, harmful);
        // 1.16: from just under the eyes, along the look vector, without the thrower's own motion added.
        rune.setNoGravity(true);
        rune.setPos(owner.getX(), owner.getEyeY() - 0.1D, owner.getZ());
        Vec3 look = owner.getLookAngle();
        rune.shoot(look.x, look.y, look.z, 3.0F, 0.5F);
        owner.level().addFreshEntity(rune);
        return rune;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(HARMFUL, false);
    }

    // 1.16 RuneSnowballEntity.tick replaced the snowball's tick instead of extending it: it moves by its whole
    // velocity with block collision first and only then looks one velocity ahead for a hit. The first 3 blocks are
    // therefore never tested for entities, and a wall inside them stops the rune rather than breaking it.
    @Override
    public void tick() {
        baseTick();
        if (tickCount > 100) {
            if (!level().isClientSide()) {
                discard();
            }
            return;
        }
        move(MoverType.SELF, getDeltaMovement());
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(
                this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS
                && !EventHooks.onProjectileImpact(this, hit)) {
            hitTargetOrDeflectSelf(hit);
        }
        if (level() instanceof ServerLevel level) {
            trail(level);
        }
    }

    // 1.16 RuneSnowballEntity.tick: the trail follows the snowball the rune is drawn as.
    private void trail(ServerLevel level) {
        Vec3 at = position();
        for (int i = 0; i < 6; i++) {
            send(level, ParticleTypes.ENCHANT, at.add(jitter(0.4D)), 0.01D);
        }
        for (int i = 0; i < 3; i++) {
            send(level, ParticleTypes.PORTAL, at.add(jitter(0.2D)), 0.005D);
        }
        if (tickCount % 3 == 0) {
            for (int i = 0; i < 2; i++) {
                send(level, ParticleTypes.WITCH, at.add(jitter(0.1D)), 0.001D);
            }
        }
    }

    // 1.16 RuneSnowballEntity.spawnHitParticles, the same for both runes.
    private void hitBurst(ServerLevel level, LivingEntity target) {
        Vec3 center = target.position().add(
                0.0D, target.getBbHeight() / 2.0D, 0.0D);
        for (int i = 0; i < 20; i++) {
            Vec3 offset = jitter(2.0D);
            send(level, ParticleTypes.ENCHANT, center.add(offset), 0.05D);
            send(level, ParticleTypes.CRIT,
                    center.add(offset.scale(0.5D)), 0.02D);
        }
    }

    // A uniformly random offset of up to size/2 on each axis.
    private Vec3 jitter(double size) {
        return new Vec3(
                (random.nextDouble() - 0.5D) * size,
                (random.nextDouble() - 0.5D) * size,
                (random.nextDouble() - 0.5D) * size);
    }

    // One particle per call with no spread, as 1.16 sent them.
    private static void send(
            ServerLevel level,
            ParticleOptions particle,
            Vec3 at,
            double speed) {
        level.sendParticles(
                particle, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, speed);
    }

    // 1.16 canHitEntity: the rune passes through its thrower.
    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && entity != getOwner();
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        Entity entity = hit.getEntity();
        // 1.16's rune was a snowball and hit like one first: nothing for a player, a knockback without damage for
        // a mob (which then turns on the thrower), 3 damage for a blaze.
        entity.hurt(
                damageSources().thrown(this, getOwner()),
                entity instanceof Blaze ? 3.0F : 0.0F);
        if (entity instanceof LivingEntity target && target != getOwner()) {
            if (entityData.get(HARMFUL)) {
                applyHarmful(target);
            } else {
                applyBeneficial(target);
            }
            hitBurst(level, target);
        }
    }

    // 1.16's rune reached every client as a plain snowball, so its hit showed the snowball's burst.
    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (!level().isClientSide()) {
            level().broadcastEntityEvent(this, SNOWBALL_BURST_EVENT);
            discard();
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == SNOWBALL_BURST_EVENT) {
            for (int i = 0; i < 8; i++) {
                level().addParticle(
                        ParticleTypes.ITEM_SNOWBALL,
                        getX(), getY(), getZ(), 0.0D, 0.0D, 0.0D);
            }
        }
    }

    // 1.16 ActiveInvisibilityAction.applyActiveInvisibilityDebuff: players and mobs get different sets. A player
    // with a Stand past resolve level 1 gets a longer, stronger set first, which the ordinary one then leaves alone.
    private static void applyHarmful(LivingEntity target) {
        if (target instanceof Player player) {
            StandPower power = StandPower.get(player);
            if (power != null && power.hasPower()
                    && power.getResolveLevel() > 1) {
                player.addEffect(new MobEffectInstance(
                        MobEffects.WEAKNESS, 400, 3));
                player.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SLOWDOWN, 400, 2));
                player.addEffect(new MobEffectInstance(
                        MobEffects.DIG_SLOWDOWN, 400, 2));
            }
            player.addEffect(new MobEffectInstance(
                    MobEffects.WEAKNESS, 300, 2));
            player.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN, 300, 2));
            player.addEffect(new MobEffectInstance(
                    MobEffects.DIG_SLOWDOWN, 300, 1));
        } else {
            target.addEffect(new MobEffectInstance(
                    MobEffects.WEAKNESS, 200, 3));
            target.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN, 200, 3));
            target.addEffect(new MobEffectInstance(
                    MobEffects.CONFUSION, 200, 0));
        }
    }

    // 1.16 GrantInvisibilityAction.grantInvisibilityEffect/addRandomBeneficialEffects: 240 ticks of invisibility on
    // top of what is left, then two different effects out of these thirteen, each at level 0-4 for 200 ticks, with
    // no particles.
    private static void applyBeneficial(LivingEntity target) {
        MobEffectInstance current =
                target.getEffect(MobEffects.INVISIBILITY);
        int duration = 240;
        if (current != null && !current.isInfiniteDuration()) {
            duration += current.getDuration();
        }
        target.addEffect(new MobEffectInstance(
                MobEffects.INVISIBILITY, duration, 0,
                false, false, true));
        List<Holder<MobEffect>> effects = new ArrayList<>(List.of(
                MobEffects.MOVEMENT_SPEED,
                MobEffects.DIG_SPEED,
                MobEffects.DAMAGE_BOOST,
                MobEffects.JUMP,
                MobEffects.REGENERATION,
                MobEffects.DAMAGE_RESISTANCE,
                MobEffects.FIRE_RESISTANCE,
                MobEffects.WATER_BREATHING,
                MobEffects.NIGHT_VISION,
                MobEffects.HEALTH_BOOST,
                MobEffects.ABSORPTION,
                MobEffects.SATURATION,
                MobEffects.LUCK));
        RandomSource random = target.level().getRandom();
        Util.shuffle(effects, random);
        for (int i = 0; i < 2; i++) {
            target.addEffect(new MobEffectInstance(
                    effects.get(i),
                    200,
                    random.nextInt(5),
                    false,
                    false,
                    true));
        }
    }

    @Override
    protected Item getDefaultItem() {
        return Items.SNOWBALL;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Harmful", entityData.get(HARMFUL));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(HARMFUL, tag.getBoolean("Harmful"));
    }
}
