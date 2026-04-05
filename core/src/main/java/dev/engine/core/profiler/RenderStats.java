package dev.engine.core.profiler;

public class RenderStats {

    private int drawCalls;
    private long verticesSubmitted;
    private long indicesSubmitted;
    private int pipelineBinds;
    private int textureBinds;
    private int bufferBinds;
    private int renderTargetBinds;

    private final ResourceStats resourceStats = new ResourceStats();

    public void recordDrawCall(int vertices, int indices) {
        drawCalls++;
        verticesSubmitted += vertices;
        indicesSubmitted += indices;
    }

    public void recordPipelineBind() { pipelineBinds++; }
    public void recordTextureBind() { textureBinds++; }
    public void recordBufferBind() { bufferBinds++; }
    public void recordRenderTargetBind() { renderTargetBinds++; }

    public int drawCalls() { return drawCalls; }
    public long verticesSubmitted() { return verticesSubmitted; }
    public long indicesSubmitted() { return indicesSubmitted; }
    public int pipelineBinds() { return pipelineBinds; }
    public int textureBinds() { return textureBinds; }
    public int bufferBinds() { return bufferBinds; }
    public int renderTargetBinds() { return renderTargetBinds; }

    /** Per-resource-type lifecycle stats (live totals + per-frame create/destroy counts). */
    public ResourceStats resources() { return resourceStats; }

    public void reset() {
        resourceStats.newFrame();
        drawCalls = 0;
        verticesSubmitted = 0;
        indicesSubmitted = 0;
        pipelineBinds = 0;
        textureBinds = 0;
        bufferBinds = 0;
        renderTargetBinds = 0;
    }

    @Override
    public String toString() {
        return "RenderStats{draws=%d, verts=%d, indices=%d, pipelines=%d, textures=%d, resources=%s}"
                .formatted(drawCalls, verticesSubmitted, indicesSubmitted, pipelineBinds, textureBinds, resourceStats);
    }
}
