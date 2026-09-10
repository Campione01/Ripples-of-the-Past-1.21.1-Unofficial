#version 150

uniform sampler2D SurfaceColorSampler;
uniform sampler2D SurfaceDepthSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 surfaceColor = texture(SurfaceColorSampler, texCoord);
    if (surfaceColor.a <= 0.0) {
        discard;
    }
    fragColor = surfaceColor;
    gl_FragDepth = texture(SurfaceDepthSampler, texCoord).r;
}
