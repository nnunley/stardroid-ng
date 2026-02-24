# Stardroid Awakening

A modern Android planetarium built by AI agents from reverse-engineered specifications of Google Sky Map.

## Table of Contents

- [What Is This?](#what-is-this)
- [The Story](#the-story)
- [Quick Start](#quick-start)
- [The Development Process](#the-development-process)
- [Architecture](#architecture)
- [Current State](#current-state)
- [Project Structure](#project-structure)
- [License](#license)

## What Is This?

Stardroid Awakening is a ground-up rewrite of [Google Sky Map](https://github.com/sky-map-team/stardroid) (originally released by Google in 2009, open-sourced in 2012). The original app was built with OpenGL and a now-ancient Android SDK. This rewrite targets modern Android with Vulkan rendering, Kotlin, and Material Design 3.

Point your phone at the sky and see what's there -- 3,200+ stars, 88 constellations, and deep-sky objects rendered in real-time using device sensor fusion. But the interesting part isn't what the app does. It's how it was built.

## The Story

The original stardroid codebase -- Java, OpenGL ES, legacy Android APIs -- was fed to a large language model. The LLM analyzed the source and produced 45 detailed specification documents covering everything from astronomical coordinate algorithms to sensor fusion to rendering architecture to UI design. These specs were then curated and refined with an LLM in the loop, turning raw analysis into actionable build plans.

From those specs, the entire implementation was built using the "Ralph Loop" -- an iterative AI development methodology. A file called `PROMPT.md` served as the instruction set: it told the AI agent to read the specs, consult `status.md` for progress, pick the next highest-priority task, implement it, commit the work, update the status file, and exit. The human observed from the outside, stopping and amending the prompt each time the agents ran into difficulties.

The result: a working Android planetarium rendering 3,200+ stars and 781 constellation line segments at 60 FPS via Vulkan, with real-time sensor-based sky tracking -- built through autonomous AI development cycles with human steering at key junctures.

## Quick Start

**Prerequisites:**
- Android SDK (compileSdk 35, minSdk 26)
- Java 23 (Java 25 has AGP compatibility issues)
- Android device with Vulkan 1.1+ support

**Build and install:**

```bash
# Build debug APK
./gradlew assembleDebug

# Install to connected device
./gradlew installDebug

# View logs
adb logcat -s StardroidAwakening
```

Output APK: `app/build/outputs/apk/debug/app-debug.apk` (~9 MB)

## The Development Process

### Spec Generation

The original [stardroid](https://github.com/sky-map-team/stardroid) repository was analyzed by an LLM to produce 45 specification documents organized by domain:

| Domain | Specs | Covers |
|--------|-------|--------|
| `specs/core/` | Data models, algorithms, search | Celestial coordinates, position calculations, catalog indexing |
| `specs/features/` | Layers, search, time-travel, settings | 12 display layers, object search, historical sky views |
| `specs/sensors/` | Orientation, coordinate transforms | Sensor fusion, phone-to-sky mapping, manual control |
| `specs/ui/` | Material 3, dialogs, activities | Modern Android UI patterns |
| `specs/rendering/` | Rendering abstraction | Backend-agnostic renderer contract |
| `specs/bootstrap/` | Vulkan demo | Step-by-step Vulkan bring-up plan |

### Spec Curation

The raw LLM-generated specs were refined with an LLM in the loop -- iterating on structure, filling gaps, resolving contradictions, and ensuring each spec was concrete enough to implement from.

### The Ralph Loop

Development used the Ralph Loop (the "Ralph Wiggum technique") -- an iterative methodology where an AI agent autonomously implements one task per session:

1. Agent reads `PROMPT.md` (instructions), `specs/` (requirements), and `status.md` (progress)
2. Agent selects the highest-priority unblocked task
3. Agent implements, tests, and commits the work
4. Agent updates `status.md` and exits
5. Human reviews, amends `PROMPT.md` if needed, and re-launches

The human's role was supervisory: observing output, intervening when agents got stuck, and adjusting the prompt to steer past difficulties. The agents handled implementation, testing, and commit hygiene autonomously.

### Progression

The loop progressed through these phases:

1. **Bootstrap** (Phases 1-9): Project structure, Vulkan initialization, swapchain, render pass, graphics pipeline, shaders, vertex buffers, animation, device verification
2. **Rendering Abstraction** (Phases 1-3): Backend-agnostic renderer interface, dynamic vertex buffers, multiple primitive types
3. **Star Data**: Protocol Buffers catalog loading, RA/Dec to xyz conversion, magnitude-based rendering
4. **Constellation Lines**: Line segment catalog, celestial coordinate rendering
5. **Sensor Integration**: Rotation vector sensor, accelerometer/magnetometer fallback, magnetic declination correction, Local Sidereal Time
6. **Layer System**: 12 toggleable layers, AR camera, spatial indexing, UI overlays

## Architecture

### Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin 2.1.0 |
| Graphics | Vulkan 1.1+ (C++17 via JNI) |
| Build | Gradle Kotlin DSL, CMake 3.22.1 |
| Data | Protocol Buffers (star catalogs from original stardroid) |
| UI | Material Design 3 |
| Sensors | Android Rotation Vector + accelerometer/magnetometer fallback |
| Spatial | HEALPix (Hierarchical Equal Area isoLatitude Pixelization) |
| Testing | Google Test (C++), JUnit (Kotlin) |

### Design

The architecture uses a hexagonal (ports and adapters) pattern:

- **Domain layer** speaks in astronomical concepts (RA/Dec, magnitudes, celestial coordinates) and knows nothing about Vulkan
- **`RendererInterface`** defines the boundary -- `beginFrame()`, `draw(DrawBatch)`, `endFrame()`
- **Vulkan backend** implements the interface in C++ via JNI
- **Layer system** composes 12 independent layers (stars, constellations, grid, horizon, AR camera, etc.) into the final view
- **Sensor fusion** transforms device orientation into a celestial view matrix using rotation vector sensors with magnetic declination correction
- **HEALPix spatial indexing** culls off-screen stars to reduce vertex buffer usage

## Current State

### Working

- Vulkan rendering pipeline (60+ FPS on Mali-G715)
- 3,200+ stars from Hipparcos catalog
- 781 constellation line segments (88 constellations)
- Real-time device orientation tracking
- 12-layer system with independent visibility toggles
- AR camera overlay
- HEALPix spatial indexing for efficient star culling
- UI overlays: FPS counter, compass, layer toggle
- C++ unit tests (8 gtest tests for math utilities)

### Planned

- Planet position calculations (ephemeris)
- Search with autocomplete
- Time-travel mode (view sky at any date)
- Touch gestures (tap, pinch, drag)
- Night mode (red-tinted display)
- Messier object rendering
- Multi-device testing and sensor tuning

## Project Structure

```
stardroidawakening/
  app/                  # Main Android application
    src/main/
      kotlin/           # Kotlin source (domain, layers, sensors, UI, Vulkan bindings)
      cpp/              # C++ Vulkan implementation + JNI bridge
  datamodel/            # Astronomy data models (Protocol Buffers, FlatBuffers schemas)
  tools/                # Build tools and binary star catalog data
    data/               # stars.binary, constellations.binary, messier.binary
  specs/                # 45 LLM-generated specification documents
  docs/plans/           # Design documents and development plans
  PROMPT.md             # Ralph Loop instruction set for AI agents
  status.md             # Development progress tracker
  AGENTS.md             # Build/test/deploy instructions for agents
```

## License

Apache 2.0. See [LICENSE](LICENSE).

Based on [Google Sky Map / Stardroid](https://github.com/sky-map-team/stardroid), originally released by Google in 2009 and open-sourced in 2012.
