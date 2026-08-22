# Create 1.21.1 Performance Walkthrough

This file records evidence, design changes, validation, and deployment findings for the
Create performance branch. It intentionally documents auditable engineering
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

- Standardized this branch on `6.0.10-mc1.21.1-performance-v4`:
  `<upstream Create version>-mc<Minecraft version>-<downstream iteration>`.
- Iteration `v4` is shared with the corresponding MTR build. Advancing from the previous
  `v2`/`v3` artifact suffixes avoids release-name collisions and makes the pair unambiguous.

### Build-system changes

- Made the complete standardized value the Gradle project version and generated NeoForge mod
  version.
- Changed the archive base from `create-<minecraft-version>` to `create`, because Minecraft is now
  represented exactly once inside the version. The expected artifact is
  `create-6.0.10-mc1.21.1-performance-v4.jar`.
- Aligned optional publishing version, display name and Git tag with the same identifier; no
  publishing path appends a second Minecraft version.

### Validation

- `gradlew jar` completed with `BUILD SUCCESSFUL`; compilation reported 66 existing removal and
  deprecation warnings and no new error.
- Produced `create-6.0.10-mc1.21.1-performance-v4.jar`.
- ZIP-level inspection confirmed NeoForge `modId = "create"`, runtime
  `version = "6.0.10-mc1.21.1-performance-v4"`, and matching manifest
  `Specification-Version` and `Implementation-Version` values.
- The pre-commit validation JAR SHA-256 is
  `5F83D2B637A47F5BE2BB2B639EA67181CDB4D1BC345350D139AB544DBD2E72A5`; a clean rebuild after
  committing is required so the embedded Git hash identifies this versioning commit rather than
  the prior commit plus the `-modified` suffix.

## 2026-08-10T21:04:26+08:00 — Derive the version from independent components

- The first standardized configuration stored `mc1.21.1` inside `mod_version` while also retaining
  `minecraft_version = 1.21.1`. Although its output was correct, a future Minecraft upgrade could
  change one value without the other.
- Restored `mod_version` to the upstream-only value `6.0.10`, retained the authoritative
  `minecraft_version`, and added `modification_version = performance-v4`.
- Gradle now constructs `performanceVersion` once from those three fields and supplies it to the
  archive, generated Java build info, NeoForge metadata, publication display/version, and Git tag.
- The externally visible version remains `6.0.10-mc1.21.1-performance-v4`; the change removes
  duplicated version literals rather than renaming the artifact again.
- `gradlew jar` completed with `BUILD SUCCESSFUL`; ZIP inspection reconfirmed the expected filename,
  NeoForge version, and manifest `Implementation-Version` generated from the independent fields.

## 2026-08-20T15:35:00+08:00 — Restore deterministic station-door collision refresh

### Evidence and root cause

- The current server log contains no Create door exception. Static tracing shows station arrival
  still reaches `SlidingDoorMovementBehaviour`, toggles both door-half block states, and sends the
  changed states to tracking clients.
- The v4 collision-coalescing optimization marked the carriage collider dirty but rebuilt it only
  when a later collision consumer requested the cached list. A stationary train can have no such
  read after arriving, so its closed-door collision shape could remain cached indefinitely even
  though the door state had changed.
- `CarriageContraptionEntity.setBlock` mirrors a door state into every present carriage entity, but
  only the actor's contraption was previously marked dirty. This could leave another entity copy
  with an inconsistent collision cache.

### Change

- Centralized collider invalidation in both contraption `setBlock` paths, so every dynamic block
  state change and every present carriage entity copy is covered.
- Added a deterministic end-of-contraption-tick flush. Multiple door-half updates in one tick still
  coalesce into one full rebuild, while stationary trains cannot retain stale collision data.
- Kept the on-read refresh as a defensive path for updates arriving outside the entity tick.
- Removed the sliding-door-specific dirty mark because state mutation now owns invalidation.
- Advanced the downstream iteration to `6.0.10-mc1.21.1-performance-v5`.

