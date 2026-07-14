# The Window Solver — Design

*How the agent decides, in real time, the actual movement sequence it will
execute: launch points, gaits, and speeds. Companion to
`parkour-fidelity-plan.md`. This is the "hard part".*

---

## 1. Shape of the system

```
L2  COARSE GRID          position-only, optimistic, cheap voxel masks.
    (whole route)        Fast. Decides TOPOLOGY. Costs are LOWER BOUNDS.
        │  corridor of coarse nodes
        ▼
L1  WINDOW SOLVER        searches the real (position × velocity × gait) state
    (next 3-4 jumps)     over the corridor. Decides the TRAJECTORY: where to
                         launch, how fast to arrive, walk vs jump vs walk-off.
                         Anytime; runs on the worker WHILE the agent moves.
        │  certified trajectory + commit point
        ▼
L0  CONTROLLER           one shared object. Drives both the solver's sims and
    (per tick)           the live executor. If they differ, nothing is validated.
```

The coarse layer must be **optimistic** (never overestimate cost, never reject a
physically-possible edge) and the window solver must be **sound** (never certify
a trajectory that does not land). Coarse mispricing is repaired by feeding the
solver's true cost back upward — the LazySP/LIS mechanism already in
`ManeuverDiscovery` (optimistic cost, revised upward on validation), generalized
from single edges to windowed trajectories.

---

## 2. The action set

### EVERY tick is simulated — walking included

This is not a jump solver. **The plan is a full tick-level rollout of the whole
trajectory**, walking as much as jumping. The grid supplies only two things:
*topology* and the *cost-to-go oracle* (§7b). It does not supply the path the
player walks.

That matters far beyond parkour, because a grid path is a bad walking path:

- **The lattice zig-zags.** An 8-connected route to a point at 22.5° alternates
  0°/45° steps. The refiner string-pulls it, but string-pulling is a *geometric*
  approximation of what a simulated pure-pursuit controller does natively.
- **Grid moves stop and turn; a player carries momentum.** A 90° corner taken at
  sprint is a real, simulated speed loss — so the solver *sees* it, and will
  either round the corner wide (keeping speed) or slow earlier, whichever
  simulates faster. Cornering behaviour is an *output*, not a hand-tuned rule.
  This is the direct answer to the field complaint that the planner emits
  direction changes "that do not align with real curve-taking dynamics".
- **The grid is conservative about geometry the player can actually pass.** The
  diagonal-walk template demands both flanking columns be clear; a 0.6-wide
  player at an arbitrary heading often fits where the lattice says it cannot.
  Simulation finds those; masks never will.
- **Sprint is a trajectory property.** Sprint is lost on collision and needs
  sustained forward input — where it is actually sustainable is a simulated
  fact, not a config flag.

So walking cost is **simulated ticks**, not `MoveCosts.CARDINAL`. The coarse
cost stays an optimistic lower bound (§7b); the solver computes the truth.

*Consequence:* `PathRefiner`'s corridor-shortcut machinery is subsumed. Its
sweep-validated string-pulling is a crude, geometry-only approximation of "roll
the controller forward and see where it goes". It gets deleted, not ported.

### What the solver actually branches on

It does **not** search raw per-tick inputs — that is a TAS search, and 4+ binary
inputs × 20 Hz explodes past ~20 ticks. The per-tick inputs are the
`ManeuverController`'s job; the solver simulates that controller and branches
only on the small set of *discrete* decisions:

| Action | Parameters | Notes |
|---|---|---|
| `Walk(target, gait)` | steer target (a corridor node, or a node **skipped/offset** to cut a corner) + gait | fully simulated. Corner-cutting and any-angle lines fall out of pure pursuit; the sim rejects a line that clips a wall |
| `Jump(launchTick, gait)` | which grounded tick to press jump | the launch *position* is an output, not an input — see §3 |
| `WalkOff` | — | descend with **no jump**. The correct, non-overshooting action for most drops |
| `BackUp(dist) → Jump` | retreat distance | only when no forward schedule reaches the entry band |

Branching stays small because the continuous work — steering, lateral
correction, braking — is delegated to the controller and *rolled out*, not
searched. A 20-block walk rollout is ~100 ticks of physics: trivially cheap.
The cost is in the discrete choices, and there are few of them.

The **approach schedule** is the small discrete set of longitudinal input
programs between two nodes:

```
{ full-throttle, coast(no forward), brake(reverse) } × { sprint, walk }
```
with at most one switch point. That is ~12 schedules; most are pruned instantly.

