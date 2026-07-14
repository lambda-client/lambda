# A Layered Task and Motion Planning Architecture for Voxel Worlds

**Research and Implementation Plan — NeoLambda Next-Generation Planner**

*Draft v0.5 — July 2026 (v0.2 incorporated the leijurv design discussions of Aug 2025 — voxel-grid
failure taxonomy, arbitrary-angle maneuver discovery, reach-set local planning — and implementation
evidence on dynamic connectivity from btrekkie/dynamic-connectivity issue #4; v0.3 added the SOTA
challenger review (§8) and the pre-implementation Theory Track WP-T; v0.4 worked the theory track:
see `planner-theory-notes.md` — all of T1–T8 now resolved or resolved modulo calibration; v0.5
retargets to current master (MC 1.21.11), maps existing integration assets (§4.8), and adds the
WP0 harness spec: `benchmark-harness-spec.md`)*

---

## 1. Abstract

This document defines a research plan for extending the Lazy D* Lite navigation system
[[paper]](../../Lambda%20-%20Lazy%20D*%20Lite/lazy_d_star_lite.tex) into a full **Task and Motion
Planning (TAMP)** system for Minecraft: an agent that moves through the world with high accuracy
(including momentum-dependent parkour maneuvers), modifies terrain to enable traversal, constructs
arbitrary schematics, and plans resource acquisition (crafting, mining, stash logistics) — all
under partial observability and continuous world change.

The central thesis: the joint planning space
$(\text{position} \times \text{velocity} \times \text{world edits} \times \text{inventory})$
is intractable to search directly, but decomposes into a small stack of planners, each operating in
an abstraction where its subproblem is cheap, connected by two disciplined interfaces — a shared
**hypothetical world model** flowing downward and **typed failures plus cost estimates** flowing
upward. Three prior systems each solve one layer of this stack: Lazy D* Lite (incremental route
planning), GenerelSchwerz's *minecraft-pathfinding* (modular maneuver execution), and leijurv's
Baritone builder rewrite (dependency-ordered task planning with a connectivity safety oracle).
This plan integrates their insights, closes their individually acknowledged gaps, and defines the
experiments that validate each claim.

---

## 2. Problem Statement

### 2.1 Composite state space

Extending the formalism of the Lazy D* Lite paper: the world is a dynamic voxel grid
$\mathcal{W} \subseteq \mathbb{Z}^3$ with per-voxel state
$\text{state}(\mathbf{v}, t) \in \{\text{free}, \text{occupied}, \text{unknown}\}$, partially
observable through loaded chunks. The full agent state is

$$\sigma = (\mathbf{x}, \dot{\mathbf{x}}, \Delta_\mathcal{W}, I)$$

where $\mathbf{x} \in \mathbb{R}^3$ is position, $\dot{\mathbf{x}} \in \mathbb{R}^3$ is velocity,
$\Delta_\mathcal{W}$ is the set of world edits (block placements/breaks) performed so far relative
to the observed base world, and $I \in \mathbb{N}^m$ is the inventory as a resource vector.

