#version 330
#extension GL_ARB_separate_shader_objects : require

// Kairo Void "Cinematic kill": the world as a drawing for a moment. Lines come from where the distance jumps
// (outlines), where surfaces fold (creases) and where the colour changes sharply (details); everything else is paper.
// Runs over a copy of the finished world, before the hand is drawn, so your hand stays as it is.

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

layout(std140) uniform NoirConfig {
    vec4 Amount; // x how much (0..1), y style (0 white, 1 ink, 2 your colour), z fewer flashes (1 = grey), w unused
    vec4 Accent; // rgb accent
    vec4 View;   // x near, y far, zw size of one pixel
    vec4 Wave;   // xy the kill on screen (0..1), z how far the drawing reaches (screen heights), w brightness of its front
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

float luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

// Distance along the view for a depth value (reversed depth: 0 is the far plane / sky).
float viewZ(vec2 uv) {
    float d = texture(DepthSampler, uv).r;
    return d <= 0.0 ? View.y : View.x * View.y / (d * (View.y - View.x) + View.x);
}

void main() {
    vec2 uv = texCoord;
    vec3 color = texture(InSampler, uv).rgb;
    float amount = Amount.x;
    if (amount <= 0.001) {
        fragColor = vec4(color, 1.0);
        return;
    }
    vec2 px = View.zw;
    float z = viewZ(uv);
    float zl = viewZ(uv - vec2(px.x, 0.0));
    float zr = viewZ(uv + vec2(px.x, 0.0));
    float zd = viewZ(uv - vec2(0.0, px.y));
    float zu = viewZ(uv + vec2(0.0, px.y));
    float scale = max(z, 0.2);
    // Outlines: the distance jumps between neighbours.
    float jump = (abs(zl - z) + abs(zr - z) + abs(zu - z) + abs(zd - z)) / scale;
    float outline = smoothstep(0.035, 0.09, jump);
    // Creases: the slope changes (a block's edge, a corner) even where the distance doesn't jump.
    float bend = (abs(zl + zr - 2.0 * z) + abs(zu + zd - 2.0 * z)) / scale;
    float crease = smoothstep(0.004, 0.012, bend) * (1.0 - smoothstep(0.6, 1.0, z / View.y));
    // Details: sharp colour changes (textures, faces), drawn lighter.
    float ll = luma(texture(InSampler, uv - vec2(px.x, 0.0)).rgb);
    float lr = luma(texture(InSampler, uv + vec2(px.x, 0.0)).rgb);
    float ld = luma(texture(InSampler, uv - vec2(0.0, px.y)).rgb);
    float lu = luma(texture(InSampler, uv + vec2(0.0, px.y)).rgb);
    float detail = smoothstep(0.10, 0.30, length(vec2(lr - ll, lu - ld))) * 0.55;
    float ink = clamp(max(max(outline, crease), detail), 0.0, 1.0);
    // Far things fade into the paper a little, like a sketch.
    ink *= 1.0 - 0.55 * smoothstep(0.35, 0.95, z / View.y);

    int style = int(Amount.y + 0.5);
    bool calm = Amount.z > 0.5;
    // A hint of the original light keeps shapes readable.
    float shade = luma(color);
    vec3 paper;
    vec3 line;
    if (style == 1) {
        paper = vec3(0.03 + 0.06 * shade);
        line = vec3(1.0);
    } else if (style == 2) {
        paper = mix(vec3(1.0), Accent.rgb, 0.18) * (0.88 + 0.12 * shade);
        line = Accent.rgb * 0.35;
    } else {
        paper = vec3(0.93 + 0.07 * shade);
        line = vec3(0.0);
    }
    if (calm) {
        // Fewer flashes: no bright white screen, a calm grey drawing instead.
        paper = style == 1 ? paper : mix(vec3(0.42), paper, 0.15) * (0.9 + 0.1 * shade);
        line = style == 1 ? vec3(0.62) : line * 0.6 + vec3(0.06);
    }
    vec3 drawing = mix(paper, line, ink);
    // The drawing spreads out from the kill as a wave with an inked front, and later draws back into it.
    float aspect = View.w / View.z;
    float dist = length((uv - Wave.xy) * vec2(aspect, 1.0));
    float inside = 1.0 - smoothstep(Wave.z - 0.05, Wave.z, dist);
    float front = Wave.w * exp(-pow((dist - Wave.z) / 0.018, 2.0)) * step(0.001, Wave.z);
    vec3 frontColor = calm ? line : mix(line, Accent.rgb, 0.45);
    vec3 result = mix(color, drawing, amount * inside);
    result = mix(result, frontColor, clamp(front, 0.0, 1.0) * amount);
    fragColor = vec4(result, 1.0);
}
