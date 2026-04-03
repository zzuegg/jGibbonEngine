# Provider/Platform Separation Design

**Date:** 2026-04-03
**Status:** Approved
**Scope:** Separate rendering API from native library providers. Backend modules define binding interfaces, provider modules implement them.

---

## 1. Goals

- Backend modules (opengl, vulkan, webgpu) have zero direct dependencies on native libraries (LWJGL, jWebGPU)
- Native library calls are abstracted behind binding interfaces defined by each backend
- Providers implement these interfaces using specific native libraries
- Constructor injection — providers passed explicitly, no service discovery
- All existing screenshot tests keep passing
- Same engine code can target desktop/web/mobile by swapping providers

## 2. Module Structure

```
graphics/
├── api/              — RenderDevice, RenderCommand, handles (unchanged)
├── common/           — Renderer, ShaderManager, materials (unchanged)
├── opengl/           — OpenGL RenderDevice + GlBindings interface (no LWJGL imports)
├── vulkan/           — Vulkan RenderDevice + VkBindings interface (no LWJGL imports)
└── webgpu/           — WebGPU RenderDevice + WgpuBindings interface (no jWebGPU imports)

providers/
├── lwjgl/
│   ├── graphics/
│   │   ├── opengl/   — LwjglGlBindings implements GlBindings
│   │   └── vulkan/   — LwjglVkBindings implements VkBindings
│   └── windowing/
│       └── glfw/     — LWJGL GLFW implementation (moves from windowing/glfw)
├── jwebgpu/
│   └── graphics/
│       └── webgpu/   — JWebGpuBindings implements WgpuBindings
├── slang/
│   └── shader/       — Slang compiler bindings (moves from bindings/slang)
└── assimp/
    └── asset/        — Assimp model loader (moves from bindings/assimp)

windowing/
└── api/              — WindowToolkit, WindowHandle interfaces (extracted from windowing/glfw)

core/                 — unchanged
examples/             — updated to wire providers explicitly
```

## 3. Binding Interfaces

### 3.1 OpenGL — Thin 1:1 Wrapper

`graphics/opengl/` defines `GlBindings` interface with methods mapping directly to GL calls:

