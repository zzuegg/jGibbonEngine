package dev.engine.graphics.opengl;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * Abstraction over OpenGL 4.5 DSA functions and constants.
 *
 * <p>Implementations delegate to a concrete GL loader (e.g. LWJGL, custom FFM).
 * All GL constants are defined here as interface fields so backend code
 * never needs to import a loader-specific class.
 */
public interface GlBindings {

    // ===== Constants =====

    // --- Boolean ---
    int GL_FALSE = 0;
    int GL_TRUE  = 1;

    // --- Draw modes ---
    int GL_TRIANGLES = 0x0004;

    // --- Data types ---
    int GL_BYTE          = 0x1400;
    int GL_UNSIGNED_BYTE = 0x1401;
    int GL_UNSIGNED_INT  = 0x1405;
    int GL_INT           = 0x1404;
    int GL_FLOAT         = 0x1406;

    // --- Blend factors ---
    int GL_ZERO                = 0;
    int GL_ONE                 = 1;
    int GL_SRC_COLOR           = 0x0300;
    int GL_ONE_MINUS_SRC_COLOR = 0x0301;
    int GL_SRC_ALPHA           = 0x0302;
    int GL_ONE_MINUS_SRC_ALPHA = 0x0303;
    int GL_DST_ALPHA           = 0x0304;
    int GL_ONE_MINUS_DST_ALPHA = 0x0305;
    int GL_DST_COLOR           = 0x0306;
    int GL_ONE_MINUS_DST_COLOR = 0x0307;

    // --- Blend equations ---
    int GL_FUNC_ADD              = 0x8006;
    int GL_MIN                   = 0x8007;
    int GL_MAX                   = 0x8008;
    int GL_FUNC_SUBTRACT         = 0x800A;
    int GL_FUNC_REVERSE_SUBTRACT = 0x800B;

    // --- Capabilities ---
    int GL_DEPTH_TEST    = 0x0B71;
    int GL_BLEND         = 0x0BE2;
    int GL_CULL_FACE     = 0x0B44;
    int GL_SCISSOR_TEST  = 0x0C11;
    int GL_STENCIL_TEST  = 0x0B90;

    // --- Cull / winding ---
    int GL_FRONT          = 0x0404;
    int GL_BACK           = 0x0405;
    int GL_FRONT_AND_BACK = 0x0408;
    int GL_CW             = 0x0900;
    int GL_CCW            = 0x0901;

    // --- Polygon modes ---
    int GL_LINE = 0x1B01;
    int GL_FILL = 0x1B02;

    // --- Compare functions ---
    int GL_NEVER    = 0x0200;
    int GL_LESS     = 0x0201;
    int GL_EQUAL    = 0x0202;
    int GL_LEQUAL   = 0x0203;
    int GL_GREATER  = 0x0204;
    int GL_NOTEQUAL = 0x0205;
    int GL_GEQUAL   = 0x0206;
    int GL_ALWAYS   = 0x0207;

    // --- Stencil ops ---
    int GL_KEEP      = 0x1E00;
    int GL_REPLACE   = 0x1E01;
    int GL_INCR      = 0x1E02;
    int GL_DECR      = 0x1E03;
    int GL_INVERT    = 0x150A;
    int GL_INCR_WRAP = 0x8507;
    int GL_DECR_WRAP = 0x8508;

    // --- Texture targets ---
    int GL_TEXTURE_2D       = 0x0DE1;
    int GL_TEXTURE_3D       = 0x806F;
    int GL_TEXTURE_2D_ARRAY = 0x8C1A;
    int GL_TEXTURE_CUBE_MAP = 0x8513;

    // --- Texture internal formats ---
    int GL_R8                  = 0x8229;
    int GL_RGB8                = 0x8051;
    int GL_RGBA8               = 0x8058;
    int GL_DEPTH_COMPONENT24   = 0x81A6;
    int GL_DEPTH_COMPONENT32F  = 0x8CAC;
    int GL_DEPTH24_STENCIL8    = 0x88F0;
    int GL_DEPTH32F_STENCIL8   = 0x8CAD;

    // --- Texture upload formats ---
    int GL_RED              = 0x1903;
    int GL_RGB              = 0x1907;
    int GL_RGBA             = 0x1908;
    int GL_DEPTH_COMPONENT  = 0x1902;
    int GL_DEPTH_STENCIL    = 0x84F9;

