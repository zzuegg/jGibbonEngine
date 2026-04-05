package dev.engine.graphics.opengl;

import dev.engine.core.handle.Handle;
import dev.engine.graphics.BufferResource;
import dev.engine.graphics.CapabilityRegistry;
import dev.engine.graphics.DeviceCapability;
import dev.engine.graphics.PipelineResource;
import dev.engine.graphics.RenderDevice;
import dev.engine.graphics.RenderTargetResource;
import dev.engine.graphics.SamplerResource;
import dev.engine.graphics.TextureResource;
import dev.engine.graphics.VertexInputResource;
import dev.engine.graphics.command.CommandList;
import dev.engine.graphics.command.RenderCommand;
import dev.engine.graphics.pipeline.ComputePipelineDescriptor;
import dev.engine.graphics.pipeline.PipelineDescriptor;
import dev.engine.graphics.pipeline.ShaderCompilationException;
import dev.engine.graphics.pipeline.ShaderSource;
import dev.engine.graphics.pipeline.ShaderStage;
import dev.engine.core.mesh.ComponentType;
import dev.engine.core.mesh.VertexAttribute;
import dev.engine.core.mesh.VertexFormat;
import dev.engine.graphics.target.RenderTargetDescriptor;
import dev.engine.graphics.buffer.AccessPattern;
import dev.engine.graphics.buffer.BufferDescriptor;
import dev.engine.graphics.buffer.BufferUsage;
import dev.engine.graphics.buffer.BufferWriter;
import dev.engine.graphics.buffer.StreamingBuffer;
import dev.engine.graphics.sync.GpuFence;
import dev.engine.graphics.resource.ResourceRegistry;
import dev.engine.graphics.sampler.FilterMode;
import dev.engine.graphics.sampler.SamplerDescriptor;
import dev.engine.graphics.sampler.WrapMode;
import dev.engine.graphics.renderstate.*;
import dev.engine.graphics.texture.MipMode;
import dev.engine.graphics.texture.TextureDescriptor;
import dev.engine.graphics.texture.TextureFormat;
import dev.engine.graphics.texture.TextureType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.engine.core.memory.NativeMemory;
import dev.engine.core.memory.SegmentNativeMemory;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class GlRenderDevice implements RenderDevice {

    private static final Logger log = LoggerFactory.getLogger(GlRenderDevice.class);

    private record GlBuffer(int glName, long size) {}
    private final ResourceRegistry<BufferResource, GlBuffer> buffers = new ResourceRegistry<>("buffer");

    private record GlTexture(int glName, TextureDescriptor desc) {}
    private final ResourceRegistry<TextureResource, GlTexture> textures = new ResourceRegistry<>("texture");

    private record GlVertexInput(int vao, int stride) {}
    private final ResourceRegistry<VertexInputResource, GlVertexInput> vertexInputs = new ResourceRegistry<>("vertex-input");

    private record GlRenderTarget(int fbo, List<Handle<TextureResource>> colorTextures) {}
    private final ResourceRegistry<RenderTargetResource, GlRenderTarget> renderTargets = new ResourceRegistry<>("render-target");

    private final Map<Integer, Boolean> textureMipsDirty = new HashMap<>();

    private record GlSampler(int glName, SamplerDescriptor desc) {}
    private final ResourceRegistry<SamplerResource, GlSampler> samplers = new ResourceRegistry<>("sampler");

    @SuppressWarnings("unchecked")
    private final Handle<TextureResource>[] boundTextures = new Handle[32];
    @SuppressWarnings("unchecked")
    private final Handle<SamplerResource>[] boundSamplers = new Handle[32];
    private Handle<RenderTargetResource> currentRenderTarget;

    private final ResourceRegistry<PipelineResource, Integer> pipelines = new ResourceRegistry<>("pipeline");
    private final AtomicLong frameCounter = new AtomicLong(0);
    private final CapabilityRegistry capabilities = new CapabilityRegistry();
    private final dev.engine.graphics.window.WindowHandle window;
    private final int pushConstantUbo;
    private final GlBindings gl;

    public GlRenderDevice(dev.engine.graphics.window.WindowHandle window, GlBindings gl) {
        this.gl = gl;
        this.window = window;
        gl.makeContextCurrent(window.nativeHandle());
        gl.createCapabilities();
        log.info("OpenGL context created: {}", gl.glGetString(GlBindings.GL_VERSION));

        pushConstantUbo = gl.glCreateBuffers();
        gl.glNamedBufferStorage(pushConstantUbo, 128, GlBindings.GL_DYNAMIC_STORAGE_BIT);

        registerCapabilities();
    }

    /** Returns the {@link GlBindings} instance used by this device. */
    public GlBindings glBindings() { return gl; }

    private void registerCapabilities() {
        // Limits (lazy — queried each time, cached by caller if needed)
        capabilities.register(DeviceCapability.MAX_TEXTURE_SIZE, () -> gl.glGetInteger(GlBindings.GL_MAX_TEXTURE_SIZE));
        capabilities.register(DeviceCapability.MAX_FRAMEBUFFER_WIDTH, () -> gl.glGetInteger(GlBindings.GL_MAX_FRAMEBUFFER_WIDTH));
        capabilities.register(DeviceCapability.MAX_FRAMEBUFFER_HEIGHT, () -> gl.glGetInteger(GlBindings.GL_MAX_FRAMEBUFFER_HEIGHT));
        capabilities.register(DeviceCapability.MAX_ANISOTROPY, () -> gl.glGetFloat(GlBindings.GL_MAX_TEXTURE_MAX_ANISOTROPY));
        capabilities.register(DeviceCapability.MAX_UNIFORM_BUFFER_SIZE, () -> gl.glGetInteger(GlBindings.GL_MAX_UNIFORM_BLOCK_SIZE));
        capabilities.register(DeviceCapability.MAX_STORAGE_BUFFER_SIZE, () -> gl.glGetInteger(GlBindings.GL_MAX_SHADER_STORAGE_BLOCK_SIZE));

        // Features
        capabilities.registerStatic(DeviceCapability.COMPUTE_SHADERS, true);
        capabilities.registerStatic(DeviceCapability.GEOMETRY_SHADERS, true);
        capabilities.registerStatic(DeviceCapability.TESSELLATION, true);
        capabilities.registerStatic(DeviceCapability.ANISOTROPIC_FILTERING, true);
        capabilities.register(DeviceCapability.BINDLESS_TEXTURES, this::hasBindlessTextures);

        // Device info
        capabilities.registerStatic(DeviceCapability.BACKEND_NAME, "OpenGL");
        capabilities.register(DeviceCapability.DEVICE_NAME, () -> gl.glGetString(GlBindings.GL_RENDERER));
        capabilities.register(DeviceCapability.API_VERSION, () -> gl.glGetString(GlBindings.GL_VERSION));
    }

    @Override
    public Handle<BufferResource> createBuffer(BufferDescriptor descriptor) {
        int glBuffer = gl.glCreateBuffers();
        int usage = mapUsage(descriptor.accessPattern());
        gl.glNamedBufferData(glBuffer, descriptor.size(), usage);

        return buffers.register(new GlBuffer(glBuffer, descriptor.size()));
    }

    @Override
    public void destroyBuffer(Handle<BufferResource> buffer) {
        if (!buffers.isValid(buffer)) return;
        var buf = buffers.remove(buffer);
        if (buf != null) gl.glDeleteBuffers(buf.glName());
    }

    @Override
    public boolean isValidBuffer(Handle<BufferResource> buffer) {
        return buffers.isValid(buffer);
    }

    @Override
    public BufferWriter writeBuffer(Handle<BufferResource> buffer) {
        var buf = buffers.get(buffer);
        long size = buf != null ? buf.size() : 0L;
        return writeBuffer(buffer, 0, size);
    }

    @Override
    public BufferWriter writeBuffer(Handle<BufferResource> buffer, long offset, long length) {
        int glName = buffers.get(buffer).glName();
        var arena = Arena.ofConfined();
        var segment = arena.allocate(length);
        var memory = new SegmentNativeMemory(segment);
        return new BufferWriter() {
            @Override
            public NativeMemory memory() { return memory; }

            @Override
            public void close() {
                gl.nglNamedBufferSubData(glName, offset, length, segment.address());
                arena.close();
            }
        };
    }

    @Override
    public Handle<TextureResource> createTexture(TextureDescriptor descriptor) {
        int glTarget = glTextureTarget(descriptor);
        int glTex = gl.glCreateTextures(glTarget);
        int internalFormat = mapTextureFormat(descriptor.format());
        int levels = computeMipLevels(descriptor);

        if (descriptor.type() == TextureType.TEXTURE_3D) {
            gl.glTextureStorage3D(glTex, levels, internalFormat,
                    descriptor.width(), descriptor.height(), descriptor.depth());
        } else if (descriptor.type() == TextureType.TEXTURE_2D_ARRAY) {
            gl.glTextureStorage3D(glTex, levels, internalFormat,
                    descriptor.width(), descriptor.height(), descriptor.layers());
        } else {
            // TEXTURE_2D and TEXTURE_CUBE both use glTextureStorage2D
            gl.glTextureStorage2D(glTex, levels, internalFormat, descriptor.width(), descriptor.height());
        }

        return textures.register(new GlTexture(glTex, descriptor));
    }

    @Override
    public void uploadTexture(Handle<TextureResource> texture, ByteBuffer pixels) {
        var tex = textures.get(texture);
        int glName = tex.glName();
        var desc = tex.desc();
        int[] formatAndType = mapUploadFormat(desc.format());

        if (desc.type() == TextureType.TEXTURE_3D) {
            gl.glTextureSubImage3D(glName, 0, 0, 0, 0,
                    desc.width(), desc.height(), desc.depth(),
                    formatAndType[0], formatAndType[1], pixels);
        } else if (desc.type() == TextureType.TEXTURE_2D_ARRAY) {
            gl.glTextureSubImage3D(glName, 0, 0, 0, 0,
                    desc.width(), desc.height(), desc.layers(),
                    formatAndType[0], formatAndType[1], pixels);
        } else if (desc.type() == TextureType.TEXTURE_CUBE) {
            // DSA cube maps: upload all 6 faces via glTextureSubImage3D with depth=6
            gl.glTextureSubImage3D(glName, 0, 0, 0, 0,
                    desc.width(), desc.height(), 6,
                    formatAndType[0], formatAndType[1], pixels);
        } else {
            gl.glTextureSubImage2D(glName, 0, 0, 0, desc.width(), desc.height(),
                    formatAndType[0], formatAndType[1], pixels);
        }
        textureMipsDirty.put(texture.index(), true);
    }

    @Override
    public void destroyTexture(Handle<TextureResource> texture) {
        if (!textures.isValid(texture)) return;
        var tex = textures.remove(texture);
        textureMipsDirty.remove(texture.index());
        if (tex != null) gl.glDeleteTextures(tex.glName());
    }

    @Override
    public boolean isValidTexture(Handle<TextureResource> texture) {
        return textures.isValid(texture);
    }

    @Override
    public Handle<RenderTargetResource> createRenderTarget(RenderTargetDescriptor descriptor) {
        int fbo = gl.glCreateFramebuffers();
        var colorTextures = new ArrayList<Handle<TextureResource>>();

        for (int i = 0; i < descriptor.colorAttachments().size(); i++) {
            var format = descriptor.colorAttachments().get(i);
            int glTex = gl.glCreateTextures(GlBindings.GL_TEXTURE_2D);
            gl.glTextureStorage2D(glTex, 1, mapTextureFormat(format), descriptor.width(), descriptor.height());
            gl.glNamedFramebufferTexture(fbo, GlBindings.GL_COLOR_ATTACHMENT0 + i, glTex, 0);

            var texHandle = textures.register(new GlTexture(glTex,
                    new dev.engine.graphics.texture.TextureDescriptor(
                            descriptor.width(), descriptor.height(), format)));
            colorTextures.add(texHandle);
        }

        if (descriptor.depthFormat() != null) {
            int depthTex = gl.glCreateTextures(GlBindings.GL_TEXTURE_2D);
            gl.glTextureStorage2D(depthTex, 1, mapTextureFormat(descriptor.depthFormat()),
                    descriptor.width(), descriptor.height());
            gl.glNamedFramebufferTexture(fbo, GlBindings.GL_DEPTH_ATTACHMENT, depthTex, 0);
        }

        return renderTargets.register(new GlRenderTarget(fbo, colorTextures));
    }

    @Override
    public Handle<TextureResource> getRenderTargetColorTexture(Handle<RenderTargetResource> renderTarget, int index) {
        return renderTargets.get(renderTarget).colorTextures().get(index);
    }

    @Override
    public void destroyRenderTarget(Handle<RenderTargetResource> renderTarget) {
        if (!renderTargets.isValid(renderTarget)) return;
        var rt = renderTargets.remove(renderTarget);
        if (rt != null) {
            gl.glDeleteFramebuffers(rt.fbo());
            for (var tex : rt.colorTextures()) destroyTexture(tex);
        }
    }

    int getGlFboName(Handle<RenderTargetResource> renderTarget) {
        var rt = renderTargets.get(renderTarget);
        return rt != null ? rt.fbo() : 0;
    }

    @Override
    public Handle<VertexInputResource> createVertexInput(VertexFormat format) {
        int vao = gl.glCreateVertexArrays();
        for (var attr : format.attributes()) {
            gl.glEnableVertexArrayAttrib(vao, attr.location());
            int glType = mapComponentType(attr.componentType());
            gl.glVertexArrayAttribFormat(vao, attr.location(),
                    attr.componentCount(), glType, attr.normalized(), attr.offset());
            gl.glVertexArrayAttribBinding(vao, attr.location(), 0); // binding point 0
        }
        // Set per-instance divisors for instanced attributes
        for (var attr : format.attributes()) {
            if (attr.divisor() > 0) {
                gl.glBindVertexArray(vao);
                gl.glVertexAttribDivisor(attr.location(), attr.divisor());
                gl.glBindVertexArray(0);
            }
        }

        return vertexInputs.register(new GlVertexInput(vao, format.stride()));
    }

    @Override
    public void destroyVertexInput(Handle<VertexInputResource> vertexInput) {
        if (!vertexInputs.isValid(vertexInput)) return;
        var vi = vertexInputs.remove(vertexInput);
        if (vi != null) gl.glDeleteVertexArrays(vi.vao());
    }

    int getGlVaoName(Handle<VertexInputResource> vertexInput) {
        var vi = vertexInputs.get(vertexInput);
        return vi != null ? vi.vao() : 0;
    }

    int getVertexInputStride(Handle<VertexInputResource> vertexInput) {
        var vi = vertexInputs.get(vertexInput);
        return vi != null ? vi.stride() : 0;
    }

    @Override
    public Handle<SamplerResource> createSampler(SamplerDescriptor descriptor) {
        int glSampler = gl.glCreateSamplers();
        gl.glSamplerParameteri(glSampler, GlBindings.GL_TEXTURE_MIN_FILTER, mapFilterMode(descriptor.minFilter()));
        gl.glSamplerParameteri(glSampler, GlBindings.GL_TEXTURE_MAG_FILTER, mapFilterMode(descriptor.magFilter()));
        gl.glSamplerParameteri(glSampler, GlBindings.GL_TEXTURE_WRAP_S, mapWrapMode(descriptor.wrapS()));
        gl.glSamplerParameteri(glSampler, GlBindings.GL_TEXTURE_WRAP_T, mapWrapMode(descriptor.wrapT()));
        gl.glSamplerParameteri(glSampler, GlBindings.GL_TEXTURE_WRAP_R, mapWrapMode(descriptor.wrapR()));
        gl.glSamplerParameterf(glSampler, GlBindings.GL_TEXTURE_MIN_LOD, descriptor.minLod());
        gl.glSamplerParameterf(glSampler, GlBindings.GL_TEXTURE_MAX_LOD, descriptor.maxLod());
        gl.glSamplerParameterf(glSampler, GlBindings.GL_TEXTURE_LOD_BIAS, descriptor.lodBias());
        if (descriptor.maxAnisotropy() > 1f) {
            gl.glSamplerParameterf(glSampler, GlBindings.GL_MAX_TEXTURE_MAX_ANISOTROPY, descriptor.maxAnisotropy());
        }
        if (descriptor.compareFunc() != null) {
            gl.glSamplerParameteri(glSampler, GlBindings.GL_TEXTURE_COMPARE_MODE, GlBindings.GL_COMPARE_REF_TO_TEXTURE);
            gl.glSamplerParameteri(glSampler, GlBindings.GL_TEXTURE_COMPARE_FUNC, mapCompareFunc(descriptor.compareFunc()));
        }
        if (descriptor.wrapS() == dev.engine.graphics.sampler.WrapMode.CLAMP_TO_BORDER
                || descriptor.wrapT() == dev.engine.graphics.sampler.WrapMode.CLAMP_TO_BORDER
                || descriptor.wrapR() == dev.engine.graphics.sampler.WrapMode.CLAMP_TO_BORDER) {
            gl.glSamplerParameterfv(glSampler, GlBindings.GL_TEXTURE_BORDER_COLOR, mapBorderColor(descriptor.borderColor()));
        }

        return samplers.register(new GlSampler(glSampler, descriptor));
    }

    @Override
    public void destroySampler(Handle<SamplerResource> sampler) {
        if (!samplers.isValid(sampler)) return;
        var s = samplers.remove(sampler);
        if (s != null) gl.glDeleteSamplers(s.glName());
    }

    int getGlSamplerName(Handle<SamplerResource> sampler) {
        var s = samplers.get(sampler);
        return s != null ? s.glName() : 0;
    }

    int getGlProgramName(Handle<PipelineResource> pipeline) {
        var p = pipelines.get(pipeline);
        return p != null ? p : 0;
    }

    private static int mapFilterMode(FilterMode mode) {
        if (mode == FilterMode.NEAREST) return GlBindings.GL_NEAREST;
        if (mode == FilterMode.LINEAR) return GlBindings.GL_LINEAR;
        if (mode == FilterMode.NEAREST_MIPMAP_NEAREST) return GlBindings.GL_NEAREST_MIPMAP_NEAREST;
        if (mode == FilterMode.NEAREST_MIPMAP_LINEAR) return GlBindings.GL_NEAREST_MIPMAP_LINEAR;
        if (mode == FilterMode.LINEAR_MIPMAP_NEAREST) return GlBindings.GL_LINEAR_MIPMAP_NEAREST;
        if (mode == FilterMode.LINEAR_MIPMAP_LINEAR) return GlBindings.GL_LINEAR_MIPMAP_LINEAR;
        return GlBindings.GL_LINEAR;
    }

    private static int mapWrapMode(WrapMode mode) {
        if (mode == WrapMode.REPEAT) return GlBindings.GL_REPEAT;
        if (mode == WrapMode.CLAMP_TO_EDGE) return GlBindings.GL_CLAMP_TO_EDGE;
        if (mode == WrapMode.MIRRORED_REPEAT) return GlBindings.GL_MIRRORED_REPEAT;
        if (mode == WrapMode.CLAMP_TO_BORDER) return GlBindings.GL_CLAMP_TO_BORDER;
        return GlBindings.GL_REPEAT;
    }

    private static int mapCompareFunc(dev.engine.graphics.sampler.CompareFunc func) {
        if (func == dev.engine.graphics.sampler.CompareFunc.NEVER) return GlBindings.GL_NEVER;
        if (func == dev.engine.graphics.sampler.CompareFunc.LESS) return GlBindings.GL_LESS;
        if (func == dev.engine.graphics.sampler.CompareFunc.EQUAL) return GlBindings.GL_EQUAL;
        if (func == dev.engine.graphics.sampler.CompareFunc.LESS_EQUAL) return GlBindings.GL_LEQUAL;
        if (func == dev.engine.graphics.sampler.CompareFunc.GREATER) return GlBindings.GL_GREATER;
        if (func == dev.engine.graphics.sampler.CompareFunc.NOT_EQUAL) return GlBindings.GL_NOTEQUAL;
        if (func == dev.engine.graphics.sampler.CompareFunc.GREATER_EQUAL) return GlBindings.GL_GEQUAL;
        if (func == dev.engine.graphics.sampler.CompareFunc.ALWAYS) return GlBindings.GL_ALWAYS;
        return GlBindings.GL_LESS;
    }

    private static float[] mapBorderColor(dev.engine.graphics.sampler.BorderColor color) {
        if (color == dev.engine.graphics.sampler.BorderColor.OPAQUE_BLACK) return new float[]{0, 0, 0, 1};
        if (color == dev.engine.graphics.sampler.BorderColor.OPAQUE_WHITE) return new float[]{1, 1, 1, 1};
        return new float[]{0, 0, 0, 0}; // TRANSPARENT_BLACK
    }

    private static int mapComponentType(ComponentType type) {
        if (type == ComponentType.FLOAT) return GlBindings.GL_FLOAT;
        if (type == ComponentType.BYTE) return GlBindings.GL_BYTE;
        if (type == ComponentType.INT) return GlBindings.GL_INT;
        return GlBindings.GL_FLOAT;
    }

    @Override
    public Handle<PipelineResource> createPipeline(PipelineDescriptor descriptor) {
        int program = gl.glCreateProgram();
        var shaderIds = new java.util.ArrayList<Integer>();

        try {
            for (var shaderSource : descriptor.shaders()) {
                int type = mapShaderStage(shaderSource.stage());
                int shader = gl.glCreateShader(type);
                gl.glShaderSource(shader, shaderSource.source());
                gl.glCompileShader(shader);

                if (gl.glGetShaderi(shader, GlBindings.GL_COMPILE_STATUS) == GlBindings.GL_FALSE) {
                    String infoLog = gl.glGetShaderInfoLog(shader);
                    gl.glDeleteShader(shader);
                    gl.glDeleteProgram(program);
                    throw new ShaderCompilationException(
                            "Failed to compile " + shaderSource.stage().name() + " shader: " + infoLog);
                }
                shaderIds.add(shader);
                gl.glAttachShader(program, shader);
            }

            gl.glLinkProgram(program);
            if (gl.glGetProgrami(program, GlBindings.GL_LINK_STATUS) == GlBindings.GL_FALSE) {
                String infoLog = gl.glGetProgramInfoLog(program);
                gl.glDeleteProgram(program);
                throw new ShaderCompilationException("Failed to link program: " + infoLog);
            }
        } finally {
            for (int shader : shaderIds) {
                gl.glDeleteShader(shader);
            }
        }

        return pipelines.register(program);
    }

    @Override
    public void destroyPipeline(Handle<PipelineResource> pipeline) {
        if (!pipelines.isValid(pipeline)) return;
        var program = pipelines.remove(pipeline);
        if (program != null) gl.glDeleteProgram(program);
    }

    @Override
    public Handle<PipelineResource> createComputePipeline(ComputePipelineDescriptor descriptor) {
        int program = gl.glCreateProgram();

        ShaderSource source = descriptor.shader();
        if (source == null) {
            gl.glDeleteProgram(program);
            throw new UnsupportedOperationException("OpenGL compute requires GLSL source, not SPIRV");
        }

        int shader = gl.glCreateShader(GlBindings.GL_COMPUTE_SHADER);
        gl.glShaderSource(shader, source.source());
        gl.glCompileShader(shader);
        if (gl.glGetShaderi(shader, GlBindings.GL_COMPILE_STATUS) == GlBindings.GL_FALSE) {
            String infoLog = gl.glGetShaderInfoLog(shader);
            gl.glDeleteShader(shader);
            gl.glDeleteProgram(program);
            throw new ShaderCompilationException("Compute shader compilation failed: " + infoLog);
        }

        gl.glAttachShader(program, shader);
        gl.glLinkProgram(program);
        if (gl.glGetProgrami(program, GlBindings.GL_LINK_STATUS) == GlBindings.GL_FALSE) {
            String infoLog = gl.glGetProgramInfoLog(program);
            gl.glDeleteShader(shader);
            gl.glDeleteProgram(program);
            throw new ShaderCompilationException("Compute program link failed: " + infoLog);
        }

        gl.glDeleteShader(shader);
        return pipelines.register(program);
    }

    @Override
    public boolean isValidPipeline(Handle<PipelineResource> pipeline) {
        return pipelines.isValid(pipeline);
    }

    private static int mapShaderStage(ShaderStage stage) {
        if (stage == ShaderStage.VERTEX) return GlBindings.GL_VERTEX_SHADER;
        if (stage == ShaderStage.FRAGMENT) return GlBindings.GL_FRAGMENT_SHADER;
        if (stage == ShaderStage.GEOMETRY) return GlBindings.GL_GEOMETRY_SHADER;
        if (stage == ShaderStage.COMPUTE) return GlBindings.GL_COMPUTE_SHADER;
        throw new IllegalArgumentException("Unknown shader stage: " + stage.name());
    }

    public int getGlTextureName(Handle<TextureResource> texture) {
        var tex = textures.get(texture);
        return tex != null ? tex.glName() : 0;
    }

    public int getGlBufferName(Handle<BufferResource> buffer) {
        var buf = buffers.get(buffer);
        return buf != null ? buf.glName() : 0;
    }

    @Override
    public void beginFrame() {
        frameCounter.incrementAndGet();
    }

    @Override
    public void endFrame() {
        window.swapBuffers();
    }

    @Override
    public void submit(CommandList commands) {
        for (var command : commands.commands()) {
            executeCommand(command);
        }
    }

    private void executeCommand(RenderCommand command) {
        switch (command) {
            case RenderCommand.BindPipeline cmd -> {
                int program = getGlProgramName(cmd.pipeline());
                gl.glUseProgram(program);
            }
            case RenderCommand.BindVertexBuffer cmd -> {
                int vao = getGlVaoName(cmd.vertexInput());
                int vbo = getGlBufferName(cmd.buffer());
                gl.glBindVertexArray(vao);
                int stride = getVertexInputStride(cmd.vertexInput());
                gl.glVertexArrayVertexBuffer(vao, 0, vbo, 0, stride);
            }
            case RenderCommand.BindIndexBuffer cmd -> {
                int ibo = getGlBufferName(cmd.buffer());
                gl.glBindBuffer(GlBindings.GL_ELEMENT_ARRAY_BUFFER, ibo);
            }
            case RenderCommand.BindUniformBuffer cmd -> {
                int ubo = getGlBufferName(cmd.buffer());
                gl.glBindBufferBase(GlBindings.GL_UNIFORM_BUFFER, cmd.binding(), ubo);
            }
            case RenderCommand.BindTexture cmd -> {
                int glTex = getGlTextureName(cmd.texture());
                gl.glBindTextureUnit(cmd.unit(), glTex);
                boundTextures[cmd.unit()] = cmd.texture();
                maybeGenerateMipmaps(cmd.unit());
            }
            case RenderCommand.BindSampler cmd -> {
                gl.glBindSampler(cmd.unit(), getGlSamplerName(cmd.sampler()));
                boundSamplers[cmd.unit()] = cmd.sampler();
                maybeGenerateMipmaps(cmd.unit());
            }
            case RenderCommand.BindStorageBuffer cmd -> {
                int ssbo = getGlBufferName(cmd.buffer());
                gl.glBindBufferBase(GlBindings.GL_SHADER_STORAGE_BUFFER, cmd.binding(), ssbo);
            }
            case RenderCommand.Draw cmd -> {
                gl.glDrawArrays(GlBindings.GL_TRIANGLES, cmd.firstVertex(), cmd.vertexCount());
            }
            case RenderCommand.DrawIndexed cmd -> {
                gl.glDrawElements(GlBindings.GL_TRIANGLES, cmd.indexCount(), GlBindings.GL_UNSIGNED_INT,
                        (long) cmd.firstIndex() * Integer.BYTES);
            }
            case RenderCommand.DrawInstanced cmd -> {
                gl.glDrawArraysInstancedBaseInstance(GlBindings.GL_TRIANGLES, cmd.firstVertex(), cmd.vertexCount(), cmd.instanceCount(), cmd.firstInstance());
            }
            case RenderCommand.DrawIndexedInstanced cmd -> {
                gl.glDrawElementsInstancedBaseInstance(GlBindings.GL_TRIANGLES, cmd.indexCount(), GlBindings.GL_UNSIGNED_INT,
                        (long) cmd.firstIndex() * Integer.BYTES, cmd.instanceCount(), cmd.firstInstance());
            }
            case RenderCommand.DrawIndirect(var buffer, long offset, int drawCount, int stride) -> {
                int buf = buffers.get(buffer).glName();
                gl.glBindBuffer(GlBindings.GL_DRAW_INDIRECT_BUFFER, buf);
                if (drawCount == 1) {
                    gl.glDrawArraysIndirect(GlBindings.GL_TRIANGLES, offset);
                } else {
                    gl.glMultiDrawArraysIndirect(GlBindings.GL_TRIANGLES, offset, drawCount, stride == 0 ? 16 : stride);
                }
            }
            case RenderCommand.DrawIndexedIndirect(var buffer, long offset, int drawCount, int stride) -> {
                int buf = buffers.get(buffer).glName();
                gl.glBindBuffer(GlBindings.GL_DRAW_INDIRECT_BUFFER, buf);
                if (drawCount == 1) {
                    gl.glDrawElementsIndirect(GlBindings.GL_TRIANGLES, GlBindings.GL_UNSIGNED_INT, offset);
                } else {
                    gl.glMultiDrawElementsIndirect(GlBindings.GL_TRIANGLES, GlBindings.GL_UNSIGNED_INT, offset, drawCount, stride == 0 ? 20 : stride);
                }
            }
            case RenderCommand.BindRenderTarget cmd -> {
                int fbo = getGlFboName(cmd.renderTarget());
                gl.glBindFramebuffer(GlBindings.GL_FRAMEBUFFER, fbo);
                var rt = renderTargets.get(cmd.renderTarget());
                if (rt != null && rt.colorTextures().size() > 1) {
                    int[] drawBuffers = new int[rt.colorTextures().size()];
                    for (int i = 0; i < drawBuffers.length; i++) drawBuffers[i] = GlBindings.GL_COLOR_ATTACHMENT0 + i;
                    gl.glDrawBuffers(drawBuffers);
                }
                currentRenderTarget = cmd.renderTarget();
            }
            case RenderCommand.BindDefaultRenderTarget cmd -> {
                // Mark all color attachment textures of the previous render target as mips-dirty
                if (currentRenderTarget != null) {
                    var rt = renderTargets.get(currentRenderTarget);
                    if (rt != null) {
                        for (var tex : rt.colorTextures()) {
                            textureMipsDirty.put(tex.index(), true);
                        }
                    }
                }
                gl.glBindFramebuffer(GlBindings.GL_FRAMEBUFFER, 0);
                currentRenderTarget = null;
            }
            case RenderCommand.SetDepthTest cmd -> {
                if (cmd.enabled()) gl.glEnable(GlBindings.GL_DEPTH_TEST);
                else gl.glDisable(GlBindings.GL_DEPTH_TEST);
            }
            case RenderCommand.SetBlending cmd -> {
                applyBlendMode(cmd.enabled() ? BlendMode.ALPHA : BlendMode.NONE);
            }
            case RenderCommand.SetCullFace cmd -> {
                if (cmd.enabled()) {
                    gl.glEnable(GlBindings.GL_CULL_FACE);
                    gl.glCullFace(GlBindings.GL_BACK);
                    gl.glFrontFace(GlBindings.GL_CCW);
                } else {
                    gl.glDisable(GlBindings.GL_CULL_FACE);
                }
            }
            case RenderCommand.SetWireframe cmd -> {
                gl.glPolygonMode(GlBindings.GL_FRONT_AND_BACK, cmd.enabled() ? GlBindings.GL_LINE : GlBindings.GL_FILL);
            }
            case RenderCommand.Clear cmd -> {
                gl.glClearColor(cmd.r(), cmd.g(), cmd.b(), cmd.a());
                gl.glClear(GlBindings.GL_COLOR_BUFFER_BIT | GlBindings.GL_DEPTH_BUFFER_BIT);
            }
            case RenderCommand.Viewport cmd -> {
                gl.glViewport(cmd.x(), cmd.y(), cmd.width(), cmd.height());
            }
            case RenderCommand.Scissor cmd -> {
                gl.glScissor(cmd.x(), cmd.y(), cmd.width(), cmd.height());
            }
            case RenderCommand.SetRenderState(var props) -> {
                if (props.contains(RenderState.DEPTH_TEST)) {
                    if (props.get(RenderState.DEPTH_TEST)) gl.glEnable(GlBindings.GL_DEPTH_TEST);
                    else gl.glDisable(GlBindings.GL_DEPTH_TEST);
                }
                if (props.contains(RenderState.DEPTH_WRITE)) {
                    gl.glDepthMask(props.get(RenderState.DEPTH_WRITE));
                }
                if (props.contains(RenderState.DEPTH_FUNC)) {
                    gl.glDepthFunc(mapCompareFunc(props.get(RenderState.DEPTH_FUNC)));
                }
                if (props.contains(RenderState.BLEND_MODE)) {
                    applyBlendMode(props.get(RenderState.BLEND_MODE));
                }
                if (props.contains(RenderState.BLEND_MODES)) {
                    applyBlendModes(props.get(RenderState.BLEND_MODES));
                }
                if (props.contains(RenderState.CULL_MODE)) {
                    applyCullMode(props.get(RenderState.CULL_MODE));
                }
                if (props.contains(RenderState.FRONT_FACE)) {
                    gl.glFrontFace(props.get(RenderState.FRONT_FACE) == FrontFace.CCW ? GlBindings.GL_CCW : GlBindings.GL_CW);
                }
                if (props.contains(RenderState.WIREFRAME)) {
                    gl.glPolygonMode(GlBindings.GL_FRONT_AND_BACK, props.get(RenderState.WIREFRAME) ? GlBindings.GL_LINE : GlBindings.GL_FILL);
                }
                if (props.contains(RenderState.LINE_WIDTH)) {
                    gl.glLineWidth(props.get(RenderState.LINE_WIDTH));
                }
                if (props.contains(RenderState.SCISSOR_TEST)) {
                    if (props.get(RenderState.SCISSOR_TEST)) gl.glEnable(GlBindings.GL_SCISSOR_TEST);
                    else gl.glDisable(GlBindings.GL_SCISSOR_TEST);
                }
                if (props.contains(RenderState.STENCIL_TEST)) {
                    if (props.get(RenderState.STENCIL_TEST)) gl.glEnable(GlBindings.GL_STENCIL_TEST);
                    else gl.glDisable(GlBindings.GL_STENCIL_TEST);
                }
                if (props.contains(RenderState.STENCIL_FUNC)) {
                    int ref = props.contains(RenderState.STENCIL_REF) ? props.get(RenderState.STENCIL_REF) : 0;
                    int mask = props.contains(RenderState.STENCIL_MASK) ? props.get(RenderState.STENCIL_MASK) : 0xFF;
                    gl.glStencilFunc(mapCompareFunc(props.get(RenderState.STENCIL_FUNC)), ref, mask);
                }
                if (props.contains(RenderState.STENCIL_FAIL)) {
                    StencilOp fail = props.get(RenderState.STENCIL_FAIL);
                    StencilOp depthFail = props.contains(RenderState.STENCIL_DEPTH_FAIL) ? props.get(RenderState.STENCIL_DEPTH_FAIL) : StencilOp.KEEP;
                    StencilOp pass = props.contains(RenderState.STENCIL_PASS) ? props.get(RenderState.STENCIL_PASS) : StencilOp.KEEP;
                    gl.glStencilOp(mapStencilOp(fail), mapStencilOp(depthFail), mapStencilOp(pass));
                }
            }
            case RenderCommand.PushConstants(var data) -> {
                data.rewind();
                gl.glNamedBufferSubData(pushConstantUbo, 0, data);
                gl.glBindBufferBase(GlBindings.GL_UNIFORM_BUFFER, 15, pushConstantUbo);
            }
            case RenderCommand.BindComputePipeline(var pipeline) -> {
                int program = getGlProgramName(pipeline);
                gl.glUseProgram(program);
            }
            case RenderCommand.Dispatch(int gx, int gy, int gz) -> {
                gl.glDispatchCompute(gx, gy, gz);
            }
            case RenderCommand.CopyBuffer(var src, var dst, long srcOff, long dstOff, long size) -> {
                int srcBuf = buffers.get(src).glName();
                int dstBuf = buffers.get(dst).glName();
                gl.glCopyNamedBufferSubData(srcBuf, dstBuf, srcOff, dstOff, size);
            }
            case RenderCommand.CopyTexture(var src, var dst, int sx, int sy, int dx, int dy, int w, int h, int srcMip, int dstMip) -> {
                var srcInfo = textures.get(src);
                var dstInfo = textures.get(dst);
                gl.glCopyImageSubData(srcInfo.glName(), glTextureTarget(srcInfo.desc()), srcMip, sx, sy, 0,
                                        dstInfo.glName(), glTextureTarget(dstInfo.desc()), dstMip, dx, dy, 0, w, h, 1);
            }
            case RenderCommand.BlitTexture(var src, var dst,
                    int sx0, int sy0, int sx1, int sy1,
                    int dx0, int dy0, int dx1, int dy1, boolean linear) -> {
                // BlitFramebuffer requires FBO binding — create temp FBOs
                int srcFbo = gl.glCreateFramebuffers();
                int dstFbo = gl.glCreateFramebuffers();
                int srcTex = textures.get(src).glName();
                int dstTex = textures.get(dst).glName();
                gl.glNamedFramebufferTexture(srcFbo, GlBindings.GL_COLOR_ATTACHMENT0, srcTex, 0);
                gl.glNamedFramebufferTexture(dstFbo, GlBindings.GL_COLOR_ATTACHMENT0, dstTex, 0);
                gl.glBlitNamedFramebuffer(srcFbo, dstFbo,
                    sx0, sy0, sx1, sy1, dx0, dy0, dx1, dy1,
                    GlBindings.GL_COLOR_BUFFER_BIT, linear ? GlBindings.GL_LINEAR : GlBindings.GL_NEAREST);
                gl.glDeleteFramebuffers(srcFbo);
                gl.glDeleteFramebuffers(dstFbo);
            }
            case RenderCommand.BindImage(int unit, var texture, int mipLevel, boolean read, boolean write) -> {
                var texInfo = textures.get(texture);
                int glTex = texInfo.glName();
                int internalFormat = mapTextureFormat(texInfo.desc().format());
                int access = GlBindings.GL_READ_WRITE;
                if (read && !write) access = GlBindings.GL_READ_ONLY;
                else if (!read && write) access = GlBindings.GL_WRITE_ONLY;
                boolean layered = texInfo.desc().type() == TextureType.TEXTURE_3D
                        || texInfo.desc().type() == TextureType.TEXTURE_2D_ARRAY
                        || texInfo.desc().type() == TextureType.TEXTURE_CUBE;
                gl.glBindImageTexture(unit, glTex, mipLevel, layered, 0, access, internalFormat);
            }
            case RenderCommand.MemoryBarrier(var scope) -> {
                int bits;
                if (scope == dev.engine.graphics.renderstate.BarrierScope.STORAGE_BUFFER) {
                    bits = GlBindings.GL_SHADER_STORAGE_BARRIER_BIT;
                } else if (scope == dev.engine.graphics.renderstate.BarrierScope.TEXTURE) {
                    bits = GlBindings.GL_TEXTURE_FETCH_BARRIER_BIT;
                } else {
                    bits = GlBindings.GL_ALL_BARRIER_BITS;
                }
                gl.glMemoryBarrier(bits);
            }
        }
    }

    @Override
    public <T> T queryCapability(DeviceCapability<T> capability) {
        return capabilities.query(capability);
    }

    /**
     * Returns the capability registry for this device.
     * Users can register custom capabilities without modifying engine code:
     * <pre>
     *   var MY_CAP = DeviceCapability.intCap("MY_CUSTOM_LIMIT");
     *   device.capabilities().register(MY_CAP, () -> gl.glGetInteger(MY_GL_CONSTANT));
     * </pre>
     */
    public CapabilityRegistry capabilities() { return capabilities; }

    private boolean hasBindlessTextures() {
        var extensions = gl.glGetString(GlBindings.GL_EXTENSIONS);
        if (extensions != null && extensions.contains("GL_ARB_bindless_texture")) return true;
        // Also check via glGetIntegerv GL_NUM_EXTENSIONS
        int numExt = gl.glGetInteger(GlBindings.GL_NUM_EXTENSIONS);
        for (int i = 0; i < numExt; i++) {
            var ext = gl.glGetStringi(GlBindings.GL_EXTENSIONS, i);
            if ("GL_ARB_bindless_texture".equals(ext)) return true;
        }
        return false;
    }

    @Override
    public long getBindlessTextureHandle(Handle<TextureResource> texture) {
        int glTex = getGlTextureName(texture);
        long handle = gl.glGetTextureHandleARB(glTex);
        gl.glMakeTextureHandleResidentARB(handle);
        return handle;
    }

    @Override
    public StreamingBuffer createStreamingBuffer(long frameSize, int frameCount, BufferUsage usage) {
        return new GlStreamingBuffer(this, frameSize, frameCount, usage);
    }

    @Override
    public GpuFence createFence() {
        return new GlFence(gl);
    }

    /**
     * Registers an externally created GL buffer (e.g. from a streaming buffer) in
     * the device's buffer pool so that {@link #getGlBufferName} works for it.
     */
    Handle<BufferResource> registerStreamingBuffer(int glBuffer, long size) {
        return buffers.register(new GlBuffer(glBuffer, size));
    }

    @Override
    public byte[] readFramebuffer(int width, int height) {
        java.nio.ByteBuffer pixels = java.nio.ByteBuffer.allocateDirect(width * height * 4);
        gl.glReadPixels(0, 0, width, height, GlBindings.GL_RGBA, GlBindings.GL_UNSIGNED_BYTE, pixels);
        byte[] rgba = new byte[width * height * 4];
        // Flip Y (OpenGL reads bottom-up)
        for (int y = 0; y < height; y++) {
            int srcRow = (height - 1 - y) * width * 4;
            int dstRow = y * width * 4;
            pixels.position(srcRow);
            pixels.get(rgba, dstRow, width * 4);
        }
        return rgba;
    }

    private void maybeGenerateMipmaps(int unit) {
        var texture = boundTextures[unit];
        var sampler = boundSamplers[unit];
        if (texture == null || sampler == null) return;

        var tex = textures.get(texture);
        if (tex == null || tex.desc().mipMode() == MipMode.NONE) return;

        Boolean dirty = textureMipsDirty.get(texture.index());
        if (dirty == null || !dirty) return;

        var s = samplers.get(sampler);
        SamplerDescriptor sd = s != null ? s.desc() : null;
        if (sd != null && usesMipmaps(sd)) {
            int glTex = getGlTextureName(texture);
            gl.glGenerateTextureMipmap(glTex);
            textureMipsDirty.put(texture.index(), false);
        }
    }

    private static boolean usesMipmaps(SamplerDescriptor desc) {
        return desc.minFilter() == FilterMode.NEAREST_MIPMAP_NEAREST
                || desc.minFilter() == FilterMode.LINEAR_MIPMAP_LINEAR
                || desc.minFilter() == FilterMode.NEAREST_MIPMAP_LINEAR
                || desc.minFilter() == FilterMode.LINEAR_MIPMAP_NEAREST;
    }

    private static int glTextureTarget(TextureDescriptor desc) {
        return switch (desc.type().name()) {
            case "TEXTURE_3D"       -> GlBindings.GL_TEXTURE_3D;
            case "TEXTURE_2D_ARRAY" -> GlBindings.GL_TEXTURE_2D_ARRAY;
            case "TEXTURE_CUBE"     -> GlBindings.GL_TEXTURE_CUBE_MAP;
            default                 -> GlBindings.GL_TEXTURE_2D;
        };
    }

    private static int computeMipLevels(TextureDescriptor descriptor) {
        if (descriptor.mipMode() == MipMode.AUTO) {
            return (int) Math.floor(Math.log(Math.max(descriptor.width(), descriptor.height())) / Math.log(2)) + 1;
        }
        return descriptor.mipMode().levelCount();
    }

    @Override
    public void close() {
        // Report leaked resources
        int leaks = buffers.reportLeaks()
                + textures.reportLeaks()
                + vertexInputs.reportLeaks()
                + renderTargets.reportLeaks()
                + samplers.reportLeaks()
                + pipelines.reportLeaks();
        if (leaks > 0) {
            log.warn("Total {} resource handle(s) leaked at GL device shutdown", leaks);
        }

        buffers.destroyAll(buf -> gl.glDeleteBuffers(buf.glName()));
        textures.destroyAll(tex -> gl.glDeleteTextures(tex.glName()));
        pipelines.destroyAll(gl::glDeleteProgram);
        vertexInputs.destroyAll(vi -> gl.glDeleteVertexArrays(vi.vao()));
        samplers.destroyAll(s -> gl.glDeleteSamplers(s.glName()));
        renderTargets.destroyAll(rt -> gl.glDeleteFramebuffers(rt.fbo()));
        gl.glDeleteBuffers(pushConstantUbo);
        log.info("GlRenderDevice closed");
    }

    private static int mapTextureFormat(TextureFormat format) {
        if (format == TextureFormat.RGBA8) return GlBindings.GL_RGBA8;
        if (format == TextureFormat.RGB8) return GlBindings.GL_RGB8;
        if (format == TextureFormat.R8) return GlBindings.GL_R8;
        if (format == TextureFormat.DEPTH24) return GlBindings.GL_DEPTH_COMPONENT24;
        if (format == TextureFormat.DEPTH32F) return GlBindings.GL_DEPTH_COMPONENT32F;
        if (format == TextureFormat.DEPTH24_STENCIL8) return GlBindings.GL_DEPTH24_STENCIL8;
        if (format == TextureFormat.DEPTH32F_STENCIL8) return GlBindings.GL_DEPTH32F_STENCIL8;
        return GlBindings.GL_RGBA8;
    }

    private static int[] mapUploadFormat(TextureFormat format) {
        if (format == TextureFormat.RGBA8) return new int[]{GlBindings.GL_RGBA, GlBindings.GL_UNSIGNED_BYTE};
        if (format == TextureFormat.RGB8) return new int[]{GlBindings.GL_RGB, GlBindings.GL_UNSIGNED_BYTE};
        if (format == TextureFormat.R8) return new int[]{GlBindings.GL_RED, GlBindings.GL_UNSIGNED_BYTE};
        if (format == TextureFormat.DEPTH24) return new int[]{GlBindings.GL_DEPTH_COMPONENT, GlBindings.GL_UNSIGNED_INT};
        if (format == TextureFormat.DEPTH32F) return new int[]{GlBindings.GL_DEPTH_COMPONENT, GlBindings.GL_FLOAT};
        if (format == TextureFormat.DEPTH24_STENCIL8) return new int[]{GlBindings.GL_DEPTH_STENCIL, GlBindings.GL_UNSIGNED_INT_24_8};
        if (format == TextureFormat.DEPTH32F_STENCIL8) return new int[]{GlBindings.GL_DEPTH_STENCIL, GlBindings.GL_FLOAT_32_UNSIGNED_INT_24_8_REV};
        return new int[]{GlBindings.GL_RGBA, GlBindings.GL_UNSIGNED_BYTE};
    }

    private static int mapUsage(AccessPattern pattern) {
        if (pattern == AccessPattern.STATIC) return GlBindings.GL_STATIC_DRAW;
        if (pattern == AccessPattern.DYNAMIC) return GlBindings.GL_DYNAMIC_DRAW;
        if (pattern == AccessPattern.STREAM) return GlBindings.GL_STREAM_DRAW;
        return GlBindings.GL_STATIC_DRAW;
    }

    private static int mapCompareFunc(CompareFunc func) {
        if (func == CompareFunc.LESS) return GlBindings.GL_LESS;
        if (func == CompareFunc.LEQUAL) return GlBindings.GL_LEQUAL;
        if (func == CompareFunc.GREATER) return GlBindings.GL_GREATER;
        if (func == CompareFunc.GEQUAL) return GlBindings.GL_GEQUAL;
        if (func == CompareFunc.EQUAL) return GlBindings.GL_EQUAL;
        if (func == CompareFunc.NOT_EQUAL) return GlBindings.GL_NOTEQUAL;
        if (func == CompareFunc.ALWAYS) return GlBindings.GL_ALWAYS;
        if (func == CompareFunc.NEVER) return GlBindings.GL_NEVER;
        return GlBindings.GL_LESS;
    }

    private static int mapStencilOp(StencilOp op) {
        if (op == StencilOp.KEEP) return GlBindings.GL_KEEP;
        if (op == StencilOp.ZERO) return GlBindings.GL_ZERO;
        if (op == StencilOp.REPLACE) return GlBindings.GL_REPLACE;
        if (op == StencilOp.INCR) return GlBindings.GL_INCR;
        if (op == StencilOp.DECR) return GlBindings.GL_DECR;
        if (op == StencilOp.INVERT) return GlBindings.GL_INVERT;
        if (op == StencilOp.INCR_WRAP) return GlBindings.GL_INCR_WRAP;
        if (op == StencilOp.DECR_WRAP) return GlBindings.GL_DECR_WRAP;
        return GlBindings.GL_KEEP;
    }

    private void applyBlendMode(BlendMode mode) {
        if (!mode.enabled()) {
            gl.glDisable(GlBindings.GL_BLEND);
        } else {
            gl.glEnable(GlBindings.GL_BLEND);
            gl.glBlendFuncSeparate(
                    mapBlendFactor(mode.srcColorFactor()), mapBlendFactor(mode.dstColorFactor()),
                    mapBlendFactor(mode.srcAlphaFactor()), mapBlendFactor(mode.dstAlphaFactor()));
            gl.glBlendEquationSeparate(
                    mapBlendEquation(mode.colorEquation()),
                    mapBlendEquation(mode.alphaEquation()));
        }
    }

    /**
     * Applies per-draw-buffer blend modes for MRT (GL 4.0+ indexed blend).
     * Index {@code i} of {@code modes} maps to draw buffer {@code i}; the last entry
     * is repeated for any extra buffers.
     */
    private void applyBlendModes(BlendMode[] modes) {
        if (modes == null || modes.length == 0) return;
        for (int i = 0; i < modes.length; i++) {
            BlendMode mode = modes[i];
            if (!mode.enabled()) {
                gl.glDisablei(GlBindings.GL_BLEND, i);
            } else {
                gl.glEnablei(GlBindings.GL_BLEND, i);
                gl.glBlendFuncSeparatei(i,
                        mapBlendFactor(mode.srcColorFactor()), mapBlendFactor(mode.dstColorFactor()),
                        mapBlendFactor(mode.srcAlphaFactor()), mapBlendFactor(mode.dstAlphaFactor()));
                gl.glBlendEquationSeparatei(i,
                        mapBlendEquation(mode.colorEquation()),
                        mapBlendEquation(mode.alphaEquation()));
            }
        }
    }

    private int mapBlendFactor(BlendFactor factor) {
        if (factor == BlendFactor.ZERO)                return GlBindings.GL_ZERO;
        if (factor == BlendFactor.ONE)                 return GlBindings.GL_ONE;
        if (factor == BlendFactor.SRC_COLOR)           return GlBindings.GL_SRC_COLOR;
        if (factor == BlendFactor.ONE_MINUS_SRC_COLOR) return GlBindings.GL_ONE_MINUS_SRC_COLOR;
        if (factor == BlendFactor.DST_COLOR)           return GlBindings.GL_DST_COLOR;
        if (factor == BlendFactor.ONE_MINUS_DST_COLOR) return GlBindings.GL_ONE_MINUS_DST_COLOR;
        if (factor == BlendFactor.SRC_ALPHA)           return GlBindings.GL_SRC_ALPHA;
        if (factor == BlendFactor.ONE_MINUS_SRC_ALPHA) return GlBindings.GL_ONE_MINUS_SRC_ALPHA;
        if (factor == BlendFactor.DST_ALPHA)           return GlBindings.GL_DST_ALPHA;
        if (factor == BlendFactor.ONE_MINUS_DST_ALPHA) return GlBindings.GL_ONE_MINUS_DST_ALPHA;
        return GlBindings.GL_ZERO;
    }

    private int mapBlendEquation(BlendEquation eq) {
        if (eq == BlendEquation.SUBTRACT)         return GlBindings.GL_FUNC_SUBTRACT;
        if (eq == BlendEquation.REVERSE_SUBTRACT) return GlBindings.GL_FUNC_REVERSE_SUBTRACT;
        if (eq == BlendEquation.MIN)              return GlBindings.GL_MIN;
        if (eq == BlendEquation.MAX)              return GlBindings.GL_MAX;
        return GlBindings.GL_FUNC_ADD;
    }

    private void applyCullMode(CullMode mode) {
        if (mode == CullMode.NONE) {
            gl.glDisable(GlBindings.GL_CULL_FACE);
        } else {
            gl.glEnable(GlBindings.GL_CULL_FACE);
            gl.glCullFace(mode == CullMode.FRONT ? GlBindings.GL_FRONT : GlBindings.GL_BACK);
        }
    }
}
