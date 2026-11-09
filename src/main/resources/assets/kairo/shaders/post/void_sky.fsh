#version 330
#extension GL_ARB_separate_shader_objects : require

// Kairo Void sky and depth fog, over a copy of the finished world (before the hand is drawn).
// Sky pixels are where nothing wrote depth (reversed depth is cleared to 0). Everything is computed from the
// view direction, so it stays fixed in the world as you look around.

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;
uniform sampler2D SkyImage;

layout(std140) uniform SkyConfig {
    mat4 InvViewProj; // clip space -> camera-relative world space
    vec4 Sun;         // xyz direction of the sun, w time (s)
    vec4 SkyCol;      // rgb vanilla sky colour, w night 0..1
    vec4 Glow;        // rgb sunrise/sunset colour, a strength
    vec4 Fog;         // rgb vanilla fog colour, w render distance (blocks)
    vec4 Accent;      // rgb accent, w clear weather 0..1
    vec4 Flags;       // cosmic sky, planet, shooting stars, aurora
    vec4 Flags2;      // sunsets, depth fog 0..1, quality 0..3, star angle
    vec4 View;        // x camera height, y near, z far, w aspect
    vec4 AtmoLow;     // atmosphere: sky colour at the horizon, w how much it takes over
    vec4 AtmoHigh;    // sky colour overhead, w forces the cosmic night sky
    vec4 AtmoFog;     // fog colour, w density per block
    vec4 AtmoGrade;   // colour multiply, w saturation
    vec4 AtmoFx;      // x snow on the ground, y caustics, z feature (1 blood moon, 2 synth sun), w lightning flash
    vec4 AtmoMisc;    // x strength, yz camera x/z (wrapped)
    vec4 SkyImg;      // custom sky picture: x how much shows, y brightness, z turn (radians), w keep sun/moon/stars
    vec4 AuroraCfg;   // x style (0 curtains, 1 ribbons), y colour (0 classic, 1 accent, 2 rainbow, 3 sunset), z brightness
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

const float PI = 3.14159265;

float hash1(float n) {
    return fract(sin(n) * 43758.5453);
}

float hash3(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.x + p.y) * p.z);
}

float noise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float n000 = hash3(i);
    float n100 = hash3(i + vec3(1, 0, 0));
    float n010 = hash3(i + vec3(0, 1, 0));
    float n110 = hash3(i + vec3(1, 1, 0));
    float n001 = hash3(i + vec3(0, 0, 1));
    float n101 = hash3(i + vec3(1, 0, 1));
    float n011 = hash3(i + vec3(0, 1, 1));
    float n111 = hash3(i + vec3(1, 1, 1));
    return mix(mix(mix(n000, n100, f.x), mix(n010, n110, f.x), f.y), mix(mix(n001, n101, f.x), mix(n011, n111, f.x), f.y), f.z);
}

float fbm(vec3 p, int octaves) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 6; i++) {
        if (i >= octaves) {
            break;
        }
        sum += amp * noise3(p);
        p = p * 2.03 + vec3(1.7, 9.2, 3.1);
        amp *= 0.5;
    }
    return sum;
}

vec3 rotY(vec3 v, float a) {
    float c = cos(a);
    float s = sin(a);
    return vec3(c * v.x + s * v.z, v.y, -s * v.x + c * v.z);
}

vec3 rotX(vec3 v, float a) {
    float c = cos(a);
    float s = sin(a);
    return vec3(v.x, c * v.y - s * v.z, s * v.y + c * v.z);
}

// A tangent frame around a direction, for drawing flat things (galaxy, planet) on the sky.
void frame(vec3 n, out vec3 t, out vec3 b) {
    vec3 up = abs(n.y) < 0.95 ? vec3(0, 1, 0) : vec3(1, 0, 0);
    t = normalize(cross(up, n));
    b = cross(n, t);
}

// ---- night sky pieces ----

