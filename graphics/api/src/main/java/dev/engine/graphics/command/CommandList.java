package dev.engine.graphics.command;

import java.util.Collections;
import java.util.List;

/**
 * An immutable list of recorded render commands.
 * Built by {@link CommandRecorder}, submitted to a backend's CommandExecutor.
 */
public record CommandList(List<RenderCommand> commands) {
    public CommandList {
        commands = Collections.unmodifiableList(commands);
    }

    public int size() { return commands.size(); }
    public boolean isEmpty() { return commands.isEmpty(); }
}