```java
public interface GlBindings {
    int createBuffers();
    void namedBufferData(int buffer, long size, int usage);
    void namedBufferSubData(int buffer, long offset, long size, long dataPtr);
    void deleteBuffers(int buffer);
    
    int createTextures(int target);
    void textureStorage2D(int tex, int levels, int fmt, int w, int h);
    void textureSubImage2D(int tex, int level, int x, int y, int w, int h, int fmt, int type, ByteBuffer data);
    void generateTextureMipmap(int tex);
    void deleteTextures(int tex);
    void bindTextureUnit(int unit, int tex);
    
    int createProgram();
    int createShader(int type);
    void shaderSource(int shader, String source);
    void compileShader(int shader);
    int getShaderi(int shader, int pname);
    String getShaderInfoLog(int shader);
    void attachShader(int program, int shader);
    void linkProgram(int program);
    int getProgrami(int program, int pname);
    String getProgramInfoLog(int program);
    void deleteShader(int shader);
    void deleteProgram(int program);
    void useProgram(int program);
    
    void drawArrays(int mode, int first, int count);
    void drawElements(int mode, int count, int type, long offset);
    void drawArraysInstancedBaseInstance(int mode, int first, int count, int instCount, int baseInst);
    void drawElementsInstancedBaseInstance(int mode, int count, int type, long offset, int instCount, int baseInst);
    void drawArraysIndirect(int mode, long offset);
    void drawElementsIndirect(int mode, int type, long offset);
    void multiDrawArraysIndirect(int mode, long offset, int drawCount, int stride);
    void multiDrawElementsIndirect(int mode, int type, long offset, int drawCount, int stride);
    void dispatchCompute(int gx, int gy, int gz);
    
    void enable(int cap);
    void disable(int cap);
    void depthFunc(int func);
    void depthMask(boolean flag);
    void blendFunc(int sfactor, int dfactor);
    void cullFace(int mode);
    void frontFace(int mode);
    void polygonMode(int face, int mode);
    void lineWidth(float width);
    void stencilFunc(int func, int ref, int mask);
    void stencilOp(int sfail, int dpfail, int dppass);
    void scissor(int x, int y, int w, int h);
    void viewport(int x, int y, int w, int h);
    void clearColor(float r, float g, float b, float a);
    void clear(int mask);
    
    int createVertexArrays();
    void deleteVertexArrays(int vao);
    void bindVertexArray(int vao);
    void enableVertexArrayAttrib(int vao, int index);
    void vertexArrayAttribFormat(int vao, int index, int size, int type, boolean normalized, int offset);
    void vertexArrayAttribBinding(int vao, int index, int bindingIndex);
    void vertexArrayVertexBuffer(int vao, int bindingIndex, int buffer, long offset, int stride);
    void vertexArrayBindingDivisor(int vao, int bindingIndex, int divisor);
    void vertexAttribDivisor(int index, int divisor);
    
    void bindBufferBase(int target, int index, int buffer);
    void bindBuffer(int target, int buffer);
    void bindSampler(int unit, int sampler);
    void bindImageTexture(int unit, int tex, int level, boolean layered, int layer, int access, int format);
    void memoryBarrier(int barriers);
    
    int createSamplers();
    void samplerParameteri(int sampler, int pname, int param);
    void deleteSamplers(int sampler);
    
    int createFramebuffers();
    void namedFramebufferTexture(int fbo, int attachment, int tex, int level);
    void bindFramebuffer(int target, int fbo);
    void deleteFramebuffers(int fbo);
    void blitNamedFramebuffer(int src, int dst, int sx0, int sy0, int sx1, int sy1, int dx0, int dy0, int dx1, int dy1, int mask, int filter);
    void drawBuffers(int fbo, int[] bufs);
    
    void readPixels(int x, int y, int w, int h, int format, int type, ByteBuffer pixels);
    void copyNamedBufferSubData(int src, int dst, long srcOff, long dstOff, long size);
    void copyImageSubData(int src, int srcTarget, int srcLevel, int sx, int sy, int sz, int dst, int dstTarget, int dstLevel, int dx, int dy, int dz, int w, int h, int d);
    
    long namedBufferStorage(int buffer, long size, int flags);
    long mapNamedBufferRange(int buffer, long offset, long length, int access);
    void unmapNamedBuffer(int buffer);
    
    // Context
    void makeContextCurrent(long window);
    void createCapabilities();
    void swapBuffers(long window);
    String getString(int name);
    int getInteger(int pname);
    float getFloat(int pname);
    boolean isExtensionAvailable(String name);
    
    // Queries
    int genQueries();
    void deleteQueries(int query);
    void queryCounter(int query, int target);
    int getQueryObjecti(int query, int pname);
    long getQueryObjecti64(int query, int pname);
    
    // Bindless textures (optional)
    long getTextureHandleARB(int tex);
    void makeTextureHandleResidentARB(long handle);
    
    // Sync
    long fenceSync(int condition, int flags);
    int getSynci(long sync, int pname);
    int clientWaitSync(long sync, int flags, long timeout);
    void deleteSync(long sync);
}
```

### 3.2 Vulkan — Medium-Level Wrapper

Vulkan's struct-heavy API is wrapped at a medium level. The provider handles LWJGL struct allocation internally.

