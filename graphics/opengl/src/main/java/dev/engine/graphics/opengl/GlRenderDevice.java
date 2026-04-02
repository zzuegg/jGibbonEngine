package dev.engine.graphics.opengl;

import dev.engine.core.handle.Handle;
import dev.engine.core.handle.HandlePool;
import dev.engine.graphics.BufferResource;
import dev.engine.graphics.PipelineResource;
import dev.engine.graphics.RenderCapability;
import dev.engine.graphics.RenderContext;
import dev.engine.graphics.RenderDevice;
import dev.engine.graphics.RenderTargetResource;
import dev.engine.graphics.TextureResource;
import dev.engine.graphics.VertexInputResource;
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

    int getGlProgramName(Handle<PipelineResource> pipeline) {
        return pipelineGlPrograms.getOrDefault(pipeline.index(), 0);
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
    public RenderContext beginFrame() {
        long frame = frameCounter.incrementAndGet();
        return new GlRenderContext(frame, this);
    }

    @Override
    public void endFrame(RenderContext context) {
        GLFW.glfwSwapBuffers(glfwWindow);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T queryCapability(RenderCapability<T> capability) {
        if (capability == RenderCapability.MAX_TEXTURE_SIZE) {
            return (T) Integer.valueOf(GL45.glGetInteger(GL45.GL_MAX_TEXTURE_SIZE));
        }
        if (capability == RenderCapability.MAX_FRAMEBUFFER_WIDTH) {
            return (T) Integer.valueOf(GL45.glGetInteger(GL45.GL_MAX_FRAMEBUFFER_WIDTH));
        }
        if (capability == RenderCapability.MAX_FRAMEBUFFER_HEIGHT) {
            return (T) Integer.valueOf(GL45.glGetInteger(GL45.GL_MAX_FRAMEBUFFER_HEIGHT));
        }
        return null;
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
