package dev.engine.graphics.webgpu;

import dev.engine.core.mesh.VertexAttribute;
import dev.engine.core.mesh.VertexFormat;
import dev.engine.graphics.window.WindowHandle;

import java.nio.ByteBuffer;

/**
 * Abstraction over WebGPU native bindings.
 *
 * <p>All WebGPU objects are represented as opaque {@code long} handles.
 * Implementations convert between these handles and their concrete wrapper
 * types (e.g. jWebGPU's {@code WGPUDevice}).
 *
 * <p>The interface mirrors the WebGPU API at a level of abstraction suitable
 * for the engine's render device. Descriptor-heavy WebGPU calls are flattened
 * into method parameters to avoid leaking provider-specific descriptor types.
 */
public interface WgpuBindings {

    // ===== Lifecycle =====

    /**
     * Initializes the WebGPU loader/library. Returns true if successful.
     */
    boolean initialize();

    /**
     * Returns true if the native WebGPU library is available.
     */
    boolean isAvailable();

    // ===== Surface / Presentation =====

    /**
     * Configures a presentation surface for the given window.
     * Desktop: creates a wgpu surface using WindowHandle.surfaceInfo().
     * Web: configures the canvas context.
     * Returns a surface/context handle, or 0 if not supported (headless).
     */
    default long configureSurface(long instance, long device, WindowHandle window) { return 0; }

    /**
     * Gets the current surface texture view for rendering.
     * Returns 0 if no surface is configured (offscreen/headless).
     */
    default long getSurfaceTextureView(long surface) { return 0; }

    /**
     * Releases a surface texture view obtained from getSurfaceTextureView.
     */
    default void releaseSurfaceTextureView(long textureView) {}

    /**
     * Presents the current surface texture to the screen.
     * Desktop: calls wgpuSurfacePresent. Web: no-op (browser presents after submit).
     */
    default void surfacePresent(long surface) {}

    /**
     * Returns the texture format used by the presentation surface.
     * Defaults to BGRA8 (desktop wgpu-native). Web browsers may use RGBA8.
     */
    default int surfaceFormat() { return TEXTURE_FORMAT_BGRA8_UNORM; }

    /**
     * Returns true if a presentation surface is available.
     */
    default boolean hasSurface() { return false; }

    // ===== Instance =====

    /** Creates a WebGPU instance. */
    long createInstance();

    /** Processes pending events on the instance. */
    void instanceProcessEvents(long instance);

    /** Releases the instance. */
    void instanceRelease(long instance);

    // ===== Adapter =====

    /**
     * Requests an adapter from the instance (synchronous).
     * Returns the adapter handle, or 0 on failure.
     */
    long instanceRequestAdapter(long instance);

    /** Releases the adapter. */
    void adapterRelease(long adapter);

    // ===== Device =====

    /**
     * Requests a device from the adapter (synchronous).
     * Requires the instance handle for event processing.
     * Returns the device handle, or 0 on failure.
     */
    long adapterRequestDevice(long instance, long adapter);

    /** Gets the device's queue. */
    long deviceGetQueue(long device);

    /** Releases the device. */
    void deviceRelease(long device);

    /** Device limits queried from the GPU. */
    record DeviceLimits(
            int maxTextureDimension2D,
            int maxTextureDimension3D,
            int maxUniformBufferBindingSize,
            int maxStorageBufferBindingSize,
            int maxColorAttachments,
            float maxSamplerAnisotropy
    ) {}

    /** Queries device limits. Returns null if not supported. */
    default DeviceLimits deviceGetLimits(long device) { return null; }

    // ===== Buffer =====

    /**
     * Creates a GPU buffer.
     *
     * @param device the device handle
     * @param size   buffer size in bytes
     * @param usage  combined WebGPU buffer usage flags
     * @return buffer handle
     */
    long deviceCreateBuffer(long device, long size, int usage);

    /** Releases a buffer. */
    void bufferRelease(long buffer);

    /**
     * Writes data to a buffer via the queue.
     *
     * @param queue  the queue handle
     * @param buffer the destination buffer handle
     * @param offset byte offset into the buffer
     * @param data   direct ByteBuffer with data to write
     * @param size   number of bytes to write
     */
    void queueWriteBuffer(long queue, long buffer, int offset, ByteBuffer data, int size);

