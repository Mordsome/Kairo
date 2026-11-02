#version 330
#extension GL_ARB_separate_shader_objects : require

// Held item outline: the item's shape (texture alpha) filled with one flat colour (the vertex colour).

#include <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
layout(location = 6) in vec2 texCoord0;

layout(location = 0) out vec4 fragColor;

void main() {
    if (texture(Sampler0, texCoord0).a < 0.1) {
        discard;
    }
    fragColor = vertexColor * ColorModulator;
}