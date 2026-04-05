package dev.engine.graphics.renderstate;

/**
 * Blending configuration specifying how source and destination colors are combined.
 *
 * <p>Use the predefined constants ({@link #NONE}, {@link #ALPHA}, {@link #ADDITIVE},
 * {@link #MULTIPLY}, {@link #PREMULTIPLIED}) for common cases, or create a custom
 * blend mode with {@link #of}.
 *
 * <pre>{@code
 * // Predefined:
 * renderState.set(RenderState.BLEND_MODE, BlendMode.ALPHA);
 *
 * // Custom (e.g., screen blend):
 * var screen = BlendMode.of("SCREEN",
 *         BlendFactor.ONE, BlendFactor.ONE_MINUS_SRC_COLOR,
 *         BlendFactor.ONE, BlendFactor.ONE_MINUS_SRC_ALPHA);
 * renderState.set(RenderState.BLEND_MODE, screen);
 * }</pre>
 */
public interface BlendMode {
    String name();
    boolean enabled();
    BlendFactor srcColorFactor();
    BlendFactor dstColorFactor();
    BlendFactor srcAlphaFactor();
    BlendFactor dstAlphaFactor();
    BlendEquation colorEquation();
    BlendEquation alphaEquation();

    /** Creates a custom blend mode with the same factors for color and alpha. ADD equation. */
    static BlendMode of(String name, BlendFactor src, BlendFactor dst) {
        return of(name, src, dst, src, dst);
    }

    /** Creates a custom blend mode with separate color and alpha factors. ADD equation. */
    static BlendMode of(String name, BlendFactor srcColor, BlendFactor dstColor,
                        BlendFactor srcAlpha, BlendFactor dstAlpha) {
        return of(name, srcColor, dstColor, srcAlpha, dstAlpha,
                BlendEquation.ADD, BlendEquation.ADD);
    }

    /** Creates a fully customized blend mode. */
    static BlendMode of(String name, BlendFactor srcColor, BlendFactor dstColor,
                        BlendFactor srcAlpha, BlendFactor dstAlpha,
                        BlendEquation colorEq, BlendEquation alphaEq) {
        return new BlendMode() {
            public String name()              { return name; }
            public boolean enabled()          { return true; }
            public BlendFactor srcColorFactor() { return srcColor; }
            public BlendFactor dstColorFactor() { return dstColor; }
            public BlendFactor srcAlphaFactor() { return srcAlpha; }
            public BlendFactor dstAlphaFactor() { return dstAlpha; }
            public BlendEquation colorEquation() { return colorEq; }
            public BlendEquation alphaEquation() { return alphaEq; }
        };
    }

    BlendMode NONE = new BlendMode() {
        public String name()               { return "NONE"; }
        public boolean enabled()           { return false; }
        public BlendFactor srcColorFactor() { return BlendFactor.ONE; }
        public BlendFactor dstColorFactor() { return BlendFactor.ZERO; }
        public BlendFactor srcAlphaFactor() { return BlendFactor.ONE; }
        public BlendFactor dstAlphaFactor() { return BlendFactor.ZERO; }
        public BlendEquation colorEquation() { return BlendEquation.ADD; }
        public BlendEquation alphaEquation() { return BlendEquation.ADD; }
    };

    BlendMode ALPHA = new BlendMode() {
        public String name()               { return "ALPHA"; }
        public boolean enabled()           { return true; }
        public BlendFactor srcColorFactor() { return BlendFactor.SRC_ALPHA; }
        public BlendFactor dstColorFactor() { return BlendFactor.ONE_MINUS_SRC_ALPHA; }
        public BlendFactor srcAlphaFactor() { return BlendFactor.ONE; }
        public BlendFactor dstAlphaFactor() { return BlendFactor.ONE_MINUS_SRC_ALPHA; }
        public BlendEquation colorEquation() { return BlendEquation.ADD; }
        public BlendEquation alphaEquation() { return BlendEquation.ADD; }
    };

    BlendMode ADDITIVE = new BlendMode() {
        public String name()               { return "ADDITIVE"; }
        public boolean enabled()           { return true; }
        public BlendFactor srcColorFactor() { return BlendFactor.SRC_ALPHA; }
        public BlendFactor dstColorFactor() { return BlendFactor.ONE; }
        public BlendFactor srcAlphaFactor() { return BlendFactor.ONE; }
        public BlendFactor dstAlphaFactor() { return BlendFactor.ONE; }
        public BlendEquation colorEquation() { return BlendEquation.ADD; }
        public BlendEquation alphaEquation() { return BlendEquation.ADD; }
    };

    BlendMode MULTIPLY = new BlendMode() {
        public String name()               { return "MULTIPLY"; }
        public boolean enabled()           { return true; }
        public BlendFactor srcColorFactor() { return BlendFactor.DST_COLOR; }
        public BlendFactor dstColorFactor() { return BlendFactor.ZERO; }
        public BlendFactor srcAlphaFactor() { return BlendFactor.DST_ALPHA; }
        public BlendFactor dstAlphaFactor() { return BlendFactor.ZERO; }
        public BlendEquation colorEquation() { return BlendEquation.ADD; }
        public BlendEquation alphaEquation() { return BlendEquation.ADD; }
    };

    BlendMode PREMULTIPLIED = new BlendMode() {
        public String name()               { return "PREMULTIPLIED"; }
        public boolean enabled()           { return true; }
        public BlendFactor srcColorFactor() { return BlendFactor.ONE; }
        public BlendFactor dstColorFactor() { return BlendFactor.ONE_MINUS_SRC_ALPHA; }
        public BlendFactor srcAlphaFactor() { return BlendFactor.ONE; }
        public BlendFactor dstAlphaFactor() { return BlendFactor.ONE_MINUS_SRC_ALPHA; }
        public BlendEquation colorEquation() { return BlendEquation.ADD; }
        public BlendEquation alphaEquation() { return BlendEquation.ADD; }
    };
}