```java
public interface VkBindings {
    // Instance/Device lifecycle
    record PhysicalDeviceInfo(String name, int queueFamily) {}
    
    long createInstance(boolean enableValidation, String[] extensions);
    PhysicalDeviceInfo[] enumeratePhysicalDevices(long instance, long surface);
    long createDevice(long instance, long physicalDevice, int queueFamily, String[] extensions);
    long getDeviceQueue(long device, int queueFamily, int queueIndex);
    long createCommandPool(long device, int queueFamily, int flags);
    void deviceWaitIdle(long device);
    
    // Surface
    long createSurface(long instance, long windowHandle);
    
    // Swapchain
    record SwapchainInfo(long swapchain, int format, int width, int height, long[] images, long[] imageViews) {}
    SwapchainInfo createSwapchain(long device, long physicalDevice, long surface, int width, int height, long oldSwapchain);
    int acquireNextImage(long device, long swapchain, long semaphore);
    int queuePresent(long queue, long swapchain, int imageIndex, long waitSemaphore);
    void destroySwapchain(long device, long swapchain, long[] imageViews);
    
    // Buffers
    record BufferAllocation(long buffer, long memory, long size) {}
    BufferAllocation createBuffer(long device, long physicalDevice, long size, int usage, int memoryProperties);
    void destroyBuffer(long device, long buffer, long memory);
    long mapMemory(long device, long memory, long offset, long size);
    void unmapMemory(long device, long memory);
    
    // Textures
    record ImageAllocation(long image, long memory, long imageView) {}
    ImageAllocation createImage(long device, long physicalDevice, int width, int height, int depth, int layers, int mipLevels, int format, int usage, int imageType, int viewType, int aspectMask, boolean cubeCompatible);
    void destroyImage(long device, long image, long memory, long imageView);
    
    // Shaders
    long createShaderModule(long device, byte[] spirv);
    void destroyShaderModule(long device, long module);
    
    // Pipeline
    long createGraphicsPipeline(long device, long renderPass, long pipelineLayout, long[] shaderModules, int[] shaderStages, VertexFormat vertexFormat, boolean blendEnabled, int blendSrcFactor, int blendDstFactor, boolean wireframe);
    long createComputePipeline(long device, long pipelineLayout, long shaderModule);
    void destroyPipeline(long device, long pipeline);
    
    // Render pass
    long createRenderPass(long device, int colorFormat, int depthFormat);
    void destroyRenderPass(long device, long renderPass);
    
    // Framebuffer
    long createFramebuffer(long device, long renderPass, long[] attachments, int width, int height);
    void destroyFramebuffer(long device, long framebuffer);
    
    // Descriptor sets
    long createDescriptorSetLayout(long device, int[] bindingTypes, int[] bindingStages, int[] bindingCounts);
    long createPipelineLayout(long device, long descriptorSetLayout, int pushConstantSize);
    long createDescriptorPool(long device, int[] poolTypes, int[] poolCounts, int maxSets);
    long allocateDescriptorSet(long device, long pool, long layout);
    void resetDescriptorPool(long device, long pool);
    void updateDescriptorBuffer(long device, long descriptorSet, int binding, int type, long buffer, long offset, long range);
    void updateDescriptorImage(long device, long descriptorSet, int binding, long imageView, long sampler, int imageLayout);
    void destroyDescriptorPool(long device, long pool);
    void destroyDescriptorSetLayout(long device, long layout);
    void destroyPipelineLayout(long device, long pipelineLayout);
    
    // Sampler
    long createSampler(long device, int minFilter, int magFilter, int mipmapMode, float maxLod, int addressModeU, int addressModeV);
    void destroySampler(long device, long sampler);
    
    // Command buffers
    long allocateCommandBuffer(long device, long commandPool);
    void beginCommandBuffer(long commandBuffer, int flags);
    void endCommandBuffer(long commandBuffer);
    void resetCommandBuffer(long commandBuffer);
    void freeCommandBuffer(long device, long commandPool, long commandBuffer);
    
    // Command recording
    void cmdBeginRenderPass(long cmd, long renderPass, long framebuffer, int x, int y, int w, int h, float[] clearColor, float clearDepth);
    void cmdEndRenderPass(long cmd);
    void cmdBindPipeline(long cmd, int bindPoint, long pipeline);
    void cmdBindVertexBuffers(long cmd, long buffer);
    void cmdBindIndexBuffer(long cmd, long buffer, int indexType);
    void cmdBindDescriptorSets(long cmd, int bindPoint, long pipelineLayout, long descriptorSet);
    void cmdDraw(long cmd, int vertexCount, int instanceCount, int firstVertex, int firstInstance);
    void cmdDrawIndexed(long cmd, int indexCount, int instanceCount, int firstIndex, int vertexOffset, int firstInstance);
    void cmdDrawIndirect(long cmd, long buffer, long offset, int drawCount, int stride);
    void cmdDrawIndexedIndirect(long cmd, long buffer, long offset, int drawCount, int stride);
    void cmdDispatch(long cmd, int gx, int gy, int gz);
    void cmdSetViewport(long cmd, float x, float y, float w, float h, float minDepth, float maxDepth);
    void cmdSetScissor(long cmd, int x, int y, int w, int h);
    void cmdPushConstants(long cmd, long pipelineLayout, int stageFlags, int offset, ByteBuffer data);
    void cmdPipelineBarrier(long cmd, int srcStage, int dstStage, int srcAccess, int dstAccess);
    void cmdCopyBufferToImage(long cmd, long buffer, long image, int width, int height);
    void cmdCopyImageToBuffer(long cmd, long image, long buffer, int width, int height);
    void cmdCopyBuffer(long cmd, long src, long dst, long srcOffset, long dstOffset, long size);
    void cmdBlitImage(long cmd, long srcImage, long dstImage, int srcW, int srcH, int dstW, int dstH, int srcMip, int dstMip, int filter);
    void cmdImageBarrier(long cmd, long image, int oldLayout, int newLayout, int srcStage, int dstStage, int srcAccess, int dstAccess, int baseMip, int levelCount);
    
    // Dynamic state (VK 1.3)
    void cmdSetDepthTestEnable(long cmd, boolean enabled);
    void cmdSetDepthWriteEnable(long cmd, boolean enabled);
    void cmdSetDepthCompareOp(long cmd, int op);
    void cmdSetCullMode(long cmd, int mode);
    void cmdSetFrontFace(long cmd, int face);
    void cmdSetStencilTestEnable(long cmd, boolean enabled);
    void cmdSetStencilOp(long cmd, int faceMask, int failOp, int passOp, int depthFailOp, int compareOp);
    void cmdSetStencilCompareMask(long cmd, int faceMask, int mask);
    void cmdSetStencilReference(long cmd, int faceMask, int ref);
    
    // Sync
    long createSemaphore(long device);
    long createFence(long device, boolean signaled);
    void waitForFence(long device, long fence, long timeout);
    void resetFence(long device, long fence);
    void destroySemaphore(long device, long semaphore);
    void destroyFence(long device, long fence);
    
    // Queue
    void queueSubmit(long queue, long commandBuffer, long waitSemaphore, long signalSemaphore, long fence);
    void queueWaitIdle(long queue);
    
    // Readback
    long bufferGetMappedPointer(long device, long memory, long offset, long size);
    
    // Cleanup
    void destroyCommandPool(long device, long pool);
    void destroyDevice(long device);
    void destroyInstance(long instance);
    void destroySurface(long instance, long surface);
    
    // Capabilities
    int getMaxTextureSize(long physicalDevice);
    String getDeviceName(long physicalDevice);
    String getApiVersion(long physicalDevice);
    int findDepthFormat(long physicalDevice);
    int findMemoryType(long physicalDevice, int typeFilter, int properties);
}
```

