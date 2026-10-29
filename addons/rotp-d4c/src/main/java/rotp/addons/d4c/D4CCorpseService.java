package rotp.addons.d4c;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import rotp.core.api.RotpAddonApi;
import rotp.core.api.stand.StandStaminaModifiers;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.d4c.entity.D4CMinionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

public final class D4CCorpseService {
    private static final String SPINE_READY_AT =
            "rotp_d4c.phase2b.spine_ready_at";
    private static final String EYES_NIGHT_VISION =
            "rotp_d4c.phase2b.eyes_night_vision";

    private D4CCorpseService() {}

    public static void tickPlayer(ServerPlayer player) {
        boolean complete = has(
                player, D4CAddon.COMPLETE_SAINT_CORPSE.get());
        if (complete || has(player, D4CAddon.SAINT_CORPSE_HEART.get())) {
            refresh(player, MobEffects.REGENERATION, 2);
            player.removeEffect(MobEffects.POISON);
            player.removeEffect(MobEffects.WITHER);
        }
        if (complete || has(player, D4CAddon.SAINT_CORPSE_RIGHT_ARM.get())) {
            refresh(player, MobEffects.DAMAGE_BOOST, 1);
        }
        // 1.16 af.ab: Love Train grants Night Vision too; the owned-corpse
        // check keeps it through the expiry tick until the corpse returns
        boolean loveTrainSight = player.hasEffect(D4CAddon.LOVE_TRAIN)
                || D4CPhase2BService.ownsLoveTrainCorpse(player);
        if (complete || loveTrainSight
                || has(player, D4CAddon.SAINT_CORPSE_EYES.get())) {
            refreshNightVision(player);
        } else {
            clearNightVision(player);
        }
        if (complete || has(player, D4CAddon.SAINT_CORPSE_EARS.get())) {
            // 1.16 ao: Water Breathing + Dolphin's Grace
            refresh(player, MobEffects.WATER_BREATHING, 0);
            refresh(player, MobEffects.DOLPHINS_GRACE, 0);
        }
        if (complete || has(player, D4CAddon.SAINT_CORPSE_LEGS.get())) {
            refresh(player, MobEffects.MOVEMENT_SPEED, 1);
            refresh(player, MobEffects.JUMP, 1);
        }
        if (complete || has(player, D4CAddon.SAINT_CORPSE_THORAX.get())) {
            refresh(player, MobEffects.DAMAGE_RESISTANCE, 1);
        }
        if (complete || has(player, D4CAddon.SAINT_CORPSE_HEAD.get())) {
            refresh(player, MobEffects.SATURATION, 0);
            refresh(player, MobEffects.DIG_SPEED, 1);
            refresh(player, MobEffects.FIRE_RESISTANCE, 0);
        }

        if (player.hasEffect(D4CAddon.LOVE_TRAIN)) {
            tickLoveTrain(player);
        } else if (D4CPhase2BService.ownsLoveTrainCorpse(player)) {
            // Love Train expired: 1.16 af.ab returned the corpse with a message
            D4CPhase2BService.returnLoveTrainCorpse(player);
            D4CPhase2BService.notify(player,
                    D4CPhase2BService.MSG_LOVE_TRAIN_EXPIRE_RETURN_CORPSE);
        }
    }

    private static void tickLoveTrain(ServerPlayer player) {
        List<MobEffectInstance> effects =
                new ArrayList<>(player.getActiveEffects());
        for (MobEffectInstance effect : effects) {
            if (effect.getEffect().value().getCategory()
                    == MobEffectCategory.HARMFUL) {
                player.removeEffect(effect.getEffect());
            }
        }
        refresh(player, MobEffects.MOVEMENT_SPEED, 0);
        refresh(player, MobEffects.FIRE_RESISTANCE, 0);
        if (player.getFoodData().getFoodLevel() < 20) {
            player.getFoodData().setFoodLevel(20);
        }
        if (player.getFoodData().getSaturationLevel() < 5.0F) {
            player.getFoodData().setSaturation(5.0F);
        }
        ServerLevel level = player.serverLevel();
        if (player.hasEffect(D4CAddon.LOVE_TRAIN_LIGHT_WALL)) {
            tickLightWall(level, player);
        }

        StandPower power = StandPower.get(player);
        if (power != null
                && power.getSummonedStandEntity()
                        instanceof StandEntity stand) {
            stand.addEffect(new MobEffectInstance(
                    D4CAddon.LOVE_TRAIN,
                    D4CPhase2BSpec.EFFECT_REFRESH_TICKS,
                    0,
                    false,
                    false,
                    true));
            repelAndIntercept(
                    player.serverLevel(), stand, player, 2.0D);
        }

        repelAndIntercept(level, player, player, 3.0D);
    }

