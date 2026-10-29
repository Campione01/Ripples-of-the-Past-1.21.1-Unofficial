package rotp.addons.heavensdoor.network;

import java.util.Arrays;
import java.util.Optional;

/**
 * Closed command vocabulary for the Heaven's Door book screen.
 *
 * <p>Every network ID maps to one bounded server operation. Commands which
 * acquire core leases are still authorized through the same server-created
 * command mask.</p>
 */
public enum BookCommand {
    INVALID(-1, 0, Integer.MAX_VALUE, true),
    CLOSE(0, 0, 0, false),
    FLY_BACK(1, 1 << 0, 2, false),
    BLINDNESS_TOGGLE(2, 1 << 1, 3, false),
    FREEZE(3, 1 << 2, 1, false),
    SELF_HARM_TOGGLE(4, 1 << 3, 4, false),
    CANNOT_ATTACK_SELECTED_PLAYER(5, 1 << 4, 3, true),
    MOB_BEHAVIOR_TOGGLE(6, 1 << 5, 4, true);

    private final int networkId;
    private final int permissionBit;
    private final int requiredResolve;
    private final boolean coreLeaseRequired;

    BookCommand(
            int networkId,
            int permissionBit,
            int requiredResolve,
            boolean coreLeaseRequired) {
        this.networkId = networkId;
        this.permissionBit = permissionBit;
        this.requiredResolve = requiredResolve;
        this.coreLeaseRequired = coreLeaseRequired;
    }

    public int networkId() {
        return networkId;
    }

    public int permissionBit() {
        return permissionBit;
    }

    public int requiredResolve() {
        return requiredResolve;
    }

    public boolean coreLeaseRequired() {
        return coreLeaseRequired;
    }

    public boolean healthLimited() {
        return this == CANNOT_ATTACK_SELECTED_PLAYER || this == SELF_HARM_TOGGLE
                || this == FREEZE || this == BLINDNESS_TOGGLE;
    }

    public boolean permittedBy(int mask) {
        return permissionBit != 0
                && (mask & permissionBit) == permissionBit;
    }

    public static Optional<BookCommand> fromNetworkId(int networkId) {
        return Arrays.stream(values())
                .filter(command -> command.networkId == networkId)
                .findFirst();
    }
}
