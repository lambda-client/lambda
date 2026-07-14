# High-Fidelity Parkour — Implementation Plan (v2)

*Companion to `planner-research-plan.md` (§4.4 macro-edges, RQ6/H6) and
`planner-theory-notes.md` (T3 envelope algebra, T7 rotation equivariance).
v2 (July 13 2026) replaces the v1 sketch, whose "validate the jump, launch from
the lip" framing was wrong: **the launch point and the entry velocity are one
coupled decision, and both belong in the plan.**

---

## 1. The mistake to avoid

A jump is not "an edge you validate and then fire". A jump is a choice of
**launch state**:

$$\ell = (s,\; v,\; g,\; \theta) \quad\text{—\; launch progress, entry speed, gait, heading}$$

and these are *coupled*. For one edge:

- A max-distance jump only works **fast and deep** (launch at the lip).
- A short hop only works **slow and early** — launch late/fast and you sail
  straight over a 1x1 pad. `simFallOffEdge` exists in minecraft-pathfinding
  precisely because on many descents the correct action is **not to jump at all**.
- In between, the feasible set is a **diagonal band**: the faster you arrive,
  the earlier you must leave.

Our `EntrySpeedEnvelope` models this band as an **axis-aligned box**
(`[vmin,vmax] × [smin,smax]`). That is why admission keeps failing: the box's
corners (fast+deep, slow+early) fall outside the band, `robustEntryBox` rejects
them, and a perfectly jumpable edge is discarded. Widening the box admits
launches that miss. **The box is the bug.**

And entry velocity is not a free variable: it is whatever the *previous* edge's
exit left behind. Speed therefore has to be planned **along the path**, not
sampled per edge.

### What the references actually do (and why neither suffices)

