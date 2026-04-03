# Screenshot Fixes + Texture Extensions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix report badge generation, ensure all screenshot tests produce meaningful comparable images, and add 3D/array texture and compute image support.

**Architecture:** Fix report generator's test-result matching, audit all screenshot scenes for visual correctness, extend TextureDescriptor with dimension/layer support, add image load/store commands for compute.

**Tech Stack:** Java 25, LWJGL (OpenGL 4.5, Vulkan 1.3), JUnit 5

---

## Part A: Report Fixes + Screenshot Quality

### Task 1: Fix report badge matching

The report generator parses JUnit XML and matches test methods to scenes via camelToSnake conversion. The issue is that `forkEvery = 1` creates separate JUnit XML files per test CLASS, and the class names contain the scene references. Debug by checking the actual XML filenames and content.

**Files:**
- Modify: `examples/src/test/java/dev/engine/examples/ScreenshotReportGenerator.java`

- [ ] **Step 1:** Run tests and check XML output to understand the matching issue
```bash
./gradlew :examples:test --rerun
ls examples/build/test-results/test/
```

- [ ] **Step 2:** Fix the badge matching — ensure camelToSnake handles edge cases like "Pbr" → "pbr" (not "p_b_r") and multi-word names

- [ ] **Step 3:** Verify report shows badges for ALL scenes including material_texture and texture_switching

- [ ] **Step 4:** Commit

### Task 2: Audit all screenshot scenes produce meaningful images

Every screenshot test must produce a clearly visible, non-black image. Check each one:

- [ ] **Step 1:** View all 18 OpenGL and 18 Vulkan screenshots, flag any that are blank/black/invisible
- [ ] **Step 2:** Fix any broken scenes
- [ ] **Step 3:** Regenerate report and verify
- [ ] **Step 4:** Commit

---

## Part B: 3D/Array Textures + Compute Image Support

### Task 3: Extend TextureDescriptor for 3D and array textures

**Files:**
- Modify: `graphics/api/src/main/java/dev/engine/graphics/texture/TextureDescriptor.java`
- Create: `graphics/api/src/main/java/dev/engine/graphics/texture/TextureType.java`

Add texture type (2D, 3D, 2D_ARRAY, CUBE) and depth/layer dimensions.

### Task 4: Add image load/store for compute shaders

**Files:**
- Modify: `graphics/api/src/main/java/dev/engine/graphics/command/RenderCommand.java`
- Modify: `graphics/api/src/main/java/dev/engine/graphics/command/CommandRecorder.java`

Add `BindImage` command for compute shader image access (imageLoad/imageStore).

### Task 5: Implement 3D textures in OpenGL backend

### Task 6: Implement 3D textures in Vulkan backend

### Task 7: Implement image load/store in both backends

### Task 8: Screenshot tests for 3D textures and compute images
