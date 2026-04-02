// PBR material shader with directional lighting

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

// __MATERIAL_STRUCT__

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

    float3 diffuse = albedoColor * (0.15 + 0.85 * NdotL);
    float3 V = float3(0, 0, 1);
    float3 H = normalize(L + V);
    float shininess = lerp(8.0, 256.0, 1.0 - roughness);
    float spec = pow(max(dot(N, H), 0.0), shininess);
    float3 specular = float3(spec * metallic, spec * metallic, spec * metallic);

    float3 color = diffuse + specular + emissive;
    return float4(color, opacity);
}
