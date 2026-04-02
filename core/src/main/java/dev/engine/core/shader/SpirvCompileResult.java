package dev.engine.core.shader;

public record SpirvCompileResult(boolean success, byte[] binary, String error) {
    public static SpirvCompileResult ok(byte[] binary) { return new SpirvCompileResult(true, binary, null); }
    public static SpirvCompileResult fail(String error) { return new SpirvCompileResult(false, null, error); }
}
