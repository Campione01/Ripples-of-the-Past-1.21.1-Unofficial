package rotp.addons.catchtherainbow.client;

import java.util.List;

import net.minecraft.core.Direction;

/** Literal geometry inputs from the three byte-identical donor models. */
public final class RainBladeDonorGeometry {
    public static final float TEXTURE_WIDTH = 16.0F;
    public static final float TEXTURE_HEIGHT = 16.0F;
    public static final float WATER_CUTTER_Y = -3.1F;
    public static final float DROPS_X = 0.6F;
    public static final float DROPS_Y = -3.0F;
    public static final float DROPS_Z = -3.0F;

    // The donor calls setRotationAngle, not setRotationAngleDegrees.
    public static final float DROPS_X_ROTATION = -44.75F;

    public static final List<BladeVertex> BLADE_VERTICES = List.of(
            new BladeVertex(true, true, false,
                    0.5F, 3.0F, -7.85F),
            new BladeVertex(false, true, false,
                    -0.5F, 3.0F, -7.85F),
            new BladeVertex(true, false, false,
                    0.5F, 0.0F, -0.1F),
            new BladeVertex(false, false, false,
                    -0.5F, 0.0F, -0.1F),
            new BladeVertex(true, true, true,
                    0.5F, 6.8F, 0.15F),
            new BladeVertex(false, true, true,
                    -0.5F, 6.8F, 0.15F),
            new BladeVertex(true, false, true,
                    0.5F, 2.3F, 1.1F),
            new BladeVertex(false, false, true,
                    -0.5F, 2.3F, 1.1F));

    public static final List<FaceUv> BLADE_FACE_UVS = List.of(
            new FaceUv(Direction.UP, 0.0F, 6.0F, 16.0F, 6.0F),
            new FaceUv(Direction.DOWN, 0.0F, 6.0F, 16.0F, 6.0F),
            new FaceUv(Direction.EAST, 0.0F, 6.0F, 16.0F, 6.0F),
            new FaceUv(Direction.NORTH, 0.0F, 6.0F, 16.0F, 6.0F),
            new FaceUv(Direction.WEST, 0.0F, 6.0F, 16.0F, 6.0F),
            new FaceUv(Direction.SOUTH, 0.0F, 6.0F, 16.0F, 6.0F));

    public static final List<DropletSpec> DROPLETS = List.of(
            droplet("droplet_00", 10, 2,
                    -1.45F, -3.375F, -1.125F,
                    1.7F, 1.0F, 1.0F, -0.25F),
            droplet("droplet_01", 10, 2,
                    -1.45F, -1.125F, 2.875F,
                    1.7F, 1.0F, 1.0F, -0.25F),
            droplet("droplet_02", 10, 2,
                    -1.45F, -3.875F, -0.375F,
                    1.7F, 1.0F, 1.0F, -0.25F),
            droplet("droplet_03", 10, 2,
                    -1.45F, -0.125F, 1.125F,
                    1.7F, 1.0F, 1.0F, -0.25F),
            droplet("droplet_04", 11, 2,
                    -1.2F, 0.375F, -0.625F,
                    1.2F, 1.0F, 1.0F, 0.0F),
            droplet("droplet_05", 10, 2,
                    -1.45F, -2.375F, 1.375F,
                    1.7F, 1.0F, 1.0F, -0.25F),
            droplet("droplet_06", 10, 0,
                    -1.45F, 1.875F, -3.125F,
                    1.7F, 1.0F, 1.0F, -0.25F),
            droplet("droplet_07", 11, 2,
                    -1.2F, -1.375F, 0.425F,
                    1.2F, 1.0F, 1.0F, 0.0F),
            droplet("droplet_08", 10, 0,
                    -1.45F, -2.375F, -0.875F,
                    1.7F, 1.0F, 1.0F, -0.25F),
            droplet("droplet_09", 10, 2,
                    -1.45F, -0.125F, -1.625F,
                    1.7F, 1.0F, 1.0F, -0.25F),
            droplet("droplet_10", 11, 0,
                    -1.2F, -1.125F, 1.875F,
                    1.2F, 1.0F, 1.0F, 0.0F),
            droplet("droplet_11", 11, 0,
                    -1.2F, -2.875F, 0.125F,
                    1.2F, 1.0F, 1.0F, 0.0F),
            droplet("droplet_12", 10, 2,
                    -1.45F, -0.375F, 0.375F,
                    1.7F, 1.0F, 1.0F, -0.25F),
            droplet("droplet_13", 11, 0,
                    -1.2F, -1.125F, -1.375F,
                    1.2F, 1.0F, 1.0F, 0.0F),
            droplet("droplet_14", 11, 0,
                    -1.2F, 0.875F, -2.175F,
                    1.2F, 1.0F, 1.0F, 0.0F));

    private RainBladeDonorGeometry() {}

    private static DropletSpec droplet(
            String segmentId,
            int textureU,
            int textureV,
            float x,
            float y,
            float z,
            float sizeX,
            float sizeY,
            float sizeZ,
            float deformation) {
        return new DropletSpec(
                segmentId,
                textureU,
                textureV,
                x,
                y,
                z,
                sizeX,
                sizeY,
                sizeZ,
                deformation);
    }

    public record BladeVertex(
            boolean xPositive,
            boolean yPositive,
            boolean zPositive,
            float x,
            float y,
            float z) {}

    public record FaceUv(
            Direction direction,
            float u,
            float v,
            float width,
            float height) {}

    public record DropletSpec(
            String segmentId,
            int textureU,
            int textureV,
            float x,
            float y,
            float z,
            float sizeX,
            float sizeY,
            float sizeZ,
            float deformation) {}
}
