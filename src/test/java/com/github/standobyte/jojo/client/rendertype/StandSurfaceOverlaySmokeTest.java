package com.github.standobyte.jojo.client.rendertype;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.github.standobyte.jojo.client.entityrender.stand.StandEntityRenderState;
import com.github.standobyte.v1_21_4_stuff.renderstate.RenderStateCrutches;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

public final class StandSurfaceOverlaySmokeTest {
    public static void runChecks() {
        verifyBodyPolicy();
        verifyFinalBlend();
    }

    private static void verifyBodyPolicy() {
        ModRenderTypes.SurfaceBodyState empty = new ModRenderTypes.SurfaceBodyState();
        check(!empty.hasQueuedBody(true) && !empty.hasNearestBody(true), "native body must not be queued");
        empty.observe(false, false);
        check(!empty.hasQueuedBody(true), "overlay alone must not establish body coverage");
        empty.observe(true, false);
        check(empty.hasQueuedBody(true) && !empty.hasNearestBody(true),
                "ordinary queued body supports overlays, not RHCP nearest-only glow");
        check(!empty.hasQueuedBody(false), "another group must not lend depth");
        empty.observe(false, true);
        check(empty.hasQueuedBody(true) && empty.hasNearestBody(true),
                "nearest body must preserve RHCP coverage eligibility");
        empty.observe(false, false);
        check(empty.hasNearestBody(true), "overlay must not clear established body state");
    }

    // Like the existing body-boundary smoke check, this needs the client's registries.
    public static void verifyBodyRoutingInBootstrappedClient() {
        var previous = RenderStateCrutches.currentStandEntityRenderState;
        StandEntityRenderState state = new StandEntityRenderState();
        Object group = new Object();
        state.surfaceDrawGroup = group;
        RenderStateCrutches.currentStandEntityRenderState = state;
        try {
            ResourceLocation texture = ResourceLocation.parse("test:overlay");
            RenderType material = RenderType.eyes(texture);
            RenderType fallback = RenderType.entityTranslucent(texture);
            Supplier<ShaderInstance> shader = () -> null;
            List<RenderType> requested = new ArrayList<>();
            VertexConsumer sink = (VertexConsumer) java.lang.reflect.Proxy.newProxyInstance(
                    VertexConsumer.class.getClassLoader(), new Class<?>[] {VertexConsumer.class},
                    (proxy, method, args) -> method.getReturnType() == void.class ? null : proxy);
            MultiBufferSource source = type -> { requested.add(type); return sink; };

            MultiBufferSource noBody = ModRenderTypes.separateSurfaceBarrage(source, group);
            RenderType overlay = ModRenderTypes.standSurfaceOverlay(material, shader, fallback, material);
            check(ModRenderTypes.asStandBody(overlay) == overlay, "overlay must not be marked as a body");
            noBody.getBuffer(overlay);
            check(requested.getLast() == fallback, "no body must use native fallback");
            noBody.getBuffer(ModRenderTypes.standSurfaceGlow(texture, true));
            check(field(requested.getLast(), "surfaceMaterial") == field(requested.getLast(), "ordinaryMaterial"),
                    "orphan overlay must not activate RHCP nearest-body glow");

            MultiBufferSource ordinary = ModRenderTypes.separateSurfaceBarrage(source, group);
            ordinary.getBuffer(ModRenderTypes.asStandBody(ModRenderTypes.standTranslucent(texture)));
            Object body = requested.getLast();
            ordinary.getBuffer(ModRenderTypes.standSurfaceOverlay(material, shader, fallback, material));
            Object queued = requested.getLast();
            check(queued != fallback && field(queued, "surfaceMaterial") == material,
                    "ordinary queued body must retain custom overlay material");
            check(field(queued, "replayShader") == shader && field(queued, "groupKey") == group,
                    "custom shader and body group must survive resolution");
            check(Boolean.FALSE.equals(field(queued, "bodyPass"))
                            && Boolean.FALSE.equals(field(queued, "nearestSurface"))
                            && Boolean.FALSE.equals(field(queued, "requiresQueuedBody")),
                    "resolved overlay must remain a non-body pass");
            check((int) field(body, "emissionOrder") < (int) field(queued, "emissionOrder"),
                    "overlay submission must follow the body");
            ordinary.getBuffer(ModRenderTypes.standSurfaceGlow(texture, true));
            check(field(requested.getLast(), "surfaceMaterial") == field(requested.getLast(), "ordinaryMaterial"),
                    "ordinary body must not change RHCP nearest-only semantics");

            MultiBufferSource nearest = ModRenderTypes.separateSurfaceBarrage(source, group);
            nearest.getBuffer(ModRenderTypes.standSurfaceDiagnostic(texture, true));
            nearest.getBuffer(ModRenderTypes.standSurfaceGlow(texture, true));
            check(field(requested.getLast(), "surfaceMaterial") != field(requested.getLast(), "ordinaryMaterial"),
                    "nearest body must still activate RHCP coverage glow");
            state.surfaceDrawGroup = new Object();
            nearest.getBuffer(ModRenderTypes.standSurfaceOverlay(material, shader, fallback, material));
            check(requested.getLast() == fallback, "another group cannot lend its body depth");
        }
        finally {
            RenderStateCrutches.currentStandEntityRenderState = previous;
        }
    }

    private static void verifyFinalBlend() {
        for (double alpha : new double[] {0.0, 0.25, 0.8, 1.0}) {
            double world = 0.1;
            double body = 0.4 * alpha;
            double glint = 0.6 * Math.sqrt(alpha);
            double scratch = body + glint * glint;
            double finalColor = scratch + world * (1.0 - alpha);
            double expected = body + 0.36 * alpha + world * (1.0 - alpha);
            check(Math.abs(finalColor - expected) < 1.0E-10, "glint must not fade twice");
            double beforeBody = body + (world + glint * glint) * (1.0 - alpha);
            if (alpha > 0.0) {
                check(Math.abs(finalColor - beforeBody) > 1.0E-5,
                        "test must reject the old world-before-body ordering");
            }
        }
    }

    private static Object field(Object value, String name) {
        try {
            Field field = value.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(value);
        }
        catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
