package rotp.addons.stand_bearing_creatures.mechanics.combo;

import javax.annotation.Nullable;

public record ComboStep(
        String action,
        @Nullable String distance,
        @Nullable Integer requiredStamina) {

    /** The same step with a 1.16 registry-id action ("ns:name") reduced to its moveset name. */
    ComboStep withMovesetName() {
        String name = ComboManager.movesetName(action);
        return name == null || name.equals(action)
                ? this
                : new ComboStep(name, distance, requiredStamina);
    }
}
