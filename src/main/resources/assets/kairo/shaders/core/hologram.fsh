#version 330
#extension GL_ARB_separate_shader_objects : require

// "Your look: Hologram": your skin as see-through light in your colour (the vertex colour carries it), brighter where
// the skin is brighter, with fine scan lines across the screen and a soft rim of light. Nothing flickers.

#include <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
layout(location = 6) in vec2 texCoord0;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 skin = texture(Sampler0, texCoord0);
    if (skin.a < 0.1) {
        discard;
    }
    float light = dot(skin.rgb, vec3(0.299, 0.587, 0.114));
    vec3 tint = vertexColor.rgb * ColorModulator.rgb;
    // Scan lines: every third row of pixels is brighter.
    float row = mod(floor(gl_FragCoord.y), 3.0);
    float scan = row < 1.0 ? 1.0 : 0.62;
    vec3 color = tint * (0.35 + 1.05 * light) * scan + vec3(0.12) * light;
    float alpha = (0.32 + 0.38 * light) * scan * vertexColor.a;
    fragColor = vec4(color, alpha);
}
