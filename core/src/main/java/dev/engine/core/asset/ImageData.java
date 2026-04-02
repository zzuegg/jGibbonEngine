package dev.engine.core.asset;

import java.nio.ByteBuffer;

public record ImageData(int width, int height, int channels, ByteBuffer pixels) {}
