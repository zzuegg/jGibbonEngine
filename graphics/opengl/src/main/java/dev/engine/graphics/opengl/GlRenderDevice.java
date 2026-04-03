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
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL45;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private final long glfwWindow;
    private final int pushConstantUbo;

    public GlRenderDevice(dev.engine.graphics.window.WindowHandle window) {
        this.glfwWindow = window.nativeHandle();
        GLFW.glfwMakeContextCurrent(glfwWindow);
        GL.createCapabilities();
        log.info("OpenGL context created: {}", GL45.glGetString(GL45.GL_VERSION));

        pushConstantUbo = GL45.glCreateBuffers();
        GL45.glNamedBufferStorage(pushConstantUbo, 128, GL45.GL_DYNAMIC_STORAGE_BIT);

        registerCapabilities();
    }

    private void registerCapabilities() {
        // Limits (lazy — queried each time, cached by caller if needed)
        capabilities.register(DeviceCapability.MAX_TEXTURE_SIZE, () -> GL45.glGetInteger(GL45.GL_MAX_TEXTURE_SIZE));
        capabilities.register(DeviceCapability.MAX_FRAMEBUFFER_WIDTH, () -> GL45.glGetInteger(GL45.GL_MAX_FRAMEBUFFER_WIDTH));
        capabilities.register(DeviceCapability.MAX_FRAMEBUFFER_HEIGHT, () -> GL45.glGetInteger(GL45.GL_MAX_FRAMEBUFFER_HEIGHT));
        capabilities.register(DeviceCapability.MAX_ANISOTROPY, () -> GL45.glGetFloat(0x84FF));
        capabilities.register(DeviceCapability.MAX_UNIFORM_BUFFER_SIZE, () -> GL45.glGetInteger(GL45.GL_MAX_UNIFORM_BLOCK_SIZE));
        capabilities.register(DeviceCapability.MAX_STORAGE_BUFFER_SIZE, () -> GL45.glGetInteger(GL45.GL_MAX_SHADER_STORAGE_BLOCK_SIZE));

        // Features
        capabilities.registerStatic(DeviceCapability.COMPUTE_SHADERS, true);
        capabilities.registerStatic(DeviceCapability.GEOMETRY_SHADERS, true);
        capabilities.registerStatic(DeviceCapability.TESSELLATION, true);
        capabilities.registerStatic(DeviceCapability.ANISOTROPIC_FILTERING, true);
        capabilities.register(DeviceCapability.BINDLESS_TEXTURES, this::hasBindlessTextures);

        // Device info
        capabilities.registerStatic(DeviceCapability.BACKEND_NAME, "OpenGL");
        capabilities.register(DeviceCapability.DEVICE_NAME, () -> GL45.glGetString(GL45.GL_RENDERER));
        capabilities.register(DeviceCapability.API_VERSION, () -> GL45.glGetString(GL45.GL_VERSION));
    }

    @Override
    public Handle<BufferResource> createBuffer(BufferDescriptor descriptor) {
        int glBuffer = GL45.glCreateBuffers();
        int usage = mapUsage(descriptor.accessPattern());
        GL45.glNamedBufferData(glBuffer, descriptor.size(), usage);

        return buffers.register(new GlBuffer(glBuffer, descriptor.size()));
    }

    @Override
    public void destroyBuffer(Handle<BufferResource> buffer) {
        if (!buffers.isValid(buffer)) return;
        var buf = buffers.remove(buffer);
        if (buf != null) GL45.glDeleteBuffers(buf.glName());
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
        return new BufferWriter() {
            @Override
            public MemorySegment segment() { return segment; }

            @Override
            public void close() {
                GL45.nglNamedBufferSubData(glName, offset, length, segment.address());
                arena.close();
            }
        };
    }

    @Override
    public Handle<TextureResource> createTexture(TextureDescriptor descriptor) {
        int glTarget = switch (descriptor.type().name()) {
            case "TEXTURE_3D"       -> GL45.GL_TEXTURE_3D;
            case "TEXTURE_2D_ARRAY" -> GL45.GL_TEXTURE_2D_ARRAY;
            case "TEXTURE_CUBE"     -> GL45.GL_TEXTURE_CUBE_MAP;
            default                 -> GL45.GL_TEXTURE_2D;
        };

        int glTex = GL45.glCreateTextures(glTarget);
        int internalFormat = mapTextureFormat(descriptor.format());
        int levels = computeMipLevels(descriptor);

        if (descriptor.type() == TextureType.TEXTURE_3D) {
            GL45.glTextureStorage3D(glTex, levels, internalFormat,
                    descriptor.width(), descriptor.height(), descriptor.depth());
        } else if (descriptor.type() == TextureType.TEXTURE_2D_ARRAY) {
            GL45.glTextureStorage3D(glTex, levels, internalFormat,
                    descriptor.width(), descriptor.height(), descriptor.layers());
        } else {
            // TEXTURE_2D and TEXTURE_CUBE both use glTextureStorage2D
            GL45.glTextureStorage2D(glTex, levels, internalFormat, descriptor.width(), descriptor.height());
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
            GL45.glTextureSubImage3D(glName, 0, 0, 0, 0,
                    desc.width(), desc.height(), desc.depth(),
                    formatAndType[0], formatAndType[1], pixels);
        } else if (desc.type() == TextureType.TEXTURE_2D_ARRAY) {
            GL45.glTextureSubImage3D(glName, 0, 0, 0, 0,
                    desc.width(), desc.height(), desc.layers(),
                    formatAndType[0], formatAndType[1], pixels);
        } else {
            GL45.glTextureSubImage2D(glName, 0, 0, 0, desc.width(), desc.height(),
                    formatAndType[0], formatAndType[1], pixels);
        }
        textureMipsDirty.put(texture.index(), true);
    }

    @Override
    public void destroyTexture(Handle<TextureResource> texture) {
        if (!textures.isValid(texture)) return;
        var tex = textures.remove(texture);
        textureMipsDirty.remove(texture.index());
        if (tex != null) GL45.glDeleteTextures(tex.glName());
    }

    @Override
    public boolean isValidTexture(Handle<TextureResource> texture) {
        return textures.isValid(texture);
    }

    @Override
    public Handle<RenderTargetResource> createRenderTarget(RenderTargetDescriptor descriptor) {
        int fbo = GL45.glCreateFramebuffers();
        var colorTextures = new ArrayList<Handle<TextureResource>>();

        for (int i = 0; i < descriptor.colorAttachments().size(); i++) {
            var format = descriptor.colorAttachments().get(i);
            int glTex = GL45.glCreateTextures(GL45.GL_TEXTURE_2D);
            GL45.glTextureStorage2D(glTex, 1, mapTextureFormat(format), descriptor.width(), descriptor.height());
            GL45.glNamedFramebufferTexture(fbo, GL45.GL_COLOR_ATTACHMENT0 + i, glTex, 0);

            var texHandle = textures.register(new GlTexture(glTex,
                    new dev.engine.graphics.texture.TextureDescriptor(
                            descriptor.width(), descriptor.height(), format)));
            colorTextures.add(texHandle);
        }

        if (descriptor.depthFormat() != null) {
            int depthTex = GL45.glCreateTextures(GL45.GL_TEXTURE_2D);
            GL45.glTextureStorage2D(depthTex, 1, mapTextureFormat(descriptor.depthFormat()),
                    descriptor.width(), descriptor.height());
            GL45.glNamedFramebufferTexture(fbo, GL45.GL_DEPTH_ATTACHMENT, depthTex, 0);
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
            GL45.glDeleteFramebuffers(rt.fbo());
            for (var tex : rt.colorTextures()) destroyTexture(tex);
        }
    }

    int getGlFboName(Handle<RenderTargetResource> renderTarget) {
        var rt = renderTargets.get(renderTarget);
        return rt != null ? rt.fbo() : 0;
    }

    @Override
    public Handle<VertexInputResource> createVertexInput(VertexFormat format) {
        int vao = GL45.glCreateVertexArrays();
        for (var attr : format.attributes()) {
            GL45.glEnableVertexArrayAttrib(vao, attr.location());
            int glType = mapComponentType(attr.componentType());
            GL45.glVertexArrayAttribFormat(vao, attr.location(),
                    attr.componentCount(), glType, attr.normalized(), attr.offset());
            GL45.glVertexArrayAttribBinding(vao, attr.location(), 0); // binding point 0
        }
        // Set per-instance divisors for instanced attributes
        for (var attr : format.attributes()) {
            if (attr.divisor() > 0) {
                GL45.glBindVertexArray(vao);
                GL45.glVertexAttribDivisor(attr.location(), attr.divisor());
                GL45.glBindVertexArray(0);
            }
        }

        return vertexInputs.register(new GlVertexInput(vao, format.stride()));
    }

    @Override
    public void destroyVertexInput(Handle<VertexInputResource> vertexInput) {
        if (!vertexInputs.isValid(vertexInput)) return;
        var vi = vertexInputs.remove(vertexInput);
        if (vi != null) GL45.glDeleteVertexArrays(vi.vao());
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
        int glSampler = GL45.glCreateSamplers();
        GL45.glSamplerParameteri(glSampler, GL45.GL_TEXTURE_MIN_FILTER, mapFilterMode(descriptor.minFilter()));
        GL45.glSamplerParameteri(glSampler, GL45.GL_TEXTURE_MAG_FILTER, mapFilterMode(descriptor.magFilter()));
        GL45.glSamplerParameteri(glSampler, GL45.GL_TEXTURE_WRAP_S, mapWrapMode(descriptor.wrapS()));
        GL45.glSamplerParameteri(glSampler, GL45.GL_TEXTURE_WRAP_T, mapWrapMode(descriptor.wrapT()));

        return samplers.register(new GlSampler(glSampler, descriptor));
    }

    @Override
    public void destroySampler(Handle<SamplerResource> sampler) {
        if (!samplers.isValid(sampler)) return;
        var s = samplers.remove(sampler);
        if (s != null) GL45.glDeleteSamplers(s.glName());
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
        if (mode == FilterMode.NEAREST) return GL45.GL_NEAREST;
        if (mode == FilterMode.LINEAR) return GL45.GL_LINEAR;
        if (mode == FilterMode.NEAREST_MIPMAP_NEAREST) return GL45.GL_NEAREST_MIPMAP_NEAREST;
        if (mode == FilterMode.LINEAR_MIPMAP_LINEAR) return GL45.GL_LINEAR_MIPMAP_LINEAR;
        return GL45.GL_LINEAR;
    }

    private static int mapWrapMode(WrapMode mode) {
        if (mode == WrapMode.REPEAT) return GL45.GL_REPEAT;
        if (mode == WrapMode.CLAMP_TO_EDGE) return GL45.GL_CLAMP_TO_EDGE;
        if (mode == WrapMode.MIRRORED_REPEAT) return GL45.GL_MIRRORED_REPEAT;
        return GL45.GL_REPEAT;
    }

    private static int mapComponentType(ComponentType type) {
        if (type == ComponentType.FLOAT) return GL45.GL_FLOAT;
        if (type == ComponentType.BYTE) return GL45.GL_BYTE;
        if (type == ComponentType.INT) return GL45.GL_INT;
        return GL45.GL_FLOAT;
    }

    @Override
    public Handle<PipelineResource> createPipeline(PipelineDescriptor descriptor) {
        int program = GL45.glCreateProgram();
        var shaderIds = new java.util.ArrayList<Integer>();

        try {
            for (var shaderSource : descriptor.shaders()) {
                int type = mapShaderStage(shaderSource.stage());
                int shader = GL45.glCreateShader(type);
                GL45.glShaderSource(shader, shaderSource.source());
                GL45.glCompileShader(shader);

                if (GL45.glGetShaderi(shader, GL45.GL_COMPILE_STATUS) == GL45.GL_FALSE) {
                    String infoLog = GL45.glGetShaderInfoLog(shader);
                    GL45.glDeleteShader(shader);
                    GL45.glDeleteProgram(program);
                    throw new ShaderCompilationException(
                            "Failed to compile " + shaderSource.stage().name() + " shader: " + infoLog);
                }
                shaderIds.add(shader);
                GL45.glAttachShader(program, shader);
            }

            GL45.glLinkProgram(program);
            if (GL45.glGetProgrami(program, GL45.GL_LINK_STATUS) == GL45.GL_FALSE) {
                String infoLog = GL45.glGetProgramInfoLog(program);
                GL45.glDeleteProgram(program);
                throw new ShaderCompilationException("Failed to link program: " + infoLog);
            }
        } finally {
            for (int shader : shaderIds) {
                GL45.glDeleteShader(shader);
            }
        }

        return pipelines.register(program);
    }

    @Override
    public void destroyPipeline(Handle<PipelineResource> pipeline) {
        if (!pipelines.isValid(pipeline)) return;
        var program = pipelines.remove(pipeline);
        if (program != null) GL45.glDeleteProgram(program);
    }

    @Override
    public Handle<PipelineResource> createComputePipeline(ComputePipelineDescriptor descriptor) {
        int program = GL45.glCreateProgram();

        ShaderSource source = descriptor.shader();
        if (source == null) {
            GL45.glDeleteProgram(program);
            throw new UnsupportedOperationException("OpenGL compute requires GLSL source, not SPIRV");
        }

        int shader = GL45.glCreateShader(GL45.GL_COMPUTE_SHADER);
        GL45.glShaderSource(shader, source.source());
        GL45.glCompileShader(shader);
        if (GL45.glGetShaderi(shader, GL45.GL_COMPILE_STATUS) == GL45.GL_FALSE) {
            String infoLog = GL45.glGetShaderInfoLog(shader);
            GL45.glDeleteShader(shader);
            GL45.glDeleteProgram(program);
            throw new ShaderCompilationException("Compute shader compilation failed: " + infoLog);
        }

        GL45.glAttachShader(program, shader);
        GL45.glLinkProgram(program);
        if (GL45.glGetProgrami(program, GL45.GL_LINK_STATUS) == GL45.GL_FALSE) {
            String infoLog = GL45.glGetProgramInfoLog(program);
            GL45.glDeleteShader(shader);
            GL45.glDeleteProgram(program);
            throw new ShaderCompilationException("Compute program link failed: " + infoLog);
        }

        GL45.glDeleteShader(shader);
        return pipelines.register(program);
    }

    @Override
    public boolean isValidPipeline(Handle<PipelineResource> pipeline) {
        return pipelines.isValid(pipeline);
    }

    private static int mapShaderStage(ShaderStage stage) {
        if (stage == ShaderStage.VERTEX) return GL45.GL_VERTEX_SHADER;
        if (stage == ShaderStage.FRAGMENT) return GL45.GL_FRAGMENT_SHADER;
        if (stage == ShaderStage.GEOMETRY) return GL45.GL_GEOMETRY_SHADER;
        if (stage == ShaderStage.COMPUTE) return GL45.GL_COMPUTE_SHADER;
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
        GLFW.glfwSwapBuffers(glfwWindow);
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
                GL45.glUseProgram(program);
            }
            case RenderCommand.BindVertexBuffer cmd -> {
                int vao = getGlVaoName(cmd.vertexInput());
                int vbo = getGlBufferName(cmd.buffer());
                GL45.glBindVertexArray(vao);
                int stride = getVertexInputStride(cmd.vertexInput());
                GL45.glVertexArrayVertexBuffer(vao, 0, vbo, 0, stride);
            }
            case RenderCommand.BindIndexBuffer cmd -> {
                int ibo = getGlBufferName(cmd.buffer());
                GL45.glBindBuffer(GL45.GL_ELEMENT_ARRAY_BUFFER, ibo);
            }
            case RenderCommand.BindUniformBuffer cmd -> {
                int ubo = getGlBufferName(cmd.buffer());
                GL45.glBindBufferBase(GL45.GL_UNIFORM_BUFFER, cmd.binding(), ubo);
            }
            case RenderCommand.BindTexture cmd -> {
                int glTex = getGlTextureName(cmd.texture());
                GL45.glBindTextureUnit(cmd.unit(), glTex);
                boundTextures[cmd.unit()] = cmd.texture();
                maybeGenerateMipmaps(cmd.unit());
            }
            case RenderCommand.BindSampler cmd -> {
                GL45.glBindSampler(cmd.unit(), getGlSamplerName(cmd.sampler()));
                boundSamplers[cmd.unit()] = cmd.sampler();
                maybeGenerateMipmaps(cmd.unit());
            }
            case RenderCommand.BindStorageBuffer cmd -> {
                int ssbo = getGlBufferName(cmd.buffer());
                GL45.glBindBufferBase(GL45.GL_SHADER_STORAGE_BUFFER, cmd.binding(), ssbo);
            }
            case RenderCommand.Draw cmd -> {
                GL45.glDrawArrays(GL45.GL_TRIANGLES, cmd.firstVertex(), cmd.vertexCount());
            }
            case RenderCommand.DrawIndexed cmd -> {
                GL45.glDrawElements(GL45.GL_TRIANGLES, cmd.indexCount(), GL45.GL_UNSIGNED_INT,
                        (long) cmd.firstIndex() * Integer.BYTES);
            }
            case RenderCommand.DrawInstanced cmd -> {
                GL45.glDrawArraysInstancedBaseInstance(GL45.GL_TRIANGLES, cmd.firstVertex(), cmd.vertexCount(), cmd.instanceCount(), cmd.firstInstance());
            }
            case RenderCommand.DrawIndexedInstanced cmd -> {
                GL45.glDrawElementsInstancedBaseInstance(GL45.GL_TRIANGLES, cmd.indexCount(), GL45.GL_UNSIGNED_INT,
                        (long) cmd.firstIndex() * Integer.BYTES, cmd.instanceCount(), cmd.firstInstance());
            }
            case RenderCommand.DrawIndirect(var buffer, long offset, int drawCount, int stride) -> {
                int buf = buffers.get(buffer).glName();
                GL45.glBindBuffer(GL45.GL_DRAW_INDIRECT_BUFFER, buf);
                if (drawCount == 1) {
                    GL45.glDrawArraysIndirect(GL45.GL_TRIANGLES, offset);
                } else {
                    GL45.glMultiDrawArraysIndirect(GL45.GL_TRIANGLES, offset, drawCount, stride == 0 ? 16 : stride);
                }
            }
            case RenderCommand.DrawIndexedIndirect(var buffer, long offset, int drawCount, int stride) -> {
                int buf = buffers.get(buffer).glName();
                GL45.glBindBuffer(GL45.GL_DRAW_INDIRECT_BUFFER, buf);
                if (drawCount == 1) {
                    GL45.glDrawElementsIndirect(GL45.GL_TRIANGLES, GL45.GL_UNSIGNED_INT, offset);
                } else {
                    GL45.glMultiDrawElementsIndirect(GL45.GL_TRIANGLES, GL45.GL_UNSIGNED_INT, offset, drawCount, stride == 0 ? 20 : stride);
                }
            }
            case RenderCommand.BindRenderTarget cmd -> {
                int fbo = getGlFboName(cmd.renderTarget());
                GL45.glBindFramebuffer(GL45.GL_FRAMEBUFFER, fbo);
                var rt = renderTargets.get(cmd.renderTarget());
                if (rt != null && rt.colorTextures().size() > 1) {
                    int[] drawBuffers = new int[rt.colorTextures().size()];
                    for (int i = 0; i < drawBuffers.length; i++) drawBuffers[i] = GL45.GL_COLOR_ATTACHMENT0 + i;
                    GL45.glDrawBuffers(drawBuffers);
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
                GL45.glBindFramebuffer(GL45.GL_FRAMEBUFFER, 0);
                currentRenderTarget = null;
            }
            case RenderCommand.SetDepthTest cmd -> {
                if (cmd.enabled()) GL45.glEnable(GL45.GL_DEPTH_TEST);
                else GL45.glDisable(GL45.GL_DEPTH_TEST);
            }
            case RenderCommand.SetBlending cmd -> {
                if (cmd.enabled()) {
                    GL45.glEnable(GL45.GL_BLEND);
                    GL45.glBlendFunc(GL45.GL_SRC_ALPHA, GL45.GL_ONE_MINUS_SRC_ALPHA);
                } else {
                    GL45.glDisable(GL45.GL_BLEND);
                }
            }
            case RenderCommand.SetCullFace cmd -> {
                if (cmd.enabled()) {
                    GL45.glEnable(GL45.GL_CULL_FACE);
                    GL45.glCullFace(GL45.GL_BACK);
                    GL45.glFrontFace(GL45.GL_CCW);
                } else {
                    GL45.glDisable(GL45.GL_CULL_FACE);
                }
            }
            case RenderCommand.SetWireframe cmd -> {
                GL45.glPolygonMode(GL45.GL_FRONT_AND_BACK, cmd.enabled() ? GL45.GL_LINE : GL45.GL_FILL);
            }
            case RenderCommand.Clear cmd -> {
                GL45.glClearColor(cmd.r(), cmd.g(), cmd.b(), cmd.a());
                GL45.glClear(GL45.GL_COLOR_BUFFER_BIT | GL45.GL_DEPTH_BUFFER_BIT);
            }
            case RenderCommand.Viewport cmd -> {
                GL45.glViewport(cmd.x(), cmd.y(), cmd.width(), cmd.height());
            }
            case RenderCommand.Scissor cmd -> {
                GL45.glScissor(cmd.x(), cmd.y(), cmd.width(), cmd.height());
            }
            case RenderCommand.SetRenderState(var props) -> {
                if (props.contains(RenderState.DEPTH_TEST)) {
                    if (props.get(RenderState.DEPTH_TEST)) GL45.glEnable(GL45.GL_DEPTH_TEST);
                    else GL45.glDisable(GL45.GL_DEPTH_TEST);
                }
                if (props.contains(RenderState.DEPTH_WRITE)) {
                    GL45.glDepthMask(props.get(RenderState.DEPTH_WRITE));
                }
                if (props.contains(RenderState.DEPTH_FUNC)) {
                    GL45.glDepthFunc(mapCompareFunc(props.get(RenderState.DEPTH_FUNC)));
                }
                if (props.contains(RenderState.BLEND_MODE)) {
                    applyBlendMode(props.get(RenderState.BLEND_MODE));
                }
                if (props.contains(RenderState.CULL_MODE)) {
                    applyCullMode(props.get(RenderState.CULL_MODE));
                }
                if (props.contains(RenderState.FRONT_FACE)) {
                    GL45.glFrontFace(props.get(RenderState.FRONT_FACE) == FrontFace.CCW ? GL45.GL_CCW : GL45.GL_CW);
                }
                if (props.contains(RenderState.WIREFRAME)) {
                    GL45.glPolygonMode(GL45.GL_FRONT_AND_BACK, props.get(RenderState.WIREFRAME) ? GL45.GL_LINE : GL45.GL_FILL);
                }
                if (props.contains(RenderState.LINE_WIDTH)) {
                    GL45.glLineWidth(props.get(RenderState.LINE_WIDTH));
                }
                if (props.contains(RenderState.SCISSOR_TEST)) {
                    if (props.get(RenderState.SCISSOR_TEST)) GL45.glEnable(GL45.GL_SCISSOR_TEST);
                    else GL45.glDisable(GL45.GL_SCISSOR_TEST);
                }
                if (props.contains(RenderState.STENCIL_TEST)) {
                    if (props.get(RenderState.STENCIL_TEST)) GL45.glEnable(GL45.GL_STENCIL_TEST);
                    else GL45.glDisable(GL45.GL_STENCIL_TEST);
                }
                if (props.contains(RenderState.STENCIL_FUNC)) {
                    int ref = props.contains(RenderState.STENCIL_REF) ? props.get(RenderState.STENCIL_REF) : 0;
                    int mask = props.contains(RenderState.STENCIL_MASK) ? props.get(RenderState.STENCIL_MASK) : 0xFF;
                    GL45.glStencilFunc(mapCompareFunc(props.get(RenderState.STENCIL_FUNC)), ref, mask);
                }
                if (props.contains(RenderState.STENCIL_FAIL)) {
                    StencilOp fail = props.get(RenderState.STENCIL_FAIL);
                    StencilOp depthFail = props.contains(RenderState.STENCIL_DEPTH_FAIL) ? props.get(RenderState.STENCIL_DEPTH_FAIL) : StencilOp.KEEP;
                    StencilOp pass = props.contains(RenderState.STENCIL_PASS) ? props.get(RenderState.STENCIL_PASS) : StencilOp.KEEP;
                    GL45.glStencilOp(mapStencilOp(fail), mapStencilOp(depthFail), mapStencilOp(pass));
                }
            }
            case RenderCommand.PushConstants(var data) -> {
                data.rewind();
                GL45.nglNamedBufferSubData(pushConstantUbo, 0, data.remaining(), org.lwjgl.system.MemoryUtil.memAddress(data));
                GL45.glBindBufferBase(GL45.GL_UNIFORM_BUFFER, 15, pushConstantUbo);
            }
            case RenderCommand.BindComputePipeline(var pipeline) -> {
                int program = getGlProgramName(pipeline);
                GL45.glUseProgram(program);
            }
            case RenderCommand.Dispatch(int gx, int gy, int gz) -> {
                GL45.glDispatchCompute(gx, gy, gz);
            }
            case RenderCommand.CopyBuffer(var src, var dst, long srcOff, long dstOff, long size) -> {
                int srcBuf = buffers.get(src).glName();
                int dstBuf = buffers.get(dst).glName();
                GL45.glCopyNamedBufferSubData(srcBuf, dstBuf, srcOff, dstOff, size);
            }
            case RenderCommand.CopyTexture(var src, var dst, int sx, int sy, int dx, int dy, int w, int h, int srcMip, int dstMip) -> {
                int srcTex = textures.get(src).glName();
                int dstTex = textures.get(dst).glName();
                GL45.glCopyImageSubData(srcTex, GL45.GL_TEXTURE_2D, srcMip, sx, sy, 0,
                                        dstTex, GL45.GL_TEXTURE_2D, dstMip, dx, dy, 0, w, h, 1);
            }
            case RenderCommand.BlitTexture(var src, var dst,
                    int sx0, int sy0, int sx1, int sy1,
                    int dx0, int dy0, int dx1, int dy1, boolean linear) -> {
                // BlitFramebuffer requires FBO binding — create temp FBOs
                int srcFbo = GL45.glCreateFramebuffers();
                int dstFbo = GL45.glCreateFramebuffers();
                int srcTex = textures.get(src).glName();
                int dstTex = textures.get(dst).glName();
                GL45.glNamedFramebufferTexture(srcFbo, GL45.GL_COLOR_ATTACHMENT0, srcTex, 0);
                GL45.glNamedFramebufferTexture(dstFbo, GL45.GL_COLOR_ATTACHMENT0, dstTex, 0);
                GL45.glBlitNamedFramebuffer(srcFbo, dstFbo,
                    sx0, sy0, sx1, sy1, dx0, dy0, dx1, dy1,
                    GL45.GL_COLOR_BUFFER_BIT, linear ? GL45.GL_LINEAR : GL45.GL_NEAREST);
                GL45.glDeleteFramebuffers(srcFbo);
                GL45.glDeleteFramebuffers(dstFbo);
            }
            case RenderCommand.BindImage(int unit, var texture, int mipLevel, boolean read, boolean write) -> {
                var texInfo = textures.get(texture);
                int glTex = texInfo.glName();
                int internalFormat = mapTextureFormat(texInfo.desc().format());
                int access = GL45.GL_READ_WRITE;
                if (read && !write) access = GL45.GL_READ_ONLY;
                else if (!read && write) access = GL45.GL_WRITE_ONLY;
                boolean layered = texInfo.desc().type() == TextureType.TEXTURE_3D
                        || texInfo.desc().type() == TextureType.TEXTURE_2D_ARRAY
                        || texInfo.desc().type() == TextureType.TEXTURE_CUBE;
                GL45.glBindImageTexture(unit, glTex, mipLevel, layered, 0, access, internalFormat);
            }
            case RenderCommand.MemoryBarrier(var scope) -> {
                int bits;
                if (scope == dev.engine.graphics.renderstate.BarrierScope.STORAGE_BUFFER) {
                    bits = GL45.GL_SHADER_STORAGE_BARRIER_BIT;
                } else if (scope == dev.engine.graphics.renderstate.BarrierScope.TEXTURE) {
                    bits = GL45.GL_TEXTURE_FETCH_BARRIER_BIT;
                } else {
                    bits = GL45.GL_ALL_BARRIER_BITS;
                }
                GL45.glMemoryBarrier(bits);
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
     *   device.capabilities().register(MY_CAP, () -> GL45.glGetInteger(MY_GL_CONSTANT));
     * </pre>
     */
    public CapabilityRegistry capabilities() { return capabilities; }

    private boolean hasBindlessTextures() {
        var extensions = GL45.glGetString(GL45.GL_EXTENSIONS);
        if (extensions != null && extensions.contains("GL_ARB_bindless_texture")) return true;
        // Also check via glGetIntegerv GL_NUM_EXTENSIONS
        int numExt = GL45.glGetInteger(GL45.GL_NUM_EXTENSIONS);
        for (int i = 0; i < numExt; i++) {
            var ext = GL45.glGetStringi(GL45.GL_EXTENSIONS, i);
            if ("GL_ARB_bindless_texture".equals(ext)) return true;
        }
        return false;
    }

    @Override
    public long getBindlessTextureHandle(Handle<TextureResource> texture) {
        int glTex = getGlTextureName(texture);
        long handle = org.lwjgl.opengl.ARBBindlessTexture.glGetTextureHandleARB(glTex);
        org.lwjgl.opengl.ARBBindlessTexture.glMakeTextureHandleResidentARB(handle);
        return handle;
    }

    @Override
    public StreamingBuffer createStreamingBuffer(long frameSize, int frameCount, BufferUsage usage) {
        return new GlStreamingBuffer(this, frameSize, frameCount, usage);
    }

    @Override
    public GpuFence createFence() {
        return new GlFence();
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
        GL45.glReadPixels(0, 0, width, height, GL45.GL_RGBA, GL45.GL_UNSIGNED_BYTE, pixels);
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
            GL45.glGenerateTextureMipmap(glTex);
            textureMipsDirty.put(texture.index(), false);
        }
    }

    private static boolean usesMipmaps(SamplerDescriptor desc) {
        return desc.minFilter() == FilterMode.NEAREST_MIPMAP_NEAREST
                || desc.minFilter() == FilterMode.LINEAR_MIPMAP_LINEAR;
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

        buffers.destroyAll(buf -> GL45.glDeleteBuffers(buf.glName()));
        textures.destroyAll(tex -> GL45.glDeleteTextures(tex.glName()));
        pipelines.destroyAll(GL45::glDeleteProgram);
        vertexInputs.destroyAll(vi -> GL45.glDeleteVertexArrays(vi.vao()));
        samplers.destroyAll(s -> GL45.glDeleteSamplers(s.glName()));
        renderTargets.destroyAll(rt -> GL45.glDeleteFramebuffers(rt.fbo()));
        GL45.glDeleteBuffers(pushConstantUbo);
        log.info("GlRenderDevice closed");
    }

    private static int mapTextureFormat(TextureFormat format) {
        if (format == TextureFormat.RGBA8) return GL45.GL_RGBA8;
        if (format == TextureFormat.RGB8) return GL45.GL_RGB8;
        if (format == TextureFormat.R8) return GL45.GL_R8;
        if (format == TextureFormat.DEPTH24) return GL45.GL_DEPTH_COMPONENT24;
        if (format == TextureFormat.DEPTH32F) return GL45.GL_DEPTH_COMPONENT32F;
        if (format == TextureFormat.DEPTH24_STENCIL8) return GL45.GL_DEPTH24_STENCIL8;
        if (format == TextureFormat.DEPTH32F_STENCIL8) return GL45.GL_DEPTH32F_STENCIL8;
        return GL45.GL_RGBA8;
    }

    private static int[] mapUploadFormat(TextureFormat format) {
        if (format == TextureFormat.RGBA8) return new int[]{GL45.GL_RGBA, GL45.GL_UNSIGNED_BYTE};
        if (format == TextureFormat.RGB8) return new int[]{GL45.GL_RGB, GL45.GL_UNSIGNED_BYTE};
        if (format == TextureFormat.R8) return new int[]{GL45.GL_RED, GL45.GL_UNSIGNED_BYTE};
        return new int[]{GL45.GL_RGBA, GL45.GL_UNSIGNED_BYTE};
    }

    private static int mapUsage(AccessPattern pattern) {
        if (pattern == AccessPattern.STATIC) return GL45.GL_STATIC_DRAW;
        if (pattern == AccessPattern.DYNAMIC) return GL45.GL_DYNAMIC_DRAW;
        if (pattern == AccessPattern.STREAM) return GL45.GL_STREAM_DRAW;
        return GL45.GL_STATIC_DRAW;
    }

    private static int mapCompareFunc(CompareFunc func) {
        if (func == CompareFunc.LESS) return GL45.GL_LESS;
        if (func == CompareFunc.LEQUAL) return GL45.GL_LEQUAL;
        if (func == CompareFunc.GREATER) return GL45.GL_GREATER;
        if (func == CompareFunc.GEQUAL) return GL45.GL_GEQUAL;
        if (func == CompareFunc.EQUAL) return GL45.GL_EQUAL;
        if (func == CompareFunc.NOT_EQUAL) return GL45.GL_NOTEQUAL;
        if (func == CompareFunc.ALWAYS) return GL45.GL_ALWAYS;
        if (func == CompareFunc.NEVER) return GL45.GL_NEVER;
        return GL45.GL_LESS;
    }

    private static int mapStencilOp(StencilOp op) {
        if (op == StencilOp.KEEP) return GL45.GL_KEEP;
        if (op == StencilOp.ZERO) return GL45.GL_ZERO;
        if (op == StencilOp.REPLACE) return GL45.GL_REPLACE;
        if (op == StencilOp.INCR) return GL45.GL_INCR;
        if (op == StencilOp.DECR) return GL45.GL_DECR;
        if (op == StencilOp.INVERT) return GL45.GL_INVERT;
        if (op == StencilOp.INCR_WRAP) return GL45.GL_INCR_WRAP;
        if (op == StencilOp.DECR_WRAP) return GL45.GL_DECR_WRAP;
        return GL45.GL_KEEP;
    }

    private static void applyBlendMode(BlendMode mode) {
        if (mode == BlendMode.NONE) {
            GL45.glDisable(GL45.GL_BLEND);
        } else {
            GL45.glEnable(GL45.GL_BLEND);
            if (mode == BlendMode.ALPHA) {
                GL45.glBlendFunc(GL45.GL_SRC_ALPHA, GL45.GL_ONE_MINUS_SRC_ALPHA);
            } else if (mode == BlendMode.ADDITIVE) {
                GL45.glBlendFunc(GL45.GL_SRC_ALPHA, GL45.GL_ONE);
            } else if (mode == BlendMode.MULTIPLY) {
                GL45.glBlendFunc(GL45.GL_DST_COLOR, GL45.GL_ZERO);
            } else if (mode == BlendMode.PREMULTIPLIED) {
                GL45.glBlendFunc(GL45.GL_ONE, GL45.GL_ONE_MINUS_SRC_ALPHA);
            }
        }
    }

    private static void applyCullMode(CullMode mode) {
        if (mode == CullMode.NONE) {
            GL45.glDisable(GL45.GL_CULL_FACE);
        } else {
            GL45.glEnable(GL45.GL_CULL_FACE);
            GL45.glCullFace(mode == CullMode.FRONT ? GL45.GL_FRONT : GL45.GL_BACK);
        }
    }
}
