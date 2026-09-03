#version 150

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
in vec2 oneTexel;

uniform vec2 InSize;
uniform float EffectStrength;
uniform float PixelSize;
uniform float BlurRadius;
uniform float MediumRed;
uniform float MediumGreen;
uniform float MediumBlue;
uniform float ColorStrength;
uniform float FogStrength;

out vec4 fragColor;

float stableNoise(vec2 cell) {
    return fract(sin(dot(cell, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec3 mediumColor = vec3(MediumRed, MediumGreen, MediumBlue);
    float mosaic = max(1.0, PixelSize);
    vec2 cell = floor(texCoord * InSize / mosaic);
    vec2 uv = (cell + vec2(0.5)) * mosaic / InSize;
    vec2 stepSize = oneTexel * BlurRadius;
    vec3 color = texture(DiffuseSampler, uv).rgb * 0.36;
    color += texture(DiffuseSampler, uv + vec2(stepSize.x, 0.0)).rgb * 0.16;
    color += texture(DiffuseSampler, uv - vec2(stepSize.x, 0.0)).rgb * 0.16;
    color += texture(DiffuseSampler, uv + vec2(0.0, stepSize.y)).rgb * 0.16;
    color += texture(DiffuseSampler, uv - vec2(0.0, stepSize.y)).rgb * 0.16;

    float tint = clamp(ColorStrength * EffectStrength, 0.0, 0.88);
    float luma = dot(color, vec3(0.30, 0.59, 0.11));
    vec3 materialColor = mediumColor * (0.56 + luma * 0.62);
    color = mix(color, materialColor, tint);

    float edge = max(abs(texCoord.x - 0.5) * 2.0, abs(texCoord.y - 0.5) * 2.0);
    float irregular = (stableNoise(cell) - 0.5) * 0.10;
    float fogMask = smoothstep(0.26 + irregular, 1.0, edge);
    float fog = clamp(FogStrength * (0.32 + fogMask * 0.68), 0.0, 0.94);
    color = mix(color, mediumColor * 0.42, fog);

    fragColor = vec4(color, 1.0);
}
