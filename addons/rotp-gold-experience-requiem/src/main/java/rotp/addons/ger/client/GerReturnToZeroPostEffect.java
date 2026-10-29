package rotp.addons.ger.client;

import java.util.Set;

import org.joml.Vector3f;

import rotp.core.api.client.render.AddonPostEffect;
import rotp.core.api.client.render.ClientRenderCompatibility;
import com.mojang.blaze3d.shaders.AbstractUniform;

import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.client.GerRtzVfxSettings.Mode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@OnlyIn(Dist.CLIENT)
public final class GerReturnToZeroPostEffect {
    private static final float SHADER_PACK_COSMIC_MAX_ALPHA = 0.72F;
    private static final ResourceLocation DONOR_CHAIN =
            GoldExperienceRequiemAddon.id("rtz_bad_signal");
    private static final ResourceLocation SHADER_PACK_CHAIN =
            GoldExperienceRequiemAddon.id(
                    "rtz_bad_signal_shader_pack");
    private static final AddonPostEffect EFFECT =
            AddonPostEffect.registerRoutes(
                    GoldExperienceRequiemAddon.id(
                            "rtz_bad_signal"),
                    Set.of(DONOR_CHAIN, SHADER_PACK_CHAIN),
                    DONOR_CHAIN,
                    RenderLevelStageEvent.Stage.AFTER_LEVEL,
                    GerReturnToZeroPostEffect::updateUniforms);

    private GerReturnToZeroPostEffect() {}

    public static void tick() {
        EFFECT.setPostChainLocation(
                ClientRenderCompatibility.snapshot()
                                .irisShaderPackInUse()
                        ? SHADER_PACK_CHAIN
                        : DONOR_CHAIN);
        EFFECT.setActive(
                GerRtzClientState.activeMode()
                        == Mode.TIME_ERASE);
    }

    public static void reset() {
        EFFECT.setActive(false);
    }

    private static void updateUniforms(
            PostChain postChain,
            float frameDelta,
            ClientRenderCompatibility.Snapshot compatibility) {
        GerRtzDiagnostics.recordPostProcess();
        float elapsed =
                GerRtzClientState.elapsedTicks() + frameDelta;
        float envelope = GerRtzClientState.lifecycleAlpha(
                frameDelta);
        float pulse = 0.94F
                + 0.06F * Mth.sin(elapsed * 0.25F);
        float intensity = Mth.clamp(
                (0.26F + 0.6F * envelope) * pulse,
                0.0F,
                1.0F);
        var camera = Minecraft.getInstance()
                .gameRenderer.getMainCamera();
        Vector3f look = new Vector3f(
                camera.getLookVector()).normalize();
        Vector3f up = new Vector3f(
                camera.getUpVector()).normalize();
        Vector3f right = new Vector3f(look)
                .cross(up)
                .normalize();
        for (PostPass pass : postChain.passes) {
            AbstractUniform alphaUniform =
                    pass.getEffect().safeGetUniform("Alpha");
            AbstractUniform time =
                    pass.getEffect().safeGetUniform("Time");
            if (time != null) {
                time.set(alphaUniform != null
                        ? elapsed
                        : elapsed / 20.0F);
            }
            AbstractUniform intensityUniform =
                    pass.getEffect()
                            .safeGetUniform("Intensity");
            if (intensityUniform != null) {
                intensityUniform.set(intensity);
            }
            if (alphaUniform != null) {
                alphaUniform.set(
                        compatibility.irisShaderPackInUse()
                                ? Math.min(
                                        envelope,
                                        SHADER_PACK_COSMIC_MAX_ALPHA)
                                : envelope);
                setVector(
                        pass,
                        "CameraLook",
                        look);
                setVector(
                        pass,
                        "CameraUp",
                        up);
                setVector(
                        pass,
                        "CameraRight",
                        right);
            }
        }
    }

    private static void setVector(
            PostPass pass,
            String uniformName,
            Vector3f value) {
        AbstractUniform uniform = pass.getEffect()
                .safeGetUniform(uniformName);
        if (uniform != null) {
            uniform.set(value.x(), value.y(), value.z());
        }
    }

}
