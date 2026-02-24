# README Design

## Date: 2026-02-24

## Goal

Create a README.md that serves both as a standard project README and as a showcase of the AI-driven development process that produced the codebase.

## Audience

Both developers (who want to build/contribute) and anyone curious about the AI development story.

## Central Theme

The AI-driven development process is the central narrative. The app is the artifact; the process is the story.

## Structure

### 1. Title + Tagline
- "Stardroid Awakening"
- One-line description emphasizing AI-built origin

### 2. Table of Contents
- Links to all major sections

### 3. What Is This?
- 3-4 sentences: modern rewrite of Google Sky Map, Vulkan + Kotlin + Material 3
- Mention original: 2009 release, 2012 open-source, OpenGL + legacy Android SDK
- Hook: "the interesting part is how it was built"

### 4. The Story (~3 paragraphs)
- P1: Original stardroid (OpenGL, ancient Android SDK) analyzed by LLM -> 45 spec documents covering algorithms, sensors, rendering, UI, features
- P2: Specs curated and refined with LLM in the loop
- P3: Ralph Loop methodology - PROMPT.md instructs agent to read specs, pick task, implement, commit, exit. Human observes, intervenes on difficulties. Result: working planetarium app.

### 5. Quick Start
- Prerequisites: Android SDK, Java 23, Vulkan 1.1+ device
- Build: `./gradlew assembleDebug`
- Install: `./gradlew installDebug` or `adb install`
- Logs: `adb logcat -s StardroidAwakening`

### 6. The Development Process (detailed)
- Spec Generation: how the 45 specs were produced from original codebase
- Spec Curation: human-in-the-loop refinement
- The Ralph Loop: iterative autonomous development methodology
- PROMPT.md: the instruction set driving each loop iteration
- status.md: progress tracking across iterations

### 7. Architecture
- Tech stack table
- High-level design: rendering abstraction (hexagonal), layer system, sensor fusion, spatial indexing (HEALPix)
- C++/Kotlin split: Vulkan in C++, domain in Kotlin

### 8. Current State
- Working: stars (3200+), constellations (781 segments), sensor tracking, Vulkan @ 60 FPS, UI overlays, layer system, AR camera, spatial indexing
- Planned: search, time-travel, gestures, planet positions, night mode, multi-device testing

### 9. Project Structure
- Directory tree: app/, datamodel/, tools/, specs/, docs/
- Brief description of each module

### 10. License
- Apache 2.0
- Attribution to original stardroid project

## Tone

Standard open-source project README. Factual, not hype. Let the process speak for itself.

## Key Facts to Get Right

- Original stardroid used OpenGL and legacy Android SDK (not Vulkan)
- The rewrite targets Vulkan as a deliberate modernization choice
- 45 spec documents were LLM-generated from the original codebase
- Specs were curated/refined with LLM in the loop
- Ralph Loop: human observed from outside, amended PROMPT.md when agents had difficulties
- Not fully autonomous - human steering was required at key junctures
