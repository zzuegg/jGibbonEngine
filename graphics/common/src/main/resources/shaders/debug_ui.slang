// Debug UI overlay shader — 2D screen-space rendering with texture + vertex color.
// Vertex format: position (float2), texcoord (float2), color (unorm byte4).
// Push constant: screen size for NDC conversion.

struct PushConstants {
    float2 screenSize;
};

[[vk::push_constant]]
ConstantBuffer<PushConstants> pc;

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
    // Convert from screen coordinates to clip space [-1, 1]
    float2 pos = input.position / pc.screenSize * 2.0 - 1.0;
    pos.y = -pos.y; // Flip Y: screen origin at top-left
    output.position = float4(pos, 0.0, 1.0);
    output.uv = input.uv;
    output.color = input.color;
    return output;
}

Sampler2D fontAtlas;

[shader("fragment")]
float4 fragmentMain(VertexOutput input) : SV_Target {
    float4 texColor = fontAtlas.Sample(input.uv);
    return input.color * texColor;
}
