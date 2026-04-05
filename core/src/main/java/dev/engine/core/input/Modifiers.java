package dev.engine.core.input;

public record Modifiers(int bits) {
    public boolean shift()    { return (bits & 0x01) != 0; }
    public boolean ctrl()     { return (bits & 0x02) != 0; }
    public boolean alt()      { return (bits & 0x04) != 0; }
    public boolean superKey() { return (bits & 0x08) != 0; }
}
