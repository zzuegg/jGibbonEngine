# Vulkan Backend Notes

## Texture and Sampler Implementation

### Descriptor Layout

The Vulkan backend uses a single descriptor set layout with:
- Bindings 0-15: `VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER` (UBO slots)
- Bindings 16-23: `VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER` (texture+sampler slots)

Texture unit N maps to descriptor binding `16 + N`. This is handled by `VkDescriptorManager.textureBindingOffset()`.

### Dummy Resources for Unused Bindings

All descriptor set bindings must be valid in Vulkan (unlike OpenGL where unbound slots are silently ignored). The descriptor manager creates:
- A 16-byte dummy UBO for unused uniform buffer bindings
- A 1x1 RGBA8 dummy image + image view + sampler for unused texture bindings

These are automatically bound when allocating a new descriptor set via `allocateSet()`.

### Texture Format Mapping

Formats use `_UNORM` variants (not `_SRGB`) to match OpenGL behavior where sRGB conversion is not automatic:
- `RGBA8` -> `VK_FORMAT_R8G8B8A8_UNORM`
- `RGB8` -> `VK_FORMAT_R8G8B8_UNORM` (note: RGB8 has poor hardware support on some GPUs)
- `DEPTH24` -> `VK_FORMAT_D24_UNORM_S8_UINT` (includes stencil)
- `DEPTH32F` -> `VK_FORMAT_D32_SFLOAT`

### Layout Transitions

Textures are created with `VK_IMAGE_LAYOUT_UNDEFINED` and immediately transitioned to `VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL`. During upload, the sequence is:
1. `SHADER_READ_ONLY` -> `TRANSFER_DST` (prepare for copy)
2. Copy staging buffer to image
3. `TRANSFER_DST` -> `SHADER_READ_ONLY` (ready for shader sampling)

### Staging Buffer Pattern

Texture uploads use a temporary HOST_VISIBLE staging buffer. The data is copied via `vkCmdCopyBufferToImage` inside a one-shot command buffer that blocks on `vkQueueWaitIdle`. This is simple but synchronous -- a production engine would use async transfer queues.

### Sampler Mapping

- `FilterMode.LINEAR` / `LINEAR_MIPMAP_LINEAR` -> `VK_FILTER_LINEAR`
- `FilterMode.NEAREST` / `NEAREST_MIPMAP_NEAREST` -> `VK_FILTER_NEAREST`
- Mipmap filter modes set `maxLod = 1000.0f` (effectively unlimited); non-mipmap modes set `maxLod = 0.0f`
- `WrapMode.REPEAT` -> `VK_SAMPLER_ADDRESS_MODE_REPEAT` (default)
- `WrapMode.CLAMP_TO_EDGE` -> `VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE`

### Combined Image Sampler Binding

BindTexture and BindSampler are tracked separately per unit (0-7). At draw time, `flushDescriptorSet()` pairs them: a texture unit is only written to the descriptor set if both an image view AND a sampler are pending for that unit. If only one is set, the dummy binding from `allocateSet()` remains.

### Descriptor Pool Sizing

Each per-frame pool allocates:
- `16 * 256 = 4096` uniform buffer descriptors
- `8 * 256 = 2048` combined image sampler descriptors
- Max 256 descriptor sets per frame

If exceeded, `vkAllocateDescriptorSets` will fail. Increase `MAX_SETS_PER_FRAME` in `VkDescriptorManager` if needed.

## Dynamic State (Vulkan 1.3)

### API Version Requirement

The Vulkan backend requests API version 1.3 (`VK_API_VERSION_1_3`) in the instance creation. This is required for using core dynamic state functions from `VK13`:

- `vkCmdSetDepthTestEnable` / `vkCmdSetDepthWriteEnable`
- `vkCmdSetCullMode`
- `vkCmdSetFrontFace`

These functions are promoted from `VK_EXT_extended_dynamic_state` and are mandatory in Vulkan 1.3 conformant devices. No explicit feature enable is needed when the device supports 1.3.

### Pipeline Dynamic State List

`VkPipelineFactory` declares these as dynamic states in the pipeline:

- `VK_DYNAMIC_STATE_VIEWPORT` / `VK_DYNAMIC_STATE_SCISSOR` (standard)
- `VK_DYNAMIC_STATE_DEPTH_TEST_ENABLE` / `VK_DYNAMIC_STATE_DEPTH_WRITE_ENABLE`
- `VK_DYNAMIC_STATE_CULL_MODE` / `VK_DYNAMIC_STATE_FRONT_FACE`

The pipeline's rasterizer and depth-stencil initial values are overridden at draw time by these dynamic state commands.

### What is NOT dynamically configurable

- **Wireframe / polygon mode**: `vkCmdSetPolygonModeEXT` requires `VK_EXT_extended_dynamic_state3`, which has poor driver support. This is a no-op in the Vulkan backend.

### Blending via Pipeline Variants

`vkCmdSetColorBlendEnableEXT` requires `VK_EXT_extended_dynamic_state3`, which has poor driver support. Instead, the backend creates **pipeline variants** on demand when a blend mode is requested.

When `SetRenderState` with `BLEND_MODE` (or `SetBlending`) is received:
1. The currently bound pipeline handle is looked up in a `pipelineSpecs` map to retrieve the original shader binaries and vertex format.
2. A variant pipeline is created via `VkPipelineFactory.create()` with the appropriate `BlendConfig` (NONE, ALPHA, ADDITIVE, MULTIPLY, PREMULTIPLIED).
3. Variants are cached in `pipelineBlendVariants` keyed by `"pipelineIndex_blendModeName"` so they are only created once per combination.
4. The variant pipeline is bound via `vkCmdBindPipeline`.

Cleanup: variants are destroyed when the base pipeline is destroyed (`destroyPipeline`) and when the device is closed.

## Compute Pipelines

### Render Pass Constraint

Vulkan compute dispatches (`vkCmdDispatch`) **cannot** be recorded inside an active render pass. This is a hard Vulkan specification constraint (VUID-vkCmdDispatch-renderpass). The caller must ensure compute work is issued outside of `vkCmdBeginRenderPass` / `vkCmdEndRenderPass` pairs.

Currently the engine does not automatically end/begin render passes around compute dispatches. Users must structure their command lists so that compute work happens before the render pass begins or after it ends.

### Descriptor Set Binding

When dispatching compute work, the descriptor set must be bound to `VK_PIPELINE_BIND_POINT_COMPUTE` in addition to `VK_PIPELINE_BIND_POINT_GRAPHICS`. The `flushDescriptorSet()` method binds to GRAPHICS; the Dispatch command handler additionally binds the same set to COMPUTE before dispatching.

### Pipeline Layout Sharing

Compute pipelines share the same `VkPipelineLayout` as graphics pipelines (from `VkDescriptorManager.pipelineLayout()`). This means compute shaders can access UBOs, SSBOs, and textures using the same binding numbers as graphics shaders.
