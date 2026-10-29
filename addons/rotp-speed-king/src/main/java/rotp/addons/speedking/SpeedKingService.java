package rotp.addons.speedking;

import java.util.List;
import java.util.UUID;

import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.target.HitResultUtil;
import rotp.core.util.functions.DamageUtil;

import rotp.addons.speedking.entity.SpeedKingEntity;
import rotp.addons.speedking.entity.SpeedKingPickaxeEntity;
import rotp.addons.speedking.network.SpeedKingStatePayload;
import rotp.addons.speedking.state.HeatVisionTargetState;
import rotp.addons.speedking.state.HeatVisionTargetState.Lease;
import rotp.addons.speedking.state.SpeedKingState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SpeedKingService {
    private static final int HEAT_VISION_DURATION = 200;
    private static final int HEAT_VISION_EFFECT_GRACE_TICKS = 2;

    private SpeedKingService() {}

    public static SpeedKingEntity activeStand(LivingEntity user) {
        StandPower power = user != null ? StandPower.get(user) : null;
        return power != null
                && power.hasPower()
                && power.isSummoned()
                && power.getPowerType() == SpeedKingAddon.SPEED_KING.get()
                && power.getSummonedStandEntity()
                        instanceof SpeedKingEntity stand
                && stand.isAlive()
                && !stand.isRemoved()
                ? stand : null;
    }

    public static void holdPickaxe(ServerPlayer player, boolean held) {
        SpeedKingEntity stand = activeStand(player);
        if (stand != null) {
            stand.setItemInHand(
                    InteractionHand.MAIN_HAND,
                    held ? new ItemStack(Items.DIAMOND_PICKAXE)
                            : ItemStack.EMPTY);
        }
    }

    public static void throwPickaxe(
            ServerPlayer player, EntityActionInstance action) {
        SpeedKingEntity stand = activeStand(player);
        if (stand == null) {
            return;
        }
        holdPickaxe(player, false);
        SpeedKingPickaxeEntity pickaxe =
                new SpeedKingPickaxeEntity(stand, stand.level());
        pickaxe.setPos(
                stand.getX(),
                stand.getEyeY() - 0.2D,
                stand.getZ());
        // 1.16 standEntity.shootProjectile(pickaxe, 1.5F, 1.0F), whose
        // addProjectile scaled its damage and speed by the Stand's stats
        pickaxe.shootFromRotation(stand, 1.5F,
                stand.getProjectileInaccuracy(1.0F));
        action.addProjectileWithStandStats(pickaxe);
        stand.playSound(
                ModSoundEvents.STAND_PUNCH_LIGHT.get(),
                1.0F,
                0.9F);
    }

    public static void heatInjectionTick(
            ServerPlayer player, int tick) {
        SpeedKingEntity stand = activeStand(player);
        if (stand == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        float progress = Math.min(tick / 80.0F, 1.0F);
        double radius = 2.0D + 4.0D * progress;
        float damage = 2.0F + 8.0F * progress;
        AABB area = new AABB(
                stand.getX() - radius,
                stand.getY() - 1.0D,
                stand.getZ() - radius,
                stand.getX() + radius,
                stand.getY() + 3.0D,
                stand.getZ() + radius);
        if (tick % 5 == 0) {
            for (LivingEntity target : level.getEntitiesOfClass(
                    LivingEntity.class,
                    area,
                    entity -> entity != player && entity != stand)) {
                target.hurt(
                        DamageUtil.make(
                                level,
                                ModDamageTypes.STAND_EXPLOSION_FIRE,
                                stand,
                                player),
                        damage);
                target.igniteForSeconds(5);
            }
        }
        boolean soul = progress > 0.7F;
        int count = (int) (25.0F + progress * 50.0F);
        level.sendParticles(
                soul ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME,
                stand.getX(), stand.getY() + 0.5D, stand.getZ(),
                count,
                radius * 0.75D, 1.25D, radius * 0.75D,
                0.02D);
        if (tick == 0) {
            level.playSound(
                    null,
                    stand.blockPosition(),
                    SoundEvents.FIRECHARGE_USE,
                    SoundSource.PLAYERS,
                    1.0F,
                    1.0F);
        }
    }

    public static void heatVision(ServerPlayer player) {
        SpeedKingEntity stand = activeStand(player);
        if (stand == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        cleanupHeatVision(player);
        UUID leaseId = UUID.randomUUID();
        long expiresAt = level.getGameTime() + HEAT_VISION_DURATION;
        SpeedKingAddon.state(player).beginHeatVisionLease(
                leaseId,
                expiresAt,
                level.dimension().location().toString());
        AABB area = new AABB(
                stand.getX() - 20.0D,
                stand.getY() - 5.0D,
                stand.getZ() - 20.0D,
                stand.getX() + 20.0D,
                stand.getY() + 5.0D,
                stand.getZ() + 20.0D);
        for (LivingEntity target : level.getEntitiesOfClass(
                LivingEntity.class,
                area,
                entity -> entity != player && entity != stand)) {
            acquireHeatVisionLease(
                    player, target, leaseId, expiresAt);
        }
        level.sendParticles(
                ParticleTypes.GLOW,
                stand.getX(), stand.getY() + 1.0D, stand.getZ(),
                30,
                2.0D, 1.25D, 2.0D,
                0.01D);
    }

    public static void heatCook(ServerPlayer player) {
        ItemStack offhand = player.getOffhandItem();
        if (offhand.isEmpty()) {
            return;
        }
        SingleRecipeInput input = new SingleRecipeInput(offhand);
        player.serverLevel().getRecipeManager()
                .getRecipeFor(
                        RecipeType.SMELTING,
                        input,
                        player.serverLevel())
                .map(RecipeHolder::value)
                .ifPresent(recipe -> {
                    ItemStack result = recipe.assemble(
                            input,
                            player.serverLevel().registryAccess());
                    if (!result.isEmpty()) {
                        result.setCount(offhand.getCount());
                        player.setItemInHand(
                                InteractionHand.OFF_HAND, result);
                        player.serverLevel().playSound(
                                null,
                                player.blockPosition(),
                                SoundEvents.FIRECHARGE_USE,
                                SoundSource.PLAYERS,
                                0.5F,
                                1.0F);
                        player.serverLevel().sendParticles(
                                ParticleTypes.FLAME,
                                player.getX(),
                                player.getY() + 1.0D,
                                player.getZ(),
                                12,
                                0.4D, 0.25D, 0.4D,
                                0.02D);
                    }
                });
    }

    public static void heatGrabTick(
            ServerPlayer player, int tick) {
        SpeedKingEntity stand = activeStand(player);
        if (stand == null) {
            clearGrab(player);
            return;
        }
        ServerLevel level = player.serverLevel();
        SpeedKingState state = SpeedKingAddon.state(player);
        Vec3 userPos = player.position();
        Vec3 look = player.getLookAngle();
        Vec3 standPos = stand.position();

        if (tick < 8) {
            moveStandTowards(
                    stand,
                    userPos.add(look.scale(3.5D)).add(0.0D, 1.0D, 0.0D),
                    1.2D);
            if (tick == 7 && state.grabbedTargetId() < 0) {
                List<Entity> targets = level.getEntities(
                        stand,
                        new AABB(
                                standPos.x - 2.0D,
                                standPos.y - 5.0D,
                                standPos.z - 2.0D,
                                standPos.x + 2.0D,
                                standPos.y + 5.0D,
                                standPos.z + 2.0D),
                        entity -> entity instanceof LivingEntity living
                                && living.isAlive()
                                && living != player
                                && living != stand);
                if (!targets.isEmpty()) {
                    state.setGrabbedTargetId(targets.get(0).getId());
                }
            }
            emitGrabParticles(level, stand.position(), null, tick, 8);
            return;
        }

        Entity entity = state.grabbedTargetId() >= 0
                ? level.getEntity(state.grabbedTargetId()) : null;
        LivingEntity target = entity instanceof LivingEntity living
                && living.isAlive() ? living : null;
        if (tick < 59) {
            moveStandTowards(
                    stand,
                    userPos.add(look.scale(1.2D)).add(0.0D, 0.5D, 0.0D),
                    0.6D);
            if (target == null) {
                emitGrabParticles(level, stand.position(), null, tick, 4);
                return;
            }
            if (tick == 8) {
                target.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SLOWDOWN,
                        100,
                        1));
            }
            Vec3 drag = stand.position().subtract(target.position());
            if (drag.length() > 1.0D) {
                Vec3 next = target.position().add(
                        drag.normalize().scale(0.5D));
                target.teleportTo(next.x, next.y, next.z);
            }
            if (tick % 5 == 0) {
                target.hurt(
                        DamageUtil.make(
                                level,
                                ModDamageTypes.STAND_EXPLOSION_FIRE,
                                stand,
                                player),
                        2.5F);
                target.igniteForSeconds(3);
            }
            if (tick % 8 == 0) {
                level.playSound(
                        null,
                        stand.blockPosition(),
                        SoundEvents.BLAZE_SHOOT,
                        SoundSource.PLAYERS,
                        0.6F,
                        1.0F + level.random.nextFloat() * 0.3F);
            }
            emitGrabParticles(
                    level, stand.position(), target.position(), tick, 15);
            return;
        }

        if (target != null) {
            target.hurt(
                    DamageUtil.make(
                            level,
                            ModDamageTypes.STAND_EXPLOSION_FIRE,
                            stand,
                            player),
                    8.0F);
            target.knockback(0.8D, -look.x, -look.z);
            level.playSound(
                    null,
                    stand.blockPosition(),
                    SoundEvents.GENERIC_EXPLODE.value(),
                    SoundSource.PLAYERS,
                    0.8F,
                    1.5F);
            emitGrabParticles(
                    level, stand.position(), target.position(), tick, 25);
        }
        clearGrab(player);
    }

    public static void placeHeatTrap(ServerPlayer player) {
        SpeedKingEntity stand = activeStand(player);
        if (stand == null) {
            return;
        }
        LivingEntity source = stand.isManuallyControlled() ? stand : player;
        // 1.16 standPerform's JojoModUtil.rayTrace took an entity on the ray
        // first, through blocks, and then set no trap
        if (!HitResultUtil.clipMultipleEntities(
                source.getEyePosition(),
                source.getLookAngle(),
                SpeedKingAddon.MAX_RANGE,
                source.level(),
                source,
                entity -> entity != stand && entity != player,
                0.0D,
                0.0D).isEmpty()) {
            return;
        }
        // and met blocks by their outline: the trap goes on grass, a flower
        // or a torch it meets, Speed King to the point the ray met (:79)
        Vec3 eyes = source.getEyePosition();
        BlockHitResult hit = HitResultUtil.clipBlocks(
                new ClipContext(
                        eyes,
                        eyes.add(source.getLookAngle()
                                .scale(SpeedKingAddon.MAX_RANGE)),
                        ClipContext.Block.OUTLINE,
                        ClipContext.Fluid.NONE,
                        source),
                source.level());
        if (hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        SpeedKingState state = SpeedKingAddon.state(player);
        if (state.hasHeatTrap()) {
            triggerHeatTrap(player, stand);
        }
        state.setHeatTrap(
                pos,
                player.serverLevel().dimension().location().toString());
        Vec3 targetPos = hit.getLocation();
        stand.teleportTo(targetPos.x, targetPos.y, targetPos.z);
        player.serverLevel().playSound(
                null,
                pos,
                SoundEvents.FIRECHARGE_USE,
                SoundSource.PLAYERS,
                1.0F,
                1.2F);
        // 1.16 standPerform :83-89: 20 flames, each sent alone, over the
        // block's centre +-0.6 across and from it to 1 block up
        ServerLevel level = player.serverLevel();
        for (int i = 0; i < 20; i++) {
            level.sendParticles(
                    ParticleTypes.FLAME,
                    pos.getX() + 0.5D + (level.random.nextDouble() - 0.5D) * 1.2D,
                    pos.getY() + 0.5D + level.random.nextDouble(),
                    pos.getZ() + 0.5D + (level.random.nextDouble() - 0.5D) * 1.2D,
                    1,
                    0.0D, 0.05D, 0.0D,
                    0.02D);
        }
        sync(player);
    }

    public static boolean triggerHeatTrap(ServerPlayer player) {
        return triggerHeatTrap(player, activeStand(player));
    }

    private static boolean triggerHeatTrap(
            ServerPlayer player, SpeedKingEntity stand) {
        SpeedKingState state = SpeedKingAddon.state(player);
        if (!state.hasHeatTrap()
                || !sameTrapDimension(player, state)) {
            return false;
        }
        BlockPos pos = state.heatTrapPos();
        ServerLevel level = player.serverLevel();
        AABB area = trapArea(pos);
        for (LivingEntity target : level.getEntitiesOfClass(
                LivingEntity.class,
                area,
                entity -> entity.isAlive()
                        && entity != player
                        && entity != stand)) {
            target.hurt(
                    DamageUtil.make(
                            level,
                            ModDamageTypes.STAND_EXPLOSION_FIRE,
                            stand,
                            player),
                    8.0F);
            target.igniteForSeconds(5);
        }
        Vec3 center = Vec3.atCenterOf(pos);
        level.sendParticles(
                ParticleTypes.FLAME,
                center.x, center.y, center.z,
                40,
                1.5D, 1.0D, 1.5D,
                0.02D);
        level.sendParticles(
                ParticleTypes.SOUL_FIRE_FLAME,
                center.x, center.y, center.z,
                20,
                1.0D, 1.25D, 1.0D,
                0.01D);
        level.playSound(
                null,
                pos,
                SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.BLOCKS,
                1.0F,
                1.5F);
        state.clearHeatTrap();
        sync(player);
        return true;
    }

    public static void tickHeatTrap(
            ServerPlayer player, SpeedKingEntity stand) {
        SpeedKingState state = SpeedKingAddon.state(player);
        if (!state.hasHeatTrap() || !sameTrapDimension(player, state)) {
            return;
        }
        BlockPos pos = state.heatTrapPos();
        if (stand.tickCount % 10 == 0) {
            Vec3 center = Vec3.atCenterOf(pos);
            player.serverLevel().sendParticles(
                    ParticleTypes.FLAME,
                    center.x,
                    center.y + 0.1D,
                    center.z,
                    2,
                    0.2D, 0.05D, 0.2D,
                    0.005D);
        }
        boolean occupied = !player.serverLevel().getEntitiesOfClass(
                LivingEntity.class,
                trapArea(pos),
                entity -> entity.isAlive()
                        && entity != player
                        && entity != stand).isEmpty();
        if (occupied) {
            triggerHeatTrap(player, stand);
        }
    }

    public static void clearGrab(ServerPlayer player) {
        SpeedKingAddon.state(player).setGrabbedTargetId(-1);
    }

    public static void cleanupTransient(ServerPlayer player) {
        SpeedKingState state = SpeedKingAddon.state(player);
        boolean hadVision = state.heatVisionLeaseId() != null
                || state.heatVisionExpiresAt() > 0L;
        holdPickaxe(player, false);
        cleanupHeatVision(player);
        if (state.clearTransient() || hadVision) {
            sync(player);
        }
    }

    // 1.16 kept the heat trap on the Stand entity, so unsummoning, removing
    // or replacing the Stand dropped it.
    public static void onStandCleared(LivingEntity user) {
        if (!(user instanceof ServerPlayer player)
                || player.level().isClientSide()) {
            return;
        }
        cleanupTransient(player);
        if (SpeedKingAddon.state(player).clearAll()) {
            sync(player);
        }
    }

    public static void cleanupHeatVision(ServerPlayer player) {
        UUID ownerUuid = player.getUUID();
        UUID leaseId = SpeedKingAddon.state(player).heatVisionLeaseId();
        for (ServerLevel level : player.server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof LivingEntity living
                        && living.hasData(
                                SpeedKingAddon.HEAT_VISION_TARGET_STATE)) {
                    releaseHeatVisionLease(living, ownerUuid);
                }
            }
        }
        if (leaseId != null) {
            removeTeamIfEmpty(player.server.getScoreboard(), leaseId);
        }
        SpeedKingAddon.state(player).clearHeatVisionLease();
    }

    public static void tickHeatVisionTarget(LivingEntity target) {
        reconcileHeatVisionTarget(target, false);
    }

    public static void rejoinHeatVisionTarget(LivingEntity target) {
        reconcileHeatVisionTarget(target, true);
    }

    private static void reconcileHeatVisionTarget(
            LivingEntity target, boolean allowFrozenGlowing) {
        if (target.level().isClientSide()
                || !target.hasData(
                        SpeedKingAddon.HEAT_VISION_TARGET_STATE)) {
            return;
        }
        HeatVisionTargetState state =
                SpeedKingAddon.heatVisionTargetState(target);
        if (!state.hasLeases()) {
            return;
        }
        for (Lease lease : state.leases()) {
            if (!heatVisionLeaseActive(target, lease)) {
                releaseHeatVisionLease(
                        target,
                        lease.ownerUuid(),
                        allowFrozenGlowing);
            }
        }
        if (state.hasLeases() && state.glowingOwned()) {
            if (!ownsCurrentGlowing(
                    target, state, allowFrozenGlowing)) {
                state.clearGlowingOwnership();
            }
            else {
                int duration = remainingDuration(
                        target, state.maxExpiresAt());
                MobEffectInstance current = target.getEffect(
                        MobEffects.GLOWING);
                if (current != null
                        && Math.abs(current.getDuration() - duration) > 2) {
                    installOwnedGlowing(target, state, duration);
                }
                else if (current != null) {
                    elapseOriginalGlowing(state, current);
                    state.observeOwnedGlowing(
                            state.maxExpiresAt(),
                            target.level().getGameTime(),
                            current.getDuration());
                }
            }
        }
    }

    public static void clearHeatVisionTarget(LivingEntity target) {
        if (target.level().isClientSide()
                || !target.hasData(
                        SpeedKingAddon.HEAT_VISION_TARGET_STATE)) {
            return;
        }
        HeatVisionTargetState state =
                SpeedKingAddon.heatVisionTargetState(target);
        while (state.hasLeases()) {
            releaseHeatVisionLease(
                    target, state.leases().get(0).ownerUuid());
        }
    }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                player, SpeedKingStatePayload.from(player));
    }

    private static boolean sameTrapDimension(
            ServerPlayer player, SpeedKingState state) {
        return state.heatTrapDimension().equals(
                player.serverLevel().dimension().location().toString());
    }

    private static void acquireHeatVisionLease(
            ServerPlayer owner,
            LivingEntity target,
            UUID leaseId,
            long expiresAt) {
        tickHeatVisionTarget(target);
        HeatVisionTargetState state =
                SpeedKingAddon.heatVisionTargetState(target);
        boolean firstLease = !state.hasLeases();
        boolean glowWasOwned = state.glowingOwned()
                && ownsCurrentGlowing(target, state, false);
        if (firstLease) {
            PlayerTeam originalTeam = target.getTeam();
            state.captureSnapshot(
                    originalTeam != null ? originalTeam.getName() : null,
                    target.getEffect(MobEffects.GLOWING));
        }
        state.putLease(owner.getUUID(), leaseId, expiresAt);

        Scoreboard scoreboard = target.level().getScoreboard();
        PlayerTeam team = getOrCreateHeatVisionTeam(
                scoreboard, leaseId);
        scoreboard.addPlayerToTeam(target.getScoreboardName(), team);

        if (firstLease || glowWasOwned) {
            installOwnedGlowing(
                    target,
                    state,
                    remainingDuration(target, state.maxExpiresAt()));
        }
        else {
            state.clearGlowingOwnership();
        }
    }

    private static void releaseHeatVisionLease(
            LivingEntity target, UUID ownerUuid) {
        releaseHeatVisionLease(target, ownerUuid, false);
    }

    private static void releaseHeatVisionLease(
            LivingEntity target,
            UUID ownerUuid,
            boolean allowFrozenGlowing) {
        HeatVisionTargetState state =
                SpeedKingAddon.heatVisionTargetState(target);
        Lease removed = state.leases().stream()
                .filter(lease -> lease.ownerUuid().equals(ownerUuid))
                .findFirst()
                .orElse(null);
        if (removed == null) {
            return;
        }
        PlayerTeam currentTeam = target.getTeam();
        Lease currentTeamLease = findLeaseForTeam(
                state.leases(), currentTeam);
        boolean glowWasOwned = state.glowingOwned()
                && ownsCurrentGlowing(
                        target, state, allowFrozenGlowing);
        state.removeLease(ownerUuid);

        Scoreboard scoreboard = target.level().getScoreboard();
        if (state.hasLeases()) {
            if (currentTeamLease != null
                    && currentTeamLease.leaseId().equals(
                            removed.leaseId())) {
                Lease replacement = state.lastLease();
                scoreboard.addPlayerToTeam(
                        target.getScoreboardName(),
                        getOrCreateHeatVisionTeam(
                                scoreboard, replacement.leaseId()));
            }
            if (glowWasOwned) {
                installOwnedGlowing(
                        target,
                        state,
                        remainingDuration(target, state.maxExpiresAt()));
            }
            else {
                state.clearGlowingOwnership();
            }
        }
        else {
            if (currentTeamLease != null) {
                restoreOriginalTeam(target, state, currentTeam);
            }
            if (glowWasOwned) {
                elapseOriginalGlowing(
                        state, target.getEffect(MobEffects.GLOWING));
                target.removeEffect(MobEffects.GLOWING);
                MobEffectInstance original =
                        state.remainingOriginalGlowing();
                if (original != null) {
                    target.addEffect(original);
                }
            }
            state.clearSnapshot();
        }
        removeTeamIfEmpty(scoreboard, removed.leaseId());
    }

    private static boolean heatVisionLeaseActive(
            LivingEntity target, Lease lease) {
        if (!(target.level() instanceof ServerLevel level)
                || level.getGameTime() >= lease.expiresAt()) {
            return false;
        }
        ServerPlayer owner = level.getServer().getPlayerList()
                .getPlayer(lease.ownerUuid());
        if (owner == null
                || owner.level() != target.level()
                || !SpeedKingAddon.isUser(owner)
                || activeStand(owner) == null) {
            return false;
        }
        SpeedKingState ownerState = SpeedKingAddon.state(owner);
        return ownerState.matchesHeatVisionLease(
                lease.leaseId(),
                lease.expiresAt(),
                level.dimension().location().toString());
    }

    private static void installOwnedGlowing(
            LivingEntity target,
            HeatVisionTargetState state,
            int duration) {
        // Lease cleanup must run before vanilla expiry discards our effect identity.
        int ownedDuration = (int) Math.min(Integer.MAX_VALUE,
                (long) duration + HEAT_VISION_EFFECT_GRACE_TICKS);
        elapseOriginalGlowing(state, target.getEffect(MobEffects.GLOWING));
        target.removeEffect(MobEffects.GLOWING);
        target.addEffect(new MobEffectInstance(
                MobEffects.GLOWING,
                ownedDuration,
                0,
                false,
                false,
                false));
        state.observeOwnedGlowing(
                state.maxExpiresAt(),
                target.level().getGameTime(),
                ownedDuration);
    }

    // Our Glowing only ticks when the target's effects tick, so each tick it ran
    // since the last observation is a tick the 1.16 original also ran.
    private static void elapseOriginalGlowing(
            HeatVisionTargetState state, MobEffectInstance current) {
        if (current != null && state.glowingOwned()) {
            state.elapseOriginalGlowing(
                    state.observedGlowingDuration() - current.getDuration());
        }
    }

    private static boolean ownsCurrentGlowing(
            LivingEntity target,
            HeatVisionTargetState state,
            boolean allowFrozenGlowing) {
        MobEffectInstance current = target.getEffect(MobEffects.GLOWING);
        if (current == null
                || current.getAmplifier() != 0
                || current.isAmbient()
                || current.isVisible()
                || current.showIcon()
                || state.observedGlowingDuration() <= 0) {
            return false;
        }
        long elapsed = Math.max(
                0L,
                target.level().getGameTime()
                        - state.observedGlowingGameTime());
        int expectedDuration = (int) Math.max(
                1L,
                state.observedGlowingDuration()
                        - Math.min(Integer.MAX_VALUE, elapsed));
        if (allowFrozenGlowing) {
            return current.getDuration()
                    <= state.observedGlowingDuration() + 1
                    && current.getDuration() >= expectedDuration - 2;
        }
        return Math.abs(current.getDuration() - expectedDuration) <= 2;
    }

    private static int remainingDuration(
            LivingEntity target, long expiresAt) {
        long remaining = Math.max(
                1L, expiresAt - target.level().getGameTime());
        return (int) Math.min(Integer.MAX_VALUE, remaining);
    }

    private static Lease findLeaseForTeam(
            List<Lease> leases, PlayerTeam team) {
        if (team == null) {
            return null;
        }
        for (Lease lease : leases) {
            if (team.getName().equals(
                    heatVisionTeamName(lease.leaseId()))) {
                return lease;
            }
        }
        return null;
    }

    private static PlayerTeam getOrCreateHeatVisionTeam(
            Scoreboard scoreboard, UUID leaseId) {
        String teamName = heatVisionTeamName(leaseId);
        PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team == null) {
            team = scoreboard.addPlayerTeam(teamName);
        }
        team.setColor(ChatFormatting.GOLD);
        return team;
    }

    private static void restoreOriginalTeam(
            LivingEntity target,
            HeatVisionTargetState state,
            PlayerTeam currentTeam) {
        Scoreboard scoreboard = target.level().getScoreboard();
        scoreboard.removePlayerFromTeam(
                target.getScoreboardName(), currentTeam);
        String originalTeamName = state.originalTeamName();
        if (originalTeamName != null) {
            PlayerTeam originalTeam = scoreboard.getPlayerTeam(
                    originalTeamName);
            if (originalTeam != null) {
                scoreboard.addPlayerToTeam(
                        target.getScoreboardName(), originalTeam);
            }
        }
    }

    private static void removeTeamIfEmpty(
            Scoreboard scoreboard, UUID leaseId) {
        PlayerTeam team = scoreboard.getPlayerTeam(
                heatVisionTeamName(leaseId));
        if (team != null && team.getPlayers().isEmpty()) {
            scoreboard.removePlayerTeam(team);
        }
    }

    private static String heatVisionTeamName(UUID leaseId) {
        return "skv" + leaseId.toString()
                .replace("-", "").substring(0, 13);
    }

    private static AABB trapArea(BlockPos pos) {
        return new AABB(
                pos.getX() - 2.0D,
                pos.getY() - 1.0D,
                pos.getZ() - 2.0D,
                pos.getX() + 2.0D,
                pos.getY() + 3.0D,
                pos.getZ() + 2.0D);
    }

    private static void moveStandTowards(
            SpeedKingEntity stand, Vec3 destination, double maxStep) {
        Vec3 direction = destination.subtract(stand.position());
        double distance = direction.length();
        if (distance > 0.1D) {
            Vec3 next = stand.position().add(
                    direction.normalize().scale(Math.min(distance, maxStep)));
            stand.teleportTo(next.x, next.y, next.z);
        }
    }

    private static void emitGrabParticles(
            ServerLevel level,
            Vec3 standPos,
            Vec3 targetPos,
            int tick,
            int count) {
        Vec3 center = targetPos != null
                ? standPos.add(targetPos).scale(0.5D)
                : standPos;
        level.sendParticles(
                tick / 2 % 2 == 0
                        ? ParticleTypes.SOUL_FIRE_FLAME
                        : ParticleTypes.FLAME,
                center.x,
                center.y + 0.75D,
                center.z,
                count,
                targetPos != null ? 1.25D : 0.5D,
                targetPos != null ? 1.0D : 0.75D,
                targetPos != null ? 1.25D : 0.5D,
                0.02D);
    }
}
