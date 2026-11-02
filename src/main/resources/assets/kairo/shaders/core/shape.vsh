#version 330
#extension GL_ARB_separate_shader_objects : require

// Antialiased SDF shapes for the GUI (rounded rects, ring sectors, line segments, the menu backdrop).
// Shape parameters ride along in spare vertex attributes (see ShapeElement.java for the packing).

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;   // xy = GUI position, z = param 0
layout(location = 1) in vec4 Color;      // fill color (or glass tint)
layout(location = 2) in vec2 UV0;        // position in the shape's own space
layout(location = 3) in ivec2 UV1;       // stroke color, packed RGBA8 into two shorts
layout(location = 4) in ivec2 UV2;       // x = shape type | flags, y = stroke width * 64
layout(location = 5) in vec2 UV3;        // params 1, 2
layout(location = 6) in float LineWidth; // param 3
layout(location = 7) in vec4 Normal;     // x = corner radius / 64, y = opacity, z = saturation - 1

layout(location = 0) out vec4 fillColor;
layout(location = 1) flat out vec4 strokeColor;
layout(location = 2) out vec2 localPos;
layout(location = 3) flat out ivec2 shapeInfo;
layout(location = 4) flat out vec4 params;
layout(location = 5) flat out vec4 extra;
layout(location = 6) out vec2 screenUv;

vec4 unpackColor(ivec2 packed) {
    int hi = packed.x & 0xFFFF;
    int lo = packed.y & 0xFFFF;
    return vec4(float((hi >> 8) & 0xFF), float(hi & 0xFF), float((lo >> 8) & 0xFF), float(lo & 0xFF)) / 255.0;
}

void main() {
    vec4 clip = ProjMat * ModelViewMat * vec4(Position.xy, 0.0, 1.0);
    gl_Position = clip;
    // Same NDC -> UV mapping the fullscreen blur passes use, so this lines up on every backend.
    screenUv = clip.xy / clip.w * 0.5 + 0.5;

    fillColor = Color;
    strokeColor = unpackColor(UV1);
    localPos = UV0;
    shapeInfo = UV2;
    params = vec4(Position.z, UV3.x, UV3.y, LineWidth);
    extra = Normal;
}
