# AGENTS.md

Guidance for anyone, human or AI coding agent, working on this repository. (`CLAUDE.md` is a
symlink to this file.)

Technomagy is a NeoForge 1.21.1 mod (Java 21, mod id `technomagy`): a void dimension
filled with procedurally placed, slot-matched pieces. **`docs/design.md` is the source of truth**
for every gameplay and generation decision (numbered D1, D2, ...). Read the relevant sections
before writing code, and follow its implementation phases in order.

## Build

- `./gradlew build` builds the mod jar.
- `./gradlew runClient` / `./gradlew runServer` launch dev instances.
- `./gradlew check` runs every test and check below; `build` includes it.
- `./gradlew runSpellIconChecks` composes every spell icon from the committed glyph files and
  asserts the baked PNGs match, pixel for pixel (it is part of `check`); when the composition
  itself changes, `./gradlew blessSpellIcons` regenerates the committed baked PNGs from that same
  code.
- `./gradlew pureTest` runs pure-Java unit tests (`src/pureTest`, JUnit 5). Minecraft and NeoForge
  are not on its classpath, so `planner` code that touches them fails to compile or load there.
- `./gradlew test` runs unit tests that need Minecraft (`src/test`, JUnit 5), inside a NeoForge
  environment booted by ModDevGradle with this mod loaded.
- `./gradlew integrationTest` starts a dedicated server with the test-only mod
  `technomagy_itest` (`src/integrationTest`), which runs the server checks in
  `itest.ServerChecks` and stops the server; the build verifies the results and scans the logs.
- `./gradlew runGameTestServer` runs GameTests (hosted by the test mod) and exits; not yet part of
  `check` (TODO 1.8 adds it with the first GameTest).
- Every test server run's logs are scanned by `gradle/runtime-log-scan.gradle`: any ERROR/FATAL
  record, linkage error or new crash report fails the build. Its fixtures run with
  `./gradlew testRuntimeLogScanner`.
- `./gradlew spotlessCheck` / `spotlessApply` check and fix formatting. Formatting is enforced by
  Spotless (part of `check`, so `build` fails on violations); run `spotlessApply` before
  committing. The import-block layout below is a custom step in `gradle/import-blocks.gradle`; it
  rejects input it can't rewrite safely (comments between imports, wildcard imports) with an error
  naming the line. Its fixtures in `gradle/import-blocks/` run with `./gradlew testImportBlocks`.

## 1. Packages

Every distinct area of functionality lives in its own package under the base package
`zone.moddev.mc.technomagy`. The mixed-case base package is a deliberate, accepted exception
to Google style; **all packages below it are lowercase**. Never pile unrelated classes into the
base package or a catch-all `util`.

Create a package when the first class for it is written, not ahead of time. If new code fits none
of these, **ask** before inventing a new top-level package. Each package gets a `package-info.java`
(see Documentation).

## 2. Code format

Follow the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html) with these
project overrides:

- **Indentation: 4 spaces for both blocks and continuations.** Wrapped expressions and array
  initializers add one four-space indentation level, not eight spaces. Spotless uses Eclipse JDT
  with the `Technomagy` profile in `gradle/java-formatter.xml`; VS Code uses the same profile.
  Existing explicit line breaks are preserved where the formatter supports them. The 100-column
  limit and the remaining Google conventions (brace style, one statement per line, naming,
  `@Override` always, no wildcard imports, ...) still apply.
- **Imports are split into blocks, one per second-level package, separated by a single blank
  line.** Order:
  1. All static imports, as one block.
  2. `java.*` blocks, then `javax.*` blocks.
  3. Third-party blocks in ASCII order of their second-level prefix (`com.google`, `com.mojang`,
     `net.minecraft`, `net.neoforged`, `org.slf4j`, ...).
  4. This project's own imports (`com.keildraco.*`) last.

  Within a block, imports are ASCII-sorted. Example:

  ```java
  import java.util.List;
  import java.util.Map;

  import com.mojang.logging.LogUtils;
  import com.mojang.serialization.Codec;

  import net.minecraft.core.BlockPos;
  import net.minecraft.world.level.Level;

  import net.neoforged.bus.api.IEventBus;
  import net.neoforged.fml.common.Mod;

  import org.slf4j.Logger;

  import zone.moddev.mc.technomagy;
  ```

- Template-generated files that don't match yet are reformatted when they are next edited.

## 3. Documentation

Code is documented thoroughly, for a reader who knows Java and Minecraft modding but not this
project.

- **`package-info.java`** in every package: what the package is responsible for, what it must not
  depend on, and which `docs/design.md` sections/decisions it implements.
- **Javadoc on every class, interface, record, enum, and every public or protected member.**
  Non-trivial private methods get Javadoc too. Describe what it does, its parameters, return value,
  thrown exceptions, thread-safety, and any invariants or units (blocks vs cells vs levels; internal
  level `L` vs player-facing level number).
- **Reference design decisions by ID** (`D24`, `D37`, ...) wherever code implements one, so the code
  and the design doc can be cross-checked.
- **Truly complex parts get a full explanation**, not just a summary line: BSP splitting, corridor
  routing, edge stitching, deterministic value derivation, cell resolution and local matching,
  rotation math, tier/cache coordination. Explain *why* the approach was chosen, the invariants it
  relies on, edge cases, and where helpful a small worked example or ASCII diagram. Inline comments
  mark the non-obvious steps inside the method.
- Don't write comments that restate the code; explain intent and reasoning.
- Keep docs in sync: when behavior changes, update its Javadoc, `package-info.java`, and (if a
  design point changed) `docs/design.md`.

## 4. Decisions not covered by the design

**Do not guess.** If a decision is not covered by `docs/design.md`, or the doc is ambiguous or
contradicts itself or the code, stop and ask the user before implementing it. Present the options
with a recommendation. This includes:

- gameplay behavior, data-format fields, or file layouts the doc doesn't specify;
- numeric values marked "to be tuned" or listed under "Open questions" (use a documented default if
  the doc gives one; otherwise ask);
- adding dependencies, libraries, or build changes;
- new top-level packages, public APIs, or registry names;
- anything listed under "Proposals" (those are suggestions, not rules).

**No convenience choices.** The code must be robust, and shortcuts taken because they are easier
tend to make it fragile. Never pick an option because it is quicker to write, shorter, or avoids
work, for example:

- swallowing or loosely catching exceptions, or logging and continuing where failure should be
  reported;
- silent fallbacks or default values that hide bad data instead of rejecting it with a clear error;
- skipping validation, bounds checks, or null handling "because it can't happen";
- shared mutable state, unsynchronized caches, or assumptions about thread or load order;
- hard-coded values that the design makes configurable or data-driven;
- stringly-typed data, magic numbers, or copy-pasted logic instead of a proper type or shared code;
- partial implementations, `TODO` stubs, or narrowed scope presented as finished work.

If there is any possible argument over a choice (robustness vs simplicity, strict vs lenient,
fail vs recover, one structure vs another), ask the user instead of deciding. Only choices with
no plausible alternative (such as a local variable's name) go without a question. After the user
decides something that belongs in the design, offer to record it in `docs/design.md` as a new
numbered decision.
