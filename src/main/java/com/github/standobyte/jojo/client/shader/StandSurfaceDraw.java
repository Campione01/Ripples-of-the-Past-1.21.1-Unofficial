package com.github.standobyte.jojo.client.shader;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Objects;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;

public final class StandSurfaceDraw implements AutoCloseable {
    private final VertexBuffer vertexBuffer;
    private final VertexFormat.Mode mode;
    private final RenderType surfaceMaterial;
    private final ShaderInstance shader;
    private final Snapshot snapshot;
    private final double viewDepth;
    private final int ownerId;
    private final Object groupKey;
    private final int emissionOrder;
    private final boolean bodyPass;
    private boolean closed;

    private StandSurfaceDraw(VertexBuffer vertexBuffer, VertexFormat.Mode mode,
            RenderType surfaceMaterial, ShaderInstance shader, Snapshot snapshot,
            double viewDepth, int ownerId, Object groupKey, int emissionOrder, boolean bodyPass) {
        this.vertexBuffer = vertexBuffer;
        this.mode = mode;
        this.surfaceMaterial = surfaceMaterial;
        this.shader = shader;
        this.snapshot = snapshot;
        this.viewDepth = viewDepth;
        this.ownerId = ownerId;
        this.groupKey = groupKey;
        this.emissionOrder = emissionOrder;
        this.bodyPass = bodyPass;
    }

