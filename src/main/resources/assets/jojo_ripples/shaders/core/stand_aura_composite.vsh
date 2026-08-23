#version 150

// Adapted from KINnao087/StandAuraFx revision 6f36008 (GPL-3.0).

in vec3 Position;
in vec2 UV0;

out vec2 vUv;

void main() {
    gl_Position = vec4(Position, 1.0);
    vUv = UV0;
}
