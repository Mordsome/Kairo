#version 330
#extension GL_ARB_separate_shader_objects : require

// Menu background image: samples a sub-rectangle of the image (cover fit, slow pan) and darkens it.

uniform sampler2D InSampler;

layout(std140) uniform BackgroundConfig {
    vec4 UvRect; // u0, v0, width, height of the visible part of the image
    vec4 Params; // x = darken 0..1
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    // Screen-quad texCoord has v = 0 at the bottom; image rows start at the top.
    vec2 uv = UvRect.xy + vec2(texCoord.x, 1.0 - texCoord.y) * UvRect.zw;
    vec3 color = texture(InSampler, uv).rgb;
    fragColor = vec4(color * (1.0 - Params.x), 1.0);
}
