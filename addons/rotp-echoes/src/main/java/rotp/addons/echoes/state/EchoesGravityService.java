package rotp.addons.echoes.state;

import java.util.Comparator;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.HitResultUtil;

import rotp.addons.echoes.RotpEchoes;
import rotp.addons.echoes.ability.EchoesGravityAbility.Mode;
import rotp.addons.echoes.init.EchoesDataComponents;
import rotp.addons.echoes.init.EchoesStands;
import rotp.addons.echoes.state.EchoesUserState.ActiveSkill;
import rotp.addons.echoes.state.EchoesFreezeState.Kind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class EchoesGravityService {
    public static final int ITEM_LIFETIME_TICKS = 100;
    // 1.16 canonical donor (092) GravityFreezeAction MAX_RANGE: pick and hold
    public static final double TARGET_FREEZE_RANGE = 5.0D;
    public static final double HAND_FREEZE_RANGE = 5.0D;
    public static final double INFUSE_BLOCK_RANGE = 10.0D;
    public static final double PROJECTILE_RANGE = 30.0D;
    public static final double PROJECTILE_SPEED = 2.0D;
    public static final float PROJECTILE_DAMAGE = 6.0F;
    public static final int SELF_DRAIN_INTERVAL = 20;
    public static final float SELF_STAMINA_DRAIN = 0.05F;
    // 092 DisablerTagHandler.disable, for the target and the hand freeze:
    // Slowness, Mining Fatigue, Weakness, Jump and Hunger at 255.
    public static final int FREEZE_EFFECT_AMPLIFIER = 255;
    // 1.21 clamps effect levels to 0..255, so the donor's Jump 255 and
    // Freeze Self's Jump -10 would both raise the jump. The entity's jump
    // strength is held at 0 instead, as long as the freeze lasts.
    public static final ResourceLocation FREEZE_JUMP_MODIFIER =
            RotpEchoes.id("gravity_freeze_jump");
    public static final ResourceLocation SELF_FREEZE_JUMP_MODIFIER =
            RotpEchoes.id("gravity_freeze_self_jump");
    public static final ResourceLocation CARRIER_JUMP_MODIFIER =
            RotpEchoes.id("gravity_item_jump");
    public static final ResourceLocation CARRIER_FALL_MODIFIER =
            RotpEchoes.id("gravity_item_fall");
    // 1.16 LivingEntity.jumpFromGround: 0.1 per Jump level
    private static final double CARRIER_JUMP_PER_LEVEL = 0.1D;
    private static final double FROZEN_SINK_SPEED = -0.5D;
    // 092 HandFreezeHandler: Slowness, Mining Fatigue and Weakness come off
    // once 20 of its 600 ticks are left.
    private static final long HAND_FREEZE_EASING_TICKS = 20L;

    private EchoesGravityService() {}

    public static void perform(ServerPlayer player, Mode mode) {
        switch (mode) {
            case PROJECTILE -> launchProjectile(player);
            case FREEZE_TARGET -> toggleTargetFreeze(player);
            case HAND_FREEZE -> toggleHandFreeze(player);
            case FREEZE_SELF -> toggleSelfFreeze(player);
            case INFUSE -> toggleInfuse(player);
        }
    }

    public static boolean hasInfusedItem(
            ServerPlayer player, long gameTime) {
        for (int slot = 0;
                slot < player.getInventory().getContainerSize();
                slot++) {
            if (isInfused(
                    player.getInventory().getItem(slot), gameTime, true)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isInfused(
            ItemStack stack, long gameTime, boolean removeExpired) {
        if (stack.isEmpty()) {
            return false;
        }
        Long expiresAt = stack.get(
                EchoesDataComponents.GRAVITY_INFUSED_UNTIL);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt > gameTime) {
            return true;
        }
        if (removeExpired) {
            stack.remove(EchoesDataComponents.GRAVITY_INFUSED_UNTIL);
        }
        return false;
    }

    public static void infuse(ItemStack stack, long gameTime) {
        if (!stack.isEmpty()) {
            stack.set(
                    EchoesDataComponents.GRAVITY_INFUSED_UNTIL,
                    gameTime + ITEM_LIFETIME_TICKS);
        }
    }

    public static void clearInfusion(ItemStack stack) {
        if (!stack.isEmpty()) {
            stack.remove(EchoesDataComponents.GRAVITY_INFUSED_UNTIL);
        }
    }

    public static int tickInventoryGravity(ServerPlayer player) {
        int count = countInfused(player, true);
        if (count <= 0) {
            releaseJump(player, CARRIER_JUMP_MODIFIER);
            releaseModifier(
                    player, Attributes.SAFE_FALL_DISTANCE, CARRIER_FALL_MODIFIER);
            return 0;
        }
        int amplifier = Math.min(count, 5);
        player.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN,
                10,
                amplifier,
                false,
                false,
                false));
        // 092 GravityItemEvents: Jump -amplifier, which 1.16 added to the
        // jump as 0.1 * (1 - amplifier) and to the safe fall distance as
        // 1 - amplifier (calculateFallDamage). 1.21 clamps a negative level
        // to Jump Boost I, so the two attributes carry it instead.
        setCarrierModifier(
                player,
                Attributes.JUMP_STRENGTH,
                CARRIER_JUMP_MODIFIER,
                CARRIER_JUMP_PER_LEVEL * (1 - amplifier));
        setCarrierModifier(
                player,
                Attributes.SAFE_FALL_DISTANCE,
                CARRIER_FALL_MODIFIER,
                1 - amplifier);
        applyCarrierMotion(player, count);
        // 092 GravityFieldHandler: creative carriers have no field
        if (!player.isCreative() && !player.isSpectator()) {
            pinNearbyEntities(player);
        }
        // 092 GravityItemEvents: pressure has no creative check
        pressNearbyFrozen(player);
        return count;
    }

    // 092 carrier drag ran on both sides; the client owns player motion
    public static void tickLocalCarrier(Player player) {
        int count = countInfused(player, false);
        if (count > 0) {
            applyCarrierMotion(player, count);
        }
    }

    private static int countInfused(Player player, boolean removeExpired) {
        long gameTime = player.level().getGameTime();
        int count = 0;
        for (int slot = 0;
                slot < player.getInventory().getContainerSize();
                slot++) {
            if (isInfused(
                    player.getInventory().getItem(slot),
                    gameTime,
                    removeExpired)) {
                count++;
            }
        }
        return count;
    }

    // 092 GravityItemEvents: drag, and a carrier sinks in water
    private static void applyCarrierMotion(Player player, int count) {
        Vec3 movement = player.getDeltaMovement();
        boolean inWater = player.isInWater();
        double[] next = EchoesGravityField.carrierMotion(
                movement.x, movement.y, movement.z, count, inWater);
        player.setDeltaMovement(next[0], next[1], next[2]);
        if (inWater) {
            player.setSwimming(false);
            player.xxa = 0.0F;
            player.zza = 0.0F;
        }
    }

    // 092 GravityFieldHandler: living entities near a carrier are pinned
    private static void pinNearbyEntities(ServerPlayer player) {
        for (LivingEntity entity : player.level().getEntitiesOfClass(
                LivingEntity.class,
                player.getBoundingBox().inflate(EchoesGravityField.FIELD_RANGE),
                other -> other != player && other.isAlive())) {
            if (!EchoesGravityField.inField(entity.distanceTo(player))
                    || isFreezeDisabled(entity)) {
                continue;
            }
            Vec3 movement = entity.getDeltaMovement();
            double[] next = EchoesGravityField.pinnedMotion(
                    movement.x, movement.y, movement.z);
            entity.setDeltaMovement(next[0], next[1], next[2]);
        }
    }

    // 092 DisablerTag counterpart: Echoes-frozen entities are skipped
    private static boolean isFreezeDisabled(LivingEntity entity) {
        return entity.hasData(
                        rotp.addons.echoes.init
                                .EchoesAttachments.FREEZE_STATE)
                && RotpEchoes.freezeState(entity).active();
    }

    // 092 GravityItemEvents: with another Echoes user near the carrier,
    // the block under each frozen entity breaks every 20 ticks
    private static void pressNearbyFrozen(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (!EchoesGravityField.pressureTick(level.getGameTime())
                || level.getEntitiesOfClass(
                        Player.class,
                        player.getBoundingBox().inflate(
                                EchoesGravityField.PRESSURE_RANGE),
                        other -> other != player && other.isAlive()
                                && hasEchoesStand(other)).isEmpty()) {
            return;
        }
        for (LivingEntity entity : level.getEntitiesOfClass(
                LivingEntity.class,
                player.getBoundingBox().inflate(
                        EchoesGravityField.PRESSURE_RANGE),
                other -> other != player && other.isAlive()
                        && isFreezeDisabled(other))) {
            if (!EchoesGravityField.inPressure(entity.distanceTo(player))) {
                continue;
            }
            BlockPos below = entity.blockPosition().below();
            BlockState state = level.getBlockState(below);
            if (EchoesGravityField.pressureBreaks(
                    state.isAir(), state.is(Blocks.BEDROCK))) {
                level.destroyBlock(below, true);
            }
        }
    }

    // 092 hasEchoesStand: any act, summoned or not
    private static boolean hasEchoesStand(Player player) {
        StandPower power = StandPower.get(player);
        if (power == null) {
            return false;
        }
        StandType type = power.getPowerType();
        return type != null
                && (type == EchoesStands.ECHOES_ACT1.get()
                        || type == EchoesStands.ECHOES_ACT2.get()
                        || type == EchoesStands.ECHOES_ACT3.get());
    }

    public static void tickInfusedItem(ItemEntity item) {
        long gameTime = item.level().getGameTime();
        if (!isInfused(item.getItem(), gameTime, true)) {
            return;
        }
        if (item.isInWater()) {
            Vec3 movement = item.getDeltaMovement();
            item.setDeltaMovement(
                    movement.x,
                    Math.min(movement.y, -1.0D),
                    movement.z);
        }
        item.setPickUpDelay(40);
    }

    public static void tickFrozenTarget(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel level)
                || !target.hasData(
                        rotp.addons.echoes.init
                                .EchoesAttachments.FREEZE_STATE)) {
            return;
        }
        EchoesFreezeState state = RotpEchoes.freezeState(target);
        if (!state.active()) {
            return;
        }
        if (EchoesFreezeTiming.expired(state.expiresAt(), level.getGameTime())) {
            releaseFrozenTarget(target);
            return;
        }
        ServerPlayer owner = level.getServer()
                .getPlayerList()
                .getPlayer(state.owner());
        StandPower power = owner != null ? StandPower.get(owner) : null;
        StandEntity stand = power != null
                ? power.getSummonedStandEntity()
                : null;
        if (owner == null
                || owner.level() != level
                || power == null
                || !RotpEchoes.isEchoesUser(owner)
                || !power.isSummoned()
                || stand == null
                || !stand.isAlive()
                || stand.distanceTo(target) > freezeRange(state.kind())) {
            releaseFrozenTarget(target);
            return;
        }

        state.initializeLegacyDeadline(level.getGameTime());
        // 092 DisablerTagHandler.updateFreeze sets a frozen player's or mob's
        // motion to 0, -0.5, 0 in water just before it moves, so it sinks;
        // it is held where it ends up.
        if (target.isInWater()
                && (target instanceof Player || target instanceof Mob)) {
            target.setDeltaMovement(0.0D, FROZEN_SINK_SPEED, 0.0D);
            state.moveAnchor(target.position());
        } else {
            target.setDeltaMovement(Vec3.ZERO);
            target.setPos(
                    state.anchor().x,
                    state.anchor().y,
                    state.anchor().z);
        }
        target.setNoGravity(true);
        target.fallDistance = 0.0F;
        if (target instanceof Mob mob) {
            mob.getNavigation().stop();
        }
        restrainFrozen(target, state.kind());
        // Every tick: the transient hold is not saved, and the last 20 ticks
        // of a hand freeze skip the effect refresh below.
        holdJump(target, FREEZE_JUMP_MODIFIER);
        if (state.kind() == Kind.HAND && target instanceof Player player) {
            // 092 HandFreezeHandler.tickFreeze
            player.swing(InteractionHand.MAIN_HAND, false);
            player.swing(InteractionHand.OFF_HAND, false);
        }
        if (state.kind() == Kind.HAND
                && state.expiresAt() - level.getGameTime()
                        <= HAND_FREEZE_EASING_TICKS) {
            // 092 HandFreezeHandler.tickFreeze, in the last 20 ticks
            target.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            target.removeEffect(MobEffects.DIG_SLOWDOWN);
            target.removeEffect(MobEffects.WEAKNESS);
        } else if (target.tickCount % 20 == 0) {
            applyTargetEffects(target, 40);
        }
    }

    public static void tickUser(ServerPlayer player) {
        EchoesUserState state = RotpEchoes.userState(player);
        if (state.activeSkill() == ActiveSkill.NONE) {
            return;
        }
        StandPower power = StandPower.get(player);
        if (!RotpEchoes.isEchoesUser(player)
                || power == null) {
            cleanupUser(player, true);
            return;
        }
        if (!power.isSummoned()) {
            cleanupManifestationState(player);
            return;
        }
        if (state.activeSkill() == ActiveSkill.FREEZE_SELF) {
            if (player.tickCount % SELF_DRAIN_INTERVAL == 0
                    && !power.isStaminaInfinite()) {
                float next = power.getStamina() - SELF_STAMINA_DRAIN;
                if (next <= 0.0F) {
                    state.clearActiveSkill();
                    clearSelfEffects(player);
                    return;
                }
                power.setStamina(next);
            }
            applySelfEffects(player);
        } else if ((state.activeSkill() == ActiveSkill.FREEZE_TARGET
                        || state.activeSkill() == ActiveSkill.HAND_FREEZE)
                && findEntity(
                                player.getServer(),
                                state.frozenTarget())
                        == null) {
            state.clearActiveSkill();
        }
    }

    private static void cleanupManifestationState(
            ServerPlayer player) {
        EchoesUserState state = RotpEchoes.userState(player);
        if (state.activeSkill() == ActiveSkill.FREEZE_TARGET
                || state.activeSkill() == ActiveSkill.HAND_FREEZE) {
            releaseTargetOwnedBy(player, state.frozenTarget());
        }
        else if (state.activeSkill() == ActiveSkill.FREEZE_SELF) {
            clearSelfEffects(player);
        }
        if (state.activeSkill() != ActiveSkill.GRAVITY_INFUSE) {
            state.clearActiveSkill();
        }
    }

    public static void cleanupUser(
            ServerPlayer player, boolean removeInfusedItems) {
        EchoesUserState state = RotpEchoes.userState(player);
        if (state.activeSkill() == ActiveSkill.FREEZE_TARGET
                || state.activeSkill() == ActiveSkill.HAND_FREEZE) {
            releaseTargetOwnedBy(player, state.frozenTarget());
        }
        if (state.activeSkill() == ActiveSkill.FREEZE_SELF) {
            clearSelfEffects(player);
        }
        if (state.activeSkill() == ActiveSkill.GRAVITY_INFUSE) {
            removeOwnedMarks(player.getServer(), player.getUUID());
        }
        state.clearActiveSkill();
        if (removeInfusedItems) {
            clearInfusedInventory(player);
        }
    }

    public static void releaseFrozenTarget(LivingEntity target) {
        EchoesFreezeState freezeState = RotpEchoes.freezeState(target);
        if (!freezeState.active()) {
            return;
        }
        UUID ownerId = freezeState.owner();
        freezeState.clear(target);
        clearTargetEffects(target);
        if (ownerId == null
                || !(target.level() instanceof ServerLevel level)) {
            return;
        }
        ServerPlayer owner = level.getServer()
                .getPlayerList()
                .getPlayer(ownerId);
        if (owner != null) {
            EchoesUserState userState = RotpEchoes.userState(owner);
            if (target.getUUID().equals(userState.frozenTarget())) {
                userState.clearActiveSkill();
            }
        }
    }

    public static boolean isHandFrozen(LivingEntity target) {
        return target.hasData(
                        rotp.addons.echoes.init
                                .EchoesAttachments.FREEZE_STATE)
                && RotpEchoes.freezeState(target).active()
                && RotpEchoes.freezeState(target).kind() == Kind.HAND;
    }

    public static void removeOwnedMarks(
            MinecraftServer server, UUID owner) {
        for (ServerLevel level : server.getAllLevels()) {
            GravityMarkSavedData.get(level).removeOwner(owner);
        }
    }

    private static void launchProjectile(ServerPlayer player) {
        long gameTime = player.level().getGameTime();
        ItemStack source = takeOneInfusedItem(player, gameTime);
        if (source.isEmpty()) {
            return;
        }
        StandPower power = StandPower.get(player);
        // 092 GravityProjectileAction.standPerform paid its cost itself once
        // it found an item, and StandEntityTask paid it again after the
        // perform: 200 in all, both results ignored.
        if (power != null) {
            power.consumeStamina(Mode.PROJECTILE.staminaCost());
        }
        clearInfusion(source);
        StandEntity performer = power != null
                ? power.getSummonedStandEntity()
                : null;
        ActionTarget aimed = HitResultUtil.clip(
                player.getEyePosition(),
                player.getLookAngle(),
                PROJECTILE_RANGE,
                PROJECTILE_RANGE,
                player.level(),
                entity -> entity.isAlive()
                        && entity != player
                        && entity != performer
                        && entity instanceof LivingEntity,
                player,
                0.0D);
        Entity target = aimed.getMainEntity();

        ItemEntity projectile = new ItemEntity(
                player.level(),
                player.getX(),
                player.getEyeY() - 0.1D,
                player.getZ(),
                source);
        projectile.setDeltaMovement(
                player.getLookAngle().scale(PROJECTILE_SPEED));
        projectile.setPickUpDelay(20);
        player.level().addFreshEntity(projectile);

        if (target instanceof LivingEntity living) {
            living.hurt(
                    player.damageSources().playerAttack(player),
                    PROJECTILE_DAMAGE);
            Vec3 impulse = player.getLookAngle().scale(0.5D);
            living.setDeltaMovement(
                    living.getDeltaMovement().add(impulse));
        }
    }

    private static void toggleTargetFreeze(ServerPlayer player) {
        EchoesUserState state = RotpEchoes.userState(player);
        if (state.activeSkill() == ActiveSkill.FREEZE_TARGET) {
            releaseTargetOwnedBy(player, state.frozenTarget());
            state.clearActiveSkill();
            return;
        }
        StandPower power = StandPower.get(player);
        StandEntity performer = power != null
                ? power.getSummonedStandEntity()
                : null;
        ActionTarget aimed = HitResultUtil.clip(
                player.getEyePosition(),
                player.getLookAngle(),
                TARGET_FREEZE_RANGE,
                TARGET_FREEZE_RANGE,
                player.level(),
                entity -> entity.isAlive()
                        && entity != player
                        && entity != performer
                        && entity instanceof LivingEntity,
                player,
                0.0D);
        if (!(aimed.getMainEntity() instanceof LivingEntity target)
                || !standInFreezeRange(player, target, Kind.TARGET)) {
            return;
        }
        // 092: skill switch only after the target and Stand-range checks
        clearConflictingSkill(player);
        EchoesFreezeState targetState = RotpEchoes.freezeState(target);
        if (targetState.active()) {
            releaseFrozenTarget(target);
        }
        int amplifier = distanceAmplifier(player.distanceTo(target));
        targetState.activate(
                target,
                player.getUUID(),
                amplifier,
                Kind.TARGET,
                EchoesFreezeTiming.deadlineAfter(player.level().getGameTime()));
        state.activateTargetFreeze(target.getUUID());
        applyTargetEffects(
                target, (int) EchoesFreezeTiming.DURATION_TICKS);
        restrainFrozen(target, Kind.TARGET);
        EchoesChatService.playSkillSound(player);
    }

    private static void toggleHandFreeze(ServerPlayer player) {
        EchoesUserState state = RotpEchoes.userState(player);
        if (state.activeSkill() == ActiveSkill.HAND_FREEZE) {
            releaseTargetOwnedBy(player, state.frozenTarget());
            state.clearActiveSkill();
            return;
        }
        StandPower power = StandPower.get(player);
        StandEntity performer = power != null
                ? power.getSummonedStandEntity()
                : null;
        ActionTarget aimed = HitResultUtil.clip(
                player.getEyePosition(),
                player.getLookAngle(),
                HAND_FREEZE_RANGE,
                HAND_FREEZE_RANGE,
                player.level(),
                entity -> entity.isAlive()
                        && entity != player
                        && entity != performer
                        && entity instanceof LivingEntity,
                player,
                0.0D);
        if (!(aimed.getMainEntity() instanceof LivingEntity target)
                || !standInFreezeRange(player, target, Kind.HAND)) {
            return;
        }
        // 092: skill switch only after the target and Stand-range checks
        clearConflictingSkill(player);
        EchoesFreezeState targetState = RotpEchoes.freezeState(target);
        if (targetState.active()) {
            releaseFrozenTarget(target);
        }
        int amplifier = distanceAmplifier(player.distanceTo(target));
        targetState.activate(
                target,
                player.getUUID(),
                amplifier,
                Kind.HAND,
                EchoesFreezeTiming.deadlineAfter(player.level().getGameTime()));
        state.activateHandFreeze(target.getUUID());
        applyTargetEffects(
                target, (int) EchoesFreezeTiming.DURATION_TICKS);
        restrainFrozen(target, Kind.HAND);
        EchoesChatService.playSkillSound(player);
        player.level().playSound(
                null,
                player.blockPosition(),
                rotp.addons.echoes.init.EchoesSounds
                        .SKILL_BGM.get(),
                SoundSource.PLAYERS,
                1.0F,
                1.0F);
    }

    private static void toggleSelfFreeze(ServerPlayer player) {
        EchoesUserState state = RotpEchoes.userState(player);
        if (state.activeSkill() == ActiveSkill.FREEZE_SELF) {
            state.clearActiveSkill();
            clearSelfEffects(player);
            return;
        }
        clearConflictingSkill(player);
        state.activateSelfFreeze();
        applySelfEffects(player);
        EchoesChatService.playSkillSound(player);
    }

    private static void toggleInfuse(ServerPlayer player) {
        EchoesUserState state = RotpEchoes.userState(player);
        if (state.activeSkill() != ActiveSkill.GRAVITY_INFUSE) {
            clearConflictingSkill(player);
            state.activateGravityInfuse();
        }
        ActionTarget aimed = HitResultUtil.clip(
                player.getEyePosition(),
                player.getLookAngle(),
                INFUSE_BLOCK_RANGE,
                INFUSE_BLOCK_RANGE,
                player.level(),
                entity -> false,
                player,
                0.0D,
                // 092 GravityInfuseAction.getTargetBlock: an OUTLINE ray, so grass, a flower or a torch is the block
                ClipContext.Block.OUTLINE);
        BlockPos position = aimed.getBlockPos();
        if (position != null
                && player.level().getBlockState(position)
                        .getBlock()
                        .asItem()
                        != net.minecraft.world.item.Items.AIR) {
            toggleBlockInfusion(player, position);
            return;
        }
        toggleItemInfusion(player);
    }

    private static void toggleBlockInfusion(
            ServerPlayer player, BlockPos position) {
        GravityMarkSavedData data =
                GravityMarkSavedData.get(player.serverLevel());
        GravityMarkSavedData.Mark existing =
                data.mark(position, player.level().getGameTime());
        if (existing != null
                && existing.owner().equals(player.getUUID())) {
            data.remove(position);
            RotpEchoes.userState(player).clearActiveSkill();
            return;
        }
        removeOwnedMarks(player.getServer(), player.getUUID());
        data.mark(
                position,
                player.getUUID(),
                player.level().getGameTime()
                        + GravityMarkSavedData.MARK_LIFETIME_TICKS);
        EchoesChatService.playSkillSound(player);
    }

    private static void toggleItemInfusion(ServerPlayer player) {
        long gameTime = player.level().getGameTime();
        if (hasInfusedItem(player, gameTime)) {
            clearInfusedInventory(player);
            removeOwnedMarks(player.getServer(), player.getUUID());
            RotpEchoes.userState(player).clearActiveSkill();
            return;
        }
        ItemStack single = extractInfusionSource(player);
        if (single.isEmpty()) {
            RotpEchoes.userState(player).clearActiveSkill();
            return;
        }
        infuse(single, gameTime);
        if (!player.getInventory().add(single)) {
            player.drop(single, false);
        }
        removeOwnedMarks(player.getServer(), player.getUUID());
        RotpEchoes.userState(player).clearActiveSkill();
        EchoesChatService.playSkillSound(player);
    }

    // 092 FinisherAction: an ACT3 finisher hit ends self-freeze or infuse
    public static void onAct3FinisherHit(ServerPlayer player) {
        EchoesUserState state = RotpEchoes.userState(player);
        ActiveSkill ended = state.activeSkill();
        if (ended == ActiveSkill.FREEZE_SELF) {
            clearSelfEffects(player); // slowness, held jump, weakness
        } else if (ended == ActiveSkill.GRAVITY_INFUSE) {
            clearInfusedInventory(player); // un-infuse held items
            removeOwnedMarks(player.getServer(), player.getUUID()); // block marks
        } else {
            // target and hand freezes stay on, as in 092
            return;
        }
        state.clearActiveSkill();
    }

    private static void clearConflictingSkill(ServerPlayer player) {
        EchoesUserState state = RotpEchoes.userState(player);
        if (state.activeSkill() == ActiveSkill.FREEZE_TARGET
                || state.activeSkill() == ActiveSkill.HAND_FREEZE) {
            releaseTargetOwnedBy(player, state.frozenTarget());
        } else if (state.activeSkill() == ActiveSkill.FREEZE_SELF) {
            clearSelfEffects(player);
        } else if (state.activeSkill() == ActiveSkill.GRAVITY_INFUSE) {
            clearInfusedInventory(player);
            removeOwnedMarks(player.getServer(), player.getUUID());
        }
        state.clearActiveSkill();
    }

    private static void releaseTargetOwnedBy(
            ServerPlayer player, @Nullable UUID targetId) {
        Entity target = findEntity(player.getServer(), targetId);
        if (target instanceof LivingEntity living
                && RotpEchoes.freezeState(living).active()
                && player.getUUID().equals(
                        RotpEchoes.freezeState(living).owner())) {
            releaseFrozenTarget(living);
        }
    }

    private static double freezeRange(Kind kind) {
        return kind == Kind.HAND ? HAND_FREEZE_RANGE : TARGET_FREEZE_RANGE;
    }

    // 092 start gate: summoned Stand within range of the target
    private static boolean standInFreezeRange(
            ServerPlayer player, LivingEntity target, Kind kind) {
        StandPower power = StandPower.get(player);
        StandEntity stand = power != null
                ? power.getSummonedStandEntity()
                : null;
        return stand != null
                && stand.isAlive()
                && stand.distanceTo(target) <= freezeRange(kind);
    }

    @Nullable
    private static Entity findEntity(
            MinecraftServer server, @Nullable UUID entityId) {
        if (entityId == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(entityId);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    private static ItemStack takeOneInfusedItem(
            ServerPlayer player, long gameTime) {
        for (int slot = 0;
                slot < player.getInventory().getContainerSize();
                slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!isInfused(stack, gameTime, true)) {
                continue;
            }
            ItemStack single = stack.copyWithCount(1);
            stack.shrink(1);
            return single;
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack extractInfusionSource(ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.isEmpty()) {
            ItemStack single = mainHand.copyWithCount(1);
            mainHand.shrink(1);
            return single;
        }
        ItemStack offHand = player.getOffhandItem();
        if (!offHand.isEmpty()) {
            ItemStack single = offHand.copyWithCount(1);
            offHand.shrink(1);
            return single;
        }
        ItemEntity nearest = player.serverLevel()
                .getEntitiesOfClass(
                        ItemEntity.class,
                        player.getBoundingBox().inflate(5.0D),
                        item -> item.isAlive() && !item.getItem().isEmpty())
                .stream()
                .min(Comparator.comparingDouble(
                        player::distanceToSqr))
                .orElse(null);
        if (nearest == null) {
            return ItemStack.EMPTY;
        }
        ItemStack single = nearest.getItem().copyWithCount(1);
        nearest.getItem().shrink(1);
        if (nearest.getItem().isEmpty()) {
            nearest.discard();
        }
        return single;
    }

    private static void clearInfusedInventory(ServerPlayer player) {
        for (int slot = 0;
                slot < player.getInventory().getContainerSize();
                slot++) {
            clearInfusion(player.getInventory().getItem(slot));
        }
    }

    // 255 stops walking (Slowness), mining (Mining Fatigue) and melee damage
    // (Weakness), and Hunger drains the food bar; there is no Resistance, so
    // the frozen target can still be hurt.
    private static void applyTargetEffects(
            LivingEntity target, int duration) {
        target.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN,
                duration,
                FREEZE_EFFECT_AMPLIFIER,
                false,
                false,
                false));
        target.addEffect(new MobEffectInstance(
                MobEffects.DIG_SLOWDOWN,
                duration,
                FREEZE_EFFECT_AMPLIFIER,
                false,
                false,
                false));
        target.addEffect(new MobEffectInstance(
                MobEffects.WEAKNESS,
                duration,
                FREEZE_EFFECT_AMPLIFIER,
                false,
                false,
                false));
        target.addEffect(new MobEffectInstance(
                MobEffects.HUNGER,
                duration,
                FREEZE_EFFECT_AMPLIFIER,
                false,
                false,
                false));
        holdJump(target, FREEZE_JUMP_MODIFIER);
    }

    // 092 DisablerTagHandler (both freezes) and HandFreezeHandler (the hand
    // freeze), at the start and on every tick: a frozen player has no
    // movement input, cannot fly, stops using its item and restarts its
    // attack charge; a hand-frozen mob loses its target. Release gives the
    // player back the flight it had (EchoesFreezeState.clear). 092 cancels
    // the use (stopUsingItem), so a drawn bow or a charged trident is not
    // let go.
    private static void restrainFrozen(LivingEntity target, Kind kind) {
        if (target instanceof Player player) {
            player.xxa = 0.0F;
            player.zza = 0.0F;
            player.setSpeed(0.0F);
            player.setSwimming(false);
            Abilities abilities = player.getAbilities();
            if (abilities.flying || abilities.mayfly) {
                abilities.flying = false;
                abilities.mayfly = false;
                player.onUpdateAbilities();
            }
            if (player.isUsingItem()) {
                player.stopUsingItem();
            }
            player.resetAttackStrengthTicker();
        }
        if (kind == Kind.HAND && target instanceof Mob mob) {
            mob.setTarget(null);
        }
    }

    private static void applySelfEffects(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN,
                40,
                10,
                false,
                false,
                false));
        // 092 GravityFreezeSelfAction: Jump -10, which kept 1.16 grounded
        holdJump(player, SELF_FREEZE_JUMP_MODIFIER);
        // 092 GravityFreezeSelfAction: Weakness III (field_76437_t)
        player.addEffect(new MobEffectInstance(
                MobEffects.WEAKNESS,
                40,
                2,
                false,
                false,
                false));
    }

    private static void clearTargetEffects(LivingEntity target) {
        target.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        target.removeEffect(MobEffects.DIG_SLOWDOWN);
        target.removeEffect(MobEffects.WEAKNESS);
        target.removeEffect(MobEffects.HUNGER);
        releaseJump(target, FREEZE_JUMP_MODIFIER);
    }

    private static void clearSelfEffects(ServerPlayer player) {
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        player.removeEffect(MobEffects.WEAKNESS);
        releaseJump(player, SELF_FREEZE_JUMP_MODIFIER);
    }

    private static void holdJump(
            LivingEntity entity, ResourceLocation modifier) {
        AttributeInstance jump =
                entity.getAttribute(Attributes.JUMP_STRENGTH);
        AttributeModifier current =
                jump != null ? jump.getModifier(modifier) : null;
        // An unchanged hold is not replaced, so it is not sent again.
        if (jump != null && (current == null
                || current.amount() != -1.0D
                || current.operation()
                        != AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)) {
            jump.addOrUpdateTransientModifier(new AttributeModifier(
                    modifier,
                    -1.0D,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void releaseJump(
            LivingEntity entity, ResourceLocation modifier) {
        releaseModifier(entity, Attributes.JUMP_STRENGTH, modifier);
    }

    private static void releaseModifier(
            LivingEntity entity,
            Holder<Attribute> attribute,
            ResourceLocation modifier) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(modifier);
        }
    }

    // Both attributes are synced: a modifier added again marks them for a
    // packet even when its amount is the same, so it is only replaced when
    // the amount changes.
    private static void setCarrierModifier(
            LivingEntity entity,
            Holder<Attribute> attribute,
            ResourceLocation modifier,
            double amount) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier current = instance.getModifier(modifier);
        if (current == null || current.amount() != amount) {
            instance.addOrUpdateTransientModifier(new AttributeModifier(
                    modifier,
                    amount,
                    AttributeModifier.Operation.ADD_VALUE));
        }
    }

    private static int distanceAmplifier(double distance) {
        return distance <= 3.0D ? 10 : distance <= 6.0D ? 8 : 6;
    }
}