### 3.3 WebGPU — Thin Wrapper

WebGPU's API is already high-level (no manual memory allocation). The binding interface wraps jWebGPU/TeaVM calls:

```java
public interface WgpuBindings {
    // Lifecycle
    long createInstance();
    long requestAdapter(long instance);
    long requestDevice(long instance, long adapter);
    long getQueue(long device);
    void devicePoll(long device);
    void releaseInstance(long instance);
    void releaseAdapter(long adapter);
    void releaseDevice(long device);
    
    // Buffers
    long createBuffer(long device, long size, long usage, boolean mappedAtCreation);
    void queueWriteBuffer(long queue, long buffer, long offset, ByteBuffer data);
    long bufferGetMappedRange(long buffer, long offset, long size);
    void bufferUnmap(long buffer);
    long bufferGetSize(long buffer);
    void bufferMapSync(long device, long buffer, int mode, long offset, long size);
    void releaseBuffer(long buffer);
    
    // Textures
    long createTexture(long device, int width, int height, int depth, int layers, int mipLevels, int format, long usage, int dimension);
    long createTextureView(long texture, int format, int dimension, int baseMip, int mipCount, int baseLayer, int layerCount);
    void queueWriteTexture(long queue, long texture, ByteBuffer data, int width, int height, int depth, int bytesPerRow, int format);
    void releaseTexture(long texture);
    void releaseTextureView(long textureView);
    
    // Samplers
    long createSampler(long device, int minFilter, int magFilter, int mipmapFilter, int addressModeU, int addressModeV, int addressModeW, float maxLod, int compare);
    void releaseSampler(long sampler);
    
    // Shaders
    long createShaderModule(long device, String wgslSource);
    void releaseShaderModule(long module);
    
    // Pipelines
    long createRenderPipeline(long device, long shaderModule, String vsEntry, String fsEntry, long pipelineLayout, VertexFormat vertexFormat, int colorFormat, int depthFormat, boolean blendEnabled, int blendSrcColor, int blendDstColor, int blendSrcAlpha, int blendDstAlpha, int cullMode, int frontFace, boolean depthTest, boolean depthWrite, int depthCompare, boolean stencilTest);
    long createComputePipeline(long device, long shaderModule, String entry, long pipelineLayout);
    long renderPipelineGetBindGroupLayout(long pipeline, int groupIndex);
    void releaseRenderPipeline(long pipeline);
    void releaseComputePipeline(long pipeline);
    
    // Bind groups
    long createBindGroupLayout(long device, int[] bindings, int[] types, int[] visibility, int[] bufferTypes, int[] textureSampleTypes, int[] samplerTypes);
    long createPipelineLayout(long device, long bindGroupLayout);
    long createBindGroup(long device, long layout, int[] bindings, long[] buffers, long[] bufferSizes, long[] textureViews, long[] samplers);
    void releaseBindGroup(long bindGroup);
    void releaseBindGroupLayout(long layout);
    void releasePipelineLayout(long layout);
    
    // Command encoding
    long createCommandEncoder(long device);
    long beginRenderPass(long encoder, long[] colorViews, int[] loadOps, float[][] clearColors, long depthView, float clearDepth);
    void renderPassSetPipeline(long pass, long pipeline);
    void renderPassSetVertexBuffer(long pass, int slot, long buffer, long offset, long size);
    void renderPassSetIndexBuffer(long pass, long buffer, int format, long offset, long size);
    void renderPassSetBindGroup(long pass, int index, long bindGroup);
    void renderPassDraw(long pass, int vertexCount, int instanceCount, int firstVertex, int firstInstance);
    void renderPassDrawIndexed(long pass, int indexCount, int instanceCount, int firstIndex, int baseVertex, int firstInstance);
    void renderPassSetViewport(long pass, float x, float y, float w, float h, float minDepth, float maxDepth);
    void renderPassSetScissorRect(long pass, int x, int y, int w, int h);
    void renderPassEnd(long pass);
    void releaseRenderPassEncoder(long pass);
    long beginComputePass(long encoder);
    void computePassSetPipeline(long pass, long pipeline);
    void computePassSetBindGroup(long pass, int index, long bindGroup);
    void computePassDispatch(long pass, int gx, int gy, int gz);
    void computePassEnd(long pass);
    void releaseComputePassEncoder(long pass);
    void encoderCopyTextureToBuffer(long encoder, long texture, long buffer, int width, int height, int bytesPerRow);
    void encoderCopyBufferToBuffer(long encoder, long src, long srcOffset, long dst, long dstOffset, long size);
    long encoderFinish(long encoder);
    void releaseCommandEncoder(long encoder);
    
    // Queue
    void queueSubmit(long queue, long commandBuffer);
    void releaseCommandBuffer(long commandBuffer);
    
    // Capabilities
    boolean isAvailable();
}
```

