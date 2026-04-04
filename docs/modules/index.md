<!-- AUTO-GENERATED — do not edit, run ./gradlew :tools:site-generator:generateSite -->
---
layout: page
title: "Modules"
description: "Engine module overview — architecture at a glance."
---

## Core

<div class="index-grid">
  <a class="index-card" href="{{ site.baseurl }}/modules/core">
    <span class="feature-icon">⚙️</span>
    <h3>Core</h3>
    <p>Backend-agnostic engine foundation — math, ECS, events, assets, profiler, and resource management.</p>
  </a>
</div>

## Graphics

<div class="index-grid">
  <a class="index-card" href="{{ site.baseurl }}/modules/graphics-api">
    <span class="feature-icon">🎨</span>
    <h3>Graphics API</h3>
    <p>Low-level SPI that all backends implement — RenderDevice, descriptors, command buffers, and pipeline state.</p>
  </a>
  <a class="index-card" href="{{ site.baseurl }}/modules/graphics-common">
    <span class="feature-icon">🖥️</span>
    <h3>Graphics Common</h3>
    <p>High-level rendering engine — Renderer, ShaderManager, material system, mesh handling, and render graph orchestration.</p>
  </a>
</div>

## Graphics Backend

<div class="index-grid">
  <a class="index-card" href="{{ site.baseurl }}/modules/opengl-backend">
    <span class="feature-icon">🟢</span>
    <h3>OpenGL Backend</h3>
    <p>OpenGL 4.5 DSA backend — direct state access, bindless resources, and full pipeline implementation.</p>
  </a>
  <a class="index-card" href="{{ site.baseurl }}/modules/vulkan-backend">
    <span class="feature-icon">🔴</span>
    <h3>Vulkan Backend</h3>
    <p>Vulkan backend with explicit memory management, command pools, and SPIR-V shader pipeline.</p>
  </a>
  <a class="index-card" href="{{ site.baseurl }}/modules/webgpu-backend">
    <span class="feature-icon">🌐</span>
    <h3>WebGPU Backend</h3>
    <p>WebGPU backend for browser and native — WGSL shaders, modern GPU API, runs on desktop and web.</p>
  </a>
</div>

## Platform

<div class="index-grid">
  <a class="index-card" href="{{ site.baseurl }}/modules/desktop-platform">
    <span class="feature-icon">💻</span>
    <h3>Desktop Platform</h3>
    <p>Opinionated desktop assembly — filesystem asset sources, native Slang, LWJGL windowing, JVM deployment.</p>
  </a>
  <a class="index-card" href="{{ site.baseurl }}/modules/web-platform">
    <span class="feature-icon">🌐</span>
    <h3>Web Platform</h3>
    <p>Opinionated web assembly — fetch-based asset sources, Slang WASM, TeaVM compilation, browser deployment.</p>
  </a>
</div>

## Provider

<div class="index-grid">
  <a class="index-card" href="{{ site.baseurl }}/modules/assimp-asset-loader">
    <span class="feature-icon">📦</span>
    <h3>Assimp Asset Loader</h3>
    <p>Asset loading via Assimp — imports 40+ 3D model formats (OBJ, FBX, glTF, etc.) through Java FFM bindings.</p>
  </a>
  <a class="index-card" href="{{ site.baseurl }}/modules/glfw-windowing">
    <span class="feature-icon">🪟</span>
    <h3>GLFW Windowing</h3>
    <p>GLFW windowing provider via LWJGL — window creation, input handling, and OpenGL/Vulkan context management.</p>
  </a>
  <a class="index-card" href="{{ site.baseurl }}/modules/lwjgl-opengl-provider">
    <span class="feature-icon">🔌</span>
    <h3>LWJGL OpenGL Provider</h3>
    <p>LWJGL bindings for the OpenGL backend — native OpenGL 4.5 access on desktop platforms.</p>
  </a>
  <a class="index-card" href="{{ site.baseurl }}/modules/lwjgl-vulkan-provider">
    <span class="feature-icon">🔌</span>
    <h3>LWJGL Vulkan Provider</h3>
    <p>LWJGL bindings for the Vulkan backend — native Vulkan access on desktop platforms.</p>
  </a>
  <a class="index-card" href="{{ site.baseurl }}/modules/sdl3-windowing">
    <span class="feature-icon">🪟</span>
    <h3>SDL3 Windowing</h3>
    <p>SDL3 windowing provider — cross-platform window creation and input via Java FFM bindings.</p>
  </a>
  <a class="index-card" href="{{ site.baseurl }}/modules/slang-shader-compiler">
    <span class="feature-icon">🔮</span>
    <h3>Slang Shader Compiler</h3>
    <p>Slang shader compilation provider — write shaders once, compile to GLSL, SPIR-V, or WGSL per backend.</p>
  </a>
  <a class="index-card" href="{{ site.baseurl }}/modules/jwebgpu-provider">
    <span class="feature-icon">🔌</span>
    <h3>jWebGPU Provider</h3>
    <p>Native WebGPU bindings for desktop — uses Dawn/wgpu for WebGPU on JVM without a browser.</p>
  </a>
</div>

