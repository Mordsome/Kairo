#version 330
#extension GL_ARB_separate_shader_objects : require

// Motion blur: the previous frames, laid over this one at a fixed opacity.

uniform sampler2D InSampler;

layout(std140) uniform BlendConfig {
    vec4 Params; // x = opacity of the history
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = vec4(texture(InSampler, texCoord).rgb, Params.x);
}