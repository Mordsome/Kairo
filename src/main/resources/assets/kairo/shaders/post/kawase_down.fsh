#version 330
#extension GL_ARB_separate_shader_objects : require

// Dual Kawase blur, downsample step (5 taps).

uniform sampler2D InSampler;

layout(std140) uniform BlurConfig {
    vec2 HalfTexel; // 0.5 / input size
    float Offset;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 o = HalfTexel * Offset;
    vec3 sum = texture(InSampler, texCoord).rgb * 4.0;
    sum += texture(InSampler, texCoord - o).rgb;
    sum += texture(InSampler, texCoord + o).rgb;
    sum += texture(InSampler, texCoord + vec2(o.x, -o.y)).rgb;
    sum += texture(InSampler, texCoord - vec2(o.x, -o.y)).rgb;
    fragColor = vec4(sum / 8.0, 1.0);
}
