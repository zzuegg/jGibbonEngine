// PBR material shader with basic lighting

struct VertexInput {
    float3 position : POSITION;
    float3 color    : COLOR;
};

struct VertexOutput {
    float4 position : SV_Position;
    float3 worldPos;
    float3 color;
};

cbuffer Matrices : register(b0) {
    float4x4 mvp;
    float4x4 model;
};

cbuffer MaterialData : register(b1) {
    float3 albedoColor;
    float roughness;
    float metallic;
    float3 emissive;
};

cbuffer LightData : register(b2) {
    float3 lightDirection;
    float lightIntensity;
    float3 lightColor;
    float3 ambientColor;
};

[shader("vertex")]
VertexOutput vertexMain(VertexInput input) {
    VertexOutput output;
    output.position = mul(float4(input.position, 1.0), mvp);
    output.worldPos = mul(float4(input.position, 1.0), model).xyz;
    output.color = input.color;
    return output;
}

// Simplified PBR — Lambertian diffuse + Blinn-Phong specular
[shader("fragment")]
float4 fragmentMain(VertexOutput input) : SV_Target {
    float3 albedo = albedoColor * input.color;
    float3 N = normalize(cross(ddx(input.worldPos), ddy(input.worldPos)));
    float3 L = normalize(-lightDirection);

    // Diffuse
    float NdotL = max(dot(N, L), 0.0);
    float3 diffuse = albedo * lightColor * lightIntensity * NdotL;

    // Specular (Blinn-Phong approximation)
    float3 V = normalize(-input.worldPos);
    float3 H = normalize(L + V);
    float spec = pow(max(dot(N, H), 0.0), mix(8.0, 256.0, 1.0 - roughness));
    float3 specular = lightColor * spec * metallic;

    float3 ambient = ambientColor * albedo;
    float3 color = ambient + diffuse + specular + emissive;
    return float4(color, 1.0);
}