    private static void tickLightWall(
            ServerLevel level,
            ServerPlayer player) {
        long gameTime = level.getGameTime();
        if (gameTime
                % D4CPhase2BSpec
                        .LIGHT_WALL_FLUID_CLEAR_INTERVAL_TICKS
                == 0L) {
            clearLightWallFluids(player);
        }
        interceptLightWallProjectiles(level, player);
        if (gameTime
                % D4CPhase2BSpec
                        .LIGHT_WALL_ENTITY_PUSH_INTERVAL_TICKS
                == 0L) {
            pushEntitiesAcrossLightWall(level, player);
        }
        forceEntitiesOffLightWall(level, player);
    }

    private static void interceptLightWallProjectiles(
            ServerLevel level,
            ServerPlayer player) {
        Vec3 axis = lightWallAxis(player);
        double negativeX = player.getX()
                - axis.x * D4CPhase2BSpec.LIGHT_WALL_RANGE;
        double negativeZ = player.getZ()
                - axis.z * D4CPhase2BSpec.LIGHT_WALL_RANGE;
        double positiveX = player.getX()
                + axis.x * D4CPhase2BSpec.LIGHT_WALL_RANGE;
        double positiveZ = player.getZ()
                + axis.z * D4CPhase2BSpec.LIGHT_WALL_RANGE;
        AABB searchBounds = new AABB(
                Math.min(negativeX, positiveX) - 2.0D,
                player.getY()
                        + D4CPhase2BSpec
                                .LIGHT_WALL_PROJECTILE_MIN_Y_OFFSET,
                Math.min(negativeZ, positiveZ) - 2.0D,
                Math.max(negativeX, positiveX) + 2.0D,
                player.getY()
                        + D4CPhase2BSpec
                                .LIGHT_WALL_PROJECTILE_MAX_Y_OFFSET,
                Math.max(negativeZ, positiveZ) + 2.0D);
        boolean intercepted = false;
        for (Projectile projectile : level.getEntitiesOfClass(
                Projectile.class, searchBounds)) {
            if (projectile.isRemoved()) {
                continue;
            }
            Entity projectileOwner = projectile.getOwner();
            if (projectileOwner != null
                    && projectileOwner.getUUID().equals(
                            player.getUUID())) {
                continue;
            }
            if (!touchesLightWall(player, axis, projectile)) {
                continue;
            }
            projectile.discard();
            intercepted = true;
        }
        if (intercepted) {
            punishLightWallInterception(level, player);
        }
    }

    private static boolean touchesLightWall(
            ServerPlayer player,
            Vec3 axis,
            Projectile projectile) {
        double projectileY = projectile.getY();
        if (projectileY
                        < player.getY()
                                + D4CPhase2BSpec
                                        .LIGHT_WALL_PROJECTILE_MIN_Y_OFFSET
                || projectileY
                        > player.getY()
                                + D4CPhase2BSpec
                                        .LIGHT_WALL_PROJECTILE_MAX_Y_OFFSET) {
            return false;
        }
        for (int distance = -D4CPhase2BSpec.LIGHT_WALL_RANGE;
                distance <= D4CPhase2BSpec.LIGHT_WALL_RANGE;
                distance++) {
            if (distance == 0) {
                continue;
            }
            double wallX = player.getX() + axis.x * distance;
            double wallZ = player.getZ() + axis.z * distance;
            if (Math.abs(projectile.getX() - wallX) <= 1.0D
                    && Math.abs(projectile.getZ() - wallZ) <= 1.0D) {
                return true;
            }
        }
        return false;
    }