### Expected result

- A train arriving at a controlled station opens a passable doorway on both server and client.
- Large and bidirectional arrivals retain the v4 coalescing benefit: at most one collider rebuild
  per affected contraption entity per tick, rather than one rebuild per door half.
- Compilation and runtime validation are recorded in the next timestamped entry after the build.

## 2026-08-20T16:01:00+08:00 — Validate the Create v5 artifact

- `gradlew compileJava --no-daemon` completed with `BUILD SUCCESSFUL`. Its 33 warnings are existing
  removal/deprecation notices; the collider lifecycle changes introduced no compile error.
- A post-commit `gradlew jar --no-daemon` completed with `BUILD SUCCESSFUL` and produced
  `create-6.0.10-mc1.21.1-performance-v5.jar` (19,137,611 bytes).
- ZIP metadata reports NeoForge `modId = "create"`, version
  `6.0.10-mc1.21.1-performance-v5`, and matching manifest specification and implementation
  versions.
- Artifact SHA-256: `598034A2E345191D3786C8315047DB4692E88023347E8168A0619ECFCB9E10DE`.
- Dedicated-server startup and an in-game station-door test remain deployment checks; compilation
  alone cannot observe client door animation or passage through the rebuilt collider.

## 2026-08-20T16:14:00+08:00 — Deploy and package the Create v5 fix

- Replaced the active server and client-pack Create JAR with
  `[自修改]create-6.0.10-mc1.21.1-performance-v5.jar`; the superseded v4 files were moved to
  dated recovery directories rather than deleted.
- Restarted the dedicated server. It reached `Done (6.326s)` and listened on port 25565. The
  startup log identifies Create version `6.0.10-mc1.21.1-performance-v5` and commit
  `228ab42faace3be776666a9cd019e155ff9b5303`; no startup error, duplicate mod ID, or dependency
  failure was reported.
- Deployed the paired Copycats rendering fix on both sides because the reported Copycat material
  defect is client-rendering code, while Create's carriage collision state is authoritative on the
  server and mirrored to clients.
- Updated the client manifest and documentation, then packaged 38 active JARs as
  `mods-1-21-b8-performance-fixes.zip` (562,390,276 bytes; SHA-256
  `F15D1AD1FBF25072C338035B544E135A5A26826FAED80EC78B364610FF6DE109`). Every embedded JAR was
  streamed through SHA-256 and matched the embedded 38-entry manifest; UTF-8 names and four
  documentation entries were also verified.
- The prior b7 bundle is retained as
  `client-pack/archive/mods-1-21-b7-pre-create-copycats-fix.zip`.
- Remaining acceptance test: drive a train into a scheduled station and verify door animation and
  passage on a b8 client, including simultaneous arrival of two large trains. Dedicated-server
  readiness proves load compatibility but cannot observe the client animation.

## 2026-08-20T20:02:00+08:00 — Isolate the Create v5 station-door test

- At the user's request, restored official Copycats+
  `copycats-3.0.4+mc.1.21.1-neoforge.jar` on both the server and client while retaining Create
  `6.0.10-mc1.21.1-performance-v5`. No Create source or artifact changed in this step.
- The official Copycats+ JAR is byte-identical on both sides: 1,791,747 bytes, SHA-256
  `8480E2A62EAA625F75776831C1D8A9EF3D77D2D816FA2E67566C6EC25BA745D1`. The customized Copycats+
  build was moved into dated rollback directories and is not active.
- Generated the Create-only control pack `mods-1-21-b8-create-v5-copycats-original.zip`
  (562,387,115 bytes; SHA-256
  `A6DEC1564A07350FDA58C1A12441D0397DD2A58C7BA2AE2AFA0A8FCC2B2E46D3`). All 38 embedded JARs
  matched the embedded manifest; official Copycats+ was present and the customized build absent.
