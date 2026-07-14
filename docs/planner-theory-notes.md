# Theory Notes — Pre-Implementation Soundness (WP-T)

Companion to `planner-research-plan.md` (v0.4). One note per theory-track item. Status legend:
**RESOLVED** (result stands, ready to build against), **RESOLVED\*** (result stands modulo
simulator-calibrated constants), **SKETCH** (structured, needs a final write-up pass).

All speeds below are *nominal* community values in blocks/tick (b/t); WP0's F1 calibration
replaces them with simulator-measured constants. Nothing in the proofs depends on the exact
numbers, only on the caps being true maxima.

---

## T1 — Heuristic admissibility under macro-edges — **RESOLVED\***

### The problem is worse than the plan stated

The source paper's heuristic is $h(\mathbf{u},\mathbf{v}) = \|\mathbf{u}-\mathbf{v}\|_2 / v_{\max}$,
isotropic in all directions. Minecraft movement is strongly anisotropic:

| Mode | Rate (nominal) |
|---|---|
| Walk (horizontal) | 0.216 b/t |
| Sprint (horizontal) | 0.281 b/t |
| Sprint-jump (horizontal, sustained) | ≈ 0.356 b/t |
| Sustained ascent (repeated 1-block jump-ups) | ≈ 0.10–0.14 b/t |
| Falling (terminal) | ≈ 3.92 b/t |

Two failure modes of the isotropic form:

1. **Inadmissibility.** If $v_{\max}$ = sprint speed (a natural reading of the current
   `WalkingMovementModel` constants), the heuristic *overestimates* nothing today — but the
   moment drop primitives land (WP2), a 20-block fall takes ~25 ticks while the heuristic claims
   ≥ 71. That direction is safe. The unsafe direction: sprint-jump moves at 0.356 b/t > sprint
   0.281 b/t, so once jump primitives exist, $h$ with $v_{\max}=$ sprint **overestimates and
   breaks optimality guarantees silently**. ⚠ *Action item for the current codebase, not just
   the future one.*
2. **Uninformativeness.** The safe fix — $v_{\max}$ = terminal fall velocity ≈ 3.92 b/t — makes
   $h$ ~14× weaker than necessary for horizontal travel, degenerating toward Dijkstra exactly
   where searches are largest (long horizontal routes).

### Result: the library-derived anisotropic heuristic

Let $P$ be the set of *enabled movement modes* (primitives + maneuver classes, per config).
Define three caps as maxima of per-edge ratios over $P$:

$$v_h = \max_{p \in P} \frac{\Delta_{xz}(p)}{\mathrm{cost}(p)}, \qquad
  r_{\uparrow} = \max_{p \in P} \frac{\Delta_y^+(p)}{\mathrm{cost}(p)}, \qquad
  r_{\downarrow} = \max_{p \in P} \frac{\Delta_y^-(p)}{\mathrm{cost}(p)}$$

and the heuristic

$$h(\mathbf{a},\mathbf{b}) = \max\!\left( \frac{\|\mathbf{b}_{xz}-\mathbf{a}_{xz}\|_2}{v_h},\;
  \frac{(b_y - a_y)^+}{r_{\uparrow}},\; \frac{(a_y - b_y)^+}{r_{\downarrow}} \right).$$

**Theorem T1.** $h$ is admissible and consistent for any graph whose edges are instances of
modes in $P$.

