package rotp.addons.kiss.service;

import java.util.UUID;

import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;

import rotp.addons.kiss.KissAddon;
import rotp.addons.kiss.entity.KissBombEntity;
import rotp.addons.kiss.entity.KissStickerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class KissService {
    public static final double DIRECT_STICKER_RANGE = 8.0D;
    public static final double PULL_SPEED = 0.6D;
    public static final double PULL_STOP_DISTANCE = 1.0D;

    private KissService() {}

    public static boolean validController(
            ServerLevel level, UUID ownerUuid, UUID standUuid) {
        if (ownerUuid == null || standUuid == null) {
            return false;
        }
        Entity ownerEntity = level.getServer().getPlayerList().getPlayer(ownerUuid);
        if (!(ownerEntity instanceof ServerPlayer owner)
                || !owner.isAlive()
                || !KissAddon.isKissUser(owner)) {
            return false;
        }
        StandPower power = StandPower.get(owner);
        StandEntity stand = power != null ? power.getSummonedStandEntity() : null;
        return power != null
                && power.isSummoned()
                && stand != null
                && stand.isAlive()
                && stand.level() == level
                && standUuid.equals(stand.getUUID());
    }

    public static void attachFromAim(
            ServerPlayer owner, StandEntity stand, ActionTarget target) {
        if (!(owner.level() instanceof ServerLevel level)
                || !validController(level, owner.getUUID(), stand.getUUID())
                || target == null) {
            return;
        }
        Entity aimed = target.getMainEntity();
        if (aimed instanceof LivingEntity living
                && living != owner
                && !(living instanceof StandEntity)
                && living.isAlive()
                && owner.distanceToSqr(living)
                        <= DIRECT_STICKER_RANGE * DIRECT_STICKER_RANGE) {
            KissStickerEntity marker = new KissStickerEntity(
                    KissAddon.STICKER_ENTITY.get(), level);
            marker.attachLiving(owner, stand, living, living.getEyePosition());
            level.addFreshEntity(marker);
            play(owner, KissAddon.STICKER_SOUND.get());
            return;
        }

        BlockPos blockPos = target.getBlockPos();
        if (target.getType() == ActionTarget.TargetType.BLOCK
                && blockPos != null
                && !level.getBlockState(blockPos).isAir()) {
            HitResult picked = owner.pick(DIRECT_STICKER_RANGE, 1.0F, false);
            if (!(picked instanceof BlockHitResult blockHit)
                    || picked.getType() != HitResult.Type.BLOCK
                    || !blockPos.equals(blockHit.getBlockPos())) {
                return;
            }
            Direction face = blockHit.getDirection();
            KissStickerEntity marker = new KissStickerEntity(
                    KissAddon.STICKER_ENTITY.get(), level);
            marker.attachBlock(owner, stand, blockPos, face);
            level.addFreshEntity(marker);
            play(owner, KissAddon.STICKER_SOUND.get());
        }
    }

    public static void launchSticker(ServerPlayer owner, StandEntity stand) {
        if (!(owner.level() instanceof ServerLevel level)
                || !validController(level, owner.getUUID(), stand.getUUID())) {
            return;
        }
        KissStickerEntity marker = new KissStickerEntity(
                KissAddon.STICKER_ENTITY.get(), level);
        marker.launch(owner, stand);
        level.addFreshEntity(marker);
        play(owner, KissAddon.STICKER_SOUND.get());
    }

    public static boolean pullLatest(ServerPlayer owner) {
        if (!(owner.level() instanceof ServerLevel level)) {
            return true;
        }
        KissTokenService.Token token = KissTokenService.heldToken(owner);
        if (token == null) {
            return true;
        }
        if (token.isEntity()) {
            Entity target = level.getEntity(token.entityUuid());
            if (!(target instanceof LivingEntity living) || !living.isAlive()) {
                token.consume();
                return true;
            }
            Vec3 destination = owner.position()
                    .add(owner.getLookAngle().normalize().scale(2.0D));
            living.teleportTo(destination.x, destination.y, destination.z);
            living.fallDistance = 0.0F;
            removeMatchingStickers(
                    level, owner.getUUID(), token.entityUuid(), null);
            token.consume();
            play(owner, KissAddon.PULL_SOUND.get());
            return true;
        }

        Vec3 destination = pullPoint(token.blockPos(), token.face());
        Entity pulled = owner.getRootVehicle();
        Vec3 delta = destination.subtract(pulled.position());
        if (delta.lengthSqr() <= PULL_STOP_DISTANCE * PULL_STOP_DISTANCE) {
            pulled.setDeltaMovement(Vec3.ZERO);
            removeMatchingStickers(
                    level, owner.getUUID(), null, token.blockPos());
            token.consume();
            play(owner, KissAddon.PULL_SOUND.get());
            return true;
        }
        double distance = delta.length();
        double step = Math.min(
                PULL_SPEED, distance - PULL_STOP_DISTANCE);
        Vec3 next = pulled.position().add(
                delta.scale(step / distance));
        if (pulled instanceof ServerPlayer player) {
            player.teleportTo(next.x, next.y, next.z);
        }
        else {
            pulled.setPos(next);
            if (pulled.getControllingPassenger() instanceof ServerPlayer controller) {
                controller.connection.send(new ClientboundMoveVehiclePacket(pulled));
            }
        }
        pulled.fallDistance = 0.0F;
        pulled.hasImpulse = true;
        return false;
    }

    public static void launchBomb(ServerPlayer owner, StandEntity stand) {
        if (!(owner.level() instanceof ServerLevel level)
                || !validController(level, owner.getUUID(), stand.getUUID())) {
            return;
        }
        KissTokenService.Token token = KissTokenService.heldToken(owner);
        if (token == null) {
            return;
        }
        KissBombEntity bomb = new KissBombEntity(
                KissAddon.BOMB_ENTITY.get(), level);
        if (!bomb.launch(owner, stand, token)) {
            return;
        }
        level.addFreshEntity(bomb);
        token.consume();
    }

    public static void removeMatchingStickers(
            ServerLevel level,
            UUID ownerUuid,
            UUID targetUuid,
            BlockPos blockPos) {
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof KissStickerEntity marker
                    && marker.isOwnedBy(ownerUuid)
                    && marker.matchesAttachment(targetUuid, blockPos)) {
                marker.discard();
            }
        }
    }

    public static void cleanupOwner(MinecraftServer server, UUID ownerUuid) {
        if (server == null || ownerUuid == null) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof KissStickerEntity marker
                        && marker.isOwnedBy(ownerUuid)
                        || entity instanceof KissBombEntity bomb
                                && bomb.isOwnedBy(ownerUuid)) {
                    entity.discard();
                }
            }
        }
    }

    private static void play(ServerPlayer owner, net.minecraft.sounds.SoundEvent sound) {
        owner.level().playSound(
                null, owner.blockPosition(), sound, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private static Vec3 pullPoint(BlockPos position, Direction face) {
        Vec3 center = Vec3.atCenterOf(position);
        if (face == Direction.UP) {
            return center.add(0.0D, 0.5D, 0.0D);
        }
        return center.add(Vec3.atLowerCornerOf(face.getNormal()).scale(1.0D));
    }
}
