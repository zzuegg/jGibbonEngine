package dev.engine.graphics.opengl;

public class GlGpuTimer {

    private final GlBindings gl;
    private int queryBegin;
    private int queryEnd;
    private boolean active;

    public GlGpuTimer(GlBindings gl) {
        this.gl = gl;
    }

    public void begin() {
        if (queryBegin == 0) {
            queryBegin = gl.glGenQueries();
            queryEnd = gl.glGenQueries();
        }
        gl.glQueryCounter(queryBegin, GlBindings.GL_TIMESTAMP);
        active = true;
    }

    public void end() {
        if (!active) return;
        gl.glQueryCounter(queryEnd, GlBindings.GL_TIMESTAMP);
        active = false;
    }

    public long waitForResult() {
        // Wait for end query to be available
        while (gl.glGetQueryObjecti(queryEnd, GlBindings.GL_QUERY_RESULT_AVAILABLE) == GlBindings.GL_FALSE) {
            Thread.onSpinWait();
        }
        long startTime = gl.glGetQueryObjecti64(queryBegin, GlBindings.GL_QUERY_RESULT);
        long endTime = gl.glGetQueryObjecti64(queryEnd, GlBindings.GL_QUERY_RESULT);
        return endTime - startTime;
    }

    public void destroy() {
        if (queryBegin != 0) {
            gl.glDeleteQueries(queryBegin);
            gl.glDeleteQueries(queryEnd);
            queryBegin = 0;
            queryEnd = 0;
        }
    }
}
