---
layout: page
title: "Module Threading Model"
description: "Per-platform executor contract for the ModuleManager — parallel on desktop, sequential on web."
---

# Module Threading Model

The `ModuleManager` runs same-level modules (those in the same dependency
level of the dependency graph) through a `java.util.concurrent.Executor`.
The executor is supplied by the active `Platform`, which means the actual
execution semantics differ between deployment targets.

## Contract

| Platform            | Executor                                         | Semantics                          |
|---------------------|--------------------------------------------------|------------------------------------|
| `DesktopPlatform`   | `runnable -> Thread.ofVirtual().start(runnable)` | Parallel — one virtual thread per same-level module per tick |
| `WebPlatform`       | default — `Runnable::run`                        | Sequential on the caller thread    |
| `GraalWasmPlatform` | default — `Runnable::run`                        | Sequential on the caller thread    |
| `HeadlessPlatform`  | default — `Runnable::run`                        | Sequential on the caller thread    |

The default on `Platform` itself is `Runnable::run`, so any new platform
that doesn't override `moduleExecutor()` gets the safe sequential behavior
automatically. Virtual threads are only referenced from within the desktop
platform module, so TeaVM and GraalWasm compilation never see the
`Thread.ofVirtual()` call — the build-graph isolation is what keeps the
web builds compilable.

## Implications for module authors

Because desktop is the primary development target and it runs same-level
modules concurrently, **modules at the same dependency level must not
mutate shared state without synchronization**. The `ModuleManager`
guarantees:

- All modules in level N complete before any module in level N+1 starts
  (via a `CountDownLatch` inside `updateAll`).
- Modules in different levels never run concurrently with each other.
- A dependent module may safely read state computed by its declared
  dependencies in the same tick.

It does **not** guarantee:

- Any particular order between peers at the same level.
- Visibility of writes between peers at the same level (they may race).
- That `Runnable::run` semantics will be preserved on desktop — if you
  write a module assuming sequential same-level execution, it may work
  on web and break on desktop.

If you need two modules to coordinate their writes, declare a dependency
between them so they land in different levels.

## Why the split

The `Platform` interface is the project's existing seam for differences
between deployment targets. Putting the executor choice behind it keeps
the threading implementation physically isolated to
`platforms/desktop/`, which is not on the compile classpath of the
TeaVM or GraalWasm builds. The result:

- Desktop builds get real parallelism.
- Web builds compile cleanly, without runtime feature detection or
  reflective fallback.
- Any future platform (mobile, embedded) can make its own choice just
  by overriding `moduleExecutor()`.

## Verifying the contract holds

The screenshot test
`dev.engine.tests.screenshot.scenes.module.ModuleSystemScenes#PARALLEL_MODULES_OPERATIONAL`
exercises the full module system end-to-end on every backend: desktop
OpenGL, desktop Vulkan, TeaVM/WebGPU, and GraalWasm/WebGPU. It uses
three level-0 modules and one level-1 aggregator, and the reference
image matches if and only if the dependency graph, lifecycle, and
cross-module lookup all work. The test passes identically regardless
of whether the executor is parallel or sequential, because the scene's
state is constant and each module writes only to its own entity.

## Unit-test coverage

Low-level threading behavior of `ModuleManager` is covered by
`core/src/test/java/dev/engine/core/module/ModuleManagerTest.java`,
which runs a real `Executors.newFixedThreadPool(3)` and verifies that
three independent modules actually execute concurrently — see the
nested `ParallelExecutionTests` and `SharedExecutorTests` classes.

## Empirical probe: what happens if web backends use a real executor?

On 2026-04-10 we probed what actually happens when a non-default
executor is plugged into `Platform.moduleExecutor()` on TeaVM and
GraalWasm. The probes were run against the inline anonymous `Platform`
in `WebTestApp.java:86` and `GraalWasmTestApp.java:131` (NOT against
`WebPlatform`/`GraalWasmPlatform`, which are unused — the screenshot
test apps deliberately avoid those modules to keep their build graph
minimal). Any earlier note claiming that placing the override in
`WebPlatform` would exercise the test code path was wrong: the class
was never instantiated at runtime, so TeaVM's dead-code elimination
stripped it entirely. The correct probe lives in the inline platform.

### Probe 1: `Thread.ofVirtual().start(r)`

```java
@Override
public Executor moduleExecutor() {
    return runnable -> Thread.ofVirtual().start(runnable);
}
```

| Backend | Result |
|---|---|
| **TeaVM → JS** | ❌ **hard compile-time failure**: `Method java.lang.Thread.ofVirtual()Ljava/lang/Thread$Builder$OfVirtual; was not found` at `ModuleManager.updateAll:235`. TeaVM's classlib simply doesn't ship `Thread.ofVirtual()` or `Thread.Builder`. |
| **GraalWasm → WASM** | ⚠️ **compiles cleanly, crashes at runtime**: native-image's JDK classlib *does* contain `Thread.ofVirtual()`, so `wasmCompile` succeeds (12.5 s, 9.69 MB binary). At runtime on the first executor.execute call, it throws `java.lang.UnsupportedOperationException: VirtualThread.runContinuation`. Virtual threads depend on JDK-internal stack-switching primitives (`Continuation`) that svm-wasm doesn't implement because WASM MVP has no stack-switching opcodes. Notable: only scenes that take `ModuleManager.updateAll`'s parallel code path fail — single-module scenes still pass, because they bypass `executor.execute` via the "direct" branch. |

### Probe 2: `ForkJoinPool.commonPool()`

```java
@Override
public Executor moduleExecutor() {
    return ForkJoinPool.commonPool();
}
```

