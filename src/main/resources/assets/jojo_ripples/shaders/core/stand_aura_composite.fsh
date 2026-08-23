#version 150

// Adapted from KINnao087/StandAuraFx revision 6f36008 (GPL-3.0).

uniform sampler2D uMaskTex;
uniform sampler2D uEntityDepthTex;
uniform sampler2D uSceneDepthTex;
uniform vec2 uTexelSize;
uniform vec2 uMaskUvMin;
uniform vec2 uMaskUvMax;

uniform float uTime;
uniform float uChaos;
uniform float uGlobalAlpha;
uniform vec2 uShapeScale;
uniform vec2 uShapeOffset;
uniform float uAspect;
uniform float uAntiAlias;
uniform float uSceneDepthOcclusion;
uniform float uAuraThickness;
uniform float uBaseAuraWidth;
uniform float uAuraWidthChaos;
uniform float uEdgeWarpStrength;
uniform float uNoiseScale;
uniform float uFillAlphaBase;
uniform float uFillAlphaFlow;
uniform float uCoreAlpha;
uniform float uEdgeAlphaBase;
uniform float uEdgeAlphaFlow;
uniform float uRimAlpha;
uniform vec3 uInnerColorA;
uniform vec3 uInnerColorB;
uniform vec3 uOuterColorA;
uniform vec3 uOuterColorB;
uniform vec3 uEdgeColor;
uniform vec3 uRimColor;
uniform float uInnerHighlightBase;
uniform float uInnerHighlightFlow;
uniform float uOuterHighlightBase;
uniform float uOuterHighlightFlow;
uniform float uEdgeHighlightStrength;

in vec2 vUv;
out vec4 fragColor;

const float PI = 3.14159265358979323846;
const int DIRECTION_COUNT = 16;
const int STEP_COUNT = 48;
const float SDF_SEARCH_RADIUS = 0.16;
const float DEPTH_BIAS = 0.00035;
const float MASK_THRESHOLD = 0.05;
const float VALID_DEPTH_MAX = 0.9995;

float hashValue(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noiseValue(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    float a = hashValue(i);
    float b = hashValue(i + vec2(1.0, 0.0));
    float c = hashValue(i + vec2(0.0, 1.0));
    float d = hashValue(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(a, b, u.x)
         + (c - a) * u.y * (1.0 - u.x)
         + (d - b) * u.x * u.y;
}

float fbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 5; i++) {
        value += amplitude * noiseValue(p);
        p *= 2.0;
        amplitude *= 0.5;
    }
    return value;
}

float sampleMask(vec2 uv) {
    if (uv.x < 0.0 || uv.x > 1.0
            || uv.y < 0.0 || uv.y > 1.0) {
        return 0.0;
    }
    vec4 maskColor = texture(uMaskTex, uv);
    return max(
        maskColor.a,
        max(maskColor.r, max(maskColor.g, maskColor.b))
    );
}

float sampleDepth(sampler2D depthTexture, vec2 uv) {
    if (uv.x < 0.0 || uv.x > 1.0
            || uv.y < 0.0 || uv.y > 1.0) {
        return 1.0;
    }
    return texture(depthTexture, uv).r;
}

vec2 maskUv(vec2 p) {
    vec2 localUv = vec2(
        (p.x / max(uAspect, 0.0001)) * 0.5 + 0.5,
        p.y * 0.5 + 0.5
    );
    return mix(uMaskUvMin, uMaskUvMax, localUv);
}

vec2 effectCoordinates(vec2 p) {
    vec2 defaultScale = vec2(0.42, 0.68);
    vec2 relativeScale = max(
        abs(uShapeScale / defaultScale),
        vec2(0.0001)
    );
    vec2 relativeOffset = uShapeOffset - vec2(0.0, 0.05);
    return (p - relativeOffset) / relativeScale;
}

float sampleEntityDepthAtShape(vec2 p) {
    vec2 uv = maskUv(p);
    float depth = sampleDepth(uEntityDepthTex, uv);
    if (depth < VALID_DEPTH_MAX) {
        return depth;
    }

    float best = 1.0;
    for (int directionIndex = 0;
            directionIndex < DIRECTION_COUNT;
            directionIndex++) {
        float angle = 2.0 * PI
            * (float(directionIndex) / float(DIRECTION_COUNT));
        vec2 direction = vec2(cos(angle), sin(angle));
        for (int stepIndex = 1; stepIndex <= 4; stepIndex++) {
            float shapeDistance =
                0.012 * (float(stepIndex) / 4.0);
            float nearbyDepth = sampleDepth(
                uEntityDepthTex,
                maskUv(p + direction * shapeDistance)
            );
            best = min(best, nearbyDepth);
        }
    }
    return best;
}

