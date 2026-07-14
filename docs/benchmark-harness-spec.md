# WP0 — Benchmark Harness & Instrumentation Spec

Companion to `planner-research-plan.md` (v0.5) and `planner-theory-notes.md`.
Target: Lambda on current master (MC 1.21.11, Fabric, Kotlin). The old
`feature/pathfinder-new` branch is *reference only*; its Lazy D* Lite core, refiner, and unit
tests get ported through this harness, not merged.

## 1. Purpose and acceptance

Every hypothesis in the research plan (H1–H9, C1 studies) is falsifiable only through this
harness. It must therefore exist **before** WP1+ code.

**Acceptance criterion (fixed):** the harness reproduces the Lazy D* Lite paper's headline
result — median per-tick computation time of the reference D* Lite implementation roughly an
order of magnitude below A*, on the Open Plains / Dense Forest / Maze suites — within noise,
using the reference-branch planner driven through the harness API. If we can't reproduce our
own published numbers, the harness (or the port) is wrong; nothing else proceeds.

**Second deliverable (theory-driven):** the WP0 calibration pass replaces every nominal
constant marked in the theory notes — movement-mode rate caps for the T1 heuristic, the
server-divergence distribution δ for T3 envelopes — with simulator-measured values.

## 2. Architecture: three tiers

### Tier 0 — JVM-only microbenchmarks (no Minecraft)

For the search-core shootout (§8.1 of the plan: D* Lite vs MPGAA* vs D* Extra Lite vs
truncated/lazy variants) and data-structure work (priority queues, mask stamping, overlay
diffs, later the connectivity oracle).

- Plain JUnit in `src/test/kotlin` (the reference branch's `pathing/` unit tests are the seed:
  `DStarLiteCoreTest`, `UpdatablePriorityQueueTest`, `GridGraphTestUtil`).
- Synthetic deterministic worlds: seeded grid/maze/cave generators implementing the same
  world-query interface the planner uses (`WorldView` once WP1 lands), so search cores are
  benchmarked against identical, MC-free graphs.
- Timing via JMH-style warmup/repeat discipline (manual harness is fine; JMH optional later).
  Report medians + p99 across ≥ 30 repetitions after warmup; pin `-Xmx`, disable tiered-compile
  surprises with a warmup budget.
- Oracle checks: every benchmark instance also asserts path optimality against a reference
  Dijkstra, so performance runs double as correctness fuzzing.

### Tier 1 — Scenario runner on Fabric client gametest (the main tier)

Builds on the already-wired `src/gametest` source set and the `FabricClientGameTest` pattern in
`LambdaTest.kt` (full client + integrated server, one JVM, CI-capable).

**Lifecycle per scenario:**
1. Create singleplayer world from the scenario's fixture spec (see §3), deterministic settings:
   fixed seed, `doDaylightCycle=false`, `doWeatherCycle=false`, `doMobSpawning=false`,
   `randomTickSpeed=0`, creative or survival per scenario.
2. Build the fixture (paste schematic / run mutation script prologue), wait for chunk sync.
3. Position the agent (pose = position + look), apply scenario config overrides, clear/populate
   inventory per scenario.
4. Start the system under test through its public handle (planner traversal handle / task
   handle), with the scenario's tick budget.
5. Drive ticks; the S2-style *mutation script* fires world edits at scheduled ticks (via server
   commands, so changes arrive through the real chunk-update path the planner listens to).
6. Terminate on oracle success / failure / budget exhaustion; emit metrics (§4); tear down the
   world (fresh world per repetition — no cross-contamination).

**Determinism policy:** fixed seeds and fixtures make *worlds* deterministic; agent physics
and thread scheduling remain mildly nondeterministic (async planner), which is why every
scenario runs `n ≥ 10` repetitions and all comparisons use paired distributions, never single
runs. Do not chase bit-perfect determinism; measure variance instead.

### Tier 2 — Endurance runs (S7)

