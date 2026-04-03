package dev.engine.graphics.vulkan;

import dev.engine.core.mesh.ComponentType;
import dev.engine.core.mesh.VertexAttribute;
import dev.engine.core.mesh.VertexFormat;
import dev.engine.graphics.pipeline.ShaderBinary;
import dev.engine.graphics.pipeline.ShaderStage;
import org.lwjgl.vulkan.*;
import org.lwjgl.vulkan.VK13;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.List;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.VK10.*;

/**
 * Creates Vulkan graphics pipelines from SPIRV shader binaries.
 */
final class VkPipelineFactory {

    private VkPipelineFactory() {}

    /**
     * Creates a graphics pipeline from SPIRV binaries.
     *
     * @param pipelineLayout the shared pipeline layout (from VkDescriptorManager)
     */
    static long create(VkDevice device, long renderPass, long pipelineLayout,
                        List<ShaderBinary> binaries, VertexFormat vertexFormat) {
        try (var stack = stackPush()) {
            // Create shader modules
            long[] modules = new long[binaries.size()];
            var stageInfos = VkPipelineShaderStageCreateInfo.calloc(binaries.size(), stack);

            for (int i = 0; i < binaries.size(); i++) {
                var bin = binaries.get(i);
                ByteBuffer spirvBuf = memAlloc(bin.spirv().length);
                spirvBuf.put(bin.spirv()).flip();

                var moduleInfo = VkShaderModuleCreateInfo.calloc(stack)
                        .sType$Default()
                        .pCode(spirvBuf);

                var pModule = stack.mallocLong(1);
                int result = vkCreateShaderModule(device, moduleInfo, null, pModule);
                memFree(spirvBuf);
                if (result != VK_SUCCESS) throw new RuntimeException("Failed to create shader module: " + result);
                modules[i] = pModule.get(0);

                stageInfos.get(i)
                        .sType$Default()
                        .stage(mapStage(bin.stage()))
                        .module(modules[i])
                        .pName(stack.UTF8("main")); // Slang emits "main" as entry point name in SPIRV
            }

            // Vertex input from format
            var vertexInputInfo = VkPipelineVertexInputStateCreateInfo.calloc(stack).sType$Default();
            if (vertexFormat != null && !vertexFormat.attributes().isEmpty()) {
                var bindingDesc = VkVertexInputBindingDescription.calloc(1, stack)
                        .binding(0)
                        .stride(vertexFormat.stride())
                        .inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

                var attrDescs = VkVertexInputAttributeDescription.calloc(vertexFormat.attributes().size(), stack);
                for (int i = 0; i < vertexFormat.attributes().size(); i++) {
                    var attr = vertexFormat.attributes().get(i);
                    attrDescs.get(i)
                            .binding(0)
                            .location(attr.location())
                            .format(mapAttributeFormat(attr))
                            .offset(attr.offset());
                }

                vertexInputInfo
                        .pVertexBindingDescriptions(bindingDesc)
                        .pVertexAttributeDescriptions(attrDescs);
            }

            var inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
                    .primitiveRestartEnable(false);

            // Dynamic viewport + scissor + depth/cull (VK 1.3 / VK_EXT_extended_dynamic_state)
            var dynamicStates = stack.ints(
                    VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR,
                    VK13.VK_DYNAMIC_STATE_DEPTH_TEST_ENABLE,
                    VK13.VK_DYNAMIC_STATE_DEPTH_WRITE_ENABLE,
                    VK13.VK_DYNAMIC_STATE_CULL_MODE,
                    VK13.VK_DYNAMIC_STATE_FRONT_FACE,
                    VK13.VK_DYNAMIC_STATE_STENCIL_TEST_ENABLE,
                    VK13.VK_DYNAMIC_STATE_STENCIL_OP
            );
            var dynamicStateInfo = VkPipelineDynamicStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .pDynamicStates(dynamicStates);

            var viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .viewportCount(1)
                    .scissorCount(1);

            var rasterizer = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .depthClampEnable(false)
                    .rasterizerDiscardEnable(false)
                    .polygonMode(VK_POLYGON_MODE_FILL)
                    .lineWidth(1.0f)
                    .cullMode(VK_CULL_MODE_NONE)
                    .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)
                    .depthBiasEnable(false);

            var multisampling = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .sampleShadingEnable(false)
                    .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);

            var depthStencil = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .depthTestEnable(true)
                    .depthWriteEnable(true)
                    .depthCompareOp(VK_COMPARE_OP_LESS)
                    .depthBoundsTestEnable(false)
                    .stencilTestEnable(false);

            var colorBlendAttachment = VkPipelineColorBlendAttachmentState.calloc(1, stack)
                    .colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT |
                            VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT)
                    .blendEnable(false);

            var colorBlending = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .logicOpEnable(false)
                    .pAttachments(colorBlendAttachment);

            // Create the pipeline
            var pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                    .sType$Default()
                    .pStages(stageInfos)
                    .pVertexInputState(vertexInputInfo)
                    .pInputAssemblyState(inputAssembly)
                    .pViewportState(viewportState)
                    .pRasterizationState(rasterizer)
                    .pMultisampleState(multisampling)
                    .pDepthStencilState(depthStencil)
                    .pColorBlendState(colorBlending)
                    .pDynamicState(dynamicStateInfo)
                    .layout(pipelineLayout)
                    .renderPass(renderPass)
                    .subpass(0);

            var pPipeline = stack.mallocLong(1);
            int result = vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, pipelineInfo, null, pPipeline);

            // Destroy shader modules — no longer needed after pipeline creation
            for (long mod : modules) {
                vkDestroyShaderModule(device, mod, null);
            }

            if (result != VK_SUCCESS) throw new RuntimeException("Failed to create graphics pipeline: " + result);

            return pPipeline.get(0);
        }
    }

    private static int mapAttributeFormat(VertexAttribute attr) {
        if ("FLOAT".equals(attr.componentType().name())) {
            return switch (attr.componentCount()) {
                case 1 -> VK_FORMAT_R32_SFLOAT;
                case 2 -> VK_FORMAT_R32G32_SFLOAT;
                case 3 -> VK_FORMAT_R32G32B32_SFLOAT;
                case 4 -> VK_FORMAT_R32G32B32A32_SFLOAT;
                default -> throw new IllegalArgumentException("Unsupported float component count: " + attr.componentCount());
            };
        } else if ("INT".equals(attr.componentType().name())) {
            return switch (attr.componentCount()) {
                case 1 -> VK_FORMAT_R32_SINT;
                case 2 -> VK_FORMAT_R32G32_SINT;
                case 3 -> VK_FORMAT_R32G32B32_SINT;
                case 4 -> VK_FORMAT_R32G32B32A32_SINT;
                default -> throw new IllegalArgumentException("Unsupported int component count: " + attr.componentCount());
            };
        }
        throw new IllegalArgumentException("Unsupported component type: " + attr.componentType().name());
    }

    private static int mapStage(ShaderStage stage) {
        return switch (stage.name()) {
            case "VERTEX" -> VK_SHADER_STAGE_VERTEX_BIT;
            case "FRAGMENT" -> VK_SHADER_STAGE_FRAGMENT_BIT;
            case "GEOMETRY" -> VK_SHADER_STAGE_GEOMETRY_BIT;
            case "COMPUTE" -> VK_SHADER_STAGE_COMPUTE_BIT;
            default -> throw new IllegalArgumentException("Unsupported shader stage: " + stage.name());
        };
    }
}
