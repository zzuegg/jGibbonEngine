// Common types and interfaces shared across all shaders

interface IMaterial {
    float3 shade(float3 normal, float3 viewDir, float3 lightDir, float3 lightColor);
};

struct VertexInput {
    float3 position : POSITION;
    float3 color    : COLOR;
};

struct VertexOutput {
    float4 clipPosition : SV_Position;
    float3 worldPosition;
    float3 vertexColor;
};

cbuffer FrameData : register(b0) {
    float4x4 mvp;
    float4x4 model;
    float3 cameraPosition;
};
