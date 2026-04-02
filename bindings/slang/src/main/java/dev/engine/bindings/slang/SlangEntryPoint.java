package dev.engine.bindings.slang;

/**
 * Wraps an IEntryPoint COM interface pointer.
 *
 * <p>IEntryPoint extends IComponentType, so it inherits all IComponentType
 * methods (getLayout, getEntryPointCode, link, etc.) accessible via
 * the underlying {@link ComPtr}.
 */
public class SlangEntryPoint implements AutoCloseable {

    private final ComPtr com;

    SlangEntryPoint(ComPtr com) {
        this.com = com;
    }

    public ComPtr com() {
        return com;
    }

    @Override
    public void close() {
        com.close();
    }
}