float sampleMaskAtShape(vec2 p) {
    return sampleMask(maskUv(p));
}

float standAuraSdf(vec2 p, out float nearestEntityDepth) {
    float centerMask = sampleMaskAtShape(p);
    float inside = step(MASK_THRESHOLD, centerMask);
    float best = 1000.0;
    nearestEntityDepth = inside > 0.5
        ? sampleEntityDepthAtShape(p)
        : 1.0;

    for (int directionIndex = 0;
            directionIndex < DIRECTION_COUNT;
            directionIndex++) {
        float angle = 2.0 * PI
            * (float(directionIndex) / float(DIRECTION_COUNT));
        vec2 direction = vec2(cos(angle), sin(angle));
        float previousDistance = 0.0;
        float previousMask = centerMask;

        for (int stepIndex = 1;
                stepIndex <= STEP_COUNT;
                stepIndex++) {
            float shapeDistance = SDF_SEARCH_RADIUS
                * (float(stepIndex) / float(STEP_COUNT));
            vec2 samplePoint =
                p + direction * shapeDistance;
            float maskValue = sampleMaskAtShape(samplePoint);
            float sampleInside =
                step(MASK_THRESHOLD, maskValue);

            if (abs(sampleInside - inside) > 0.5) {
                float denominator = max(
                    abs(maskValue - previousMask),
                    0.0001
                );
                float amount = clamp(
                    abs(MASK_THRESHOLD - previousMask)
                        / denominator,
                    0.0,
                    1.0
                );
                float hitDistance = mix(
                    previousDistance,
                    shapeDistance,
                    amount
                );
                vec2 hitPoint =
                    p + direction * hitDistance;
                if (hitDistance < best) {
                    best = hitDistance;
                    nearestEntityDepth =
                        sampleInside > 0.5
                        ? sampleEntityDepthAtShape(hitPoint)
                        : sampleEntityDepthAtShape(p);
                }
                break;
            }

            previousDistance = shapeDistance;
            previousMask = maskValue;
        }
    }

    if (best > 999.0) {
        best = 2.0;
    }
    return inside > 0.5 ? -best : best;
}

