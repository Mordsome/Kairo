#version 330
#extension GL_ARB_separate_shader_objects : require

// GLASS variant: Sampler0 is a blurred copy of the world; the shape shows it (saturation boosted) under a tint.

#ifdef GLASS
uniform sampler2D Sampler0;
#endif

layout(location = 0) in vec4 fillColor;
layout(location = 1) flat in vec4 strokeColor;
layout(location = 2) in vec2 localPos;
layout(location = 3) flat in ivec2 shapeInfo;
layout(location = 4) flat in vec4 params;
layout(location = 5) flat in vec4 extra;
layout(location = 6) in vec2 screenUv;

layout(location = 0) out vec4 fragColor;

#define SHAPE_RECT 0
#define SHAPE_SECTOR 1
#define SHAPE_SEGMENT 2
#define SHAPE_BACKDROP 3
#define SHAPE_TRIANGLE 4
#define FLAG_SOFT 16

float sdRoundRect(vec2 p, vec2 halfSize, float r) {
    r = min(r, min(halfSize.x, halfSize.y));
    vec2 q = abs(p) - halfSize + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

// Intersection of two SDFs with rounded corners.
float roundIntersect(float a, float b, float r) {
    vec2 u = max(vec2(r + a, r + b), vec2(0.0));
    return min(-r, max(a, b)) + length(u);
}

// Ring sector centred on +X. The straight edges are pushed inwards by gapHalf, so neighbouring
// sectors keep a constant gap in pixels no matter the radius.
float sdSector(vec2 p, float innerR, float outerR, float halfAngle, float gapHalf, float cornerR) {
    float r = length(p);
    float ring = max(r - outerR, innerR - r);
    if (halfAngle >= 3.1415) {
        return ring;
    }
    vec2 n = vec2(-sin(halfAngle), cos(halfAngle));
    float edge = dot(vec2(p.x, abs(p.y)), n) + gapHalf;
    return cornerR > 0.0 ? roundIntersect(ring, edge, cornerR) : max(ring, edge);
}

// Capsule along X from -halfLength to +halfLength.
float sdSegment(vec2 p, float halfLength, float halfThickness) {
    vec2 q = vec2(p.x - clamp(p.x, -halfLength, halfLength), p.y);
    return length(q) - halfThickness;
}

// Isosceles triangle, tip at the origin, base q.y below it, half-width q.x (Inigo Quilez).
float sdTriangleIsosceles(vec2 p, vec2 q) {
    p.x = abs(p.x);
    vec2 a = p - q * clamp(dot(p, q) / dot(q, q), 0.0, 1.0);
    vec2 b = p - q * vec2(clamp(p.x / q.x, 0.0, 1.0), 1.0);
    float s = -sign(q.y);
    vec2 d = min(vec2(dot(a, a), s * (p.x * q.y - p.y * q.x)), vec2(dot(b, b), s * (p.y - q.y)));
    return -sqrt(d.x) * sign(d.y);
}

// One screen pixel of antialiasing, whatever the transform.
float aa(float d) {
    float w = max(fwidth(d), 1e-5);
    return clamp(0.5 - d / w, 0.0, 1.0);
}

void main() {
    int type = shapeInfo.x & 15;
    int flags = shapeInfo.x & ~15;
    float strokeWidth = float(shapeInfo.y) / 64.0;

#ifdef GLASS
    vec3 behind = texture(Sampler0, screenUv).rgb;
    if (type == SHAPE_BACKDROP) {
        // params: x = vignette strength, y = dim, zw = half screen size (localPos is centred GUI pixels).
        float vignette = smoothstep(0.25, 1.5, length(localPos / params.zw)) * params.x;
        vec3 c = behind * (1.0 - params.y) * (1.0 - vignette);
        fragColor = vec4(c, 1.0) * extra.y;
        return;
    }
#endif

    float d;
    if (type == SHAPE_RECT) {
        d = sdRoundRect(localPos, params.xy, params.z);
    } else if (type == SHAPE_SECTOR) {
        d = sdSector(localPos, params.x, params.y, params.z, params.w, extra.x * 64.0);
    } else if (type == SHAPE_SEGMENT) {
        d = sdSegment(localPos, params.x, params.y);
    } else {
        // Triangle centred on its box: shift so the tip sits at the origin, then round the corners.
        d = sdTriangleIsosceles(localPos + vec2(0.0, params.y * 0.5), params.xy) - params.z;
    }

    float coverage;
    if ((flags & FLAG_SOFT) != 0) {
        float soft = max(params.w, 0.001);
        coverage = 1.0 - smoothstep(-soft, soft, d);
    } else {
        coverage = aa(d);
    }

    // Everything is premultiplied from here on.
    vec4 color;
#ifdef GLASS
    float opacity = extra.y;
    float luma = dot(behind, vec3(0.2126, 0.7152, 0.0722));
    vec3 glass = mix(vec3(luma), behind, 1.0 + extra.z);
    glass = mix(glass, fillColor.rgb, fillColor.a);
    color = vec4(glass, 1.0) * (coverage * opacity);
#else
    float opacity = 1.0;
    color = vec4(fillColor.rgb * fillColor.a, fillColor.a) * coverage;
#endif

    if (strokeWidth > 0.0 && strokeColor.a > 0.0) {
        // Inner stroke: the band between the edge and strokeWidth inside it.
        float ds = max(d, -(d + strokeWidth));
        float s = aa(ds) * strokeColor.a * opacity;
        color = vec4(strokeColor.rgb * s, s) + color * (1.0 - s);
    }

    if (color.a <= 0.0) {
        discard;
    }
    fragColor = color;
}
