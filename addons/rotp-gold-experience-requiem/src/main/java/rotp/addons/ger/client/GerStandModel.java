package rotp.addons.ger.client;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import rotp.core.client.entityrender.stand.StandEntityModel;
import rotp.core.client.entityrender.stand.StandEntityRenderState;
import rotp.core.core.JojoMod;
import rotp.core.compat.v1_21_4.renderstate.RenderStateCrutches;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.ger.entity.GerStandEntity;
import net.minecraft.client.model.geom.ModelPart;

public final class GerStandModel extends StandEntityModel<
        GerStandEntity,
        StandEntityRenderState> {
    private static final Set<String> LOGGED_PHASES =
            ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<DiagnosticContext>
            ACTIVE_DIAGNOSTIC = new ThreadLocal<>();

    public GerStandModel(ModelPart root) {
        super(root);
    }

    static DiagnosticContext pushDiagnostic(
            String phase,
            int entityId,
            int entityAge) {
        DiagnosticContext previous = ACTIVE_DIAGNOSTIC.get();
        ACTIVE_DIAGNOSTIC.set(
                new DiagnosticContext(phase, entityId, entityAge));
        return previous;
    }

    static void restoreDiagnostic(DiagnosticContext previous) {
        if (previous != null) {
            ACTIVE_DIAGNOSTIC.set(previous);
        }
        else {
            ACTIVE_DIAGNOSTIC.remove();
        }
    }

    @Override
    public void renderToBuffer(
            PoseStack poseStack,
            VertexConsumer buffer,
            int packedLight,
            int packedOverlay,
            int color) {
        logRenderMatrixOnce(poseStack);
        super.renderToBuffer(
                poseStack,
                buffer,
                packedLight,
                packedOverlay,
                color);
    }

    private static void logRenderMatrixOnce(PoseStack poseStack) {
        DiagnosticContext context = ACTIVE_DIAGNOSTIC.get();
        if (context == null
                || !LOGGED_PHASES.add(context.phase())) {
            return;
        }

        Matrix4f matrix = new Matrix4f(poseStack.last().pose());
        Vector3f origin = transform(matrix, 0.0F, 0.0F, 0.0F);
        Vector3f basisX = transform(matrix, 1.0F, 0.0F, 0.0F)
                .sub(origin);
        Vector3f basisY = transform(matrix, 0.0F, 1.0F, 0.0F)
                .sub(origin);
        Vector3f basisZ = transform(matrix, 0.0F, 0.0F, 1.0F)
                .sub(origin);
        float scaleX = basisX.length();
        float scaleY = basisY.length();
        float scaleZ = basisZ.length();
        float determinant = new Vector3f(basisX)
                .cross(basisY)
                .dot(basisZ);

        Vector3f top = transformPixels(
                matrix, 0.0F, -13.43023F, 0.0F);
        Vector3f bottom = transformPixels(
                matrix, 0.0F, 23.999F, 0.0F);
        Vector3f left = transformPixels(
                matrix, -8.5F, 0.0F, 0.0F);
        Vector3f right = transformPixels(
                matrix, 8.5F, 0.0F, 0.0F);
        StandEntityRenderState renderState =
                RenderStateCrutches.currentStandEntityRenderState;
        float stateAge = renderState != null
                ? renderState.ageInTicks
                : Float.NaN;

        JojoMod.getLogger().info(String.format(
                Locale.ROOT,
                "GER_RENDER_MATRIX phase=%s entityId=%d "
                        + "entityAge=%d stateAge=%.3f "
                        + "basis3x3Columns=[X%s,Y%s,Z%s] "
                        + "scale=(%.6f,%.6f,%.6f) "
                        + "determinant=%.6f mirrored=%s origin=%s "
                        + "endpointsPxToRender=[top%s,bottom%s,"
                        + "left%s,right%s]",
                context.phase(),
                context.entityId(),
                context.entityAge(),
                stateAge,
                vector(basisX),
                vector(basisY),
                vector(basisZ),
                scaleX,
                scaleY,
                scaleZ,
                determinant,
                determinant < 0.0F,
                vector(origin),
                vector(top),
                vector(bottom),
                vector(left),
                vector(right)));
    }

    private static Vector3f transform(
            Matrix4f matrix,
            float x,
            float y,
            float z) {
        return matrix.transformPosition(new Vector3f(x, y, z));
    }

    private static Vector3f transformPixels(
            Matrix4f matrix,
            float x,
            float y,
            float z) {
        return transform(matrix, x / 16.0F, y / 16.0F, z / 16.0F);
    }

    private static String vector(Vector3f vector) {
        return String.format(
                Locale.ROOT,
                "(%.6f,%.6f,%.6f)",
                vector.x(),
                vector.y(),
                vector.z());
    }

    static record DiagnosticContext(
            String phase,
            int entityId,
            int entityAge) {}
}