## 4. Constructor Injection

```java
// Desktop OpenGL
var glBindings = new LwjglGlBindings();
var toolkit = new GlfwWindowToolkit(glBindings);
var window = toolkit.createWindow(desc);
glBindings.makeContextCurrent(window.nativeHandle());
glBindings.createCapabilities();
var device = new GlRenderDevice(window, glBindings);

// Desktop Vulkan
var vkBindings = new LwjglVkBindings();
var toolkit = new GlfwWindowToolkit(glBindings);
var window = toolkit.createWindow(desc);
var device = new VkRenderDevice(vkBindings, window, width, height);

// Desktop WebGPU
var wgpuBindings = new JWebGpuBindings();
var toolkit = new GlfwWindowToolkit(glBindings);
var window = toolkit.createWindow(desc);
var device = new WgpuRenderDevice(window, wgpuBindings);

// Web WebGPU (future)
var wgpuBindings = new TeaVmWgpuBindings();
var toolkit = new CanvasWindowToolkit();
var window = toolkit.createWindow(desc);
var device = new WgpuRenderDevice(window, wgpuBindings);
```

## 5. Migration Strategy

### Phase 1: OpenGL (simplest, 4 files with LWJGL)
1. Define `GlBindings` interface in `graphics/opengl/`
2. Create `providers/lwjgl/graphics/opengl/LwjglGlBindings` implementing it
3. Refactor `GlRenderDevice` to use `GlBindings` instead of `GL45.*`
4. Refactor `GlStreamingBuffer`, `GlFence`, `GlGpuTimer` similarly
5. Update constructors, tests, examples
6. Verify all GL screenshot tests pass

