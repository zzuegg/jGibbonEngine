package dev.engine.graphics.buffer;

public record BufferDescriptor(long size, BufferUsage usage, AccessPattern accessPattern) {}
