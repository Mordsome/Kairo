#version 330
#extension GL_ARB_separate_shader_objects : require

// Bloom, step 1: keep only the bright parts of the frame (soft threshold), at half size.

uniform sampler2D InSampler;

layout(std140) uniform BlurConfig {
    vec2 HalfTexel; // 0.5 / input size
    float Offset;   // here: the brightness threshold
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 o = HalfTexel;
    vec3 c = texture(InSampler, texCoord + vec2(-o.x, -o.y)).rgb;
    c += texture(InSampler, texCoord + vec2(o.x, -o.y)).rgb;
    c += texture(InSampler, texCoord + vec2(-o.x, o.y)).rgb;
    c += texture(InSampler, texCoord + vec2(o.x, o.y)).rgb;
    c *= 0.25;
    float threshold = Offset;
    float knee = 0.25;
    float bright = max(c.r, max(c.g, c.b));
    float soft = clamp(bright - threshold + knee, 0.0, 2.0 * knee);
    soft = soft * soft / (4.0 * knee + 1e-4);
    float keep = max(soft, bright - threshold) / max(bright, 1e-4);
    fragColor = vec4(c * keep, 1.0);
}
