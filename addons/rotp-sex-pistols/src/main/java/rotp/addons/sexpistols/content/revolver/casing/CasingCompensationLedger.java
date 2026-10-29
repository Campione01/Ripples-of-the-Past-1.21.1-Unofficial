package rotp.addons.sexpistols.content.revolver.casing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class CasingCompensationLedger {
    private final Map<UUID, Long> owedByPlayer = new HashMap<>();

    void credit(UUID playerId, long count) {
        if (count <= 0L) {
            return;
        }
        long current = owed(playerId);
        long updated = current > Long.MAX_VALUE - count
                ? Long.MAX_VALUE
                : current + count;
        owedByPlayer.put(playerId, updated);
    }

    long consume(UUID playerId, long requested) {
        if (requested <= 0L) {
            return 0L;
        }
        long current = owed(playerId);
        long consumed = Math.min(current, requested);
        long remaining = current - consumed;
        if (remaining == 0L) {
            owedByPlayer.remove(playerId);
        }
        else {
            owedByPlayer.put(playerId, remaining);
        }
        return consumed;
    }

    long owed(UUID playerId) {
        return owedByPlayer.getOrDefault(playerId, 0L);
    }

    static int insertedCount(
            int offered, int countBefore, int countAfter) {
        if (offered <= 0) {
            return 0;
        }
        long delta = (long) countAfter - countBefore;
        return (int) Math.min(offered, Math.max(0L, delta));
    }

    boolean isEmpty() {
        return owedByPlayer.isEmpty();
    }

    List<Credit> snapshot() {
        List<Credit> credits = new ArrayList<>(owedByPlayer.size());
        owedByPlayer.forEach((playerId, count) ->
                credits.add(new Credit(playerId, count)));
        credits.sort(Comparator.comparing(
                credit -> credit.playerId().toString()));
        return List.copyOf(credits);
    }

    record Credit(UUID playerId, long count) {}
}
