# Input System

## Architecture

The input system is event-driven with a sealed record hierarchy. Events flow from platform providers through a thread-safe queue to the game loop.

### Event Hierarchy

```
InputEvent { Time time() }
└── DeviceEvent { DeviceId device() }
    ├── BooleanEvent { BooleanSource source(), boolean pressed() }
    │   ├── KeyEvent [HasModifiers] { KeyCode, ScanCode }
    │   ├── MouseButtonEvent [HasModifiers] { MouseButton, x, y }
    │   └── GamepadButtonEvent { GamepadButton }
    ├── AxisEvent [HasModifiers] { AxisSource source(), x, y }
    │   ├── CursorMoved, Scrolled, GamepadAxisMoved
    ├── CharTyped { Codepoint }
    └── DeviceConnectionChanged { boolean connected() }

WindowEvent { Time time() }
├── Resized, FocusGained, FocusLost, Closed, Moved
```

### Source Types

Input sources implement `BooleanSource` or `AxisSource`:
- `KeyCode` enum — keyboard keys
- `MouseButton` enum — mouse buttons
- `MouseAxis` enum — CURSOR, SCROLL
- `GamepadButton` / `GamepadAxis` enums — gamepad inputs
- Open for extension: third-party `BooleanSource`/`AxisSource` implementations

### Device System

Typed device interfaces prevent cross-device property misuse:
- `MouseDevice` — `setCursorMode()`, `cursorModeRef()`
- `KeyboardDevice` — (extensible)
- `GamepadDevice` — `setDeadzone(axis, value)`
- All extend `InputDevice` — `name()`, `connected()`

### Usage

```java
// In BaseApplication subclass
@Override
protected void update(float dt, List<InputEvent> events) {
    for (var event : events) {
        switch (event) {
            case InputEvent.KeyPressed e when e.keyCode() == KeyCode.E -> openInventory();
            case InputEvent.Scrolled e when e.modifiers().ctrl() -> camera.zoom(e.y());
            case MouseButtonEvent e when e.pressed() -> handleClick(e.x(), e.y());
            default -> {}
        }
    }
}

// Device properties
inputSystem().mouse(0).setCursorMode(CursorMode.LOCKED);
```

## Gotchas

### PropertyKey Scoping

`PropertyKey<O, T>` has an owner type parameter. `PropertyMap<O>` only accepts keys with matching owner. This prevents accidentally setting a light property on a material.

However, the owner type is **erased at runtime** — two keys with the same name/type but different owners will be `.equals()`. The scoping is compile-time only.

### MaterialData and RenderState

MaterialData properties are `PropertyKey<MaterialData, T>`. RenderState properties are `PropertyKey<RenderState, T>`. To set render state overrides on a material, use `MaterialData.withRenderState()` which stores them in a nested `PropertyMap<RenderState>`:

```java
MaterialData.unlit(color).withRenderState(RenderState.CULL_MODE, CullMode.NONE);
```

Do NOT use `.set(RenderState.CULL_MODE, ...)` — it won't compile.

### GLFW Modifier Bits

GLFW and the engine use the same bit layout for modifiers: 0x01=shift, 0x02=ctrl, 0x04=alt, 0x08=super. Direct passthrough works.

### CursorMoved/Scrolled Modifiers

Cursor and scroll events carry modifiers, but GLFW doesn't provide them in those callbacks. The provider polls the current key state to reconstruct modifiers for these events.

### Versioned<T> for Window Properties

`WindowHandle.sizeRef()` and `focusedRef()` return `Reference<T>` that tracks changes via version numbers. Call `ref.update()` each frame — returns true only when the value changed. This avoids polling `width()`/`height()` every frame.