vec3 stars(vec3 dir, float time, float density) {
    vec3 p = dir * 220.0;
    vec3 cell = floor(p);
    float h = hash3(cell);
    if (h < 1.0 - density) {
        return vec3(0.0);
    }
    vec3 centre = cell + 0.5 + (vec3(hash3(cell + 3.1), hash3(cell + 7.7), hash3(cell + 1.3)) - 0.5) * 0.6;
    float d = length(p - centre);
    float size = 0.16 + 0.3 * pow(hash3(cell + 5.0), 3.0);
    float twinkle = 0.65 + 0.35 * sin(time * (1.5 + 3.0 * hash3(cell + 9.0)) + h * 40.0);
    float core = exp(-d * d / (size * size)) * twinkle;
    vec3 tint = mix(vec3(0.75, 0.85, 1.0), vec3(1.0, 0.85, 0.7), hash3(cell + 2.0));
    return tint * core * 2.2;
}

vec3 nebula(vec3 dir, float time, vec3 accent, int octaves) {
    // Concentrated along a tilted band across the sky, like a milky way.
    vec3 bandNormal = normalize(vec3(0.35, 0.55, 0.76));
    float band = exp(-pow(dot(dir, bandNormal) * 2.6, 2.0));
    vec3 q = dir * 2.2 + vec3(0.0, 0.0, time * 0.004);
    float n = fbm(q, octaves);
    float warp = fbm(q * 1.7 + n * 1.5, octaves);
    float dust = fbm(q * 3.5 + 11.0, max(octaves - 1, 2));
    vec3 c1 = accent;
    vec3 c2 = vec3(0.10, 0.55, 0.85);
    vec3 c3 = vec3(0.95, 0.30, 0.55);
    vec3 col = mix(c1, c2, smoothstep(0.35, 0.7, warp));
    col = mix(col, c3, smoothstep(0.55, 0.85, n) * 0.6);
    float density = smoothstep(0.35, 0.85, warp) * (0.25 + band * 0.9);
    // Dark dust lanes cut through it.
    density *= 1.0 - smoothstep(0.55, 0.75, dust) * 0.7 * band;
    return col * density * 0.55;
}

vec3 galaxy(vec3 dir, float time, vec3 accent) {
    vec3 g = normalize(vec3(-0.55, 0.62, 0.55));
    float facing = dot(dir, g);
    if (facing < 0.93) {
        return vec3(0.0);
    }
    vec3 t;
    vec3 b;
    frame(g, t, b);
    vec2 uv = vec2(dot(dir, t), dot(dir, b)) / facing;
    // Tilted disc.
    float tilt = 0.5;
    float ca = cos(tilt);
    float sa = sin(tilt);
    uv = vec2(ca * uv.x - sa * uv.y, sa * uv.x + ca * uv.y);
    uv.y /= 0.42;
    float r = length(uv) / 0.16;
    if (r > 1.6) {
        return vec3(0.0);
    }
    float a = atan(uv.y, uv.x) + time * 0.03;
    float arms = pow(0.5 + 0.5 * cos(2.0 * (a - log(r + 0.05) * 2.6)), 3.0);
    float disc = exp(-r * 2.6);
    float core = exp(-r * r * 30.0);
    float speckle = noise3(vec3(uv * 90.0, 3.0));
    vec3 armCol = mix(vec3(0.55, 0.75, 1.0), accent, 0.45);
    vec3 col = armCol * arms * disc * (0.6 + 0.6 * speckle) * 1.6 + vec3(1.0, 0.9, 0.75) * core * 1.8;
    return col * (1.0 - smoothstep(1.0, 1.6, r));
}