    private static void punishLightWallInterception(
            ServerLevel level,
            ServerPlayer player) {
        AABB punishmentBounds = new AABB(
                player.getX()
                        - D4CPhase2BSpec
                                .LIGHT_WALL_PUNISH_HORIZONTAL_RANGE,
                player.getY()
                        - D4CPhase2BSpec
                                .LIGHT_WALL_PUNISH_VERTICAL_RANGE,
                player.getZ()
                        - D4CPhase2BSpec
                                .LIGHT_WALL_PUNISH_HORIZONTAL_RANGE,
                player.getX()
                        + D4CPhase2BSpec
                                .LIGHT_WALL_PUNISH_HORIZONTAL_RANGE,
                player.getY()
                        + D4CPhase2BSpec
                                .LIGHT_WALL_PUNISH_VERTICAL_RANGE,
                player.getZ()
                        + D4CPhase2BSpec
                                .LIGHT_WALL_PUNISH_HORIZONTAL_RANGE);
        for (LivingEntity living : level.getEntitiesOfClass(
                LivingEntity.class,
                punishmentBounds,
                candidate -> candidate != player
                        && !(candidate instanceof Player))) {
            living.hurt(
                    level.damageSources().magic(),
                    D4CPhase2BSpec.LIGHT_WALL_PUNISH_DAMAGE);
            Vec3 direction = living.position()
                    .subtract(player.position())
                    .normalize()
                    .scale(0.5D);
            living.push(direction.x, 0.2D, direction.z);
            living.hurtMarked = true;
        }
    }

    private static void pushEntitiesAcrossLightWall(
            ServerLevel level,
            ServerPlayer player) {
        Vec3 axis = lightWallAxis(player);
        for (int distance = -D4CPhase2BSpec.LIGHT_WALL_RANGE;
                distance <= D4CPhase2BSpec.LIGHT_WALL_RANGE;
                distance++) {
            if (distance == 0) {
                continue;
            }
            double wallX = player.getX() + axis.x * distance;
            double wallZ = player.getZ() + axis.z * distance;
            AABB bounds = new AABB(
                    wallX - 0.5D,
                    player.getY() - 0.5D,
                    wallZ - 0.5D,
                    wallX + 0.5D,
                    player.getY() + 3.5D,
                    wallZ + 0.5D);
            for (LivingEntity living : level.getEntitiesOfClass(
                    LivingEntity.class,
                    bounds,
                    candidate -> !isLightWallPushImmune(
                            candidate, player))) {
                double normalX = axis.z;
                double normalZ = -axis.x;
                double deltaX = living.getX() - wallX;
                double deltaZ = living.getZ() - wallZ;
                if (deltaX * normalX + deltaZ * normalZ < 0.0D) {
                    normalX = -normalX;
                    normalZ = -normalZ;
                }
                living.push(
                        normalX * 0.5D,
                        0.1D,
                        normalZ * 0.5D);
                living.hurtMarked = true;
            }
        }
    }

