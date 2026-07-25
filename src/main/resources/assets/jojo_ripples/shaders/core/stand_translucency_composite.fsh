#version 150

uniform sampler2D StandColorSampler;
uniform sampler2D StandDepthSampler;
uniform sampler2D SceneDepthSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 standColor = texture(StandColorSampler, texCoord);
    float standDepth = texture(StandDepthSampler, texCoord).r;
    float sceneDepth = texture(SceneDepthSampler, texCoord).r;
    if (standColor.a <= 0.0 || standDepth > sceneDepth + 0.000001) {
        discard;
    }
    fragColor = standColor;
}
