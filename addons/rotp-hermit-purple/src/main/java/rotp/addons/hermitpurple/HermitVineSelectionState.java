package rotp.addons.hermitpurple;

import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntFunction;

public final class HermitVineSelectionState {
    private HermitVineSelectionState() {}

    public static <T> T resolveTrackedEntity(
            boolean clientSide,
            int clientEntityId,
            UUID serverEntityUuid,
            IntFunction<T> clientLookup,
            Function<UUID, T> serverLookup) {
        if (clientSide) {
            return clientEntityId >= 0
                    ? clientLookup.apply(clientEntityId)
                    : null;
        }
        return serverEntityUuid != null
                ? serverLookup.apply(serverEntityUuid)
                : null;
    }
}