    private static void forceEntitiesOffLightWall(
            ServerLevel level,
            ServerPlayer player) {
        Vec3 axis = lightWallAxis(player);
        for (int distance = -D4CPhase2BSpec.LIGHT_WALL_RANGE;
                distance <= D4CPhase2BSpec.LIGHT_WALL_RANGE;
                distance++) {
            if (distance == 0) {
                continue;
            }
            double wallX = player.getX() + axis.x * distance;
            double wallZ = player.getZ() + axis.z * distance;
            AABB bounds = new AABB(
                    wallX - 1.5D,
                    player.getY()
                            + D4CPhase2BSpec.LIGHT_WALL_MIN_Y_OFFSET,
                    wallZ - 1.5D,
                    wallX + 1.5D,
                    player.getY()
                            + D4CPhase2BSpec.LIGHT_WALL_MAX_Y_OFFSET,
                    wallZ + 1.5D);
            for (LivingEntity living : level.getEntitiesOfClass(
                    LivingEntity.class,
                    bounds,
                    candidate -> !isLightWallPushImmune(
                            candidate, player))) {
                double normalX = axis.z;
                double normalZ = -axis.x;
                double signedDistance =
                        (living.getX() - wallX) * normalX
                                + (living.getZ() - wallZ)
                                        * normalZ;
                double distanceFromWall = Math.abs(signedDistance);
                if (distanceFromWall >= 1.5D) {
                    continue;
                }
                if (signedDistance < 0.0D) {
                    normalX = -normalX;
                    normalZ = -normalZ;
                }
                double displacement = 2.0D - distanceFromWall;
                living.setPos(
                        living.getX() + normalX * displacement,
                        living.getY(),
                        living.getZ() + normalZ * displacement);
                living.push(normalX * 2.0D, 0.2D, normalZ * 2.0D);
                living.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SLOWDOWN, 10, 2));
                living.hurtMarked = true;
            }
        }
    }

    private static boolean isLightWallPushImmune(
            LivingEntity candidate,
            ServerPlayer player) {
        return candidate == player
                || candidate.getType() == D4CAddon.D4C_ENTITY.get()
                || candidate instanceof AbstractHorse
                || candidate instanceof D4CMinionEntity;
    }

    private static Vec3 lightWallAxis(ServerPlayer player) {
        double radians = Math.toRadians(player.getYRot());
        return new Vec3(
                -Math.sin(radians),
                0.0D,
                Math.cos(radians));
    }

    private static void repelAndIntercept(
            ServerLevel level,
            LivingEntity protectedEntity,
            LivingEntity owner,
            double repelRadius) {
        boolean intercepted = false;
        AABB projectileBounds =
                protectedEntity.getBoundingBox().inflate(3.0D);
        for (Projectile projectile : level.getEntitiesOfClass(
                Projectile.class, projectileBounds)) {
            if (projectile.isRemoved()) {
                continue;
            }
            Entity projectileOwner = projectile.getOwner();
            if (projectileOwner != null
                    && D4CRuntimeRules
                            .preserveLoveTrainProjectile(
                                    projectileOwner.getUUID().equals(
                                            protectedEntity.getUUID()),
                                    projectileOwner.getUUID().equals(
                                            owner.getUUID()))) {
                continue;
            }
            projectile.discard();
            intercepted = true;
        }
        if (intercepted) {
            redirectMisfortune(level, protectedEntity);
        }

        AABB livingBounds =
                protectedEntity.getBoundingBox().inflate(repelRadius);
        for (LivingEntity living : level.getEntitiesOfClass(
                LivingEntity.class,
                livingBounds,
                candidate -> candidate.isAlive()
                        && candidate != protectedEntity
                        && candidate != owner
                        && !(candidate instanceof AbstractHorse)
                        && !(candidate instanceof StandEntity))) {
            double x = living.getX() - protectedEntity.getX();
            double z = living.getZ() - protectedEntity.getZ();
            double horizontalDistance = Math.sqrt(x * x + z * z);
            if (horizontalDistance <= 0.0D
                    || horizontalDistance >= repelRadius) {
                continue;
            }
            double normalizedX = x / horizontalDistance;
            double normalizedZ = z / horizontalDistance;
            double displacement =
                    repelRadius + 0.1D - horizontalDistance;
            living.setPos(
                    living.getX() + normalizedX * displacement,
                    living.getY(),
                    living.getZ() + normalizedZ * displacement);
            living.setDeltaMovement(
                    normalizedX * 0.5D,
                    living.getDeltaMovement().y,
                    normalizedZ * 0.5D);
            living.hurtMarked = true;
        }
    }

    private static void clearLightWallFluids(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        double radians = Math.toRadians(player.getYRot());
        double axisX = -Math.sin(radians);
        double axisZ = Math.cos(radians);
        for (int distance = -D4CPhase2BSpec.LIGHT_WALL_RANGE;
                distance <= D4CPhase2BSpec.LIGHT_WALL_RANGE;
                distance++) {
            if (distance == 0) {
                continue;
            }
            double x = player.getX() + axisX * distance;
            double z = player.getZ() + axisZ * distance;
            for (int yOffset = D4CPhase2BSpec.LIGHT_WALL_MIN_Y_OFFSET;
                    yOffset
                            <= D4CPhase2BSpec.LIGHT_WALL_MAX_Y_OFFSET;
                    yOffset++) {
                BlockPos position = BlockPos.containing(
                        x,
                        player.getY() + yOffset,
                        z);
                Fluid fluid = level.getFluidState(position).getType();
                if (fluid == Fluids.WATER
                        || fluid == Fluids.FLOWING_WATER
                        || fluid == Fluids.LAVA
                        || fluid == Fluids.FLOWING_LAVA) {
                    level.setBlockAndUpdate(
                            position,
                            Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    public static void modifyIncomingDamage(
            LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level)
                || event.getAmount() <= 0.0F) {
            return;
        }

        LivingEntity attacker = resolveAttacker(event.getSource());
        if (attacker != null
                && (has(attacker, D4CAddon.SAINT_CORPSE_LEFT_ARM.get())
                        || has(
                                attacker,
                                D4CAddon.COMPLETE_SAINT_CORPSE.get()))
                && attacker.getRandom().nextFloat() < 0.1F) {
            event.setAmount(event.getAmount() * 1.5F);
        }

        if (target.hasEffect(D4CAddon.LOVE_TRAIN)) {
            event.setCanceled(true);
            if (attacker != null
                    && !event.getSource().is(
                            DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                redirectMisfortune(level, target);
            }
            return;
        }

        if (target instanceof ServerPlayer player
                && event.getAmount() >= target.getHealth()
                && canSpineRescue(player)) {
            rescueWithSpine(player);
            event.setAmount(0.0F);
            return;
        }
    }

    private static void redirectMisfortune(
            ServerLevel level,
            LivingEntity target) {
        List<LivingEntity> candidates = level.getEntitiesOfClass(
                LivingEntity.class,
                target.getBoundingBox().inflate(20.0D),
                living -> living.isAlive()
                        && living != target
                        && !(living instanceof AbstractHorse)
                        && !(living instanceof StandEntity)
                        && !living.hasEffect(D4CAddon.LOVE_TRAIN));
        if (candidates.isEmpty()) {
            return;
        }
        LivingEntity redirected = candidates.get(
                target.getRandom().nextInt(candidates.size()));
        redirected.hurt(
                redirected.damageSources().magic(),
                redirected.getMaxHealth() * 0.2F);
    }

    public static boolean hasLegs(LivingEntity entity) {
        return has(entity, D4CAddon.SAINT_CORPSE_LEGS.get())
                || has(entity, D4CAddon.COMPLETE_SAINT_CORPSE.get());
    }

    public static boolean hasHeartOrComplete(LivingEntity entity) {
        return has(entity, D4CAddon.SAINT_CORPSE_HEART.get())
                || has(entity, D4CAddon.COMPLETE_SAINT_CORPSE.get());
    }

    public static float staminaCapacityMultiplier(
            LivingEntity entity) {
        if (entity == null) {
            return 1.0F;
        }
        if (has(entity, D4CAddon.COMPLETE_SAINT_CORPSE.get())) {
            return 4.0F;
        }
        return 1.0F + componentCount(entity) * 0.3F;
    }

    public static float staminaRegenerationMultiplier(
            LivingEntity entity) {
        if (entity == null) {
            return 1.0F;
        }
        if (has(entity, D4CAddon.COMPLETE_SAINT_CORPSE.get())) {
            return 2.5F;
        }
        return 1.0F + componentCount(entity) * 0.15F;
    }

    // 1.16 StandPowerMixin / StaminaRegenMixin: any player Stand user
    static void registerStaminaModifiers() {
        if (!RotpAddonApi.supportsFeature(
                RotpAddonApi.FEATURE_STAND_STAMINA_MODIFIERS_V1)) {
            return;
        }
        StandStaminaModifiers.registerMaxStamina(
                D4CAddon.id("saint_corpse_stamina"),
                power -> power.getUser() instanceof Player player
                        ? staminaCapacityMultiplier(player)
                        : 1.0F);
        StandStaminaModifiers.registerTickGain(
                D4CAddon.id("saint_corpse_stamina"),
                power -> power.getUser() instanceof Player player
                        ? staminaRegenerationMultiplier(player)
                        : 1.0F);
    }

    private static int componentCount(LivingEntity entity) {
        int count = 0;
        for (Item item : List.of(
                D4CAddon.SAINT_CORPSE_EARS.get(),
                D4CAddon.SAINT_CORPSE_EYES.get(),
                D4CAddon.SAINT_CORPSE_HEAD.get(),
                D4CAddon.SAINT_CORPSE_HEART.get(),
                D4CAddon.SAINT_CORPSE_LEFT_ARM.get(),
                D4CAddon.SAINT_CORPSE_RIGHT_ARM.get(),
                D4CAddon.SAINT_CORPSE_LEGS.get(),
                D4CAddon.SAINT_CORPSE_THORAX.get(),
                D4CAddon.SAINT_CORPSE_SPINE.get())) {
            if (has(entity, item)) {
                count++;
            }
        }
        return count;
    }

    private static boolean canSpineRescue(ServerPlayer player) {
        if (!has(player, D4CAddon.SAINT_CORPSE_SPINE.get())
                && !has(
                        player,
                        D4CAddon.COMPLETE_SAINT_CORPSE.get())) {
            return false;
        }
        return player.level().getGameTime()
                >= player.getPersistentData().getLong(SPINE_READY_AT);
    }

    private static void rescueWithSpine(ServerPlayer player) {
        player.getPersistentData().putLong(
                SPINE_READY_AT,
                player.level().getGameTime()
                        + D4CPhase2BSpec.SPINE_COOLDOWN_TICKS);
        player.removeAllEffects();
        player.setHealth(1.0F);
        player.addEffect(new MobEffectInstance(
                MobEffects.REGENERATION, 900, 1));
        player.addEffect(new MobEffectInstance(
                MobEffects.ABSORPTION, 100, 1));
        player.addEffect(new MobEffectInstance(
                MobEffects.FIRE_RESISTANCE, 800, 0));
        // 1.16 ao: pickup sound at pitch 0.5 plus 30 totem particles
        player.level().playSound(null,
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_PICKUP, player.getSoundSource(),
                1.0F, 0.5F);
        player.serverLevel().sendParticles(
                ParticleTypes.TOTEM_OF_UNDYING,
                player.getX(), player.getY() + 1.0D, player.getZ(),
                30, 0.5D, 0.5D, 0.5D, 0.1D);
    }

    // long ambient Night Vision so vanilla's last-200-tick dimming never shows
    private static void refreshNightVision(ServerPlayer player) {
        MobEffectInstance current =
                player.getEffect(MobEffects.NIGHT_VISION);
        if (current == null
                || (!current.isInfiniteDuration()
                        && current.getDuration()
                                <= D4CPhase2BSpec
                                        .NIGHT_VISION_REFRESH_BELOW_TICKS)) {
            player.addEffect(new MobEffectInstance(
                    MobEffects.NIGHT_VISION,
                    D4CPhase2BSpec.NIGHT_VISION_REFRESH_TICKS,
                    0,
                    true,
                    false,
                    true));
        }
        player.getPersistentData().putBoolean(EYES_NIGHT_VISION, true);
    }

    // part lost: drop only our ambient instance, like 1.16's tracked removal
    private static void clearNightVision(ServerPlayer player) {
        if (!player.getPersistentData().getBoolean(EYES_NIGHT_VISION)) {
            return;
        }
        player.getPersistentData().remove(EYES_NIGHT_VISION);
        MobEffectInstance current =
                player.getEffect(MobEffects.NIGHT_VISION);
        if (current != null
                && current.isAmbient()
                && !current.isInfiniteDuration()
                && current.getDuration()
                        <= D4CPhase2BSpec.NIGHT_VISION_REFRESH_TICKS) {
            player.removeEffect(MobEffects.NIGHT_VISION);
        }
    }

    private static void refresh(
            LivingEntity entity,
            net.minecraft.core.Holder<
                    net.minecraft.world.effect.MobEffect> effect,
            int amplifier) {
        entity.addEffect(new MobEffectInstance(
                effect,
                D4CPhase2BSpec.EFFECT_REFRESH_TICKS,
                amplifier,
                true,
                false,
                true));
    }

    private static boolean has(LivingEntity entity, Item item) {
        return D4CPhase2BService.hasCorpseItem(entity, item);
    }

    @Nullable
    private static LivingEntity resolveAttacker(DamageSource source) {
        LivingEntity living = resolveLiving(source.getEntity());
        return living != null ? living
                : resolveLiving(source.getDirectEntity());
    }

    @Nullable
    private static LivingEntity resolveLiving(Entity entity) {
        if (entity instanceof StandEntity stand
                && stand.getUser() != null) {
            return stand.getUser();
        }
        return entity instanceof LivingEntity living ? living : null;
    }
}