    /**
     * Maps a buffer for reading (synchronous, polls instance events).
     *
     * @param instance   the instance handle (for event polling)
     * @param buffer     the buffer handle
     * @param size       number of bytes to map
     * @param maxPolls   maximum number of event poll iterations
     */
    void bufferMapReadSync(long instance, long buffer, int size, int maxPolls);

    /**
     * Gets the mapped range of a buffer into the provided direct ByteBuffer.
     */
    void bufferGetConstMappedRange(long buffer, int offset, int size, ByteBuffer dest);

    /** Unmaps a previously mapped buffer. */
    void bufferUnmap(long buffer);

    // ===== Texture =====

    /**
     * Creates a texture.
     *
     * @param device         the device handle
     * @param width          texture width
     * @param height         texture height
     * @param depthOrLayers  depth (for 3D) or array layers
     * @param format         WebGPU texture format ordinal (from {@link WgpuTextureFormat})
     * @param dimension      0 = 2D, 1 = 3D
     * @param usage          combined WebGPU texture usage flags
     * @return texture handle
     */
    long deviceCreateTexture(long device, int width, int height, int depthOrLayers,
                             int format, int dimension, int usage);

    /**
     * Creates a texture view.
     *
     * @param texture        the texture handle
     * @param format         WebGPU texture format ordinal
     * @param viewDimension  view dimension ordinal (from {@link WgpuTextureViewDimension})
     * @param arrayLayerCount number of array layers
     * @return texture view handle
     */
    long textureCreateView(long texture, int format, int viewDimension, int arrayLayerCount);

    /** Releases a texture. */
    void textureRelease(long texture);

    /** Releases a texture view. */
    void textureViewRelease(long textureView);

    /**
     * Writes pixel data to a texture via the queue.
     *
     * @param queue          the queue handle
     * @param texture        the destination texture handle
     * @param width          write region width
     * @param height         write region height
     * @param depthOrLayers  write region depth/layers
     * @param bytesPerRow    bytes per row in the source data
     * @param data           direct ByteBuffer with pixel data
     */
    void queueWriteTexture(long queue, long texture, int width, int height,
                           int depthOrLayers, int bytesPerRow, ByteBuffer data);

    // ===== Sampler =====

    /**
     * Creates a sampler.
     *
     * @param device     the device handle
     * @param addressU   address mode U ordinal (from {@link WgpuAddressMode})
     * @param addressV   address mode V ordinal
     * @param addressW   address mode W ordinal
     * @param magFilter  mag filter ordinal (from {@link WgpuFilterMode})
     * @param minFilter  min filter ordinal
     * @param mipmapFilter mipmap filter ordinal (from {@link WgpuMipmapFilterMode})
     * @return sampler handle
     */
    long deviceCreateSampler(long device, int addressU, int addressV, int addressW,
                             int magFilter, int minFilter, int mipmapFilter,
                             float lodMinClamp, float lodMaxClamp,
                             int compare, float maxAnisotropy);

    /** Releases a sampler. */
    void samplerRelease(long sampler);

    // ===== Shader Module =====

    /**
     * Creates a shader module from WGSL source.
     *
     * @param device the device handle
     * @param wgsl   WGSL shader source code
     * @return shader module handle, or 0 if creation failed
     */
    long deviceCreateShaderModule(long device, String wgsl);

    /** Returns true if the shader module handle is valid. */
    boolean shaderModuleIsValid(long shaderModule);

    /** Releases a shader module. */
    void shaderModuleRelease(long shaderModule);

    // ===== Bind Group Layout =====

    /**
     * Creates a bind group layout with the given entries.
     *
     * @param device  the device handle
     * @param entries array of {@link BindGroupLayoutEntry} descriptors
     * @return bind group layout handle
     */
    long deviceCreateBindGroupLayout(long device, BindGroupLayoutEntry[] entries);

    /** Releases a bind group layout. */
    void bindGroupLayoutRelease(long bindGroupLayout);

    // ===== Pipeline Layout =====

    /**
     * Creates a pipeline layout from bind group layouts.
     *
     * @param device          the device handle
     * @param bindGroupLayouts array of bind group layout handles
     * @return pipeline layout handle
     */
    long deviceCreatePipelineLayout(long device, long[] bindGroupLayouts);

