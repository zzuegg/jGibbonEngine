// Unlit material shader — renders vertex colors or material color

struct VertexInput {
    float3 position : POSITION;
    float3 normal   : NORMAL;
    float2 uv       : TEXCOORD;
};

struct VertexOutput {
    float4 position : SV_Position;
    float3 color;
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
    output.color = input.normal * 0.5 + 0.5; // visualize normals as colors
    output.uv = input.uv;
    return output;
}

[shader("fragment")]
float4 fragmentMain(VertexOutput input) : SV_Target {
    return float4(input.color, 1.0);
}
