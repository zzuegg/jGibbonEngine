package dev.engine.ui;

/**
 * Draw commands produced by the UI context.
 * The renderer iterates these to produce GPU draw calls.
 */
public sealed interface NkDrawCommand {

    record Scissor(int x, int y, int w, int h) implements NkDrawCommand {}

    record FilledRect(NkRect rect, float rounding, NkColor color) implements NkDrawCommand {}

    record StrokedRect(NkRect rect, float rounding, float lineThickness, NkColor color) implements NkDrawCommand {}

    record FilledTriangle(NkVec2 a, NkVec2 b, NkVec2 c, NkColor color) implements NkDrawCommand {}

    record FilledCircle(NkRect bounds, NkColor color) implements NkDrawCommand {}

    record Line(NkVec2 from, NkVec2 to, float lineThickness, NkColor color) implements NkDrawCommand {}

    record Text(NkRect rect, String text, NkFont font, NkColor fg) implements NkDrawCommand {}
}
