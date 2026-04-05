package dev.engine.ui;

/**
 * Font interface for the UI system. Provides glyph metrics and atlas data.
 */
public interface NkFont {

    /** Font height in pixels. */
    float height();

    /** Measures the pixel width of the given text string. */
    float textWidth(String text);

    /** Width of the atlas texture in pixels. */
    int atlasWidth();

    /** Height of the atlas texture in pixels. */
    int atlasHeight();

    /** RGBA8 pixel data for the font atlas (atlasWidth * atlasHeight * 4 bytes). */
    byte[] atlasData();

    /** Returns glyph info for a codepoint. Returns null for missing glyphs. */
    Glyph glyph(int codepoint);

    /** UV coordinates for a 1x1 white pixel in the atlas (for solid color drawing). */
    float whiteU();
    float whiteV();

    record Glyph(float xAdvance, float u0, float v0, float u1, float v1,
                 float xOffset, float yOffset, float width, float height) {}
}
