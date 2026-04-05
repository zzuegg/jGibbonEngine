package dev.engine.ui;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts {@link NkDrawCommand}s into vertex/index buffers for GPU rendering.
 *
 * <p>Vertex format: position (2 floats) + texcoord (2 floats) + color (1 packed int) = 20 bytes.
 * Index format: unsigned short (2 bytes).
 *
 * <p>Produces a list of {@link DrawBatch} records, each with a scissor rect and
 * a range of indices to draw.
 */
public class NkDrawList {

    /** A batch of draw commands sharing the same scissor rect. */
    public record DrawBatch(int scissorX, int scissorY, int scissorW, int scissorH,
                            int indexOffset, int indexCount) {}

    // Vertex: pos.x, pos.y, uv.x, uv.y, color (packed ABGR as float bits)
    private static final int VERTEX_SIZE = 20; // bytes

    private ByteBuffer vertexBuffer;
    private ByteBuffer indexBuffer;
    private final List<DrawBatch> batches = new ArrayList<>();
    private int vertexCount;
    private int indexCount;

    // Current scissor
    private int scissorX, scissorY, scissorW = 8192, scissorH = 8192;
    private int batchIndexStart;

    // Circle approximation
    private static final int CIRCLE_SEGMENTS = 16;

    public NkDrawList() {
        vertexBuffer = ByteBuffer.allocateDirect(64 * 1024).order(ByteOrder.nativeOrder());
        indexBuffer = ByteBuffer.allocateDirect(32 * 1024).order(ByteOrder.nativeOrder());
    }

    public void clear() {
        vertexBuffer.clear();
        indexBuffer.clear();
        batches.clear();
        vertexCount = 0;
        indexCount = 0;
        batchIndexStart = 0;
        scissorX = 0;
        scissorY = 0;
        scissorW = 8192;
        scissorH = 8192;
    }

    /**
     * Converts a list of NkDrawCommands into vertex/index buffer data.
     */
    public void convert(List<NkDrawCommand> commands, NkFont font) {
        clear();
        for (var cmd : commands) {
            switch (cmd) {
                case NkDrawCommand.Scissor s -> {
                    flushBatch();
                    scissorX = s.x();
                    scissorY = s.y();
                    scissorW = s.w();
                    scissorH = s.h();
                }
                case NkDrawCommand.FilledRect r -> drawFilledRect(r.rect(), r.rounding(), r.color(), font);
                case NkDrawCommand.StrokedRect r -> drawStrokedRect(r.rect(), r.rounding(), r.lineThickness(), r.color(), font);
                case NkDrawCommand.FilledTriangle t -> drawFilledTriangle(t.a(), t.b(), t.c(), t.color(), font);
                case NkDrawCommand.FilledCircle c -> drawFilledCircle(c.bounds(), c.color(), font);
                case NkDrawCommand.Line l -> drawLine(l.from(), l.to(), l.lineThickness(), l.color(), font);
                case NkDrawCommand.Text t -> drawText(t.rect(), t.text(), t.font(), t.fg());
            }
        }
        flushBatch();
    }

    // ========================= Primitive drawing =========================