Everything else (yaw, strafe, mid-air correction, brake-on-approach-to-landing)
is **not searched** — it is the `ManeuverController`'s job, and the solver
simulates that controller. This is what keeps the branching tiny.

---

## 3. The key physical fact: launch progress is quantized

You cannot choose where to jump from. You can only choose **which grounded tick
to press jump on**, and the player moves `v` blocks per tick. So the available
launch positions are a **lattice with spacing `v`**:

```
sprint  v ≈ 0.28 b/tick   → launch positions 0.28 blocks apart
walk    v ≈ 0.21 b/tick   → 0.21 apart
creep   v ≈ 0.10 b/tick   → 0.10 apart
```

**Consequence, and this is the crux:** if the required launch correction is
smaller than one stride, *no choice of tick can fix it*. You must change the
approach so the lattice itself shifts. That is precisely what a human does when
they stutter-step before a long jump, and it is what the current code
approximates with `phaseAlignmentThrottle` — a hand-rolled, per-tick guess at a
problem that is properly a search over (schedule, tick).

So the true decision variable is the **pair**:

$$(\text{approach schedule } u,\; \text{launch tick } k)$$

`u` chooses the *phase and spacing* of the lattice; `k` picks a point on it.

---

## 4. "We landed badly — how do we change the jump-off?"

This is the question that makes the search *smart* instead of brute force.
Define the signed miss along the jump line:

$$e(s, v) \;=\; \text{landing}_\parallel - \text{target}_\parallel$$

### MEASURED (W0, `JumpJacobian`, flat open ground, simulator)

```
stride  walk   0.2159 b/tick        <- launch-lattice spacing
stride  sprint 0.2806 b/tick
dD/ds  = +1.000   (spread 0.000)    <- exactly linear, both gaits
dD/dv  = +5.29 blocks per (b/t)     (spread 0.00) <- exactly linear, both gaits
T_air  = 14 ticks, constant across all launches
monotone in s: yes (99/99)   monotone in v: yes (100/100)   220/220 landed
```

Three consequences, and they are stronger than assumed:

- **The miss function is not merely monotone, it is *exactly linear*** over the
  whole useful range: `D(s, v) = D₀ + s + 5.29·v` per gait. The Newton step is
  therefore *exact*, not an approximation.
- **`dD/dv` is 5.29, not the 8–12 I guessed.** Still decisive — a 0.02 b/t entry
  error moves the landing **0.11 blocks**, and a 1x1 pad has roughly ±0.3 of
  tolerance — but the correct number matters: to buy one sprint stride (0.28) of
  launch correction you must change entry speed by 0.053 b/t, which is a *large*
  change. This is exactly why the agent cannot fix a phase error by nudging the
  throttle at the lip, and why the approach must be planned.
- **`T_air` is constant** (14 ticks flat), because a jump returns to its launch
  height in a fixed time regardless of horizontal speed.

**Big win for the solver:** because `D` is closed-form in `(s, v)`, the launch-
tick search needs **one jump simulation per (edge, gait)** to calibrate `D₀`,
and then evaluating every candidate launch tick is *arithmetic*. Simulation is
demoted from "per candidate" to "calibrate once + verify the winner". The
verification sim remains mandatory — linearity holds on open ground and says
nothing about head bonks, mid-column pillars, or rising/descending air time,
which is precisely why the sim, not the model, is the decider.

*Still to measure:* rises (+1) and descents (−1…−3) change `T_air` and hence
`dD/dv`; the sweep must be repeated per rise class before W2 trusts the model.

That gives a **secant/Newton correction** instead of blind enumeration. From one
failed simulation with miss `e`:

```
overshoot by e  ⇒  launch e earlier         (Δs = −e)
                or arrive slower by e / T_air (Δv = −e / T_air)
```

The algorithm then becomes:

1. Simulate the jump from the nominal launch state. Get `e`.
2. If `|e| ≤ tol` → certified.
3. Else compute the **desired launch state** `(s*, v*)` from the Jacobian.
4. `s*` is generally *not on the lattice*. So:
   - Find the grounded tick `k` whose lattice point is nearest `s*` **under each
     candidate schedule `u`** — different schedules shift the lattice.
   - `e(k)` is monotone in `k` along a fixed schedule (later tick ⇒ further
     landing), so the best `k` is found by **bisection, not scan**.
5. Keep the `(u, k)` with the smallest `|e|` that also satisfies the landing
   tolerance and the tube.

Cost: a handful of jump sims per edge (typically 3–6), not the ~60 a blind
enumeration would need.