// Returns rgb and how much it covers what's behind (the planet is solid).
vec4 planet(vec3 dir, vec3 lightDir, vec3 accent) {
    vec3 c = normalize(vec3(0.62, 0.30, -0.72));
    float facing = dot(dir, c);
    if (facing < 0.97) {
        return vec4(0.0);
    }
    vec3 t;
    vec3 b;
    frame(c, t, b);
    vec2 uv = vec2(dot(dir, t), dot(dir, b)) / facing;
    float radius = 0.075;
    float r = length(uv) / radius;
    vec4 result = vec4(0.0);
    // The ring: a flat ellipse, hidden behind the planet's upper half.
    float ringTilt = 0.38;
    vec2 ru = vec2(cos(ringTilt) * uv.x + sin(ringTilt) * uv.y, -sin(ringTilt) * uv.x + cos(ringTilt) * uv.y);
    float ringR = length(vec2(ru.x, ru.y / 0.22)) / radius;
    float ring = smoothstep(1.35, 1.45, ringR) * (1.0 - smoothstep(2.15, 2.3, ringR));
    ring *= 0.55 + 0.45 * sin(ringR * 38.0);
    bool ringInFront = ru.y < 0.0;
    vec3 ringCol = mix(vec3(0.85, 0.78, 0.65), accent, 0.35);
    if (r < 1.0) {
        // Shaded sphere with bands.
        float z = sqrt(1.0 - r * r);
        vec3 n = normalize(t * uv.x / radius + b * uv.y / radius + c * z);
        float light = clamp(dot(n, normalize(lightDir + vec3(0.0001))), 0.0, 1.0);
        float bands = 0.5 + 0.5 * sin((uv.y / radius) * 9.0 + noise3(vec3(uv / radius * 3.0, 1.0)) * 2.0);
        vec3 surface = mix(vec3(0.55, 0.42, 0.62), mix(vec3(0.85, 0.65, 0.5), accent, 0.3), bands);
        vec3 col = surface * (0.08 + 0.92 * light);
        // Thin bright rim.
        col += accent * pow(1.0 - z, 3.0) * 0.6;
        float edge = 1.0 - smoothstep(0.96, 1.0, r);
        result = vec4(col, edge);
        if (ringInFront && ring > 0.0) {
            result.rgb = mix(result.rgb, ringCol * (0.3 + 0.7 * light), ring * 0.85);
        }
        return result;
    }
    // Soft atmosphere halo just outside the planet.
    float halo = exp(-(r - 1.0) * 9.0) * 0.35;
    vec3 col = accent * halo;
    float cover = 0.0;
    if (ring > 0.0) {
        col += ringCol * ring * 0.7;
        cover = ring * 0.8;
    }
    return vec4(col, cover);
}

vec3 shootingStars(vec3 dir, float time) {
    vec3 total = vec3(0.0);
    for (int i = 0; i < 2; i++) {
        float period = 5.0 + float(i) * 3.0;
        float slot = floor((time + float(i) * 2.3) / period);
        float local = (time + float(i) * 2.3) - slot * period;
        float life = 0.9;
        if (local > life) {
            continue;
        }
        float seed = slot * 13.7 + float(i) * 91.0;
        vec3 s = normalize(vec3(hash1(seed) * 2.0 - 1.0, 0.35 + hash1(seed + 1.0) * 0.5, hash1(seed + 2.0) * 2.0 - 1.0));
        vec3 t;
        vec3 b;
        frame(s, t, b);
        float ang = hash1(seed + 3.0) * PI * 2.0;
        vec3 travel = normalize(t * cos(ang) + b * sin(ang) - vec3(0.0, 0.6, 0.0));
        vec3 side = normalize(cross(s, travel));
        float progress = local / life;
        float head = progress * 0.45;
        float len = 0.12;
        vec3 rel = dir - s;
        float along = dot(rel, travel);
        float across = dot(rel, side);
        if (dot(dir, s) < 0.8) {
            continue;
        }
        float k = (along - (head - len)) / len;
        float body = step(0.0, k) * step(k, 1.0) * k * k;
        float width = 0.0012 + 0.0016 * k;
        float line = exp(-across * across / (width * width));
        float fade = sin(progress * PI);
        total += vec3(0.9, 0.95, 1.0) * body * line * fade * 1.6;
    }
    return total;
}