void main() {
    float chaos = clamp(uChaos, 0.0, 1.0);
    vec2 p = vUv * 2.0 - 1.0;
    p.x *= uAspect;

    float nearestEntityDepth;
    float distanceToMask =
        standAuraSdf(p, nearestEntityDepth);
    float antiAlias = max(uAntiAlias, 0.0001);
    vec2 q = effectCoordinates(p) * mix(
        uNoiseScale,
        uNoiseScale * 1.25,
        chaos
    );

    float n1 = fbm(
        q + vec2(
            0.0,
            -uTime * mix(1.6, 1.8, chaos)
        )
    );
    float n2 = fbm(
        q * mix(1.7, 1.9, chaos)
        + vec2(
            2.7,
            -uTime * mix(2.8, 3.2, chaos)
        )
    );
    float n3 = fbm(vec2(
        q.x * mix(0.8, 0.9, chaos)
            - uTime * mix(0.7, 0.9, chaos),
        q.y * mix(2.4, 2.8, chaos)
            - uTime * mix(1.9, 2.3, chaos)
    ));
    float n4 = fbm(
        q * mix(2.2, 3.4, chaos)
        + vec2(
            -uTime * mix(2.8, 4.5, chaos),
            uTime * mix(1.2, 2.0, chaos)
        )
    );

    float flow = clamp(
        mix(0.45, 0.30, chaos) * n1
        + mix(0.35, 0.25, chaos) * n2
        + mix(0.20, 0.20, chaos) * n3
        + mix(0.00, 0.25, chaos) * n4,
        0.0,
        1.0
    );
    float tonguePower = mix(2.5, 5.0, chaos);
    float tongue1 = pow(
        0.5 + 0.5 * sin(
            p.y * mix(34.0, 52.0, chaos)
            + uTime * mix(7.0, 11.0, chaos)
            + flow * mix(7.0, 12.0, chaos)
        ),
        tonguePower
    );
    float tongue2 = pow(
        0.5 + 0.5 * sin(
            p.x * mix(20.0, 34.0, chaos)
            - uTime * mix(5.0, 8.0, chaos)
            + flow * mix(8.0, 10.0, chaos)
        ),
        tonguePower
    );
    float tongue3 = pow(
        0.5 + 0.5 * sin(
            (p.x + p.y) * mix(26.0, 40.0, chaos)
            + uTime * mix(5.5, 9.0, chaos)
            + flow * mix(6.0, 9.0, chaos)
        ),
        mix(2.8, 6.0, chaos)
    );
    float tongue = max(max(tongue1, tongue2), tongue3);

    float jagged = smoothstep(
        mix(0.25, 0.40, chaos),
        mix(0.85, 0.92, chaos),
        flow * mix(0.85, 0.75, chaos)
            + tongue * mix(0.40, 0.75, chaos)
    );
    jagged = pow(jagged, mix(1.0, 1.35, chaos));

    float spikePulse = (
        mix(0.012, 0.020, chaos) * tongue1
        + mix(0.010, 0.018, chaos) * tongue2
        + mix(0.008, 0.016, chaos) * tongue3
    ) * uAuraThickness;
    float auraWidth =
        mix(uBaseAuraWidth, uBaseAuraWidth * 0.80, chaos)
        + mix(
            uAuraWidthChaos * 0.54,
            uAuraWidthChaos,
            chaos
        ) * jagged
        + spikePulse
        + mix(
            0.000,
            uAuraWidthChaos * 0.36,
            chaos
        ) * smoothstep(0.55, 0.95, n4);
    float edgeWarp =
        mix(0.000, uEdgeWarpStrength, chaos) * (n4 - 0.5)
        + mix(
            0.000,
            uEdgeWarpStrength * 0.8,
            chaos
        ) * (tongue - 0.5);
    float shellPosition = auraWidth + edgeWarp;

    float outerMask = 1.0 - smoothstep(
        shellPosition - antiAlias,
        shellPosition + antiAlias,
        distanceToMask
    );
    float innerCut =
        smoothstep(0.0005, 0.006, distanceToMask);
    float auraBand = outerMask
        * innerCut
        * step(0.0, distanceToMask);
    float bandStart = 0.0005;
    float bandEnd = max(
        shellPosition,
        bandStart + 0.001
    );
    float bandPosition = clamp(
        (distanceToMask - bandStart)
            / (bandEnd - bandStart),
        0.0,
        1.0
    );
    float rim = auraBand
        * (1.0 - smoothstep(0.00, 0.28, bandPosition));
    float outerEdge = auraBand
        * smoothstep(0.45, 1.00, bandPosition);
    float wisps = smoothstep(
        0.20,
        0.95,
        flow * 0.85 + tongue * 0.30
    );

    float fillAlpha = auraBand
        * (uFillAlphaBase + uFillAlphaFlow * wisps);
    float movingCoreAlpha = auraBand
        * smoothstep(0.55, 1.00, flow)
        * uCoreAlpha;
    float edgeAlpha = outerEdge
        * (uEdgeAlphaBase + uEdgeAlphaFlow * wisps);
    float innerRimAlpha = rim * uRimAlpha;
    float colorPosition =
        smoothstep(0.05, 1.00, bandPosition);

    vec3 auraInnerColor = mix(
        uInnerColorA,
        uInnerColorB,
        flow
    );
    auraInnerColor = mix(
        auraInnerColor,
        uRimColor,
        clamp(
            uInnerHighlightBase
                + uInnerHighlightFlow * wisps,
            0.0,
            1.0
        )
    );
    vec3 auraOuterColor = mix(
        uOuterColorA,
        uOuterColorB,
        clamp(
            uOuterHighlightBase
                + uOuterHighlightFlow * wisps,
            0.0,
            1.0
        )
    );
    vec3 auraFillColor = mix(
        auraInnerColor,
        auraOuterColor,
        colorPosition
    );
    vec3 finalEdgeColor = mix(
        auraFillColor,
        uEdgeColor,
        smoothstep(0.55, 1.00, bandPosition)
            * uEdgeHighlightStrength
    );

    vec3 color = auraFillColor;
    color += auraFillColor * movingCoreAlpha * 0.8;
    color = mix(
        color,
        finalEdgeColor,
        clamp(edgeAlpha, 0.0, 1.0)
    );
    color = mix(
        color,
        uRimColor,
        clamp(innerRimAlpha, 0.0, 1.0)
    );
    float alpha = clamp(
        fillAlpha
        + movingCoreAlpha
        + edgeAlpha
        + innerRimAlpha,
        0.0,
        1.0
    ) * uGlobalAlpha;

    if (uSceneDepthOcclusion > 0.5) {
        float sceneDepth = sampleDepth(
            uSceneDepthTex,
            mix(uMaskUvMin, uMaskUvMax, vUv)
        );
        if (nearestEntityDepth < VALID_DEPTH_MAX
                && sceneDepth
                    < nearestEntityDepth - DEPTH_BIAS) {
            discard;
        }
    }
    if (alpha <= 0.001) {
        discard;
    }
    fragColor = vec4(color, alpha);
}
