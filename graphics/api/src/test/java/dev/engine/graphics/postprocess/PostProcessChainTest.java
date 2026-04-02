package dev.engine.graphics.postprocess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PostProcessChainTest {

    private PostProcessChain chain;
    private List<String> executionOrder;

    @BeforeEach
    void setUp() {
        chain = new PostProcessChain();
        executionOrder = new ArrayList<>();
    }

    @Test void emptyChainHasNoEffects() {
        assertEquals(0, chain.size());
    }

    @Test void addAndExecuteEffect() {
        chain.add("tonemap", ctx -> executionOrder.add("tonemap"));
        chain.execute(new PostProcessContext());
        assertEquals(List.of("tonemap"), executionOrder);
    }

    @Test void multipleEffectsExecuteInOrder() {
        chain.add("bloom", ctx -> executionOrder.add("bloom"));
        chain.add("tonemap", ctx -> executionOrder.add("tonemap"));
        chain.add("fxaa", ctx -> executionOrder.add("fxaa"));
        chain.execute(new PostProcessContext());
        assertEquals(List.of("bloom", "tonemap", "fxaa"), executionOrder);
    }

    @Test void removeEffect() {
        chain.add("bloom", ctx -> executionOrder.add("bloom"));
        chain.add("fxaa", ctx -> executionOrder.add("fxaa"));
        chain.remove("bloom");
        chain.execute(new PostProcessContext());
        assertEquals(List.of("fxaa"), executionOrder);
    }

    @Test void enableDisableEffect() {
        chain.add("tonemap", ctx -> executionOrder.add("tonemap"));
        chain.setEnabled("tonemap", false);
        chain.execute(new PostProcessContext());
        assertTrue(executionOrder.isEmpty());

        chain.setEnabled("tonemap", true);
        chain.execute(new PostProcessContext());
        assertEquals(List.of("tonemap"), executionOrder);
    }
}
