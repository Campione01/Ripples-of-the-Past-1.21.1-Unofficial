#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;
uniform float Time;
uniform float Intensity;

in vec2 texCoord;
out vec4 fragColor;

float saturate(float value) {
    return clamp(value, 0.0, 1.0);
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec2 hash22(vec2 p) {
    float n = hash12(p);
    return vec2(n, hash12(p + n + 23.17));
}

vec2 clampPx(vec2 fragPx) {
    return clamp(fragPx, vec2(0.0), max(OutSize - vec2(1.0), vec2(0.0)));
}

vec3 scenePx(vec2 fragPx) {
    vec2 uv = clampPx(fragPx) / OutSize;
    return texture(DiffuseSampler, uv).rgb;
}

vec2 rectangleGlitchOffsetPx(vec2 fragPx, float time, float intensity) {
    vec2 anchorCellPx = vec2(180.0, 100.0);
    vec2 rectSizePx = vec2(280.0, 25.0);
    float strengthPx = OutSize.y * mix(0.015, 0.05, intensity);
    vec2 anchorId = floor(fragPx / anchorCellPx);

    vec2 bestOffset = vec2(0.0);
    float bestPriority = -1.0;

    for (int oy = -1; oy <= 1; oy++) {
        for (int ox = -1; ox <= 1; ox++) {
            vec2 cell = anchorId + vec2(float(ox), float(oy));
            vec2 cellOrigin = cell * anchorCellPx;

            vec2 randInCell = vec2(
                hash12(cell + vec2(1.3, 5.7)),
                hash12(cell + vec2(8.9, 2.4))
            );

            vec2 topLeft = cellOrigin + randInCell * anchorCellPx;

            float period = mix(0.35, 1.25, hash12(cell + vec2(4.1, 7.2)));
            float duration = mix(0.08, 0.12, hash12(cell + vec2(6.6, 9.3)));
            float phaseOffset = hash12(cell + vec2(11.4, 3.5)) * period;

            float localTime = time + phaseOffset;
            float cycleIndex = floor(localTime / period);
            float cyclePhase = mod(localTime, period);

            float activeWindow = 1.0 - step(duration, cyclePhase);
            float cycleRnd = hash12(cell + vec2(cycleIndex * 13.7, cycleIndex * 5.1));
            float cycleEnabled = step(mix(0.985, 0.93, intensity), cycleRnd);
            float glitchActive = activeWindow * cycleEnabled;

            float inside =
                step(topLeft.x, fragPx.x) *
                step(topLeft.y, fragPx.y) *
                step(fragPx.x, topLeft.x + rectSizePx.x) *
                step(fragPx.y, topLeft.y + rectSizePx.y);

            float hit = glitchActive * inside;
            vec2 dirRnd = hash22(cell + cycleIndex * 3.71) * 2.0 - 1.0;
            vec2 dir = sign(dirRnd);
            float priority = hash12(cell + vec2(cycleIndex * 17.3, cycleIndex * 2.9));

            if (hit > 0.5 && priority > bestPriority) {
                bestPriority = priority;
                bestOffset = dir * strengthPx;
            }
        }
    }

    return bestOffset;
}

void main() {
    float intensity = saturate(Intensity);
    float time = Time;
    vec2 fragCoord = texCoord * OutSize;
    vec2 centerDir = normalize((fragCoord - 0.5 * OutSize) + vec2(0.0001));
    float rgbShiftPx = (10.0 + 3.0 * sin(time * 1.8)) * mix(0.35, 1.0, intensity);

    vec2 samplePxR = fragCoord + centerDir * rgbShiftPx;
    vec2 samplePxG = fragCoord;
    vec2 samplePxB = fragCoord - centerDir * rgbShiftPx;

    samplePxR += rectangleGlitchOffsetPx(samplePxR, time + 0.00, intensity);
    samplePxG += rectangleGlitchOffsetPx(samplePxG, time + 0.03, intensity);
    samplePxB += rectangleGlitchOffsetPx(samplePxB, time + 0.06, intensity);

    vec3 col;
    col.r = scenePx(samplePxR).r;
    col.g = scenePx(samplePxG).g;
    col.b = scenePx(samplePxB).b;

    fragColor = vec4(col, 1.0);
}
