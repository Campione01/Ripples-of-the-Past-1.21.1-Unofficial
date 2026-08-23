package com.github.standobyte.jojo.client.entityrender.stand.aura;

import com.github.standobyte.jojo.api.client.render.EntityMaskCompositeContext;
import com.github.standobyte.jojo.client.shader.ModShaders;
import com.github.standobyte.jojo.config.client.ClientModSettings;
import com.github.standobyte.jojo.config.client.StandAuraSettings;
import com.github.standobyte.jojo.core.JojoMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;

import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/**
 * ShaderInstance adaptation of StandAuraFx's GPL-3.0 billboard compositor.
 */
public final class StandAuraShaders {
    private static final float[] SILVER = {0.95F, 0.97F, 1.0F};
    private static final float[] PALE_BLUE = {0.74F, 0.82F, 0.97F};
    private static ShaderInstance composite;

    private StandAuraShaders() {}

    public static void loadCoreShader(RegisterShadersEvent event) {
        composite = null;
        ModShaders.loadPrivateTargetCoreShader(
                event,
                JojoMod.resLoc("stand_aura_composite"),
                DefaultVertexFormat.POSITION_TEX,
                shader -> composite = shader);
    }

    static ShaderInstance composite() {
        return composite;
    }

    static void applyCompositeUniforms(
            ShaderInstance shader,
            EntityMaskCompositeContext context,
            int color,
            float alpha) {
        StandAuraSettings settings =
                ClientModSettings.getSettingsReadOnly().standAura;
        float thickness = context.auraThicknessScale()
                * settings.auraThickness;
        float[] base = liftedBaseColor(color);
        float[] innerA = mix(base, PALE_BLUE, 0.16F);
        float[] innerB = mix(base, SILVER, 0.28F);
        float[] outerA = mix(base, PALE_BLUE, 0.34F);
        float[] outerB = mix(base, SILVER, 0.48F);
        float[] edge = mix(base, SILVER, 0.62F);

        shader.safeGetUniform("uTime").set(
                (context.primaryEntity().tickCount
                        + context.partialTick()) * 0.05F);
        shader.safeGetUniform("uChaos").set(settings.chaos);
        shader.safeGetUniform("uGlobalAlpha").set(
                settings.globalAlpha * alpha);
        shader.safeGetUniform("uShapeScale").set(
                settings.shapeScaleX,
                settings.shapeScaleY);
        shader.safeGetUniform("uShapeOffset").set(
                settings.shapeOffsetX,
                settings.shapeOffsetY);
        shader.safeGetUniform("uAuraThickness").set(thickness);
        shader.safeGetUniform("uBaseAuraWidth").set(
                settings.baseAuraWidth * thickness);
        shader.safeGetUniform("uAuraWidthChaos").set(
                settings.auraWidthChaos * thickness);
        shader.safeGetUniform("uEdgeWarpStrength").set(
                settings.edgeWarpStrength * thickness);
        shader.safeGetUniform("uNoiseScale").set(settings.noiseScale);
        shader.safeGetUniform("uFillAlphaBase")
                .set(settings.fillAlphaBase);
        shader.safeGetUniform("uFillAlphaFlow")
                .set(settings.fillAlphaFlow);
        shader.safeGetUniform("uCoreAlpha").set(settings.coreAlpha);
        shader.safeGetUniform("uEdgeAlphaBase")
                .set(settings.edgeAlphaBase);
        shader.safeGetUniform("uEdgeAlphaFlow")
                .set(settings.edgeAlphaFlow);
        shader.safeGetUniform("uRimAlpha").set(settings.rimAlpha);
        setVec3(shader, "uInnerColorA", innerA);
        setVec3(shader, "uInnerColorB", innerB);
        setVec3(shader, "uOuterColorA", outerA);
        setVec3(shader, "uOuterColorB", outerB);
        setVec3(shader, "uEdgeColor", edge);
        setVec3(shader, "uRimColor", SILVER);
        shader.safeGetUniform("uInnerHighlightBase")
                .set(settings.innerHighlightBase);
        shader.safeGetUniform("uInnerHighlightFlow")
                .set(settings.innerHighlightFlow);
        shader.safeGetUniform("uOuterHighlightBase")
                .set(settings.outerHighlightBase);
        shader.safeGetUniform("uOuterHighlightFlow")
                .set(settings.outerHighlightFlow);
        shader.safeGetUniform("uEdgeHighlightStrength")
                .set(settings.edgeHighlightStrength);
    }

    private static void setVec3(
            ShaderInstance shader, String uniform, float[] value) {
        shader.safeGetUniform(uniform)
                .set(value[0], value[1], value[2]);
    }

    private static float[] liftedBaseColor(int color) {
        float red = (color >> 16 & 0xFF) / 255.0F;
        float green = (color >> 8 & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        float luminance =
                0.299F * red + 0.587F * green + 0.114F * blue;
        float lift = luminance < 0.35F
                ? (0.35F - luminance) * 1.15F
                : 0.0F;
        return new float[] {
                clamp(red + (1.0F - red) * lift),
                clamp(green + (1.0F - green) * lift),
                clamp(blue + (1.0F - blue) * lift)
        };
    }

    private static float[] mix(
            float[] first, float[] second, float amount) {
        return new float[] {
                clamp(first[0] + (second[0] - first[0]) * amount),
                clamp(first[1] + (second[1] - first[1]) * amount),
                clamp(first[2] + (second[2] - first[2]) * amount)
        };
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(value, 1.0F));
    }
}
