// Debug UI overlay shader — 2D screen-space rendering with texture + vertex color.
// Vertex format: position (float2), texcoord (float2), color (unorm byte4).
// TEXTURE_BINDING is injected by DebugUiOverlay to match the backend's descriptor layout.

cbuffer ScreenData {
    float2 screenSize;
};

struct VertexInput {
    float2 position : POSITION;
    float2 uv       : TEXCOORD;
    float4 color    : COLOR;
};

struct VertexOutput {
    float4 position : SV_Position;
    float2 uv;
    float4 color;
};

[shader("vertex")]
VertexOutput vertexMain(VertexInput input) {
    VertexOutput output;
    float2 pos = input.position / screenSize * 2.0 - 1.0;
    pos.y = -pos.y;
    output.position = float4(pos, 0.0, 1.0);
    output.uv = input.uv;
    output.color = input.color;
    return output;
}

[[vk::binding(TEXTURE_BINDING)]]
Sampler2D fontAtlas : register(t7);

[shader("fragment")]
float4 fragmentMain(VertexOutput input) : SV_Target {
    float4 texColor = fontAtlas.Sample(input.uv);
    return input.color * texColor;
}
