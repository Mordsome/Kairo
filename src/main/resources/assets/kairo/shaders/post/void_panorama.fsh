#version 330
#extension GL_ARB_separate_shader_objects : require

// Kairo Void's menu background in place of Minecraft's panorama: deep space with nebula clouds in the accent
// colour, twinkling stars, a slowly turning spiral galaxy, a huge ringed planet rising from the corner, and glowing
// crystal shards drifting past at different depths. Everything moves slowly; nothing is a texture.

layout(std140) uniform PanoramaConfig {
    vec4 Info;    // x time (s), y aspect, z dim 0..1, w motion 0/1
    vec4 Accent;  // rgb accent
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

float fbm(vec2 p) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 6; i++) {
        sum += amp * noise(p);
        p = p * 2.02 + vec2(3.1, 1.7);
        amp *= 0.5;
    }
    return sum;
}

vec2 rot(vec2 v, float a) {
    float c = cos(a);
    float s = sin(a);
    return vec2(c * v.x - s * v.y, s * v.x + c * v.y);
}

vec3 stars(vec2 p, float time, float density, float scale) {
    vec2 cell = floor(p * scale);
    vec2 f = fract(p * scale) - 0.5;
    float h = hash(cell);
    if (h < 1.0 - density) {
        return vec3(0.0);
    }
    vec2 offset = (vec2(hash(cell + 3.1), hash(cell + 7.3)) - 0.5) * 0.6;
    float d = length(f - offset);
    float size = 0.04 + 0.08 * hash(cell + 1.7);
    float twinkle = 0.6 + 0.4 * sin(time * (1.0 + 3.0 * hash(cell + 9.0)) + h * 50.0);
    vec3 tint = mix(vec3(0.75, 0.85, 1.0), vec3(1.0, 0.85, 0.75), hash(cell + 4.0));
    return tint * exp(-d * d / (size * size)) * twinkle * 1.5;
}

// A diamond-shaped crystal shard with a glow; returns colour and how much it covers.
vec4 shard(vec2 p, vec2 c, float size, float spin, vec3 accent) {
    vec2 q = rot(p - c, spin);
    q.y *= 0.62;
    float d = (abs(q.x) + abs(q.y)) / size;
    float body = 1.0 - smoothstep(0.96, 1.0, d);
    float facet = step(0.0, q.x) * 0.25 + step(0.0, q.y) * 0.12;
    vec3 col = mix(accent, vec3(1.0), 0.35 + facet) * (0.55 + 0.45 * (1.0 - d));
    float edge = smoothstep(0.82, 0.97, d) * body;
    col += vec3(1.0) * edge * 0.6;
    float glow = exp(-max(d - 1.0, 0.0) * 4.0) * (1.0 - body) * 0.45;
    return vec4(col * body + accent * glow, body * 0.92);
}

