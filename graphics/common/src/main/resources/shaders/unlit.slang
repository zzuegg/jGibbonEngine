// Unlit material shader — material color with simple directional lighting
// Generated param blocks are prepended before compilation.
// All params use generic specialization — the shader declares its dependencies.

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

[shader("vertex")]
VertexOutput vertexMain<C : ICameraParams, O : IObjectParams>(VertexInput input) {
    C camera;
    O object;
    VertexOutput output;
    float4x4 mvp = mul(object.world(), camera.viewProjection());
    output.position = mul(float4(input.position, 1.0), mvp);
    output.normal = mul(float4(input.normal, 0.0), object.world()).xyz;
    output.uv = input.uv;
    return output;
}

[shader("fragment")]
float4 fragmentMain<M : IMaterialParams>(VertexOutput input) : SV_Target {
    M material;
    float3 N = normalize(input.normal);
    float3 L = normalize(float3(0.5, 1.0, 0.3));
    float NdotL = max(dot(N, L), 0.0);
    float3 lit = material.color() * (0.3 + 0.7 * NdotL);
    return float4(lit, 1.0);
}
