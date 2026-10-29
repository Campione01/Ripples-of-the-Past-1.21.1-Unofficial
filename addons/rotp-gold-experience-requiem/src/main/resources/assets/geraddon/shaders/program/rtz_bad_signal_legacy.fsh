#version 150
// Written by AI.

uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;
uniform float Time;
uniform float Intensity;

in vec2 texCoord;
out vec4 fragColor;

#define u_texture DiffuseSampler
#define u_resolution OutSize
#define u_time Time
#define u_intensity Intensity

float saturate(float value) {
    return clamp(value, 0.0, 1.0);
}

float hash11(float value) {
    return fract(sin(value * 127.1) * 43758.5453123);
}

vec2 clampUv(vec2 uv) {
    return clamp(uv, vec2(0.0), vec2(1.0));
}

vec2 frameJitter(float time, float intensity) {
    float rate = mix(8.0, 20.0, intensity);
    float stepped = floor(time * rate);
    float jitterX = hash11(stepped + 1.7) * 2.0 - 1.0;
    float jitterY = hash11(stepped + 7.9) * 2.0 - 1.0;

    return vec2(
        jitterX * (0.0005 + 0.0038 * intensity),
        jitterY * (0.0001 + 0.0010 * intensity)
    );
}

float bandOffset(float bandIndex, float time, float intensity) {
    float steppedTime = floor(time * mix(7.0, 16.0, intensity));
    float base = hash11(bandIndex * 13.17 + steppedTime * 0.73) * 2.0 - 1.0;
    float grouped = hash11(floor(bandIndex * 0.25) * 5.91 + steppedTime * 0.31) * 2.0 - 1.0;
    float sparseMask = step(0.58 - 0.18 * intensity, hash11(bandIndex * 3.41 + steppedTime * 0.43));
    float clusterMask = step(0.50 - 0.16 * intensity, hash11(floor(bandIndex * 0.33) * 7.27 + steppedTime * 0.19));
    float activeMask = max(sparseMask, clusterMask * 0.85);
    float amplitude = 0.0003 + 0.0100 * intensity * intensity;
    return (base * 0.65 + grouped * 0.35) * amplitude * activeMask;
}

float tearingMaskA(float bandIndex, float bandCount, float time, float intensity) {
    float tearTime = floor(time * 1.7);
    float center = floor(hash11(tearTime + 2.1) * bandCount);
    float width = mix(4.0, 14.0, intensity);
    float distanceToCenter = abs(bandIndex - center);
    float pulse = 0.45 + 0.55 * hash11(tearTime + 9.7);
    return (1.0 - smoothstep(width, width + 1.5, distanceToCenter)) * pulse;
}

float tearingMaskB(float bandIndex, float bandCount, float time, float intensity) {
    float tearTime = floor(time * 1.3);
    float center = floor(hash11(tearTime + 5.4) * bandCount);
    float width = mix(6.0, 18.0, intensity);
    float distanceToCenter = abs(bandIndex - center);
    float pulse = 0.35 + 0.65 * hash11(tearTime + 12.8);
    return (1.0 - smoothstep(width, width + 1.5, distanceToCenter)) * pulse;
}

float tearingOffset(float bandIndex, float bandCount, float time, float intensity, out float tearMask) {
    float maskA = tearingMaskA(bandIndex, bandCount, time, intensity);
    float maskB = tearingMaskB(bandIndex, bandCount, time, intensity);
    float tearTime = floor(time * mix(5.0, 11.0, intensity));
    float offsetA = (hash11(tearTime + 15.1) * 2.0 - 1.0) * (0.004 + 0.040 * intensity);
    float offsetB = (hash11(tearTime + 23.6) * 2.0 - 1.0) * (0.003 + 0.030 * intensity);

    tearMask = max(maskA, maskB);
    return offsetA * maskA + offsetB * maskB;
}

float trackingMask(float bandIndex, float bandCount, float time, float intensity) {
    float center = fract(1.0 - time * (0.10 + 0.05 * intensity)) * bandCount;
    float width = mix(5.0, 16.0, intensity);
    float distanceToCenter = abs(bandIndex - center);
    return 1.0 - smoothstep(width, width + 2.0, distanceToCenter);
}

void main() {
    float intensity = saturate(u_intensity);
    vec2 uv = texCoord;
    vec2 jitter = frameJitter(u_time, intensity);
    vec2 jitteredUv = clampUv(uv + jitter);

    float bandCount = mix(180.0, 420.0, intensity);
    float bandIndex = floor(jitteredUv.y * bandCount);
    float stripOffset = bandOffset(bandIndex, u_time, intensity);

    float tearMask;
    float tearOffset = tearingOffset(bandIndex, bandCount, u_time, intensity, tearMask);

    float trackMask = trackingMask(bandIndex, bandCount, u_time, intensity);
    float trackStepTime = floor(u_time * mix(9.0, 15.0, intensity));
    float trackDirection = hash11(floor(bandIndex * 0.5) + trackStepTime + 3.4) * 2.0 - 1.0;
    float trackOffset = trackMask * trackDirection * (0.001 + 0.012 * intensity);

    vec2 baseUv = clampUv(jitteredUv + vec2(stripOffset + tearOffset + trackOffset, 0.0));

    float rgbOffset = (0.0004 + 0.0024 * intensity) * (1.0 + 0.45 * tearMask + 0.35 * trackMask);
    vec2 rgbShift = vec2(rgbOffset, 0.0);

    float red = texture(u_texture, clampUv(baseUv + rgbShift)).r;
    float green = texture(u_texture, baseUv).g;
    float blue = texture(u_texture, clampUv(baseUv - rgbShift)).b;
    vec3 color = vec3(red, green, blue);

    float scanlineStrength = 0.022 + 0.070 * intensity;
    float scanlines = 1.0 - scanlineStrength * (0.5 + 0.5 * sin(baseUv.y * u_resolution.y * 3.14159265));
    float trackingBrightness = 1.0 + trackMask * (0.035 - 0.020 * hash11(trackStepTime + 19.1));
    float flicker = 1.0 + (0.003 + 0.012 * intensity) * (0.6 * sin(u_time * 10.0) + 0.4 * sin(u_time * 21.0 + 1.3));

    color *= scanlines * trackingBrightness * flicker;
    fragColor = vec4(color, 1.0);
}