| Backend | Result |
|---|---|
| **TeaVM → JS** | ❌ **hard compile-time failure**: `Class java.util.concurrent.ForkJoinPool was not found` at `WebTestApp$1.moduleExecutor`. TeaVM's classlib ships very little of `java.util.concurrent` — `ForkJoinPool` is not in it. |
| **GraalWasm → WASM** | ✅ **compiles AND runs cleanly**. Full class tree present in the compiled WAT — `commonPool`, `execute`, `WorkQueue`, `DefaultForkJoinWorkerThreadFactory.create`, `managedBlock`, `parallelism`, `queues`, etc. All 36 scenes in `runGraalWasm` pass, rendering pixel-identical to the default executor. |

### How GraalWasm *actually* executes `ForkJoinPool.execute()`

An instrumented probe was added that captured, for a single
`executor.execute(r)` invocation, (a) the current thread name when
submitting, (b) the current thread name from *inside* the submitted
Runnable, and (c) whether the Runnable had completed before `execute()`
returned to its caller. The probe then force-threw a `RuntimeException`
so the observation flowed through the tick-error pipeline to the manifest:

```
fjp-observation: schedThread=main runThread=main syncExecute=true delta_ns=100000
```

- **`schedThread=main`** and **`runThread=main`** — the submitter and the
  Runnable body both run on the same thread named "main". svm-wasm has
  no worker threads at runtime.
- **`syncExecute=true`** — the Runnable completed *before* `execute()`
  returned. This is inline, synchronous execution, not async dispatch.
- **`delta_ns=100000`** — roughly 100 µs between the "schedule" and
  "run" timestamps, which is just the overhead of the inner lambda
  setup; there's no scheduling latency because there's no scheduler.

### Why `ForkJoinPool` silently degrades to synchronous execution

GraalVM's Substrate VM ships a large set of `*Feature_ServiceRegistration`
classes specifically for single-threaded targets like svm-wasm. All of
these are present in the compiled WAT binary:

| Feature class | What it does |
|---|---|
| `SingleThreadedMonitorSupport` | `synchronized` blocks compile to no-ops — ForkJoinPool's internal locks never actually block |
| `SingleThreadedVMLockSupportFeature` | VM-level locks are no-ops |
| `WebImageMonitorFeature` | Web-specific monitor substitution |
| `WebImageJSJavaThreadsFeature` | Java thread management substituted — no real OS threads can be spawned |
| `WebImageWasmLMVMThreadSTFeature` | VMThread substitution ("**ST**" = single-threaded) |

When `ForkJoinPool` tries to hand a task to a worker thread, the
substituted thread-creation path never actually creates a worker, and
the submission code path ends up running the task synchronously on the
current thread. The pool is an elaborately-compiled no-op facade.

**Static verification:** the WAT file uses method pointer slot `0x86`
for 338 distinct methods including `ForkJoinPool.execute`, my lambda's
`execute`, `InputStreamReader.read`, `String.isEmpty`, etc. — a strong
deduplication pattern that confirms many "unused" method bodies are
being consolidated onto a shared handler or substitution.

**Performance:** `parallel_modules_operational` completes in ~740 ms on
GraalWasm with `ForkJoinPool.commonPool()`, which is the same as with
the `Runnable::run` default. There's no parallelism speedup because
there's no parallelism. The only "benefit" is that code written to use
`java.util.concurrent` still runs without crashing — at the cost of a
larger WASM binary and the potential for misleading assumptions about
concurrency semantics.

### Summary

| Executor | TeaVM | GraalWasm |
|---|---|---|
| `Runnable::run` (default) | ✅ baseline | ✅ baseline |
| `Thread.ofVirtual().start(r)` | ❌ compile error (classlib missing) | ❌ runtime `UnsupportedOperationException` |
| `ForkJoinPool.commonPool()` | ❌ compile error (classlib missing) | ✅ works (effectively synchronous) |

### What this means for the Platform split

The `Platform.moduleExecutor()` split (desktop virtual threads, web
backends inherit the `Runnable::run` default) is **necessary, not just
stylistic**. Both probes above would fail any test suite if put into
the default path:

- **TeaVM** rejects `java.util.concurrent` primitives at compile time.
  Its classlib is fundamentally too small to host any real executor
  implementation. The only safe choice is `Runnable::run` or a
  TeaVM-specific `Platform.startThread` wrapper.
- **GraalWasm** is more permissive — it compiles most of
  `java.util.concurrent`, so some executors work. But `Thread.ofVirtual()`
  crashes at runtime because virtual threads require `Continuation`
  which svm-wasm doesn't implement, and even the executors that work
  provide no real parallelism (single-threaded WASM).

Keeping the default at `Runnable::run` on both web platforms is the
only choice that works across both backends without surprises. Desktop
is the only target where a real threaded executor provides measurable
benefit.

### Diagnostic infrastructure for future probes

Getting the real exception out of GraalWasm required a small patch to
`GraalWasmTestApp.exportTickFrame` (now catches `Throwable` and uses a
new `exposeTickError` JS bridge) plus `test.html` to include
`window._tickError` in the reported error message. This change was
kept after the probe — any future tick-level exception on GraalWasm
now surfaces the real class name and message in
`build/screenshots/screenshot-report.json`, instead of the opaque
`"Tick error at frame N"` string that was there before.

## Unit-test coverage

Low-level threading behavior of `ModuleManager` is covered by
`core/src/test/java/dev/engine/core/module/ModuleManagerTest.java`,
which runs a real `Executors.newFixedThreadPool(3)` and verifies that
three independent modules actually execute concurrently — see the
nested `ParallelExecutionTests` and `SharedExecutorTests` classes.
