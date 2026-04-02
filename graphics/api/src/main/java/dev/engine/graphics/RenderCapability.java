package dev.engine.graphics;

public interface RenderCapability<T> {
    String name();
    Class<T> type();

    RenderCapability<Integer> MAX_TEXTURE_SIZE = StandardCap.ofInt("MAX_TEXTURE_SIZE");
    RenderCapability<Integer> MAX_FRAMEBUFFER_WIDTH = StandardCap.ofInt("MAX_FRAMEBUFFER_WIDTH");
    RenderCapability<Integer> MAX_FRAMEBUFFER_HEIGHT = StandardCap.ofInt("MAX_FRAMEBUFFER_HEIGHT");
}

record StandardCap<T>(String name, Class<T> type) implements RenderCapability<T> {
    @SuppressWarnings("unchecked")
    static RenderCapability<Integer> ofInt(String name) {
        return new StandardCap<>(name, Integer.class);
    }
}
