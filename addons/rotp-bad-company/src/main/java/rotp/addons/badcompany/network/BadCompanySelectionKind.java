package rotp.addons.badcompany.network;

import java.util.Optional;

public enum BadCompanySelectionKind {
    UNIT(0),
    FORMATION(1);

    private final int networkId;

    BadCompanySelectionKind(int networkId) {
        this.networkId = networkId;
    }

    public int networkId() {
        return networkId;
    }

    public static Optional<BadCompanySelectionKind>
            fromNetworkId(int networkId) {
        for (BadCompanySelectionKind kind : values()) {
            if (kind.networkId == networkId) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