*Proof.* (Admissibility) For any edge $e$ of mode $p$: $\mathrm{cost}(e) \ge \Delta_{xz}(e)/v_h$,
$\ge \Delta_y^+(e)/r_\uparrow$, $\ge \Delta_y^-(e)/r_\downarrow$, each by definition of the cap
as a maximum of ratios. Summing along any path $\pi$ from $\mathbf{a}$ to $\mathbf{b}$:
$\mathrm{cost}(\pi) \ge \sum_e \Delta_{xz}(e)/v_h \ge \|\mathbf{b}_{xz}-\mathbf{a}_{xz}\|_2/v_h$
(triangle inequality in the horizontal plane), and
$\mathrm{cost}(\pi) \ge \sum_e \Delta_y^+(e)/r_\uparrow \ge (b_y-a_y)^+/r_\uparrow$ (net ascent
is at most total ascent); symmetrically for descent. Hence
$\mathrm{cost}(\pi) \ge h(\mathbf{a},\mathbf{b})$. (Consistency) Each component has the form
$f(\mathbf{b}-\mathbf{a})$ with $f$ positively homogeneous and subadditive (a norm of a
projection, or a positive part of a linear functional, scaled), so each satisfies the triangle
inequality; a max of consistent heuristics whose edge condition
$h(\mathbf{u},\mathbf{v}) \le \mathrm{cost}(\mathbf{u},\mathbf{v})$ holds (shown above) is
consistent. ∎

**Why this is the right construction.**
- *Self-maintaining:* adding a primitive/maneuver class updates the caps mechanically;
  admissibility can never silently rot. Caps are computed over enabled *modes* (static, from
  config), not instantiated edges — so a sprint-jump discovered at runtime is already covered.
- *Strictly dominates* the isotropic form wherever movement anisotropy is real: for ascent-heavy
  goals $h$ is ~3–4× larger (tighter) than Euclid/sprint, and it stays admissible in the
  presence of fast falling by paying the fall rate only in the descent component.
- *A subtle trap avoided:* one might tighten the ascent bound piecewise (ballistic single-jump
  reach then tower rate). That bound is **inadmissible**: climbing 2.5 blocks as two 1-block
  jumps on terrain beats the piecewise formula's estimate. Linear caps derived from the fastest
  *repeatable* mode are the correct — and safe — form. (This is why hand-tuned heuristics here
  are dangerous, and derivation-from-library is the principle.)

**Plan impact:** replaces the heuristic in §4.7; `max(h_aniso, h_abstract)` is the full form.
D* Lite's $k_m$ machinery is untouched ($h$ still satisfies the triangle inequality over agent
moves). Flag to fix the current implementation before jump-heavy configs ship.

---

## T2 — Correctness under gated edge discovery — **RESOLVED**

**Setting.** Discovery adds edges at expansion time: when the backward search expands node
$\mathbf{v}$ and $\mathbf{v}$ satisfies the trigger predicate (ledge/overhang), the maneuver
solver is invoked once and returns landing-anchored predecessor edges into $\mathbf{v}$. (Note
the direction alignment: D* Lite expands from the goal asking for *predecessors* — exactly the
landing-anchored question. Discovery and search want the same anchor.)

**Model.** A discovery-monotone graph sequence $G_0 \subseteq G_1 \subseteq \dots \subseteq
G_K$: each discovery event only *adds* edges (world-change removals are handled separately by
the existing `UpdateEdge` machinery and are orthogonal). Discovery is memoized per (node,
world-neighborhood-hash): each node triggers at most once per local world state.

**Theorem T2.** Interleaving memoized, expansion-gated edge discovery with
`ComputeShortestPath` preserves D* Lite's termination and correctness; on termination the path
is optimal w.r.t. the final discovered graph $G_K$.

*Proof sketch.* Each discovery event is a finite batch of edge-cost decreases
($\infty \to c$), applied through `UpdateEdge` — precisely the event class D* Lite is proven
correct under (Koenig & Likhachev's correctness holds for arbitrary sequences of edge-cost
changes interleaved with computation, provided each change is registered via the update
procedure before affected vertices are further used; expansion-time registration satisfies
this). Memoization bounds the number of discovery events by the number of trigger nodes, which
is finite in any bounded search episode; between the last event and termination, the unmodified
termination argument applies. ∎

**The honest caveat (unchanged from the plan):** optimality is *relative to discovered edges*.
The trigger predicate determines F3 coverage; an undiscovered maneuver is a missed connection.
Coverage is an empirical S3 metric, not a theorem. Second consequence: discovered macro-edges
embed expensive computation — RQ7's pruning score must weight them above ordinary edges (add
"reconstruction cost" to the NRS factors).

