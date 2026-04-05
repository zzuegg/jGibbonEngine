package dev.engine.graphics.vulkan;

import dev.engine.core.mesh.VertexAttribute;
import dev.engine.core.mesh.VertexFormat;
import dev.engine.graphics.pipeline.ShaderBinary;
import dev.engine.graphics.pipeline.ShaderStage;
import dev.engine.graphics.renderstate.BlendEquation;
import dev.engine.graphics.renderstate.BlendFactor;
import dev.engine.graphics.renderstate.BlendMode;

import java.util.List;

/**
 * Creates Vulkan graphics pipelines from SPIRV shader binaries.
 */
final class VkPipelineFactory {

    private VkPipelineFactory() {}

    /**
     * Blend configuration for a single pipeline color attachment.
     */
    record BlendConfig(boolean enabled, int srcColorFactor, int dstColorFactor,
                       int srcAlphaFactor, int dstAlphaFactor,
                       int colorBlendOp, int alphaBlendOp) {

        /** Convenience constructor that defaults to VK_BLEND_OP_ADD for both equations. */
        BlendConfig(boolean enabled, int srcColorFactor, int dstColorFactor,
                    int srcAlphaFactor, int dstAlphaFactor) {
            this(enabled, srcColorFactor, dstColorFactor, srcAlphaFactor, dstAlphaFactor,
                    VkBindings.VK_BLEND_OP_ADD, VkBindings.VK_BLEND_OP_ADD);
        }

        static final BlendConfig NONE = new BlendConfig(false,
                VkBindings.VK_BLEND_FACTOR_ONE, VkBindings.VK_BLEND_FACTOR_ZERO,
                VkBindings.VK_BLEND_FACTOR_ONE, VkBindings.VK_BLEND_FACTOR_ZERO);
        static final BlendConfig ALPHA = new BlendConfig(true,
                VkBindings.VK_BLEND_FACTOR_SRC_ALPHA, VkBindings.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA,
                VkBindings.VK_BLEND_FACTOR_ONE, VkBindings.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA);
        static final BlendConfig ADDITIVE = new BlendConfig(true,
                VkBindings.VK_BLEND_FACTOR_SRC_ALPHA, VkBindings.VK_BLEND_FACTOR_ONE,
                VkBindings.VK_BLEND_FACTOR_ONE, VkBindings.VK_BLEND_FACTOR_ONE);
        static final BlendConfig MULTIPLY = new BlendConfig(true,
                VkBindings.VK_BLEND_FACTOR_DST_COLOR, VkBindings.VK_BLEND_FACTOR_ZERO,
                VkBindings.VK_BLEND_FACTOR_DST_ALPHA, VkBindings.VK_BLEND_FACTOR_ZERO);
        static final BlendConfig PREMULTIPLIED = new BlendConfig(true,
                VkBindings.VK_BLEND_FACTOR_ONE, VkBindings.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA,
                VkBindings.VK_BLEND_FACTOR_ONE, VkBindings.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA);

        static BlendConfig fromBlendMode(BlendMode mode) {
            if (!mode.enabled()) return NONE;
            return new BlendConfig(true,
                    mapFactor(mode.srcColorFactor()), mapFactor(mode.dstColorFactor()),
                    mapFactor(mode.srcAlphaFactor()), mapFactor(mode.dstAlphaFactor()),
                    mapEquation(mode.colorEquation()), mapEquation(mode.alphaEquation()));
        }

        private static int mapFactor(BlendFactor factor) {
            if (factor == BlendFactor.ZERO)                return VkBindings.VK_BLEND_FACTOR_ZERO;
            if (factor == BlendFactor.ONE)                 return VkBindings.VK_BLEND_FACTOR_ONE;
            if (factor == BlendFactor.SRC_COLOR)           return VkBindings.VK_BLEND_FACTOR_SRC_COLOR;
            if (factor == BlendFactor.ONE_MINUS_SRC_COLOR) return VkBindings.VK_BLEND_FACTOR_ONE_MINUS_SRC_COLOR;
            if (factor == BlendFactor.DST_COLOR)           return VkBindings.VK_BLEND_FACTOR_DST_COLOR;
            if (factor == BlendFactor.ONE_MINUS_DST_COLOR) return VkBindings.VK_BLEND_FACTOR_ONE_MINUS_DST_COLOR;
            if (factor == BlendFactor.SRC_ALPHA)           return VkBindings.VK_BLEND_FACTOR_SRC_ALPHA;
            if (factor == BlendFactor.ONE_MINUS_SRC_ALPHA) return VkBindings.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
            if (factor == BlendFactor.DST_ALPHA)           return VkBindings.VK_BLEND_FACTOR_DST_ALPHA;
            if (factor == BlendFactor.ONE_MINUS_DST_ALPHA) return VkBindings.VK_BLEND_FACTOR_ONE_MINUS_DST_ALPHA;
            return VkBindings.VK_BLEND_FACTOR_ZERO;
        }

        private static int mapEquation(BlendEquation eq) {
            if (eq == BlendEquation.SUBTRACT)         return VkBindings.VK_BLEND_OP_SUBTRACT;
            if (eq == BlendEquation.REVERSE_SUBTRACT) return VkBindings.VK_BLEND_OP_REVERSE_SUBTRACT;
            if (eq == BlendEquation.MIN)              return VkBindings.VK_BLEND_OP_MIN;
            if (eq == BlendEquation.MAX)              return VkBindings.VK_BLEND_OP_MAX;
            return VkBindings.VK_BLEND_OP_ADD;
        }
    }