    /** Releases a pipeline layout. */
    void pipelineLayoutRelease(long pipelineLayout);

    // ===== Render Pipeline =====

    /**
     * Creates a render pipeline.
     *
     * @param device the device handle
     * @param desc   the pipeline descriptor
     * @return render pipeline handle
     */
    long deviceCreateRenderPipeline(long device, RenderPipelineDescriptor desc);

    /** Releases a render pipeline. */
    void renderPipelineRelease(long renderPipeline);

    // ===== Bind Group =====

    /**
     * Creates a bind group.
     *
     * @param device   the device handle
     * @param layout   the bind group layout handle
     * @param entries  array of bind group entry descriptors
     * @return bind group handle
     */
    long deviceCreateBindGroup(long device, long layout, BindGroupEntry[] entries);

    /** Releases a bind group. */
    void bindGroupRelease(long bindGroup);

    // ===== Command Encoder =====

    /** Creates a command encoder. */
    long deviceCreateCommandEncoder(long device);

    /**
     * Begins a render pass on the command encoder.
     *
     * @param encoder the command encoder handle
     * @param desc    the render pass descriptor
     * @return render pass encoder handle
     */
    long commandEncoderBeginRenderPass(long encoder, RenderPassDescriptor desc);

    /**
     * Copies data between buffers.
     */
    void commandEncoderCopyBufferToBuffer(long encoder, long src, int srcOffset,
                                          long dst, int dstOffset, int size);

    /**
     * Copies a texture to a buffer.
     */
    void commandEncoderCopyTextureToBuffer(long encoder, long texture, long buffer,
                                           int width, int height,
                                           int bytesPerRow, int rowsPerImage);

    /**
     * Finishes the command encoder, producing a command buffer.
     *
     * @param encoder the command encoder handle
     * @return command buffer handle
     */
    long commandEncoderFinish(long encoder);

    /** Releases a command encoder. */
    void commandEncoderRelease(long encoder);

    // ===== Command Buffer =====

    /** Releases a command buffer. */
    void commandBufferRelease(long commandBuffer);

    // ===== Queue =====

    /** Submits a command buffer to the queue. */
    void queueSubmit(long queue, long commandBuffer);

    // ===== Render Pass Encoder =====

    /** Ends the render pass. */
    void renderPassEnd(long renderPass);

    /** Releases the render pass encoder. */
    void renderPassRelease(long renderPass);

    /** Sets the pipeline on the render pass. */
    void renderPassSetPipeline(long renderPass, long pipeline);

    /** Sets a vertex buffer on the render pass. */
    void renderPassSetVertexBuffer(long renderPass, int slot, long buffer, int offset, int size);

    /** Sets the index buffer on the render pass. */
    void renderPassSetIndexBuffer(long renderPass, long buffer, int indexFormat, int offset, int size);

    /** Sets the bind group on the render pass. */
    void renderPassSetBindGroup(long renderPass, int groupIndex, long bindGroup);

    /** Sets the viewport on the render pass. */
    void renderPassSetViewport(long renderPass, float x, float y, float w, float h,
                               float minDepth, float maxDepth);

    /** Sets the scissor rect on the render pass. */
    void renderPassSetScissorRect(long renderPass, int x, int y, int width, int height);

    /** Sets the stencil reference on the render pass. */
    void renderPassSetStencilReference(long renderPass, int ref);

    /** Draws primitives. */
    void renderPassDraw(long renderPass, int vertexCount, int instanceCount,
                        int firstVertex, int firstInstance);

    /** Draws indexed primitives. */
    void renderPassDrawIndexed(long renderPass, int indexCount, int instanceCount,
                               int firstIndex, int baseVertex, int firstInstance);

    // ===== Descriptor records =====

    /** Describes a bind group layout entry. */
    record BindGroupLayoutEntry(int binding, int visibility, BindingType type) {}

    /** Describes a bind group entry for buffer binding. */
    record BindGroupEntry(int binding, BindingResourceType resourceType,
                          long handle, long offset, long size) {}

