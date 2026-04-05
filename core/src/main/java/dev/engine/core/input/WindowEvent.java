package dev.engine.core.input;

import dev.engine.core.module.Time;

public sealed interface WindowEvent {

    Time time();

    record Resized(Time time, int width, int height) implements WindowEvent {}
    record FocusGained(Time time) implements WindowEvent {}
    record FocusLost(Time time) implements WindowEvent {}
    record Closed(Time time) implements WindowEvent {}
    record Moved(Time time, int x, int y) implements WindowEvent {}
}
