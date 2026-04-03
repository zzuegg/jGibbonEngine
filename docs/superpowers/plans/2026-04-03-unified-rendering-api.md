# Unified Rendering API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the rendering interface into a safe, property-driven, backend-agnostic API with full modern feature support on both OpenGL 4.5 and Vulkan 1.3.

**Architecture:** Property-based configuration everywhere (render state, materials, window). Handle-based resources with Cleaner safety. Unified command recording for graphics + compute. Three-tier screenshot testing for regression safety.

**Tech Stack:** Java 25, LWJGL (OpenGL/Vulkan), Slang shaders, JUnit 5, Foreign Function & Memory API

**Spec:** `docs/superpowers/specs/2026-04-03-unified-rendering-api-design.md`

---

## File Map

### New Files

| File | Responsibility |
|------|---------------|
| `graphics/api/.../renderstate/RenderState.java` | PropertyKey constants for render state |
| `graphics/api/.../renderstate/CompareFunc.java` | Depth comparison functions |
| `graphics/api/.../renderstate/BlendMode.java` | Blend mode presets |
| `graphics/api/.../renderstate/CullMode.java` | Face culling modes |
| `graphics/api/.../renderstate/FrontFace.java` | Winding order |
| `graphics/api/.../renderstate/BarrierScope.java` | Memory barrier scopes |
| `graphics/api/.../texture/MipMode.java` | Mipmap generation modes |
| `graphics/api/.../pipeline/ComputePipelineDescriptor.java` | Compute shader descriptor |
| `graphics/api/.../command/DrawCall.java` | Safe bundled draw builder |
| `graphics/api/.../command/ValidationMode.java` | Validation toggle |
| `graphics/api/.../window/WindowProperty.java` | Window property keys |
| `examples/src/test/.../ScreenshotTestSuite.java` | Shared test scene library |
| `examples/src/test/.../CrossBackendTest.java` | Cross-backend comparison tests |

### Modified Files

| File | Changes |
|------|---------|
| `graphics/api/.../command/RenderCommand.java` | Add SetRenderState, PushConstants, BindComputePipeline, Dispatch, MemoryBarrier |
| `graphics/api/.../command/CommandRecorder.java` | Add new command methods, DrawCall support |
| `graphics/api/.../RenderDevice.java` | Add createComputePipeline, getTextureIndex, pushConstants support |
| `graphics/api/.../texture/TextureFormat.java` | Add HDR/integer formats |
| `graphics/api/.../texture/TextureDescriptor.java` | Add MipMode field |
| `graphics/api/.../window/WindowHandle.java` | Add property get/set |
| `graphics/opengl/.../GlRenderDevice.java` | Implement new commands, compute, auto-mipmaps, push constants |
| `graphics/vulcan/.../VkRenderDevice.java` | Implement textures, samplers, render targets, state commands, compute |
| `examples/src/test/.../RenderTestHarness.java` | Add 3-tier tolerance, cross-backend mode |
| `examples/src/test/.../CrossBackendScenes.java` | Add more test scenes |
| `examples/src/test/.../OpenGlRenderTest.java` | Add per-feature screenshot tests |
| `examples/src/test/.../VulkanRenderTest.java` | Add per-feature screenshot tests |

**Base package paths:**
- API: `graphics/api/src/main/java/dev/engine/graphics/`
- OpenGL: `graphics/opengl/src/main/java/dev/engine/graphics/opengl/`
- Vulkan: `graphics/vulcan/src/main/java/dev/engine/graphics/vulkan/`
- Tests: `examples/src/test/java/dev/engine/examples/`
- Test resources: `examples/src/test/resources/reference-screenshots/`

---

## Phase 1: Screenshot Tests (Capture Current Behavior)

### Task 1: Expand RenderTestHarness with 3-Tier Tolerance

**Files:**
- Modify: `examples/src/test/java/dev/engine/examples/RenderTestHarness.java`

- [ ] **Step 1: Write test for Tolerance record**

```java
// In a new file or inline test — verify Tolerance works
// For now, just add the Tolerance type to RenderTestHarness
```

- [ ] **Step 2: Add Tolerance record and cross-backend comparison to RenderTestHarness**

Add these to `RenderTestHarness.java`:

```java
public record Tolerance(int maxChannelDiff, double maxDiffPercent) {
    public static Tolerance tight() { return new Tolerance(1, 0.001); }
    public static Tolerance loose() { return new Tolerance(5, 0.05); }
    public static Tolerance exact() { return new Tolerance(0, 0.0); }
}
```

Update `assertBackendsMatch` to accept a `Tolerance` parameter. Add `assertCrossBackend(RenderTestScene scene, String name, Tolerance tolerance)` that renders on both backends, compares them, and saves both screenshots to `build/screenshots/`.

- [ ] **Step 3: Add saveScreenshot helper for human review**

Add a method that saves screenshots to `build/screenshots/<backend>/<name>.png` after every render, for tier-3 human review.

- [ ] **Step 4: Verify existing tests still pass**

Run: `./gradlew :examples:test --tests "dev.engine.examples.*" -x :graphics:webgpu:test`

- [ ] **Step 5: Commit**

```bash
git add examples/src/test/java/dev/engine/examples/RenderTestHarness.java
git commit -m "feat: 3-tier tolerance in RenderTestHarness"
```

---

### Task 2: Create ScreenshotTestSuite with Basic Scenes

**Files:**
- Create: `examples/src/test/java/dev/engine/examples/ScreenshotTestSuite.java`

- [ ] **Step 1: Create test suite with coloredTriangle scene**

```java
package dev.engine.examples;

import dev.engine.graphics.common.Renderer;
import dev.engine.graphics.command.CommandRecorder;
import dev.engine.core.mesh.VertexFormat;
import dev.engine.core.mesh.VertexAttribute;
import dev.engine.core.mesh.ComponentType;

public final class ScreenshotTestSuite {

    public static void coloredTriangle(Renderer renderer) {
        // Create a simple colored triangle using raw vertex data
        // Position (3f) + Color (3f) vertex format
        // Red, green, blue vertices
        // Renders one frame with clear + draw
    }

    public static void indexedCubeDepthTest(Renderer renderer) {
        // Two overlapping cubes at different Z depths
        // Verifies depth test works correctly
        // Front cube should occlude back cube
    }

    public static void multiEntityScene(Renderer renderer) {
        // Multiple entities with different transforms
        // Uses scene graph + MeshRenderer pipeline
        // Verifies per-object UBO upload
    }
}
```

Each scene method takes a Renderer, sets up entities/meshes/materials, calls `renderer.renderFrame()` exactly once. The harness captures the framebuffer after.

- [ ] **Step 2: Implement coloredTriangle**

Use the existing TriangleExample pattern: create vertex buffer with position+color, compile a simple passthrough shader, draw 3 vertices. Use `renderer.device()` for low-level access.

- [ ] **Step 3: Implement indexedCubeDepthTest**

Based on existing SpinningCubeExample: two cubes, one at z=-2 (red) and z=-4 (blue). Camera looking down -Z. Red cube should be in front.

- [ ] **Step 4: Implement multiEntityScene**

Based on existing CrossBackendScenes.TWO_CUBES_UNLIT pattern: create scene entities, assign mesh data and unlit materials, render via high-level Renderer.

- [ ] **Step 5: Commit**

```bash
git add examples/src/test/java/dev/engine/examples/ScreenshotTestSuite.java
git commit -m "feat: screenshot test suite with basic scenes"
```

---

### Task 3: Create Per-Backend and Cross-Backend Tests

**Files:**
- Modify: `examples/src/test/java/dev/engine/examples/OpenGlRenderTest.java`
- Modify: `examples/src/test/java/dev/engine/examples/VulkanRenderTest.java`
- Create: `examples/src/test/java/dev/engine/examples/CrossBackendTest.java`

- [ ] **Step 1: Add screenshot tests to OpenGlRenderTest**