vec3 aurora(vec3 dir, float time, vec3 accent, int steps) {
    if (dir.y < 0.02) {
        return vec3(0.0);
    }
    vec3 total = vec3(0.0);
    // Curtains hang over the north (-z), stacked in layers of height.
    float northness = smoothstep(-0.2, 0.6, -dir.z);
    if (northness <= 0.0) {
        return vec3(0.0);
    }
    for (int i = 0; i < 24; i++) {
        if (i >= steps) {
            break;
        }
        float fi = float(i) / float(steps);
        float h = 0.8 + fi * 1.6;
        vec2 p = dir.xz * (h / dir.y);
        if (length(p) > 18.0) {
            continue;
        }
        float wave = sin(p.x * 0.55 + time * 0.12 + sin(p.y * 0.4 + time * 0.07) * 2.0);
        float curtain = exp(-pow((p.y + 4.5 + wave * 1.3) * 0.9, 2.0));
        // Fine vertical rays drifting along the curtain.
        float along = p.x + wave * 1.3;
        float rays = 0.35 + 0.65 * pow(noise3(vec3(along * 3.2, time * 0.3, 0.0)), 1.6);
        rays *= 0.7 + 0.3 * noise3(vec3(along * 11.0, time * 0.6, 4.0));
        float intensity = curtain * rays * pow(1.0 - fi, 1.4) * 0.15;
        vec3 col = mix(vec3(0.15, 1.0, 0.55), vec3(0.1, 0.75, 0.9), fi);
        col = mix(col, accent, smoothstep(0.45, 1.0, fi));
        total += col * intensity;
    }
    return total * northness;
}

float luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