    /** Consumes the mesh on both success and failure. */
    public static StandSurfaceDraw capture(MeshData meshData, RenderType surfaceMaterial,
            ShaderInstance privateSurfaceShader, double viewDepth, int ownerId,
            Object groupKey, int emissionOrder, boolean bodyPass) {
        try (meshData) {
            RenderSystem.assertOnRenderThread();
            Objects.requireNonNull(surfaceMaterial, "surfaceMaterial");
            Objects.requireNonNull(privateSurfaceShader, "privateSurfaceShader");
            Objects.requireNonNull(groupKey, "groupKey");
            Snapshot snapshot = Snapshot.capture(privateSurfaceShader);
            VertexFormat.Mode mode = meshData.drawState().mode();
            double geometryDepth = geometryViewDepth(meshData, snapshot.modelView(), viewDepth);
            VertexBuffer vertexBuffer = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
            try {
                try {
                    vertexBuffer.bind();
                    // Iris invalidates its CPU buffers at the end of the enclosing flush.
                    vertexBuffer.upload(meshData);
                }
                finally {
                    VertexBuffer.unbind();
                }
                return new StandSurfaceDraw(vertexBuffer, mode, surfaceMaterial,
                        privateSurfaceShader, snapshot, geometryDepth, ownerId,
                        groupKey, emissionOrder, bodyPass);
            }
            catch (RuntimeException | Error failure) {
                try {
                    vertexBuffer.close();
                }
                catch (RuntimeException | Error closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
                throw failure;
            }
        }
    }

    private static double geometryViewDepth(MeshData meshData, Matrix4f modelView, double fallback) {
        MeshData.DrawState drawState = meshData.drawState();
        VertexFormat format = drawState.format();
        int vertexCount = drawState.vertexCount();
        int stride = format.getVertexSize();
        if (vertexCount <= 0 || stride <= 0 || !format.contains(VertexFormatElement.POSITION)) {
            return fallback;
        }
        int positionOffset = format.getOffset(VertexFormatElement.POSITION);
        if (positionOffset < 0 || positionOffset > stride - 3 * Float.BYTES) {
            return fallback;
        }
        ByteBuffer vertices = meshData.vertexBuffer().duplicate().order(ByteOrder.nativeOrder());
        if ((long) vertexCount * stride > vertices.remaining()) {
            return fallback;
        }
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        int start = vertices.position() + positionOffset;
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            int offset = start + vertex * stride;
            float x = vertices.getFloat(offset);
            float y = vertices.getFloat(offset + Float.BYTES);
            float z = vertices.getFloat(offset + 2 * Float.BYTES);
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
                return fallback;
            }
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }
        Vector3f center = modelView.transformPosition(new Vector3f(
                (float) (((double) minX + maxX) * 0.5),
                (float) (((double) minY + maxY) * 0.5),
                (float) (((double) minZ + maxZ) * 0.5)));
        if (!Float.isFinite(center.x()) || !Float.isFinite(center.y()) || !Float.isFinite(center.z())) {
            return fallback;
        }
        return -(double) center.z();
    }

    public double viewDepth() {
        return viewDepth;
    }

    public int ownerId() {
        return ownerId;
    }

    public Object groupKey() {
        return groupKey;
    }

    public int emissionOrder() {
        return emissionOrder;
    }

    public boolean bodyPass() {
        return bodyPass;
    }

    public static int compareBackToFront(double leftDepth, int leftOwner, double rightDepth, int rightOwner) {
        int depthOrder = Double.compare(rightDepth, leftDepth);
        return depthOrder != 0 ? depthOrder : Integer.compare(leftOwner, rightOwner);
    }

    /** Draws into the caller-bound surface target. */
    public void draw() {
        RenderSystem.assertOnRenderThread();
        if (closed) {
            throw new IllegalStateException("Stand surface draw is closed");
        }
        try {
            surfaceMaterial.setupRenderState();
            try {
                shader.setDefaultUniforms(mode, snapshot.modelView(), snapshot.projection(),
                        Minecraft.getInstance().getWindow());
                snapshot.apply(shader);
                shader.apply();
                vertexBuffer.bind();
                vertexBuffer.draw();
            }
            finally {
                shader.clear();
            }
        }
        finally {
            try {
                VertexBuffer.unbind();
            }
            finally {
                surfaceMaterial.clearRenderState();
            }
        }
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (!closed) {
            closed = true;
            vertexBuffer.close();
        }
    }

    private record Snapshot(Matrix4f modelView, Matrix4f projection, float[] color,
            float fogStart, float fogEnd, float[] fogColor, int fogShape,
            Vector3f light0, Vector3f light1) {
        private static Snapshot capture(ShaderInstance shader) {
            RenderSystem.setupShaderLights(shader);
            return new Snapshot(
                    new Matrix4f(RenderSystem.getModelViewMatrix()),
                    new Matrix4f(RenderSystem.getProjectionMatrix()),
                    RenderSystem.getShaderColor().clone(),
                    RenderSystem.getShaderFogStart(), RenderSystem.getShaderFogEnd(),
                    RenderSystem.getShaderFogColor().clone(),
                    RenderSystem.getShaderFogShape().getIndex(),
                    copyDirection(shader.LIGHT0_DIRECTION), copyDirection(shader.LIGHT1_DIRECTION));
        }

        private static Vector3f copyDirection(Uniform uniform) {
            if (uniform == null) {
                return null;
            }
            FloatBuffer values = uniform.getFloatBuffer();
            return new Vector3f(values.get(0), values.get(1), values.get(2));
        }

        private void apply(ShaderInstance shader) {
            if (shader.COLOR_MODULATOR != null) {
                shader.COLOR_MODULATOR.set(color);
            }
            if (shader.FOG_START != null) {
                shader.FOG_START.set(fogStart);
            }
            if (shader.FOG_END != null) {
                shader.FOG_END.set(fogEnd);
            }
            if (shader.FOG_COLOR != null) {
                shader.FOG_COLOR.set(fogColor);
            }
            if (shader.FOG_SHAPE != null) {
                shader.FOG_SHAPE.set(fogShape);
            }
            if (shader.LIGHT0_DIRECTION != null && light0 != null) {
                shader.LIGHT0_DIRECTION.set(light0);
            }
            if (shader.LIGHT1_DIRECTION != null && light1 != null) {
                shader.LIGHT1_DIRECTION.set(light1);
            }
        }
    }
}