    /** Types of bind group layout entries. */
    enum BindingType {
        UNIFORM_BUFFER,
        READ_ONLY_STORAGE_BUFFER,
        SAMPLED_TEXTURE,
        FILTERING_SAMPLER
    }

    /** Types of bind group entry resources. */
    enum BindingResourceType {
        BUFFER,
        TEXTURE_VIEW,
        SAMPLER
    }

    /** Describes a color attachment for a render pass. */
    record ColorAttachment(long textureView, float clearR, float clearG, float clearB, float clearA) {}

    /** Describes a depth/stencil attachment for a render pass. */
    record DepthStencilAttachment(long textureView, float depthClearValue, int stencilClearValue) {}

    /** Describes a render pass. */
    record RenderPassDescriptor(ColorAttachment[] colorAttachments,
                                DepthStencilAttachment depthStencil) {}

    /** Describes vertex buffer layout for pipeline creation. */
    record VertexBufferLayoutDesc(int stride, int stepMode,
                                  VertexAttributeDesc[] attributes) {}

    /** Describes a vertex attribute. */
    record VertexAttributeDesc(int format, int offset, int shaderLocation) {}

    /** Describes stencil face state. */
    record StencilFaceState(int compare, int passOp, int failOp, int depthFailOp) {}

    /** Describes a render pipeline. */
    record RenderPipelineDescriptor(
            long pipelineLayout,
            long vertexModule,
            String vertexEntryPoint,
            long fragmentModule,
            String fragmentEntryPoint,
            VertexBufferLayoutDesc vertexBufferLayout,
            // Primitive state
            int topology,
            int frontFace,
            int cullMode,
            // Depth/stencil
            int depthStencilFormat,
            int depthWriteEnabled,
            int depthCompare,
            int stencilReadMask,
            int stencilWriteMask,
            StencilFaceState stencilFront,
            StencilFaceState stencilBack,
            // Fragment targets
            int colorTargetFormat,
            int blendColorSrcFactor,
            int blendColorDstFactor,
            int blendColorOperation,
            int blendAlphaSrcFactor,
            int blendAlphaDstFactor,
            int blendAlphaOperation
    ) {}

    // ===== WebGPU enum ordinals (wgpu-native v27 / webgpu.h 2025) =====
    // These constants match the C enum values in webgpu.h.
    // Note: v27 renumbered most enums — 0 is now "Undefined" for most types.

    // --- Buffer usage flags (bitmask, unchanged) ---
    int BUFFER_USAGE_COPY_SRC  = 0x0004;
    int BUFFER_USAGE_COPY_DST  = 0x0008;
    int BUFFER_USAGE_INDEX     = 0x0010;
    int BUFFER_USAGE_VERTEX    = 0x0020;
    int BUFFER_USAGE_UNIFORM   = 0x0040;
    int BUFFER_USAGE_STORAGE   = 0x0080;
    int BUFFER_USAGE_MAP_READ  = 0x0001;

    // --- Texture usage flags (bitmask, unchanged) ---
    int TEXTURE_USAGE_COPY_SRC          = 0x0001;
    int TEXTURE_USAGE_COPY_DST          = 0x0002;
    int TEXTURE_USAGE_TEXTURE_BINDING   = 0x0004;
    int TEXTURE_USAGE_RENDER_ATTACHMENT = 0x0010;

    // --- Index format ---
    int INDEX_FORMAT_UINT32 = 2;

    // --- Shader stage visibility (bitmask, unchanged) ---
    int SHADER_STAGE_VERTEX   = 0x1;
    int SHADER_STAGE_FRAGMENT = 0x2;

    // --- Primitive topology ---
    int PRIMITIVE_TOPOLOGY_TRIANGLE_LIST = 4;

    // --- Front face ---
    int FRONT_FACE_CCW = 1;
    int FRONT_FACE_CW  = 2;

    // --- Cull mode ---
    int CULL_MODE_NONE  = 1;
    int CULL_MODE_FRONT = 2;
    int CULL_MODE_BACK  = 3;

    // --- Compare function ---
    int COMPARE_NEVER         = 1;
    int COMPARE_LESS          = 2;
    int COMPARE_EQUAL         = 3;
    int COMPARE_LESS_EQUAL    = 4;
    int COMPARE_GREATER       = 5;
    int COMPARE_NOT_EQUAL     = 6;
    int COMPARE_GREATER_EQUAL = 7;
    int COMPARE_ALWAYS        = 8;

