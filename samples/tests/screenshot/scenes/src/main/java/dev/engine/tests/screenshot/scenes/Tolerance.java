package dev.engine.tests.screenshot.scenes;

public record Tolerance(int maxChannelDiff, double maxDiffPercent) {
    public static Tolerance exact() { return new Tolerance(0, 0.0); }
    public static Tolerance tight() { return new Tolerance(1, 0.001); }
    public static Tolerance loose() { return new Tolerance(2, 0.01); }
    public static Tolerance wide()  { return new Tolerance(3, 0.5); }
}
