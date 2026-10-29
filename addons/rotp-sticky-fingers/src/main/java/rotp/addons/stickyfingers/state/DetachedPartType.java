package rotp.addons.stickyfingers.state;

import java.util.Set;

import javax.annotation.Nullable;

public enum DetachedPartType {
    HEAD,
    LEFT_ARM,
    RIGHT_ARM,
    LEFT_LEG,
    RIGHT_LEG;

    public enum Region {
        HEAD,
        TORSO_ARMS,
        LEGS
    }

    public boolean isArm() {
        return this == LEFT_ARM || this == RIGHT_ARM;
    }

    public boolean isHead() {
        return this == HEAD;
    }

    public boolean isLeg() {
        return this == LEFT_LEG || this == RIGHT_LEG;
    }

    public boolean isRight() {
        return this == RIGHT_ARM || this == RIGHT_LEG;
    }

    public int mask() {
        return 1 << ordinal();
    }

    public static int allPartsMask() {
        return (1 << values().length) - 1;
    }

    @Nullable
    public static DetachedPartType selectAvailable(
            Region region,
            Set<DetachedPartType> detached,
            boolean chooseLeft) {
        return switch (region) {
            case HEAD -> detached.contains(HEAD) ? null : HEAD;
            case TORSO_ARMS -> chooseSide(
                    LEFT_ARM,
                    RIGHT_ARM,
                    detached,
                    chooseLeft);
            case LEGS -> chooseSide(
                    LEFT_LEG,
                    RIGHT_LEG,
                    detached,
                    chooseLeft);
        };
    }

    @Nullable
    public static DetachedPartType fromOrdinal(int ordinal) {
        DetachedPartType[] values = values();
        return ordinal >= 0 && ordinal < values.length
                ? values[ordinal]
                : null;
    }

    @Nullable
    private static DetachedPartType chooseSide(
            DetachedPartType left,
            DetachedPartType right,
            Set<DetachedPartType> detached,
            boolean chooseLeft) {
        boolean leftAvailable = !detached.contains(left);
        boolean rightAvailable = !detached.contains(right);
        if (!leftAvailable && !rightAvailable) {
            return null;
        }
        if (!leftAvailable) {
            return right;
        }
        if (!rightAvailable) {
            return left;
        }
        return chooseLeft ? left : right;
    }
}
