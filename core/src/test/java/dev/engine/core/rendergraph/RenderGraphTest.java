package dev.engine.core.rendergraph;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RenderGraphTest {

    private RenderGraph graph;
    private List<String> executionOrder;

    @BeforeEach
    void setUp() {
        graph = new RenderGraph();
        executionOrder = new ArrayList<>();
    }

    @Nested
    class BasicExecution {
        @Test void singlePassExecutes() {
            graph.addPass("forward", new RenderPass() {
                @Override public void setup(PassBuilder builder) {}
                @Override public void execute(PassContext ctx) {
                    executionOrder.add("forward");
                }
            });
            graph.compile();
            graph.execute();
            assertEquals(List.of("forward"), executionOrder);
        }

        @Test void multiplePasesExecuteInOrder() {
            graph.addPass("shadows", new RenderPass() {
                @Override public void setup(PassBuilder builder) { builder.writes("shadowMap"); }
                @Override public void execute(PassContext ctx) { executionOrder.add("shadows"); }
            });
            graph.addPass("lighting", new RenderPass() {
                @Override public void setup(PassBuilder builder) { builder.reads("shadowMap"); }
                @Override public void execute(PassContext ctx) { executionOrder.add("lighting"); }
            });
            graph.compile();
            graph.execute();
            assertEquals(List.of("shadows", "lighting"), executionOrder);
        }
    }

    @Nested
    class DependencyOrdering {
        @Test void dependenciesResolveCorrectly() {
            // GBuffer -> Lighting -> Post
            graph.addPass("post", new RenderPass() {
                @Override public void setup(PassBuilder builder) { builder.reads("lit"); builder.writes("final"); }
                @Override public void execute(PassContext ctx) { executionOrder.add("post"); }
            });
            graph.addPass("gbuffer", new RenderPass() {
                @Override public void setup(PassBuilder builder) { builder.writes("albedo"); builder.writes("normals"); }
                @Override public void execute(PassContext ctx) { executionOrder.add("gbuffer"); }
            });
            graph.addPass("lighting", new RenderPass() {
                @Override public void setup(PassBuilder builder) { builder.reads("albedo"); builder.reads("normals"); builder.writes("lit"); }
                @Override public void execute(PassContext ctx) { executionOrder.add("lighting"); }
            });

            graph.compile();
            graph.execute();
            assertEquals(List.of("gbuffer", "lighting", "post"), executionOrder);
        }

        @Test void independentPassesCanCoexist() {
            graph.addPass("a", new RenderPass() {
                @Override public void setup(PassBuilder builder) { builder.writes("x"); }
                @Override public void execute(PassContext ctx) { executionOrder.add("a"); }
            });
            graph.addPass("b", new RenderPass() {
                @Override public void setup(PassBuilder builder) { builder.writes("y"); }
                @Override public void execute(PassContext ctx) { executionOrder.add("b"); }
            });
            graph.addPass("c", new RenderPass() {
                @Override public void setup(PassBuilder builder) { builder.reads("x"); builder.reads("y"); }
                @Override public void execute(PassContext ctx) { executionOrder.add("c"); }
            });
            graph.compile();
            graph.execute();
            // a and b before c, but order between a and b is unspecified
            assertEquals("c", executionOrder.getLast());
            assertEquals(3, executionOrder.size());
        }
    }

    @Nested
    class DynamicPasses {
        @Test void removeAndRecompile() {
            graph.addPass("a", new RenderPass() {
                @Override public void setup(PassBuilder builder) {}
                @Override public void execute(PassContext ctx) { executionOrder.add("a"); }
            });
            graph.addPass("b", new RenderPass() {
                @Override public void setup(PassBuilder builder) {}
                @Override public void execute(PassContext ctx) { executionOrder.add("b"); }
            });
            graph.compile();
            graph.execute();
            assertEquals(2, executionOrder.size());

            executionOrder.clear();
            graph.removePass("b");
            graph.compile();
            graph.execute();
            assertEquals(List.of("a"), executionOrder);
        }
    }

    @Nested
    class PassContextTests {
        @Test void passContextPassedToExecute() {
            var captured = new Object() { PassContext ctx; };
            graph.addPass("test", new RenderPass() {
                @Override public void setup(PassBuilder builder) {}
                @Override public void execute(PassContext ctx) { captured.ctx = ctx; }
            });
            graph.compile();
            graph.execute();
            assertNotNull(captured.ctx);
            assertEquals("test", captured.ctx.passName());
        }
    }
}
