package rotp.addons.ger.client;

import java.util.IdentityHashMap;
import java.util.Map;

import org.lwjgl.opengl.GL30;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Redirects entity and baked-model materials through a scoped alpha blend.
 */
@OnlyIn(Dist.CLIENT)
final class GerRtzAlphaBlendBufferSource
        implements MultiBufferSource {
    private final MultiBufferSource delegate;
    private final Map<RenderType, RenderType> alphaBlendTypes =
            new IdentityHashMap<>();
    private RenderTargetBinding renderTarget;

    GerRtzAlphaBlendBufferSource(MultiBufferSource delegate) {
        this.delegate = delegate;
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderType) {
        if (renderTarget == null) {
            throw new IllegalStateException(
                    "RTZ ghost render target is not captured");
        }
        RenderType alphaBlendType =
                alphaBlendTypes.computeIfAbsent(
                        renderType,
                        AlphaBlendRenderType::new);
        return delegate.getBuffer(alphaBlendType);
    }

    void setRenderTarget(
            int drawFramebuffer,
            int readFramebuffer,
            int viewportX,
            int viewportY,
            int viewportWidth,
            int viewportHeight) {
        RenderSystem.assertOnRenderThread();
        renderTarget = new RenderTargetBinding(
                drawFramebuffer,
                readFramebuffer,
                viewportX,
                viewportY,
                viewportWidth,
                viewportHeight);
    }

    void clearRenderTarget() {
        renderTarget = null;
    }

    private void bindRenderTarget() {
        RenderTargetBinding target = renderTarget;
        if (target == null) {
            throw new IllegalStateException(
                    "RTZ ghost render target is not captured");
        }
        GL30.glBindFramebuffer(
                GL30.GL_DRAW_FRAMEBUFFER,
                target.drawFramebuffer());
        GL30.glBindFramebuffer(
                GL30.GL_READ_FRAMEBUFFER,
                target.readFramebuffer());
        GlStateManager._viewport(
                target.viewportX(),
                target.viewportY(),
                target.viewportWidth(),
                target.viewportHeight());
    }

    private final class AlphaBlendRenderType
            extends RenderType {
        @Deprecated
        private AlphaBlendRenderType(RenderType source) {
            super(
                    "geraddon:rtz_ghost_alpha_blend",
                    source.format(),
                    source.mode(),
                    source.bufferSize(),
                    source.affectsCrumbling(),
                    source.sortOnUpload(),
                    () -> {
                        source.setupRenderState();
                        bindRenderTarget();
                        RenderSystem.enableBlend();
                        RenderSystem.defaultBlendFunc();
                    },
                    () -> {
                        try {
                            source.clearRenderState();
                        }
                        finally {
                            bindRenderTarget();
                        }
                    });
        }
    }

    private record RenderTargetBinding(
            int drawFramebuffer,
            int readFramebuffer,
            int viewportX,
            int viewportY,
            int viewportWidth,
            int viewportHeight) {}
}