| | Baritone | minecraft-pathfinding |
|---|---|---|
| Jump set | cardinal, integer 2–4, `checkOvershootSafety` (assumes overshoot) | providers + `ParkourJumpHelper` |
| Launch decision | fixed rule | **menu, simulated live**: jump-here / jump-from-edge / fall-off-edge / back-up-then-jump |
| Where simulated | nowhere | **execution time**, `EPhysicsCtx.FROM_BOT` |
| Cost model | static ticks | static ticks (Baritone's) |
| Entry velocity in the plan | no | **no** |

Baritone is rigid. minecraft-pathfinding is *reactive*: its strategy menu is a
better dance, but still a dance at the lip, because A\* handed the executor a
takeoff without telling it how fast to arrive. We keep MCP's strategy menu and
its shared closed-loop controller, and we **move the decision into the plan.**

---

## 2. The model

### 2.1 LaunchSet — replaces the box envelope

For each jump edge, discovery computes not a box but a **table**: for each
launch progress $s_i$ (a small ladder, e.g. −0.2 … 0.75 in ~0.13 steps — a
player may stand with their centre up to ~0.8 past the block centre before
losing support), the admissible entry-speed interval per gait, plus the
predicted **exit state**.

```kotlin
data class LaunchSet(
    val samples: List<LaunchSample>,   // sorted by progress
    val strategies: Set<Strategy>,     // Jump | WalkOff | BackUpThenJump
)
data class LaunchSample(
    val progress: Double,              // blocks past the takeoff node centre
    val gait: Gait,                    // Sprint | Walk
    val speed: ClosedRange<Double>,    // stored velocity along the jump line
    val exitSpeed: Double,             // speed carried onto the landing  ← chaining
    val ticks: Double,                 // cost
)
```

Two properties make this the right object:

- It is **shape-faithful**: a diagonal band is representable; a box is not.
- It carries **`exitSpeed`**, which is what lets jumps *chain*. Today nothing
  knows what a landing leaves behind, so the next jump's entry is a surprise.

`WalkOff` (no jump input) is a first-class strategy, not an afterthought: it is
the correct, non-overshooting action for most descents.

### 2.2 Speed profile — the piece that does not exist today

Given the refined path, run the classical **backward–forward velocity-profile
pass** (racing-line generation) over the longitudinal coordinate:

1. **Limits per node.** For each node, an upper bound on speed:
   - at a jump takeoff: `max` over the LaunchSet of admissible entry speeds;
   - at a **turn**: the cornering limit — a 90° heading change at 0.28 b/t is
     not physically trackable, so the plan must slow *before* it (this is the
     measured defect: planned turn <15° costs 2.6 wasted ticks at the lip,
     45–90° costs 9.6);
   - at a landing: the exit speed the LaunchSet predicts.
2. **Backward pass:** propagate limits upstream through the max *deceleration*
   the player can achieve (drag + no-input coast + reverse input).
3. **Forward pass:** clamp by the max *acceleration* (walk/sprint accel from
   the calibrated `MoveRates`).

Output: a target speed at every node, a **gait schedule** (when to sprint), and
— at each jump — the **chosen launch sample plus the tick it fires on**.

This is what makes the jump reliable, and it is what kills the lip dance: the
executor never has to *discover* a launch state, because the plan already picked
a reachable one and told it how to arrive there.

### 2.3 One controller

`ManeuverController` — one pure object mapping `(state, target, gait) → inputs`
(aim, strafe correction, brake, sprint), driven by **all three** of: discovery's
validation sim, the executor's launch gate, and the executor's flight. Today the
steering is duplicated and divergent between `ManeuverDiscovery.simulateEntry`
and `PathfinderExecutor`, and *every* bug found this round was a divergence
between those copies (open-loop validation, no strafe correction, hardcoded
sprint). MCP gets this right via a shared `Controller`; we adopt it.

---

## 3. Work packages

Gated on the corpus (`ParkourChainGenerator`) and the rating matrix
(`JumpMatrix`). `chain-thin-*` (1x1 pads) green is the north star.

### P1 — One shared controller
Extract `ManeuverController` from the executor's steering/brake/gait logic;
rewrite `ManeuverDiscovery.simulateEntry` to drive it. No behaviour change
intended.
**Gate:** bench parity except for admissions that were open-loop artefacts.
*Without this, "validated" is meaningless — so it goes first.*

### P2 — LaunchSet replaces EntrySpeedEnvelope
Discovery emits the progress×speed×gait table + `exitSpeed`, and the
`Strategy` set (`Jump` / `WalkOff` / `BackUpThenJump`). Delete
`robustEntryBox`'s box-corner test in favour of per-sample robustness.
**Gate:** the 1x1 admissions that currently fail on box corners are admitted;
no admitted sample overshoots in simulation.

### P3 — Every jump edge is simulated
Delete `MoveTable.gapJump` and `WalkingMovementModel`'s gap branch; lower
`FLAT_MIN_DISTANCE` to 2.0 so discovery owns **all** gap jumps and step-ups.
`no-envelope(whole-band)` — the state in which the field log shows takeoffs at
`entry v = 0.000` — ceases to exist.
**Gate:** no jump edge without a LaunchSet; zero PLANNER/PHYSICS DISAGREEMENT
lines in a field run; through-block edges become unrepresentable.

### P4 — The speed profile (the core)
Backward–forward pass over the refined path producing target speed, gait
schedule, and per-jump launch sample + launch tick. The executor tracks the
profile; at a jump it checks the live state against the chosen sample and fires.
Re-derived each tick from the live state (receding horizon), so it cannot go
stale. If no sample is reachable from the live state → report infeasible →
replan. **Never** shape entry at the lip.
Then **delete `invalidMomentumApproach`**: its 15°-cosine "is there a straight
runway behind the takeoff" test is a world-geometry *proxy* for exactly the
entry state the profile now plans, and it is what deletes the
two-forward/one-up edge after any turn.
**Gate:** align/refusal ticks flat across turn buckets (today 2.6 → 9.6);
`entry-gap2-up1-elbow90/45` plan and pass; field entry speeds stop collapsing
to 0.05; the "Edge obstructed → penalty x8" cascade on good edges stops.

### P5 — Tube monitor
Per-tick membership in the LaunchSample's predicted tube; violation → recovery
primitive (stabilise, replan), never blind continuation. Airborne MPC becomes
the corrector *inside* the tube.
**Gate:** H6 ≥95% single-attempt; `chain-thin-*` green.

### P6 — Cost
T7 rotation equivariance: one simulation per (input pattern × entry-speed class),
reused across yaws by a geometric sweep; cache per landing anchor. Needed
because P2/P3 multiply sims per landing (bedrock 487 → 801 ticks today).
**Gate:** bedrock back under its tick gate; repair p50 unchanged.

---

## 4. Non-goals

- **Baritone's rigidity.** No cardinal-only jump set, no static costs, no
  `checkOvershootSafety`.
- **"Always jump from the edge."** Launch progress is an output of the profile.
  Deep for max-distance jumps; early for short hops; **not at all** for most
  descents (`WalkOff`).
- **Widening pads to pass tests.** `chain-thin-*`/`chain-rise-*` are the target;
  `chain-wide-*` is a control isolating pad forgiveness.
- **Entry shaping by executor choreography.** Rejected twice in this project
  (WP3.2 run-up/walk-back oscillation, 400% waste; P0 "never pre-empt the
  gates"). The lip dance *is* that failure. If the certified entry state is
  unreachable, report infeasible and reroute.

---

## 5. Why this order

P1 makes "validated" mean something. P2 fixes the *shape* of the contract, so
admission stops rejecting jumpable edges. P3 puts every jump under that contract
(no more `no-envelope`). P4 is the one the field logs have been screaming about
since the beginning — *the plan hands the executor takeoffs it can only reach by
destroying its own momentum, then blames the edge when the jump fails* — and it
is simultaneously what makes 1x1 chains, turn-then-rise, and short-hop accuracy
work. P5 is the H6 payoff; P6 pays the bill.
