// Unlit material shader — material color * vertex normal visualization

struct VertexInput {
    float3 position : POSITION;
    float3 normal   : NORMAL;
    float2 uv       : TEXCOORD;
};

struct VertexOutput {
    float4 position : SV_Position;
    float3 normal;
    float2 uv;
};

cbuffer Matrices {
    float4x4 mvp;
};

// __MATERIAL_STRUCT__

[shader("vertex")]
VertexOutput vertexMain(VertexInput input) {
    VertexOutput output;
    output.position = mul(float4(input.position, 1.0), mvp);
    output.normal = input.normal;
    output.uv = input.uv;
    return output;
}

[shader("fragment")]
float4 fragmentMain(VertexOutput input) : SV_Target {
    float3 N = normalize(input.normal);
    float3 L = normalize(float3(0.5, 1.0, 0.3));
    float NdotL = max(dot(N, L), 0.0);
    float3 lit = color * (0.3 + 0.7 * NdotL);
    return float4(lit, 1.0);
}
