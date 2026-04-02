package dev.engine.core.rendergraph;

public interface RenderPass {

    void setup(PassBuilder builder);

    void execute(PassContext ctx);
}