- The dedicated server loaded Create v5 commit `228ab42faace3be776666a9cd019e155ff9b5303` and official
  Copycats+ `3.0.4+mc.1.21.1-neoforge`, reached `Done (7.462s)`, and listened on port 25565. The
  world-version warning records the intentional Copycats+ downgrade; it is not a loader failure.
- This deployment isolates the Create collider lifecycle change for the next in-game station-door
  test. Copycats+ rendering behavior is explicitly outside this test pass.

## 2026-08-20T20:09:17+08:00 — Rename the Create-only control pack to b8

- Renamed the active control-test revision from b9 to b8 at the user's request. No mod JAR, server
  configuration, or Create source changed, so the running server did not require another restart.
- Rebuilt `mods-1-21-b8-create-v5-copycats-original.zip` so its embedded README and changelog use
  the same b8 identity. The prior outer-name archive remains recoverable under `client-pack/archive`.
- Stream verification reconfirmed all 38 embedded JAR hashes and confirmed that packaged documents
  contain no stale b9 reference. Final size and SHA-256 are 562,387,115 bytes and
  `A6DEC1564A07350FDA58C1A12441D0397DD2A58C7BA2AE2AFA0A8FCC2B2E46D3`.

## 2026-08-20T20:53:37+08:00 — Diagnose the remaining automatic train-door failure

### Runtime evidence

- The b8 control test used Create v5 with official Copycats+ 3.0.4. The player was connected from
  `20:22:39+08:00` to `20:33:35+08:00`; the test window contains no Create exception and no
  `Can't keep up!` warning. A separate economy-mod date-parsing exception was emitted about
  once per second and should be fixed independently, but it did not alter Create's door state.
- The saved train nearest the player's logout position was approximately 31 blocks away. It had
  three carriages, speed zero, a valid `currentStation`, 26 registered door actors, and no stalled
  carriage. None of those actors persisted `Data.Open = true`: 13 were false and 13 had not yet
  stored the key. This rules out client animation and collider refresh as the primary failure;
  server-side `shouldOpen()` returned false before any door block-state mutation.
- The train's station UUID is `920db7f2-f905-4bd3-a4b4-d46f8d7afb18`. Its active station block
  entity has the same UUID at `(4836, 71, 2059)` and `DoorControl = ALL`, but the corresponding
  `GlobalStation` in `create_tracks.dat` persisted `BlockEntityPos = (0, 0, 0)`. Door lookup
  therefore queried the origin and returned no `DoorControlBehaviour`.
- The corruption is systemic: 418 of 419 persisted Create stations have a zero block-entity
  position. Including observers and Steam 'n' Rails single-block edge points, 522 of 523 persisted
  positions are zero; the only nonzero station was created or refreshed after migration.

### Migration and source cause

- The imported 1.20.1 source data contains the same station UUID with the correct
  `TilePos = (4836, 71, 2059)` and `TileDimension = 0`. Across that file, 526 single-block edge
  points use the legacy `TilePos`/`TileDimension` keys and none use the 1.21.1 names.
- The 1.21.1 `SingleBlockEntityEdgePoint.read` path reads only `BlockEntityPos` and
  `BlockEntityDimension`. Its backwards-compatible block-position decoder handles the old value
  encoding only when the new key exists; it does not fall back to the legacy key names. The first
  1.21.1 save consequently rewrote each missing position as `(0, 0, 0)`.
- `TrackTargetingBehaviour.createEdgePoint` returns an already registered point by UUID without
  calling `blockEntityAdded`. Loading the real station block entity therefore does not repair the
  stale global position, even though the authoritative current-world coordinate is available.
- Read-only searches of the official Create issue and commit history found no later fix or issue
  matching these legacy key names. Official commit `70933fea` addresses old block-position value
  encoding, but not the `TilePos` to `BlockEntityPos` field rename.

