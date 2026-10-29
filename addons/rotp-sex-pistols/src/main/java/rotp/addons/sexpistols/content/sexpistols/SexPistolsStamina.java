package rotp.addons.sexpistols.content.sexpistols;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.sexpistols.SexPistolsIds;
import rotp.addons.sexpistols.registry.SexPistolsStands;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 1.16 SexPistolsStaminaUtil.consumeStamina: without Resolve a short bar is
 * left untouched; every payment pauses stamina regen for 10 ticks.
 */
@EventBusSubscriber(modid = SexPistolsIds.MOD_ID)
public final class SexPistolsStamina {
    public static final int RECOVERY_PAUSE_TICKS = 10;
    // Server thread only; players only, as in 1.16.
    private static final Map<UUID, Integer> RECOVERY_PAUSES =
            new HashMap<>();

    private SexPistolsStamina() {}

    public static boolean tryConsume(
            @Nullable StandPower power,
            @Nullable LivingEntity owner,
            float amount) {
        if (power == null
                || owner == null
                || power.getPowerType()
                        != SexPistolsStands.SEX_PISTOLS.get()) {
            return false;
        }
        if (amount <= 0.0F
                || !power.usesStamina()
                || power.isStaminaInfinite()) {
            return true;
        }
        float stamina = power.getStamina();
        if (stamina < amount) {
            if (!owner.hasEffect(ModStatusEffects.RESOLVE)) {
                // Core consumeStamina would zero the bar here.
                return false;
            }
            if (stamina > 0.0F) {
                power.consumeStamina(stamina);
            }
        }
        else {
            power.consumeStamina(amount);
        }
        pauseRecovery(owner);
        return true;
    }

    // Also for payments made elsewhere (1.16 quick reload paused too).
    public static void pauseRecovery(LivingEntity owner) {
        if (owner instanceof Player
                && !owner.level().isClientSide()) {
            RECOVERY_PAUSES.put(
                    owner.getUUID(), RECOVERY_PAUSE_TICKS);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        UUID id = player.getUUID();
        Integer ticks = RECOVERY_PAUSES.get(id);
        if (ticks == null) {
            return;
        }
        suppressRecovery(player);
        if (ticks <= 1) {
            RECOVERY_PAUSES.remove(id);
        }
        else {
            RECOVERY_PAUSES.put(id, ticks - 1);
        }
    }

    private static void suppressRecovery(Player player) {
        StandPower power = StandPower.get(player);
        if (power == null
                || power.getPowerType()
                        != SexPistolsStands.SEX_PISTOLS.get()
                || !power.usesStamina()
                || power.isStaminaInfinite()) {
            return;
        }
        // Cancels next tick's regen, like 1.16 addStamina(-regen, true).
        float cut = Math.min(
                power.getStaminaTickGain(), power.getStamina());
        if (cut > 0.0F) {
            power.consumeStamina(cut, true);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        RECOVERY_PAUSES.clear();
    }
}