vec3 hueColor(float h) {
    return clamp(abs(mod(h * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
}

// The aurora's colour for its height in the ribbon (0 bottom edge .. 1 top) and where along it you look.
vec3 auroraColor(float up, float along, float time, vec3 accent) {
    int mode = int(AuroraCfg.y + 0.5);
    if (mode == 1) {
        return mix(accent, mix(accent, vec3(1.0), 0.45), up);
    }
    if (mode == 2) {
        return mix(hueColor(along * 0.25 + time * 0.02), hueColor(along * 0.25 + time * 0.02 + 0.15), up);
    }
    if (mode == 3) {
        return mix(vec3(1.0, 0.45, 0.15), vec3(0.85, 0.2, 0.75), up);
    }
    return mix(vec3(0.15, 1.0, 0.55), mix(vec3(0.35, 0.45, 1.0), vec3(0.85, 0.3, 0.9), smoothstep(0.4, 1.0, up)), smoothstep(0.1, 0.7, up));
}

// Living ribbons: bands of light winding across the whole sky. Each has a bright folded lower edge and fine rays
// rising from it; light runs along them, and they slowly twist, drift and breathe.
vec3 auroraRibbons(vec3 dir, float time, vec3 accent, int quality) {
    if (dir.y < 0.0) {
        return vec3(0.0);
    }
    float az = atan(dir.x, -dir.z);
    float el = asin(clamp(dir.y, -1.0, 1.0));
    int count = quality >= 3 ? 5 : quality == 2 ? 4 : 3;
    vec3 total = vec3(0.0);
    for (int i = 0; i < 5; i++) {
        if (i >= count) {
            break;
        }
        float fi = float(i);
        float speed = 0.04 + 0.015 * fi;
        // Where the ribbon's lower edge sits, at this point around you.
        float edge = 0.42 + 0.22 * sin(az * (1.0 + fi * 0.35) + time * speed + fi * 2.1)
            + 0.07 * sin(az * 3.0 - time * 0.09 + fi * 1.7)
            + 0.03 * sin(az * 7.0 + time * 0.21 + fi);
        float slope = abs(0.22 * cos(az * (1.0 + fi * 0.35) + time * speed + fi * 2.1) + 0.21 * cos(az * 3.0 - time * 0.09 + fi * 1.7));
        float d = el - edge;
        // Bright, sharp lower edge; light fading up through the rays above it.
        float lower = exp(-d * d / 0.0006);
        float rays = 0.35 + 0.65 * pow(noise3(vec3(az * 26.0 + fi * 13.0, time * 0.35, fi)), 1.8);
        float above = d > 0.0 ? exp(-d / (0.16 + 0.05 * fi)) * rays : 0.0;
        float body = lower * 0.9 + above;
        // Light running along it, and folds (where it turns) glowing brighter.
        float run = 0.55 + 0.45 * sin(az * 6.0 - time * (0.6 + fi * 0.15) + fi * 3.0);
        float fold = 1.0 + 1.6 * smoothstep(0.15, 0.0, slope);
        // Each ribbon only covers part of the sky, and breathes in and out.
        float reach = smoothstep(-0.2, 0.6, sin(az * 0.5 + fi * 1.9 + time * 0.013));
        float breathe = 0.7 + 0.3 * sin(time * 0.17 + fi * 1.3);
        float up = clamp(d / 0.35, 0.0, 1.0);
        vec3 col = auroraColor(up, az + fi, time, accent);
        total += col * body * run * fold * reach * breathe * 0.11;
    }
    float horizon = smoothstep(0.0, 0.12, dir.y);
    return total * horizon;
}

// The custom sky picture in this direction (a 2:1 panorama wraps round; the bottom half mirrors the top).
vec3 skyPicture(vec3 dir) {
    float az = atan(dir.x, -dir.z) + SkyImg.z;
    float u = fract(az / (2.0 * PI) + 0.5);
    float v = clamp(acos(clamp(dir.y, -1.0, 1.0)) / PI, 0.0, 1.0);
    return texture(SkyImage, vec2(u, v)).rgb;
}

// A huge red moon with dark seas, and a glow around it.
vec3 bloodMoon(vec3 dir, out float cover) {
    vec3 m = normalize(vec3(0.45, 0.42, -0.55));
    float d = acos(clamp(dot(dir, m), -1.0, 1.0));
    float r = 0.16;
    cover = 1.0 - smoothstep(r * 0.97, r, d);
    vec3 t;
    vec3 b;
    frame(m, t, b);
    vec2 uv = vec2(dot(dir, t), dot(dir, b)) / r;
    float seas = fbm(vec3(uv * 2.2, 5.0), 4);
    vec3 disc = mix(vec3(0.95, 0.18, 0.12), vec3(0.45, 0.04, 0.05), smoothstep(0.45, 0.7, seas));
    disc *= 0.75 + 0.25 * sqrt(max(0.0, 1.0 - dot(uv, uv)));
    vec3 glow = vec3(0.9, 0.08, 0.06) * exp(-max(d - r, 0.0) * 9.0) * 0.55;
    return disc * cover + glow * (1.0 - cover);
}

// A striped retro sun sitting on the horizon.
vec3 synthSun(vec3 dir, out float cover) {
    vec3 s = normalize(vec3(-1.0, 0.1, 0.25));
    float d = acos(clamp(dot(dir, s), -1.0, 1.0));
    float r = 0.24;
    vec3 t;
    vec3 b;
    frame(s, t, b);
    vec2 uv = vec2(dot(dir, t), dot(dir, b)) / r;
    float inside = 1.0 - smoothstep(0.97, 1.0, length(uv));
    // Horizontal gaps that get wider towards the bottom.
    float band = fract(uv.y * 5.5 + 0.3);
    float gap = uv.y < 0.1 ? step(band, mix(0.05, 0.45, clamp(-uv.y + 0.1, 0.0, 1.0))) : 0.0;
    cover = inside * (1.0 - gap);
    vec3 disc = mix(vec3(1.0, 0.25, 0.55), vec3(1.0, 0.9, 0.35), clamp(uv.y * 0.5 + 0.5, 0.0, 1.0));
    vec3 glow = vec3(1.0, 0.3, 0.6) * exp(-max(d - r, 0.0) * 6.0) * 0.5;
    return disc * cover + glow * (1.0 - inside);
}

void main() {
    vec2 uv = texCoord;
    vec3 color = texture(InSampler, uv).rgb;
    float depth = texture(DepthSampler, uv).r;
    vec2 ndc = uv * 2.0 - 1.0;
    vec4 far4 = InvViewProj * vec4(ndc, 0.5, 1.0);
    vec3 dir = normalize(far4.xyz / far4.w);
    vec4 fwd4 = InvViewProj * vec4(0.0, 0.0, 0.5, 1.0);
    vec3 forward = normalize(fwd4.xyz / fwd4.w);
    float time = Sun.w;
    vec3 accent = Accent.rgb;
    float clear = Accent.w;
    int quality = int(Flags2.z + 0.5);
    float atmo = AtmoMisc.x;
    float night = max(SkyCol.w, AtmoHigh.w);
    bool cosmic = Flags.x > 0.5 || AtmoHigh.w > 0.5;
    vec3 sunDir = normalize(Sun.xyz);
    // How bright the day is (atmosphere skies follow it, so nights stay dark).
    float daylight = clamp(luma(SkyCol.rgb) * 1.5 + 0.12, 0.12, 1.0);

    // Distance and surface direction for every pixel (sky pixels sit at the far plane).
    float near = View.y;
    float far = View.z;
    float viewZ = depth <= 0.0 ? far : near * far / (depth * (far - near) + near);
    float dist = viewZ / max(dot(dir, forward), 0.05);
    vec3 pos = dir * dist;
    vec3 normal = normalize(cross(dFdx(pos), dFdy(pos)));
    if (dot(normal, dir) > 0.0) {
        normal = -normal;
    }
    vec3 world = pos + vec3(AtmoMisc.y, View.x, AtmoMisc.z);

    // The colour of the sky towards this direction, used to tint the fog.
    vec3 horizon = mix(Fog.rgb, SkyCol.rgb, 0.35);
    float towardsSun = max(dot(normalize(vec3(dir.x, 0.0, dir.z) + vec3(0.0001)), normalize(vec3(sunDir.x, 0.0, sunDir.z) + vec3(0.0001))), 0.0);
    if (Flags2.x > 0.5 && Glow.a > 0.0) {
        horizon = mix(horizon, Glow.rgb, Glow.a * pow(towardsSun, 3.0) * 0.7);
    }
    vec3 atmoSky = mix(AtmoLow.rgb, AtmoHigh.rgb, smoothstep(-0.05, 0.65, dir.y)) * mix(1.0, daylight, 0.65 * (1.0 - AtmoHigh.w));

    if (depth <= 0.0) {
        // ---- sky ----
        float above = clamp(dir.y, 0.0, 1.0);
        bool layers = true;
        if (SkyImg.x > 0.001) {
            // Your own picture, darker at night. With "keep the sun, moon and stars" the bright things vanilla drew
            // (anything brighter than its plain sky colour) stay on top, and the night layers below still draw.
            vec3 picture = skyPicture(dir) * SkyImg.y * mix(0.3, 1.0, daylight);
            vec3 celestial = max(color - SkyCol.rgb * 1.05, vec3(0.0));
            vec3 replaced = picture + (SkyImg.w > 0.5 ? celestial : vec3(0.0));
            color = mix(color, replaced, SkyImg.x);
            layers = SkyImg.w > 0.5 || SkyImg.x < 0.999;
        }
        if (AtmoLow.w > 0.0) {
            color = mix(color, atmoSky, AtmoLow.w);
        }
        // Sunrise and sunset: warm horizon towards the sun, pink and violet higher up.
        if (Flags2.x > 0.5 && Glow.a > 0.0) {
            float g = Glow.a * (1.0 - AtmoLow.w * 0.7);
            float low = exp(-max(dir.y, -0.1) * 7.0);
            vec3 warm = mix(Glow.rgb, vec3(1.0, 0.45, 0.25), 0.35);
            vec3 high = mix(vec3(0.55, 0.30, 0.65), accent, 0.35);
            float sunSide = pow(towardsSun, 2.0);
            color = mix(color, warm, g * low * (0.25 + 0.6 * sunSide));
            color = mix(color, color * 0.75 + high * 0.35, g * (1.0 - low) * 0.55 * (1.0 - sunSide * 0.5));
            float around = max(dot(dir, sunDir), 0.0);
            color += Glow.rgb * pow(around, 24.0) * 0.6 * g;
        }
        if (night > 0.001 && layers) {
            float k = night * clear;
            if (cosmic) {
                vec3 starDir = rotX(rotY(dir, PI * 0.5), -Flags2.w);
                int octaves = quality >= 3 ? 6 : quality == 2 ? 5 : 4;
                vec3 neb = nebula(starDir, time, accent, octaves) * (1.0 + AtmoHigh.w * 0.8);
                color = mix(color, color * vec3(0.55, 0.5, 0.75), k * 0.6);
                color += neb * k * (0.4 + 0.6 * above);
                color += stars(starDir, time, quality >= 3 ? 0.012 : 0.007) * k;
                color += galaxy(starDir, time, accent) * k * (1.0 + AtmoHigh.w);
                if (Flags.y > 0.5 || AtmoHigh.w > 0.5) {
                    vec4 pl = planet(dir, sunDir * -1.0 + vec3(0.3, 0.4, 0.0), accent);
                    color = mix(color, pl.rgb, pl.a * k);
                    color += pl.rgb * (1.0 - pl.a) * k;
                }
                if (Flags.z > 0.5 || AtmoHigh.w > 0.5) {
                    color += shootingStars(dir, time) * k;
                }
            }
            if (Flags.w > 0.5 && AtmoLow.w < 0.5) {
                if (AuroraCfg.x > 0.5) {
                    color += auroraRibbons(dir, time, accent, quality) * k * AuroraCfg.z;
                } else {
                    int steps = quality >= 3 ? 24 : quality == 2 ? 16 : 10;
                    color += aurora(dir, time, accent, steps) * k * AuroraCfg.z;
                }
            }
        }
        int feature = int(AtmoFx.z + 0.5);
        if (feature == 1) {
            float cover;
            vec3 moon = bloodMoon(dir, cover);
            color = mix(color, moon, cover * atmo) + moon * (1.0 - cover) * atmo;
        } else if (feature == 2) {
            float cover;
            vec3 sun = synthSun(dir, cover);
            color = mix(color, sun, cover * atmo) + sun * (1.0 - cover) * atmo;
        }
        color += vec3(0.75, 0.8, 1.0) * AtmoFx.w * 0.7;
    } else {
        float up = normal.y;
        // ---- snow settling on the tops of blocks ----
        if (AtmoFx.x > 0.0 && dist < far * 0.95) {
            float lumps = 0.7 + 0.3 * noise3(world * 1.7);
            float cover = smoothstep(0.55, 0.85, up) * lumps * AtmoFx.x;
            vec3 snow = vec3(0.9, 0.94, 1.0) * (0.45 + 0.75 * luma(color));
            color = mix(color, snow, clamp(cover, 0.0, 0.92));
        }
        // ---- light rippling across the ground (abyss) ----
        if (AtmoFx.y > 0.0) {
            vec2 q = world.xz * 0.45;
            float n1 = noise3(vec3(q, time * 0.45));
            float n2 = noise3(vec3(q * 1.7 + 3.1, -time * 0.38));
            float ripple = pow(clamp(1.0 - abs(n1 - n2) * 2.6, 0.0, 1.0), 7.0);
            float facing = smoothstep(0.2, 0.9, up);
            color += vec3(0.3, 0.75, 0.95) * ripple * facing * AtmoFx.y * (1.0 - smoothstep(20.0, 60.0, dist)) * 0.6;
        }
        // ---- depth fog ----
        if (Flags2.y > 0.001) {
            float range = Fog.w;
            float amount = smoothstep(range * 0.18, range * 0.95, dist);
            amount = 1.0 - exp(-amount * amount * 3.0);
            float worldY = View.x + dir.y * dist;
            amount *= mix(1.0, 0.55, smoothstep(70.0, 160.0, worldY));
            vec3 fogCol = horizon;
            if (night > 0.001 && cosmic) {
                fogCol = mix(fogCol, mix(fogCol, accent * 0.25, 0.35), night);
            }
            color = mix(color, fogCol, clamp(amount * Flags2.y * 0.85, 0.0, 0.9));
        }
        // ---- atmosphere fog: thick, in the atmosphere's colour ----
        if (AtmoFog.w > 0.0) {
            float amount = 1.0 - exp(-dist * AtmoFog.w);
            vec3 fogCol = mix(AtmoFog.rgb * mix(1.0, daylight, 0.6 * (1.0 - AtmoHigh.w)), atmoSky, 0.35);
            color = mix(color, fogCol, clamp(amount, 0.0, 0.97));
        }
        color += vec3(0.75, 0.8, 1.0) * AtmoFx.w * 0.18;
    }

    // ---- atmosphere colour grade (sky and land) ----
    if (atmo > 0.0) {
        color *= mix(vec3(1.0), AtmoGrade.rgb, atmo);
        color = mix(vec3(luma(color)), color, AtmoGrade.w);
    }

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}