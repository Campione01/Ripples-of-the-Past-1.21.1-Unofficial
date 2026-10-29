package rotp.addons.sexpistols.content.revolver.casing;

public final class CasingFlightState {
    public static final int TIME_STOP_SLOWDOWN_TICKS = 3;
    public static final double PARTICLE_WIDTH = 0.2D;
    public static final double PARTICLE_HEIGHT = 0.2D;
    private static final double GRAVITY = 0.58D;
    private static final double PARTICLE_FRICTION = 0.98D;
    private static final double AIR_HORIZONTAL_FRICTION = 0.985D;
    private static final double GROUND_HORIZONTAL_FRICTION = 0.42D;
    private static final double GROUND_PARTICLE_FRICTION = 0.7D;
    private static final double RESTORE_MOTION_THRESHOLD_SQR = 1.0E-7D;

    private final CasingEjectionSpec spec;
    private double previousX;
    private double previousY;
    private double previousZ;
    private double x;
    private double y;
    private double z;
    private double xVelocity;
    private double yVelocity;
    private double zVelocity;
    private double storedXVelocity;
    private double storedYVelocity;
    private double storedZVelocity;
    private int age;
    private int slowdownTicks;
    private boolean onGround;
    private boolean landed;
    private boolean stoppedByCollision;
    private boolean inTimeStop;
    private boolean removed;

    public CasingFlightState(CasingEjectionSpec spec) {
        this.spec = spec;
        this.previousX = spec.x();
        this.previousY = spec.y();
        this.previousZ = spec.z();
        this.x = spec.x();
        this.y = spec.y();
        this.z = spec.z();
        this.xVelocity = spec.xVelocity();
        this.yVelocity = spec.yVelocity();
        this.zVelocity = spec.zVelocity();
    }

    public TickResult tick(
            boolean timeStopped,
            CollisionResolver collisionResolver) {
        if (removed) {
            return new TickResult(false, true);
        }
        if (timeStopped) {
            if (!inTimeStop) {
                inTimeStop = true;
                slowdownTicks = TIME_STOP_SLOWDOWN_TICKS;
                storedXVelocity = xVelocity;
                storedYVelocity = yVelocity;
                storedZVelocity = zVelocity;
            }
            return tickStopped(collisionResolver);
        }
        if (inTimeStop) {
            inTimeStop = false;
            double motionSqr = xVelocity * xVelocity
                    + yVelocity * yVelocity
                    + zVelocity * zVelocity;
            if (motionSqr < RESTORE_MOTION_THRESHOLD_SQR) {
                xVelocity = storedXVelocity;
                yVelocity = storedYVelocity;
                zVelocity = storedZVelocity;
            }
        }
        return tickNormally(collisionResolver);
    }

    private TickResult tickNormally(
            CollisionResolver collisionResolver) {
        rememberPreviousPosition();
        if (age++ >= spec.lifetime()) {
            removed = true;
            return new TickResult(false, true);
        }

        yVelocity -= 0.04D * GRAVITY;
        boolean landedThisTick = move(
                xVelocity,
                yVelocity,
                zVelocity,
                collisionResolver);
        xVelocity *= PARTICLE_FRICTION;
        yVelocity *= PARTICLE_FRICTION;
        zVelocity *= PARTICLE_FRICTION;
        if (onGround) {
            xVelocity *= GROUND_PARTICLE_FRICTION;
            zVelocity *= GROUND_PARTICLE_FRICTION;
        }
        applyDonorHorizontalFriction();
        return new TickResult(landedThisTick, false);
    }

    private TickResult tickStopped(
            CollisionResolver collisionResolver) {
        rememberPreviousPosition();
        boolean landedThisTick = false;
        if (slowdownTicks > 0) {
            double slowdown = (double) slowdownTicks
                    / TIME_STOP_SLOWDOWN_TICKS;
            double scale = Math.min(
                    0.35D, slowdown * slowdown * 0.35D);
            xVelocity = storedXVelocity * scale;
            yVelocity = storedYVelocity * scale;
            zVelocity = storedZVelocity * scale;
            landedThisTick = move(
                    xVelocity,
                    yVelocity,
                    zVelocity,
                    collisionResolver);
            applyDonorHorizontalFriction();
            slowdownTicks--;
        }
        else {
            xVelocity = 0.0D;
            yVelocity = 0.0D;
            zVelocity = 0.0D;
        }
        if (age++ >= spec.lifetime()) {
            removed = true;
        }
        return new TickResult(landedThisTick, removed);
    }

    private boolean move(
            double requestedX,
            double requestedY,
            double requestedZ,
            CollisionResolver collisionResolver) {
        if (stoppedByCollision) {
            return false;
        }
        ResolvedMotion resolved = collisionResolver.resolve(
                x,
                y,
                z,
                PARTICLE_WIDTH,
                PARTICLE_HEIGHT,
                requestedX,
                requestedY,
                requestedZ);
        double resolvedX = finiteOrZero(resolved.x());
        double resolvedY = finiteOrZero(resolved.y());
        double resolvedZ = finiteOrZero(resolved.z());
        x += resolvedX;
        y += resolvedY;
        z += resolvedZ;
        if (Math.abs(requestedY) >= 1.0E-5F
                && Math.abs(resolvedY) < 1.0E-5F) {
            stoppedByCollision = true;
        }
        onGround = requestedY != resolvedY && requestedY < 0.0D;
        if (requestedX != resolvedX) {
            xVelocity = 0.0D;
        }
        if (requestedZ != resolvedZ) {
            zVelocity = 0.0D;
        }
        boolean landedThisTick = onGround && !landed;
        landed |= onGround;
        return landedThisTick;
    }

    private void rememberPreviousPosition() {
        previousX = x;
        previousY = y;
        previousZ = z;
    }

    private void applyDonorHorizontalFriction() {
        double friction = onGround
                ? GROUND_HORIZONTAL_FRICTION
                : AIR_HORIZONTAL_FRICTION;
        xVelocity *= friction;
        zVelocity *= friction;
    }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0.0D;
    }

    public CasingEjectionSpec spec() {
        return spec;
    }

    public double previousX() {
        return previousX;
    }

    public double previousY() {
        return previousY;
    }

    public double previousZ() {
        return previousZ;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public double xVelocity() {
        return xVelocity;
    }

    public double yVelocity() {
        return yVelocity;
    }

    public double zVelocity() {
        return zVelocity;
    }

    public int age() {
        return age;
    }

    public boolean onGround() {
        return onGround;
    }

    public boolean removed() {
        return removed;
    }

    @FunctionalInterface
    public interface CollisionResolver {
        ResolvedMotion resolve(
                double x,
                double y,
                double z,
                double width,
                double height,
                double requestedX,
                double requestedY,
                double requestedZ);
    }

    public record ResolvedMotion(double x, double y, double z) {}

    public record TickResult(boolean landed, boolean removed) {}
}
