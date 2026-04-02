package dev.engine.graphics.opengl;

import org.lwjgl.opengl.GL45;

public class GlGpuTimer {

    private int queryBegin;
    private int queryEnd;
    private boolean active;

    public void begin() {
        if (queryBegin == 0) {
            queryBegin = GL45.glGenQueries();
            queryEnd = GL45.glGenQueries();
        }
        GL45.glQueryCounter(queryBegin, GL45.GL_TIMESTAMP);
        active = true;
    }

    public void end() {
        if (!active) return;
        GL45.glQueryCounter(queryEnd, GL45.GL_TIMESTAMP);
        active = false;
    }

    public long waitForResult() {
        // Wait for end query to be available
        while (GL45.glGetQueryObjecti(queryEnd, GL45.GL_QUERY_RESULT_AVAILABLE) == GL45.GL_FALSE) {
            Thread.onSpinWait();
        }
        long startTime = GL45.glGetQueryObjecti64(queryBegin, GL45.GL_QUERY_RESULT);
        long endTime = GL45.glGetQueryObjecti64(queryEnd, GL45.GL_QUERY_RESULT);
        return endTime - startTime;
    }

    public void destroy() {
        if (queryBegin != 0) {
            GL45.glDeleteQueries(queryBegin);
            GL45.glDeleteQueries(queryEnd);
            queryBegin = 0;
            queryEnd = 0;
        }
    }
}
