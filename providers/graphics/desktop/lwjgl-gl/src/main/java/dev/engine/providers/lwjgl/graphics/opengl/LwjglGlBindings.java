package dev.engine.providers.lwjgl.graphics.opengl;

import dev.engine.graphics.opengl.GlBindings;
import org.lwjgl.opengl.ARBBindlessTexture;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL45;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFW.glfwMakeContextCurrent;
import static org.lwjgl.glfw.GLFW.glfwSwapBuffers;

/**
 * LWJGL-based implementation of {@link GlBindings}.
 *
 * <p>Delegates every call to the corresponding LWJGL GL45 / GLFW static method.
 */
public class LwjglGlBindings implements GlBindings {

    // ===== Context =====

    @Override public void makeContextCurrent(long window) { glfwMakeContextCurrent(window); }

    @Override public void createCapabilities() { GL.createCapabilities(); }

    // ===== Query =====

    @Override public String glGetString(int name) { return GL45.glGetString(name); }

    @Override public String glGetStringi(int name, int index) { return GL45.glGetStringi(name, index); }

    @Override public int glGetInteger(int pname) { return GL45.glGetInteger(pname); }

    @Override public float glGetFloat(int pname) { return GL45.glGetFloat(pname); }

    // ===== Buffers =====

    @Override public int glCreateBuffers() { return GL45.glCreateBuffers(); }

    @Override public void glNamedBufferData(int buffer, long size, int usage) { GL45.glNamedBufferData(buffer, size, usage); }

    @Override public void glNamedBufferStorage(int buffer, long size, int flags) { GL45.glNamedBufferStorage(buffer, size, flags); }

    @Override public void nglNamedBufferSubData(int buffer, long offset, long size, long dataAddress) {
        GL45.nglNamedBufferSubData(buffer, offset, size, dataAddress);
    }

    @Override public ByteBuffer glMapNamedBufferRange(int buffer, long offset, long length, int access) {
        return GL45.glMapNamedBufferRange(buffer, offset, length, access);
    }

    @Override public void glUnmapNamedBuffer(int buffer) { GL45.glUnmapNamedBuffer(buffer); }

    @Override public void glDeleteBuffers(int buffer) { GL45.glDeleteBuffers(buffer); }

    @Override public void glBindBuffer(int target, int buffer) { GL45.glBindBuffer(target, buffer); }

    @Override public void glBindBufferBase(int target, int index, int buffer) { GL45.glBindBufferBase(target, index, buffer); }

    @Override public void glCopyNamedBufferSubData(int readBuffer, int writeBuffer, long readOffset, long writeOffset, long size) {
        GL45.glCopyNamedBufferSubData(readBuffer, writeBuffer, readOffset, writeOffset, size);
    }

    // ===== Textures =====

    @Override public int glCreateTextures(int target) { return GL45.glCreateTextures(target); }

    @Override public void glTextureStorage2D(int texture, int levels, int internalFormat, int width, int height) {
        GL45.glTextureStorage2D(texture, levels, internalFormat, width, height);
    }

    @Override public void glTextureStorage3D(int texture, int levels, int internalFormat, int width, int height, int depth) {
        GL45.glTextureStorage3D(texture, levels, internalFormat, width, height, depth);
    }

    @Override public void glTextureSubImage2D(int texture, int level, int xoffset, int yoffset, int width, int height, int format, int type, ByteBuffer pixels) {
        GL45.glTextureSubImage2D(texture, level, xoffset, yoffset, width, height, format, type, pixels);
    }

    @Override public void glTextureSubImage3D(int texture, int level, int xoffset, int yoffset, int zoffset, int width, int height, int depth, int format, int type, ByteBuffer pixels) {
        GL45.glTextureSubImage3D(texture, level, xoffset, yoffset, zoffset, width, height, depth, format, type, pixels);
    }

    @Override public void glDeleteTextures(int texture) { GL45.glDeleteTextures(texture); }

    @Override public void glBindTextureUnit(int unit, int texture) { GL45.glBindTextureUnit(unit, texture); }

    @Override public void glGenerateTextureMipmap(int texture) { GL45.glGenerateTextureMipmap(texture); }

    @Override public void glBindImageTexture(int unit, int texture, int level, boolean layered, int layer, int access, int format) {
        GL45.glBindImageTexture(unit, texture, level, layered, layer, access, format);
    }

    @Override public void glCopyImageSubData(int srcName, int srcTarget, int srcLevel, int srcX, int srcY, int srcZ,
                                              int dstName, int dstTarget, int dstLevel, int dstX, int dstY, int dstZ,
                                              int srcWidth, int srcHeight, int srcDepth) {
        GL45.glCopyImageSubData(srcName, srcTarget, srcLevel, srcX, srcY, srcZ,
                                dstName, dstTarget, dstLevel, dstX, dstY, dstZ,
                                srcWidth, srcHeight, srcDepth);
    }