    // --- Additional data types for depth ---
    int GL_UNSIGNED_INT_24_8                  = 0x84FA;
    int GL_FLOAT_32_UNSIGNED_INT_24_8_REV     = 0x8DAD;

    // --- Texture / sampler parameters ---
    int GL_TEXTURE_MIN_FILTER    = 0x2801;
    int GL_TEXTURE_MAG_FILTER    = 0x2800;
    int GL_TEXTURE_WRAP_S        = 0x2802;
    int GL_TEXTURE_WRAP_T        = 0x2803;
    int GL_TEXTURE_WRAP_R        = 0x8072;
    int GL_TEXTURE_MIN_LOD       = 0x813A;
    int GL_TEXTURE_MAX_LOD       = 0x813B;
    int GL_TEXTURE_LOD_BIAS      = 0x8501;
    int GL_TEXTURE_COMPARE_MODE  = 0x884C;
    int GL_TEXTURE_COMPARE_FUNC  = 0x884D;
    int GL_TEXTURE_BORDER_COLOR  = 0x1004;
    int GL_COMPARE_REF_TO_TEXTURE = 0x884E;

    // --- Filter modes ---
    int GL_NEAREST                = 0x2600;
    int GL_LINEAR                 = 0x2601;
    int GL_NEAREST_MIPMAP_NEAREST = 0x2700;
    int GL_LINEAR_MIPMAP_NEAREST  = 0x2701;
    int GL_NEAREST_MIPMAP_LINEAR  = 0x2702;
    int GL_LINEAR_MIPMAP_LINEAR   = 0x2703;

    // --- Wrap modes ---
    int GL_REPEAT          = 0x2901;
    int GL_CLAMP_TO_EDGE   = 0x812F;
    int GL_MIRRORED_REPEAT = 0x8370;
    int GL_CLAMP_TO_BORDER = 0x812D;

    // --- Buffer targets ---
    int GL_ELEMENT_ARRAY_BUFFER = 0x8893;
    int GL_UNIFORM_BUFFER       = 0x8A11;
    int GL_SHADER_STORAGE_BUFFER = 0x90D2;
    int GL_DRAW_INDIRECT_BUFFER = 0x8F3F;

    // --- Buffer usage ---
    int GL_STATIC_DRAW  = 0x88E4;
    int GL_DYNAMIC_DRAW = 0x88E8;
    int GL_STREAM_DRAW  = 0x88E0;

    // --- Buffer storage flags ---
    int GL_MAP_WRITE_BIT      = 0x0002;
    int GL_MAP_PERSISTENT_BIT = 0x0040;
    int GL_MAP_COHERENT_BIT   = 0x0080;
    int GL_DYNAMIC_STORAGE_BIT = 0x0100;

    // --- Framebuffer ---
    int GL_FRAMEBUFFER       = 0x8D40;
    int GL_COLOR_ATTACHMENT0 = 0x8CE0;
    int GL_DEPTH_ATTACHMENT  = 0x8D00;

    // --- Clear bits ---
    int GL_COLOR_BUFFER_BIT = 0x00004000;
    int GL_DEPTH_BUFFER_BIT = 0x00000100;

    // --- Shader types ---
    int GL_VERTEX_SHADER   = 0x8B31;
    int GL_FRAGMENT_SHADER = 0x8B30;
    int GL_GEOMETRY_SHADER = 0x8DD9;
    int GL_COMPUTE_SHADER  = 0x91B9;

    // --- Shader / program status ---
    int GL_COMPILE_STATUS = 0x8B81;
    int GL_LINK_STATUS    = 0x8B82;

    // --- Image access ---
    int GL_READ_ONLY  = 0x88B8;
    int GL_WRITE_ONLY = 0x88B9;
    int GL_READ_WRITE = 0x88BA;

    // --- Memory barrier bits ---
    int GL_SHADER_STORAGE_BARRIER_BIT = 0x00002000;
    int GL_TEXTURE_FETCH_BARRIER_BIT  = 0x00000008;
    int GL_ALL_BARRIER_BITS           = 0xFFFFFFFF;

    // --- Sync ---
    int GL_SYNC_GPU_COMMANDS_COMPLETE = 0x9117;
    int GL_SYNC_STATUS               = 0x9114;
    int GL_SIGNALED                  = 0x9119;
    int GL_SYNC_FLUSH_COMMANDS_BIT   = 0x00000001;
    int GL_ALREADY_SIGNALED          = 0x911A;
    int GL_CONDITION_SATISFIED       = 0x911C;