A scripted task playlist (travel → mine → build → stash round-trips) driven through the
existing `task/` tree on a persistent world for multi-hour sessions; samples memory (graph
nodes/edges resident, heap), RQ7 pruning metrics, and leak detection. Same metric pipeline,
lower sampling frequency.

## 3. Scenario definition

Kotlin DSL (matches the codebase's task-DSL idiom; scenarios are code, reviewed like code),
one file per suite under `src/gametest/kotlin/com/lambda/bench/suites/`:

```kotlin
scenario("maze-64-static") {
    suite = S1
    fixture = schematic("fixtures/maze64.litematic", at = Origin)
    start = pose(1.5, 1.0, 1.5, yaw = 0f)
    goal = blockGoal(62, 1, 62)
    budget = 2.minutes
    reps = 10
    oracle = reachedGoal(tolerance = 1.0)
    metrics += listOf(PlannerTimings, PathQuality, Memory)
}
```

- **Fixtures:** (a) generated presets (superflat, parametric mazes/gauntlets built by code —
  preferred: no binary assets to version); (b) `.litematic` schematics via the existing
  Litematica dependency for the S5 corpus and community parkour maps; (c) seeded natural
  terrain (fixed seed + fixed region) for S1/S2 realism suites.
- **Mutation scripts** (S2/S4): `at(tick = 200) { setBlock(x, y, z, stone) }` — arbitrary
  scheduled edits, including adversarial "grief the path" generators parameterized by seed.
- **Config overrides:** planner settings per scenario (this is how ablations run — same
  scenario, config matrix).

## 4. Metrics pipeline

**Event stream:** JSON Lines, one file per (scenario, repetition):
`logs/benchmarks/<timestamp>-<git-sha>/<suite>/<scenario>/rep-<n>.jsonl`.

```json
{"t": 1234, "ev": "repair_end", "expansions": 412, "heap_ops": 1893, "wall_us": 2210}
```

Event vocabulary (extend as WPs land; never rename — dashboards depend on it):

| Source | Events |
|---|---|
| Planner | `plan_start`, `initial_path`, `repair_start/end` (expansions, heap ops, wall time), `edge_eval` (lazy-eval cost, WP2+), `discovery_invoked` (pattern, yaw intervals, sims, WP3+), `refine_accept/reject`, `prune_pass` (WP7) |
| Executor | `waypoint_reached`, `segment_start/end`, `envelope_violation`, `recovery_entered`, `fall_damage`, `death`, `stuck_detected` |
| World | `mutation_applied`, `chunk_loaded`, `overlay_commit/rollback/violation` (WP1+) |
| Task | `task_start/complete/fail` (typed reason), `certificate_result` (T8: certified / failing pair) |
| System | `tick_time`, `heap_used`, `graph_nodes`, `graph_edges` |

**Instrumentation API:** a `PlannerMetrics` sink interface in the planner's core (no-op
implementation in production, JSONL writer in the harness). The reference branch's
`PathRefinementStats` / `PathfinderExecutorDebugState` / `ShortcutAttemptDebug` classes are the
seeds — port their counters into sink events rather than debug-state objects.

**Aggregation:** a small JVM CLI (`./gradlew benchReport`) folds JSONL → `summary.json` per
run: p50/p95/p99 per metric per scenario, success rates, cost ratios vs recorded
optimal-reference where available (Tier 0 Dijkstra; RQ9 reach-set optima later).

**Regression gates:** golden `summary.json` checked into `docs/bench-baselines/`; CI compares
current run to golden with per-metric tolerance bands (default ±15% on medians, hard fail on
success-rate drops). Updating a golden file is a reviewed change with justification — that's
the mechanism that makes performance a maintained invariant instead of a launch-day number.

## 5. Baselines

1. **Self-ablations** (primary): config-matrix runs of our planner — every ablation in plan
   §5.4 is a scenario × config pair; zero extra integration cost.
2. **Baritone (in-process):** already a mod dependency (rfresh2 fabric fork) with source
   reference at `/home/constructor/Git/baritone/`. Adapter maps our goals to Baritone goals
   (`GoalBlock`, `GoalNear`, `GoalGetToBlock`), subscribes to its path events for timing, runs
   the *same scenarios* through the same oracle. Fairness notes: warm its cache worlds first;
   pin its settings to defaults; record its version.