---

## T3 — Envelope algebra as funnel composition — **RESOLVED** (spec-level)

**Definitions.** Agent state $s = (\mathbf{p}, \mathbf{v}, g) \in
\mathbb{R}^3 \times \mathbb{R}^3 \times \{0,1\}$ (position, velocity, onGround). A maneuver
$m$ is a control script $u_0 \dots u_{T-1}$ (per-tick inputs) together with an **entry
envelope** $\mathcal{E}_m$, per-tick **tube sections** $B_0 \dots B_T$, and an **exit set**
$\mathcal{X}_m = B_T$. All sets are axis-aligned boxes in the reduced coordinates
$(\mathbf{p}, \|\mathbf{v}_{xz}\|, \mathrm{dir}(\mathbf{v}_{xz}), v_y, g)$ — boxes first;
richer shapes only if S3 shows box conservatism costs completions. This instantiates
Frazzoli's Maneuver Automaton with sampled tubes as poor-man's funnels (Majumdar–Tedrake)
— no Lyapunov certificates; the runtime monitor is the backstop for the missing formal
guarantee.

**Disturbance model, and why short maneuvers are benign.** Let $\delta$ bound the per-tick
divergence between the simulator and the authoritative server state (measured in WP0; sources:
latency reconciliation, mob pushes, unmodeled block interactions). Divergence propagates
through the dynamics as $e_{t+1} \le L\,e_t + \delta$. Minecraft's airborne dynamics are
*contractive in velocity*: horizontal drag multiplies $\mathbf{v}_{xz}$ by ≈ 0.91 per tick
(vertical similarly, plus constant gravity), so the velocity error component contracts while
only the position component integrates it. Consequently the tube inflation needed at tick $t$
is $\varepsilon_t = O(\delta \cdot t)$ with a small constant — and maneuvers are short
($T \lesssim 20$ ticks), so inflation stays a small fraction of a block. This is the
quantitative reason envelope-checked scripted execution is feasible at all, and it comes from
the game's own drag constants.

**Composition theorem.** Chain $m_1; m_2; \dots; m_k$ is valid iff for each consecutive pair
$\mathcal{X}_{m_i} \oplus B_{\varepsilon(T_i)} \subseteq \mathcal{E}_{m_{i+1}}$.
*Claim:* if the chain is valid and the observed state satisfies the (inflated) tube membership
at every tick, then at every handoff the observed state lies in the next maneuver's entry
envelope, and the whole chain executes within its tubes. *Proof:* induction over maneuvers;
within $m_i$, per-tick membership is checked directly; at handoff, the observed exit state
lies in $\mathcal{X}_{m_i} \oplus B_{\varepsilon(T_i)}$ (tube membership + divergence bound),
which is contained in $\mathcal{E}_{m_{i+1}}$ by validity. ∎

**Envelope estimation procedure.** For each (input pattern × entry-speed class): grid-sample
candidate entry states, forward-simulate (one profile per class by T7's rotation equivariance
— the samples vary only the residual coordinates), classify success/failure, take
$\mathcal{E}_m$ as the largest axis-aligned box inside the success region shrunk by one sample
step, and $B_t$ as the bounding box of the successful trajectories at tick $t$, inflated by
$\varepsilon_t$. Sampling densifies adaptively near the failure boundary, which T7's geometric
sweep locates analytically. Honest limitation: sampled inner/outer approximations carry no
formal guarantee — the per-tick monitor converts any estimation error into a detected
violation rather than a fall.

**Runtime semantics.** Monitor = per-tick membership check against $B_t \oplus
B_{\varepsilon(t)}$ (a handful of float comparisons). Violation ⇒ transition to the recovery
primitive: zero inputs, stabilize, replan from observed state — never blind script
continuation. Worst-case exposure is one tick of unmonitored flight, bounded by the maximum
per-tick displacement. H6 restated precisely: *envelope-checked execution = tube-membership
monitoring with a recovery transition*.