    // ===== Samplers =====

    @Override public int glCreateSamplers() { return GL45.glCreateSamplers(); }

    @Override public void glSamplerParameteri(int sampler, int pname, int param) { GL45.glSamplerParameteri(sampler, pname, param); }
    @Override public void glSamplerParameterf(int sampler, int pname, float param) { GL45.glSamplerParameterf(sampler, pname, param); }
    @Override public void glSamplerParameterfv(int sampler, int pname, float[] params) { GL45.glSamplerParameterfv(sampler, pname, params); }

    @Override public void glDeleteSamplers(int sampler) { GL45.glDeleteSamplers(sampler); }

    @Override public void glBindSampler(int unit, int sampler) { GL45.glBindSampler(unit, sampler); }

    // ===== Framebuffers =====

    @Override public int glCreateFramebuffers() { return GL45.glCreateFramebuffers(); }

    @Override public void glNamedFramebufferTexture(int framebuffer, int attachment, int texture, int level) {
        GL45.glNamedFramebufferTexture(framebuffer, attachment, texture, level);
    }

    @Override public void glBindFramebuffer(int target, int framebuffer) { GL45.glBindFramebuffer(target, framebuffer); }

    @Override public void glDeleteFramebuffers(int framebuffer) { GL45.glDeleteFramebuffers(framebuffer); }

    @Override public void glDrawBuffers(int[] bufs) { GL45.glDrawBuffers(bufs); }

    @Override public void glBlitNamedFramebuffer(int readFramebuffer, int drawFramebuffer,
                                                  int srcX0, int srcY0, int srcX1, int srcY1,
                                                  int dstX0, int dstY0, int dstX1, int dstY1,
                                                  int mask, int filter) {
        GL45.glBlitNamedFramebuffer(readFramebuffer, drawFramebuffer,
                                    srcX0, srcY0, srcX1, srcY1,
                                    dstX0, dstY0, dstX1, dstY1, mask, filter);
    }

    @Override public void glReadPixels(int x, int y, int width, int height, int format, int type, ByteBuffer pixels) {
        GL45.glReadPixels(x, y, width, height, format, type, pixels);
    }

    // ===== VAOs =====

    @Override public int glCreateVertexArrays() { return GL45.glCreateVertexArrays(); }

    @Override public void glEnableVertexArrayAttrib(int vaobj, int index) { GL45.glEnableVertexArrayAttrib(vaobj, index); }

    @Override public void glVertexArrayAttribFormat(int vaobj, int attribindex, int size, int type, boolean normalized, int relativeoffset) {
        GL45.glVertexArrayAttribFormat(vaobj, attribindex, size, type, normalized, relativeoffset);
    }

    @Override public void glVertexArrayAttribBinding(int vaobj, int attribindex, int bindingindex) {
        GL45.glVertexArrayAttribBinding(vaobj, attribindex, bindingindex);
    }

    @Override public void glVertexArrayVertexBuffer(int vaobj, int bindingindex, int buffer, long offset, int stride) {
        GL45.glVertexArrayVertexBuffer(vaobj, bindingindex, buffer, offset, stride);
    }

    @Override public void glBindVertexArray(int array) { GL45.glBindVertexArray(array); }

    @Override public void glVertexAttribDivisor(int index, int divisor) { GL45.glVertexAttribDivisor(index, divisor); }

    @Override public void glDeleteVertexArrays(int array) { GL45.glDeleteVertexArrays(array); }

    // ===== Shaders =====

    @Override public int glCreateProgram() { return GL45.glCreateProgram(); }

    @Override public int glCreateShader(int type) { return GL45.glCreateShader(type); }

    @Override public void glShaderSource(int shader, CharSequence source) { GL45.glShaderSource(shader, source); }

    @Override public void glCompileShader(int shader) { GL45.glCompileShader(shader); }

    @Override public int glGetShaderi(int shader, int pname) { return GL45.glGetShaderi(shader, pname); }

    @Override public String glGetShaderInfoLog(int shader) { return GL45.glGetShaderInfoLog(shader); }

    @Override public void glDeleteShader(int shader) { GL45.glDeleteShader(shader); }

    @Override public void glAttachShader(int program, int shader) { GL45.glAttachShader(program, shader); }

    @Override public void glLinkProgram(int program) { GL45.glLinkProgram(program); }

    @Override public int glGetProgrami(int program, int pname) { return GL45.glGetProgrami(program, pname); }

    @Override public String glGetProgramInfoLog(int program) { return GL45.glGetProgramInfoLog(program); }

    @Override public void glDeleteProgram(int program) { GL45.glDeleteProgram(program); }

    @Override public void glUseProgram(int program) { GL45.glUseProgram(program); }

    // ===== Drawing =====