3. **Reference-branch Lazy D* Lite** (continuity baseline): the paper implementation driven
   through the harness — this is also the §1 acceptance vehicle.
4. mineflayer-pathfinder: out of scope for phase 1 (separate process/protocol; revisit only if
   a result needs external validation).

## 6. Calibration pass (theory-note constants)

Runs as ordinary Tier 1 scenarios with dedicated oracles:

- **Movement-rate caps (T1):** scripted max-effort runs per movement mode (sprint, sprint-jump
  flat/ascending stairs, ladder, fall) on purpose-built tracks; caps = measured maxima + safety
  margin; emitted as a generated Kotlin constants file with provenance header (date, git sha,
  MC version) — the heuristic *imports measured constants*, never literals.
- **Server divergence δ (T3):** run scripted maneuvers, record per-tick |predicted − observed|
  distribution in singleplayer (lower bound) and against a local dedicated server with
  simulated latency (realistic bound); store the quantiles the envelope inflation uses.
- **Break/place timing (WP4 costs):** measured per tool tier / block family on fixture walls.

## 7. Gradle & CI wiring

- `./gradlew test` — Tier 0 (fast, every commit).
- `./gradlew bench` — Tier 1 via loom's client gametest run config, scenario filter flag
  (`-Pbench.filter=S1,S3`), headless on CI via xvfb (client gametest renders; verify
  first CI run; fallback: self-hosted runner with GPU-less GL like `llvmpipe`).
- `./gradlew benchReport` — aggregation + gate check.
- CI matrix: smoke subset (1 scenario per suite, reps=3) on PRs; full nightly.

### Deterministic generated parkour corpus

The implemented client GameTest exposes an opt-in quartz corpus:

```shell
./gradlew runClientGameTest -Pbench.parkour=true -Pbench.filter=parkour-
```

`ParkourCourseGenerator` emits canonical isolated landing layouts across the discovery
fan and short-pad chains covering straight carry plus 45°/90° direction changes and
vertical transitions. It is deliberately deterministic; every row has a stable case id
and can be replayed through `bench.filter`. The run directory includes
`parkour-manifest.json`, per-tick telemetry, and per-jump launch/target/landing records.
Generated cases additionally require their intended quartz pads to be targeted and
landed in order, with no retry, so reaching the final goal through a shortcut or recovery
does not hide an execution failure. The complete generated corpus is gated now that all
rows pass; any planner rejection, retry, shortcut, walk-off, or missed pad fails GameTest.

**Open items to resolve during WP0 implementation (not blockers to start):**
(a) whether client gametest supports tick-rate acceleration (`/tick rate` exists in
1.20.3+ servers; client sync needs verification) — endurance tier wants it, Tier 1 doesn't
need it; (b) xvfb vs native headless support in current loom client-test; (c) whether Baritone
adapter runs in the same JVM without classloader friction (it already loads as a dep today —
likely fine).

## 8. Work breakdown

| Step | Content | Exit criterion |
|---|---|---|
| WP0.1 | Scenario DSL + runner skeleton on client gametest; one trivial scenario green | `bench` task runs one scenario headless locally |
| WP0.2 | Metrics sink API + JSONL writer + `benchReport` aggregation | summary.json with percentiles for WP0.1 scenario |
| WP0.3 | Fixture infrastructure: generators, litematic paste, mutation scripts | maze + mutation scenario runs |
| WP0.4 | Port reference-branch planner behind harness API; paper suites (plains/forest/maze) | **§1 acceptance: paper result reproduced** |
| WP0.5 | Baritone adapter + first head-to-head on S1 | comparative summary.json |
| WP0.6 | Calibration pass (T1 caps, T3 δ, break/place table) | generated constants file consumed by theory-note formulas |
| WP0.7 | CI wiring + golden baselines + gates | PR smoke suite green, nightly full suite |