    private void drawFilledRect(NkRect rect, float rounding, NkColor color, NkFont font) {
        float u = font.whiteU();
        float v = font.whiteV();
        int packed = color.toPackedABGR();

        if (rounding <= 0) {
            int base = vertexCount;
            addVertex(rect.x(), rect.y(), u, v, packed);
            addVertex(rect.x() + rect.w(), rect.y(), u, v, packed);
            addVertex(rect.x() + rect.w(), rect.y() + rect.h(), u, v, packed);
            addVertex(rect.x(), rect.y() + rect.h(), u, v, packed);
            addIndex(base, base + 1, base + 2);
            addIndex(base, base + 2, base + 3);
        } else {
            // Rounded rect: fan from center with arc corners and straight edges
            float cx = rect.x() + rect.w() / 2;
            float cy = rect.y() + rect.h() / 2;
            int centerIdx = vertexCount;
            addVertex(cx, cy, u, v, packed);

            float r = Math.min(rounding, Math.min(rect.w(), rect.h()) / 2);
            int arcSegments = 4; // segments per 90-degree corner arc

            // Corner centers: top-right, top-left, bottom-left, bottom-right
            float[][] corners = {
                {rect.x() + rect.w() - r, rect.y() + r},           // TR
                {rect.x() + r,            rect.y() + r},           // TL
                {rect.x() + r,            rect.y() + rect.h() - r}, // BL
                {rect.x() + rect.w() - r, rect.y() + rect.h() - r}  // BR
            };
            // Start angles for each corner arc (radians)
            float[] startAngles = {
                (float)(-Math.PI / 2), // TR: -90 to 0
                (float)( Math.PI),     // TL: 180 to 270
                (float)( Math.PI / 2), // BL: 90 to 180
                0f                      // BR: 0 to 90
            };

            int firstRim = vertexCount;
            for (int corner = 0; corner < 4; corner++) {
                float ccx = corners[corner][0], ccy = corners[corner][1];
                float startAngle = startAngles[corner];
                for (int j = 0; j <= arcSegments; j++) {
                    float angle = startAngle + (float)(j * Math.PI / 2 / arcSegments);
                    float px = ccx + r * (float) Math.cos(angle);
                    float py = ccy + r * (float) Math.sin(angle);
                    addVertex(px, py, u, v, packed);
                }
            }
            int totalRimVerts = 4 * (arcSegments + 1);
            for (int j = 1; j < totalRimVerts; j++) {
                addIndex(centerIdx, firstRim + j - 1, firstRim + j);
            }
            // Close the fan
            addIndex(centerIdx, firstRim + totalRimVerts - 1, firstRim);
        }
    }

    private void drawStrokedRect(NkRect rect, float rounding, float thickness, NkColor color, NkFont font) {
        float x = rect.x(), y = rect.y(), w = rect.w(), h = rect.h();
        float t = thickness;
        // Top
        drawFilledRect(new NkRect(x, y, w, t), 0, color, font);
        // Bottom
        drawFilledRect(new NkRect(x, y + h - t, w, t), 0, color, font);
        // Left
        drawFilledRect(new NkRect(x, y + t, t, h - 2 * t), 0, color, font);
        // Right
        drawFilledRect(new NkRect(x + w - t, y + t, t, h - 2 * t), 0, color, font);
    }

    private void drawFilledTriangle(NkVec2 a, NkVec2 b, NkVec2 c, NkColor color, NkFont font) {
        float u = font.whiteU();
        float v = font.whiteV();
        int packed = color.toPackedABGR();
        int base = vertexCount;
        addVertex(a.x(), a.y(), u, v, packed);
        addVertex(b.x(), b.y(), u, v, packed);
        addVertex(c.x(), c.y(), u, v, packed);
        addIndex(base, base + 1, base + 2);
    }

    private void drawFilledCircle(NkRect bounds, NkColor color, NkFont font) {
        float u = font.whiteU();
        float v = font.whiteV();
        int packed = color.toPackedABGR();

        float cx = bounds.x() + bounds.w() / 2;
        float cy = bounds.y() + bounds.h() / 2;
        float rx = bounds.w() / 2;
        float ry = bounds.h() / 2;

        int centerIdx = vertexCount;
        addVertex(cx, cy, u, v, packed);

        int firstRim = vertexCount;
        for (int i = 0; i <= CIRCLE_SEGMENTS; i++) {
            float angle = (float) (i * 2 * Math.PI / CIRCLE_SEGMENTS);
            float px = cx + rx * (float) Math.cos(angle);
            float py = cy + ry * (float) Math.sin(angle);
            addVertex(px, py, u, v, packed);
            if (i > 0) {
                addIndex(centerIdx, firstRim + i - 1, firstRim + i);
            }
        }
    }

    private void drawLine(NkVec2 from, NkVec2 to, float thickness, NkColor color, NkFont font) {
        float dx = to.x() - from.x();
        float dy = to.y() - from.y();
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001f) return;

