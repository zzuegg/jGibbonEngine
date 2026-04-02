// Unlit material shader — renders vertex colors directly

struct VertexInput {
    float3 position : POSITION;
    float3 color    : COLOR;
};

struct VertexOutput {
    float4 position : SV_Position;
    float3 color;
};

cbuffer Matrices : register(b0) {
    float4x4 mvp;
};

[shader("vertex")]
VertexOutput vertexMain(VertexInput input) {
    VertexOutput output;
    output.position = mul(mvp, float4(input.position, 1.0));
    output.color = input.color;
    return output;
}

[shader("fragment")]
float4 fragmentMain(VertexOutput input) : SV_Target {
    return float4(input.color, 1.0);
}
