# Create 1.21.1 Yunniverse Performance Walkthrough

This file records evidence, design changes, validation, and deployment findings for the
Yunniverse Create performance branch. It intentionally documents auditable engineering
reasoning and results rather than private chain-of-thought.

## 2026-08-01T21:51:16+08:00 — Reduce train tick allocation pressure

- Static review found avoidable temporary allocation in frequently executed train state paths.
- Reused stable data where ownership and invalidation semantics permit, reducing garbage
  generation without changing train movement rules.
- Source commit: `841187d Reduce train tick allocation pressure`.

## 2026-08-10T19:39:17+08:00 — Coalesce train-door collider rebuilds

- Every sliding-door block update could immediately rebuild the entire carriage collision shape.
  Large trains and two trains opening at the same station therefore multiplied an expensive
  full-contraption operation within one client update burst.
- Added lazy dirty tracking so door packets mark the collider stale and the next consumer performs
  one rebuild for the whole burst.
- This targets the reported client freeze during large bidirectional arrivals while preserving the
  final collision shape.
- `compileJava` passed with only existing warnings.
- Source commit: `1cf7219 Coalesce train door collider rebuilds`.

## 2026-08-10T19:41:12+08:00 — Remove redundant schedule NBT deep copies

- JFR showed the five-minute server warning inside
  `RailwaySavedData.save -> Train.write -> ScheduleRuntime.write -> CompoundTag.copy/ListTag.copy`.
- SavedData serializes the returned tree synchronously, so the temporary output list can reference
  the already owned context compounds without recursively copying the entire structure again.
- Source commit: `d65dfb0 Avoid redundant schedule NBT copies`.

## 2026-08-10T19:49:21+08:00 — Bound corrupted schedule condition state

- Binary NBT analysis found 102 trains and 4,838,253 total `ConditionContext` entries. Five trains
  contained approximately 1.23 million, 1.23 million, 1.13 million, 617 thousand and 617 thousand
  mostly empty compounds. An older July world file contained the same historical corruption.
- Loading now accepts only the number of conditions in the active schedule entry and normalizes
  related state before the next save. This prevents malformed persisted data from becoming a
  permanent autosave and heap cost.
- Source commit: `e4eb1c7 Bound persisted schedule condition state`.

## 2026-08-10T19:54:14+08:00 — Cache Steam 'n' Rails bogey style resolution

- JFR attributed a repeated hot path to SNR calling `CarriageBogey.getStyle` while rendering or
  updating large trains.
- Cached the registry resolution and invalidated it when the style ID stored in NBT changes.
- This is an API-level compatibility optimization: SNR remains the owner of its bogey styles, and
  Create avoids repeating the same registry lookup.
- Source commit: `d1f8a7f Cache carriage bogey style resolution`.

## 2026-08-10T19:57:38+08:00 — Back off failed train path searches

- Failed navigation attempts could retry at the same cadence across many trains, synchronizing
  expensive searches and amplifying congestion.
- Added bounded exponential retry backoff with deterministic train-UUID jitter. Successful paths
  return to normal scheduling, while repeated failures spread their CPU cost across ticks.
- Source commit: `101540a Back off failed train path searches`.

## 2026-08-10T20:44:57+08:00 — Production persistence validation

- The corrected build reached the dedicated-server ready state and completed saves at
  `20:39:18+08:00` and `20:44:18+08:00` without `Can't keep up!`. The pre-fix JFR recorded the
  same periodic route blocking the server thread for 3.823 seconds.
- Post-save GZIP NBT inspection found 7 total `ConditionContext` entries across 102 trains, down
  from 4,838,253; the maximum for one train is 1.
- Remaining large data is legitimate world content, dominated by 76,145 carriage contraption
  blocks, 13,590 signal blocks, and graph/intersection state for 1,132 rail graphs and 316
  carriage contraptions.
- Client collider coalescing still requires a real frame-time comparison with two large trains
  opening doors together; the server smoke test cannot measure that rendering result.

## 2026-08-10T20:52:51+08:00 — Standardize the performance version

### Version decision

- Standardized this branch on `6.0.10-mc1.21.1-yunniverse-perf-v4`:
  `<upstream Create version>-mc<Minecraft version>-<downstream iteration>`.
- Iteration `v4` is shared with the corresponding MTR build. Advancing from the previous
  `v2`/`v3` artifact suffixes avoids release-name collisions and makes the pair unambiguous.

### Build-system changes

- Made the complete standardized value the Gradle project version and generated NeoForge mod
  version.
- Changed the archive base from `create-<minecraft-version>` to `create`, because Minecraft is now
  represented exactly once inside the version. The expected artifact is
  `create-6.0.10-mc1.21.1-yunniverse-perf-v4.jar`.
- Aligned optional publishing version, display name and Git tag with the same identifier; no
  publishing path appends a second Minecraft version.

### Validation

- `gradlew jar` completed with `BUILD SUCCESSFUL`; compilation reported 66 existing removal and
  deprecation warnings and no new error.
- Produced `create-6.0.10-mc1.21.1-yunniverse-perf-v4.jar`.
- ZIP-level inspection confirmed NeoForge `modId = "create"`, runtime
  `version = "6.0.10-mc1.21.1-yunniverse-perf-v4"`, and matching manifest
  `Specification-Version` and `Implementation-Version` values.
- The pre-commit validation JAR SHA-256 is
  `5F83D2B637A47F5BE2BB2B639EA67181CDB4D1BC345350D139AB544DBD2E72A5`; a clean rebuild after
  committing is required so the embedded Git hash identifies this versioning commit rather than
  the prior commit plus the `-modified` suffix.
