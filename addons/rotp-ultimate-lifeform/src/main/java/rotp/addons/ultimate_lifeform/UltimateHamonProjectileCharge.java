package rotp.addons.ultimate_lifeform;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import rotp.addons.ultimate_lifeform.entity.UltimateScaleProjectileEntity;
import rotp.addons.ultimate_lifeform.network.UltimateHamonProjectileChargePayload;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformData;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.customobjects.entity_projectile.MolotovEntity;
import rotp.core.impl.powers.hamon.HamonUtil;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModParticles;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.util.functions.DamageUtil;

/**
 * The Hamon charge an Ultimate Lifeform puts on its projectiles: 1.16 ProjectileUltimateHamonChargeCap with
 * KarsUtil.chargeNewEntity and KarsUtil.onProjectileImpact. It is the add-on's own charge, apart from the core's
 * ProjectileHamonChargeState: raw damage with the plain Hamon source, no wear-off, no Molotov waves.
 */
@EventBusSubscriber(modid = UltimateLifeformAddon.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class UltimateHamonProjectileCharge {
    private static final String DATA = "UltimateHamonCharge";
    private static final String CLIENT_DATA = "UltimateHamonChargeClient";
    private static final String DAMAGE = "HamonDamage";
    private static final String AGE = "ChargeAge";
    private static final String TICKS = "ChargeTicks";
    private static final String SPENT_ENERGY = "SpentEnergy";
    private static final String HAS_CHARGE = "HasCharge";
    private static final String MULTIPLY = "MultiplyWithUserStrength";

    private UltimateHamonProjectileCharge() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (event.getEntity() instanceof Projectile projectile) {
            reloadSavedCharge(projectile);
            if (projectile.getOwner() instanceof LivingEntity shooter) {
                chargeNewProjectile(projectile, shooter);
            }
        }
        else if (event.getEntity() instanceof Chicken chicken) {
            chargeChickenFromEgg(level, chicken);
        }
    }

    private static void chargeNewProjectile(Projectile projectile, LivingEntity shooter) {
        ChargeProperties properties = ChargeProperties.of(projectile);
        if (properties == null) {
            return;
        }
        UltimateLifeformData data = data(shooter);
        if (data != null && data.getHamonCharge() > 0) {
            CompoundTag charge = applyCharge(projectile, properties, 4.0F, true);
            charge.putFloat(SPENT_ENERGY, Math.min(data.getEnergy(), properties.energyRequired));
            // 1.16 subtracted the whole cost and reset a negative charge to 0 on the next tick
            data.consumeHamonCharge(Math.min(data.getHamonCharge(), properties.energyRequired));
            data.syncOnUpdate(shooter);
        }
        // 1.16 EntityUltimateHamonChargeCap of the shooter (Hamon Organism Infusion): the projectile takes a charge
        // that deals nothing. The core never sees this charge, so it charges nothing from it.
        UltimateHamonCharge infusion = UltimateHamonEntityCharge.getExisting(shooter)
                .map(UltimateHamonEntityCharge::getHamonCharge).orElse(null);
        if (infusion != null) {
            infusion.decreaseTicks((int) (infusion.getInitialTicks() * properties.energyRequired / 1000.0F));
            applyCharge(projectile, properties, infusion.getDamage(), false);
        }
    }

    private static CompoundTag applyCharge(
            Projectile projectile, ChargeProperties properties, float damageMultiplier, boolean multiplyWithUserStrength) {
        CompoundTag persistent = projectile.getPersistentData();
        CompoundTag charge = persistent.getCompound(DATA);
        float damage = properties.baseMultiplier * damageMultiplier;
        charge.putFloat(DAMAGE, damage);
        charge.putInt(AGE, 0);
        charge.putInt(TICKS, properties.chargeTicks);
        charge.putBoolean(HAS_CHARGE, damage > 0.0F);
        charge.putBoolean(MULTIPLY, multiplyWithUserStrength);
        persistent.put(DATA, charge);
        PacketDistributor.sendToPlayersTrackingEntity(projectile,
                new UltimateHamonProjectileChargePayload(projectile.getId(), damage > 0.0F));
        return charge;
    }

    // 1.16 fromNBT: only the four saved values come back, and the charge is live only when its age is past its ticks,
    // which holds for the endless charge of an egg alone.
    private static void reloadSavedCharge(Projectile projectile) {
        CompoundTag charge = savedCharge(projectile);
        if (charge != null) {
            charge.putBoolean(HAS_CHARGE, charge.getFloat(DAMAGE) > 0.0F && charge.getInt(AGE) > charge.getInt(TICKS));
            charge.putBoolean(MULTIPLY, false);
        }
    }

    private static void chargeChickenFromEgg(ServerLevel level, Chicken chicken) {
        level.getData(ModDataAttachmentTypes.CHARGED_HAMON_EGGS.get()).eggChargingChicken(chicken).ifPresent(egg -> {
            if (hasCharge(egg)) {
                LivingEntity thrower = egg.getOwner() instanceof LivingEntity living ? living : null;
                UltimateHamonEntityCharge.get(chicken).setHamonCharge(
                        getHamonDamage(egg) * 2.0F, Integer.MAX_VALUE, thrower, 0.0F);
            }
        });
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (!(projectile.level() instanceof ServerLevel level) || !hasCharge(projectile)) {
            return;
        }
        onTargetHit(level, projectile, event.getRayTraceResult());
        savedCharge(projectile).putInt(TICKS, 0);
    }

    private static void onTargetHit(ServerLevel level, Projectile projectile, HitResult target) {
        if (projectile instanceof ThrownPotion potion) {
            if (HamonUtil.isWaterBottle(potion)) {
                Vec3 pos = projectile.getBoundingBox().getCenter();
                AABB waterSplashArea = projectile.getBoundingBox().inflate(4.0D, 2.0D, 4.0D);
                for (LivingEntity splashed : level.getEntitiesOfClass(LivingEntity.class, waterSplashArea,
                        EntitySelector.ENTITY_STILL_ALIVE.and(EntitySelector.NO_SPECTATORS)
                                .and(entity -> !entity.is(potion.getOwner())))) {
                    double distSqr = splashed.distanceToSqr(pos);
                    if (distSqr < 16.0D) {
                        dealHamonDamage(projectile, splashed, getHamonDamage(projectile) * (1.0F - (float) (distSqr / 16.0D)));
                    }
                }
                level.playSound(null, pos.x, pos.y, pos.z, ModSoundEvents.HAMON_SPARK.get(),
                        SoundSource.AMBIENT, 0.1F, 1.0F + (level.random.nextFloat() - 0.5F) * 0.15F);
                level.sendParticles(ModParticles.HAMON_SPARK.get(), pos.x, pos.y, pos.z, 32, 0.75D, 0.05D, 0.75D, 0.25D);
            }
            return;
        }
        if (target.getType() == HitResult.Type.ENTITY && target instanceof EntityHitResult entityTarget) {
            dealHamonDamage(projectile, entityTarget.getEntity(), getHamonDamage(projectile));
        }
        if (projectile instanceof ThrownEgg egg) {
            level.getData(ModDataAttachmentTypes.CHARGED_HAMON_EGGS.get()).addChargedEggEntity(egg);
        }
    }

    private static void dealHamonDamage(Projectile projectile, Entity target, float damage) {
        if (damage <= 0.0F || !savedCharge(projectile).getBoolean(MULTIPLY)
                || !(projectile.getOwner() instanceof LivingEntity owner) || data(owner) == null) {
            return;
        }
        DamageSource source = HamonAbilityHelpers.hamonDamageSource(target.level(), null, null);
        if (source instanceof DamageSourceModified modified) {
            // 1.16 hurt() knocked back only from a source entity; 1.21 pushes in a random direction without one
            modified.jojo_ripples$modifyKnockback(0.0F, 0.0F);
        }
        DamageUtil.hurtThroughInvulTicks(target, source, damage);
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof Projectile projectile && event.getEntity() instanceof ServerPlayer player
                && hasCharge(projectile)) {
            PacketDistributor.sendToPlayer(player, new UltimateHamonProjectileChargePayload(projectile.getId(), true));
        }
    }

    public static boolean hasCharge(Projectile projectile) {
        CompoundTag charge = savedCharge(projectile);
        return charge != null && charge.getBoolean(HAS_CHARGE);
    }

    /** 1.16 getHamonDamage: chargeWearsOff() is false, so the wear-off multiplier is always 1. */
    public static float getHamonDamage(Projectile projectile) {
        CompoundTag charge = savedCharge(projectile);
        return charge != null ? 3.0F * charge.getFloat(DAMAGE) : 0.0F;
    }

    @Nullable
    private static CompoundTag savedCharge(Projectile projectile) {
        CompoundTag persistent = projectile.getPersistentData();
        return persistent.contains(DATA, Tag.TAG_COMPOUND) ? persistent.getCompound(DATA) : null;
    }

    public static void setClientCharge(Projectile projectile, boolean hasCharge) {
        projectile.getPersistentData().putBoolean(CLIENT_DATA, hasCharge);
    }

    public static boolean hasClientCharge(Projectile projectile) {
        return projectile.getPersistentData().getBoolean(CLIENT_DATA);
    }

    @Nullable
    private static UltimateLifeformData data(LivingEntity entity) {
        return PlayerPower.getPowerData(entity, UltimateLifeformAddon.ULTIMATE_LIFEFORM).orElse(null);
    }

    private record ChargeProperties(float baseMultiplier, int chargeTicks, int energyRequired) {
        @Nullable
        private static ChargeProperties of(Projectile projectile) {
            if (projectile instanceof AbstractArrow) {
                return new ChargeProperties(1.5F, 10, 1000);
            }
            if (projectile instanceof Snowball) {
                return new ChargeProperties(0.75F, 20, 500);
            }
            if (projectile instanceof ThrownEgg) {
                return new ChargeProperties(0.75F, -1, 200);
            }
            if (projectile instanceof ThrownPotion potion && HamonUtil.isWaterBottle(potion)) {
                return new ChargeProperties(1.0F, 30, 750);
            }
            if (projectile instanceof MolotovEntity) {
                return new ChargeProperties(1.0F, 200, 500);
            }
            if (projectile instanceof UltimateScaleProjectileEntity
                    && projectile.getType() == UltimateLifeformAddon.SCALE_PROJECTILE.get()) {
                return new ChargeProperties(1.5F, 10, 1000);
            }
            return null;
        }
    }
}
