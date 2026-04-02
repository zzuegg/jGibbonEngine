package dev.engine.graphics.command;

/**
 * Backend-specific translator from {@link CommandList} to native API calls.
 * Each backend (GL, Vulkan, WebGPU) provides one implementation.
 */
public interface CommandExecutor {

    void execute(CommandList commands);
}
