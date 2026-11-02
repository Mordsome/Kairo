#version 330
#extension GL_ARB_separate_shader_objects : require

// Screen effects on the rendered world (before the GUI): colour filter, saturation/contrast, glow,
// chromatic aberration, vignette, film grain, low-health pulse and cinema bars.

uniform sampler2D InSampler;

layout(std140) uniform FxConfig {
    vec4 Grade;   // x saturation, y contrast, z filter strength, w filter id
    vec4 Effects; // x vignette, y chromatic, z grain, w glow
    vec4 Extra;   // x letterbox, y time (s), z low-health pulse 0..1, w accent vignette 0/1
    vec4 Accent;  // rgb accent colour
    vec4 Texel;   // xy = 1 / size, zw = size
    vec4 Motion;  // x speed lines, y white flash, z hit flash
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

float luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

vec3 saturate3(vec3 c, float s) {
    return mix(vec3(luma(c)), c, s);
}

vec3 contrast3(vec3 c, float k) {
    return (c - 0.5) * k + 0.5;
}

vec3 filterColor(vec3 c, int id, vec2 uv) {
    float l = luma(c);
    if (id == 1) { // cinematic: teal shadows, orange highlights
        vec3 shadows = vec3(0.0, 0.42, 0.52);
        vec3 highs = vec3(1.0, 0.62, 0.32);
        vec3 graded = c + mix(shadows - 0.25, highs - 0.6, smoothstep(0.15, 0.85, l)) * 0.28;
        return contrast3(saturate3(graded, 1.1), 1.12);
    }
    if (id == 2) { // warm
        return contrast3(c * vec3(1.1, 1.0, 0.82) + vec3(0.03, 0.01, 0.0), 1.04);
    }
    if (id == 3) { // cold
        return contrast3(c * vec3(0.85, 0.98, 1.15) + vec3(0.0, 0.01, 0.03), 1.05);
    }
    if (id == 4) { // vibrant
        return contrast3(saturate3(c, 1.45), 1.12);
    }
    if (id == 5) { // dreamy: lifted, soft, pastel pink
        vec3 soft = mix(c, vec3(1.0, 0.86, 0.94), 0.12) * 0.92 + 0.08;
        return saturate3(soft, 0.85);
    }
    if (id == 6) { // noir
        float g = luma(contrast3(c, 1.35));
        return vec3(g) * vec3(1.02, 1.0, 0.97);
    }
    if (id == 7) { // retro: faded sepia
        vec3 sepia = vec3(dot(c, vec3(0.393, 0.769, 0.189)), dot(c, vec3(0.349, 0.686, 0.168)), dot(c, vec3(0.272, 0.534, 0.131)));
        return contrast3(mix(c, sepia, 0.75), 0.88) + vec3(0.04, 0.03, 0.0);
    }
    if (id == 8) { // neon: punchy colours, highlights pushed to the accent
        vec3 punchy = contrast3(saturate3(c, 1.7), 1.15);
        return punchy + Accent.rgb * smoothstep(0.55, 1.0, l) * 0.22;
    }
    if (id == 9) { // vaporwave: pink highlights, cyan shadows
        vec3 graded = mix(vec3(0.15, 0.85, 0.95), vec3(1.0, 0.45, 0.85), smoothstep(0.1, 0.9, l));
        return saturate3(mix(c, graded * (l * 1.1 + 0.15), 0.45), 1.2);
    }
    if (id == 10) { // sunset: golden top, pink-purple bottom
        vec3 sky = mix(vec3(0.95, 0.4, 0.55), vec3(1.0, 0.75, 0.35), uv.y);
        return contrast3(c * mix(vec3(1.0), sky * 1.25, 0.35), 1.05);
    }
    if (id == 11) { // matrix
        float g = luma(c);
        return vec3(g * 0.35, g * 1.15 + 0.03, g * 0.45);
    }
    return c;
}

void main() {
    vec2 uv = texCoord;
    vec2 centred = uv - 0.5;
    float dist = length(centred * vec2(Texel.z / Texel.w, 1.0)) / length(vec2(Texel.z / Texel.w, 1.0) * 0.5);

    // Chromatic aberration: red and blue slide apart towards the edges.
    vec3 color;
    float ca = Effects.y * 0.012 * dist;
    if (ca > 0.0) {
        vec2 dir = normalize(centred + 1e-5) * ca;
        color = vec3(texture(InSampler, uv + dir).r, texture(InSampler, uv).g, texture(InSampler, uv - dir).b);
    } else {
        color = texture(InSampler, uv).rgb;
    }

    // Glow: wide ring of taps, only the bright parts bleed.
    if (Effects.w > 0.0) {
        vec3 bloom = vec3(0.0);
        float total = 0.0;
        for (int ring = 1; ring <= 2; ring++) {
            float r = float(ring) * 9.0;
            for (int i = 0; i < 8; i++) {
                float a = float(i) * 0.785398 + float(ring) * 0.4;
                vec3 s = texture(InSampler, uv + vec2(cos(a), sin(a)) * r * Texel.xy).rgb;
                float w = 1.0 / float(ring);
                bloom += max(s - 0.62, 0.0) * w;
                total += w;
            }
        }
        color += bloom / total * Effects.w * 3.2;
    }

    // Filter, then the user's saturation and contrast.
    int id = int(Grade.w + 0.5);
    if (id > 0) {
        color = mix(color, filterColor(color, id, uv), Grade.z);
    }
    color = contrast3(saturate3(color, Grade.x), Grade.y);

    // Vignette (black, or the accent), and the low-health pulse in red.
    float edge = smoothstep(0.45, 1.25, dist);
    if (Effects.x > 0.0) {
        vec3 tint = Extra.w > 0.5 ? Accent.rgb * 0.55 : vec3(0.0);
        color = mix(color, tint, edge * Effects.x * 0.85);
    }
    if (Extra.z > 0.0) {
        color = mix(color, vec3(0.75, 0.02, 0.05), smoothstep(0.35, 1.2, dist) * Extra.z * 0.6);
    }

    // Speed lines: thin streaks radiating from the centre, flickering, only towards the edges.
    if (Motion.x > 0.0) {
        float aspect = Texel.z / Texel.w;
        float angle = atan(centred.y, centred.x * aspect);
        float slot = floor(angle * 48.0);
        float frame = floor(Extra.y * 14.0);
        float on = step(0.7, hash(vec2(slot, frame)));
        float width = abs(fract(angle * 48.0) - 0.5);
        float streak = on * (1.0 - smoothstep(0.05, 0.42, width));
        float reach = smoothstep(0.35 + 0.3 * hash(vec2(slot, frame + 1.0)), 0.95, dist);
        color = mix(color, vec3(1.0), clamp(streak * reach * Motion.x * 1.1, 0.0, 0.9));
    }

    // Hit flash (your colour at the edges) and the explosion flash (white).
    if (Motion.z > 0.0) {
        color = mix(color, Accent.rgb, smoothstep(0.35, 1.1, dist) * Motion.z * 0.8);
    }
    if (Motion.y > 0.0) {
        color = mix(color, vec3(1.0, 0.97, 0.9), Motion.y * 0.85);
    }

    // Film grain.
    if (Effects.z > 0.0) {
        float n = hash(uv * Texel.zw + fract(Extra.y * 7.3) * 113.0) - 0.5;
        color += n * Effects.z * 0.14;
    }

    // Cinema bars (texCoord v = 0 at the bottom).
    float bar = Extra.x * 0.12;
    if (uv.y < bar || uv.y > 1.0 - bar) {
        color = vec3(0.0);
    }

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