        float nx = -dy / len * thickness * 0.5f;
        float ny = dx / len * thickness * 0.5f;

        float u = font.whiteU();
        float v = font.whiteV();
        int packed = color.toPackedABGR();

        int base = vertexCount;
        addVertex(from.x() + nx, from.y() + ny, u, v, packed);
        addVertex(from.x() - nx, from.y() - ny, u, v, packed);
        addVertex(to.x() - nx, to.y() - ny, u, v, packed);
        addVertex(to.x() + nx, to.y() + ny, u, v, packed);
        addIndex(base, base + 1, base + 2);
        addIndex(base, base + 2, base + 3);
    }

    private void drawText(NkRect rect, String text, NkFont font, NkColor color) {
        int packed = color.toPackedABGR();
        float x = rect.x();
        float y = rect.y();

        for (int i = 0; i < text.length(); i++) {
            int cp = text.charAt(i);
            NkFont.Glyph g = font.glyph(cp);
            if (g == null) {
                x += font.height() * 0.5f; // fallback advance
                continue;
            }

            float gx = x + g.xOffset();
            float gy = y + g.yOffset();
            float gw = g.width();
            float gh = g.height();

            int base = vertexCount;
            addVertex(gx, gy, g.u0(), g.v0(), packed);
            addVertex(gx + gw, gy, g.u1(), g.v0(), packed);
            addVertex(gx + gw, gy + gh, g.u1(), g.v1(), packed);
            addVertex(gx, gy + gh, g.u0(), g.v1(), packed);
            addIndex(base, base + 1, base + 2);
            addIndex(base, base + 2, base + 3);

            x += g.xAdvance();
        }
    }

    // ========================= Buffer management =========================

    private void addVertex(float x, float y, float u, float v, int color) {
        ensureVertexCapacity(VERTEX_SIZE);
        vertexBuffer.putFloat(x);
        vertexBuffer.putFloat(y);
        vertexBuffer.putFloat(u);
        vertexBuffer.putFloat(v);
        vertexBuffer.putInt(color);
        vertexCount++;
    }

    private void addIndex(int a, int b, int c) {
        ensureIndexCapacity(12); // 3 ints = 12 bytes
        indexBuffer.putInt(a);
        indexBuffer.putInt(b);
        indexBuffer.putInt(c);
        indexCount += 3;
    }

    private void ensureVertexCapacity(int bytes) {
        if (vertexBuffer.remaining() < bytes) {
            var newBuf = ByteBuffer.allocateDirect(vertexBuffer.capacity() * 2).order(ByteOrder.nativeOrder());
            vertexBuffer.flip();
            newBuf.put(vertexBuffer);
            vertexBuffer = newBuf;
        }
    }

    private void ensureIndexCapacity(int bytes) {
        if (indexBuffer.remaining() < bytes) {
            var newBuf = ByteBuffer.allocateDirect(indexBuffer.capacity() * 2).order(ByteOrder.nativeOrder());
            indexBuffer.flip();
            newBuf.put(indexBuffer);
            indexBuffer = newBuf;
        }
    }

    private void flushBatch() {
        int count = indexCount - batchIndexStart;
        if (count > 0) {
            batches.add(new DrawBatch(scissorX, scissorY, scissorW, scissorH,
                    batchIndexStart, count));
        }
        batchIndexStart = indexCount;
    }

    // ========================= Output =========================

    /** Returns the vertex buffer data (flipped, ready to read). */
    public ByteBuffer vertexData() {
        var buf = vertexBuffer.duplicate();
        buf.flip();
        return buf;
    }

    /** Returns the index buffer data (flipped, ready to read). */
    public ByteBuffer indexData() {
        var buf = indexBuffer.duplicate();
        buf.flip();
        return buf;
    }

    public int vertexCount() { return vertexCount; }
    public int indexCount() { return indexCount; }
    public int vertexStride() { return VERTEX_SIZE; }
    public List<DrawBatch> batches() { return batches; }
}
