package dev.engine.graphics.opengl;

/**
 * @deprecated Use {@link dev.engine.windowing.glfw.GlfwWindowToolkit} instead.
 * This class exists only for backward compatibility during migration.
 */
@Deprecated
public class GlfwWindowToolkit extends dev.engine.windowing.glfw.GlfwWindowToolkit {
    public GlfwWindowToolkit() {
        super(OPENGL_HINTS);
    }
}