    // --- Queries ---
    int GL_TIMESTAMP              = 0x8E28;
    int GL_QUERY_RESULT_AVAILABLE = 0x8867;
    int GL_QUERY_RESULT           = 0x8866;

    // --- String queries ---
    int GL_VERSION    = 0x1F02;
    int GL_RENDERER   = 0x1F01;
    int GL_EXTENSIONS = 0x1F03;

    // --- Integer queries ---
    int GL_MAX_TEXTURE_SIZE              = 0x0D33;
    int GL_MAX_FRAMEBUFFER_WIDTH         = 0x9315;
    int GL_MAX_FRAMEBUFFER_HEIGHT        = 0x9316;
    int GL_MAX_TEXTURE_MAX_ANISOTROPY    = 0x84FF;
    int GL_MAX_UNIFORM_BLOCK_SIZE        = 0x8A30;
    int GL_MAX_SHADER_STORAGE_BLOCK_SIZE = 0x90DE;
    int GL_NUM_EXTENSIONS                = 0x821D;

    // ===== Context operations =====

    void makeContextCurrent(long window);

    void createCapabilities();

    // ===== Query operations =====

    String glGetString(int name);

    String glGetStringi(int name, int index);

    int glGetInteger(int pname);

    float glGetFloat(int pname);

    // ===== Buffer operations =====

    int glCreateBuffers();

    void glNamedBufferData(int buffer, long size, int usage);

    void glNamedBufferStorage(int buffer, long size, int flags);

    void nglNamedBufferSubData(int buffer, long offset, long size, long dataAddress);

    ByteBuffer glMapNamedBufferRange(int buffer, long offset, long length, int access);

    void glUnmapNamedBuffer(int buffer);

    void glDeleteBuffers(int buffer);

    void glBindBuffer(int target, int buffer);

    void glBindBufferBase(int target, int index, int buffer);

    void glCopyNamedBufferSubData(int readBuffer, int writeBuffer, long readOffset, long writeOffset, long size);

    // ===== Texture operations =====

    int glCreateTextures(int target);

    void glTextureStorage2D(int texture, int levels, int internalFormat, int width, int height);

    void glTextureStorage3D(int texture, int levels, int internalFormat, int width, int height, int depth);

    void glTextureSubImage2D(int texture, int level, int xoffset, int yoffset, int width, int height, int format, int type, ByteBuffer pixels);

    void glTextureSubImage3D(int texture, int level, int xoffset, int yoffset, int zoffset, int width, int height, int depth, int format, int type, ByteBuffer pixels);

    void glDeleteTextures(int texture);

    void glBindTextureUnit(int unit, int texture);

    void glGenerateTextureMipmap(int texture);

    void glBindImageTexture(int unit, int texture, int level, boolean layered, int layer, int access, int format);

    void glCopyImageSubData(int srcName, int srcTarget, int srcLevel, int srcX, int srcY, int srcZ,
                            int dstName, int dstTarget, int dstLevel, int dstX, int dstY, int dstZ,
                            int srcWidth, int srcHeight, int srcDepth);

    // ===== Sampler operations =====

    int glCreateSamplers();

    void glSamplerParameteri(int sampler, int pname, int param);

    void glSamplerParameterf(int sampler, int pname, float param);

    void glSamplerParameterfv(int sampler, int pname, float[] params);

    void glDeleteSamplers(int sampler);

    void glBindSampler(int unit, int sampler);

    // ===== Framebuffer operations =====

    int glCreateFramebuffers();

    void glNamedFramebufferTexture(int framebuffer, int attachment, int texture, int level);

    void glBindFramebuffer(int target, int framebuffer);

    void glDeleteFramebuffers(int framebuffer);

    void glDrawBuffers(int[] bufs);

    void glBlitNamedFramebuffer(int readFramebuffer, int drawFramebuffer,
                                int srcX0, int srcY0, int srcX1, int srcY1,
                                int dstX0, int dstY0, int dstX1, int dstY1,
                                int mask, int filter);

    void glReadPixels(int x, int y, int width, int height, int format, int type, ByteBuffer pixels);

    // ===== VAO operations =====

    int glCreateVertexArrays();

    void glEnableVertexArrayAttrib(int vaobj, int index);

