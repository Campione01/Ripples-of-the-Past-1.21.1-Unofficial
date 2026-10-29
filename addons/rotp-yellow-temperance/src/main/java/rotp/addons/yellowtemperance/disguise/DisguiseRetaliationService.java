package rotp.addons.yellowtemperance.disguise;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import rotp.addons.yellowtemperance.YellowTemperancePhase1Spec;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

public final class DisguiseRetaliationService {
    private static final Map<Mob, Map<UUID, Long>> RETALIATION =
            new WeakHashMap<>();

    private DisguiseRetaliationService() {}

    public static synchronized void mark(
            Mob mob, Player player, long gameTime) {
        RETALIATION.computeIfAbsent(
                        mob, ignored -> new HashMap<>())
                .put(
                        player.getUUID(),
                        gameTime
                                + YellowTemperancePhase1Spec
                                        .RETALIATION_TICKS);
    }

    public static synchronized boolean canRetaliate(
            Mob mob, Player player, long gameTime) {
        Map<UUID, Long> entries = RETALIATION.get(mob);
        if (entries == null) {
            return false;
        }
        Long expires = entries.get(player.getUUID());
        if (expires == null || expires < gameTime) {
            entries.remove(player.getUUID());
            if (entries.isEmpty()) {
                RETALIATION.remove(mob);
            }
            return false;
        }
        return true;
    }

    public static synchronized void clearNearby(Player player) {
        for (Mob mob : player.level().getEntitiesOfClass(
                Mob.class,
                player.getBoundingBox().inflate(128.0D))) {
            Map<UUID, Long> entries = RETALIATION.get(mob);
            if (entries != null) {
                entries.remove(player.getUUID());
                if (entries.isEmpty()) {
                    RETALIATION.remove(mob);
                }
            }
        }
    }

    public static synchronized void clear(Player player) {
        UUID id = player.getUUID();
        RETALIATION.values().forEach(entries -> entries.remove(id));
        RETALIATION.entrySet().removeIf(
                entry -> entry.getValue().isEmpty());
    }
}
