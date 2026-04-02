// PBR material shader with basic directional lighting

struct VertexInput {
    float3 position : POSITION;
    float3 normal   : NORMAL;
    float2 uv       : TEXCOORD;
};

struct VertexOutput {
    float4 position : SV_Position;
    float3 worldNormal;
    float2 uv;
};

cbuffer Matrices {
    float4x4 mvp;
};

// __MATERIAL_STRUCT__ (auto-generated from PbrMaterial.ScalarData)

[shader("vertex")]
VertexOutput vertexMain(VertexInput input) {
    VertexOutput output;
    output.position = mul(float4(input.position, 1.0), mvp);
    output.worldNormal = input.normal;
    output.uv = input.uv;
    return output;
}

[shader("fragment")]
float4 fragmentMain(VertexOutput input) : SV_Target {
    float3 N = normalize(input.worldNormal);
    float3 L = normalize(float3(0.5, 1.0, 0.3));
    float NdotL = max(dot(N, L), 0.0);

    float3 color = N * 0.5 + 0.5; // normal visualization for now
    color = color * (0.2 + 0.8 * NdotL);
    return float4(color, 1.0);
}
