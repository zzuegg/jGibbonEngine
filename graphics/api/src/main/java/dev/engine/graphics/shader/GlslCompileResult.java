package dev.engine.graphics.shader;

public record GlslCompileResult(boolean success, String glsl, String error) {
    public static GlslCompileResult ok(String glsl) { return new GlslCompileResult(true, glsl, null); }
    public static GlslCompileResult fail(String error) { return new GlslCompileResult(false, null, error); }
}
