#version 150

uniform float Time;
uniform float Alpha;
uniform vec3 CameraLook;
uniform vec3 CameraUp;
uniform vec3 CameraRight;

in vec3 vLocalPos;
out vec4 fragColor;

const float PI = 3.14159265359;
const float BASE_ROT_SPEED = 0.036 * 3;
const float BASE_EVOLVE_SPEED = 0.018;

float hash3(vec3 p) {
    return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453123);
}

float valueNoise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);

    float n000 = hash3(i + vec3(0.0, 0.0, 0.0));
    float n100 = hash3(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash3(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash3(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash3(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash3(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash3(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash3(i + vec3(1.0, 1.0, 1.0));

    float nx00 = mix(n000, n100, f.x);
    float nx10 = mix(n010, n110, f.x);
    float nx01 = mix(n001, n101, f.x);
    float nx11 = mix(n011, n111, f.x);
    float nxy0 = mix(nx00, nx10, f.y);
    float nxy1 = mix(nx01, nx11, f.y);
    return mix(nxy0, nxy1, f.z);
}

float fbm(vec3 p) {
    // Lighter 3-octave FBM. The old 5-octave version was too expensive
    // once multiplied across 10 nebula layers.
    float value = 0.0;
    value += 0.56 * valueNoise3(p);
    p *= 2.03;
    value += 0.29 * valueNoise3(p);
    p *= 2.07;
    value += 0.15 * valueNoise3(p);
    return value;
}

mat3 rotY(float a) {
    float c = cos(a);
    float s = sin(a);
    return mat3(
         c, 0.0, s,
       0.0, 1.0, 0.0,
        -s, 0.0, c
    );
}

float nebulaMask(vec3 dir, float spatialScale, vec3 seed, float evolution, float low, float high) {
    vec3 samplePos = dir * spatialScale + seed;
    samplePos.z += evolution;
    float base = fbm(samplePos);
    float detail = valueNoise3(samplePos * 1.38 + vec3(3.6, -2.7, 1.9));
    float combined = mix(base, detail, 0.24);
    return smoothstep(low, high, combined);
}

vec3 nebulaTint(vec3 dir, vec3 colorA, vec3 colorB, vec3 accentColor, float tintScale, float timeShift) {
    // Use two cheap noise samples instead of two more FBM evaluations.
    vec3 tintPos = dir * tintScale + vec3(timeShift * 0.85, -timeShift * 0.55, timeShift * 0.35);
    float blendAB = smoothstep(0.24, 0.76, valueNoise3(tintPos + vec3(4.2, -1.3, 2.6)));
    float blendAccent = smoothstep(0.34, 0.74, valueNoise3(tintPos * 1.52 + vec3(-3.4, 5.1, -2.2)));
    vec3 mixed = mix(colorA, colorB, blendAB);
    return mix(mixed, accentColor, blendAccent * 0.50);
}

float getStarNoise(vec2 pos);
vec2 getStarCoordFromDir(vec3 dir, float sphereness, vec2 lowerHemisphereOffset);
vec3 sampleNebulaGhost(vec3 dir, float time);
float sampleStarField(vec3 dir, float time);

vec3 applyLightGlitch(vec3 baseColor, vec3 dir, float time, float sceneEnergy, float starStrength) {
    vec2 coord = getStarCoordFromDir(dir, 0.72, vec2(9.1, 5.7));
    float tCell = floor(time * 7.0);

    // Two sparse horizontal glitch slice masks.
    float sliceA = smoothstep(0.935, 0.995, getStarNoise(vec2(floor(coord.y * 42.0), tCell + 3.0)));
    float sliceB = smoothstep(0.950, 0.998, getStarNoise(vec2(floor(coord.y * 67.0) + 11.0, tCell + 17.0)));
    float persistent = smoothstep(0.58, 0.92, getStarNoise(vec2(floor(coord.y * 24.0) + 31.0, floor(tCell * 0.5) + 9.0)));
    float glitchMask = max(persistent * 0.32, max(sliceA * 0.85, sliceB * 0.60)) * (0.40 + 0.60 * sceneEnergy);

    // Real directional RGB split. Use two lightweight ghost resamples instead of
    // only tinting the existing color, so the channel offset is actually visible.
    vec3 splitAxis = vec3(-dir.z, 0.0, dir.x);
    float splitAxisLen = length(splitAxis);
    if (splitAxisLen < 0.001) {
        splitAxis = vec3(1.0, 0.0, 0.0);
    } else {
        splitAxis /= splitAxisLen;
    }

    // Push the split much harder on bright star pixels so the sky gets a
    // TikTok-like red/cyan separation without over-smearing the whole nebula.
    float starBias = clamp(starStrength * 0.085, 0.0, 1.0);
    float baseSplit = 0.14 + 0.22 * starBias + 0.07 * sceneEnergy;
    float splitAmount = 0.010 + 0.030 * starBias + 0.010 * sceneEnergy + 0.016 * glitchMask;

    vec3 nebulaGhostR = sampleNebulaGhost(normalize(dir + splitAxis * (splitAmount * 0.70)), time);
    vec3 nebulaGhostC = sampleNebulaGhost(normalize(dir - splitAxis * (splitAmount * 0.70)), time);

    float starGhostR = sampleStarField(normalize(dir + splitAxis * (splitAmount * 1.60)), time);
    float starGhostC = sampleStarField(normalize(dir - splitAxis * (splitAmount * 1.60)), time);

    // Build the split from actual resampled scene content instead of tinting the final color.
    vec3 shifted = baseColor * (0.84 - 0.18 * starBias);
    shifted += vec3(nebulaGhostR.r, 0.0, 0.0) * (0.16 + 0.10 * sceneEnergy);
    shifted += vec3(0.0, nebulaGhostC.g, nebulaGhostC.b) * (0.16 + 0.10 * sceneEnergy);
    // Lower the opacity of the red/cyan star ghosts so the split stays readable
    // without overpowering the original white star field.
    shifted += vec3(starGhostR, 0.0, 0.0) * (0.42 + 0.30 * starBias);
    shifted += vec3(0.0, starGhostC * 0.82, starGhostC) * (0.42 + 0.30 * starBias);

    float barNoise = getStarNoise(vec2(tCell * 0.37 + floor(coord.y * 14.0), floor(coord.x * 9.0) + 21.0));
    vec3 barTint = mix(vec3(1.00, 0.18, 0.74), vec3(0.18, 0.82, 1.00), barNoise);
    vec3 glitchTint = shifted + barTint * (glitchMask * 0.16);

    return mix(baseColor, glitchTint, min(0.82, baseSplit + glitchMask * 0.22));
}

float getStarNoise(vec2 pos) {
    return fract(sin(dot(pos, vec2(12.9898, 4.1414))) * 43758.54953);
}

vec2 getStarCoordFromDir(vec3 dir, float sphereness, vec2 lowerHemisphereOffset) {
    vec3 w = normalize(dir);
    float lowerHemisphere = step(w.y, 0.0);
    float denom = abs(w.y) + length(w.xz) * sphereness;
    vec2 coord = w.xz / max(0.18, denom);

    if (lowerHemisphere > 0.5) {
        coord = vec2(-coord.x, coord.y) + lowerHemisphereOffset;
    }

    return coord;
}

float complementaryStarBand(
    vec3 dir,
    float sphereness,
    float density,
    float threshold,
    float quantizeFactor,
    float brightness
) {
    vec2 starCoord = getStarCoordFromDir(dir, sphereness, vec2(17.13, 11.87));
    starCoord *= density;
    starCoord = floor(starCoord * quantizeFactor) / quantizeFactor;

    float star = 1.0;
    star *= getStarNoise(starCoord);
    star *= getStarNoise(starCoord + vec2(0.10, 0.10));
    star *= getStarNoise(starCoord + vec2(0.23, 0.23));
    star = max(0.0, star - threshold);
    star *= star;

    return star * brightness;
}

vec3 sampleNebulaGhost(vec3 dir, float time) {
    // Reduced-cost nebula resample used only for the RGB split pass.
    vec3 layerADir = rotY(-time * (BASE_ROT_SPEED / 2.0)) * dir;
    vec3 layerBDir = rotY(-time * (BASE_ROT_SPEED / 5.0)) * dir;
    vec3 layerCDir = rotY(-time * (BASE_ROT_SPEED / 8.0)) * dir;

    float evolveA = time * (BASE_EVOLVE_SPEED / 2.0);
    float evolveB = time * (BASE_EVOLVE_SPEED / 5.0);
    float evolveC = time * (BASE_EVOLVE_SPEED / 8.0);

    float layerA = nebulaMask(layerADir, 5.0, vec3(-4.1, 2.4, -1.0), evolveA, 0.51, 0.85);
    float layerB = nebulaMask(layerBDir, 3.9, vec3(3.0, -5.2, 4.6), evolveB, 0.48, 0.83);
    float layerC = nebulaMask(layerCDir, 2.9, vec3(-1.7, 3.6, 6.0), evolveC, 0.45, 0.82);

    vec3 color = vec3(0.0);
    color += nebulaTint(layerADir, vec3(0.60, 0.12, 0.88), vec3(0.90, 0.16, 0.74), vec3(0.18, 0.84, 0.92), 2.0, evolveA) * layerA * 0.30;
    color += nebulaTint(layerBDir, vec3(0.74, 0.14, 0.88), vec3(0.98, 0.18, 0.60), vec3(0.24, 0.56, 1.00), 1.7, evolveB) * layerB * 0.24;
    color += nebulaTint(layerCDir, vec3(0.62, 0.16, 0.80), vec3(0.86, 0.12, 0.94), vec3(0.26, 0.86, 0.88), 1.4, evolveC) * layerC * 0.20;

    return color;
}

float sampleStarField(vec3 dir, float time) {
    vec3 layer1Dir = rotY(-time * (BASE_ROT_SPEED / 1.0)) * dir;
    vec3 layer2Dir = rotY(-time * (BASE_ROT_SPEED / 2.0)) * dir;
    vec3 layer3Dir = rotY(-time * (BASE_ROT_SPEED / 3.0)) * dir;
    vec3 layer4Dir = rotY(-time * (BASE_ROT_SPEED / 4.0)) * dir;
    vec3 layer5Dir = rotY(-time * (BASE_ROT_SPEED / 5.0)) * dir;
    vec3 layer6Dir = rotY(-time * (BASE_ROT_SPEED / 6.0)) * dir;
    vec3 layer7Dir = rotY(-time * (BASE_ROT_SPEED / 7.0)) * dir;
    vec3 layer8Dir = rotY(-time * (BASE_ROT_SPEED / 8.0)) * dir;
    vec3 layer9Dir = rotY(-time * (BASE_ROT_SPEED / 9.0)) * dir;
    vec3 layer10Dir = rotY(-time * (BASE_ROT_SPEED / 10.0)) * dir;

    float stars1 = complementaryStarBand(layer1Dir, 0.76, 0.26, 0.64, 1280.0, 26.0);
    float stars2 = complementaryStarBand(layer2Dir, 0.74, 0.23, 0.66, 1152.0, 22.0);
    float stars3 = complementaryStarBand(layer3Dir, 0.72, 0.20, 0.69, 1024.0, 18.0);
    float stars4 = complementaryStarBand(layer4Dir, 0.70, 0.18, 0.72, 960.0, 15.0);
    float stars5 = complementaryStarBand(layer5Dir, 0.69, 0.16, 0.74, 896.0, 13.0);
    float stars6 = complementaryStarBand(layer6Dir, 0.68, 0.145, 0.76, 832.0, 11.0);
    float stars7 = complementaryStarBand(layer7Dir, 0.67, 0.13, 0.78, 768.0, 9.5);
    float stars8 = complementaryStarBand(layer8Dir, 0.66, 0.118, 0.80, 704.0, 8.0);
    float stars9 = complementaryStarBand(layer9Dir, 0.65, 0.106, 0.82, 640.0, 6.8);
    float stars10 = complementaryStarBand(layer10Dir, 0.64, 0.095, 0.84, 576.0, 5.8);
    return stars1 + stars2 + stars3 + stars4 + stars5
        + stars6 + stars7 + stars8 + stars9 + stars10;
}

void main() {
    vec3 localDir = normalize(vLocalPos);
    vec3 dir = normalize(
        CameraRight * localDir.x +
        CameraUp * localDir.y +
        CameraLook * (-localDir.z)
    );

    vec3 color = vec3(0.01, 0.01, 0.03);
    vec3 layer1Dir = rotY(-Time * (BASE_ROT_SPEED / 1.0)) * dir;
    vec3 layer2Dir = rotY(-Time * (BASE_ROT_SPEED / 2.0)) * dir;
    vec3 layer3Dir = rotY(-Time * (BASE_ROT_SPEED / 3.0)) * dir;
    vec3 layer4Dir = rotY(-Time * (BASE_ROT_SPEED / 4.0)) * dir;
    vec3 layer5Dir = rotY(-Time * (BASE_ROT_SPEED / 5.0)) * dir;
    vec3 layer6Dir = rotY(-Time * (BASE_ROT_SPEED / 6.0)) * dir;
    vec3 layer7Dir = rotY(-Time * (BASE_ROT_SPEED / 7.0)) * dir;
    vec3 layer8Dir = rotY(-Time * (BASE_ROT_SPEED / 8.0)) * dir;
    vec3 layer9Dir = rotY(-Time * (BASE_ROT_SPEED / 9.0)) * dir;
    vec3 layer10Dir = rotY(-Time * (BASE_ROT_SPEED / 10.0)) * dir;

    float evolve1 = Time * (BASE_EVOLVE_SPEED / 1.0);
    float evolve2 = Time * (BASE_EVOLVE_SPEED / 2.0);
    float evolve3 = Time * (BASE_EVOLVE_SPEED / 3.0);
    float evolve4 = Time * (BASE_EVOLVE_SPEED / 4.0);
    float evolve5 = Time * (BASE_EVOLVE_SPEED / 5.0);
    float evolve6 = Time * (BASE_EVOLVE_SPEED / 6.0);
    float evolve7 = Time * (BASE_EVOLVE_SPEED / 7.0);
    float evolve8 = Time * (BASE_EVOLVE_SPEED / 8.0);
    float evolve9 = Time * (BASE_EVOLVE_SPEED / 9.0);
    float evolve10 = Time * (BASE_EVOLVE_SPEED / 10.0);

    float layer1 = nebulaMask(layer1Dir, 5.4, vec3(2.5, -3.2, 1.2), evolve1, 0.52, 0.86);
    float layer2 = nebulaMask(layer2Dir, 5.0, vec3(-4.1, 2.4, -1.0), evolve2, 0.51, 0.85);
    float layer3 = nebulaMask(layer3Dir, 4.6, vec3(5.4, 1.4, 3.1), evolve3, 0.50, 0.84);
    float layer4 = nebulaMask(layer4Dir, 4.2, vec3(-2.8, 4.9, 5.0), evolve4, 0.49, 0.84);
    float layer5 = nebulaMask(layer5Dir, 3.9, vec3(3.0, -5.2, 4.6), evolve5, 0.48, 0.83);
    float layer6 = nebulaMask(layer6Dir, 3.5, vec3(-5.6, -1.8, 2.3), evolve6, 0.47, 0.83);
    float layer7 = nebulaMask(layer7Dir, 3.2, vec3(4.8, 5.1, -2.4), evolve7, 0.46, 0.82);
    float layer8 = nebulaMask(layer8Dir, 2.9, vec3(-1.7, 3.6, 6.0), evolve8, 0.45, 0.82);
    float layer9 = nebulaMask(layer9Dir, 2.6, vec3(6.1, -4.4, -1.9), evolve9, 0.44, 0.81);
    float layer10 = nebulaMask(layer10Dir, 2.3, vec3(-3.5, -5.7, 4.2), evolve10, 0.43, 0.81);

    vec3 layer1Color = nebulaTint(layer1Dir, vec3(0.56, 0.10, 0.84), vec3(0.96, 0.18, 0.66), vec3(0.20, 0.62, 1.00), 2.2, evolve1);
    vec3 layer2Color = nebulaTint(layer2Dir, vec3(0.60, 0.12, 0.88), vec3(0.90, 0.16, 0.74), vec3(0.18, 0.84, 0.92), 2.0, evolve2);
    vec3 layer3Color = nebulaTint(layer3Dir, vec3(0.68, 0.12, 0.98), vec3(0.98, 0.20, 0.72), vec3(0.16, 0.52, 1.00), 1.9, evolve3);
    vec3 layer4Color = nebulaTint(layer4Dir, vec3(0.54, 0.10, 0.82), vec3(0.84, 0.16, 0.78), vec3(0.24, 0.90, 0.96), 1.8, evolve4);
    vec3 layer5Color = nebulaTint(layer5Dir, vec3(0.74, 0.14, 0.88), vec3(0.98, 0.18, 0.60), vec3(0.24, 0.56, 1.00), 1.7, evolve5);
    vec3 layer6Color = nebulaTint(layer6Dir, vec3(0.60, 0.14, 0.94), vec3(0.92, 0.18, 0.72), vec3(0.34, 0.94, 0.98), 1.6, evolve6);
    vec3 layer7Color = nebulaTint(layer7Dir, vec3(0.72, 0.14, 0.96), vec3(0.92, 0.18, 0.80), vec3(0.18, 0.66, 1.00), 1.5, evolve7);
    vec3 layer8Color = nebulaTint(layer8Dir, vec3(0.62, 0.16, 0.80), vec3(0.86, 0.12, 0.94), vec3(0.26, 0.86, 0.88), 1.4, evolve8);
    vec3 layer9Color = nebulaTint(layer9Dir, vec3(0.52, 0.10, 0.94), vec3(0.88, 0.18, 0.84), vec3(0.16, 0.76, 1.00), 1.3, evolve9);
    vec3 layer10Color = nebulaTint(layer10Dir, vec3(0.78, 0.16, 0.72), vec3(0.94, 0.18, 0.88), vec3(0.30, 0.90, 0.94), 1.2, evolve10);

    color += layer1Color * layer1 * 0.28;
    color += layer2Color * layer2 * 0.32;
    color += layer3Color * layer3 * 0.38;
    color += layer4Color * layer4 * 0.42;
    color += layer5Color * layer5 * 0.24;
    color += layer6Color * layer6 * 0.28;
    color += layer7Color * layer7 * 0.31;
    color += layer8Color * layer8 * 0.25;
    color += layer9Color * layer9 * 0.27;
    color += layer10Color * layer10 * 0.19;

    float stars = sampleStarField(dir, Time);
    vec3 starColor = vec3(1.0, 1.0, 1.0) * (stars * 1.25);
    color += starColor;

    float haze = 0.05
        + 0.05 * layer10
        + 0.06 * layer9
        + 0.07 * layer8
        + 0.08 * layer7
        + 0.09 * layer6
        + 0.10 * layer5
        + 0.11 * layer4
        + 0.12 * layer3
        + 0.13 * layer2
        + 0.14 * layer1;
    color += vec3(0.02, 0.03, 0.05) * haze;

    float sceneEnergy = clamp(
        0.11 * layer1 + 0.10 * layer2 + 0.10 * layer3 + 0.09 * layer4 + 0.09 * layer5
        + 0.08 * layer6 + 0.07 * layer7 + 0.06 * layer8 + 0.05 * layer9 + 0.05 * layer10
        + min(0.22, stars * 0.010),
        0.0,
        1.0
    );
    color = applyLightGlitch(color, dir, Time, sceneEnergy, stars);

    color = pow(color, vec3(0.85));
    fragColor = vec4(color, clamp(Alpha, 0.0, 1.0));
}