A **task** is a goal predicate $\gamma(\sigma)$ (e.g., "position within region R", "schematic S
realized in $\Delta_\mathcal{W}$", "$I \geq$ required items"). The planning problem is to find a
control sequence minimizing expected completion time (measured in game ticks) subject to the
game's physics, the 20 Hz real-time budget, and partial observability.

### 2.2 Why direct search fails

- $\Delta_\mathcal{W}$ makes node identity exponential: a position-keyed graph cannot represent
  "the world after my own actions" (the root cause of Baritone's builder infinite loops — its
  costs cannot account for previous actions).
- $\dot{\mathbf{x}}$ raises the search dimension from 3 to 6; the paper explicitly defers this.
- $I$ couples spatially distant subproblems (crafting requires mining requires travel).
- Physics-dependent edge validity defeats classical spatial hierarchies (HPA*, octrees), as
  argued in the paper's Section on hierarchical abstraction.

### 2.3 Known limitations of the three source systems (the gaps this plan closes)

| System | Solves | Acknowledged gap |
|---|---|---|
| Lazy D* Lite (ours) | Incremental position-space routing, lazy unbounded graphs, budgeted async repair | Symmetric-traversability assumption (no drops/asymmetric moves); holonomic nodes (no momentum); no world-editing edges; unbounded memory growth |
| minecraft-pathfinding | Per-movement provider/executor/optimizer modularity; simulation-validated parkour execution | A*-based, non-incremental; no task layer; execution-focused |
| leijurv builder README | Sound build planning: place-order dependency DAG, SCC collapse, scaffolding, dynamic-connectivity anti-trap oracle | No breaking (scaffold removal unsolved); planning-focused, unimplemented movement integration; hypothetical placement engine unfinished |

---

## 3. Research Questions and Hypotheses

### 3.0 Central conjecture: voxel-grid sufficiency

From the design discussions with leijurv (Aug 2025): a position-keyed voxel graph fails to
represent optimal movement in **exactly three ways** —

- **F1 — Cost mispricing.** The route is correct but edge costs are slightly wrong (e.g.,
  sprint-jump head-hitting accelerates travel). An engineering defect, not a structural one:
  fixable by *simulator-calibrated* primitive cost models instead of hand-tuned constants.
- **F2 — Angular overestimation.** Headings quantized to 45° inflate costs. Provably repairable
  by post-refinement (the source paper); the open *empirical* question is whether angular
  quantization ever flips a large-scale route decision, or is purely a local-quality issue.
- **F3 — Missed connections.** Maneuvers that exist only at arbitrary yaw and/or with built-up
  momentum (angled gap jumps, momentum chains) are absent from the graph entirely, changing path
  *topology* — the only failure class a refiner cannot repair, because refinement is constrained
  to the homotopy class of the coarse path.

**Conjecture C1:** F1 and F2 do not justify abandoning the voxel graph — whose advantages (fast
search, fast flood-fill backtracking on dead ends, natural break/place edges) are decisive — and
F3 can be closed by *discovering the missing edges* (macro-edges, §4.4) rather than by enlarging
the search space. Supporting observation (leijurv): jumps can only begin on integer ticks, so
nearly every maneuver is expressible as a voxel-A→voxel-B edge with a tick-denominated cost;
exceptions requiring multi-block inertia buildup (e.g., ice runways) are rare and spatially
localizable. C1 is the theoretical justification for keeping L2 position-keyed; RQ6, RQ8, and
RQ9 test its three clauses.

### 3.1 Research questions

**RQ1 — Asymmetric incremental search.** Can *template-based motion primitives* with inverse
masks extend backward incremental search (D* Lite) to asymmetric, physics-dependent movements
(drops, jumps) without degrading repair efficiency?
> **H1:** Representing each movement as a voxel-mask template makes $\text{Pred}(v)$ computable by
> stamping the inverse mask in $O(\#\text{primitives})$, eliminating the symmetric-traversability
> assumption while keeping median repair times within 2× of the current system.

**RQ2 — Edit-capable traversal.** For *traversal* tasks (not construction), is position-space
search with edit-costed edges and overlay commitment sound *in practice*, and where does the
"sane starting point" relaxation (Baritone's assumption) actually fail?
> **H2:** Traversal edits are few and monotone in >95% of natural terrain queries; failures
> concentrate in enclosed/vertical scenarios and are detectable at execution time, making
> optimistic position-space planning + repair strictly better than joint-space search under a
> real-time budget.

**RQ3 — Decomposed construction planning.** Does the decomposition *(place-order dependency DAG +
dynamic-connectivity safety oracle + precedence-constrained routing)* produce complete, non-self-
trapping build plans, and to what schematic scale (target: $128^3$)?
> **H3:** The decomposition avoids all self-trapping failure classes documented for Baritone's
> builder, with preprocessing $O(n \log n)$-ish in schematic volume and per-placement decisions in
> $O(\log^2 n)$ (Holm–de Lichtenberg–Thorup bound).

**RQ4 — Unified decomposition engine.** Can one dependency-decomposition engine (AND/OR DAG over
resources + partial-order scheduling) serve building, crafting, and stash logistics as domain
plugins?
> **H4:** Recipe trees, place-order graphs, and transport plans are instances of one
> precedence-DAG structure differing only in node semantics and feasibility predicates; a shared
> engine reduces per-domain code to feasibility predicates + cost models.

**RQ5 — Approximate spatial hierarchy as estimator.** Does a cheap chunk-level connectivity
abstraction (flood-fill walkability, allowed to be wrong) materially improve (a) long-range
heuristic quality for D* Lite and (b) task-level route-cost estimation, without correctness
impact since the fine planner verifies everything?
> **H5:** $h = \max(h_{\text{euclid}}, h_{\text{abstract}})$ reduces expanded nodes by ≥5× in
> maze/cave scenarios; abstract cost estimates rank task alternatives correctly ≥90% of the time
> at <1% of full-search cost.

**RQ6 — Simulation-in-the-loop execution.** Do tick-accurate forward-simulated maneuvers with
explicit *entry envelopes* (position/velocity preconditions checked at runtime) outperform
reactive control for parkour-class movement?
> **H6:** Envelope-checked scripted maneuvers achieve ≥95% single-attempt success on a standard
> parkour gauntlet where reactive PID steering achieves <50%, at equal planning budget.

**RQ7 — Lifelong memory (carried over from the paper's future work).** Can a Node Relevance
Score (spatial proximity, temporal recency, pathing utility, topological criticality) bound the
lazy graph's memory over hours-long sessions without measurable path-quality loss?

**RQ8 — Inline maneuver discovery (F3).** Can arbitrary-angle maneuver discovery run cheaply
enough to be invoked *during search* (per candidate node) rather than only offline?
> **H8:** A **collision-guided yaw sweep** — simulate a jump at a candidate yaw; on horizontal
> collision, compute analytically the minimum yaw increment for all four AABB corners to clear
> the furthest offending block corner, thereby eliminating an entire yaw interval per simulation
> (often 90°+), rather than stepping by fixed increments — combined with trigger gating
> (overhang/ledge nodes only; early exit on >2-block falls; refiner handles the straight cases)
> bounds simulations per node by the number of local obstacle corners, making inline discovery
> affordable at graph-expansion time.

**RQ9 (exploratory) — Reach-set local planning.** The player's dynamics form a hybrid system:
time and collisions are discrete (20 Hz ticks), but the yaw input is continuous, so the per-tick
reachable set of $(\mathbf{x}, \dot{\mathbf{x}})$ is a continuous region. Can forward
reachability analysis — propagating this set tick by tick under all inputs, partitioning it at
collision constraints, and subtracting previously-reached regions so complexity scales with the
*frontier surface* rather than the volume — serve as a **complete** local planner ("auto-TAS")
for short corridors between topological corners of the coarse route?
> **H9:** Reach-set propagation is tractable for corridors of ≤ ~20–40 ticks and strictly
> dominates sampling-based discovery on contorted parkour (it cannot miss a connection);
> beyond that horizon it is intractable and sampling remains the production path. Independent of
> production use, the reach-set planner yields *ground-truth optimal* local trajectories for
> evaluating F1/F2 and calibrating primitive cost models.

---

## 4. Proposed Architecture

### 4.1 Layer stack

```
L3  TASK PLANNER            HTN decomposition over symbolic tasks; precedence-DAG
    (secs–mins horizon)     domain engines: Build / Craft / Logistics; connectivity
                            safety oracle; emits Goals + overlay commitments + budgets
─────────────────────────── interface: Goal algebra ▼ / typed results + costs ▲
L2  ROUTE PLANNER           Lazy D* Lite over motion-primitive graph; position-keyed
    (100ms–1s horizon)      nodes; edges include costed break/place actions evaluated
                            against the active WorldView overlay
─────────────────────────── interface: primitive edges ▼ / edge invalidations ▲
L1  MANEUVER LAYER          Motion-primitive library (template data); local parkour
    (10–100ms horizon)      chain solver (sim-sampled macro-edges); path refinement
                            (existing PathRefiner, simulation-validated shortcuts)
─────────────────────────── interface: control scripts + envelopes ▼ / deviations ▲
L0  CONTROL                 Per-primitive executors: steering/PID for walk segments,
    (per tick)              scripted controllers for maneuvers; rotation/interaction
                            controller (look, place, break, swap)

SHARED SUBSTRATE            WorldView (base snapshot + copy-on-write edit overlays,
                            int-encoded blockstates, precomputed trait tables);
                            hypothetical placement engine; scoped invalidation bus
```

### 4.2 The WorldView substrate (keystone)

Every layer asks "what is the world at plan step $t$?" — edit-edge costs (L2), reach-feasibility
predicates (L3), shortcut validation (L1). One abstraction serves all:

- **Base snapshot**: observed world, updated from chunk/block events.
- **Overlay stack**: ordered edit deltas, copy-on-write per chunk section, cheap to fork —
  a plan's hypothetical future is a fork; adopting the plan commits the overlay.
- **Int-encoded blockstates + trait tables** (`BlockStateCachedData[]`: isAir, canWalkOn,
  canPlaceAgainst, support shape, hardness): the hot path never touches Minecraft objects;
  version migration touches one populate function (leijurv's design, adopted wholesale).
- **Fixed-point tick-denominated costs** shared by all layers, so L3 can coherently sum route
  time + action time + crafting time, and cost comparisons are deterministic (no `0.99999` class
  bugs).

**Design principle — unified change semantics:** *the planner treats its own future actions as
predicted world changes.* Observed block changes and committed overlay edits flow through the
same invalidation bus into the same D* Lite edge-update machinery (Algorithms UpdateEdge /
Invalidate in the paper), differing only in a provenance tag. Aborting a task rolls back its
overlay, which re-emits inverse invalidations. This removes any special-casing between "the world
changed" and "I changed the world."

### 4.3 Motion primitives as data (L1 → L2 contract; addresses RQ1)

A primitive is a value, not code:

$$p = (\Delta_{\text{anchor}},\ M_{\text{clear}},\ M_{\text{support}},\ c(\cdot),\ \text{executor},\ \text{envelope})$$

— a displacement, voxel masks of required-clear and required-support cells relative to the start
anchor, a cost model, an executor reference, and an entry envelope (admissible entry
position/velocity window). Then:

- $\text{Succ}(\mathbf{u}) = \{\mathbf{u} + \Delta_p : \text{masks satisfied at } \mathbf{u}\}$,
- $\text{Pred}(\mathbf{v}) = \{\mathbf{v} - \Delta_p : \text{masks satisfied at } \mathbf{v} - \Delta_p\}$ —
  the *same* mask stamped at the shifted anchor.

Predecessor enumeration — the wall that forced the paper's symmetric-traversability assumption —
becomes a constant-cost template stamp. Asymmetric costs (cheap drop, expensive climb) are now
representable. Additional dividends:

- **Automatic invalidation regions**: `affectedNodes(changedBlock)` is derived as the union of
  inverse-stamped mask extents over all primitives, replacing the hand-maintained ±2 box in
  `WalkingMovementModel.affectedNodes` — correctness by construction as primitives are added.
- **Executor modularity**: each primitive owns its controller (the minecraft-pathfinding
  provider/executor split), so adding a movement type never touches the search core.
- **Edit primitives**: break/place become primitives whose masks reference the overlay and whose
  costs include tool-dependent break time and material availability (queried from $I$).

### 4.4 Momentum without 6D search (addresses RQ6, RQ8, RQ9)

Velocity is *not* added to node identity. Momentum-dependent movement is confined to
**macro-edges**: when the route planner encounters a gap region that no single primitive crosses,
it invokes a local **parkour chain solver** — sampling control sequences through the
tick-accurate physics simulation (pruned by known jump-distance tables) — which returns an entire
validated chain as one edge carrying a control script and an entry envelope. The executor checks
the envelope at runtime; violation triggers repair rather than blind execution.

**Discovery, not just validation (RQ8).** The solver has two jobs: validating maneuvers the
graph already hypothesizes, and *discovering* F3 connections the graph cannot see. For the
latter, the collision-guided yaw sweep (H8) replaces naive angular stepping: each failed
simulation eliminates the whole yaw interval that provably hits the same block corner, so a
handful of simulations covers 360°. Trigger gating keeps this off the hot path: only invoked at
overhang/ledge nodes, with fall-depth early exits, since the refiner already covers straight
sprint-jumps.

**Landing-anchored enumeration.** Candidate maneuvers are enumerated per *landing* anchor rather
than per takeoff (leijurv's "meet in the middle": compute once per landing spot instead of once
per jump spot). This is the continuous analogue of the inverse-mask template of §4.3 — and it is
not merely an optimization: D* Lite expands backward from the goal, so predecessor-directed
(landing-anchored) discovery is the search's native direction.

*Accepted limitations:* chains are not discovered across macro-edge boundaries (no global 6D
optimality), and maneuvers requiring multi-block inertia buildup (ice runways — the exception
noted in C1) need region-scoped handling. If evaluation shows either matters, the fallback is a
bounded context tag on nodes ($V \subseteq \mathbb{Z}^3 \times C$, $|C|$ small momentum classes)
— deferred to future work.

### 4.5 Two regimes for world-editing movement (addresses RQ2, RQ3)

- **Traversal regime** (goal is elsewhere; edits are instrumental): position-keyed search with
  edit primitives, Baritone-style optimistic node identity, edits committed to the overlay on
  path adoption. Unsound in adversarial cases; H2 claims those are rare, detectable, and
  repairable. The soundness boundary is characterized empirically (WP4).
- **Construction regime** (edits are the goal; movement is instrumental): leijurv's
  decomposition inside a bounded region —
  1. *Partial order*: place-order dependency graph (6 bits/pos) → Tarjan SCC collapse → DAG →
     scaffolding insertion until a single root component exists;
  2. *Safety oracle*: dynamic connectivity (Euler tour forests + HDT levels) over the navigable
     surface, with augmented per-component remaining-work counters, answering "does this placement
     trap me or orphan unfinished work?" in $O(\log^2 n)$;
  3. *Routing*: precedence-constrained stance sequencing (SOP-shaped; greedy + local improvement),
     with L2 as the movement cost service. Greedy is not a shortcut but the *correct* choice:
     any search over placement orderings faces a $2^{\#\text{blocks}}$ state space, and even the
     restricted problem (visit N placement stances in a plane, minimum walking) generalizes TSP —
     the NP-hardness was independently concluded in the leijurv/btrekkie exchange. Optimality
     budget goes into the safety oracle and local improvement, not global search.

  The route planner is a *service* to the builder here, not the planner of record.

  **Implementation evidence (de-risks the oracle).** btrekkie's `dynamic-connectivity` +
  `RedBlackNode` libraries provide exactly this structure, with a written potential-function
  proof that RB-tree `split` is $O(\log n)$ (not $O(\log^2 n)$; see issue #4, resolving the
  concern that `removeEdge` degrades to $O(\log^3 n)$), field-tested on Minecraft-shaped grid
  workloads in Baritone's `builder-2` branch. Adopt, don't reimplement. Two transferable
  lessons: (a) splay trees are simpler but require splaying on *every* access including
  `connected()` queries, and showed pathological imbalance (~height 150) on grid workloads
  without it — keep as fallback only; (b) Baritone's `NavigableSurfaceTest` corpus ("place as
  many blocks as possible without cutting off the path back to start") transfers directly as our
  oracle acceptance suite, and we should export realistic operation traces from S5 runs to feed
  upstream optimization (btrekkie's `optimization_ideas.txt`).

*Noted gap in the source design:* the README's decomposition assumes no breaking; scaffold
*removal* (and repair of griefed regions) reintroduces reversible actions and breaks the pure-DAG
structure. Treated as an explicit extension (WP6.3), likely via a second "deconstruction pass"
DAG rather than a unified reversible planner.

### 4.6 Task layer: HTN over precedence-DAG domains (addresses RQ4)

**Choice: HTN (SHOP2-style, with numeric resource fluents) over GOAP.** Rationale: Minecraft's
domain knowledge is rich, hierarchical, and human-authorable (recipes, building procedures,
mining strategies) — HTN methods encode it directly; GOAP-style backward chaining handles numeric
inventory quantities poorly and rediscovers known procedures at runtime. An LLM-guided
decomposition layer (Voyager-style) remains an optional L4 for open-ended objectives; the core
stays deterministic and offline-verifiable.

Unification claim (H4): recipe trees, place-order graphs, and stash transport plans are all
AND/OR precedence DAGs over typed resources; one engine handles decomposition, partial-order
maintenance, and incremental repair (placed blocks retire nodes; a creeper hole injects nodes;
consumed items propagate through recipe nodes). Domain plugins contribute only feasibility
predicates (e.g., the TAMP-style geometric predicate "∃ stance reaching face $f$ of $\mathbf{v}$
in overlay state $S$") and cost models.

**Goal algebra.** L3 addresses L2 exclusively through goals: `GoalBlock`, `GoalNear`,
`GoalRegion`, `GoalComposite` (disjunction with shared search), and crucially **stance goals**
(position set × look-direction constraint, generated by the placement engine for "see face $f$ of
$\mathbf{v}$"). Each goal contributes an admissible heuristic term. The existing 25-line
`TraversalGoal` grows into this algebra.

**Interface contract.** Downward: `(Goal, WorldView fork, tick budget)`. Upward: typed results —
`Reached(cost)`, `Blocked(frontier evidence)`, `BudgetExceeded(partial)` — plus cheap cost
*estimates* (from the L5 abstraction, §4.7) for alternative ranking. Failures route to the layer
that owns the violated assumption; every layer replans incrementally at its own timescale.

### 4.7 Approximate spatial hierarchy (addresses RQ5)

A chunk-section-level connectivity graph built by cheap walkable-column flood fill — explicitly
*allowed to be wrong* because it is used only as (a) one input to the heuristic
$\max(h_{\text{aniso}}, h_{\text{abstract}})$ for D* Lite (where $h_{\text{aniso}}$ is the
library-derived anisotropic heuristic of theory note T1, which replaces the paper's
Euclidean/$v_{\max}$ form; both frozen per query, so $k_m$ machinery is untouched), and (b)
L3's route-cost estimator for ranking task alternatives, and (c) frontier generation for
exploration under partial observability. This sidesteps the paper's (valid)
objection to HPA*-style hierarchies: no correctness ever depends on the abstraction.

---

### 4.8 Integration substrate: existing assets on master (v0.5)

The plan targets Lambda's current master (MC 1.21.11, Fabric/Kotlin). A survey of master shows
substantially more of the stack exists than the plan assumed when written against the
pathfinder branch:

| Plan component | Existing asset on master | Assessment |
|---|---|---|
| L3 task shell | `task/` — tree-structured `Task<Result>` DSL with typed results, parent/child, pause/fail handlers, tick-driven; concrete tasks (`BuildTask`, `AcquireMaterialTask`, `ContainerTransferTask`, …) | The HTN shell's skeleton exists and is idiomatic here; WP6.1 becomes "grow the precedence-DAG engine + goal algebra into this" rather than greenfield |
| Hypothetical placement engine (was the #1 risk) | `interaction/construction/simulation/` — `BuildSimulator`, `BreakSim`/`InteractSim`, placement pre/post processors per block property | **Partially exists.** WP6.2's grind is continuing coverage, not starting it; risk downgraded |
| Schematic layer | `interaction/construction/blueprint/` (`StaticBlueprint`, `PropagatingBlueprint`, `TickingBlueprint`) + Litematica dependency | S5 corpus loading and build-goal representation exist |
| L0 interaction controller | `interaction/managers/` (rotating, breaking, interacting, hotbar, inventory) | Exists; L0 movement executors are the missing half |
| L2/L1 movement | **Missing on master** — modules (`HighwayTools`, `Printer`, `StashMover`) currently delegate to the Baritone dependency | This is precisely what WP1–WP4 build; Baritone remains the interim engine and the benchmark baseline |
| Reference implementations | Lazy D* Lite core/refiner/executor + pathing unit tests on `feature/pathfinder-new` (reference-only branch); Baritone source at `/home/constructor/Git/baritone/` | Ported through the WP0 harness (see `benchmark-harness-spec.md` §5), not merged wholesale |

Two consequences. First, the integration story is concrete: the new planner replaces the
Baritone delegation seam inside existing modules, giving an incremental migration path
(module-by-module) with the old engine as fallback. Second, WP6's ordering assumption
("placement engine is the biggest grind") is stale — the grind is already underway in
production code, so WP6.2 should *inventory coverage* of the existing simulator against the S5
corpus before writing anything new.

## 5. Evaluation Methodology

### 5.1 Infrastructure

Reproducible benchmark harness: headless server + scripted client, seeded worlds, scenario
definitions as data, full metric logging (JSON lines), CI-runnable. *This is WP0 — it precedes
everything, because every hypothesis above is falsifiable only with it.*

### 5.2 Scenario suites

| Suite | Contents | Exercises |
|---|---|---|
| S1 Static navigation | Open plains, dense forest, maze (from the paper) + caves, cliffs | RQ1, RQ5 baseline continuity |
| S2 Dynamic navigation | Scripted block changes on/near path; chunk-load reveals | Repair latency (paper's replanning analysis, extended) |
| S3 Parkour gauntlet | Community jump-map corpus: 1–4 block gaps, momentum chains, neos, *arbitrary-angle jumps a 45°-quantized graph cannot represent* (F3 probes) | RQ6, RQ8, RQ9 |
| S4 Edit traversal | Walled goals, pit escapes, mineshaft descent, nether tunneling | RQ2 (incl. adversarial soundness probes) |
| S5 Construction corpus | Schematics from trivial (solid cube) to hostile (slabs/stairs/torches, overhangs, floating sections), up to 128³ | RQ3, placement engine coverage |
| S6 Task integration | "Obtain full iron gear from nothing", "build house incl. material acquisition", stash round-trips | RQ4, end-to-end |
| S7 Endurance | Hours-long mixed operation | RQ7 memory bounds |

### 5.3 Metrics

- **Planning**: per-tick compute time distribution (median, p99 — the paper's methodology),
  expanded nodes, repair latency after change, memory (nodes/edges resident).
- **Path quality**: cost ratio vs offline-optimal (where computable), path churn (plan stability
  under noise).
- **Execution**: single-attempt maneuver success rate, deviation-triggered repairs per 100 m,
  falls/deaths.
- **Construction**: correctness (% blocks matching schematic), scaffold overhead ratio,
  self-trap incidents (target: 0), time-to-completion.
- **Task**: end-to-end completion time, replan count, alternative-ranking accuracy vs ground
  truth (for H5).

### 5.4 Baselines and ablations

Baselines: Baritone (navigation + builder), mineflayer-pathfinder, current NeoLambda Lazy D*
Lite (continuity baseline). Ablations, each targeting one hypothesis: procedural successors vs
primitive templates (H1); abstract heuristic on/off (H5); envelope-checked scripts vs reactive
PID on S3 (H6); connectivity oracle vs naive flood-fill recheck (H3, also measures the $\log^2$
claim); overlay commits vs plan-blind costs on S4 (H2); NRS pruning on/off on S7 (RQ7);
collision-guided yaw sweep vs fixed-increment sweep vs offline jump tables (H8, measuring
simulations per node and connections found).

**Sufficiency studies (C1).** Two dedicated experiments close the conjecture's open clauses:
- *F1 study:* hand-tuned vs simulator-calibrated primitive costs — measure route changes and
  arrival-time error against simulated ground truth.
- *F2 study (reframed by theory note T5):* topology flips are now *known to exist* (the T5
  gadget) but their damage is bounded by the lattice constant on lattice-passable instances.
  The study therefore measures (a) the *typical* refined-vs-optimal gap on natural terrain
  (expected ≪ the ~8–13% worst case), using reach-set optima from RQ9 as ground truth, and
  (b) the frequency of lattice-impassable corridors — which count as F3 discovery work, not F2.

---

## 6. Work Packages

Dependencies: WP-T (theory) precedes and runs parallel to everything; WP0, WP1 first
(substrate); WP2 → WP3 → WP4 (movement track); WP5 parallel after WP1; WP6 requires WP4 + WP5;
WP7 orthogonal, after WP2; WP8 exploratory, after WP3.1 (simulator), feeding the F1/F2 studies
but never blocking the production track.

**WP-T — Theory Track (pre-implementation; deliverable: a theory note per item).**
Worked notes live in **`planner-theory-notes.md`**; headline outcomes: T1 yields the
library-derived anisotropic heuristic (and flags the current Euclidean/$v_{\max}$ as
inadmissible once jump primitives land); T5 proves F2's damage is bounded by the lattice
constant (≤ ~8–13%, conditional on lattice-passability — impassable corridors are F3 by
definition); T7 proves rotation equivariance, reducing H8 to one simulation per input pattern
plus a geometric yaw sweep; T8 delivers the non-interference certificate with two failure
channels proven impossible. Item statements:

- **T1 — Heuristic admissibility under macro-edges.** $h = \|\cdot\|_2 / v_{\max}$ remains
  admissible only if $v_{\max}$ is the global maximum over *all* primitives and maneuvers
  (sprint-jump exceeds walking speed). Prove admissibility with the corrected $v_{\max}$; state
  the consistency requirement for $h_{\text{abstract}}$ (optimistic abstract edge costs ⇒
  admissible; freeze per planning episode ⇒ consistent w.r.t. a fixed goal).
- **T2 — Correctness under gated edge discovery.** Discovered maneuver edges enter the graph as
  cost drops from $\infty$ — natively supported by `UpdateEdge`. What needs proof: with
  discovery gated to expansion time at trigger nodes (overhangs), the graph sequence is
  *discovery-monotone* (edges only appear, never spuriously), and D* Lite termination and
  correctness w.r.t. the *discovered subgraph* are preserved. Also state the completeness
  caveat: optimality is relative to discovered edges, quantified by the S3 F3-coverage metric.
- **T3 — Envelope algebra as funnel composition.** Formalize entry envelopes using the funnel
  formalism (Majumdar & Tedrake) over Frazzoli's Maneuver Automaton semantics: a maneuver is
  valid to chain if its reachable exit set, inflated by server-divergence bound $\delta$, is
  contained in the successor's entry envelope. H6 restated: envelope-checked execution =
  runtime funnel-membership monitoring.
- **T4 — Overlay/plan-commit invariant preservation.** Prove that commit and rollback, expressed
  as sequences of `UpdateEdge` calls, preserve D* Lite's invariants (they are ordinary edge-cost
  updates); define the conflict rule — an observed change contradicting an overlay entry
  invalidates that entry and emits a typed notification to the owning task — and show it cannot
  leave the graph inconsistent with either world.
- **T5 — F2 flip gadget.** Write down the adversarial counterexample showing angular
  quantization *can* change route topology: two corridors to the goal, one straight at ~30°
  (true cost $d$), one axis-aligned (cost $d'$), with $d < d' < d \cdot c_{\text{octile}}$ —
  the quantized planner picks the wrong corridor and no refiner can recover it. Consequence:
  C1-F2 is a claim about *frequency on natural terrain*, never impossibility; the F2 study
  measures that frequency.
- **T6 — Hardness of stance sequencing.** Formalize builder routing as Precedence-Constrained
  Generalized TSP (each placement = a *set* of feasible stances, choose one; precedence from
  the dependency DAG); reduction from SOP. Justifies greedy + local improvement; LKH-3 (SOP) /
  GLNS (GTSP) serve as offline ground truth for greedy-quality measurement in S5.
- **T7 — Reachability unification of H8 and RQ9.** The collision-guided yaw sweep is an *exact
  reachability computation along the single continuous input dimension (yaw) for a one-jump
  horizon*; RQ9 is its multi-tick generalization. Since yaw is the only continuous input and
  ticks/inputs are discrete, the per-tick reachable set is a finite union of 1-parameter
  families whose dimension grows by at most one per tick (capped at 6). Proposed hybrid scheme:
  zonotope-style over-approximations prune yaw intervals; simulation provides exact witnesses.
- **T8 — Non-interference certificate for the traversal regime (upgrades H2).** Lemma to prove:
  if no planned edit's affected region (its primitive mask extent) intersects the
  clearance/support masks of any *other* edge on the planned path, the position-keyed plan is
  sound (executes exactly as costed). The check is a static mask-overlap test over the path —
  cheap. This converts Baritone's folklore relaxation into a **per-plan soundness
  certificate**: certified plans execute without edit-order surprises; uncertified plans get
  runtime monitors. The S4 study then measures how often natural plans certify.

**WP0 — Benchmark harness & instrumentation.**
Full spec: **`benchmark-harness-spec.md`** (three tiers on the existing Fabric client-gametest
source set; scenario DSL; JSONL metrics + regression gates; Baritone in-process baseline;
calibration pass for the theory-note constants). Exit: S1/S2 reproduce the paper's published
A*-vs-D*-Lite results within noise (WP0.4), calibration constants generated (WP0.6).

**WP1 — WorldView substrate.**
Int blockstate encoding, trait table population, snapshot + overlay stack (copy-on-write chunk
sections), fixed-point tick cost type, scoped invalidation bus with provenance. Exit: current
planner runs unchanged on WorldView with equal benchmark results; overlay fork/commit/rollback
property-tested.

**WP2 — Motion primitive library & search migration (RQ1/H1).**
Template schema; port existing walk/step/gap moves; add drops and asymmetric moves (previously
impossible); derive `affectedNodes` from masks; D* Lite Pred/Succ via template stamps. Search
core made pluggable behind the primitive-graph interface; truncation (TD* Lite) and lazy edge
evaluation (LIS semantics) integrated into the reference core; MPGAA* and D* Extra Lite as
baselines (§8.1). Exit: H1 measured on S1/S2 including the search-core shootout; drop-capable
paths demonstrably found.

**WP3 — Maneuver solver & envelope execution (RQ6/H6, RQ8/H8).**
3.1 Tick-accurate forward simulator as a service (extending the movement-sim refinement work);
simulator-calibrated primitive cost models replacing hand constants (F1).
3.2 Arbitrary-angle discovery: collision-guided yaw sweep with analytic interval elimination,
landing-anchored enumeration, trigger gating; caching per landing anchor.
3.3 Parkour chain solver emitting macro-edges with control scripts + entry envelopes;
per-primitive executors; runtime envelope monitor.
Exit: H6 and H8 on S3; F1 study complete.

**WP4 — Edit-capable traversal (RQ2/H2).**
Break/place primitives costed against overlay + inventory; overlay commitment on path adoption;
execution-time soundness monitors; characterization of failure cases. Exit: H2 on S4 with a
documented soundness boundary.

**WP5 — Spatial abstraction & cost estimation service (RQ5/H5).**
Chunk-section flood-fill connectivity; incremental maintenance on chunk events; heuristic
integration; estimator API for L3; frontier generation. Exit: H5 on S1-maze/caves + estimator
accuracy study.

**WP6 — Task layer (RQ3, RQ4 / H3, H4).**
6.1 Generic precedence-DAG engine + HTN shell + goal algebra + typed result protocol.
6.2 Build domain: place-order graph extraction, hypothetical placement engine (scoped whitelist:
full blocks, slabs, stairs, torches; skip-with-warning otherwise), Tarjan + incremental SCC
maintenance, scaffolder, HDT dynamic connectivity (fuzz-tested against a naive oracle),
stance sequencing. Exit: H3 on S5.
6.3 Craft/logistics domains on the same engine; scaffold-removal extension (deconstruction
pass). Exit: H4 on S6.

**WP7 — Lifelong operation (RQ7).**
Node Relevance Score pruning (from the paper's future work), memory ceilings, exploration
policies under partial observability. Exit: S7 endurance runs with bounded memory, path quality
within 5% of unpruned.

**WP8 — Reach-set local planner (RQ9/H9; exploratory, stretch).**
Prototype forward reachability over the hybrid player dynamics on bounded corridors: reachable
$(\mathbf{x}, \dot{\mathbf{x}})$ set representation, per-tick propagation with collision
partitioning, frontier subtraction. Compare against the sampling solver (WP3) on S3 subsets;
produce ground-truth optima for the F1/F2 sufficiency studies. Explicitly allowed to fail as a
production component — its evaluation value stands alone. Exit: tractability boundary
characterized (corridor length vs compute); F2 study delivered.

---

## 7. Risk Register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Hypothetical placement engine is an open-ended per-block grind | Medium (was High) | Blocks WP6.2 | Master's `BuildSimulator` + placement preprocessors already cover part of this (§4.8); inventory coverage against S5 corpus first; strict whitelist; skip-with-warning |
| HDT connectivity implementation subtlety | Low (was Medium) | WP6.2 delay | Adopt btrekkie's proven implementation (O(log n) split proof, field-tested in Baritone builder-2) rather than reimplementing; fuzz against naive BFS oracle; reuse NavigableSurfaceTest corpus; splay-tree fallback documented with its access-pattern caveat |
| Inline maneuver discovery too slow per node | Medium | Search latency or F3 coverage on S3 | Trigger gating (overhang-only), landing-anchor caching, analytic interval elimination (H8); fallback: offline jump tables |
| Reach-set planner intractable | High | None on production path | WP8 is explicitly exploratory; sampling solver is the production path; even a short-horizon-only result retains full evaluation value |
| Simulation divergence from server (latency, anticheat, mob pushes) | Medium | Execution success | Envelopes as runtime guards; deviation → repair, never blind replay; measure divergence explicitly |
| Macro-edge non-compositionality misses long parkour chains | Medium | Path quality on S3 | Documented fallback: bounded momentum-class node context |
| Overlay/graph consistency bugs (planned vs observed edits) | Medium | Correctness everywhere | Single invalidation bus + provenance; property-based tests in WP1 |
| Traversal-regime unsoundness worse than H2 predicts | Low | Architecture revision | S4 adversarial probes early in WP4; escape hatch: bounded joint-space search in enclosed regions |
| Cost model miscalibration across layers | Medium | Bad task ranking | Single tick-denominated fixed-point unit; calibrate against simulator ground truth |

---

## 8. SOTA Review: Challengers and Adopted Upgrades

An honest audit of each architectural choice against the strongest known alternative, including
challengers to Lazy D* Lite itself. Verdicts: **keep**, **upgrade** (compose the improvement
into our choice), or **benchmark** (credible replacement; decide empirically).

### 8.1 The L2 search core — D* Lite has real challengers

| Challenger | Claim | Verdict |
|---|---|---|
| **MPGAA*** (Hernández et al. 2015/2019) | Simpler than D* Lite; outperforms it in many indoor/outdoor nav scenarios ("Making A* Run Faster than D* Lite") | **Benchmark.** Its wins concentrate where freespace assumptions hold and goal distance shrinks; our lazy expensive successor generation changes the trade-off. Add as a search-core baseline in WP2. |
| **D* Extra Lite** (Przybylski 2017) | Search-tree cutting + frontier-gap repair; outperforms optimized D* Lite *and* MPGAA* in 2D benchmarks | **Benchmark.** Reinitialization-by-cutting may interact poorly with lazy graphs (cutting discards expensively-computed successor sets); this is precisely the kind of question WP2's pluggable search core answers. |
| **Truncated D* Lite / Anytime Truncated D*** (Aine & Likhachev, SoCS'13, AIJ'16) | Bounded-suboptimal repair: truncate cost propagation when the current path is provably within $\epsilon$; ATD* adds anytime inflation | **Upgrade.** Composes with D* Lite rather than replacing it, and directly attacks our budgeted-repair use case (we currently exit on time budget with *no* quality bound; truncation gives the bound). Highest-value single algorithmic upgrade available to us. |
| **Lazy Incremental Search (LIS)** (Lim et al. 2022) | Combines LazySP-style *lazy edge evaluation* with incremental repair, for graphs where edge evaluation is expensive | **Upgrade.** Our edge evaluation *is* expensive (physics simulation). Adopt the semantics: sim-validated edges carry optimistic costs until evaluated, evaluation is deferred to path-candidate time, incremental repair reuses evaluations across replans. This is the same lazy philosophy as our lazy graph, pushed from node instantiation down to edge *evaluation* — a natural and probably publishable extension of the Lazy D* Lite idea. |
| Real-time search (LSS-LRTA*, RTAA*) | Bounded per-step planning | **Keep ours.** Agent-centric local search suffers local minima the paper's async global repair avoids; note as baseline only. |
| JPS/Anya-family symmetry breaking | Massive speedups on uniform grids | **Reject with reason.** Non-uniform action costs, support constraints, and physics successors break the symmetry assumptions (grid-cost uniformity) these methods require. |

Design consequence: **the search core becomes a pluggable component** behind the
primitive-graph interface (Succ/Pred/UpdateEdge), with D* Lite (+truncation, +anytime
inflation, +lazy edge evaluation) as the reference, and MPGAA* / D* Extra Lite as WP2
benchmark baselines. The paper-validated implementation is the incumbent, not the assumption.

### 8.2 Heuristics — one principled upgrade path

`max(h_euclid, h_abstract)` (§4.7) requires $h_{\text{abstract}}$ admissible (optimistic
abstract costs) and frozen per episode (T1). If we later want *aggressive, inadmissible*
guidance (learned heuristics, dense traffic priors), **Multi-Heuristic A*** (Aine et al. 2016)
is the principled frame — inadmissible queues anchored by an admissible one, bounded
suboptimality — though its incremental variants are immature; treat as a future option, not
plan-of-record.

### 8.3 The task layer — HTN stands, borrow one idea from modern TAMP

LLM-driven Minecraft agents (Optimus-2/3 2025–26, MineEvolve, JARVIS-1/DEPS, MrSteve) dominate
recent literature but target open-ended instruction following with learned policies —
orthogonal to a deterministic, verifiable bot core; they remain optional L4. Classical TAMP
progressed via **lazy/policy-guided skeleton search** (LAZY, 2022; COAST, 2024): keep a single
integrated search over action skeletons that becomes progressively geometrically informed as
feasibility samples arrive. **Adopt this discipline at L3**: stance-existence predicates are
optimistic by default, verified lazily at plan-candidate time, with failures refining the
skeleton search — the same lazy-evaluation philosophy again, now at the symbolic level. The
architecture is thus uniformly lazy at all three levels: node instantiation (L2 lazy graph),
edge evaluation (LIS semantics), and geometric feasibility (lazy streams).

### 8.4 Maneuver layer — adopt formal semantics, keep the algorithms

Frazzoli's **Maneuver Automaton** (trim primitives + maneuvers with entry/exit conditions) and
Majumdar–Tedrake **funnel libraries** give the formal language for our macro-edges and
envelopes (T3). Kinodynamic sampling planners (BIT*/AIT*/EIT* family) are the generic SOTA but
are built for high-dimensional continuous control; our control space (few buttons × one
continuous yaw × 20 Hz) rewards the specialized collision-guided sweep instead. Keep, with the
funnel formalism as its correctness language.

### 8.5 Builder machinery — practical SOTA confirmed

HDT dynamic connectivity remains the right practical choice; newer theory (Huang et al.
amortized $O(\log n (\log\log n)^2)$; Kapron–King–Mountjoy worst-case randomized; subpolynomial
deterministic structures) trades constants and complexity for asymptotics we don't need at
$n \approx 10^6$. Offline dynamic connectivity (divide-and-conquer over the operation sequence)
would be simpler and faster but requires the operation sequence up front — ours is
query-adaptive (the planner's next edit depends on the previous answer), so online it is.
Stance sequencing gains LKH-3/GLNS as offline ground truth (T6).

## 9. Relation to Prior Work

Incremental search: D* Lite (Koenig & Likhachev 2002), LPA* (Koenig et al. 2004), LaCAS*
(Okumura 2024) — as surveyed in the source paper; challengers and composable upgrades per §8.1:
MPGAA* (Hernández et al.), D* Extra Lite (Przybylski 2017), Truncated Incremental Search /
Anytime Truncated D* (Aine & Likhachev 2016), Lazy Incremental Search (2022), LazySP-family
lazy edge evaluation, Multi-Heuristic A* (Aine et al. 2016). Maneuver formalisms: Maneuver
Automaton (Frazzoli et al. 2002), funnel libraries (Majumdar & Tedrake 2017). Lazy TAMP
skeleton search: LAZY (2022), COAST (2024). Motion primitives / state lattices (Pivtoraiko
& Kelly 2005); Hybrid A* (Dolgov et al. 2008) for continuous-state-in-discrete-node embedding.
Any-angle refinement: Thorpe 1984, Theta* (Daniel et al. 2010) — retained as post-processing per
the paper's argument. Task and Motion Planning: Garrett et al. (PDDLStream 2020) — the
reach-feasibility predicate here is a TAMP geometric predicate. HTN planning: SHOP2 (Nau et al.
2003). Dynamic connectivity: Holm, de Lichtenberg, Thorup 2001; practical implementation
btrekkie/dynamic-connectivity + RedBlackNode (with the O(log n) split argument from issue #4).
Reachability analysis of hybrid systems: Maler, *Computing Reachable Sets: An Introduction* —
theoretical basis for RQ9; Braam 2022 (LIACS thesis) analyzed the Minecraft jump space but
without a viable planning integration. Precedence-constrained routing: sequential ordering
problem literature. Minecraft systems: Baritone, mineflayer-pathfinder, minecraft-pathfinding
(GenerelSchwerz), leijurv's builder design notes and discussions; LLM agents (Voyager, 2023)
considered only as optional L4.

## 10. Expected Contributions

1. **Template-based motion primitives with inverse masks** enabling backward incremental search
   over asymmetric physics-based movement — resolving the source paper's central stated
   limitation (publishable as a direct sequel) — together with the **library-derived
   anisotropic heuristic** (T1): admissible and consistent by construction, self-maintaining as
   primitives are added, strictly dominating the isotropic form.
2. **A unified change-semantics model** (own future actions as predicted world changes through
   one invalidation path) for incremental planners that edit their environment.
3. **A per-plan soundness certificate (mask non-interference, T8) plus empirical
   characterization of the soundness boundary** of position-space planning with instrumental
   world edits — upgrading the Baritone relaxation from folklore to a checkable property.
4. **A generalization of dependency-DAG build planning** to crafting and logistics via a single
   precedence-decomposition engine with TAMP-style feasibility predicates.
5. **Resolution of the voxel-grid sufficiency conjecture (C1)** — F2 settled theoretically
   (topology flips exist but damage is bounded by the lattice constant on lattice-passable
   instances, T5), with dedicated studies for the typical-case gap and for F3 coverage as the
   sole remaining structural question.
6. **A collision-guided arbitrary-angle maneuver discovery algorithm** (analytic yaw-interval
   elimination with landing-anchored enumeration) cheap enough for inline use during search.
7. **A complete, evaluated layered TAMP architecture for voxel worlds**, with per-layer ablations
   — to our knowledge the first system spanning tick-accurate maneuvers to symbolic task
   planning in this domain.