**Open item (one left).** Whether $\varepsilon$ should be state- or time-varying beyond the
linear model (lag spikes are bursty) — measure $\delta$'s distribution in WP0 first; if
heavy-tailed, use a quantile-based $\varepsilon_t$ rather than the mean-based one.

---

## T4 — Overlay commit/rollback invariant preservation — **RESOLVED** (spec-level)

**Per-entry state machine.** An overlay entry $e = (\mathbf{v},\, s_{\text{pre}} \to
s_{\text{post}},\, \text{owner})$ is `pending → realized | violated | rolled-back`:

- **realized**: observation at $\mathbf{v}$ equals $s_{\text{post}}$ — the prediction came
  true. Resolved view unchanged ⇒ **no graph invalidation needed** (a useful optimization:
  executing your own plan produces zero repair work when predictions hold).
- **violated**: observation differs from both $s_{\text{pre}}$ and $s_{\text{post}}$ (external
  interference). Resolved view changes ⇒ invalidate; typed event to owner (observation wins,
  always).
- **rolled-back**: owning task aborts. Resolved-view diff computed against the *current* base
  (the base may have moved beneath the entry) — diffing overlay entries instead of resolved
  views is the bug class this note exists to prevent.

**Theorem T4.** If every transition emits `Invalidate(d)` for exactly the voxels whose
*resolved* view changed, D* Lite's invariants are preserved under arbitrary interleavings of
observations, commits, and rollbacks.

*Proof sketch.* Graph costs are a pure function of the resolved view. Each transition's
resolved-view diff is a finite voxel set; `Invalidate`/`UpdateEdge` is exactly the paper's
proven-correct interface for finite world-change batches. Interleaving safety follows as in T2
(all changes route through the same update procedure). ∎

**Design rule surfaced.** Two tasks with overlapping pending entries on one voxel make
"resolved view" ill-defined ⇒ voxel ownership is exclusive: L3 grants region leases; a commit
into a leased region by another task is rejected at the API. (Cheap to enforce; painful to
retrofit.)

---

## T5 — The F2 flip gadget, and a bound that (conditionally) closes F2 — **RESOLVED**

**Gadget (flips exist).** Let the goal lie at bearing 22.5° from the start. Corridor A runs
straight at 22.5°, true cost $d$. Corridor B is axis-aligned with true (and quantized) cost
$1.05\,d$. On an 8-connected lattice the best approximation of A alternates 0°/45° moves with
cost $\lambda_8 d$, $\lambda_8 = 1/\cos(\pi/8) \approx 1.082$. The quantized planner compares
$1.082\,d$ vs $1.05\,d$ and commits to B — the wrong homotopy class; no refiner confined to B
recovers A. **F2 can flip topology.** C1's F2 clause is therefore a frequency claim, as the
plan already states.