### Correct repair boundary

- Add legacy-key fallback when reading `SingleBlockEntityEdgePoint`, preserving compatibility for
  future 1.20.1 imports.
- When `TrackTargetingBehaviour` resolves an existing single-block point by UUID, refresh its
  position and dimension from the authoritative loaded block entity and mark track data dirty only
  when the location changed. This repairs the current already-zeroed world lazily as station chunks
  load and avoids guessing coordinates or depending on an old backup.
- No source or runtime fix was applied in this diagnostic entry. The earlier collider coalescing
  remains valid as a post-toggle performance optimization, but it cannot make a door open when
  station-controller lookup fails before the toggle.

## 2026-08-21T16:58:50+08:00 — Prepare upstream Create performance contributions

### Contribution boundary

- Rebased each contribution independently onto the official
  `Creators-of-Create/Create:mc1.21.1/dev` head `0924e93`. The upstream branches contain no
  downstream version strings, deployment notes, client-pack metadata, world-specific repair code,
  or prebuilt artifacts.
- Used separate branches so persistence repair, navigation retry policy, and collision-cache
  lifecycle can be reviewed and reverted independently.
- Kept all pull requests as drafts. GitHub reports each as mergeable; the repository has not
  attached automated checks at the time of this entry.

### Schedule condition-state persistence

- Existing issue: https://github.com/Creators-of-Create/Create/issues/7633
- Draft PR: https://github.com/Creators-of-Create/Create/pull/10701
- Branch: `A-BenLi06:perf/schedule-runtime-state`
- `conditionProgress` and `conditionContext` are meaningful only for the active post-transit
  condition columns. The patch derives that expected count, trims surplus entries, fills missing
  entries, clamps progress, and performs the same normalization while loading and before saving.
- The persistent cost changes from O(historical or corrupt context count) to O(active condition
  columns). In the affected 102-train world this reduced 4,838,253 context entries to 7. The prior
  `create_tracks.dat` main-thread save took 3.823 seconds; two observed autosaves after repair
  produced no matching `Can't keep up!` warning.
- A previously tested micro-optimization that inserted runtime `CompoundTag` instances directly
  into the save tree was deliberately removed before publication. Save I/O can outlive NBT-tree
  construction, so retaining `CompoundTag::copy` avoids aliasing mutable runtime state. Once the
  list is bounded to single digits, that copy is not a material cost.

### Failed scheduled-navigation backoff

- New issue: https://github.com/Creators-of-Create/Create/issues/10700
- Draft PR: https://github.com/Creators-of-Create/Create/pull/10702
- Branch: `A-BenLi06:perf/navigation-failure-backoff`
- The fixed 40-tick retry cadence can synchronize many unreachable trains after a restart or
  server stall. Each synchronized attempt may traverse a large part of the same track graph.
- Consecutive failures now use 40, 80, 160 and 320 tick base intervals plus a deterministic
  0-39 tick UUID offset. Success, a normal cooldown, and runtime reset clear the failure count.
- A persistently unreachable train falls from 30 searches per minute to approximately 3-4 at the
  cap, while deterministic jitter distributes different trains across server ticks. Path choice
  and successful navigation are unchanged.

### Batched contraption collider refresh

- Existing reports:
  https://github.com/Creators-of-Create/Create/issues/6902,
  https://github.com/Creators-of-Create/Create/issues/9026, and
  https://github.com/Minecraft-Transit-Railway/Minecraft-Transit-Railway/issues/1019
- Draft PR: https://github.com/Creators-of-Create/Create/pull/10703
- Branch: `A-BenLi06:perf/coalesce-door-collider-refresh`
- Official commit `8f30c2c` already replaced collision AABB objects with dense structure-of-arrays
  storage. The remaining issue is multiplicity: every animated door can still initiate another
  scan over every contraption block during the same entity tick.
