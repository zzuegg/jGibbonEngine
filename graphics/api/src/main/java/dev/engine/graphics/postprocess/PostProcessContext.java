package dev.engine.graphics.postprocess;

import dev.engine.core.handle.Handle;
import dev.engine.graphics.TextureResource;
import dev.engine.graphics.command.CommandRecorder;

/**
 * Context passed to post-processing effects.
 * Provides source/destination textures, screen dimensions, and a command recorder.
 */
public record PostProcessContext(
        Handle<TextureResource> sourceTexture,
        Handle<TextureResource> destinationTexture,
        int width,
        int height,
        CommandRecorder recorder
) {}
