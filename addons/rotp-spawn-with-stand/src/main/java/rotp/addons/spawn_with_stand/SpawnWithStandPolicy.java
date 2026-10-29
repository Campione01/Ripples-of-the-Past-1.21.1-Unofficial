package rotp.addons.spawn_with_stand;

import java.util.List;
import java.util.Objects;

public final class SpawnWithStandPolicy {
    private SpawnWithStandPolicy() {}

    public static LifecycleDecision lifecycleDecision(
            Trigger trigger,
            boolean alreadyRolled,
            boolean hasStand) {
        Objects.requireNonNull(trigger, "trigger");
        if (trigger == Trigger.FIRST_LOGIN && !alreadyRolled) {
            return LifecycleDecision.ATTEMPT_FIRST_ROLL;
        }
        if (hasStand) {
            return LifecycleDecision.PRESERVE_EXISTING_STAND;
        }
        if (alreadyRolled) {
            return LifecycleDecision.ALREADY_ROLLED_NO_REASSIGN;
        }
        return LifecycleDecision.NON_LOGIN_NO_REASSIGN;
    }

    public static boolean passesChance(
            double chance, double rollPercent) {
        return chance > 0.0D && rollPercent <= chance;
    }

    public static Selection selection(
            boolean useStandOrder,
            List<? extends String> standOrder,
            int orderedIndex) {
        Objects.requireNonNull(standOrder, "standOrder");
        if (!useStandOrder) {
            return Selection.random(-1);
        }
        if (orderedIndex < 0) {
            throw new IllegalArgumentException(
                    "orderedIndex must be non-negative");
        }
        if (orderedIndex < standOrder.size()) {
            return Selection.configured(
                    orderedIndex,
                    Objects.requireNonNull(
                            standOrder.get(orderedIndex),
                            "configured Stand ID"));
        }
        return Selection.random(orderedIndex);
    }

    public enum Trigger {
        FIRST_LOGIN,
        RESPAWN,
        DIMENSION_REENTRY
    }

    public enum LifecycleDecision {
        ATTEMPT_FIRST_ROLL,
        PRESERVE_EXISTING_STAND,
        ALREADY_ROLLED_NO_REASSIGN,
        NON_LOGIN_NO_REASSIGN
    }

    public enum SelectionKind {
        CONFIGURED,
        RANDOM
    }

    public record Selection(
            SelectionKind kind,
            int orderedIndex,
            String configuredId) {
        public Selection {
            Objects.requireNonNull(kind, "kind");
            if (kind == SelectionKind.CONFIGURED
                    && configuredId == null) {
                throw new IllegalArgumentException(
                        "configured selection requires an ID");
            }
            if (kind == SelectionKind.RANDOM
                    && configuredId != null) {
                throw new IllegalArgumentException(
                        "random selection cannot carry an ID");
            }
        }

        private static Selection configured(
                int orderedIndex, String configuredId) {
            return new Selection(
                    SelectionKind.CONFIGURED,
                    orderedIndex,
                    configuredId);
        }

        private static Selection random(int orderedIndex) {
            return new Selection(
                    SelectionKind.RANDOM,
                    orderedIndex,
                    null);
        }
    }
}