- Block changes now mark collider data dirty. The entity performs one deterministic end-of-tick
  rebuild; a collision read before that flush rebuilds immediately. Carriage copies in every
  loaded dimension receive the same dirty mark.
- For D door updates on a B-block contraption, the common path changes from O(D × B) shape work to
  O(D + B), while direct immediate invalidation remains available to other callers.

### Validation and deferred work

- All three final branches passed `gradlew compileJava --no-daemon` on Java 24.0.2. The warnings
  are upstream deprecation/removal warnings; no new compilation error was introduced.
- The broad `841187d` allocation patch was not submitted upstream as one PR. It combines selector
  caching, listener caching, navigation-list reuse, and collision-loop refactoring, and part of its
  collision premise has been superseded by the official dense-collider rewrite. Those changes need
  isolated profiles and individual semantic tests before upstream publication.
- The legacy `TilePos` to `BlockEntityPos` station migration defect is correctness work and was
  intentionally excluded from all performance PRs.

## 2026-08-21T18:07:04+08:00 — Restore migrated single-block edge-point coordinates

### Source repair

- `SingleBlockEntityEdgePoint.read` now falls back from the 1.21.1
  `BlockEntityPos`/`BlockEntityDimension` keys to the 1.20.1
  `TilePos`/`TileDimension` keys. New saves continue to write only the current schema.
- When `TrackTargetingBehaviour` resolves an existing point by UUID, the server compares its
  persisted location with the authoritative loaded block entity. A mismatch refreshes the point,
  marks railway data dirty, queues a point sync, and notifies the block entity. Matching points
  take the original no-op fast path.
- Restored defensive `CompoundTag::copy` behavior for schedule saves. The bounded condition list
  makes copying cheap, and avoiding save-tree/runtime-state aliasing is safer if NBT I/O is
  asynchronous.
- Advanced the downstream test version to
  `6.0.10-mc1.21.1-performance-v6`.

### Existing-world recovery plan

- The retained 1.20.1 source file contains 526 UUID-addressed `TilePos` records.
- A dry run against the stopped live world matched 522 current single-block edge points. All 522
  matched records currently had `BlockEntityPos = (0, 0, 0)`, and all are recoverable by UUID;
  the projected post-repair zero count for matched points is zero.
- The live file will be copied to a timestamped recovery directory before an atomic NBT rewrite.
  Startup and a second NBT audit remain required before the repaired world is handed off for the
  station-door performance test.

## 2026-08-21T18:14:33+08:00 — Build and apply the coordinate recovery

### Build verification

- Built `create-6.0.10-mc1.21.1-performance-v6.jar` with
  `gradlew jar --no-daemon`; the build completed successfully. The remaining compiler warnings are
  pre-existing upstream deprecation/removal warnings.
- The artifact SHA-256 is
  `2519858C51FD825BA1D475C177551AAD1CFA33D38ECEB55EE19837912C4FC7CC`.
- Confirmed the artifact contains the repaired `SingleBlockEntityEdgePoint`,
  `TrackTargetingBehaviour`, and defensive `ScheduleRuntime` classes. Its metadata reports
  `6.0.10-mc1.21.1-performance-v6` and source commit `8ed9c36`.

### Existing-world recovery result

- Stopped the server cleanly before editing `uDays/data/create_tracks.dat`.
- Backed up the untouched live file to
  `migration-backups/20260821-181300-create-station-coordinate-restore/create_tracks.dat`; its
  SHA-256 is `9B38A5A55469196F62E050EBFAB531D398DF60D046AC0F67CEB7166164B5507E`.
- Matched 522 current edge points to the retained 1.20.1 data by UUID and atomically restored their
  positions and dimension-palette indexes. A post-write NBT reload found 522 matches, zero pending
  changes, and zero remaining origin positions among those matches.
- The repaired live file SHA-256 before restart is
  `2E221F27B2F23C6E82600D13DE21B3319E363DF52D3699D9715563C5FA70BF8B`.
