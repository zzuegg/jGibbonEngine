package dev.engine.tests.screenshot.scenes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SceneRegistryTest {

    @Test
    void discoversScenes() {
        var registry = new SceneRegistry();
        assertFalse(registry.scenes().isEmpty(), "Should discover at least one scene");
    }

    @Test
    void sceneNamesAreLowercase() {
        var registry = new SceneRegistry();
        for (var scene : registry.scenes()) {
            assertEquals(scene.name(), scene.name().toLowerCase(),
                    "Scene name should be lowercase: " + scene.name());
        }
    }

    @Test
    void scenesHaveCategories() {
        var registry = new SceneRegistry();
        for (var scene : registry.scenes()) {
            assertNotNull(scene.category(), "Scene should have category: " + scene.name());
            assertFalse(scene.category().isEmpty());
        }
    }

    @Test
    void discoversKnownScene() {
        var registry = new SceneRegistry();
        var found = registry.scenes().stream()
                .anyMatch(s -> s.name().equals("depth_test_cubes"));
        assertTrue(found, "Should discover DEPTH_TEST_CUBES from BasicScenes");
    }

    @Test
    void discoversStencilScene() {
        var registry = new SceneRegistry();
        var found = registry.scenes().stream()
                .anyMatch(s -> s.name().equals("stencil_masking"));
        assertTrue(found, "Should discover STENCIL_MASKING from StencilScenes");
    }

    @Test
    void discoveredScenesHaveClassAndFieldInfo() {
        var registry = new SceneRegistry();
        for (var scene : registry.scenes()) {
            assertNotNull(scene.className(), "Scene should have className: " + scene.name());
            assertNotNull(scene.fieldName(), "Scene should have fieldName: " + scene.name());
            assertFalse(scene.className().isEmpty());
            assertFalse(scene.fieldName().isEmpty());
        }
    }
}
