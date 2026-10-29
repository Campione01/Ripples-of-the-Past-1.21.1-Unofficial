package rotp.addons.scarymonsters.state;

import java.util.UUID;

public interface ScaryMonstersEffectIdentityAccess {
    UUID rotp_sm$getEffectIdentity();

    void rotp_sm$setEffectIdentity(UUID identity);
}