    static long create(VkBindings vk, long device, long renderPass, long pipelineLayout,
                        List<ShaderBinary> binaries, VertexFormat vertexFormat) {
        return create(vk, device, renderPass, pipelineLayout, binaries, vertexFormat,
                new BlendConfig[]{BlendConfig.NONE}, false);
    }

    static long create(VkBindings vk, long device, long renderPass, long pipelineLayout,
                        List<ShaderBinary> binaries, VertexFormat vertexFormat,
                        BlendConfig blendConfig) {
        return create(vk, device, renderPass, pipelineLayout, binaries, vertexFormat,
                new BlendConfig[]{blendConfig}, false);
    }

    static long create(VkBindings vk, long device, long renderPass, long pipelineLayout,
                        List<ShaderBinary> binaries, VertexFormat vertexFormat,
                        BlendConfig blendConfig, boolean wireframe) {
        return create(vk, device, renderPass, pipelineLayout, binaries, vertexFormat,
                new BlendConfig[]{blendConfig}, wireframe);
    }

    /**
     * Creates a pipeline with per-attachment blend configs for MRT rendering.
     * The {@code blendConfigs} array should contain one entry per color attachment.
     * If shorter than the number of attachments, the last entry is repeated.
     */
    static long create(VkBindings vk, long device, long renderPass, long pipelineLayout,
                        List<ShaderBinary> binaries, VertexFormat vertexFormat,
                        BlendConfig[] blendConfigs, boolean wireframe) {
        if (blendConfigs == null || blendConfigs.length == 0) {
            blendConfigs = new BlendConfig[]{BlendConfig.NONE};
        }

        // Create shader modules
        long[] modules = new long[binaries.size()];
        int[] stages = new int[binaries.size()];
        for (int i = 0; i < binaries.size(); i++) {
            var bin = binaries.get(i);
            modules[i] = vk.createShaderModule(device, bin.spirv());
            stages[i] = mapStage(bin.stage());
        }

        // Vertex attributes
        int[] attribLocations = null;
        int[] attribFormats = null;
        int[] attribOffsets = null;
        int vertexStride = 0;
        if (vertexFormat != null && !vertexFormat.attributes().isEmpty()) {
            var attrs = vertexFormat.attributes();
            attribLocations = new int[attrs.size()];
            attribFormats = new int[attrs.size()];
            attribOffsets = new int[attrs.size()];
            vertexStride = vertexFormat.stride();
            for (int i = 0; i < attrs.size(); i++) {
                var attr = attrs.get(i);
                attribLocations[i] = attr.location();
                attribFormats[i] = mapAttributeFormat(attr);
                attribOffsets[i] = attr.offset();
            }
        }

        // Flatten BlendConfig[] to parallel primitive arrays
        int n = blendConfigs.length;
        boolean[] blendEnabled  = new boolean[n];
        int[]     srcColor      = new int[n];
        int[]     dstColor      = new int[n];
        int[]     srcAlpha      = new int[n];
        int[]     dstAlpha      = new int[n];
        int[]     colorOps      = new int[n];
        int[]     alphaOps      = new int[n];
        for (int i = 0; i < n; i++) {
            var c = blendConfigs[i];
            blendEnabled[i] = c.enabled();
            srcColor[i]     = c.srcColorFactor();
            dstColor[i]     = c.dstColorFactor();
            srcAlpha[i]     = c.srcAlphaFactor();
            dstAlpha[i]     = c.dstAlphaFactor();
            colorOps[i]     = c.colorBlendOp();
            alphaOps[i]     = c.alphaBlendOp();
        }

        // Dynamic states
        int[] dynamicStates = {
                VkBindings.VK_DYNAMIC_STATE_VIEWPORT,
                VkBindings.VK_DYNAMIC_STATE_SCISSOR,
                VkBindings.VK_DYNAMIC_STATE_DEPTH_TEST_ENABLE,
                VkBindings.VK_DYNAMIC_STATE_DEPTH_WRITE_ENABLE,
                VkBindings.VK_DYNAMIC_STATE_DEPTH_COMPARE_OP,
                VkBindings.VK_DYNAMIC_STATE_CULL_MODE,
                VkBindings.VK_DYNAMIC_STATE_FRONT_FACE,
                VkBindings.VK_DYNAMIC_STATE_STENCIL_TEST_ENABLE,
                VkBindings.VK_DYNAMIC_STATE_STENCIL_OP,
                VkBindings.VK_DYNAMIC_STATE_STENCIL_COMPARE_MASK,
                VkBindings.VK_DYNAMIC_STATE_STENCIL_WRITE_MASK,
                VkBindings.VK_DYNAMIC_STATE_STENCIL_REFERENCE
        };

        long pipeline = vk.createGraphicsPipeline(device, renderPass, pipelineLayout,
                modules, stages,
                attribLocations, attribFormats, attribOffsets, vertexStride,
                blendEnabled, srcColor, dstColor, srcAlpha, dstAlpha,
                colorOps, alphaOps,
                wireframe, dynamicStates);

        // Destroy shader modules — no longer needed after pipeline creation
        for (long mod : modules) {
            vk.destroyShaderModule(device, mod);
        }

        return pipeline;
    }