- Independently verified station UUID `920db7f2-f905-4bd3-a4b4-d46f8d7afb18` now resolves to
  block position `(4836, 71, 2059)` in dimension-palette entry `0`, matching its loaded station
  block entity. This is the concrete door test location identified during diagnosis.

### Controlled startup verification

- Deployed the byte-identical v6 artifact to the server and client test pack, leaving official
  Copycats+ 3.0.4 and MTR performance v4 unchanged. Both active Create JARs match the build hash
  above; v5 was moved to timestamped rollback directories.
- The server loaded `6.0.10-mc1.21.1-performance-v6` from commit `8ed9c36`, listened on port
  25565, and reached `Done (7.363s)` without a startup failure, tick-loop exception, out-of-memory
  error, or `Can't keep up!` warning.
- A snapshot taken after Create loaded the world still produced 522 UUID matches, zero pending
  repairs, and zero origin positions. The known station remained `(4836, 71, 2059)` in dimension
  entry `0`; loading did not clear the restored data.
- The client archive is `mods-1-21-b8-create-v6-station-coordinate-repair.zip` (SHA-256
  `89724BAC6BE6CE8A646873908E6F194052DFAFFF84F9F256436F266B6B9471C6`). It contains one v6 Create
  JAR, no v5 Create JAR, and the official Copycats+ control build.

## 2026-08-21T18:30:00+08:00 — Neutralize downstream naming

- Replaced the downstream build qualifier with `performance-v6` and updated historical artifact
  references in this branch to use neutral performance terminology.
- The source behavior is unchanged. A fresh build is required so the JAR metadata and filename use
  the neutral version consistently.
- `gradlew jar --no-daemon` completed successfully and produced
  `create-6.0.10-mc1.21.1-performance-v6.jar` with SHA-256
  `5602A16796957CB5C7FB829C10C6B54BF9D228931C7680636710B6A60D92822E`.
- Extracted-artifact inspection confirmed matching specification/implementation versions and no
  removed downstream branding token in the JAR contents.
- Renamed the maintained branch to `perf/optimized-build-1.21.1`, pushed and commit-verified the
  replacement, then deleted the old remote branch. The maintained 1.20.1 branch was cleaned and
  renamed to `perf/optimized-build-1.20.1` with the same replacement-first sequence.
- Updated the current GitHub release to tag `create-6.0.10-performance-v6` with the neutral v6 JAR,
  and retained a neutral 1.20.1 release. Superseded branded releases and tags were removed.

## 2026-08-23T00:39:32+08:00 — Correct upstream PR commit attribution

- Rewrote the four commits across upstream PRs #10701, #10702, and #10703 so both the Git author
  and committer resolve to the contributor's verified GitHub account instead of the automation
  placeholder identity.
- Preserved each branch's final tree object exactly, so the source, tests, and PR diffs are
  byte-for-byte unchanged. Only commit metadata, parent hashes, and resulting commit IDs changed.
- Updated the fork branches with `--force-with-lease` after comparing their remote heads, and kept
  the former heads under local `refs/backup/author-rewrite-20260823/*` recovery references.
- Queried the GitHub commit API after the push and confirmed that all four rewritten commits map
  both author and committer to `A-BenLi06`.

## 2026-08-23T00:58:26+08:00 — Normalize remaining repository attribution

- Audited every branch and tag in all 21 repositories owned by the account rather than relying on
  GitHub's default-branch commit search.
- Found 22 remaining automation-identity commits in this fork, limited to the maintained 1.20.1
  and 1.21.1 performance histories and their two release tags. The three upstream PR branches were
  already clean after the earlier targeted rewrite.
- Rewrote only matching author or committer identities to `A-BenLi06` with the account's verified
  email, preserving commit messages, dates, topology, and every final source tree.
- Preserved the original affected refs in a local recovery bundle before updating remote branches
  and release tags with explicit leases.
