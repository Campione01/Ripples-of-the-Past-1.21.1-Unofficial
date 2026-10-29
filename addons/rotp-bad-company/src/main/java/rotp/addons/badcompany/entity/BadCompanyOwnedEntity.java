package rotp.addons.badcompany.entity;

import java.util.UUID;

public interface BadCompanyOwnedEntity {
    boolean isOwnedBy(UUID ownerUuid);

    boolean isBoundTo(UUID coordinatorUuid);

    BadCompanyStandEntity resolveCoordinator();
}
