package dev.engine.graphics.texture;

public interface MipMode {
    int levelCount();

    MipMode AUTO = new MipMode() {
        @Override public int levelCount() { return -1; }
        @Override public String toString() { return "AUTO"; }
    };

    MipMode NONE = new MipMode() {
        @Override public int levelCount() { return 1; }
        @Override public String toString() { return "NONE"; }
    };

    static MipMode levels(int n) {
        if (n < 1) throw new IllegalArgumentException("Level count must be >= 1");
        return new MipMode() {
            @Override public int levelCount() { return n; }
            @Override public String toString() { return "LEVELS(" + n + ")"; }
        };
    }
}
