#version 330
#extension GL_ARB_separate_shader_objects : require

// Bloom, last step: the blurred bright parts added back onto the frame (additive blending).

uniform sampler2D InSampler;

layout(std140) uniform BlendConfig {
    vec4 Params; // x = strength
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = vec4(texture(InSampler, texCoord).rgb * Params.x, 1.0);
}