**Theorem T5 (damage bound).** Call an environment *lattice-passable* if every path has a
same-homotopy grid approximation within factor $\lambda$ ($\lambda_8 \approx 1.082$ in-plane;
$\approx 1.13$ for general 26-connected 3D motion, matching the paper's cited figure). Then for
any lattice-passable instance, the refined path satisfies
$\mathrm{cost}(\pi_{\text{refined}}) \le \lambda \cdot \mathrm{OPT}$ — *even when the topology
flips*.

*Proof.* Let $\tau^*$ be a true-optimal path. Lattice-passability gives a grid path $\sigma$
with $\mathrm{cost}(\sigma) \le \lambda\,\mathrm{cost}(\tau^*)$. The grid planner returns a
grid-optimal $\pi_c$, so $\mathrm{cost}(\pi_c) \le \mathrm{cost}(\sigma)$. The refiner never
increases cost: $\mathrm{cost}(\pi_{\text{refined}}) \le \mathrm{cost}(\pi_c) \le
\lambda\,\mathrm{OPT}$. ∎

**Where the condition fails, F2 becomes F3.** A corridor traversable *only* at a non-lattice
angle (too narrow for any grid approximation) isn't a mispriced connection — it's a missing
one, i.e., an F3 case for the discovery machinery. So the taxonomy is clean:
**F2's worst-case damage is bounded by the lattice constant (≤ ~8–13%); everything worse is
definitionally F3.** The F2 empirical study is accordingly reframed: measure the *typical* gap
(expected ≪ λ) and the *frequency of lattice-impassable corridors* (which counts F3 work, not
F2 work).

---

## T6 — Hardness of stance sequencing — **RESOLVED** (routine)

**Formulation.** Builder routing = Precedence-Constrained Generalized TSP: for each placement
$i$ a nonempty *set* $S_i$ of feasible stances (choose exactly one), travel costs from L2,
precedence partial order from the dependency DAG.

**Hardness.** Restrict: singleton stance sets + arbitrary partial order = Sequential Ordering
Problem (NP-hard). Restrict: empty order + nontrivial stance sets = GTSP (NP-hard). Either
restriction embeds, so the general problem is NP-hard; greedy + local improvement (or-opt over
stance choices and adjacent transpositions respecting the order) is the justified production
approach. LKH-3 (SOP) and GLNS (GTSP) bound the greedy gap offline on S5 instances. ∎

---

## T7 — Reachability unification, and a result that makes H8 cheap — **RESOLVED\***

**Unification (as planned).** The collision-guided yaw sweep is exact reachability along the
single continuous input dimension (yaw) at one-jump horizon; RQ9's reach-set planner is the
multi-tick generalization. Yaw being the only continuous input, the per-tick reachable set is a
finite union (over discrete input patterns) of 1-parameter families; dimension grows by at most
one per tick, capped at the state dimension.

**Theorem T7 (rotation equivariance — the load-bearing new result).** Minecraft's airborne
dynamics are equivariant under horizontal rotation for fixed *relative* input: gravity is
vertical, drag horizontal-isotropic, and input acceleration is expressed in the facing frame.
Hence for a fixed input pattern (e.g., hold-forward sprint-jump) and fixed entry speed, the
trajectory in the *local frame* is a single fixed curve $\gamma(t)$, and the world trajectory
at yaw $\theta$ is exactly $R_y(\theta)\,\gamma(t)$.

**Consequence — simulate once, sweep geometrically.** Feasibility over all yaw needs **one
simulation per (input pattern × entry-speed class)**, not one per yaw sample: the free-flight
profile $\gamma$ is computed once; first-collision testing of $R_y(\theta)\gamma$ against the
local voxel geometry — including landing-plane intersection and head-hit — is pure geometry in
$\theta$. Near a blocking collision, the grazing yaw against an AABB corner is a
1-D root-finding problem, monotone locally, which is precisely the analytic interval
elimination H8 postulated — now derived rather than hoped. This upgrades H8's plausibility from
"heuristically gated sampling" to "one simulation + a geometric sweep."

**Caveats (kept explicit).** Equivariance holds for fixed relative input; input patterns mixing
strafe/forward are covered (each pattern is its own profile) but *mid-flight input changes*
multiply the pattern space — discovery restricts to a small pattern library (hold-forward;
45°-strafe; late-jump variants). Post-collision trajectories (bonk-and-continue) break the
single-profile picture; first-collision = failure boundary is the discovery semantics, so this
costs completeness of *recovery-style* jumps only. Entry-speed classes discretize the second
continuous parameter; class width is an S3-tunable.

---

## T8 — The non-interference certificate — **RESOLVED**

**Setting.** A traversal-regime plan $\pi = e_1 \dots e_n$; each edge $e_j$ carries, from its
primitive template: required-clear mask $C_j$, required-support mask $S_j$, and its own edit
set with break targets $\mathrm{brk}(E_j)$ and place targets $\mathrm{plc}(E_j)$. Define the
read set $R_j = C_j \cup S_j \cup \mathrm{tgt}(E_j)$ (the voxels whose base-world state the
search consulted when validating and pricing $e_j$), where
$\mathrm{tgt}(E_j)=\mathrm{brk}(E_j)\cup\mathrm{plc}(E_j)$ (break cost depends on the block
present; placement validity on the block absent).

**Scope condition (what "traversal regime" formally means).** Every primitive is
*self-contained*: its validity and cost depend only on $R_j$ and its own edits (e.g.,
"tower-up" places the block it stands on *within one primitive*). Positive cross-edge
dependencies — place now, stand on it three edges later — are the construction regime by
definition and excluded here.

**Lemma (automatic disjointness).** If $e_j$ was generated against base world $B$:
$\mathrm{brk}(E_i) \cap C_j = \emptyset$ and $\mathrm{plc}(E_i) \cap S_j = \emptyset$
automatically, for all $i, j$. *Proof:* break targets are occupied in $B$ while $C_j$ was
verified clear in $B$; place targets are clear in $B$ while $S_j$ was verified solid in $B$. ∎

So only three interaction channels can exist, and the certificate checks exactly those:

**Certificate (edit-consistency).** For all $i < j$:
1. $\mathrm{brk}(E_i) \cap S_j = \emptyset$  (an earlier break doesn't remove later support),
2. $\mathrm{plc}(E_i) \cap C_j = \emptyset$  (an earlier place doesn't block later clearance),
3. $\mathrm{tgt}(E_i) \cap \mathrm{tgt}(E_j) = \emptyset$  (no double-editing / mispricing).

**Theorem T8.** An edit-consistent plan over self-contained primitives executes with exactly
the planned feasibility and cost, absent external world changes.

*Proof.* Induction over $j$. The world before executing $e_j$ is
$B' = B + E_1 + \dots + E_{j-1}$, differing from $B$ only on
$\bigcup_{i<j}\mathrm{tgt}(E_i)$. By the lemma and conditions (1)–(3), this difference set is
disjoint from $R_j$. Edge semantics (validity via $C_j, S_j$; cost via $R_j$) are functions of
the world restricted to $R_j$, identical in $B$ and $B'$; self-containment covers $e_j$'s own
edits. Hence $e_j$ executes as validated and priced. ∎

**Cost of the check.** One linear pass with a voxel hash-grid over the plan's masks —
$O(\sum_j |R_j|)$; negligible against a single physics simulation.

**Graded response (replaces binary soundness).** Certified plan ⇒ execute without edit
monitors. Certificate fails at pair $(i,j)$ ⇒ (a) execute the certified prefix and force a
replan checkpoint before $e_j$, or (b) escalate the offending window to the construction
regime, or (c) execute with runtime monitors (today's default). The S4 study measures how
often natural plans certify — H2 predicts: almost always.

**Why this matters.** This is the formal answer to what was folklore ("Baritone's assumption
mostly works"): the assumption is exactly the certificate, its failure modes are exactly the
three channels, two candidate failure channels are *provably impossible*, and checking the rest
is nearly free.

---

## Consolidated plan impacts

| Note | Plan change |
|---|---|
| T1 | §4.7 heuristic replaced by library-derived anisotropic $h$; **action item**: current Euclidean/$v_{\max}$ becomes inadmissible when jump primitives land |
| T2 | Discovery correctness settled; NRS (RQ7) gains a reconstruction-cost factor |
| T3 | Envelope algebra resolved: boxes + linear tube inflation justified by drag contractivity; composition theorem; estimation procedure; monitor as backstop for sampling gaps; $\delta$ distribution measured in WP0 |
| T4 | WorldView spec gains the per-entry state machine + region leases; "realized" transitions need no invalidation |
| T5 | F2 closed theoretically: damage ≤ lattice constant, conditional on lattice-passability; impassable corridors are F3 by definition; F2 study reframed |
| T6 | Hardness settled; LKH-3/GLNS as offline gap measurement |
| T7 | H8 upgraded: one simulation per input pattern × speed class + geometric yaw sweep (rotation equivariance) |
| T8 | Traversal regime gets a per-plan soundness certificate with a graded failure response; two failure channels proven impossible |
