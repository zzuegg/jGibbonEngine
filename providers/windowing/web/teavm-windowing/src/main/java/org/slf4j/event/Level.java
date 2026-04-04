package org.slf4j.event;

/**
 * Minimal SLF4J Level shim for TeaVM.
 */
public enum Level {
    ERROR(40),
    WARN(30),
    INFO(20),
    DEBUG(10),
    TRACE(0);

    private final int levelInt;

    Level(int i) {
        this.levelInt = i;
    }

    public int toInt() {
        return levelInt;
    }
}