**Fallback if monotonicity fails** (head-bonk cases flatten the arc, and the
brake policy introduces a kink): the candidate set is small enough to enumerate
exhaustively — ~12 schedules × ~10 grounded ticks. So the Jacobian is an
*accelerator*, never a correctness dependency. Never let a heuristic be
load-bearing here; that is how the current gate forest happened.

**Turns need no special model.** If the agent arrives at a corner too fast, the
simulated controller simply fails to acquire the jump line and the candidate
misses. Cornering limits *emerge from the simulation* — we do not hand-write a
cornering-speed formula. This is the honest answer to "harsh direction changes":
the search will refuse them and slow down earlier, because a slower approach is
the only one that certifies.

---

## 5. Chaining: the window

A landing's exit state is the next jump's entry state. So the window is solved
as a **beam search over segment boundaries**:

```
state at boundary i:  (lateral+longitudinal offset on the landing, speed, gait)
```

- Discretize speed into ~0.02 b/t buckets and offset into ~0.15-block buckets;
  keep the cheapest few states per bucket (beam width 8–16).
- Depth: **3–4 jumps** — enough to cover momentum coupling (a jump that needs a
  fast entry constrains the two segments before it).
- Objective: minimize ticks. Hard constraint: every landing inside tolerance.

### Backward pass first (this is what a pure forward search gets wrong)

