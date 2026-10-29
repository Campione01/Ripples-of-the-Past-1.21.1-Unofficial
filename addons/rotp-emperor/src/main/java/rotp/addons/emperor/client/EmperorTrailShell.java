package rotp.addons.emperor.client;

/**
 * Mesh of one bullet trail segment in the segment's own frame: x runs
 * back along the trail from the newer end, y and z span the beam.
 * The donor draws a square shell whose sides sit one unit off the axis,
 * every quad from both faces. Its light normal is not geometric: the
 * donor's vertex helper swaps the Y and Z of the argument it is given and
 * the per-side rotations then turn it, which leaves world-up on every
 * vertex of the shell.
 */
public final class EmperorTrailShell {
    public static final float V1 = 0.015625F;

    @FunctionalInterface
    public interface VertexSink {
        void vertex(
                float x,
                float y,
                float z,
                float u,
                float v,
                float lightX,
                float lightY,
                float lightZ);
    }

    private EmperorTrailShell() {}

    public static void emit(
            float length,
            float u0,
            float u1,
            boolean front,
            VertexSink sink) {
        if (front) {
            frontFace(sink, 1.0F);
            frontFace(sink, -1.0F);
        }
        for (int side = 1; side <= 4; side++) {
            sideFace(sink, side, length, u0, u1, 1.0F);
            sideFace(sink, side, length, u0, u1, -1.0F);
        }
    }

    private static void frontFace(VertexSink sink, float face) {
        float low = -face;
        float high = face;
        lit(sink, 0.0F, low, 1.0F, V1, V1);
        lit(sink, 0.0F, low, -1.0F, 0.0F, V1);
        lit(sink, 0.0F, high, -1.0F, 0.0F, V1 * 2.0F);
        lit(sink, 0.0F, high, 1.0F, V1, V1 * 2.0F);
    }

    private static void lit(
            VertexSink sink,
            float x,
            float y,
            float z,
            float u,
            float v) {
        sink.vertex(x, y, z, u, v, 0.0F, 1.0F, 0.0F);
    }

    private static void sideFace(
            VertexSink sink,
            int side,
            float length,
            float u0,
            float u1,
            float face) {
        float low = -face;
        float high = face;
        sideVertex(sink, side, 0.0F, low, u1, 0.0F);
        sideVertex(sink, side, length, low, u0, 0.0F);
        sideVertex(sink, side, length, high, u0, V1);
        sideVertex(sink, side, 0.0F, high, u1, V1);
    }

    private static void sideVertex(
            VertexSink sink,
            int side,
            float x,
            float y,
            float u,
            float v) {
        lit(
                sink,
                x,
                turnedY(side, y, 1.0F),
                turnedZ(side, y, 1.0F),
                u,
                v);
    }

    // (y, z) after quarterTurns quarter turns about +x.
    private static float turnedY(int quarterTurns, float y, float z) {
        return switch (quarterTurns & 3) {
            case 1 -> -z;
            case 2 -> -y;
            case 3 -> z;
            default -> y;
        };
    }

    private static float turnedZ(int quarterTurns, float y, float z) {
        return switch (quarterTurns & 3) {
            case 1 -> y;
            case 2 -> -z;
            case 3 -> -y;
            default -> z;
        };
    }
}