```java
@Test void coloredTriangle() throws IOException {
    var harness = new RenderTestHarness(256, 256);
    harness.assertOpenGlMatchesReference(
        ScreenshotTestSuite::coloredTriangle, "colored_triangle");
}

@Test void indexedCubeDepthTest() throws IOException {
    var harness = new RenderTestHarness(256, 256);
    harness.assertOpenGlMatchesReference(
        ScreenshotTestSuite::indexedCubeDepthTest, "indexed_cube_depth");
}

@Test void multiEntityScene() throws IOException {
    var harness = new RenderTestHarness(256, 256);
    harness.assertOpenGlMatchesReference(
        ScreenshotTestSuite::multiEntityScene, "multi_entity");
}
```

- [ ] **Step 2: Add matching tests to VulkanRenderTest**

Same pattern but using `assertVulkanMatchesReference`.

- [ ] **Step 3: Create CrossBackendTest**

```java
package dev.engine.examples;

import org.junit.jupiter.api.Test;

public class CrossBackendTest {
    @Test void coloredTriangle_crossBackend() {
        var harness = new RenderTestHarness(256, 256);
        harness.assertCrossBackend(
            ScreenshotTestSuite::coloredTriangle,
            "colored_triangle",
            RenderTestHarness.Tolerance.loose());
    }

    @Test void indexedCubeDepthTest_crossBackend() {
        var harness = new RenderTestHarness(256, 256);
        harness.assertCrossBackend(
            ScreenshotTestSuite::indexedCubeDepthTest,
            "indexed_cube_depth",
            RenderTestHarness.Tolerance.loose());
    }

    @Test void multiEntityScene_crossBackend() {
        var harness = new RenderTestHarness(256, 256);
        harness.assertCrossBackend(
            ScreenshotTestSuite::multiEntityScene,
            "multi_entity",
            RenderTestHarness.Tolerance.loose());
    }
}
```

- [ ] **Step 4: Generate reference screenshots**

Run the save-reference mode first to capture baseline images for both backends.

- [ ] **Step 5: Run all tests and verify pass**

Run: `./gradlew :examples:test -x :graphics:webgpu:test`

- [ ] **Step 6: Commit**

```bash
git add examples/src/test/java/dev/engine/examples/
git add examples/src/test/resources/reference-screenshots/
git commit -m "feat: per-backend and cross-backend screenshot tests"
```

---

## Phase 2: New API Types (No Backend Changes)

### Task 4: Render State Value Types

**Files:**
- Create: `graphics/api/src/main/java/dev/engine/graphics/renderstate/CompareFunc.java`
- Create: `graphics/api/src/main/java/dev/engine/graphics/renderstate/BlendMode.java`
- Create: `graphics/api/src/main/java/dev/engine/graphics/renderstate/CullMode.java`
- Create: `graphics/api/src/main/java/dev/engine/graphics/renderstate/FrontFace.java`
- Test: `graphics/api/src/test/java/dev/engine/graphics/renderstate/RenderStateTest.java`

- [ ] **Step 1: Write test for render state value types**

```java
package dev.engine.graphics.renderstate;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RenderStateValueTypesTest {
    @Test void compareFuncHasExpectedInstances() {
        assertNotNull(CompareFunc.LESS);
        assertNotNull(CompareFunc.LEQUAL);
        assertNotNull(CompareFunc.GREATER);
        assertNotNull(CompareFunc.GEQUAL);
        assertNotNull(CompareFunc.EQUAL);
        assertNotNull(CompareFunc.NOT_EQUAL);
        assertNotNull(CompareFunc.ALWAYS);
        assertNotNull(CompareFunc.NEVER);
        assertNotEquals(CompareFunc.LESS, CompareFunc.GREATER);
    }

    @Test void blendModeHasExpectedInstances() {
        assertNotNull(BlendMode.NONE);
        assertNotNull(BlendMode.ALPHA);
        assertNotNull(BlendMode.ADDITIVE);
        assertNotNull(BlendMode.MULTIPLY);
        assertNotNull(BlendMode.PREMULTIPLIED);
    }

    @Test void cullModeHasExpectedInstances() {
        assertNotNull(CullMode.NONE);
        assertNotNull(CullMode.BACK);
        assertNotNull(CullMode.FRONT);
    }

    @Test void frontFaceHasExpectedInstances() {
        assertNotNull(FrontFace.CCW);
        assertNotNull(FrontFace.CW);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :graphics:api:test --tests "dev.engine.graphics.renderstate.*" `
Expected: compilation failure, classes don't exist

- [ ] **Step 3: Implement value types**

Each follows the project convention — interface with static instances (not enums):

```java
package dev.engine.graphics.renderstate;

public interface CompareFunc {
    String name();

    CompareFunc LESS      = () -> "LESS";
    CompareFunc LEQUAL    = () -> "LEQUAL";
    CompareFunc GREATER   = () -> "GREATER";
    CompareFunc GEQUAL    = () -> "GEQUAL";
    CompareFunc EQUAL     = () -> "EQUAL";
    CompareFunc NOT_EQUAL = () -> "NOT_EQUAL";
    CompareFunc ALWAYS    = () -> "ALWAYS";
    CompareFunc NEVER     = () -> "NEVER";
}
```

Same pattern for BlendMode (NONE, ALPHA, ADDITIVE, MULTIPLY, PREMULTIPLIED), CullMode (NONE, BACK, FRONT), FrontFace (CCW, CW).

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :graphics:api:test --tests "dev.engine.graphics.renderstate.*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add graphics/api/src/main/java/dev/engine/graphics/renderstate/
git add graphics/api/src/test/java/dev/engine/graphics/renderstate/
git commit -m "feat: render state value types (CompareFunc, BlendMode, CullMode, FrontFace)"
```

---

### Task 5: RenderState Interface with PropertyKeys

**Files:**
- Create: `graphics/api/src/main/java/dev/engine/graphics/renderstate/RenderState.java`
- Test: `graphics/api/src/test/java/dev/engine/graphics/renderstate/RenderStateTest.java`

- [ ] **Step 1: Write test for RenderState property keys and defaults**

```java
package dev.engine.graphics.renderstate;

