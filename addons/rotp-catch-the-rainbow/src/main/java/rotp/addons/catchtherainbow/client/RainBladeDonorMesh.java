package rotp.addons.catchtherainbow.client;

import java.util.EnumMap;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.catchtherainbow.client.RainBladeDonorGeometry.BladeVertex;
import rotp.addons.catchtherainbow.client.RainBladeDonorGeometry.FaceUv;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;

/** NeoForge port of the donor CustomVerticesModelBox builder. */
final class RainBladeDonorMesh {
    private final ModelPart.Polygon[] polygons;

    private RainBladeDonorMesh(
            Builder builder,
            float textureWidth,
            float textureHeight,
            boolean mirror) {
        ModelPart.Vertex x0y0z0 = mirror
                ? builder.vertices[4] : builder.vertices[0];
        ModelPart.Vertex x0y0z1 = mirror
                ? builder.vertices[5] : builder.vertices[1];
        ModelPart.Vertex x0y1z0 = mirror
                ? builder.vertices[6] : builder.vertices[2];
        ModelPart.Vertex x0y1z1 = mirror
                ? builder.vertices[7] : builder.vertices[3];
        ModelPart.Vertex x1y0z0 = mirror
                ? builder.vertices[0] : builder.vertices[4];
        ModelPart.Vertex x1y0z1 = mirror
                ? builder.vertices[1] : builder.vertices[5];
        ModelPart.Vertex x1y1z0 = mirror
                ? builder.vertices[2] : builder.vertices[6];
        ModelPart.Vertex x1y1z1 = mirror
                ? builder.vertices[3] : builder.vertices[7];

        polygons = new ModelPart.Polygon[6];
        UvInfo uv = builder.uv.get(Direction.DOWN);
        polygons[2] = polygon(
                new ModelPart.Vertex[] {
                    x1y0z1, x0y0z1, x0y0z0, x1y0z0
                },
                uv,
                textureWidth,
                textureHeight,
                mirror,
                Direction.DOWN,
                false);
        uv = builder.uv.get(Direction.UP);
        polygons[3] = polygon(
                new ModelPart.Vertex[] {
                    x1y1z0, x0y1z0, x0y1z1, x1y1z1
                },
                uv,
                textureWidth,
                textureHeight,
                mirror,
                Direction.UP,
                true);
        uv = builder.uv.get(Direction.WEST);
        polygons[1] = polygon(
                new ModelPart.Vertex[] {
                    x0y0z0, x0y0z1, x0y1z1, x0y1z0
                },
                uv,
                textureWidth,
                textureHeight,
                mirror,
                Direction.WEST,
                false);
        uv = builder.uv.get(Direction.NORTH);
        polygons[4] = polygon(
                new ModelPart.Vertex[] {
                    x1y0z0, x0y0z0, x0y1z0, x1y1z0
                },
                uv,
                textureWidth,
                textureHeight,
                mirror,
                Direction.NORTH,
                false);
        uv = builder.uv.get(Direction.EAST);
        polygons[0] = polygon(
                new ModelPart.Vertex[] {
                    x1y0z1, x1y0z0, x1y1z0, x1y1z1
                },
                uv,
                textureWidth,
                textureHeight,
                mirror,
                Direction.EAST,
                false);
        uv = builder.uv.get(Direction.SOUTH);
        polygons[5] = polygon(
                new ModelPart.Vertex[] {
                    x0y0z1, x1y0z1, x1y1z1, x0y1z1
                },
                uv,
                textureWidth,
                textureHeight,
                mirror,
                Direction.SOUTH,
                false);
    }

    static RainBladeDonorMesh create() {
        Builder builder = new Builder(true);
        for (BladeVertex vertex
                : RainBladeDonorGeometry.BLADE_VERTICES) {
            builder.withVertex(vertex);
        }
        for (FaceUv face : RainBladeDonorGeometry.BLADE_FACE_UVS) {
            builder.withUvFace(face);
        }
        return builder.build(
                RainBladeDonorGeometry.TEXTURE_WIDTH,
                RainBladeDonorGeometry.TEXTURE_HEIGHT,
                false);
    }

    void render(
            PoseStack.Pose pose,
            VertexConsumer vertices,
            int packedLight,
            int packedOverlay,
            int color) {
        Matrix4f matrix = pose.pose();
        Vector3f normalScratch = new Vector3f();
        Vector3f positionScratch = new Vector3f();
        for (ModelPart.Polygon polygon : polygons) {
            Vector3f normal = pose.transformNormal(
                    polygon.normal, normalScratch);
            for (ModelPart.Vertex vertex : polygon.vertices) {
                Vector3f position = matrix.transformPosition(
                        vertex.pos.x() / 16.0F,
                        vertex.pos.y() / 16.0F,
                        vertex.pos.z() / 16.0F,
                        positionScratch);
                vertices.addVertex(
                        position.x(),
                        position.y(),
                        position.z(),
                        color,
                        vertex.u,
                        vertex.v,
                        packedOverlay,
                        packedLight,
                        normal.x(),
                        normal.y(),
                        normal.z());
            }
        }
    }

    private static ModelPart.Polygon polygon(
            ModelPart.Vertex[] vertices,
            UvInfo uv,
            float textureWidth,
            float textureHeight,
            boolean mirror,
            Direction direction,
            boolean invertV) {
        if (uv == null) {
            throw new IllegalStateException(
                    "Missing donor mesh UV for " + direction);
        }
        return new ModelPart.Polygon(
                vertices,
                uv.u0,
                invertV ? uv.v1 : uv.v0,
                uv.u1,
                invertV ? uv.v0 : uv.v1,
                textureWidth,
                textureHeight,
                mirror,
                direction);
    }

    private static final class Builder {
        private final boolean livingEntityRenderHacks;
        private final ModelPart.Vertex[] vertices =
                new ModelPart.Vertex[8];
        private final Map<Direction, UvInfo> uv =
                new EnumMap<>(Direction.class);
        private int initializedVertices;

        private Builder(boolean livingEntityRenderHacks) {
            this.livingEntityRenderHacks = livingEntityRenderHacks;
        }

        private void withVertex(BladeVertex source) {
            boolean xPositive = source.xPositive();
            boolean yPositive = source.yPositive();
            float x = source.x();
            float y = source.y();
            if (livingEntityRenderHacks) {
                x = -x;
                y = -y;
                xPositive = !xPositive;
                yPositive = !yPositive;
            }
            int index = (source.zPositive() ? 1 : 0)
                    | (yPositive ? 2 : 0)
                    | (xPositive ? 4 : 0);
            initializedVertices |= 1 << index;
            vertices[index] = new ModelPart.Vertex(
                    x,
                    y,
                    source.z(),
                    yPositive ? 8.0F : 0.0F,
                    xPositive ? 8.0F : 0.0F);
        }

        private void withUvFace(FaceUv source) {
            Direction direction = source.direction();
            if (livingEntityRenderHacks
                    && direction.getAxis() != Direction.Axis.Z) {
                direction = direction.getOpposite();
            }
            uv.put(direction, new UvInfo(
                    source.u(),
                    source.v(),
                    source.u() + source.width(),
                    source.v() + source.height()));
        }

        private RainBladeDonorMesh build(
                float textureWidth,
                float textureHeight,
                boolean mirror) {
            if (initializedVertices != 0xFF || uv.size() != 6) {
                throw new IllegalStateException(
                        "Incomplete donor rain-blade mesh");
            }
            return new RainBladeDonorMesh(
                    this, textureWidth, textureHeight, mirror);
        }
    }

    private record UvInfo(
            float u0, float v0, float u1, float v1) {}
}
