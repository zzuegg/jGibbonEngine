package dev.engine.graphics.opengl;

import dev.engine.core.handle.Handle;
import dev.engine.core.handle.HandlePool;
import dev.engine.graphics.BufferResource;
import dev.engine.graphics.DeviceCapability;
import dev.engine.graphics.PipelineResource;
import dev.engine.graphics.RenderDevice;
import dev.engine.graphics.RenderTargetResource;
import dev.engine.graphics.SamplerResource;
import dev.engine.graphics.TextureResource;
import dev.engine.graphics.VertexInputResource;
import dev.engine.graphics.command.CommandList;
import dev.engine.graphics.command.RenderCommand;
import dev.engine.graphics.pipeline.PipelineDescriptor;
import dev.engine.graphics.pipeline.ShaderCompilationException;
import dev.engine.graphics.pipeline.ShaderSource;
import dev.engine.graphics.pipeline.ShaderStage;
import dev.engine.graphics.vertex.ComponentType;
import dev.engine.graphics.vertex.VertexAttribute;
import dev.engine.graphics.vertex.VertexFormat;
import dev.engine.graphics.target.RenderTargetDescriptor;
import dev.engine.graphics.buffer.AccessPattern;
import dev.engine.graphics.buffer.BufferDescriptor;
import dev.engine.graphics.buffer.BufferUsage;
import dev.engine.graphics.buffer.BufferWriter;
import dev.engine.graphics.sampler.FilterMode;
import dev.engine.graphics.sampler.SamplerDescriptor;
import dev.engine.graphics.sampler.WrapMode;
import dev.engine.graphics.texture.TextureDescriptor;
import dev.engine.graphics.texture.TextureFormat;
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

    private final HandlePool<BufferResource> bufferPool = new HandlePool<>();
    private final Map<Integer, Integer> bufferGlNames = new HashMap<>();
    private final Map<Integer, Long> bufferSizes = new HashMap<>();
    private final HandlePool<TextureResource> texturePool = new HandlePool<>();
    private final Map<Integer, Integer> textureGlNames = new HashMap<>();
    private final Map<Integer, TextureDescriptor> textureDescs = new HashMap<>();
    private final HandlePool<VertexInputResource> vertexInputPool = new HandlePool<>();
    private final Map<Integer, Integer> vertexInputVaos = new HashMap<>();
    private final Map<Integer, Integer> vertexInputStrides = new HashMap<>();
    private final HandlePool<RenderTargetResource> renderTargetPool = new HandlePool<>();
    private final Map<Integer, Integer> renderTargetFbos = new HashMap<>();
    private final Map<Integer, List<Handle<TextureResource>>> renderTargetColorTextures = new HashMap<>();
    private final HandlePool<SamplerResource> samplerPool = new HandlePool<>();
    private final Map<Integer, Integer> samplerGlNames = new HashMap<>();
    private final HandlePool<PipelineResource> pipelinePool = new HandlePool<>();
    private final Map<Integer, Integer> pipelineGlPrograms = new HashMap<>();
    private final AtomicLong frameCounter = new AtomicLong(0);
    private final long glfwWindow;

    public GlRenderDevice(GlfwWindowToolkit.GlfwWindowHandle window) {
        this.glfwWindow = window.glfwHandle();
        GLFW.glfwMakeContextCurrent(glfwWindow);
        GL.createCapabilities();
        log.info("OpenGL context created: {}", GL45.glGetString(GL45.GL_VERSION));
    }

    @Override
    public Handle<BufferResource> createBuffer(BufferDescriptor descriptor) {
        int glBuffer = GL45.glCreateBuffers();
        int usage = mapUsage(descriptor.accessPattern());
        GL45.glNamedBufferData(glBuffer, descriptor.size(), usage);

        var handle = bufferPool.allocate();
        bufferGlNames.put(handle.index(), glBuffer);
        bufferSizes.put(handle.index(), descriptor.size());
        return handle;
    }

    @Override
    public void destroyBuffer(Handle<BufferResource> buffer) {
        if (!bufferPool.isValid(buffer)) return;
        Integer glName = bufferGlNames.remove(buffer.index());
        if (glName != null) {
            GL45.glDeleteBuffers(glName);
        }
        bufferPool.release(buffer);
    }

    @Override
    public boolean isValidBuffer(Handle<BufferResource> buffer) {
        return bufferPool.isValid(buffer);
    }

    @Override
    public BufferWriter writeBuffer(Handle<BufferResource> buffer) {
        long size = bufferSizes.getOrDefault(buffer.index(), 0L);
        return writeBuffer(buffer, 0, size);
    }

    @Override
    public BufferWriter writeBuffer(Handle<BufferResource> buffer, long offset, long length) {
        int glName = bufferGlNames.get(buffer.index());
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
        int glTex = GL45.glCreateTextures(GL45.GL_TEXTURE_2D);
        int internalFormat = mapTextureFormat(descriptor.format());
        GL45.glTextureStorage2D(glTex, 1, internalFormat, descriptor.width(), descriptor.height());

        var handle = texturePool.allocate();
        textureGlNames.put(handle.index(), glTex);
        textureDescs.put(handle.index(), descriptor);
        return handle;
    }

    @Override
    public void uploadTexture(Handle<TextureResource> texture, ByteBuffer pixels) {
        int glName = textureGlNames.get(texture.index());
        var desc = textureDescs.get(texture.index());
        int[] formatAndType = mapUploadFormat(desc.format());
        GL45.glTextureSubImage2D(glName, 0, 0, 0, desc.width(), desc.height(),
                formatAndType[0], formatAndType[1], pixels);
    }

    @Override
    public void destroyTexture(Handle<TextureResource> texture) {
        if (!texturePool.isValid(texture)) return;
        Integer glName = textureGlNames.remove(texture.index());
        textureDescs.remove(texture.index());
        if (glName != null) GL45.glDeleteTextures(glName);
        texturePool.release(texture);
    }

    @Override
    public boolean isValidTexture(Handle<TextureResource> texture) {
        return texturePool.isValid(texture);
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

            var texHandle = texturePool.allocate();
            textureGlNames.put(texHandle.index(), glTex);
            textureDescs.put(texHandle.index(), new dev.engine.graphics.texture.TextureDescriptor(
                    descriptor.width(), descriptor.height(), format));
            colorTextures.add(texHandle);
        }

        if (descriptor.depthFormat() != null) {
            int depthTex = GL45.glCreateTextures(GL45.GL_TEXTURE_2D);
            GL45.glTextureStorage2D(depthTex, 1, mapTextureFormat(descriptor.depthFormat()),
                    descriptor.width(), descriptor.height());
            GL45.glNamedFramebufferTexture(fbo, GL45.GL_DEPTH_ATTACHMENT, depthTex, 0);
        }

        var handle = renderTargetPool.allocate();
        renderTargetFbos.put(handle.index(), fbo);
        renderTargetColorTextures.put(handle.index(), colorTextures);
        return handle;
    }

    @Override
    public Handle<TextureResource> getRenderTargetColorTexture(Handle<RenderTargetResource> renderTarget, int index) {
        return renderTargetColorTextures.get(renderTarget.index()).get(index);
    }

    @Override
    public void destroyRenderTarget(Handle<RenderTargetResource> renderTarget) {
        if (!renderTargetPool.isValid(renderTarget)) return;
        Integer fbo = renderTargetFbos.remove(renderTarget.index());
        var textures = renderTargetColorTextures.remove(renderTarget.index());
        if (fbo != null) GL45.glDeleteFramebuffers(fbo);
        if (textures != null) {
            for (var tex : textures) destroyTexture(tex);
        }
        renderTargetPool.release(renderTarget);
    }

    int getGlFboName(Handle<RenderTargetResource> renderTarget) {
        return renderTargetFbos.getOrDefault(renderTarget.index(), 0);
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

        var handle = vertexInputPool.allocate();
        vertexInputVaos.put(handle.index(), vao);
        vertexInputStrides.put(handle.index(), format.stride());
        return handle;
    }

    @Override
    public void destroyVertexInput(Handle<VertexInputResource> vertexInput) {
        if (!vertexInputPool.isValid(vertexInput)) return;
        Integer vao = vertexInputVaos.remove(vertexInput.index());
        vertexInputStrides.remove(vertexInput.index());
        if (vao != null) GL45.glDeleteVertexArrays(vao);
        vertexInputPool.release(vertexInput);
    }

    int getGlVaoName(Handle<VertexInputResource> vertexInput) {
        return vertexInputVaos.getOrDefault(vertexInput.index(), 0);
    }

    int getVertexInputStride(Handle<VertexInputResource> vertexInput) {
        return vertexInputStrides.getOrDefault(vertexInput.index(), 0);
    }

    @Override
    public Handle<SamplerResource> createSampler(SamplerDescriptor descriptor) {
        int glSampler = GL45.glCreateSamplers();
        GL45.glSamplerParameteri(glSampler, GL45.GL_TEXTURE_MIN_FILTER, mapFilterMode(descriptor.minFilter()));
        GL45.glSamplerParameteri(glSampler, GL45.GL_TEXTURE_MAG_FILTER, mapFilterMode(descriptor.magFilter()));
        GL45.glSamplerParameteri(glSampler, GL45.GL_TEXTURE_WRAP_S, mapWrapMode(descriptor.wrapS()));
        GL45.glSamplerParameteri(glSampler, GL45.GL_TEXTURE_WRAP_T, mapWrapMode(descriptor.wrapT()));

        var handle = samplerPool.allocate();
        samplerGlNames.put(handle.index(), glSampler);
        return handle;
    }

    @Override
    public void destroySampler(Handle<SamplerResource> sampler) {
        if (!samplerPool.isValid(sampler)) return;
        Integer glName = samplerGlNames.remove(sampler.index());
        if (glName != null) GL45.glDeleteSamplers(glName);
        samplerPool.release(sampler);
    }

    int getGlSamplerName(Handle<SamplerResource> sampler) {
        return samplerGlNames.getOrDefault(sampler.index(), 0);
    }

    int getGlProgramName(Handle<PipelineResource> pipeline) {
        return pipelineGlPrograms.getOrDefault(pipeline.index(), 0);
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

        var handle = pipelinePool.allocate();
        pipelineGlPrograms.put(handle.index(), program);
        return handle;
    }

    @Override
    public void destroyPipeline(Handle<PipelineResource> pipeline) {
        if (!pipelinePool.isValid(pipeline)) return;
        Integer program = pipelineGlPrograms.remove(pipeline.index());
        if (program != null) GL45.glDeleteProgram(program);
        pipelinePool.release(pipeline);
    }

    @Override
    public boolean isValidPipeline(Handle<PipelineResource> pipeline) {
        return pipelinePool.isValid(pipeline);
    }

    private static int mapShaderStage(ShaderStage stage) {
        if (stage == ShaderStage.VERTEX) return GL45.GL_VERTEX_SHADER;
        if (stage == ShaderStage.FRAGMENT) return GL45.GL_FRAGMENT_SHADER;
        if (stage == ShaderStage.GEOMETRY) return GL45.GL_GEOMETRY_SHADER;
        if (stage == ShaderStage.COMPUTE) return GL45.GL_COMPUTE_SHADER;
        throw new IllegalArgumentException("Unknown shader stage: " + stage.name());
    }

    public int getGlTextureName(Handle<TextureResource> texture) {
        return textureGlNames.getOrDefault(texture.index(), 0);
    }

    public int getGlBufferName(Handle<BufferResource> buffer) {
        return bufferGlNames.getOrDefault(buffer.index(), 0);
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
            }
            case RenderCommand.BindSampler cmd -> {
                GL45.glBindSampler(cmd.unit(), getGlSamplerName(cmd.sampler()));
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
            case RenderCommand.BindRenderTarget cmd -> {
                int fbo = getGlFboName(cmd.renderTarget());
                GL45.glBindFramebuffer(GL45.GL_FRAMEBUFFER, fbo);
            }
            case RenderCommand.BindDefaultRenderTarget cmd -> {
                GL45.glBindFramebuffer(GL45.GL_FRAMEBUFFER, 0);
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
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T queryCapability(DeviceCapability<T> capability) {
        if (capability == DeviceCapability.MAX_TEXTURE_SIZE) {
            return (T) Integer.valueOf(GL45.glGetInteger(GL45.GL_MAX_TEXTURE_SIZE));
        }
        if (capability == DeviceCapability.MAX_FRAMEBUFFER_WIDTH) {
            return (T) Integer.valueOf(GL45.glGetInteger(GL45.GL_MAX_FRAMEBUFFER_WIDTH));
        }
        if (capability == DeviceCapability.MAX_FRAMEBUFFER_HEIGHT) {
            return (T) Integer.valueOf(GL45.glGetInteger(GL45.GL_MAX_FRAMEBUFFER_HEIGHT));
        }
        if (capability == DeviceCapability.COMPUTE_SHADERS) {
            return (T) Boolean.TRUE;
        }
        if (capability == DeviceCapability.GEOMETRY_SHADERS) {
            return (T) Boolean.TRUE;
        }
        if (capability == DeviceCapability.TESSELLATION) {
            return (T) Boolean.TRUE;
        }
        if (capability == DeviceCapability.ANISOTROPIC_FILTERING) {
            return (T) Boolean.TRUE;
        }
        if (capability == DeviceCapability.MAX_ANISOTROPY) {
            return (T) Float.valueOf(GL45.glGetFloat(0x84FF /* GL_MAX_TEXTURE_MAX_ANISOTROPY */));
        }
        if (capability == DeviceCapability.DEVICE_NAME) {
            return (T) GL45.glGetString(GL45.GL_RENDERER);
        }
        if (capability == DeviceCapability.API_VERSION) {
            return (T) GL45.glGetString(GL45.GL_VERSION);
        }
        if (capability == DeviceCapability.BACKEND_NAME) {
            return (T) "OpenGL";
        }
        if (capability == DeviceCapability.BINDLESS_TEXTURES) {
            return (T) Boolean.valueOf(hasBindlessTextures());
        }
        return null;
    }

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
    public void close() {
        for (var glName : bufferGlNames.values()) {
            GL45.glDeleteBuffers(glName);
        }
        bufferGlNames.clear();
        for (var glName : textureGlNames.values()) {
            GL45.glDeleteTextures(glName);
        }
        textureGlNames.clear();
        for (var program : pipelineGlPrograms.values()) {
            GL45.glDeleteProgram(program);
        }
        pipelineGlPrograms.clear();
        for (var vao : vertexInputVaos.values()) {
            GL45.glDeleteVertexArrays(vao);
        }
        vertexInputVaos.clear();
        for (var glSampler : samplerGlNames.values()) {
            GL45.glDeleteSamplers(glSampler);
        }
        samplerGlNames.clear();
        log.info("GlRenderDevice closed");
    }

    private static int mapTextureFormat(TextureFormat format) {
        if (format == TextureFormat.RGBA8) return GL45.GL_RGBA8;
        if (format == TextureFormat.RGB8) return GL45.GL_RGB8;
        if (format == TextureFormat.R8) return GL45.GL_R8;
        if (format == TextureFormat.DEPTH24) return GL45.GL_DEPTH_COMPONENT24;
        if (format == TextureFormat.DEPTH32F) return GL45.GL_DEPTH_COMPONENT32F;
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
}
