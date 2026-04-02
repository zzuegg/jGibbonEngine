package dev.engine.graphics.postprocess;

@FunctionalInterface
public interface PostProcessEffect {
    void apply(PostProcessContext context);
}