void main() {
    vec2 uv = texCoord;
    float aspect = Info.y;
    float time = Info.x * Info.w;
    vec3 accent = Accent.rgb;
    vec2 p = (uv - 0.5) * vec2(aspect, 1.0);
    // A slow drift of the whole view.
    vec2 drift = vec2(sin(time * 0.021), cos(time * 0.017)) * 0.04;

    // Deep space: violet-black at the top, indigo at the bottom.
    vec3 color = mix(vec3(0.03, 0.025, 0.07), vec3(0.01, 0.008, 0.025), uv.y);
    color += accent * 0.04 * (1.0 - uv.y);

    // Nebula: two layers drifting at different speeds.
    vec2 np = p * 1.6 + drift * 2.0 + vec2(time * 0.006, 0.0);
    float n = fbm(np);
    float warp = fbm(np * 1.8 + n * 1.4 + vec2(0.0, time * 0.004));
    float band = exp(-pow((p.y + p.x * 0.35 - 0.05) * 2.4, 2.0));
    vec3 nebula = mix(accent, vec3(0.12, 0.55, 0.95), smoothstep(0.35, 0.75, warp));
    nebula = mix(nebula, vec3(0.95, 0.3, 0.6), smoothstep(0.6, 0.9, n) * 0.5);
    float density = smoothstep(0.38, 0.85, warp) * (0.25 + band);
    float dust = fbm(np * 3.3 + 7.0);
    density *= 1.0 - smoothstep(0.5, 0.72, dust) * 0.75 * band;
    color += nebula * density * 0.75;

    // Stars, in two depths.
    color += stars(p + drift * 0.5, time, 0.05, 90.0);
    color += stars(p + drift, time, 0.03, 45.0) * 1.2;

    // A spiral galaxy, top left.
    vec2 gp = p - vec2(-0.55 * aspect / 1.78, 0.28) - drift * 0.6;
    gp = rot(gp, 0.5);
    gp.y /= 0.42;
    float gr = length(gp) / 0.13;
    if (gr < 1.7) {
        float ga = atan(gp.y, gp.x) + time * 0.05;
        float arms = pow(0.5 + 0.5 * cos(2.0 * (ga - log(gr + 0.05) * 2.6)), 3.0);
        color += mix(vec3(0.6, 0.75, 1.0), accent, 0.4) * arms * exp(-gr * 2.4) * 1.3 * (1.0 - smoothstep(1.0, 1.7, gr));
        color += vec3(1.0, 0.9, 0.75) * exp(-gr * gr * 28.0) * 1.4;
    }

    // A huge ringed planet rising from the bottom right.
    vec2 pc = vec2(0.52 * aspect, -0.62) + drift * 0.3;
    vec2 pp = p - pc;
    float pr = 0.62;
    float pd = length(pp) / pr;
    vec2 rp = rot(pp, -0.32);
    float ringR = length(vec2(rp.x, rp.y / 0.2)) / pr;
    float ring = smoothstep(1.3, 1.36, ringR) * (1.0 - smoothstep(2.05, 2.15, ringR)) * (0.6 + 0.4 * sin(ringR * 40.0));
    vec3 ringCol = mix(vec3(0.85, 0.78, 0.65), accent, 0.45);
    if (rp.y > 0.0) {
        color = mix(color, ringCol * 0.8, ring * 0.7);
    }
    if (pd < 1.0) {
        float z = sqrt(1.0 - pd * pd);
        vec3 nrm = normalize(vec3(pp / pr, z));
        float light = clamp(dot(nrm, normalize(vec3(-0.6, 0.7, 0.4))), 0.0, 1.0);
        float bands = 0.5 + 0.5 * sin(pp.y / pr * 11.0 + noise(pp * 9.0 + time * 0.02) * 2.5);
        vec3 surface = mix(vec3(0.12, 0.07, 0.22), mix(vec3(0.42, 0.28, 0.58), accent, 0.3), bands);
        vec3 planet = surface * (0.06 + light);
        planet += accent * pow(1.0 - z, 3.0) * 0.9;
        float edge = 1.0 - smoothstep(0.985, 1.0, pd);
        color = mix(color, planet, edge);
    } else {
        color += accent * exp(-(pd - 1.0) * 10.0) * 0.45;
    }
    if (rp.y <= 0.0) {
        color = mix(color, ringCol, ring * 0.85);
    }

    // Crystal shards drifting across at three depths.
    for (int i = 0; i < 9; i++) {
        float fi = float(i);
        float depth = 0.4 + 0.6 * hash(vec2(fi, 3.0));
        float speed = 0.01 + 0.02 * depth;
        vec2 c = vec2(mod(hash(vec2(fi, 1.0)) * 2.4 + time * speed, 2.4) - 1.2, hash(vec2(fi, 2.0)) * 0.9 - 0.45);
        c.x *= aspect / 1.78 * 1.1;
        c.y += sin(time * 0.3 + fi) * 0.02;
        float size = 0.018 + 0.05 * depth;
        vec4 s = shard(p + drift * depth, c, size, time * (0.2 + 0.3 * hash(vec2(fi, 5.0))) + fi, mix(accent, vec3(0.5, 0.85, 1.0), hash(vec2(fi, 6.0)) * 0.5));
        color = mix(color, s.rgb, s.a * (0.4 + 0.6 * depth));
        color += s.rgb * (1.0 - s.a) * (0.4 + 0.6 * depth);
    }

    // A faint horizon glow and a vignette.
    color += accent * exp(-abs(uv.y - 0.18) * 14.0) * 0.08;
    float v = length((uv - 0.5) * vec2(aspect, 1.0)) / length(vec2(aspect, 1.0) * 0.5);
    color *= 1.0 - smoothstep(0.55, 1.15, v) * 0.6;
    color *= 1.0 - Info.z;

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