### Phase 2: WebGPU (already uses a library)
1. Define `WgpuBindings` interface in `graphics/webgpu/`
2. Create `providers/jwebgpu/graphics/webgpu/JWebGpuBindings` implementing it
3. Refactor `WgpuRenderDevice` to use `WgpuBindings`
4. Verify all WebGPU screenshot tests pass

### Phase 3: Vulkan (most complex)
1. Define `VkBindings` interface in `graphics/vulkan/`
2. Create `providers/lwjgl/graphics/vulkan/LwjglVkBindings` implementing it
3. Refactor `VkRenderDevice` and helper classes
4. Verify all VK screenshot tests pass

### Phase 4: Windowing
1. Extract `WindowToolkit`/`WindowHandle` interfaces to `windowing/api/` (if not already there)
2. Move GLFW implementation to `providers/lwjgl/windowing/glfw/`
3. Update wiring

### Phase 5: Cleanup
1. Remove old `bindings/wgpu/` module (replaced by providers)
2. Move `bindings/slang/` to `providers/slang/`
3. Move `bindings/assimp/` to `providers/assimp/`
4. Final screenshot test pass on all 3 backends

## 6. Success Criteria

- `graphics/opengl/` has zero imports from `org.lwjgl.*`
- `graphics/vulkan/` has zero imports from `org.lwjgl.*`
- `graphics/webgpu/` has zero imports from `webgpu.*` or `idl.*`
- All 60 screenshot tests pass on all 3 backends
- Constructor injection used throughout — no hidden dependencies
- Provider modules can be swapped without changing backend code