    private static int mapAttributeFormat(VertexAttribute attr) {
        if ("FLOAT".equals(attr.componentType().name())) {
            return switch (attr.componentCount()) {
                case 1 -> VkBindings.VK_FORMAT_R32_SFLOAT;
                case 2 -> VkBindings.VK_FORMAT_R32G32_SFLOAT;
                case 3 -> VkBindings.VK_FORMAT_R32G32B32_SFLOAT;
                case 4 -> VkBindings.VK_FORMAT_R32G32B32A32_SFLOAT;
                default -> throw new IllegalArgumentException("Unsupported float component count: " + attr.componentCount());
            };
        } else if ("INT".equals(attr.componentType().name())) {
            return switch (attr.componentCount()) {
                case 1 -> VkBindings.VK_FORMAT_R32_SINT;
                case 2 -> VkBindings.VK_FORMAT_R32G32_SINT;
                case 3 -> VkBindings.VK_FORMAT_R32G32B32_SINT;
                case 4 -> VkBindings.VK_FORMAT_R32G32B32A32_SINT;
                default -> throw new IllegalArgumentException("Unsupported int component count: " + attr.componentCount());
            };
        }
        throw new IllegalArgumentException("Unsupported component type: " + attr.componentType().name());
    }

    private static int mapStage(ShaderStage stage) {
        return switch (stage.name()) {
            case "VERTEX" -> VkBindings.VK_SHADER_STAGE_VERTEX_BIT;
            case "FRAGMENT" -> VkBindings.VK_SHADER_STAGE_FRAGMENT_BIT;
            case "GEOMETRY" -> VkBindings.VK_SHADER_STAGE_GEOMETRY_BIT;
            case "COMPUTE" -> VkBindings.VK_SHADER_STAGE_COMPUTE_BIT;
            default -> throw new IllegalArgumentException("Unsupported shader stage: " + stage.name());
        };
    }
}