    @Override public void glDrawArrays(int mode, int first, int count) { GL45.glDrawArrays(mode, first, count); }

    @Override public void glDrawElements(int mode, int count, int type, long indices) { GL45.glDrawElements(mode, count, type, indices); }

    @Override public void glDrawArraysInstancedBaseInstance(int mode, int first, int count, int instancecount, int baseinstance) {
        GL45.glDrawArraysInstancedBaseInstance(mode, first, count, instancecount, baseinstance);
    }

    @Override public void glDrawElementsInstancedBaseInstance(int mode, int count, int type, long indices, int instancecount, int baseinstance) {
        GL45.glDrawElementsInstancedBaseInstance(mode, count, type, indices, instancecount, baseinstance);
    }

    @Override public void glDrawArraysIndirect(int mode, long indirect) { GL45.glDrawArraysIndirect(mode, indirect); }

    @Override public void glMultiDrawArraysIndirect(int mode, long indirect, int drawcount, int stride) {
        GL45.glMultiDrawArraysIndirect(mode, indirect, drawcount, stride);
    }

    @Override public void glDrawElementsIndirect(int mode, int type, long indirect) { GL45.glDrawElementsIndirect(mode, type, indirect); }

    @Override public void glMultiDrawElementsIndirect(int mode, int type, long indirect, int drawcount, int stride) {
        GL45.glMultiDrawElementsIndirect(mode, type, indirect, drawcount, stride);
    }

    // ===== Compute =====

    @Override public void glDispatchCompute(int numGroupsX, int numGroupsY, int numGroupsZ) {
        GL45.glDispatchCompute(numGroupsX, numGroupsY, numGroupsZ);
    }

    @Override public void glMemoryBarrier(int barriers) { GL45.glMemoryBarrier(barriers); }

    // ===== State =====

    @Override public void glEnable(int cap) { GL45.glEnable(cap); }

    @Override public void glDisable(int cap) { GL45.glDisable(cap); }

    @Override public void glBlendFunc(int sfactor, int dfactor) { GL45.glBlendFunc(sfactor, dfactor); }

    @Override public void glCullFace(int mode) { GL45.glCullFace(mode); }

    @Override public void glFrontFace(int mode) { GL45.glFrontFace(mode); }

    @Override public void glPolygonMode(int face, int mode) { GL45.glPolygonMode(face, mode); }

    @Override public void glDepthMask(boolean flag) { GL45.glDepthMask(flag); }

    @Override public void glDepthFunc(int func) { GL45.glDepthFunc(func); }

    @Override public void glStencilFunc(int func, int ref, int mask) { GL45.glStencilFunc(func, ref, mask); }

    @Override public void glStencilOp(int fail, int zfail, int zpass) { GL45.glStencilOp(fail, zfail, zpass); }

    @Override public void glLineWidth(float width) { GL45.glLineWidth(width); }

    @Override public void glClearColor(float red, float green, float blue, float alpha) { GL45.glClearColor(red, green, blue, alpha); }

    @Override public void glClear(int mask) { GL45.glClear(mask); }

    @Override public void glViewport(int x, int y, int width, int height) { GL45.glViewport(x, y, width, height); }

    @Override public void glScissor(int x, int y, int width, int height) { GL45.glScissor(x, y, width, height); }

    // ===== Sync =====

    @Override public long glFenceSync(int condition, int flags) { return GL45.glFenceSync(condition, flags); }

    @Override public void glGetSynci(long sync, int pname, IntBuffer values) { GL45.glGetSynci(sync, pname, values); }

    @Override public int glClientWaitSync(long sync, int flags, long timeout) { return GL45.glClientWaitSync(sync, flags, timeout); }

    @Override public void glDeleteSync(long sync) { GL45.glDeleteSync(sync); }

    // ===== Timer queries =====

    @Override public int glGenQueries() { return GL45.glGenQueries(); }

    @Override public void glQueryCounter(int id, int target) { GL45.glQueryCounter(id, target); }

    @Override public int glGetQueryObjecti(int id, int pname) { return GL45.glGetQueryObjecti(id, pname); }

    @Override public long glGetQueryObjecti64(int id, int pname) { return GL45.glGetQueryObjecti64(id, pname); }

    @Override public void glDeleteQueries(int id) { GL45.glDeleteQueries(id); }

    // ===== Push constants =====

    @Override public void glNamedBufferSubData(int buffer, long offset, ByteBuffer data) {
        GL45.nglNamedBufferSubData(buffer, offset, data.remaining(), MemoryUtil.memAddress(data));
    }

    // ===== Bindless textures =====

    @Override public long glGetTextureHandleARB(int texture) { return ARBBindlessTexture.glGetTextureHandleARB(texture); }

    @Override public void glMakeTextureHandleResidentARB(long handle) { ARBBindlessTexture.glMakeTextureHandleResidentARB(handle); }
}
