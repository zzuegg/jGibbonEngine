// Textured material shader — samples albedo texture with directional lighting
// Generated param blocks are prepended before compilation.
// Uses generic specialization for camera/object params.
// Texture declarations (albedoTexture) are generated from material keys.

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
float4 fragmentMain(VertexOutput input) : SV_Target {
    float3 albedo = albedoTexture.Sample(input.uv).rgb;
    float3 N = normalize(input.normal);
    float3 L = normalize(float3(0.5, 1.0, 0.3));
    float NdotL = max(dot(N, L), 0.0);
    float3 color = albedo * (0.15 + 0.85 * NdotL);
    return float4(color, 1.0);
}
