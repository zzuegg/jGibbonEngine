// Textured unlit shader — samples a texture using UVs

struct VertexInput {
    float3 position : POSITION;
    float3 normal   : NORMAL;
    float2 uv       : TEXCOORD;
};

struct VertexOutput {
    float4 position : SV_Position;
    float3 normal;
    float2 uv;
    float3 worldPos;
};

cbuffer Matrices : register(b0) {
    float4x4 mvp;
};

cbuffer LightParams : register(b2) {
    float3 lightDir;
    float3 lightColor;
    float3 ambientColor;
};

Sampler2D albedoTexture : register(t0);

[shader("vertex")]
VertexOutput vertexMain(VertexInput input) {
    VertexOutput output;
    output.position = mul(float4(input.position, 1.0), mvp);
    output.normal = input.normal;
    output.uv = input.uv;
    output.worldPos = input.position;
    return output;
}

[shader("fragment")]
float4 fragmentMain(VertexOutput input) : SV_Target {
    float3 albedo = albedoTexture.Sample(input.uv).rgb;
    float3 N = normalize(input.normal);
    float3 L = normalize(float3(0.5, 1.0, 0.3));
    float NdotL = max(dot(N, L), 0.0);
    float3 color = albedo * (0.15 + 0.85 * NdotL);
    return float4(color, 1.0);
}