    // --- Stencil operation ---
    int STENCIL_OP_KEEP            = 1;
    int STENCIL_OP_ZERO            = 2;
    int STENCIL_OP_REPLACE         = 3;
    int STENCIL_OP_INVERT          = 4;
    int STENCIL_OP_INCREMENT_CLAMP = 5;
    int STENCIL_OP_DECREMENT_CLAMP = 6;
    int STENCIL_OP_INCREMENT_WRAP  = 7;
    int STENCIL_OP_DECREMENT_WRAP  = 8;

    // --- Optional bool ---
    int OPTIONAL_BOOL_FALSE = 0;
    int OPTIONAL_BOOL_TRUE  = 1;

    // --- Blend factor ---
    int BLEND_FACTOR_ZERO                = 1;
    int BLEND_FACTOR_ONE                 = 2;
    int BLEND_FACTOR_SRC_ALPHA           = 5;
    int BLEND_FACTOR_ONE_MINUS_SRC_ALPHA = 6;
    int BLEND_FACTOR_DST                 = 7;
    int BLEND_FACTOR_DST_ALPHA           = 9;

    // --- Blend operation ---
    int BLEND_OP_ADD = 1;

    // --- Texture format ---
    int TEXTURE_FORMAT_R8_UNORM              = 0x01;
    int TEXTURE_FORMAT_RGBA8_UNORM           = 0x12;
    int TEXTURE_FORMAT_BGRA8_UNORM           = 0x17;
    int TEXTURE_FORMAT_R16_FLOAT             = 0x07;
    int TEXTURE_FORMAT_RG16_FLOAT            = 0x11;
    int TEXTURE_FORMAT_RGBA16_FLOAT          = 0x22;
    int TEXTURE_FORMAT_R32_FLOAT             = 0x0C;
    int TEXTURE_FORMAT_RG32_FLOAT            = 0x1D;
    int TEXTURE_FORMAT_RGBA32_FLOAT          = 0x23;
    int TEXTURE_FORMAT_R32_UINT              = 0x0D;
    int TEXTURE_FORMAT_R32_SINT              = 0x0E;
    int TEXTURE_FORMAT_DEPTH24_PLUS          = 0x28;
    int TEXTURE_FORMAT_DEPTH24_PLUS_STENCIL8 = 0x29;
    int TEXTURE_FORMAT_DEPTH32_FLOAT         = 0x2A;

    // --- Texture dimension ---
    int TEXTURE_DIMENSION_2D = 2;
    int TEXTURE_DIMENSION_3D = 3;

    // --- Texture view dimension ---
    int TEXTURE_VIEW_DIMENSION_2D       = 2;
    int TEXTURE_VIEW_DIMENSION_2D_ARRAY = 3;
    int TEXTURE_VIEW_DIMENSION_CUBE     = 4;
    int TEXTURE_VIEW_DIMENSION_3D       = 6;

    // --- Filter mode ---
    int FILTER_MODE_NEAREST = 1;
    int FILTER_MODE_LINEAR  = 2;

    // --- Mipmap filter mode ---
    int MIPMAP_FILTER_MODE_NEAREST = 1;
    int MIPMAP_FILTER_MODE_LINEAR  = 2;

    // --- Address mode ---
    int ADDRESS_MODE_CLAMP_TO_EDGE = 1;
    int ADDRESS_MODE_REPEAT        = 2;
    int ADDRESS_MODE_MIRROR_REPEAT = 3;

    // --- Vertex format ---
    int VERTEX_FORMAT_UNORM8X4   = 0x09;
    int VERTEX_FORMAT_FLOAT32    = 0x1C;
    int VERTEX_FORMAT_FLOAT32X2  = 0x1D;
    int VERTEX_FORMAT_FLOAT32X3  = 0x1E;
    int VERTEX_FORMAT_FLOAT32X4  = 0x1F;

    // --- Vertex step mode ---
    int VERTEX_STEP_MODE_VERTEX = 2;

    // --- Color write mask ---
    int COLOR_WRITE_MASK_ALL = 0xF;
}
