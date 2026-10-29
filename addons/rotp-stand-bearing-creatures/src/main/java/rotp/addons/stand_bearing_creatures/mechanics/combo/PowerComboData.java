package rotp.addons.stand_bearing_creatures.mechanics.combo;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

public record PowerComboData(
        Map<Integer, List<String>> combosByResolve,
        @Nullable String blockAction,
        @Nullable MaintenanceStep maintenance,
        Map<String, List<ComboStep>> combos) {

    public List<String> availableCombos(int resolveLevel) {
        if (!combosByResolve.isEmpty()) {
            return combosByResolve.getOrDefault(resolveLevel, List.of());
        }
        return combos.keySet().stream().sorted().toList();
    }

    public List<ComboStep> combo(String name) {
        return combos.get(name);
    }
}
