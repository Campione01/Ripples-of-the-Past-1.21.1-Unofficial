package rotp.addons.cream.entity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.util.functions.JojoModUtil;

import rotp.addons.cream.CreamAddon;
import rotp.addons.cream.CreamConfig;
import rotp.addons.cream.CreamSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CreamEntity extends StandEntity {
    private static final EntityDataAccessor<Byte> VOID_MODE =
            SynchedEntityData.defineId(
                    CreamEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> VOID_TICKS =
            SynchedEntityData.defineId(
                    CreamEntity.class, EntityDataSerializers.INT);

    private final Set<UUID> erasedEntities = new HashSet<>();
    private final Map<UUID, Integer> damageCooldowns =
            new HashMap<>();

    private VoidMode entryMode = VoidMode.NONE;
    private int totalVoidTicks;
    private final CreamFlightLease flightLease = new CreamFlightLease();

    public CreamEntity(
            EntityType<? extends CreamEntity> type, Level level) {
        super(type, level);
        // 1.16 CreamEntity.getDefaultOffsetFromUser: straight behind the
        // user, 0.5 up; arms-only at the user's feet. The 0.5 is also the
        // default y of task offsets without their own.
        Y_OFFSET = 0.5D;
        offsetFromUser.idleOffset = new Vec3(0.0D, Y_OFFSET, -0.5D);
        offsetFromUser.idleRotations = StandOffsetFromUser.Rotations.HEAD;
        offsetFromUser.armsOnlyIdleOffset = Vec3.ZERO;
        offsetFromUser.armsOnlyIdleRotations =
                StandOffsetFromUser.Rotations.HEAD;
        offsetFromUser.grabIdleOffset = new Vec3(-1.0D, Y_OFFSET, 1.5D);
        offsetFromUser.resetToIdle();
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(VOID_MODE, VoidMode.NONE.id);
        builder.define(VOID_TICKS, 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && isInVoidState()) {
            tickVoidState();
        }
    }

    @Override
    public void updatePosition(LivingEntity user) {
        if (!isInVoidState()) {
            super.updatePosition(user);
            return;
        }
        setPos(user.getX(), user.getY(), user.getZ());
        setDeltaMovement(Vec3.ZERO);
        setYRot(user.getYRot());
        setXRot(user.getXRot());
        yBodyRot = user.yBodyRot;
    }

    @Override
    public void onUnsummonUserInput() {
        if (!level().isClientSide()) {
            endVoidMode(false);
        }
        super.onUnsummonUserInput();
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!level().isClientSide()) {
            endVoidMode(false);
        }
        super.remove(reason);
    }

    public VoidMode getVoidMode() {
        return VoidMode.fromId(entityData.get(VOID_MODE));
    }

    public int getVoidTicks() {
        return entityData.get(VOID_TICKS);
    }

    public boolean isInVoidState() {
        return getVoidMode() != VoidMode.NONE;
    }

    public void beginVoidMode(VoidMode requested) {
        if (level().isClientSide()
                || requested == null
                || requested == VoidMode.NONE) {
            return;
        }
        VoidMode current = getVoidMode();
        if (requested == VoidMode.DASH
                && (current == VoidMode.BALL
                        || current == VoidMode.SEMI)) {
            if (entryMode == VoidMode.NONE) {
                entryMode = current;
            }
        }
        else {
            entryMode = requested;
            totalVoidTicks = 0;
            erasedEntities.clear();
            damageCooldowns.clear();
        }
        entityData.set(VOID_MODE, requested.id, true);
        entityData.set(VOID_TICKS, 0, true);
        grantFlightLease();

        if (requested == VoidMode.DASH) {
            playVoidSound(CreamAddon.CREAM_VOID_START.get());
        }
    }

    public void endVoidMode(boolean applyCooldown) {
        if (level().isClientSide()) {
            return;
        }
        VoidMode ending = getVoidMode();
        if (ending == VoidMode.NONE && !flightLease.isLeased()) {
            return;
        }
        LivingEntity user = getUser();
        VoidMode origin = entryMode;
        int usedTicks = totalVoidTicks;
        entityData.set(VOID_MODE, VoidMode.NONE.id, true);
        entityData.set(VOID_TICKS, 0, true);
        entryMode = VoidMode.NONE;
        totalVoidTicks = 0;
        erasedEntities.clear();
        damageCooldowns.clear();

        restoreFlightLease();
        if (user != null) {
            user.removeEffect(CreamAddon.VOIDED);
            user.removeEffect(CreamAddon.INSIDE_CREAM);
            user.removeEffect(CreamAddon.TRUE_BLINDNESS);
            if (ending != VoidMode.NONE) {
                playVoidSound(CreamAddon.CREAM_VOID_END.get());
            }
            if (applyCooldown
                    && (!(user instanceof Player player)
                            || !player.isCreative())) {
                applyCooldowns(
                        user, origin, ending, usedTicks);
            }
        }
    }

    public static CreamEntity activeFor(LivingEntity user) {
        StandPower power = user != null
                ? StandPower.get(user)
                : null;
        return power != null
                        && power.getSummonedStandEntity()
                                instanceof CreamEntity cream
                ? cream
                : null;
    }

    public static boolean isInVoidState(LivingEntity user) {
        CreamEntity cream = activeFor(user);
        return cream != null && cream.isInVoidState();
    }

    /** The dash's tick in the tick its perform starts, before Cream's own tick runs the rest. */
    public void tickVoidDashStart() {
        if (!level().isClientSide()
                && getVoidMode() == VoidMode.DASH) {
            tickVoidState();
        }
    }

    private void tickVoidState() {
        LivingEntity user = getUser();
        StandPower power = getUserPower();
        if (user == null
                || power == null
                || !user.isAlive()
                || user.isRemoved()
                || user instanceof Player player
                        && player.isSpectator()) {
            endAndClearAction(false);
            return;
        }

        VoidMode mode = getVoidMode();
        // 1.16 CreamVoidBall and CreamSemiVoidState cancelled at the top
        // of every tick once the bar read 0, Resolve or not.
        if (mode != VoidMode.DASH && power.getStamina() <= 0.0F) {
            endAndClearAction(true);
            return;
        }
        int modeTicks = getVoidTicks() + 1;
        totalVoidTicks++;
        entityData.set(VOID_TICKS, modeTicks);
        tickDamageCooldowns();

        float staminaCost = staminaCostPerTick(
                user, power, mode);
        if (mode == VoidMode.DASH) {
            // 1.16 CreamVoidDash had only staminaCostTick(4), which
            // StandEntityTask paid as a ticking cost with the result
            // ignored: the dash runs its 20 ticks on a short bar.
            power.consumeStamina(staminaCost, true);
        }
        else if (staminaCost > 0.0F
                && !power.consumeStamina(staminaCost)) {
            endAndClearAction(true);
            return;
        }

        maintainUserState(user, mode);
        if (mode == VoidMode.DASH) {
            Vec3 horizontal = new Vec3(
                    user.getLookAngle().x,
                    0.0D,
                    user.getLookAngle().z);
            if (horizontal.lengthSqr() > 1.0E-5D) {
                horizontal = horizontal.normalize().scale(5.0D);
                user.setDeltaMovement(
                        horizontal.x,
                        user.getDeltaMovement().y,
                        horizontal.z);
                user.hurtMarked = true;
            }
        }

        if (mode == VoidMode.BALL || mode == VoidMode.DASH) {
            eraseNearby(user, mode);
            eraseBlocks(user);
        }

        int duration = mode == VoidMode.BALL
                ? CreamConfig.VOID_BALL_DURATION.get()
                : mode == VoidMode.SEMI
                        ? CreamConfig.SEMI_VOID_DURATION.get()
                        : Integer.MAX_VALUE;
        // 1.16 CreamVoidBall also let go at the end of the tick that
        // emptied the bar.
        if (modeTicks >= duration
                || mode == VoidMode.BALL && power.getStamina() <= 0.0F) {
            endAndClearAction(true);
        }
    }

    private void maintainUserState(
            LivingEntity user, VoidMode mode) {
        grantFlightLease();
        user.fallDistance = 0.0F;
        if (mode == VoidMode.SEMI) {
            user.addEffect(new MobEffectInstance(
                    CreamAddon.INSIDE_CREAM,
                    4,
                    0,
                    false,
                    false,
                    true));
            user.removeEffect(CreamAddon.TRUE_BLINDNESS);
        }
        else {
            user.addEffect(new MobEffectInstance(
                    CreamAddon.VOIDED,
                    4,
                    0,
                    false,
                    false,
                    true));
            user.addEffect(new MobEffectInstance(
                    CreamAddon.TRUE_BLINDNESS,
                    4,
                    0,
                    false,
                    false,
                    true));
        }
        user.addEffect(new MobEffectInstance(
                MobEffects.INVISIBILITY,
                4,
                0,
                false,
                false,
                false));
    }

    private void grantFlightLease() {
        if (!(getUser() instanceof ServerPlayer player)) {
            return;
        }
        flightLease.capture(
                player.gameMode.getGameModeForPlayer(),
                player.getAbilities());
        float speed = getVoidMode() == VoidMode.SEMI
                ? (player.isSprinting()
                        ? CreamConfig.SEMI_VOID_SPRINT_FLY_SPEED
                                .get().floatValue()
                        : CreamConfig.SEMI_VOID_FLY_SPEED
                                .get().floatValue())
                : 0.05F;
        boolean changed = !player.getAbilities().mayfly
                || !player.getAbilities().flying
                || player.getAbilities().getFlyingSpeed()
                        != speed;
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.getAbilities().setFlyingSpeed(speed);
        if (changed) {
            player.onUpdateAbilities();
        }
    }

    private void restoreFlightLease() {
        if (!flightLease.isLeased()) {
            return;
        }
        if (getUser() instanceof ServerPlayer player) {
            if (flightLease.restore(
                    player.gameMode.getGameModeForPlayer(),
                    player.getAbilities())) {
                player.onUpdateAbilities();
            }
        }
        else {
            flightLease.clear();
        }
    }

    private void eraseNearby(
            LivingEntity user, VoidMode mode) {
        AABB area = user.getBoundingBox()
                .inflate(CreamSpec.VOID_RADIUS);
        for (Entity entity : level().getEntities(
                user, area, candidate -> candidate != this)) {
            if (entity == user
                    || entity.isPassengerOfSameVehicle(user)
                    || isEntityBlacklisted(entity)
                    || entity instanceof ItemEntity
                            && !deleteItems(mode)) {
                continue;
            }
            // 1.16 gathered without spectators; the ball also spared a
            // player whose abilities.invulnerable was set, the dash any
            // entity with the invulnerable flag (a Creative player stayed
            // hit by the dash's out-of-world damage).
            if (entity.isSpectator()
                    || mode == VoidMode.BALL
                            && entity instanceof Player player
                            && player.getAbilities().invulnerable
                    || mode == VoidMode.DASH
                            && entity.isInvulnerable()) {
                continue;
            }

            if (entity instanceof LivingEntity living) {
                if (!JojoModUtil.canHarm(user, living)
                        || erasedEntities.contains(
                                living.getUUID())) {
                    continue;
                }
                boolean hit;
                if (CreamConfig.VOID_INSTAKILLS.get()) {
                    hit = living.hurt(
                            living.damageSources().genericKill(),
                            Float.MAX_VALUE);
                    if (hit) {
                        erasedEntities.add(living.getUUID());
                    }
                }
                else if (!damageCooldowns.containsKey(
                        living.getUUID())) {
                    hit = living.hurt(
                            living.damageSources().fellOutOfWorld(),
                            CreamConfig.VOID_DAMAGE.get()
                                    .floatValue());
                    damageCooldowns.put(
                            living.getUUID(),
                            CreamConfig.VOID_DAMAGE_COOLDOWN.get());
                }
                else {
                    hit = false;
                }
                if (hit) {
                    playVoidSound(
                            CreamAddon.CREAM_ENTITY_VOID.get());
                }
            }
            else if (erasedEntities.add(entity.getUUID())) {
                entity.discard();
                playVoidSound(CreamAddon.CREAM_VOID.get());
            }
        }
    }

    private void eraseBlocks(LivingEntity user) {
        if (canBreakBlocks()) {
            BlockPos center = BlockPos.containing(
                    user.getX(),
                    user.getY() - 0.5D,
                    user.getZ());
            for (BlockPos pos : BlockPos.betweenClosed(
                    center.offset(-1, -1, -1),
                    center.offset(1, 1, 1))) {
                BlockState state = level().getBlockState(pos);
                if (!state.isAir()
                        && state.getDestroySpeed(level(), pos) >= 0.0F
                        && !isBlockBlacklisted(state)) {
                    level().destroyBlock(pos, false, user);
                }
            }
        }

        if (CreamConfig.CONVERT_GRASS_TO_DIRT.get()) {
            BlockPos below = user.blockPosition().below();
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos pos = below.offset(x, 0, z);
                    BlockState state = level().getBlockState(pos);
                    if (state.is(Blocks.GRASS_BLOCK)
                            || state.is(Blocks.MYCELIUM)
                            || state.is(Blocks.PODZOL)) {
                        level().setBlockAndUpdate(
                                pos, Blocks.DIRT.defaultBlockState());
                    }
                }
            }
        }
    }

    private boolean canBreakBlocks() {
        return CreamConfig.EXCLUDE_VOID_FROM_BLOCK_BREAKING.get()
                || JojoModUtil.breakingBlocksEnabled(level());
    }

    private boolean isBlockBlacklisted(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(
                state.getBlock());
        return state.is(CreamAddon.VOID_BARRIER)
                || CreamConfig.VOID_BLOCK_BLACKLIST.get()
                        .contains(id.toString());
    }

    private boolean isEntityBlacklisted(Entity entity) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(
                entity.getType());
        return CreamConfig.VOID_ENTITY_BLACKLIST.get()
                .contains(id.toString());
    }

    private static boolean deleteItems(VoidMode mode) {
        return mode == VoidMode.DASH
                ? CreamConfig.VOID_DASH_DELETE_ITEMS.get()
                : CreamConfig.VOID_BALL_DELETE_ITEMS.get();
    }

    private static float staminaCostPerTick(
            LivingEntity user,
            StandPower power,
            VoidMode mode) {
        if (mode == VoidMode.DASH) {
            return 4.0F;
        }
        boolean vampire = PlayerPower.getPowerData(
                user, ModPlayerPowers.VAMPIRISM).isPresent();
        if (!CreamConfig.PROGRESSIVE_STAMINA_COST.get()) {
            if (mode == VoidMode.BALL) {
                return (vampire
                        ? CreamConfig
                                .VOID_BALL_STAMINA_PER_TICK_VAMPIRE
                        : CreamConfig.VOID_BALL_STAMINA_PER_TICK)
                        .get().floatValue();
            }
            return (vampire
                    ? CreamConfig
                            .SEMI_VOID_STAMINA_PER_TICK_VAMPIRE
                    : CreamConfig.SEMI_VOID_STAMINA_PER_TICK)
                    .get().floatValue();
        }

        int resolve = power.getResolveLevel();
        if (resolve >= 4) {
            return (vampire
                    ? CreamConfig.STAMINA_COST_RESOLVE_4_VAMPIRE
                    : CreamConfig.STAMINA_COST_RESOLVE_4)
                    .get().floatValue();
        }
        if (resolve >= 3) {
            return (vampire
                    ? CreamConfig.STAMINA_COST_RESOLVE_3_VAMPIRE
                    : CreamConfig.STAMINA_COST_RESOLVE_3)
                    .get().floatValue();
        }
        return (vampire
                ? CreamConfig.STAMINA_COST_RESOLVE_2_VAMPIRE
                : CreamConfig.STAMINA_COST_RESOLVE_2)
                .get().floatValue();
    }

    private void applyCooldowns(
            LivingEntity user,
            VoidMode origin,
            VoidMode ending,
            int usedTicks) {
        StandPower power = StandPower.get(user);
        if (power == null) {
            return;
        }
        boolean vampire = PlayerPower.getPowerData(
                user, ModPlayerPowers.VAMPIRISM).isPresent();
        if (ending == VoidMode.DASH) {
            int cooldown = CreamConfig.VOID_DASH_COOLDOWN.get();
            power.setAbilityCooldown(
                    CreamAddon.CREAM_VOID_DASH_NAME, cooldown);
            if (origin == VoidMode.BALL) {
                power.setAbilityCooldown(
                        CreamAddon.CREAM_VOID_BALL_NAME, cooldown);
            }
            else if (origin == VoidMode.SEMI) {
                power.setAbilityCooldown(
                        CreamAddon.CREAM_SEMI_VOID_NAME, cooldown);
            }
            return;
        }
        if (origin == VoidMode.BALL) {
            power.setAbilityCooldown(
                    CreamAddon.CREAM_VOID_BALL_NAME,
                    ballCooldown(usedTicks, vampire));
        }
        else if (origin == VoidMode.SEMI) {
            power.setAbilityCooldown(
                    CreamAddon.CREAM_SEMI_VOID_NAME,
                    semiCooldown(usedTicks, vampire));
        }
    }

    private static int ballCooldown(
            int usedTicks, boolean vampire) {
        if (!CreamConfig.VOID_BALL_DYNAMIC_COOLDOWN.get()) {
            return vampire
                    ? CreamConfig.VOID_BALL_VAMPIRE_COOLDOWN.get()
                    : CreamConfig.VOID_BALL_COOLDOWN.get();
        }
        double multiplier = vampire
                ? CreamConfig
                        .VOID_BALL_VAMPIRE_COOLDOWN_MULTIPLIER
                        .get()
                : CreamConfig.VOID_BALL_DYNAMIC_MULTIPLIER.get();
        int minimum = vampire
                ? CreamConfig.VOID_BALL_VAMPIRE_MINIMUM.get()
                : CreamConfig.VOID_BALL_DYNAMIC_MINIMUM.get();
        return Math.max(
                minimum, (int) Math.ceil(usedTicks * multiplier));
    }

    private static int semiCooldown(
            int usedTicks, boolean vampire) {
        if (!CreamConfig.SEMI_VOID_DYNAMIC_COOLDOWN.get()) {
            return vampire
                    ? CreamConfig.SEMI_VOID_VAMPIRE_COOLDOWN.get()
                    : CreamConfig.SEMI_VOID_COOLDOWN.get();
        }
        double multiplier = vampire
                ? CreamConfig
                        .SEMI_VOID_VAMPIRE_COOLDOWN_MULTIPLIER
                        .get()
                : CreamConfig.SEMI_VOID_DYNAMIC_MULTIPLIER.get();
        int minimum = vampire
                ? CreamConfig.SEMI_VOID_VAMPIRE_MINIMUM.get()
                : CreamConfig.SEMI_VOID_DYNAMIC_MINIMUM.get();
        return Math.max(
                minimum, (int) Math.ceil(usedTicks * multiplier));
    }

    private void tickDamageCooldowns() {
        Iterator<Map.Entry<UUID, Integer>> iterator =
                damageCooldowns.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                iterator.remove();
            }
            else {
                entry.setValue(remaining);
            }
        }
    }

    private void endAndClearAction(boolean applyCooldown) {
        endVoidMode(applyCooldown);
        LivingComponentAction.getComponent(this)
                .setAction(null, SyncType.TRACKING_AND_SELF);
    }

    private void playVoidSound(
            net.minecraft.sounds.SoundEvent sound) {
        LivingEntity user = getUser();
        if (user != null) {
            level().playSound(
                    null,
                    user.getX(),
                    user.getY(),
                    user.getZ(),
                    sound,
                    SoundSource.PLAYERS,
                    1.0F,
                    1.0F);
        }
    }

    public enum VoidMode {
        NONE((byte) 0),
        BALL((byte) 1),
        DASH((byte) 2),
        SEMI((byte) 3);

        private final byte id;

        VoidMode(byte id) {
            this.id = id;
        }

        private static VoidMode fromId(byte id) {
            return switch (id) {
                case 1 -> BALL;
                case 2 -> DASH;
                case 3 -> SEMI;
                default -> NONE;
            };
        }
    }
}
