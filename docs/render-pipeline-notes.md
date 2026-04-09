# Render Pipeline Design Notes

Collected during brainstorming session 2026-04-09. Not a spec — just decisions and open questions so far.

## Direction

Frame graph with setup/execute split, hybrid with scoped contexts inside passes for dynamic work (shadow cascades, per-light loops, cubemap faces).

## Desired User API

```java
graph.addPass((setup, exec) -> {
    var shadowAtlas = setup.createTexture(DEPTH32F, 4096, 4096);
    setup.writes(shadowAtlas);
    
    exec.run(ctx -> {
        for (var light : lights) {
            try (var scope = ctx.scope()) {
                scope.setCamera(light.shadowCamera());
                scope.setViewport(light.atlasRegion());
                scope.drawScene(scene, shadowCasters);
            }
        }
    });
});

graph.addPass((setup, exec) -> {
    var color = setup.createTexture(RGBA16F, width, height);
    var depth = setup.createTexture(DEPTH24, width, height);
    setup.writes(color, depth);
    
    exec.run(ctx -> {
        ctx.setCamera(playerCamera);
        ctx.drawScene(scene, SortOrder.FRONT_TO_BACK);
    });
});

graph.addPass((setup, exec) -> {
    var input = setup.reads(color);
    var output = setup.createTexture(RGBA8, width, height);
    setup.writes(output);
    
    exec.run(ctx -> {
        ctx.fullscreenQuad(tonemapShader, input);
    });
});

renderer.execute(graph);
```

## Decided

- **Setup vs Execute split** — passes declare resource needs in setup, record commands in execute. Framework handles barriers and resource lifetimes between those phases.
- **Hybrid scoped contexts inside passes** — graph manages top-level pass ordering and resources. Inside a pass, scoped contexts (like vibecity's RenderContext branching) handle dynamic work like per-light shadow rendering.
- **Camera is a per-scope value, not a component** — injected into scope via `ctx.setCamera()`. Different scopes use different cameras (player, light, minimap).
- **Passes can do dynamic internal work** — shadow pass loops over lights, creates sub-scopes. The graph doesn't need to know light count.
- **Replaces current monolithic Renderer.renderFrame()**.

## Open Questions

- **Pass identity** — magic string, typed PassKey, anonymous with debug name, or handle returned from addPass? Needs to be decided. Strings are fragile, but typed keys might be overengineered.
- **Barrier insertion** — GL has no barriers, WebGPU has implicit barriers, VK needs explicit ones. Options: (A) compile barriers per backend, (B) track state everywhere but only emit on VK, (C) ignore for now. Multi-backend complicates this.
- **Resource aliasing** — frame graph enables same GPU memory for non-overlapping resources. Worth implementing or just use pooling like vibecity?
- **Dead pass elimination** — if a pass output is unused, skip it automatically? Or leave it to the user via enable flags?
- **Pass ordering** — fully automatic from dependency edges, or user-declared order with dependency validation?
- **Scene filtering** — how does a pass declare which entities to draw? Layer masks, component queries, callbacks?
- **Material overrides** — shadow pass needs depth-only shader regardless of entity material. How is this expressed?
- **Camera-as-Component** — still useful for attaching cameras to entities in the scene graph (follow car, head bone). But the render pipeline consumes camera data, not the component directly. Need both?

## Reference Implementations

- **Vibecity (own codebase)** — `/media/mzuegg/Vault/Projects/Games/vibecity/common/jme/rendering/src/main/java/com/vibecity/jme/rendering/scope/`. Scoped pipeline with RenderContext, ResourceKey, Pass interface. Proven, portable, simpler than frame graph.
- **Frostbite Frame Graph** — GDC 2017 talk. Setup/execute split, automatic barriers, resource aliasing, dead pass elimination. Gold standard but complex.
- **Unreal RDG** — similar to Frostbite. Passes add sub-passes during setup. Lambda-based.
- **Unity SRP** — ScriptableRenderPass list, manual ordering via renderPassEvent. Simpler but no automatic optimization.
- **Bevy** — ECS render graph with typed node slots. Camera is an entity extracted into render world.
