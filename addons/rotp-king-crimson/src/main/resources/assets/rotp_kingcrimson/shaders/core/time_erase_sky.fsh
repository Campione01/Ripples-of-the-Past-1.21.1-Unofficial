#version 150

#moj_import <matrix.glsl>

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float GameTime;
uniform int PortalLayer;

in vec4 texProj0;
in vec4 vertexColor;

const mat4 SCALE_TRANSLATE = mat4(
    0.5, 0.0, 0.0, 0.25,
    0.0, 0.5, 0.0, 0.25,
    0.0, 0.0, 1.0, 0.0,
    0.0, 0.0, 0.0, 1.0
);

mat4 portal_layer(float layer) {
    mat4 translate = mat4(
        1.0, 0.0, 0.0, 17.0 / layer,
        0.0, 1.0, 0.0,
        (2.0 + layer / 1.5) * (GameTime * 1.5),
        0.0, 0.0, 1.0, 0.0,
        0.0, 0.0, 0.0, 1.0
    );
    mat2 rotate = mat2_rotate_z(radians(
        (layer * layer * 4321.0 + layer * 9.0) * 2.0));
    mat2 scale = mat2((4.5 - layer / 4.0) * 2.0);
    return mat4(scale * rotate) * translate * SCALE_TRANSLATE;
}

out vec4 fragColor;

void main() {
    vec4 sampled = textureProj(
        Sampler0,
        texProj0 * portal_layer(float(PortalLayer)));
    fragColor = sampled * vertexColor * ColorModulator;
}
