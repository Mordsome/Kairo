#version 330
#extension GL_ARB_separate_shader_objects : require

// Glyph atlas is single-channel coverage rendered by FreeType at the exact pixel size.

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

void main() {
    float coverage = texture(Sampler0, texCoord0).r;
    float a = vertexColor.a * coverage;
    if (a <= 0.002) {
        discard;
    }
    fragColor = vec4(vertexColor.rgb * a, a);
}