    void glVertexArrayAttribFormat(int vaobj, int attribindex, int size, int type, boolean normalized, int relativeoffset);

    void glVertexArrayAttribBinding(int vaobj, int attribindex, int bindingindex);

    void glVertexArrayVertexBuffer(int vaobj, int bindingindex, int buffer, long offset, int stride);

    void glBindVertexArray(int array);

    void glVertexAttribDivisor(int index, int divisor);

    void glDeleteVertexArrays(int array);

    // ===== Shader operations =====

    int glCreateProgram();

    int glCreateShader(int type);

    void glShaderSource(int shader, CharSequence source);

    void glCompileShader(int shader);

    int glGetShaderi(int shader, int pname);

    String glGetShaderInfoLog(int shader);

    void glDeleteShader(int shader);

    void glAttachShader(int program, int shader);

    void glLinkProgram(int program);

    int glGetProgrami(int program, int pname);

    String glGetProgramInfoLog(int program);

    void glDeleteProgram(int program);

    void glUseProgram(int program);

    // ===== Draw operations =====

    void glDrawArrays(int mode, int first, int count);

    void glDrawElements(int mode, int count, int type, long indices);

    void glDrawArraysInstancedBaseInstance(int mode, int first, int count, int instancecount, int baseinstance);

    void glDrawElementsInstancedBaseInstance(int mode, int count, int type, long indices, int instancecount, int baseinstance);

    void glDrawArraysIndirect(int mode, long indirect);

    void glMultiDrawArraysIndirect(int mode, long indirect, int drawcount, int stride);

    void glDrawElementsIndirect(int mode, int type, long indirect);

    void glMultiDrawElementsIndirect(int mode, int type, long indirect, int drawcount, int stride);

    // ===== Compute operations =====

    void glDispatchCompute(int numGroupsX, int numGroupsY, int numGroupsZ);

    void glMemoryBarrier(int barriers);

    // ===== State operations =====

    void glEnable(int cap);

    void glDisable(int cap);

    void glBlendFunc(int sfactor, int dfactor);
    void glBlendFuncSeparate(int sfactorRGB, int dfactorRGB, int sfactorAlpha, int dfactorAlpha);
    void glBlendEquationSeparate(int modeRGB, int modeAlpha);
    /** Per-draw-buffer blend function (GL 4.0+, required for MRT independent blending). */
    void glBlendFuncSeparatei(int buf, int sfactorRGB, int dfactorRGB, int sfactorAlpha, int dfactorAlpha);
    /** Per-draw-buffer blend equation (GL 4.0+, required for MRT independent blending). */
    void glBlendEquationSeparatei(int buf, int modeRGB, int modeAlpha);
    /** Per-draw-buffer enable (GL 3.0+). */
    void glEnablei(int cap, int index);
    /** Per-draw-buffer disable (GL 3.0+). */
    void glDisablei(int cap, int index);

    void glCullFace(int mode);

    void glFrontFace(int mode);

    void glPolygonMode(int face, int mode);

    void glDepthMask(boolean flag);

    void glDepthFunc(int func);

    void glStencilFunc(int func, int ref, int mask);

    void glStencilOp(int fail, int zfail, int zpass);

    void glLineWidth(float width);

    void glClearColor(float red, float green, float blue, float alpha);

    void glClear(int mask);

    void glViewport(int x, int y, int width, int height);

    void glScissor(int x, int y, int width, int height);

    // ===== Sync operations =====

    long glFenceSync(int condition, int flags);

    void glGetSynci(long sync, int pname, IntBuffer values);

    int glClientWaitSync(long sync, int flags, long timeout);

    void glDeleteSync(long sync);

    // ===== Query operations (timer) =====

    int glGenQueries();

    void glQueryCounter(int id, int target);

    int glGetQueryObjecti(int id, int pname);

    long glGetQueryObjecti64(int id, int pname);

    void glDeleteQueries(int id);

    // ===== Push constants (raw pointer upload) =====

    /**
     * Uploads data from a direct ByteBuffer to a named buffer via raw pointer.
     * Equivalent to {@code nglNamedBufferSubData(buffer, offset, data.remaining(), memAddress(data))}.
     */
    void glNamedBufferSubData(int buffer, long offset, ByteBuffer data);

    // ===== Bindless textures =====

    long glGetTextureHandleARB(int texture);

    void glMakeTextureHandleResidentARB(long handle);
}
