#version 330
#extension GL_ARB_separate_shader_objects : require

// Kairo Void combat effects over a copy of the frame (the energy blades are geometry on the weapon): impact shockwaves and
// the totem pop blast. Positions are in "screen space": the centre is 0, y goes up, 1 unit = the screen height.

uniform sampler2D InSampler;

layout(std140) uniform VoidConfig {
    vec4 Info;        // x time (s), y aspect
    vec4 Accent;      // rgb accent
    vec4 Texel;       // xy 1/size, zw size
    vec4 Totem;       // x age 0..1 (< 0: none)
    vec4 Waves[4];    // x, y (0..1, y up), age 0..1 (> 1: none), strength
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

const float PI = 3.14159265;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

vec2 rot(vec2 v, float a) {
    float c = cos(a);
    float s = sin(a);
    return vec2(c * v.x - s * v.y, s * v.x + c * v.y);
}

float easeOut(float t) {
    t = clamp(t, 0.0, 1.0);
    return 1.0 - (1.0 - t) * (1.0 - t) * (1.0 - t);
}

float easeIn(float t) {
    t = clamp(t, 0.0, 1.0);
    return t * t;
}

void main() {
    vec2 uv = texCoord;
    float aspect = Info.y;
    vec2 p = (uv - 0.5) * vec2(aspect, 1.0);
    float time = Info.x;

    vec2 offset = vec2(0.0);
    float split = 0.0;
    vec3 light = vec3(0.0);
    vec3 accent = Accent.rgb;
    vec3 hot = mix(accent, vec3(1.0), 0.55);
    // The blade body is painted over the picture (so it stays coloured on a bright sky); light is added on top.
    float cover = 0.0;

    // ---- impact shockwaves ----
    for (int i = 0; i < 4; i++) {
        vec4 w = Waves[i];
        float age = w.z;
        if (age > 1.0) {
            continue;
        }
        float strength = w.w;
        vec2 c = (w.xy - 0.5) * vec2(aspect, 1.0);
        vec2 dv = p - c;
        float dist = length(dv);
        float radius = 0.02 + easeOut(age) * 0.42 * (0.6 + 0.4 * strength);
        float width = 0.055;
        float k = (dist - radius) / width;
        float fade = pow(1.0 - age, 1.5);
        float ripple = k * exp(-k * k * 2.5);
        vec2 dirv = dist > 1e-4 ? dv / dist : vec2(0.0);
        offset -= dirv * ripple * 0.04 * strength * fade;
        split += exp(-k * k * 3.0) * strength * fade;
        float ring = exp(-k * k * 7.0) * fade * fade * 0.35 * strength;
        light += mix(vec3(1.0), accent, 0.4) * ring;
        // The impact itself: a hot point with a few sharp rays.
        float flash = pow(1.0 - age, 4.0) * strength;
        float star = exp(-dist / 0.022) * 1.4 + pow(abs(cos(atan(dv.y, dv.x) * 3.0 + w.x * 40.0)), 40.0) * exp(-dist / 0.11) * 0.8;
        light += hot * star * flash;
    }

    // ---- totem pop blast ----
    float totemAge = Totem.x;
    vec3 gold = vec3(1.0, 0.78, 0.32);
    float totemLight = 0.0;
    if (totemAge >= 0.0 && totemAge <= 1.0) {
        float r = length(p);
        float a = atan(p.y, p.x);
        float env = smoothstep(0.0, 0.06, totemAge) * (1.0 - smoothstep(0.3, 1.0, totemAge));
        float rays = pow(max(0.0, sin(a * 13.0 + time * 0.7) * 0.5 + 0.5), 10.0)
                   + 0.7 * pow(max(0.0, sin(a * 21.0 - time * 0.45 + 1.3) * 0.5 + 0.5), 14.0);
        totemLight = rays * exp(-r * 1.3) * env * 1.3;
        // A slow ripple rolling outwards.
        float radius = easeOut(totemAge * 1.1) * 1.15;
        float k = (r - radius) / 0.11;
        float fade = 1.0 - totemAge;
        vec2 dirv = r > 1e-4 ? p / r : vec2(0.0);
        offset -= dirv * k * exp(-k * k * 2.0) * 0.03 * fade;
        split += exp(-k * k * 2.5) * fade * 0.8;
        totemLight += exp(-k * k * 5.0) * fade * 0.4;
    }

    // ---- sample with all the bending, colour fringes along the ripples ----
    vec2 uvOffset = offset / vec2(aspect, 1.0);
    vec2 sampleUv = uv + uvOffset;
    vec3 color;
    if (split > 0.01) {
        vec2 fringe = uvOffset * 0.6 + vec2(0.0025, 0.0) * split;
        color = vec3(texture(InSampler, sampleUv + fringe).r, texture(InSampler, sampleUv).g, texture(InSampler, sampleUv - fringe).b);
    } else {
        color = texture(InSampler, sampleUv).rgb;
    }

    if (totemAge >= 0.0 && totemAge <= 1.0) {
        float warm = 1.0 - smoothstep(0.0, 0.3, totemAge);
        color = mix(color, color * vec3(1.15, 1.05, 0.8) + gold * 0.12, warm * 0.6);
        light += gold * totemLight;
    }

    if (cover > 0.0) {
        vec3 blade = clamp(accent * 1.15 + 0.04, 0.0, 1.0);
        color = mix(color, blade, cover);
    }

    // Light adds on top, rolling off softly instead of clipping hard.
    color += light;
    color = color / (1.0 + max(color - 1.0, 0.0) * 0.6);
    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
