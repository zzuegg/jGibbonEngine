package dev.engine.tests.screenshot.scenes;

/**
 * An A/B (or A/B/C) comparison test. All variants should produce
 * visually identical output. Each variant is rendered on each backend,
 * then all variants are compared pairwise within the same backend.
 */
public record ComparisonTest(Tolerance tolerance, Variant... variants) {

    public record Variant(String name, RenderTestScene scene) {}

    public static ComparisonTest of(String nameA, RenderTestScene a, String nameB, RenderTestScene b) {
        return new ComparisonTest(Tolerance.tight(), new Variant(nameA, a), new Variant(nameB, b));
    }

    public static ComparisonTest of(String nameA, RenderTestScene a,
                                     String nameB, RenderTestScene b,
                                     String nameC, RenderTestScene c) {
        return new ComparisonTest(Tolerance.tight(),
                new Variant(nameA, a), new Variant(nameB, b), new Variant(nameC, c));
    }

    public ComparisonTest withTolerance(Tolerance tolerance) {
        return new ComparisonTest(tolerance, variants);
    }
}
