#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;
uniform float Time;

uniform float TotalDuration;
uniform float ImplosionStrength;
uniform float ImplosionRadius;
uniform float OuterFadeDistance;
uniform float ResidualStrength;
uniform float FadeOutStart;
uniform vec2 Center;
uniform float Visibility;

in vec2 texCoord;
out vec4 fragColor;

float saturate(float value) {
    return clamp(value, 0.0, 1.0);
}

void main() {
    vec2 uv = texCoord;

    // Keep radial calculations circular on non-square screens.
    vec2 aspect = vec2(OutSize.x / max(OutSize.y, 1.0), 1.0);
    vec2 fromCenterUv = uv - Center;
    vec2 fromCenter = fromCenterUv * aspect;

    float dist = length(fromCenter);
    vec2 radialDir = dist > 0.0001 ? fromCenter / dist : vec2(0.0, 0.0);
    vec2 radialDirUv = vec2(radialDir.x / aspect.x, radialDir.y);

    float progress = saturate(Time / max(TotalDuration, 0.001));

    // Stage 1: quick inward collapse.
    float implosionEnvelope = 1.0 - smoothstep(0.0, 0.20, progress);

    // Stage 2: restrained middle-area instability after the collapse.
    float residualEnvelope = smoothstep(0.16, 0.34, progress) * (1.0 - smoothstep(0.52, 0.90, progress));

    // Late fade-out so the effect dies cleanly.
    float globalFade = (1.0 - smoothstep(FadeOutStart, 1.0, progress)) * Visibility;

    // Local-only falloff. The disturbance must never reach the screen edges.
    float outerMask = 1.0 - smoothstep(OuterFadeDistance * 0.72, OuterFadeDistance, dist);

    // Dense inner core for the initial collapse.
    float coreMask = 1.0 - smoothstep(ImplosionRadius * 0.55, ImplosionRadius, dist);

    // Middle-area band for residual instability.
    float residualBand =
    smoothstep(ImplosionRadius * 0.85, ImplosionRadius * 1.20, dist) *
    (1.0 - smoothstep(OuterFadeDistance * 0.72, OuterFadeDistance, dist));

    // Inward pull during the first phase.
    float implosionOffset = ImplosionStrength * implosionEnvelope * coreMask;

    // Subtle middle-area distortion after the collapse.
    float residualNoise = 0.5 + 0.5 * sin(Time * 10.0 + dist * 36.0);
    float residualOffset = ResidualStrength * residualBand * residualEnvelope * residualNoise;

    float effectMask = outerMask * globalFade;

    // Pure radial warp only.
    vec2 warpedUv = uv + radialDirUv * ((implosionOffset + residualOffset) * effectMask);
    warpedUv = clamp(warpedUv, vec2(0.0), vec2(1.0));

    fragColor = texture(DiffuseSampler, warpedUv);
}