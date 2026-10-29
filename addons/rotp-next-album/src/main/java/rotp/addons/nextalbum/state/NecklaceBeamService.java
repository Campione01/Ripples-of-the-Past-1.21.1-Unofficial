package rotp.addons.nextalbum.state;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import rotp.core.customobjects.entity_projectile.LightBeamEntity;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.HamonPowerType;
import rotp.core.impl.powers.hamon.ModHamonSkills;

import rotp.addons.nextalbum.NextAlbumAddon;
import rotp.addons.nextalbum.item.AjaStoneNecklaceItem;
import rotp.addons.nextalbum.network.NecklaceChargeStatePayload;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class NecklaceBeamService {
    public static final int CHARGE_TICKS = 50;
    public static final int KEEPALIVE_TIMEOUT_TICKS = 8;
    public static final int COOLDOWN_TICKS = 150;
    public static final float DAMAGE = 40.0F;
    public static final float LENGTH = 16.0F + DAMAGE / 2.0F;
    public static final int MINIMUM_LIGHT = 10;

    private static final Map<UUID, ChargeState> CHARGES =
            new ConcurrentHashMap<>();

    private NecklaceBeamService() {}

    public static boolean keepAlive(ServerPlayer player) {
        UUID id = player.getUUID();
        ChargeState current = CHARGES.get(id);
        if (current != null) {
            // Sneak only starts the charge; the held use keeps it.
            if (!canContinue(player)) {
                endCharge(player);
                return false;
            }
            CHARGES.put(
                    id,
                    new ChargeState(current.elapsedTicks(), 0));
            // A gap means the client let go and came back, which
            // cleared its confirmation; confirm the charge again.
            if (current.keepaliveTimeout() > 1) {
                PacketDistributor.sendToPlayer(
                        player, NecklaceChargeStatePayload.STARTED);
            }
            return true;
        }
        if (!canStart(player)) {
            return false;
        }

        PlayerPower power = PlayerPower.get(player);
        if (power != null && power.hasHeldInput()) {
            return false;
        }
        HamonData hamon = PlayerPower.getPowerData(
                        player, HamonPowerType.HAMON)
                .orElse(null);
        if (hamon == null) {
            return false;
        }
        if (hamon.isSkillLearned(
                ModHamonSkills.AJA_STONE_KEEPER.get())) {
            fire(player);
            return true;
        }
        if (!sufficientLight(player)) {
            return false;
        }

        CHARGES.put(id, new ChargeState(0, 0));
        // The client keeps its use hold only for a confirmed charge.
        PacketDistributor.sendToPlayer(
                player, NecklaceChargeStatePayload.STARTED);
        // Only the charger hears the charge, and it stops with it.
        player.connection.send(new ClientboundSoundEntityPacket(
                ModSoundEvents.AJA_STONE_CHARGING,
                player.getSoundSource(),
                player,
                0.25F,
                1.0F,
                player.getRandom().nextLong()));
        return true;
    }

    public static void tick(ServerPlayer player) {
        ChargeState current = CHARGES.get(player.getUUID());
        if (current == null) {
            return;
        }
        int timeout = current.keepaliveTimeout() + 1;
        if (!canContinue(player)
                || timeout > KEEPALIVE_TIMEOUT_TICKS) {
            endCharge(player);
            return;
        }
        int elapsed = current.elapsedTicks() + 1;
        if (elapsed >= CHARGE_TICKS) {
            endCharge(player);
            if (sufficientLight(player)) {
                fire(player);
            }
            return;
        }
        CHARGES.put(
                player.getUUID(),
                new ChargeState(elapsed, timeout));
    }

    public static void cancel(ServerPlayer player) {
        if (CHARGES.remove(player.getUUID()) != null) {
            PacketDistributor.sendToPlayer(
                    player, NecklaceChargeStatePayload.ENDED);
        }
    }

    private static void endCharge(ServerPlayer player) {
        if (CHARGES.remove(player.getUUID()) != null) {
            player.connection.send(new ClientboundStopSoundPacket(
                    ModSoundEvents.AJA_STONE_CHARGING.getId(),
                    player.getSoundSource()));
            PacketDistributor.sendToPlayer(
                    player, NecklaceChargeStatePayload.ENDED);
        }
    }

    // 1.16 started a charge from a use event, and none fires while an
    // item is in use (eating, a drawn bow, a raised shield).
    private static boolean canStart(ServerPlayer player) {
        return player.isAlive()
                && !player.isUsingItem()
                && player.isShiftKeyDown()
                && AjaStoneNecklaceItem.isEquipped(
                        player,
                        NextAlbumAddon.AJA_STONE_NECKLACE.get())
                && !player.getCooldowns().isOnCooldown(
                        NextAlbumAddon.AJA_STONE_NECKLACE.get());
    }

    private static boolean canContinue(ServerPlayer player) {
        return player.isAlive()
                && AjaStoneNecklaceItem.isEquipped(
                        player,
                        NextAlbumAddon.AJA_STONE_NECKLACE.get());
    }

    private static boolean sufficientLight(ServerPlayer player) {
        return player.level().getMaxLocalRawBrightness(
                        player.blockPosition())
                >= MINIMUM_LIGHT;
    }

    private static void fire(ServerPlayer player) {
        // Null source: the user hears the beam too, not just others.
        player.level().playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                ModSoundEvents.AJA_STONE_BEAM.get(),
                player.getSoundSource(),
                Math.min(0.02F * DAMAGE, 1.0F),
                1.0F
                        + (player.getRandom().nextFloat()
                                - player.getRandom().nextFloat())
                                * 0.1F);
        player.getCooldowns().addCooldown(
                NextAlbumAddon.AJA_STONE_NECKLACE.get(),
                COOLDOWN_TICKS);
        LightBeamEntity beam = new LightBeamEntity(
                ModEntityTypes.AJA_STONE_BEAM.get(),
                player,
                player.level());
        beam.shoot(DAMAGE, LENGTH);
        player.level().addFreshEntity(beam);
    }

    private record ChargeState(
            int elapsedTicks, int keepaliveTimeout) {}
}