Before the forward beam, propagate constraints **backward** through the window:
for each jump, the admissible entry-speed interval (from §4's monotonicity);
then bound the speed permitted at the *previous* landing by what can be shed or
gained over the walk distance between them.

Without this, the forward search happily coasts, arrives at jump 3 too slow, and
fails — having already committed the agent. With it, the beam is pruned hard and
"you must still be at ≥0.15 when you land here" is known *before* you get there.

---

## 6. Safe-stop invariant (or we fall exactly like today)

We execute into territory the solver has not finished certifying. The rule:

> **The executor may never enter a state from which neither a certified
> continuation nor a safe stop exists.**

Concretely, the solver publishes a **commit point**: the last state on the
certified trajectory. The executor may pass it only if
(a) the next window is certified, or
(b) from the current state it can still brake to a full stop before the next
hazard (hole lip / drop edge).

If neither holds, the executor **stops and waits** for the solver — a brief
pause, not a fall. The beam therefore always retains at least one "stop" plan as
a fallback branch.

Today's falls are exactly this invariant's absence: the agent executed into an
uncertifiable state and improvised. This is the single most important safety
property in the design.

---

## 7. Feedback to the coarse layer

The solver distinguishes two failures, and they must be reported differently:

| Solver result | Meaning | Action |
|---|---|---|
| No trajectory for this edge from **any** reachable entry state | the edge is physically impossible | raise coarse cost → ∞, D* repairs |
| No trajectory **from the current state** | the *approach* was wrong, not the edge | re-solve with a longer window / earlier commit; **do not penalize the edge** |

The current system cannot tell these apart, which is why the field log shows
`Edge obstructed … penalty x8` on a perfectly good two-forward/one-up jump: it
blamed the edge for an approach that arrived at `v = 0.000`. Costs must still
only ever revise **upward** (the LazySP invariant), or D* repair breaks.

---

## 7b. The corridor is a suggestion — `g` is the cost-to-go oracle

The window solver must **not** be confined to the coarse corridor. It shouldn't
have to be, either: D* Lite searches **backward from the goal**, so its
`g(node)` is *already* the cost-to-goal from any expanded node
(`DStarLite.g()`, public today). That gives the local search a terminal cost
function for free:

$$\text{total}(\tau) \;=\; \underbrace{\text{ticks}(\tau)}_{\text{simulated, exact}} \;+\; \underbrace{g(\text{landing}(\tau))}_{\text{coarse, incremental}}$$

So when the simulation shows that a sharp corner would bleed all our speed, the
solver can ask: *what if I instead land fast on block X, off the planned route?*
— evaluate `ticks(τ) + g(X)`, compare, and take the winner. **The coarse path
stops being a constraint and becomes a prior.** This is exactly Hybrid A*
(Dolgov et al., already cited in the research plan): a kinodynamic local search
guided by a grid-derived cost-to-go.

Four things make it work here, and they are worth stating precisely:

1. **Same unit.** Coarse costs are already tick-denominated (`MoveCosts` derived
   from measured `MoveRates`), so `ticks(τ) + g(X)` is a meaningful sum. This is
   the payoff of the July 2026 cost-baseline change.
2. **Admissible.** Coarse costs are *lower bounds* on true physics cost, so
   `g` never overestimates the tail. The solver may be mildly optimistic about
   the far route, never pessimistic — and the receding horizon re-solves it as
   the agent advances.
3. **Unexpanded nodes are cheap.** If `g(X) = ∞` merely because D* hasn't
   expanded X yet, we don't guess — we ask D* to expand it. That is the one
   thing D* Lite is *built* for: incremental repair reusing everything already
   computed. A plain A* pathfinder (Baritone, minecraft-pathfinding) cannot do
   this at all without re-running the whole search, which is precisely why
   both of them are stuck validating jumps reactively at the lip.
4. **Velocity blindness is bounded, and that's fine.** `g` is position-only: it
   prices landing on X the same whether we arrive fast or slow. That would be
   fatal if `g` had to price the *next* jump — but it doesn't: momentum coupling
   is *local*, and the window (3–4 jumps) already searches it in full velocity
   state. `g` only prices the tail beyond the window, where the entry velocity
   we happen to carry no longer matters. **Local physics, global topology.**

This closes the loop with §7: the solver's simulated cost feeds *back* into the
coarse graph (upward-only revision), D* repairs `g` incrementally, and the next
window solve sees the improved oracle. The two layers sharpen each other while
the agent is walking.

*Caveat to measure, not assume:* if the coarse layer is frequently wrong about
topology, the solver will thrash — every window solve proposing a different
route. The corpus counts this directly (a coarse edge killed by the solver is a
logged event). Keep coarse masks conservative-optimistic and it should be rare.

---

## 8. Caching (this is what makes it affordable)

Key jump sims by:

```
(canonical edge geometry, launch-progress bucket, entry-speed bucket, gait)
```

**T7 rotation equivariance**: the airborne dynamics are equivariant under
horizontal rotation for a fixed relative input, so one simulated profile serves
*every* yaw — canonicalize the edge by rotation and the cache collapses by ~8×.
The window is re-solved every tick, but almost every sim is a cache hit; only the
newly-revealed frontier costs anything.

---

## 9. Renderer (the debugging surface)

Publish an immutable `SolverTrajectory` snapshot from the worker (same discipline
as `PublishedPlan`: one volatile write, never live solver state) and render:

- **Certified trajectory** — solid, bright: the tick-by-tick simulated path up
  to the commit point. This is literally "what it thinks it will do".
- **Speculative tail** — dashed/faded: the current best beam candidate beyond
  the commit point, out to the search frontier.
- **Launch markers** — a cube at each chosen launch tick's position, labelled
  with `(progress, entry speed, gait)`.
- **Landing reticles** — with the predicted miss `e`, coloured by margin.
- **Commit point** — a distinct marker; it should visibly stay ahead of the
  player. If it ever collapses onto the player, the solver is losing the race
  and that is instantly visible.
- Optional: rejected candidates as faint ghosts — this is what will make "why
  did it stutter here" answerable at a glance.

Existing assets to extend: `PlannedArc`, `PathfinderExecutor.plannedArcs`,
`activeLaunchArc`, `PathfinderRender`, `PathfinderRenderSettings`.

---

## 10. Build order

- **W0 — Physics facts.** Measure `∂e/∂s`, `∂e/∂v`, `T_air`, per-tick stride vs
  gait, and confirm monotonicity ranges. Offline, from the existing simulator.
  *Everything above rests on these; measure them before building on them.*
- **W1 — `ManeuverController`.** One shared object; discovery's sim and the
  executor both drive it. (Every bug found this round was a divergence between
  the two current copies.)
- **W2a — Walk rollout.** Simulate walking through the controller: pure pursuit
  along the corridor, gait, corner-cutting via target skipping/offset, cost =
  simulated ticks. This alone should straighten the lattice zig-zag, keep speed
  through corners, and it is the prerequisite for W2b (a jump's entry velocity
  is whatever the *walk* delivered). Retires `PathRefiner`.
- **W2b — Single-jump solver.** `(schedule, tick)` search with the Jacobian
  accelerator and exhaustive fallback. Replaces `EntrySpeedEnvelope`,
  `phaseAlignmentThrottle`, `scheduledLaunchTicks`, and the takeoff FSM.
- **W3 — Renderer.** Before the window search, so W4 is debuggable at all.
- **W4 — Window beam + backward pass + commit point + safe-stop.**
- **W5 — Feedback to coarse + cache/T7.**
- **W6 — Delete** `invalidMomentumApproach`, the gate forest, and the
  mask-admitted `MoveTable.gapJump`.

Gates throughout: `chain-thin-*` (1x1 pads) and the `JumpMatrix` first-attempt
rate + align-tick-by-turn-angle table.
