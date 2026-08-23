package com.github.standobyte.jojo.config.client;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Persistent Stand Aura FX values adapted from KINnao087/StandAuraFx.
 * Upstream revision: 6f36008b37bc7165a8c1fd594b246923557dc417 (GPL-3.0).
 */
public final class StandAuraSettings {
    public static final int FALLBACK_COLOR = 0x8B5CFF;

    public boolean enabled = true;
    public Mode mode = Mode.AUTO;
    public float chaos = 0.2F;
    public float globalAlpha = 0.9F;
    public float framebufferScale = 0.5F;
    public float shapeScaleX = 0.42F;
    public float shapeScaleY = 0.68F;
    public float shapeOffsetX = 0.0F;
    public float shapeOffsetY = 0.05F;
    public float baseAuraWidth = 0.05F;
    public float auraWidthChaos = 0.028F;
    public float edgeWarpStrength = 0.01F;
    public float auraThickness = 0.45F;
    public float noiseScale = 10.0F;
    public float fillAlphaBase = 0.32F;
    public float fillAlphaFlow = 0.2F;
    public float coreAlpha = 0.16F;
    public float edgeAlphaBase = 0.8F;
    public float edgeAlphaFlow = 0.25F;
    public float rimAlpha = 0.42F;
    public float innerHighlightBase = 0.35F;
    public float innerHighlightFlow = 0.2F;
    public float outerHighlightBase = 0.7F;
    public float outerHighlightFlow = 0.25F;
    public float edgeHighlightStrength = 0.55F;

    public static final List<Parameter> PARAMETERS = List.of(
            parameter("chaos", 0.2F, 0.0F, 1.0F,
                    value -> value.chaos,
                    (value, next) -> value.chaos = next),
            parameter("globalAlpha", 0.9F, 0.0F, 2.0F,
                    value -> value.globalAlpha,
                    (value, next) -> value.globalAlpha = next),
            parameter("framebufferScale", 0.5F, 0.25F, 1.0F,
                    value -> value.framebufferScale,
                    (value, next) -> value.framebufferScale = next),
            parameter("shapeScaleX", 0.42F, 0.05F, 2.0F,
                    value -> value.shapeScaleX,
                    (value, next) -> value.shapeScaleX = next),
            parameter("shapeScaleY", 0.68F, 0.05F, 2.0F,
                    value -> value.shapeScaleY,
                    (value, next) -> value.shapeScaleY = next),
            parameter("shapeOffsetX", 0.0F, -1.0F, 1.0F,
                    value -> value.shapeOffsetX,
                    (value, next) -> value.shapeOffsetX = next),
            parameter("shapeOffsetY", 0.05F, -1.0F, 1.0F,
                    value -> value.shapeOffsetY,
                    (value, next) -> value.shapeOffsetY = next),
            parameter("baseAuraWidth", 0.05F, 0.0F, 0.5F,
                    value -> value.baseAuraWidth,
                    (value, next) -> value.baseAuraWidth = next),
            parameter("auraWidthChaos", 0.028F, 0.0F, 0.5F,
                    value -> value.auraWidthChaos,
                    (value, next) -> value.auraWidthChaos = next),
            parameter("edgeWarpStrength", 0.01F, 0.0F, 0.2F,
                    value -> value.edgeWarpStrength,
                    (value, next) -> value.edgeWarpStrength = next),
            parameter("auraThickness", 0.45F, 0.0F, 2.0F,
                    value -> value.auraThickness,
                    (value, next) -> value.auraThickness = next),
            parameter("noiseScale", 10.0F, 0.0F, 100.0F,
                    value -> value.noiseScale,
                    (value, next) -> value.noiseScale = next),
            parameter("fillAlphaBase", 0.32F, 0.0F, 2.0F,
                    value -> value.fillAlphaBase,
                    (value, next) -> value.fillAlphaBase = next),
            parameter("fillAlphaFlow", 0.2F, 0.0F, 2.0F,
                    value -> value.fillAlphaFlow,
                    (value, next) -> value.fillAlphaFlow = next),
            parameter("coreAlpha", 0.16F, 0.0F, 2.0F,
                    value -> value.coreAlpha,
                    (value, next) -> value.coreAlpha = next),
            parameter("edgeAlphaBase", 0.8F, 0.0F, 2.0F,
                    value -> value.edgeAlphaBase,
                    (value, next) -> value.edgeAlphaBase = next),
            parameter("edgeAlphaFlow", 0.25F, 0.0F, 2.0F,
                    value -> value.edgeAlphaFlow,
                    (value, next) -> value.edgeAlphaFlow = next),
            parameter("rimAlpha", 0.42F, 0.0F, 2.0F,
                    value -> value.rimAlpha,
                    (value, next) -> value.rimAlpha = next),
            parameter("innerHighlightBase", 0.35F, 0.0F, 2.0F,
                    value -> value.innerHighlightBase,
                    (value, next) -> value.innerHighlightBase = next),
            parameter("innerHighlightFlow", 0.2F, 0.0F, 2.0F,
                    value -> value.innerHighlightFlow,
                    (value, next) -> value.innerHighlightFlow = next),
            parameter("outerHighlightBase", 0.7F, 0.0F, 2.0F,
                    value -> value.outerHighlightBase,
                    (value, next) -> value.outerHighlightBase = next),
            parameter("outerHighlightFlow", 0.25F, 0.0F, 2.0F,
                    value -> value.outerHighlightFlow,
                    (value, next) -> value.outerHighlightFlow = next),
            parameter("edgeHighlightStrength", 0.55F, 0.0F, 2.0F,
                    value -> value.edgeHighlightStrength,
                    (value, next) -> value.edgeHighlightStrength = next));

    public void sanitize() {
        if (mode == null) {
            mode = Mode.AUTO;
        }
        PARAMETERS.forEach(parameter -> parameter.sanitize(this));
    }

    public boolean automaticAura(
            boolean hasResolveEffect, boolean hasStandPower) {
        if (!enabled) {
            return false;
        }
        return mode == Mode.AUTO ? hasResolveEffect : hasStandPower;
    }

    public void resetParameters() {
        PARAMETERS.forEach(parameter -> parameter.reset(this));
    }

    private static Parameter parameter(
            String name,
            float defaultValue,
            float minimum,
            float maximum,
            Function<StandAuraSettings, Float> getter,
            BiConsumer<StandAuraSettings, Float> setter) {
        return new Parameter(
                name,
                defaultValue,
                minimum,
                maximum,
                getter,
                setter);
    }

    public enum Mode {
        AUTO,
        OPEN
    }

    public record Parameter(
            String name,
            float defaultValue,
            float minimum,
            float maximum,
            Function<StandAuraSettings, Float> getter,
            BiConsumer<StandAuraSettings, Float> setter) {
        public float get(StandAuraSettings settings) {
            return getter.apply(settings);
        }

        public void set(StandAuraSettings settings, float value) {
            setter.accept(settings, clamp(value));
        }

        public void reset(StandAuraSettings settings) {
            setter.accept(settings, defaultValue);
        }

        public double normalized(StandAuraSettings settings) {
            return (get(settings) - minimum) / (maximum - minimum);
        }

        public float fromNormalized(double normalized) {
            return (float) (minimum
                    + Math.max(0.0D, Math.min(1.0D, normalized))
                            * (maximum - minimum));
        }

        private void sanitize(StandAuraSettings settings) {
            float value = get(settings);
            setter.accept(
                    settings,
                    Float.isFinite(value) ? clamp(value) : defaultValue);
        }

        private float clamp(float value) {
            return Math.max(minimum, Math.min(maximum, value));
        }
    }
}
