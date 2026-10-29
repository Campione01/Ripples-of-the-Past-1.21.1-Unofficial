package rotp.addons.d4c;

public final class D4CRuntimeRules {
    private D4CRuntimeRules() {}

    public static boolean hasOpposingConstraints(
            boolean east,
            boolean west,
            boolean up,
            boolean down,
            boolean south,
            boolean north,
            boolean northwest,
            boolean southeast,
            boolean northeast,
            boolean southwest) {
        return east && west
                || up && down
                || south && north
                || northwest && southeast
                || northeast && southwest;
    }

    public static boolean shouldBeginAnnihilation(
            boolean boxesIntersect,
            double distance,
            double threshold) {
        return boxesIntersect || distance <= threshold;
    }

    // 1.16 ag/ab.java:424: the clone takes only magic (plus /kill, void)
    public static boolean cloneAcceptsDamage(
            boolean magic, boolean adminKill) {
        return magic || adminKill;
    }

    // 1.16 ag/ab.java:420-423: no clone death in the final phase
    public static boolean cloneHoldsAtOneHealth(
            boolean adminKill,
            int annihilationTicks,
            float health,
            float amount) {
        return !adminKill
                && annihilationTicks >= 100
                && health - amount <= 0.0F;
    }

    // 1.16 ag/ab.java:426-427,439-440: clone death fires the final blow
    public static boolean cloneDeathFiresFinal(
            int annihilationTicks, boolean targetAlive) {
        return annihilationTicks >= 0 && targetAlive;
    }

    // 1.16 ag/ab.java:250,292: only periodic hits need contact
    public static boolean clonePeriodicHitInContact(
            boolean playerClone, boolean colliding) {
        return !playerClone || colliding;
    }

    public static boolean isAnnihilationPair(
            boolean bothNonEmpty,
            boolean sameItem,
            boolean firstParallel,
            boolean secondParallel) {
        return bothNonEmpty
                && sameItem
                && firstParallel != secondParallel;
    }

    public static float sampleNormalizedKeyframes(
            float progress,
            float duration,
            float[] times,
            float[] values) {
        if (times.length == 0
                || times.length != values.length
                || duration <= 0.0F) {
            throw new IllegalArgumentException(
                    "invalid keyframe track");
        }
        float time = progress * duration;
        if (time <= times[0]) {
            return values[0];
        }
        int last = times.length - 1;
        if (time >= times[last]) {
            return values[last];
        }
        for (int index = 0; index < last; index++) {
            if (time <= times[index + 1]) {
                float delta = times[index + 1] - times[index];
                float local = (time - times[index]) / delta;
                return values[index]
                        + (values[index + 1] - values[index]) * local;
            }
        }
        return values[last];
    }

    public static float annihilationDamage(
            float currentHealth,
            float killThreshold,
            float damageFraction) {
        return currentHealth <= killThreshold
                ? Float.MAX_VALUE
                : currentHealth * damageFraction;
    }

    public static boolean deferTargetAdmissionToServer(
            boolean clientSide, boolean playerControlled) {
        return clientSide && playerControlled;
    }

    public static double rapidBaseKnockback(int successfulHit) {
        return successfulHit >= D4CPhase2BSpec.RAPID_HITS
                ? D4CPhase2BSpec.RAPID_FINAL_BASE_KNOCKBACK
                : D4CPhase2BSpec.RAPID_KNOCKBACK;
    }

    public static boolean blockLoveTrainEffect(
            boolean loveTrainActive, boolean harmfulEffect) {
        return loveTrainActive && harmfulEffect;
    }

    public static boolean preserveLoveTrainProjectile(
            boolean ownedByProtectedEntity,
            boolean ownedByProtectedOwner) {
        return ownedByProtectedEntity || ownedByProtectedOwner;
    }

    public static boolean shouldScanInventoryForAnnihilation(
            boolean hasStandPower, boolean isD4CStand) {
        return hasStandPower && !isD4CStand;
    }

    public static boolean isDeathDropProtectionActive(
            long recordedAtMillis, long nowMillis) {
        long elapsed = nowMillis - recordedAtMillis;
        return elapsed >= 0L
                && elapsed
                        <= D4CPhase2BSpec
                                .DEATH_DROP_PROTECTION_MILLIS;
    }

    public static boolean isWithinDeathDropProtection(
            double deathX,
            double deathY,
            double deathZ,
            double itemX,
            double itemY,
            double itemZ) {
        double range = D4CPhase2BSpec.DEATH_DROP_PROTECTION_RANGE;
        return Math.abs(deathX - itemX) <= range
                && Math.abs(deathY - itemY) <= range
                && Math.abs(deathZ - itemZ) <= range;
    }

    public static boolean shouldDiscardFlag(
            int age, int animationDuration) {
        return animationDuration > 0 && age >= animationDuration;
    }

    // 1.16 aa/al: own minions or any annihilation player clone
    public static boolean isSwapCandidate(
            boolean alive, boolean ownMinion, boolean playerClone) {
        return alive && (ownMinion || playerClone);
    }

    // 1.16 aa/al.ab: normalized look dot must exceed 0.8
    public static final double SWAP_CONE_DOT = 0.8D;

    public static boolean inSwapCone(
            double lookX, double lookY, double lookZ,
            double dx, double dy, double dz) {
        double look = Math.sqrt(lookX * lookX + lookY * lookY + lookZ * lookZ);
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (look < 1.0E-4D || dist < 1.0E-4D) {
            return false;
        }
        return (lookX * dx + lookY * dy + lookZ * dz) / (look * dist)
                > SWAP_CONE_DOT;
    }

    public static boolean shouldAnnihilateMatchingBlock(
            boolean parallelItem,
            boolean parallelLevel,
            boolean playerTossed,
            boolean matchingBlock,
            boolean parallelNamedBlock) {
        if (!matchingBlock) {
            return false;
        }
        if (parallelLevel) {
            return !parallelItem && playerTossed;
        }
        return parallelItem && playerTossed && !parallelNamedBlock;
    }
}
