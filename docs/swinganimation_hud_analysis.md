# 🎯 Phân Tích Toàn Diện: Swing Animation & HUD System

> **Mục tiêu:** Học hỏi từ [PVPUtils](https://github.com/bakabaicai/PVPUtils) để cải thiện:
> 1. **Swing Animation Module** — Hiệu ứng trail vũ khí, kill effect, sprint effect
> 2. **HUD System** — Render HUD elements, theme/color management, editor
>
> **Repository:** `lambda` (project hiện tại) vs `PVPUtils` (bakabaicai)

---

## 📑 Mục Lục
1. [Tổng Quan Kiến Trúc](#1-tổng-quan-kiến-trúc)
2. [Swing Animation: Phân Tích Chi Tiết](#2-swing-animation-phân-tích-chi-tiết)
3. [HUD System: Phân Tích Chi Tiết](#3-hud-system-phân-tích-chi-tiết)
4. [So Sánh & Đối Chiếu](#4-so-sánh--đối-chiếu)
5. [Đề Xuất Cải Thiện](#5-đề-xuất-cải-thiện)
6. [File Map & Code References](#6-file-map--code-references)

---

## 1. TỔNG QUAN KIẾN TRÚC

### 1.1 Lambda (Project Hiện Tại)

```
┌──────────────────────────────────────────┐
│           SwingAnimationModule           │
│  Module object với settings + listeners  │
├──────────────────────────────────────────┤
│  ActiveSwingEffect — Lifecycle mỗi swing │
│  SwingContext — Data context mỗi lần swing│
│  WeaponResolver — Detect weapon type     │
│  ObjectPool — Reduce GC pressure         │
├──────────────────────────────────────────┤
│  Trail System:                           │
│  ├─ TrailRibbonMesh — Render ribbon mesh │
│  ├─ WeaponTipTracker — Tính tip position │
│  ├─ CatmullRomSpline — Spline nội suy   │
│  └─ WeaponAfterimageLayer — Ghost hình   │
├──────────────────────────────────────────┤
│  Kill Effects:                           │
│  ├─ KillDetector — Phát hiện kill       │
│  ├─ KillEffectSequence — Lifecycle seq   │
│  ├─ KillConfirmHud — Vignette + text 2D  │
│  ├─ LightningBoltRenderer — Tia sét 3D   │
│  ├─ SoulParticleLayer — Hạt linh hồn     │
│  └─ GroundShockwaveLayer — Sóng xung kích│
├──────────────────────────────────────────┤
│  Sprint Effects:                          │
│  ├─ SprintTrailManager — Speed tracking  │
│  ├─ FootstepDustLayer — Bụi chân         │
│  ├─ SpeedLineLayer — Speed lines 2D      │
│  ├─ SpeedVignetteOverlay — Vignette speed │
│  └─ DashAfterimageLayer — Ghost khi dash │
├──────────────────────────────────────────┤
│  Other:                                   │
│  ├─ ImpactPunchEffect — FOV shake/flash  │
│  ├─ SpearComboTracker — Combo spear      │
│  └─ AutoQualityDetector — Auto quality   │
└──────────────────────────────────────────┘
```

### 1.2 PVPUtils (Tham Khảo)

```
┌──────────────────────────────────────────┐
│            Config.java                   │
│  Tất cả settings là public static fields │
│  Theme: HudTheme { DARK, LIGHT }         │
│  ModuleRules → field mapping             │
│  HUD components → Hud.cfg riêng          │
├──────────────────────────────────────────┤
│  Swing Animation (gián tiếp):            │
│  ├─ HeldItemPositionManager              │
│  │  • Swing speed modifier               │
│  │  • Item alpha/transparency            │
│  │  • Blocking animation (sword block)   │
│  └─ ItemInHandRendererMixin              │
│     • First-person rendering intercept   │
│     • AnimMode: 1.7 / PUSH / NEW         │
│     • Offset: X, Y, Z, RotX, RotY        │
│     • Sword blocking pose                │
├──────────────────────────────────────────┤
│  HUD System:                              │
│  ├─ Mỗi component có renderer riêng:     │
│  │  • TargetHudRenderer                  │
│  │  • ArmorHudRenderer                   │
│  │  • KeystrokesRenderer                 │
│  │  • ArraylistRenderer                  │
│  │  • PotionStatusRenderer               │
│  │  • BlockCountDisplayRenderer          │
│  │  • MusicInfoHudRenderer               │
│  │  • NotificationOverlay                │
│  │  • DynamicIslandRenderer              │
│  ├─ HudEditOverlay — Drag & drop editor  │
│  ├─ SkiaBlurRenderer — GPU blur          │
│  ├─ FontRenderer — Skia text rendering   │
│  └─ Theme colors: primary/secondary/muted│
└──────────────────────────────────────────┘
```

---

## 2. SWING ANIMATION: PHÂN TÍCH CHI TIẾT

### 2.1 Lambda — Trail System (3D)

#### TrailRibbonMesh (`trail/TrailRibbonMesh.kt`)

**Cấu trúc ribbon:**
```
Layer 1: Glow (bloom)    — 0-3 layers, scale 1.3x-1.9x, alpha: 25%→2%
Layer 2: Core trail      — Vệt chính gradient theo spline
Layer 3: Rim light       — Sáng 2 mép (140% core brightness)
Layer 4: Spark particles — 2-28 hạt bắn tại đầu trail
Layer 5: Chroma Pulse   — Vệt vàng pulse khi crit (60ms)
```

**Spline: Catmull-Rom với subdivisions (2-10 segments)**
- Input: `tipHistory` ~6-16 points từ `WeaponTipTracker`
- Output: Smooth spline points cho ribbon mesh
- LOD: Giảm subdivisions khi camera > 32 blocks

**Đặc biệt theo vũ khí:**
- **Trident:** Dual trail (2 parallel lines) + glow
- **Mace:** Ring wave (fall smash = ring lớn hơn, impact sparks)
- **Spear:** Combo stage → Thrust / Sweep / Spin Slash (vòng tròn 3D)

#### WeaponTipTracker (`trail/WeaponTipTracker.kt`)

**Vấn đề:** ViewModel module có thể bypass `handSwinging` flag → vanilla position không chính xác

**Giải pháp:** Dual tracking strategy:
1. **Primary:** `System.nanoTime()` từ `onSwingStart()` → 300ms duration
2. **Fallback:** Vanilla `handSwingTicks` nếu timer expired

**Position calculation:**
```
tipPos = cameraPos + lookVec * reach + right * swingOffset + verticalOffset
- reach: 3.0 (default), 3.5 (mace)
- swingOffset: sin(progress * 2π) * 0.4 * armSide
- verticalOffset: cos(progress * π) * 0.3 - 0.3
```

#### Color Scheme (`WeaponType.kt`)
```kotlin
SWORD    → #FFFFFF / #BEEFFF / #DFF7FF
AXE      → #FFE3C4 / #FF7A29 / #FFB066
MACE     → #FFFFFF / #B98CFF / #8A8D91
TRIDENT  → #EAF6FF / #1E90FF / #7FD4FF
SPEAR    → #EAFFF2 / #145A32 / #8CF5B0
BOW      → #FFFFFF / #00FFFF / #B3FFFF
CROSSBOW → #FFF6D9 / #D4AF37 / #FFE58A
FIST     → semi-transparent white
```

### 2.2 Lambda — Kill Effects

**Lifecycle** (tổng 1.2s):
```
0ms     Lightning Flash (120ms): Tia sét zigzag từ 15 blocks → kill pos
120ms   Soul Particles (1s): Hạt bay lên từ kill pos
0ms     Shockwave (150ms): Ring mở rộng từ ground
0ms     Vignette + Text (800ms): Gold vignette + "ELIMINATED" text
```

**KillDetector:**
- Track `lastAttacker` mỗi entity trong window ~3s
- Trigger trên `EntityEvent.Removal` với `KILLED` reason
- Chỉ PvP player (trừ khi `triggerOnMobs` bật)
- Server-confirmed: KHÔNG predict

**KillstreakTracker:**
- Timeout 10s giữa 2 kills → streak reset
- Labels: 2=DOUBLE KILL, 3=TRIPLE KILL, 5=RAMPAGE, 10=UNSTOPPABLE
- Text color: 2=green, 3=gold, 5=orange, 10=red

### 2.3 PVPUtils — Sword Blocking Animation

**Khác biệt cơ bản:** PVPUtils **không có** trail/kill 3D system. Thay vào đó:
- Tập trung vào **first-person hand rendering** (ItemInHandRendererMixin)
- **Sword Blocking** — mô phỏng 1.7/1.8 blocking khi right-click với sword
- **AnimMode:** MODE_1_7, MODE_PUSH, MODE_1_7_PLUS, MODE_NEW
- **HeldItemPosition:** Điều chỉnh vị trí item trong tay (X/Y/Z/RotX/RotY)
- **Swing Speed:** Modifier cho tốc độ swing (main hand / off hand riêng)
- **Item Alpha:** Transparency cho held items

#### Các animation mode trong chi tiết:
```java
public enum AnimMode { MODE_1_7, MODE_PUSH, MODE_1_7_PLUS, MODE_NEW }
```

**MODE_1_7:** Classic 1.7 blocking pose
- Đưa kiếm lên trước mặt
- sin(h² * π) + sin(√h * π) rotation blend
- Multi-axis rotation (Y:45°+20°, Z:-20°, X:-80°)

**MODE_PUSH:** Đẩy kiếm về phía trước
- sin(√h * π) modifier
- Z rotation: -35° (trái) / +35° (phải)

**MODE_1_7_PLUS:** 1.7 nhưng có swing animation blend
- Khi swing: thêm X rotation -45°, Z rotation 20°

**MODE_NEW:** Modern style
- Chỉ block khi không swing
- Khi swing: full 1.7 blocking pose dynamic

### 2.4 HeldItemPositionManager

**Chức năng:**
- Modify swing speed per-hand
- Apply item alpha (0-100%)
- ThreadLocal alpha để thread-safe
- Convert RenderType (solid → translucent) khi cần alpha

```java
// Swing speed modifier
public static float applySwingSpeed(InteractionHand hand, float swingProgress) {
    float speed = hand == InteractionHand.MAIN_HAND ? Config.heldItemMainSwingSpeed : Config.heldItemOffSwingSpeed;
    return Math.max(0f, Math.min(1f, swingProgress * speed));
}

// Item alpha
private static int alphaForHand(InteractionHand hand) {
    int percent = hand == InteractionHand.MAIN_HAND ? Config.heldItemMainAlpha : Config.heldItemOffAlpha;
    return Math.round(Math.max(0, Math.min(100, percent)) * 2.55f);
}
```

---

## 3. HUD SYSTEM: PHÂN TÍCH CHI TIẾT

### 3.1 PVPUtils — Config Architecture

**File:** `Config.java`

**Đặc điểm nổi bật:**
- **Tất cả settings là `public static` fields** — no getter/setter overhead
- **Module rules:** Map field → module category (tự động serialize)
- **HUD components riêng:** `HudComponent` record với prefix + fields
- **Config files riêng:** `Config.cfg` (settings) + `Hud.cfg` (position/scale)
- **Kebab-case keys** trong JSON

**Theme System:**
```java
public enum HudTheme { DARK, LIGHT }

public static int hudPrimaryTextColor() {
    return hudTheme == HudTheme.LIGHT ? 0xFF111827 : 0xFFFFFFFF;
}

public static int hudSecondaryTextColor() {
    return hudTheme == HudTheme.LIGHT ? 0xAA111827 : 0xCCFFFFFF;
}

public static int hudMutedTextColor() {
    return hudTheme == HudTheme.LIGHT ? 0xAA5C5870 : 0xBFFFFFFF;
}

public static int hudBorderColor() {
    return hudTheme == HudTheme.LIGHT ? 0x55111827 : 0x55FFFFFF;
}

public static int skiaBlurTintColor() {
    return hudTheme == HudTheme.LIGHT ? 0x66F8FAFC : 0x66111827;
}
```

### 3.2 Mỗi HUD Component là Singleton Renderer

**Pattern:**
```java
public class TargetHudRenderer {
    private static final TargetHudRenderer INSTANCE = new TargetHudRenderer();

    public static TargetHudRenderer getInstance() { return INSTANCE; }

    // Render method called from mixin/hook
    public void render(GuiGraphics graphics) { ... }
}
```

**Các renderers:**
| Renderer | Modes | Đặc điểm |
|----------|-------|----------|
| **TargetHudRenderer** | LITE / NEW / BLUR | Health bar, avatar, name, absorption, damage flash, health animation |
| **ArmorHudRenderer** | LITE / NEW | Layout: SEPARATED / VERTICAL / HORIZONTAL, percentage/bar/both |
| **ArraylistRenderer** | — | Gradient color, border, animation |
| **KeystrokesRenderer** | LITE / NEW | Key press display |
| **PotionStatusRenderer** | — | Background, countdown, hide vanilla |
| **BlockCountDisplayRenderer** | NEW / BLUR | Block count with blur |
| **DynamicIslandRenderer** | — | Unified HUD island (block count + item use) |
| **ItemUseStatusRenderer** | LITE / NEW | Item usage display |
| **MusicInfoHudRenderer** | LITE / NEW / BLUR | Music info overlay |

### 3.3 HudEditOverlay — Drag & Drop Editor

**File:** `HudEditOverlay.java`

**Key Features:**
```
1. Drag & Drop: Mouse drag để reposition HUD elements
2. Snap-to-grid: 25%/50%/75% screen guides
3. Element snapping: Proximity detection (48px) → snap alignment
4. Weak snap: Hold Shift → softer snap (4px threshold)
5. Scale control: Scroll wheel resize (0.5x-2.0x)
6. Animation: Fade in/out (200ms) với easeOutBack
7. Dash outline: Animated dashed border quanh element
8. Grid overlay: Show/hide với fade transition
9. Hover highlight: Alpha tăng dần khi hover
10. Text hint: Guide text ở bottom
```

**Edit state management:**
```java
private enum DragTarget {
    NONE, TARGET_HUD, KEYSTROKES, BLOCK_COUNT, ARMOR_HUD,
    ITEM_USE_STATUS, DYNAMIC_ISLAND, ARRAYLIST, NOTIFICATION,
    POTION_STATUS, LYRICS_DISPLAY, MUSIC_INFO_HUD, BETTER_SCOREBOARD
}
```

**Snap algorithm:**
```
1. Build snap lines từ:
   - Screen guide: 25%, 50%, 75%
   - Other elements: start, center, end
2. For each axis (x, y):
   - Check proximity (SNAP_THRESHOLD = 10px)
   - Weak snap (Shift): WEAK_SNAP_THRESHOLD = 4px
   - Check 3 offsets: 0%, 50%, 100% của element size
3. Apply snap + clamp to screen bounds
```

### 3.4 Skia Blur Renderer

**File:** `SkiaBlurRenderer.java`

Sử dụng Skia GPU rendering để tạo blur background:
```java
// Tạo surface với Skia
Surface surface = Surface.makeRenderTarget(glBackend.getDirectContext(), ...);

// Apply blur
try (ImageFilter blur = ImageFilter.makeBlur(strength, strength, FilterTileMode.CLAMP, null)) {
    canvas.saveLayer(null, blurPaint);
    canvas.drawImage(...);
    canvas.restore();
}
```

**Modes:**
- **NEW mode:** Solid background với rounded corners
- **BLUR mode:** Skia blur background + tint color
- Tint: `skiaBlurTintColor()` theo theme (LIGHT/DARK)

### 3.5 Lambda — HUD System

**HudRenderRegistry:**
```kotlin
object HudRenderRegistry {
    private val entries = mutableMapOf<String, HudEntry>()

    // Register HUD elements
    fun immediateRenderer("HUD Render", depthTest = { false }) {
        entries.values.forEach { entry ->
            entry.renderer(this)
        }
    }
}
```

**HudModule base class:**
```kotlin
abstract class HudModule(name, description, tag) : Module(name, description, tag) {
    // Base class cho HUD modules
    // Đăng ký vào HudRenderRegistry
}
```

**Current HUD Modules:**
- Watermark, Tps, PerformanceHud, AccountName
- KeystrokesHud, TaskFlowHud, ModuleList
- BlockCountDisplay, Rotation, Fps
- Coordinates, ArmorHud, Speedometer

**So với PVPUtils:**
| Tính năng | Lambda | PVPUtils |
|-----------|--------|----------|
| Position config | ❌ Không | ✅ X, Y, scale cho mọi component |
| Theme system | ❌ Không | ✅ LIGHT/DARK với color getters |
| Drag editor | ❌ Không | ✅ HudEditOverlay đầy đủ |
| Blur/skia | ❌ Không | ✅ Skia GPU blur |
| Gradient colors | ❌ Không | ✅ Arraylist gradient |
| Modes per component | ❌ Không | ✅ LITE/NEW/BLUR modes |
| Position persistence | ❌ Không | ✅ Hud.cfg riêng |
| Snap/alignment | ❌ Không | ✅ Grid + element snap |
| Scale control | ❌ Không | ✅ Scroll wheel resize |

---

## 4. SO SÁNH & ĐỐI CHIẾU

### 4.1 Swing Animation

| Khía cạnh | Lambda | PVPUtils |
|-----------|--------|----------|
| **Phạm vi** | Full 3D trail/kill/sprint | Chỉ first-person hand |
| **3D Trail** | Catmull-Rom ribbon mesh | Không có |
| **Kill Effects** | Lightning + soul + shockwave + vignette | Không có |
| **Sprint Effects** | Dust + speed lines + afterimage | Không có |
| **Sword Block** | Không có | ✅ 4 animation modes |
| **Item Position** | Không có | ✅ X, Y, Z, RotX, RotY |
| **Item Alpha** | Không có | ✅ Per-hand transparency |
| **Swing Speed** | Fixed 350ms trail | Configurable modifier |
| **Color Custom** | Per-weapon hardcoded | Config-driven (held item) |
| **Quality Presets** | ✅ LOW/MEDIUM/HIGH/ULTRA | Không có |
| **Object Pool** | ✅ ActiveSwingEffect pool | Không cần (no 3D objects) |

### 4.2 HUD System

| Khía cạnh | Lambda | PVPUtils |
|-----------|--------|----------|
| **Color Theme** | Không | ✅ LIGHT/DARK + getters |
| **Position Config** | Không | ✅ X, Y, scale per component |
| **Drag Editor** | Không | ✅ HudEditOverlay |
| **Snap/Guide** | Không | ✅ Grid + element snap |
| **Blur Effect** | Không | ✅ Skia GPU blur |
| **Modes** | Fixed | ✅ LITE / NEW / BLUR |
| **Gradient** | Không | ✅ Arraylist gradient |
| **Health Bar**| ArmorHud (static) | TargetHud (animated, smooth) |
| **Damage Flash**| Không | ✅ Red flash + avatar scale |
| **Animation**| Không | ✅ Fade in/out easeOutBack |
| **Performance**| Immediate renderer | Skia GPU acceleration |

### 4.3 PVPUtils Weaknesses (Lambda có thể vượt trội)

1. **Không có 3D trail/kill effects** — Lambda đã có system này rất mạnh
2. **Config reflection-based** — Chậm hơn Kotlin delegated properties
3. **Single Config class** — 200+ fields, khó maintain
4. **Skia dependency** — Cần native library, không portable
5. **No object pooling** — GC pressure cao với 3D effects
6. **Theme chỉ 2 colors** — DARK/LIGHT, không có custom palette

---

## 5. ĐỀ XUẤT CẢI THIỆN

### 5.1 Cho Swing Animation (Áp dụng từ PVPUtils)

#### 🎨 Color Customization
**Goal:** Cho phép user tùy chỉnh màu trail, giống PVPUtils config-driven colors.

**Implementation:**
```kotlin
// Trong SwingAnimationModule
private var trailCoreColor by setting("Trail Core Color", Color.WHITE)
private var trailGlowColor by setting("Trail Glow Color", Color(0xBE, 0xEF, 0xFF))
private var trailSparkColor by setting("Trail Spark Color", Color(0xDF, 0xF7, 0xFF))
private var useWeaponDefaultColors by setting("Use Weapon Default Colors", true)

// Khi render
val colors = if (useWeaponDefaultColors) {
    weaponType.defaultColors
} else {
    WeaponColorScheme(trailCoreColor.rgb, trailGlowColor.rgb, trailSparkColor.rgb)
}
```

#### 🎬 Sword Blocking Animation
**Goal:** Thêm sword blocking pose khi right-click với sword.

**Implementation:**
- Mixin vào `ItemInHandRenderer.renderArmWithItem()`
- Phát hiện sword + right-click
- Apply alternate transformation matrix (1.7 blocking pose)
- 4 modes: 1.7 / PUSH / 1.7+ / NEW

```kotlin
object SwordBlockingManager {
    var enabled = false
    var animationMode = AnimMode.MODE_1_7
    var offsetX = 0f; var offsetY = 0f; var offsetZ = 0f
    var animSpeed = 1.0f

    fun isBlocking(player: Player, hand: InteractionHand): Boolean {
        if (!enabled || hand != InteractionHand.MAIN_HAND) return false
        val stack = player.mainHandStack
        if (!stack.isIn(ItemTags.SWORDS)) return false
        return mc.options.useKey.isPressed || autoMode
    }
}
```

#### 🎯 Held Item Position Editor
**Goal:** Cho phép điều chỉnh vị trí item trong tay (giống PVPUtils).

```kotlin
object HeldItemPositionManager {
    var enabled = false
    var mainHandX = 0f; var mainHandY = 0f; var mainHandZ = 0f
    var mainHandRotX = 0f; var mainHandRotY = 0f
    var mainHandSwingSpeed = 1.0f
    var mainHandAlpha = 100  // 0-100%

    var offHandX = 0f; var offHandY = 0f; var offHandZ = 0f
    // ...
}
```

### 5.2 Cho HUD System

#### 🎨 Theme System
**Goal:** Implement theme-aware color getters với LIGHT/DARK modes.

```kotlin
enum class HudTheme {
    LIGHT, DARK;

    val primaryTextColor: Int get() = when(this) {
        LIGHT -> 0xFF111827
        DARK -> 0xFFFFFFFF
    }
    val secondaryTextColor: Int get() = when(this) {
        LIGHT -> 0xAA111827
        DARK -> 0xCCFFFFFF
    }
    val mutedTextColor: Int get() = when(this) {
        LIGHT -> 0xAA5C5870
        DARK -> 0xBFFFFFFF
    }
}
```

#### 🖱️ HUD Editor
**Goal:** Drag & drop HUD editor với snap grid, scale control.

**Components cần:**
1. `HudEditOverlay` — render dashed outlines + labels
2. `HudDragHandler` — mouse drag logic
3. `HudSnapManager` — snap to grid/elements
4. `HudConfigSerializer` — save position per component

**Data model:**
```kotlin
data class HudComponentState(
    val id: String,
    var x: Float = 0f,
    var y: Float = 0f,
    var scale: Float = 1.0f,
    var visible: Boolean = true,
)
```

#### ✨ Smooth Animations
**Goal:** Animated transitions cho HUD elements (fade in/out, position lerp).

```kotlin
class AnimatedFloat(
    private var current: Float = 0f,
    private var target: Float = 0f,
    private val smoothing: Float = 10f,  // lerp speed
) {
    fun update(dt: Float) {
        current += (target - current) * smoothing.coerceAtMost(1f) * dt
    }
    fun set(value: Float) { target = value }
    val value: Float get() = current
}
```

#### 🌀 Skia-like Blur (tùy chọn)
**Goal:** Blur background cho HUD elements nếu hardware hỗ trợ.

**Options:**
1. **Skia Integration:** Full GPU blur (như PVPUtils) — cần native library
2. **Shader-based:** GLSL blur shader trong Minecraft render pipeline
3. **Mipmap blur:** MC's built-in blur via RenderPipelines

### 5.3 Priority Matrix

| Feature | Impact | Effort | Priority |
|---------|--------|--------|----------|
| Trail Color Customization | 🔴 High | 🟢 Easy | **P0** |
| Theme System (LIGHT/DARK) | 🔴 High | 🟢 Easy | **P0** |
| HUD Position Config | 🔴 High | 🟡 Medium | **P1** |
| HUD Editor (Drag & Drop) | 🔴 High | 🔴 Hard | **P1** |
| Sword Blocking Animation | 🟡 Medium | 🟡 Medium | **P2** |
| Held Item Position Editor | 🟡 Medium | 🟡 Medium | **P2** |
| Smooth Animations | 🟡 Medium | 🟡 Medium | **P2** |
| Skia Blur (optional) | 🟢 Low | 🔴 Hard | **P3** |
| Music Info / Lyrics HUD | 🟢 Low | 🟡 Medium | **P3** |

---

## 6. FILE MAP & CODE REFERENCES

### 6.1 Lambda — Swing Animation Files

| File | Mô tả |
|------|-------|
| `swinganimation/SwingAnimationModule.kt` | Main module object với settings + listeners |
| `swinganimation/ActiveSwingEffect.kt` | Lifecycle mỗi swing effect (350ms) |
| `swinganimation/SwingContext.kt` | Context data mỗi swing |
| `swinganimation/EaseFunctions.kt` | Easing functions (cubic, quad, elastic, etc.) |
| `swinganimation/WeaponType.kt` | Weapon type enum + default colors |
| `swinganimation/WeaponResolver.kt` | Resolve weapon từ ItemStack |
| `swinganimation/QualityPreset.kt` | LOW/MEDIUM/HIGH/ULTRA presets |
| `swinganimation/ObjectPool.kt` | Generic object pool |
| `swinganimation/trail/TrailRibbonMesh.kt` | Ribbon mesh rendering |
| `swinganimation/trail/WeaponTipTracker.kt` | Sword tip position calculation |
| `swinganimation/trail/WeaponAfterimageLayer.kt` | Ghost afterimages |
| `swinganimation/trail/CatmullRomSpline.kt` | Spline interpolation |
| `swinganimation/kill/KillConfirmHud.kt` | Vignette + text 2D |
| `swinganimation/kill/KillDetector.kt` | Kill detection logic |
| `swinganimation/kill/KillEffectSequence.kt` | Kill effect lifecycle (1.2s) |
| `swinganimation/kill/LightningBoltRenderer.kt` | Lightning bolt 3D |
| `swinganimation/kill/SoulParticleLayer.kt` | Soul particles |
| `swinganimation/kill/GroundShockwaveLayer.kt` | Shockwave ring |
| `swinganimation/kill/KillstreakTracker.kt` | Streak tracking |
| `swinganimation/sprint/SprintTrailManager.kt` | Sprint state manager |
| `swinganimation/sprint/FootstepDustLayer.kt` | Footstep dust 3D |
| `swinganimation/sprint/SpeedLineLayer.kt` | Speed lines 2D |
| `swinganimation/sprint/SpeedVignetteOverlay.kt` | Vignette speed |
| `swinganimation/sprint/DashAfterimageLayer.kt` | Dash ghost 3D |
| `swinganimation/impact/ImpactPunchEffect.kt` | FOV shake + flash |
| `swinganimation/combo/SpearComboTracker.kt` | Spear combo system |
| `swinganimation/quality/AutoQualityDetector.kt` | Auto quality detection |

### 6.2 PVPUtils — Key Reference Files

| File | Mô tả |
|------|-------|
| `Config.java` | Central config + theme + module rules |
| `modules/impl/Tool/HeldItemPositionManager.java` | Swing speed, alpha, blocking |
| `mixin/client/ItemInHandRendererMixin.java` | First-person hand transform intercept |
| `modules/impl/Render/HudEditOverlay.java` | Drag & drop HUD editor |
| `modules/impl/Render/TargetHudRenderer.java` | Target HUD (LITE/NEW/BLUR) |
| `modules/impl/Render/ArmorHudRenderer.java` | Armor HUD display |
| `modules/impl/Render/ArraylistRenderer.java` | Module list with gradient |
| `modules/impl/Render/KeystrokesRenderer.java` | Key press display |
| `modules/impl/Render/PotionStatusRenderer.java` | Potion effect display |
| `modules/impl/Render/NotificationOverlay.java` | Notification system |
| `modules/impl/Render/DynamicIsland/DynamicIslandRenderer.java` | Unified HUD island |
| `modules/impl/Tool/BlockCountDisplayRenderer.java` | Block count display |
| `render/skia/SkiaBlurRenderer.java` | GPU blur rendering |
| `render/skia/SkiaRenderer.java` | Skia base renderer |
| `render/skia/SkiaGlBackend.java` | OpenGL-Skia bridge |
| `render/font/FontRenderer.java` | Skia font rendering |
| `gui/clickgui/pages/ThemePage.java` | Theme selection UI |
| `mixin/client/ArmorTransparencyManager.java` | Armor alpha management |

### 6.3 Lambda — HUD Files

| File | Mô tả |
|------|-------|
| `graphics/hud/HudRenderRegistry.kt` | HUD renderer registry |
| `module/HudModule.kt` | Base class cho HUD modules |
| `module/hud/ArmorHud.kt` | Armor display |
| `module/hud/BlockCountDisplay.kt` | Block count display |
| `module/hud/KeystrokesHud.kt` | Keystrokes display |
| `module/hud/Coordinates.kt` | Coordinate display |
| `module/hud/Fps.kt` | FPS counter |
| `module/hud/Tps.kt` | TPS display |
| `module/hud/Speedometer.kt` | Speed display |
| `module/hud/Rotation.kt` | Rotation display |
| `module/hud/Watermark.kt` | Watermark |
| `module/hud/ModuleList.kt` | Module list |
| `module/hud/AccountName.kt` | Account name |
| `module/hud/TaskFlowHud.kt` | Task flow display |
| `module/hud/PerformanceHud.kt` | Performance display |

---

## 7. CÁCH TÍCH HỢP COLOR CUSTOMIZATION VÀO SWING ANIMATION

### Current State (Lambda)

Hiện tại màu trail được hardcode trong `WeaponType.defaultColors`:
```kotlin
SWORD -> WeaponColorScheme(0xFFFFFFFF, 0xFFBEEFFF, 0xFFDFF7FF)
```

### Proposed Change

**Bước 1:** Thêm settings vào `SwingAnimationModule.kt`:
```kotlin
private var useCustomColors by setting("Custom Colors", false)
private var customCoreColor by setting("Core Color", Color.WHITE)
private var customGlowColor by setting("Glow Color", Color(0xBE, 0xEF, 0xFF))
private var customSparkColor by setting("Spark Color", Color(0xDF, 0xF7, 0xFF))
```

**Bước 2:** Thêm color picker trong UI (WeaponCheckPanel):
```kotlin
if (!useWeaponDefaultColors) {
    colorPicker("Core Color", customCoreColor) { customCoreColor = it }
    colorPicker("Glow Color", customGlowColor) { customGlowColor = it }
    colorPicker("Spark Color", customSparkColor) { customSparkColor = it }
}
```

**Bước 3:** Render sử dụng custom colors:
```kotlin
val colors = if (useCustomColors) {
    WeaponColorScheme(customCoreColor.rgb, customGlowColor.rgb, customSparkColor.rgb)
} else {
    weaponType.defaultColors
}
```

---

## 8. KẾT LUẬN

### Lambda Mạnh Ở:
- ✅ 3D trail rendering với Catmull-Rom spline
- ✅ Kill effects (lightning, souls, shockwave)
- ✅ Sprint effects (dust, speed lines, afterimages)
- ✅ Object pooling để performance
- ✅ Quality presets (LOW → ULTRA)
- ✅ Auto quality detection
- ✅ Spear combo system

### Cần Học Từ PVPUtils:
- 🔴 **Color customization** — Cho user tùy chỉnh màu trail
- 🔴 **Theme system** — LIGHT/DARK theme cho HUD
- 🔴 **HUD Editor** — Drag & drop position/scale
- 🟡 **Held Item Position** — Modify item position/rotation/alpha
- 🟡 **Sword Blocking** — Animation khi right-click
- 🟡 **Smooth animations** — Easing cho HUD elements
- 🟢 **Skia blur** — GPU blur background (tùy chọn)

### Ưu Tiên Phát Triển:
1. **P0:** Trail color customization + Theme system
2. **P1:** HUD position config + basic editor
3. **P2:** Sword blocking + Smooth animations
4. **P3:** Skia blur + Advanced HUD features

---

*Document generated from analysis of `lambda` (current project) and `PVPUtils` (bakabaicai/PVPUtils) source code.*
*Last updated: July 2026*
