// PBR material shader with directional lighting
// Generated param blocks are prepended before compilation.
// All params use generic specialization — the shader declares its dependencies.

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

[shader("vertex")]
VertexOutput vertexMain<C : ICameraParams, O : IObjectParams>(VertexInput input) {
    C camera;
    O object;
    VertexOutput output;
    float4x4 mvp = mul(object.world(), camera.viewProjection());
    output.position = mul(float4(input.position, 1.0), mvp);
    output.worldNormal = mul(float4(input.normal, 0.0), object.world()).xyz;
    output.uv = input.uv;
    return output;
}

[shader("fragment")]
float4 fragmentMain<C : ICameraParams, M : IMaterialParams>(VertexOutput input) : SV_Target {
    C camera;
    M material;
    float3 N = normalize(input.worldNormal);
    float3 L = normalize(float3(0.5, 1.0, 0.3));
    float NdotL = max(dot(N, L), 0.0);

    float3 diffuse = material.albedoColor() * (0.15 + 0.85 * NdotL);
    float3 V = normalize(camera.position());
    float3 H = normalize(L + V);
    float shininess = lerp(8.0, 256.0, 1.0 - material.roughness());
    float spec = pow(max(dot(N, H), 0.0), shininess);
    float3 specular = float3(spec * material.metallic(), spec * material.metallic(), spec * material.metallic());

    float3 color = diffuse + specular + material.emissive();
    return float4(color, material.opacity());
}
