package rotp.addons.ger.client;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 1.16 DisablerClientState: the entities the server told this client are disabled. */
public final class GerDisablerClientState {
    private static final Set<Integer> DISABLED_ENTITIES =
            ConcurrentHashMap.newKeySet();

    private GerDisablerClientState() {}

    public static boolean isDisabled(int entityId) {
        return DISABLED_ENTITIES.contains(entityId);
    }

    public static void setDisabled(int entityId, boolean disabled) {
        if (disabled) {
            DISABLED_ENTITIES.add(entityId);
        }
        else {
            DISABLED_ENTITIES.remove(entityId);
        }
    }

    public static void clear() {
        DISABLED_ENTITIES.clear();
    }
}