import dev.engine.core.property.PropertyMap;
import dev.engine.core.property.PropertyKey;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RenderStateTest {
    @Test void keysAreDistinct() {
        assertNotEquals(RenderState.DEPTH_TEST, RenderState.DEPTH_WRITE);
        assertNotEquals(RenderState.BLEND_MODE, RenderState.CULL_MODE);
    }

    @Test void keysHaveCorrectTypes() {
        assertEquals(Boolean.class, RenderState.DEPTH_TEST.type());
        assertEquals(CompareFunc.class, RenderState.DEPTH_FUNC.type());
        assertEquals(BlendMode.class, RenderState.BLEND_MODE.type());
        assertEquals(CullMode.class, RenderState.CULL_MODE.type());
        assertEquals(FrontFace.class, RenderState.FRONT_FACE.type());
    }

    @Test void defaultsProvidesSafeBaseline() {
        PropertyMap defaults = RenderState.defaults();
        assertEquals(true, defaults.get(RenderState.DEPTH_TEST));
        assertEquals(true, defaults.get(RenderState.DEPTH_WRITE));
        assertEquals(CompareFunc.LESS, defaults.get(RenderState.DEPTH_FUNC));
        assertEquals(BlendMode.NONE, defaults.get(RenderState.BLEND_MODE));
        assertEquals(CullMode.BACK, defaults.get(RenderState.CULL_MODE));
        assertEquals(FrontFace.CCW, defaults.get(RenderState.FRONT_FACE));
        assertEquals(false, defaults.get(RenderState.WIREFRAME));
    }

    @Test void canBuildCustomState() {
        PropertyMap state = PropertyMap.builder()
            .set(RenderState.BLEND_MODE, BlendMode.ALPHA)
            .set(RenderState.DEPTH_WRITE, false)
            .build();
        assertEquals(BlendMode.ALPHA, state.get(RenderState.BLEND_MODE));
        assertEquals(false, state.get(RenderState.DEPTH_WRITE));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

- [ ] **Step 3: Implement RenderState**

```java
package dev.engine.graphics.renderstate;

import dev.engine.core.property.PropertyKey;
import dev.engine.core.property.PropertyMap;

public interface RenderState {
    PropertyKey<Boolean>     DEPTH_TEST  = PropertyKey.of("depthTest", Boolean.class);
    PropertyKey<Boolean>     DEPTH_WRITE = PropertyKey.of("depthWrite", Boolean.class);
    PropertyKey<CompareFunc> DEPTH_FUNC  = PropertyKey.of("depthFunc", CompareFunc.class);
    PropertyKey<BlendMode>   BLEND_MODE  = PropertyKey.of("blendMode", BlendMode.class);
    PropertyKey<CullMode>    CULL_MODE   = PropertyKey.of("cullMode", CullMode.class);
    PropertyKey<FrontFace>   FRONT_FACE  = PropertyKey.of("frontFace", FrontFace.class);
    PropertyKey<Boolean>     WIREFRAME   = PropertyKey.of("wireframe", Boolean.class);
    PropertyKey<Float>       LINE_WIDTH  = PropertyKey.of("lineWidth", Float.class);

    static PropertyMap defaults() {
        return PropertyMap.builder()
            .set(DEPTH_TEST, true)
            .set(DEPTH_WRITE, true)
            .set(DEPTH_FUNC, CompareFunc.LESS)
            .set(BLEND_MODE, BlendMode.NONE)
            .set(CULL_MODE, CullMode.BACK)
            .set(FRONT_FACE, FrontFace.CCW)
            .set(WIREFRAME, false)
            .build();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

- [ ] **Step 5: Commit**

```bash
git add graphics/api/src/main/java/dev/engine/graphics/renderstate/RenderState.java
git add graphics/api/src/test/java/dev/engine/graphics/renderstate/RenderStateTest.java
git commit -m "feat: RenderState interface with PropertyKey constants and safe defaults"
```

---

### Task 6: New RenderCommand Types

**Files:**
- Modify: `graphics/api/src/main/java/dev/engine/graphics/command/RenderCommand.java`
- Create: `graphics/api/src/main/java/dev/engine/graphics/renderstate/BarrierScope.java`
- Modify: `graphics/api/src/test/java/dev/engine/graphics/command/CommandListTest.java`

- [ ] **Step 1: Write tests for new command types**

Add to `CommandListTest.java`:

```java
@Test void setRenderStateCommand() {
    var state = PropertyMap.builder()
        .set(RenderState.DEPTH_TEST, true)
        .set(RenderState.BLEND_MODE, BlendMode.ALPHA)
        .build();
    var recorder = new CommandRecorder();
    recorder.setRenderState(state);
    var list = recorder.finish();
    assertInstanceOf(RenderCommand.SetRenderState.class, list.commands().getFirst());
}

@Test void dispatchCommand() {
    var recorder = new CommandRecorder();
    recorder.dispatch(8, 8, 1);
    var list = recorder.finish();
    var cmd = (RenderCommand.Dispatch) list.commands().getFirst();
    assertEquals(8, cmd.groupsX());
    assertEquals(8, cmd.groupsY());
    assertEquals(1, cmd.groupsZ());
}

@Test void memoryBarrierCommand() {
    var recorder = new CommandRecorder();
    recorder.memoryBarrier(BarrierScope.STORAGE_BUFFER);
    var list = recorder.finish();
    assertInstanceOf(RenderCommand.MemoryBarrier.class, list.commands().getFirst());
}

@Test void pushConstantsCommand() {
    var recorder = new CommandRecorder();
    var data = java.nio.ByteBuffer.allocate(64);
    recorder.pushConstants(data);
    var list = recorder.finish();
    assertInstanceOf(RenderCommand.PushConstants.class, list.commands().getFirst());
}
```

- [ ] **Step 2: Run tests to verify they fail**

- [ ] **Step 3: Implement BarrierScope**

```java
package dev.engine.graphics.renderstate;

public interface BarrierScope {
    String name();

    BarrierScope STORAGE_BUFFER = () -> "STORAGE_BUFFER";
    BarrierScope TEXTURE        = () -> "TEXTURE";
    BarrierScope ALL            = () -> "ALL";
}
```

- [ ] **Step 4: Add new records to RenderCommand sealed interface**

Add to `RenderCommand.java`:

```java
record SetRenderState(PropertyMap properties) implements RenderCommand {}
record PushConstants(java.nio.ByteBuffer data) implements RenderCommand {}
record BindComputePipeline(Handle<PipelineResource> pipeline) implements RenderCommand {}
record Dispatch(int groupsX, int groupsY, int groupsZ) implements RenderCommand {}
record MemoryBarrier(BarrierScope scope) implements RenderCommand {}
```

- [ ] **Step 5: Add new methods to CommandRecorder**

```java
public void setRenderState(PropertyMap properties) {
    commands.add(new RenderCommand.SetRenderState(properties));
}
public void pushConstants(java.nio.ByteBuffer data) {
    commands.add(new RenderCommand.PushConstants(data));
}
public void bindComputePipeline(Handle<PipelineResource> pipeline) {
    commands.add(new RenderCommand.BindComputePipeline(pipeline));
}
public void dispatch(int groupsX, int groupsY, int groupsZ) {
    commands.add(new RenderCommand.Dispatch(groupsX, groupsY, groupsZ));
}
public void memoryBarrier(BarrierScope scope) {
    commands.add(new RenderCommand.MemoryBarrier(scope));
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :graphics:api:test --tests "dev.engine.graphics.command.*"`

- [ ] **Step 7: Verify existing tests still pass**

Run: `./gradlew :graphics:api:test`

- [ ] **Step 8: Commit**

```bash
git add graphics/api/src/main/java/dev/engine/graphics/command/RenderCommand.java
git add graphics/api/src/main/java/dev/engine/graphics/command/CommandRecorder.java
git add graphics/api/src/main/java/dev/engine/graphics/renderstate/BarrierScope.java
git add graphics/api/src/test/java/dev/engine/graphics/command/CommandListTest.java
git commit -m "feat: new RenderCommand types (SetRenderState, PushConstants, Dispatch, MemoryBarrier)"
```

---

### Task 7: DrawCall Builder

**Files:**
- Create: `graphics/api/src/main/java/dev/engine/graphics/command/DrawCall.java`
- Create: `graphics/api/src/main/java/dev/engine/graphics/command/ValidationMode.java`
- Test: `graphics/api/src/test/java/dev/engine/graphics/command/DrawCallTest.java`

- [ ] **Step 1: Write tests for DrawCall builder**

```java
package dev.engine.graphics.command;

import dev.engine.core.handle.Handle;
import dev.engine.core.handle.HandlePool;
import dev.engine.graphics.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DrawCallTest {
    HandlePool<PipelineResource> pipelinePool = new HandlePool<>();
    HandlePool<BufferResource> bufferPool = new HandlePool<>();
    HandlePool<VertexInputResource> vertexInputPool = new HandlePool<>();

    @Test void indexedDrawCallBuilds() {
        var pipeline = pipelinePool.allocate();
        var vbo = bufferPool.allocate();
        var ibo = bufferPool.allocate();
        var vi = vertexInputPool.allocate();

        var call = DrawCall.indexed()
            .pipeline(pipeline)
            .vertices(vbo, vi)
            .indices(ibo)
            .count(36)
            .build();

        assertEquals(pipeline, call.pipeline());
        assertEquals(vbo, call.vertexBuffer());
        assertEquals(ibo, call.indexBuffer());
        assertEquals(36, call.indexCount());
    }

    @Test void validationRejectsMissingPipeline() {
        DrawCall.setValidation(true);
        var vbo = bufferPool.allocate();
        var vi = vertexInputPool.allocate();

        assertThrows(IllegalStateException.class, () ->
            DrawCall.indexed()
                .vertices(vbo, vi)
                .count(3)
                .build());
        DrawCall.setValidation(false);
    }

    @Test void noValidationAllowsMissingPipeline() {
        DrawCall.setValidation(false);
        var vbo = bufferPool.allocate();
        var vi = vertexInputPool.allocate();

        assertDoesNotThrow(() ->
            DrawCall.indexed()
                .vertices(vbo, vi)
                .count(3)
                .build());
    }

    @Test void nonIndexedDrawCall() {
        var pipeline = pipelinePool.allocate();
        var vbo = bufferPool.allocate();
        var vi = vertexInputPool.allocate();

        var call = DrawCall.nonIndexed()
            .pipeline(pipeline)
            .vertices(vbo, vi)
            .count(3)
            .build();

        assertEquals(3, call.vertexCount());
        assertNull(call.indexBuffer());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

- [ ] **Step 3: Implement ValidationMode**

```java
package dev.engine.graphics.command;

public enum ValidationMode { ENABLED, DISABLED }
```

- [ ] **Step 4: Implement DrawCall**

```java
package dev.engine.graphics.command;

import dev.engine.core.handle.Handle;
import dev.engine.graphics.*;
import java.nio.ByteBuffer;
import java.util.*;

public final class DrawCall {
    private static volatile boolean validation = true;

    // Fields for all bound resources
    private final Handle<PipelineResource> pipeline;
    private final Handle<BufferResource> vertexBuffer;
    private final Handle<VertexInputResource> vertexInput;
    private final Handle<BufferResource> indexBuffer;
    private final int vertexCount;
    private final int indexCount;
    private final int firstVertex;
    private final int firstIndex;
    private final Map<Integer, Handle<BufferResource>> uniformBuffers;
    private final Map<Integer, TextureBinding> textureBindings;
    private final Map<Integer, Handle<BufferResource>> storageBuffers;
    private final ByteBuffer pushConstants;

    // Record for texture + sampler pair
    public record TextureBinding(Handle<TextureResource> texture, Handle<SamplerResource> sampler) {}

    // Private constructor — use builders
    private DrawCall(Builder builder) { /* copy all fields */ }

    // Getters for all fields...

    public static void setValidation(boolean enabled) { validation = enabled; }

    public static IndexedBuilder indexed() { return new IndexedBuilder(); }
    public static NonIndexedBuilder nonIndexed() { return new NonIndexedBuilder(); }

    // Emits RenderCommand list for this draw call
    public List<RenderCommand> toCommands() {
        var cmds = new ArrayList<RenderCommand>();
        if (pipeline != null) cmds.add(new RenderCommand.BindPipeline(pipeline));
        if (vertexBuffer != null) cmds.add(new RenderCommand.BindVertexBuffer(vertexBuffer, vertexInput));
        if (indexBuffer != null) cmds.add(new RenderCommand.BindIndexBuffer(indexBuffer));
        uniformBuffers.forEach((binding, buf) -> cmds.add(new RenderCommand.BindUniformBuffer(binding, buf)));
        textureBindings.forEach((unit, tb) -> {
            cmds.add(new RenderCommand.BindTexture(unit, tb.texture()));
            cmds.add(new RenderCommand.BindSampler(unit, tb.sampler()));
        });
        storageBuffers.forEach((binding, buf) -> cmds.add(new RenderCommand.BindStorageBuffer(binding, buf)));
        if (pushConstants != null) cmds.add(new RenderCommand.PushConstants(pushConstants));
        if (indexBuffer != null) {
            cmds.add(new RenderCommand.DrawIndexed(indexCount, firstIndex));
        } else {
            cmds.add(new RenderCommand.Draw(vertexCount, firstVertex));
        }
        return cmds;
    }

    // Builder classes with validation on build()...
}
```

- [ ] **Step 5: Add `draw(DrawCall)` to CommandRecorder**

```java
public void draw(DrawCall call) {
    commands.addAll(call.toCommands());
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :graphics:api:test --tests "dev.engine.graphics.command.*"`

- [ ] **Step 7: Commit**

```bash
git add graphics/api/src/main/java/dev/engine/graphics/command/DrawCall.java
git add graphics/api/src/main/java/dev/engine/graphics/command/ValidationMode.java
git add graphics/api/src/main/java/dev/engine/graphics/command/CommandRecorder.java
git add graphics/api/src/test/java/dev/engine/graphics/command/DrawCallTest.java
git commit -m "feat: DrawCall builder with optional validation"
```

---

### Task 8: MipMode and Extended Texture Formats

**Files:**
- Create: `graphics/api/src/main/java/dev/engine/graphics/texture/MipMode.java`
- Modify: `graphics/api/src/main/java/dev/engine/graphics/texture/TextureFormat.java`
- Modify: `graphics/api/src/main/java/dev/engine/graphics/texture/TextureDescriptor.java`
- Test: `graphics/api/src/test/java/dev/engine/graphics/texture/TextureDescriptorTest.java`

- [ ] **Step 1: Write tests**

```java
package dev.engine.graphics.texture;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TextureDescriptorTest {
    @Test void rgbaDefaultsToAutoMips() {
        var desc = TextureDescriptor.rgba(512, 512);
        assertEquals(MipMode.AUTO, desc.mipMode());
    }

    @Test void depthDefaultsToNoMips() {
        var desc = TextureDescriptor.depth(1024, 1024);
        assertEquals(MipMode.NONE, desc.mipMode());
    }

    @Test void explicitMipLevels() {
        var mode = MipMode.levels(5);
        assertEquals(5, mode.levelCount());
    }

    @Test void hdrFormatsExist() {
        assertNotNull(TextureFormat.RGBA16F);
        assertNotNull(TextureFormat.RGBA32F);
        assertNotNull(TextureFormat.RG16F);
        assertNotNull(TextureFormat.R32F);
        assertNotNull(TextureFormat.R32UI);
        assertNotNull(TextureFormat.R32I);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

- [ ] **Step 3: Implement MipMode**

```java
package dev.engine.graphics.texture;

public interface MipMode {
    MipMode AUTO = new MipMode() {
        @Override public String toString() { return "AUTO"; }
        @Override public int levelCount() { return -1; }
    };
    MipMode NONE = new MipMode() {
        @Override public String toString() { return "NONE"; }
        @Override public int levelCount() { return 1; }
    };

    static MipMode levels(int n) {
        return new MipMode() {
            @Override public String toString() { return "LEVELS(" + n + ")"; }
            @Override public int levelCount() { return n; }
        };
    }

    int levelCount();
}
```

- [ ] **Step 4: Add HDR/integer formats to TextureFormat**

Add to `TextureFormat.java`:

```java
TextureFormat RGBA16F = () -> "RGBA16F";
TextureFormat RGBA32F = () -> "RGBA32F";
TextureFormat RG16F   = () -> "RG16F";
TextureFormat RG32F   = () -> "RG32F";
TextureFormat R16F    = () -> "R16F";
TextureFormat R32F    = () -> "R32F";
TextureFormat R32UI   = () -> "R32UI";
TextureFormat R32I    = () -> "R32I";
```

- [ ] **Step 5: Update TextureDescriptor to include MipMode**

Add `MipMode mipMode` field to TextureDescriptor record. Update factory methods:
- `rgba(w, h)` → mipMode = AUTO
- `depth(w, h)` → mipMode = NONE (add this factory if missing)
- Add `withMipMode(MipMode)` copy method

- [ ] **Step 6: Run tests to verify they pass**

- [ ] **Step 7: Verify all existing tests still pass**

Run: `./gradlew :graphics:api:test`

- [ ] **Step 8: Commit**

```bash
git add graphics/api/src/main/java/dev/engine/graphics/texture/
git add graphics/api/src/test/java/dev/engine/graphics/texture/
git commit -m "feat: MipMode, HDR/integer texture formats, TextureDescriptor update"
```

---

### Task 9: ComputePipelineDescriptor

**Files:**
- Create: `graphics/api/src/main/java/dev/engine/graphics/pipeline/ComputePipelineDescriptor.java`
- Modify: `graphics/api/src/main/java/dev/engine/graphics/RenderDevice.java`

- [ ] **Step 1: Write test**

```java
// In a new or existing test file
@Test void computePipelineDescriptorFromSource() {
    var desc = ComputePipelineDescriptor.of(
        new ShaderSource(ShaderStage.COMPUTE, "void main() {}"));
    assertEquals(ShaderStage.COMPUTE, desc.shader().stage());
}

@Test void computePipelineDescriptorFromBinary() {
    var desc = ComputePipelineDescriptor.ofSpirv(
        new ShaderBinary(ShaderStage.COMPUTE, new byte[]{0x03, 0x02}));
    assertNotNull(desc.binary());
}
```

- [ ] **Step 2: Implement ComputePipelineDescriptor**

```java
package dev.engine.graphics.pipeline;

public record ComputePipelineDescriptor(
    ShaderSource shader,
    ShaderBinary binary
) {
    public static ComputePipelineDescriptor of(ShaderSource source) {
        return new ComputePipelineDescriptor(source, null);
    }
    public static ComputePipelineDescriptor ofSpirv(ShaderBinary binary) {
        return new ComputePipelineDescriptor(null, binary);
    }
}
```

- [ ] **Step 3: Add createComputePipeline to RenderDevice interface**

Add to `RenderDevice.java`:

```java
default Handle<PipelineResource> createComputePipeline(ComputePipelineDescriptor descriptor) {
    throw new UnsupportedOperationException("Compute pipelines not supported by this backend");
}
```

Default implementation throws — backends override when they implement compute.

- [ ] **Step 4: Run tests, verify pass**

- [ ] **Step 5: Commit**

```bash
git add graphics/api/src/main/java/dev/engine/graphics/pipeline/ComputePipelineDescriptor.java
git add graphics/api/src/main/java/dev/engine/graphics/RenderDevice.java
git commit -m "feat: ComputePipelineDescriptor and RenderDevice.createComputePipeline"
```

---

### Task 10: WindowProperty Interface

**Files:**
- Create: `graphics/api/src/main/java/dev/engine/graphics/window/WindowProperty.java`
- Modify: `graphics/api/src/main/java/dev/engine/graphics/window/WindowHandle.java`
- Test: `graphics/api/src/test/java/dev/engine/graphics/window/WindowPropertyTest.java`

- [ ] **Step 1: Write test**

```java
package dev.engine.graphics.window;

import dev.engine.core.property.PropertyKey;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WindowPropertyTest {
    @Test void keysHaveCorrectTypes() {
        assertEquals(String.class, WindowProperty.TITLE.type());
        assertEquals(Boolean.class, WindowProperty.VSYNC.type());
        assertEquals(Boolean.class, WindowProperty.RESIZABLE.type());
        assertEquals(Boolean.class, WindowProperty.FULLSCREEN.type());
        assertEquals(Boolean.class, WindowProperty.DECORATED.type());
        assertEquals(Boolean.class, WindowProperty.VISIBLE.type());
        assertEquals(Integer.class, WindowProperty.SWAP_INTERVAL.type());
    }

    @Test void keysAreDistinct() {
        assertNotEquals(WindowProperty.TITLE, WindowProperty.VSYNC);
        assertNotEquals(WindowProperty.RESIZABLE, WindowProperty.FULLSCREEN);
    }
}
```

- [ ] **Step 2: Implement WindowProperty**

```java
package dev.engine.graphics.window;

import dev.engine.core.property.PropertyKey;

public interface WindowProperty {
    PropertyKey<String>  TITLE          = PropertyKey.of("title", String.class);
    PropertyKey<Boolean> VSYNC          = PropertyKey.of("vsync", Boolean.class);
    PropertyKey<Boolean> RESIZABLE      = PropertyKey.of("resizable", Boolean.class);
    PropertyKey<Boolean> FULLSCREEN     = PropertyKey.of("fullscreen", Boolean.class);
    PropertyKey<Boolean> DECORATED      = PropertyKey.of("decorated", Boolean.class);
    PropertyKey<Boolean> VISIBLE        = PropertyKey.of("visible", Boolean.class);
    PropertyKey<Integer> SWAP_INTERVAL  = PropertyKey.of("swapInterval", Integer.class);
}
```

- [ ] **Step 3: Add property get/set to WindowHandle**

Add default methods to `WindowHandle.java`:

```java
default <T> void set(PropertyKey<T> key, T value) {
    throw new UnsupportedOperationException("Property not supported: " + key.name());
}
default <T> T get(PropertyKey<T> key) {
    throw new UnsupportedOperationException("Property not supported: " + key.name());
}
```

- [ ] **Step 4: Run tests, verify pass**

- [ ] **Step 5: Commit**

```bash
git add graphics/api/src/main/java/dev/engine/graphics/window/WindowProperty.java
git add graphics/api/src/main/java/dev/engine/graphics/window/WindowHandle.java
git add graphics/api/src/test/java/dev/engine/graphics/window/WindowPropertyTest.java
git commit -m "feat: WindowProperty keys and property get/set on WindowHandle"
```

---

### Task 11: Add getTextureIndex to RenderDevice

**Files:**
- Modify: `graphics/api/src/main/java/dev/engine/graphics/RenderDevice.java`

- [ ] **Step 1: Add getTextureIndex default method**

Add to `RenderDevice.java`:

```java
/**
 * Returns an integer index for the given texture, usable in shaders for bindless access.
 * OpenGL: backed by ARB_bindless_texture. Vulkan: backed by descriptor indexing.
 */
default int getTextureIndex(Handle<TextureResource> texture) {
    throw new UnsupportedOperationException("Bindless textures not supported by this backend");
}
```

- [ ] **Step 2: Verify compile**

Run: `./gradlew :graphics:api:compileJava`

- [ ] **Step 3: Commit**

```bash
git add graphics/api/src/main/java/dev/engine/graphics/RenderDevice.java
git commit -m "feat: RenderDevice.getTextureIndex for unified bindless access"
```

---

## Phase 3: Vulkan Feature Parity

### Task 12: Vulkan Texture Create/Upload/Destroy

**Files:**
- Modify: `graphics/vulcan/src/main/java/dev/engine/graphics/vulkan/VkRenderDevice.java`
- Test: `graphics/vulcan/src/test/java/dev/engine/graphics/vulkan/VkTextureTest.java`

- [ ] **Step 1: Write test for Vulkan texture lifecycle**

```java
package dev.engine.graphics.vulkan;

import dev.engine.graphics.texture.*;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.*;

class VkTextureTest {
    @Test void createAndDestroyTexture() {
        // Use VkDeviceTest-style harness to create a Vulkan device
        // Create texture, verify handle is valid, destroy, verify invalid
    }

    @Test void uploadTextureData() {
        // Create 2x2 RGBA8 texture, upload 16 bytes, verify no crash
        // Read back via render to verify correct pixels
    }
}
```

- [ ] **Step 2: Implement VkTexture support in VkRenderDevice**

In `VkRenderDevice.java`, replace stubs at lines 399-420:

1. `createTexture`: Create VkImage (VK_IMAGE_USAGE_SAMPLED_BIT | TRANSFER_DST_BIT | TRANSFER_SRC_BIT), allocate device-local memory, bind memory, create VkImageView, transition layout to SHADER_READ_ONLY_OPTIMAL
2. `uploadTexture`: Create staging buffer (HOST_VISIBLE), copy pixels, transition image to TRANSFER_DST, vkCmdCopyBufferToImage, transition back to SHADER_READ_ONLY, free staging buffer
3. `destroyTexture`: Destroy image view, free memory, destroy image

Store texture state in a `VkTextureAllocation` record:
```java
record VkTextureAllocation(long image, long memory, long imageView, TextureDescriptor desc, boolean mipsDirty) {}
Map<Integer, VkTextureAllocation> textures = new HashMap<>();
```

- [ ] **Step 3: Add BindTexture command handling in submit()**

In the `submit()` method switch at line 845, handle `BindTexture`:
- Update descriptor set with combined image sampler
- Requires extending VkDescriptorManager to support texture descriptors

- [ ] **Step 4: Run test to verify**

- [ ] **Step 5: Commit**

```bash
git add graphics/vulcan/src/main/java/dev/engine/graphics/vulkan/VkRenderDevice.java
git add graphics/vulcan/src/test/java/dev/engine/graphics/vulkan/VkTextureTest.java
git commit -m "feat: Vulkan texture create, upload, destroy, and binding"
```

---

### Task 13: Vulkan Sampler Support

**Files:**
- Modify: `graphics/vulcan/src/main/java/dev/engine/graphics/vulkan/VkRenderDevice.java`

- [ ] **Step 1: Implement createSampler in VkRenderDevice**

Replace stub at line 461. Use `vkCreateSampler` with:
- Map FilterMode → VK_FILTER_NEAREST/LINEAR + mipmap mode
- Map WrapMode → VK_SAMPLER_ADDRESS_MODE_REPEAT/CLAMP_TO_EDGE/MIRRORED_REPEAT

Store in `Map<Integer, Long> vkSamplers`.

- [ ] **Step 2: Implement destroySampler**

`vkDestroySampler` + remove from map.

- [ ] **Step 3: Handle BindSampler in submit()**

Combined image sampler in descriptor set — the sampler gets paired with the texture at descriptor write time.

- [ ] **Step 4: Run existing tests, verify no regressions**

- [ ] **Step 5: Commit**

```bash
git commit -m "feat: Vulkan sampler create, destroy, and binding"
```

---

### Task 14: Vulkan Render Targets (FBO Equivalent)

**Files:**
- Modify: `graphics/vulcan/src/main/java/dev/engine/graphics/vulkan/VkRenderDevice.java`

- [ ] **Step 1: Implement createRenderTarget**

Replace stub at line 428:
1. For each color attachment: create VkImage (COLOR_ATTACHMENT | SAMPLED), allocate memory, create image view
2. For depth attachment: create depth image + view
3. Create a secondary VkRenderPass for this target (color + optional depth)
4. Create VkFramebuffer with the image views

Store in a `VkRenderTargetAllocation` record.

- [ ] **Step 2: Implement getRenderTargetColorTexture**

Return texture handle for the color attachment image, registered in the texture pool.

- [ ] **Step 3: Handle BindRenderTarget in submit()**

End current render pass, begin new render pass with the target's framebuffer.

- [ ] **Step 4: Handle BindDefaultRenderTarget**

End offscreen render pass, re-begin the swapchain render pass.

- [ ] **Step 5: Test with screenshot comparison**

- [ ] **Step 6: Commit**

```bash
git commit -m "feat: Vulkan render targets with multi-color attachment support"
```

---

### Task 15: Vulkan State Commands

**Files:**
- Modify: `graphics/vulcan/src/main/java/dev/engine/graphics/vulkan/VkRenderDevice.java`

- [ ] **Step 1: Enable VK_EXT_extended_dynamic_state in device creation**

In the constructor (line 84), add `VK_EXT_extended_dynamic_state` to required device extensions. Check availability first.

- [ ] **Step 2: Implement state commands in submit()**

In the switch at line 845, replace TODO stubs:

```java
case RenderCommand.SetDepthTest(boolean enabled) -> {
    vkCmdSetDepthTestEnable(cmd, enabled);
    vkCmdSetDepthWriteEnable(cmd, enabled);
}
case RenderCommand.SetBlending(boolean enabled) -> {
    // VK_EXT_extended_dynamic_state3 for dynamic blend
    // Or: pre-bake blend state into pipeline (simpler for now)
}
case RenderCommand.SetCullFace(boolean enabled) -> {
    vkCmdSetCullMode(cmd, enabled ? VK_CULL_MODE_BACK_BIT : VK_CULL_MODE_NONE);
}
case RenderCommand.SetWireframe(boolean enabled) -> {
    // Requires VK_EXT_extended_dynamic_state3 for dynamic polygon mode
    // Or skip for now — wireframe is a debug feature
}
```

- [ ] **Step 3: Implement SetRenderState command**

Iterate the PropertyMap, translate each key to the appropriate Vulkan command.

- [ ] **Step 4: Test with cross-backend screenshot comparison**

- [ ] **Step 5: Commit**

```bash
git commit -m "feat: Vulkan dynamic state commands (depth, cull, blend)"
```

---

### Task 16: Vulkan Storage Buffer Binding

**Files:**
- Modify: `graphics/vulcan/src/main/java/dev/engine/graphics/vulkan/VkRenderDevice.java`
- Modify: `graphics/vulcan/src/main/java/dev/engine/graphics/vulkan/VkDescriptorManager.java`

- [ ] **Step 1: Extend VkDescriptorManager for SSBOs**

Add storage buffer descriptor type to the layout and pool. Currently only supports UNIFORM_BUFFER. Add STORAGE_BUFFER bindings.

- [ ] **Step 2: Handle BindStorageBuffer in submit()**

Similar to BindUniformBuffer — mark pending SSBO, flush in descriptor update.

- [ ] **Step 3: Test with compute shader or SSBO-reading fragment shader**

- [ ] **Step 4: Commit**

```bash
git commit -m "feat: Vulkan storage buffer binding via descriptor sets"
```

---

## Phase 4: New Features (Both Backends)

### Task 17: Property-Based Render State in Backends

**Files:**
- Modify: `graphics/opengl/src/main/java/dev/engine/graphics/opengl/GlRenderDevice.java`
- Modify: `graphics/vulcan/src/main/java/dev/engine/graphics/vulkan/VkRenderDevice.java`

- [ ] **Step 1: Implement SetRenderState handling in GlRenderDevice**

In `executeCommand()` (line 412), add case for `SetRenderState`:

```java
case RenderCommand.SetRenderState(PropertyMap props) -> {
    if (props.contains(RenderState.DEPTH_TEST)) {
        boolean enabled = props.get(RenderState.DEPTH_TEST);
        if (enabled) glEnable(GL_DEPTH_TEST); else glDisable(GL_DEPTH_TEST);
    }
    if (props.contains(RenderState.DEPTH_WRITE)) {
        glDepthMask(props.get(RenderState.DEPTH_WRITE));
    }
    if (props.contains(RenderState.DEPTH_FUNC)) {
        glDepthFunc(mapCompareFunc(props.get(RenderState.DEPTH_FUNC)));
    }
    if (props.contains(RenderState.BLEND_MODE)) {
        applyBlendMode(props.get(RenderState.BLEND_MODE));
    }
    if (props.contains(RenderState.CULL_MODE)) {
        applyCullMode(props.get(RenderState.CULL_MODE));
    }
    if (props.contains(RenderState.FRONT_FACE)) {
        glFrontFace(props.get(RenderState.FRONT_FACE) == FrontFace.CCW ? GL_CCW : GL_CW);
    }
    if (props.contains(RenderState.WIREFRAME)) {
        glPolygonMode(GL_FRONT_AND_BACK, props.get(RenderState.WIREFRAME) ? GL_LINE : GL_FILL);
    }
}
```

Add helper methods `mapCompareFunc`, `applyBlendMode`, `applyCullMode`.

- [ ] **Step 2: Implement SetRenderState handling in VkRenderDevice**

Same pattern using `vkCmdSet*` calls.

- [ ] **Step 3: Write cross-backend screenshot test for render state**

Add to ScreenshotTestSuite:
```java
public static void blendModeAlpha(Renderer renderer) { /* semi-transparent quad over solid background */ }
public static void cullModeBack(Renderer renderer) { /* single-sided faces, back should be invisible */ }
```

- [ ] **Step 4: Run tests**

- [ ] **Step 5: Commit**

```bash
git commit -m "feat: SetRenderState command handling in OpenGL and Vulkan backends"
```

---

### Task 18: Push Constants

**Files:**
- Modify: `graphics/opengl/src/main/java/dev/engine/graphics/opengl/GlRenderDevice.java`
- Modify: `graphics/vulcan/src/main/java/dev/engine/graphics/vulkan/VkRenderDevice.java`

- [ ] **Step 1: OpenGL push constants emulation**

In GlRenderDevice:
1. On init, create a small UBO (128 bytes) at a reserved binding (15)
2. On PushConstants command: `glNamedBufferSubData(pushConstantUbo, 0, data.remaining(), data)`
3. Auto-bind the push constant UBO before each draw

- [ ] **Step 2: Vulkan native push constants**

In VkRenderDevice:
1. Add push constant range to pipeline layout in VkDescriptorManager (128 bytes, ALL_GRAPHICS | COMPUTE stages)
2. On PushConstants command: `vkCmdPushConstants(cmd, layout, stageFlags, 0, data)`

- [ ] **Step 3: Write test**

Simple shader that reads push constant and outputs it as color. Verify both backends produce the same result.

- [ ] **Step 4: Commit**

```bash
git commit -m "feat: push constants (native Vulkan, UBO-emulated OpenGL)"
```

---

### Task 19: Compute Pipelines and Dispatch

**Files:**
- Modify: `graphics/opengl/src/main/java/dev/engine/graphics/opengl/GlRenderDevice.java`
- Modify: `graphics/vulcan/src/main/java/dev/engine/graphics/vulkan/VkRenderDevice.java`

- [ ] **Step 1: OpenGL compute pipeline**

Implement `createComputePipeline`:
1. `glCreateProgram()` + compile compute shader + link
2. Store program handle in pipeline pool (same as graphics)

Handle `BindComputePipeline`: `glUseProgram(computeProgram)`
Handle `Dispatch`: `glDispatchCompute(groupsX, groupsY, groupsZ)`

- [ ] **Step 2: Vulkan compute pipeline**

Implement `createComputePipeline`:
1. Create compute shader module from SPIRV
2. `vkCreateComputePipelines` with the module + pipeline layout

Handle `BindComputePipeline`: `vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline)`
Handle `Dispatch`: `vkCmdDispatch(cmd, groupsX, groupsY, groupsZ)`

- [ ] **Step 3: Memory barriers**

OpenGL: `glMemoryBarrier()` with GL_SHADER_STORAGE_BARRIER_BIT, GL_TEXTURE_FETCH_BARRIER_BIT, GL_ALL_BARRIER_BITS
Vulkan: `vkCmdPipelineBarrier()` with appropriate stage/access masks

- [ ] **Step 4: Write compute test**

Compute shader that writes to an SSBO: `buffer[i] = i * 2`. Read back and verify. Cross-backend.

- [ ] **Step 5: Write screenshot test for compute → render pipeline**

Compute writes color data to SSBO → fragment shader reads SSBO → renders result. Compare across backends.

- [ ] **Step 6: Commit**

```bash
git commit -m "feat: compute pipeline creation, dispatch, and memory barriers"
```

---

### Task 20: Auto-Mipmap Generation

**Files:**
- Modify: `graphics/opengl/src/main/java/dev/engine/graphics/opengl/GlRenderDevice.java`
- Modify: `graphics/vulcan/src/main/java/dev/engine/graphics/vulkan/VkRenderDevice.java`

- [ ] **Step 1: Track mipsDirty per texture**

Add `Map<Integer, Boolean> textureMipsDirty` to both backends. Set true on `uploadTexture()` and when render target color attachment is written to.

- [ ] **Step 2: Allocate mip storage on creation**

When `MipMode.AUTO` or explicit levels:
- OpenGL: `glTextureStorage2D` with `log2(max(w,h)) + 1` levels
- Vulkan: `VkImageCreateInfo.mipLevels` set appropriately

When `MipMode.NONE`: single level only.

- [ ] **Step 3: Generate mips on bind when dirty**

In the `BindTexture` command handler, check if the sampler uses mipmaps AND texture mips are dirty:
- OpenGL: `glGenerateTextureMipmap(glTexName)`
- Vulkan: Blit chain from level 0 down, or use `vkCmdBlitImage` in a loop

Clear the dirty flag after generation.

- [ ] **Step 4: Detect mipmap sampler**

Need to know if the bound sampler uses mipmaps. Store the SamplerDescriptor alongside the GL/VK sampler. Check `minFilter` — if it's one of `NEAREST_MIPMAP_NEAREST` or `LINEAR_MIPMAP_LINEAR`, it needs mips.

- [ ] **Step 5: Write test**

Create texture, upload, bind with trilinear sampler → verify mips generated (render at distance where mips matter). Cross-backend screenshot.

- [ ] **Step 6: Commit**

```bash
git commit -m "feat: auto-mipmap generation on texture bind"
```

---

### Task 21: Unified Bindless / Descriptor Indexing

**Files:**
- Modify: `graphics/opengl/src/main/java/dev/engine/graphics/opengl/GlRenderDevice.java`
- Modify: `graphics/vulcan/src/main/java/dev/engine/graphics/vulkan/VkRenderDevice.java`

- [ ] **Step 1: OpenGL getTextureIndex**

Implement `getTextureIndex` using existing `getBindlessTextureHandle`:
- Maintain a `Map<Handle<TextureResource>, Integer>` mapping handles to sequential indices
- Store the uint64 bindless handles in an SSBO that shaders can access
- Return the index into this array

- [ ] **Step 2: Vulkan getTextureIndex via descriptor indexing**

Enable `VK_EXT_descriptor_indexing` (core in Vulkan 1.2):
- Create a large descriptor set with an array of combined image samplers (e.g., 4096 slots)
- `getTextureIndex` assigns the next free slot, writes the descriptor
- Returns the slot index

- [ ] **Step 3: Write test**

Shader that takes a texture index as push constant, samples from bindless array, outputs color. Cross-backend.

- [ ] **Step 4: Commit**

```bash
git commit -m "feat: unified bindless textures via getTextureIndex"
```

---

### Task 22: Cleaner-Based Handle Safety

**Files:**
- Modify: `core/src/main/java/dev/engine/core/handle/HandlePool.java`
- Modify: `graphics/opengl/src/main/java/dev/engine/graphics/opengl/GlRenderDevice.java`
- Modify: `graphics/vulcan/src/main/java/dev/engine/graphics/vulkan/VkRenderDevice.java`

- [ ] **Step 1: Add Cleaner registration to resource creation**

Each `createBuffer`, `createTexture`, etc. registers a cleanup action with `java.lang.ref.Cleaner`:

```java
private static final Cleaner CLEANER = Cleaner.create();

Handle<BufferResource> createBuffer(BufferDescriptor desc) {
    var handle = bufferPool.allocate();
    // ... create GL/VK resource ...
    CLEANER.register(handle, () -> {
        log.warn("Buffer {} leaked — cleaned up by GC", handle);
        // destroy native resource
    });
    return handle;
}
```

- [ ] **Step 2: Deregister on explicit destroy**

When `destroyBuffer` is called explicitly, deregister the Cleaner action (or mark it as already destroyed so the Cleaner no-ops).

- [ ] **Step 3: Log leaked resources on device close**

In `close()`, iterate all pools and log warnings for any handles still alive.

- [ ] **Step 4: Write test**

Create a buffer without destroying it. Call `device.close()`. Verify warning is logged.

- [ ] **Step 5: Commit**

```bash
git commit -m "feat: Cleaner-based resource safety net with leak warnings"
```

---

## Phase 5: Refactoring

### Task 23: Three-Layer Property Resolution in Renderer

**Files:**
- Modify: `graphics/common/src/main/java/dev/engine/graphics/common/Renderer.java`

- [ ] **Step 1: Add default/forced property maps to Renderer**

```java
private final MutablePropertyMap defaultProperties = new MutablePropertyMap();
private final MutablePropertyMap forcedProperties = new MutablePropertyMap();

public <T> void setDefault(PropertyKey<T> key, T value) { defaultProperties.set(key, value); }
public <T> void forceProperty(PropertyKey<T> key, T value) { forcedProperties.set(key, value); }
public <T> void clearForced(PropertyKey<T> key) { forcedProperties.remove(key); }
```

- [ ] **Step 2: Resolve properties per draw call**

In `renderFrame()`, for each entity, resolve: `forced > material > defaults`:

```java
private PropertyMap resolveProperties(MaterialData material) {
    var builder = PropertyMap.builder();
    // Layer 1: defaults
    for (var key : defaultProperties.keys()) builder.set(key, defaultProperties.get(key));
    // Layer 2: material overrides
    for (var key : material.keys()) builder.set(key, material.get(key));
    // Layer 3: forced overrides
    for (var key : forcedProperties.keys()) builder.set(key, forcedProperties.get(key));
    return builder.build();
}
```

- [ ] **Step 3: Emit SetRenderState command before each draw**

Replace individual `setDepthTest`, `setBlending` calls with a single `recorder.setRenderState(resolved)`.

- [ ] **Step 4: Test with forced wireframe override**

- [ ] **Step 5: Commit**

```bash
git commit -m "feat: three-layer property resolution in Renderer (forced > material > default)"
```

---

### Task 24: Window Toolkit Property Implementation

**Files:**
- Modify: `windowing/glfw/src/main/java/dev/engine/windowing/glfw/GlfwWindowToolkit.java` (or the actual window handle implementation)
- Modify: `bindings/sdl3/src/main/java/dev/engine/bindings/sdl3/Sdl3WindowToolkit.java`

- [ ] **Step 1: Implement property get/set in GLFW window handle**

Override the default get/set methods:

```java
@Override
public <T> void set(PropertyKey<T> key, T value) {
    if (key == WindowProperty.TITLE) glfwSetWindowTitle(handle, (String) value);
    else if (key == WindowProperty.VSYNC) glfwSwapInterval((Boolean) value ? 1 : 0);
    else if (key == WindowProperty.RESIZABLE) glfwSetWindowAttrib(handle, GLFW_RESIZABLE, (Boolean) value ? GLFW_TRUE : GLFW_FALSE);
    // ... etc
    properties.set(key, value); // store locally for get()
}

@Override
public <T> T get(PropertyKey<T> key) {
    return properties.get(key);
}
```

- [ ] **Step 2: Implement for SDL3 backend (if applicable)**

Same pattern using SDL3 calls.

- [ ] **Step 3: Write test**

Set window title via property, get it back, verify match.

- [ ] **Step 4: Commit**

```bash
git commit -m "feat: property-based window configuration (GLFW + SDL3)"
```

---

### Task 25: Extract Shared ResourceRegistry (Optional Refactoring)

**Files:**
- Create: `graphics/api/src/main/java/dev/engine/graphics/resource/ResourceRegistry.java`
- Modify: `graphics/opengl/src/main/java/dev/engine/graphics/opengl/GlRenderDevice.java`
- Modify: `graphics/vulcan/src/main/java/dev/engine/graphics/vulkan/VkRenderDevice.java`

- [ ] **Step 1: Extract shared resource tracking pattern**

Both backends use HandlePool + HashMap patterns for every resource type. Extract into a generic:

```java
public class ResourceRegistry<R, N> {
    private final HandlePool<R> pool;
    private final Map<Integer, N> nativeResources = new HashMap<>();

    public Handle<R> register(N nativeResource) { ... }
    public N get(Handle<R> handle) { ... }
    public N remove(Handle<R> handle) { ... }
    public boolean isValid(Handle<R> handle) { ... }
    public void forEach(BiConsumer<Handle<R>, N> action) { ... }
    public void clear() { ... }
}
```

- [ ] **Step 2: Refactor GlRenderDevice to use ResourceRegistry**

Replace 6+ pool+map pairs with `ResourceRegistry<BufferResource, Integer>`, `ResourceRegistry<TextureResource, GlTextureAllocation>`, etc.

- [ ] **Step 3: Refactor VkRenderDevice similarly**

- [ ] **Step 4: Verify all tests pass**

Run: `./gradlew test -x :graphics:webgpu:test`

- [ ] **Step 5: Commit**

```bash
git commit -m "refactor: extract shared ResourceRegistry for backend resource management"
```

---

### Task 26: Split VkRenderDevice

**Files:**
- Create: `graphics/vulcan/src/main/java/dev/engine/graphics/vulkan/VkResourceManager.java`
- Create: `graphics/vulcan/src/main/java/dev/engine/graphics/vulkan/VkCommandExecutor.java`
- Modify: `graphics/vulcan/src/main/java/dev/engine/graphics/vulkan/VkRenderDevice.java`

- [ ] **Step 1: Extract VkResourceManager**

Move buffer/texture/sampler/pipeline create/destroy methods out of VkRenderDevice into a focused resource manager class. VkRenderDevice delegates to it.

- [ ] **Step 2: Extract VkCommandExecutor**

Move the `submit()` switch logic into a dedicated command executor class.

- [ ] **Step 3: VkRenderDevice becomes a thin coordinator**

Holds VkResourceManager, VkCommandExecutor, VkSwapchain, VkFrameContext. Delegates all calls.

- [ ] **Step 4: Verify all Vulkan tests pass**

- [ ] **Step 5: Commit**

```bash
git commit -m "refactor: split VkRenderDevice into resource manager and command executor"
```

---

## Phase 6: Cleanup and Final Verification

### Task 27: Add Remaining Screenshot Test Scenes

**Files:**
- Modify: `examples/src/test/java/dev/engine/examples/ScreenshotTestSuite.java`
- Modify: `examples/src/test/java/dev/engine/examples/CrossBackendTest.java`

- [ ] **Step 1: Add advanced test scenes**

```java
public static void texturedQuad(Renderer renderer) { /* quad with checkerboard texture */ }
public static void alphaBlending(Renderer renderer) { /* overlapping semi-transparent quads */ }
public static void multiRenderTarget(Renderer renderer) { /* render to FBO, then sample as texture */ }
public static void computeBufferWrite(Renderer renderer) { /* compute writes SSBO, fragment reads it */ }
public static void pushConstantsTest(Renderer renderer) { /* push constant drives color output */ }
public static void primitiveMeshes(Renderer renderer) { /* all primitive types: cube, sphere, cylinder, cone */ }
```

- [ ] **Step 2: Add cross-backend tests for each**

- [ ] **Step 3: Generate reference screenshots**

- [ ] **Step 4: Run full test suite**

Run: `./gradlew test -x :graphics:webgpu:test`

- [ ] **Step 5: Commit**

```bash
git commit -m "feat: comprehensive screenshot test suite for all rendering features"
```

---

### Task 28: Deprecate Old State Commands

**Files:**
- Modify: `graphics/api/src/main/java/dev/engine/graphics/command/RenderCommand.java`
- Modify: `graphics/api/src/main/java/dev/engine/graphics/command/CommandRecorder.java`

- [ ] **Step 1: Add @Deprecated to old state commands**

```java
@Deprecated record SetDepthTest(boolean enabled) implements RenderCommand {}
@Deprecated record SetBlending(boolean enabled) implements RenderCommand {}
@Deprecated record SetCullFace(boolean enabled) implements RenderCommand {}
@Deprecated record SetWireframe(boolean enabled) implements RenderCommand {}
```

And deprecate the corresponding CommandRecorder methods.

- [ ] **Step 2: Migrate examples to use SetRenderState**

Update all examples that use `setDepthTest()`, `setBlending()`, etc. to use `setRenderState()` instead.

- [ ] **Step 3: Verify all tests pass**

- [ ] **Step 4: Commit**

```bash
git commit -m "refactor: deprecate individual state commands in favor of SetRenderState"
```

---

### Task 29: Final Screenshot Test Pass

- [ ] **Step 1: Run full test suite**

```bash
./gradlew test -x :graphics:webgpu:test
```

- [ ] **Step 2: Review all screenshots in build/screenshots/**

Visual inspection of all generated screenshots for correctness.

- [ ] **Step 3: Verify cross-backend similarity**

All cross-backend tests must pass with loose tolerance.

- [ ] **Step 4: Update reference screenshots if needed**

If rendering improved (e.g., better mip filtering), update references.

- [ ] **Step 5: Final commit**

```bash
git commit -m "chore: final screenshot verification pass — all features working on both backends"
```
