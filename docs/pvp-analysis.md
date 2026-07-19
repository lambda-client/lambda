# 📋 PHÂN TÍCH PVP TOÀN DIỆN - Minato Client (Minecraft 1.21+)

> **Mục tiêu:** Xác định TẤT CẢ các loại PVP trong Minecraft 1.21+ và đối chiếu với codebase để biết: cái nào ĐÃ CÓ, cái nào CẦN THÊM, cái nào PHẢI GIỮ.

---

## 🏆 DANH SÁCH ĐẦY ĐỦ CÁC LOẠI PVP

### I. MELEE COMBAT (Cận chiến)

| # | Loại | Vũ khí | Cơ chế | Tình trạng |
|---|------|--------|--------|:----------:|
| 1 | **Sword PVP** | Sword (Netherite: 8 dmg) | Combo, sprint reset, knockback chain | ✅ KillAura |
| 2 | **Axe PVP** | Axe (Netherite: 10 dmg) | Shield break (5 giây), damage trade | ✅ KillAura |
| 3 | **Mace PVP** 🆕 | Mace (Base: 6 dmg) | **Smash Attack**: dmg = (6 + blocks_fallen × (1 + 0.5×Density)) × 1.5 crit | ❌ **MISSING** |
| 4 | **Trident PVP** | Trident (9 dmg) | Riptide launch, Loyalty return, Channeling storm | ❌ Trident |

### II. RANGED COMBAT (Đánh xa)

| # | Loại | Vũ khí | Cơ chế | Tình trạng |
|---|------|--------|--------|:----------:|
| 5 | **Bow PVP** | Bow | Bullet drop, leading targets, quick charge | ❌ **MISSING** |
| 6 | **Crossbow PVP** | Crossbow | Piercing (xuyên), Multishot (3 mũi), Firework rockets | ❌ **MISSING** |
| 7 | **Trident Ranged** | Trident (Loyalty) | Ném + tự động quay về | ❌ **MISSING** |

### III. EXPLOSIVE PVP (Nổ - Meta chính)

| # | Loại | Cơ chế | Damage | Tình trạng |
|---|------|--------|--------|:----------:|
| 8 | **Crystal PVP** | Place Obsidian → Crystal → Detonate | **6.0 power**, AOE 12 blocks | ✅ **CrystalAura (FULL)** |
| 9 | **Anchor PVP** | Place Anchor → Charge Glowstone → Activate | **5.0 power** (Overworld tự nổ) | ⚠️ **Partial** |
| 10 | **Bed PVP** | Place Bed → Interact | **5.0 power** (Nether/End) | ⚠️ **Partial** |
| 11 | **TNT PVP** | Place TNT → Prime → Boom | **4.0 power**, fuse timer | ❌ **MISSING** |
| 12 | **Firework PVP** | Crossbow + Firework rocket | AOE damage, knockback | ❌ **MISSING** |

### IV. HYBRID & UTILITY PVP (Kết hợp)

| # | Kỹ thuật | Mô tả | Tình trạng |
|---|---------|-------|:----------:|
| 13 | **Mace + Wind Charge** | Bắn Wind Charge xuống đất → launch lên → Mace Smash | ❌ **MISSING** |
| 14 | **Weapon Swapping** | Crossbow → Axe (break shield) → Sword/Mace (dmg) | ✅ KillAura swap |
| 15 | **Pearl Reposition** | Ender Pearl để reset position | ✅ StashMover dùng |
| 16 | **Shield + Axe** | Shield block → Axe disable → Sword combo | ✅ Minecraft built-in |
| 17 | **Jump Resetting** | Jump đúng lúc nhận damage để giảm KB | ❌ **MISSING** |

---

## 🔬 MACE PVP - PHÂN TÍCH CHI TIẾT (1.21+ META)

### Smash Attack Formula
```
Total Damage = (Base Damage + Blocks_Fallen × (1 + 0.5 × Density_Level)) × 1.5 (Crit)
```
- **Base Damage:** 6 (3 hearts)
- **Fall Bonus:** +1 raw damage per block fallen (gấp 1.5 khi crit)
- **Density V:** +2.5 raw damage per block fallen → **3.75 dmg/block after crit**
- **Crit:** Smash attacks ALWAYS crit (1.5x multiplier)

### Enchantments

| Enchant | Max | Effect | PVP Use |
|---------|:---:|--------|---------|
| **Density** | V | +0.5 damage/block/level | **Raw damage** - One-shot potential |
| **Breach** | IV | -15% armor/level (max 60%) | **Anti-Netherite** - Ignore armor |
| **Wind Burst** | III | Launch lên sau smash hit | **Combo reset** - Chain smash attacks |
| **Sharpness** | V | +0.5 per level + 0.5 | Stack với smash damage |
| **Unbreaking** | III | Durability | QoL |
| **Mending** | - | Repair | QoL |

### Wind Burst Combo Loop
1. Smash hit → Wind Burst kích hoạt → launch lên
2. Smash hit lần 2 → Wind Burst lại kích hoạt
3. **Lưu ý:** Wind Burst KHÔNG negate fall damage nếu miss

### Wind Charge Synergy (Meta)
1. Bắn Wind Charge xuống đất gần chân
2. Launch lên ~5-8 blocks
3. Nhắm target → Mace Smash Attack
4. Lặp lại

### Counter-play
- Di chuyển unpredictable để mace user miss
- Dùng blocks/terrain để disrupt trajectory
- Shield blocking (giảm damage)
- KB stick để đẩy mace user ra xa

---

## 💥 CRYSTAL / ANCHOR / BED PVP

| Thuộc tính | Crystal | Anchor | Bed |
|-----------|---------|--------|:---:|
| Explosion Power | 6.0 | 5.0 | 5.0 |
| Damage Range | 12 blocks | 12 blocks | 12 blocks |
| Setup | Obsidian + Crystal | Anchor + Glowstone | Bed |
| Kích hoạt | Break/Attack | Right-click (overworld) | Right-click (nether/end) |
| **Codebase** | ✅ **CrystalAura FULL** | ⚠️ **BlockUtils detection** | ⚠️ **DamageUtils detection** |
| | Predict place/explode | Không có auto charge | Không có auto place |
| | Damage calc | Không có auto activate | Không có auto activate |
| | Weapon swap | | |

### Crystal PVP Codebase Coverage
- `CrystalAura.kt` - Auto place + explode ✅
- `CombatUtils.crystalDamage()` - Damage calculation ✅
- `AutoTotem.kt` - Crystal lethal detection ✅
- `AutoDisconnect.kt` - Crystal proximity detection ✅
- `DamageUtils` - Armor/damage scaling ✅
- `BlockUtils` - Block detection ✅

---

## ⚔️ SWORD + AXE PVP

### Weapon Damage Table (Java Edition 1.21)
| Vũ khí | Netherite | Diamond | Attack Speed |
|--------|:---------:|:-------:|:-----------:|
| Sword | 8 | 7 | 1.6 |
| Axe | 10 | 9 | 1.0 |
| Mace | 6 | 6 | 1.0 |
| Trident | - | 9 | 1.1 |

### Sharpness Bonus: `0.5 × level + 0.5` (Sharpness V = +3 damage)

### Attack Cooldown
- Tấn công đầy đủ = weapon base damage
- Tấn công sớm = reduced damage (scale theo progress)
- `attackDamage()` và `attackSpeed()` trong `ItemStackUtils.kt` ✅

### Shield Mechanics
- Axe hit → **disable shield 5 giây**
- `DamageUtils.scale()` có `BLOCKS_ATTACKS` handling ✅
- `Entity.blockingItem` check ✅

---

## 🏹 RANGED PVP

### Các loại:
1. **Bow** - Quick Charge enchant, flame, power, infinity
2. **Crossbow** - Piercing (xuyên multiple targets), Multishot (3 mũi), Firework rockets (AOE)
3. **Trident** (Loyalty) - Ném + auto return

### Codebase:
- ❌ Không có AutoBow / AutoCrossbow module
- ❌ Không có arrow trajectory prediction
- ❌ Không có trident management

---

## 🛡️ DAMAGE CALCULATION - COMPLETE FORMULA

### 1. Armor Reduction
```
Effective Armor = max(Armor/5, Armor - 4×Damage/(Toughness + 8))
Damage_Multiplier = 1 - min(20, Effective_Armor)/25
```
- **Cap:** 80% reduction (20 armor points)
- **Toughness:** Giúp armor hoạt động tốt hơn với damage lớn
- **Breach enchant:** Giảm effective armor 15%/level

### 2. Enchantment Protection (EPF)
```
EPF_capped = min(20, total_EPF)
Protection = EPF_capped × 4%
Enchanted_Damage = Armor_Reduced_Damage × (1 - EPF_capped/25)
```

### 3. Weapon Damage
```
Final_Damage = (Base_Weapon + Sharpness_Bonus + Enchantment_Bonus) × (1 + 0.5×Strength_Level)
```
- Crit: ×1.5
- Cooldown: ×0.2 (minimum) đến ×1.0 (full)

### 4. Resistance Effect
- Mỗi level: -20% damage
- Resistance V: 100% immune

### 5. Damage Immunity
- **10 ticks (0.5 giây)** invulnerability sau khi nhận damage
- Mace smash attack có thể bypass?

---

## 📦 PVP ITEMS - DANH SÁCH ĐẦY ĐỦ

| Item | PVP Use | Codebase Ref |
|------|---------|:----------:|
| **END_CRYSTAL** | Crystal PVP core | ✅ CrystalAura |
| **OBSIDIAN** | Crystal base, defense | ✅ CrystalAura |
| **RESPAWN_ANCHOR** | Anchor PVP | ⚠️ BlockUtils |
| **GLOWSTONE** | Charge anchor | ❌ |
| **BED** | Bed PVP | ⚠️ BlockUtils/DamageUtils |
| **TNT** | TNT PVP | ❌ |
| **MACE** 🆕 | Mace PVP | ❌ **MISSING** |
| **WIND_CHARGE** 🆕 | Mace synergy | ❌ **MISSING** |
| **TOTEM_OF_UNDYING** | Survival | ✅ AutoTotem |
| **SHIELD** | Blocking | ✅ DamageUtils |
| **ENDER_PEARL** | Reposition | ✅ StashMover |
| **EXPERIENCE_BOTTLE** | Mending in combat | ❌ |
| **COBWEB** | Trap | ❌ |
| **FIREWORK_ROCKET** | Crossbow + Elytra | ⚠️ BetterFirework |
| **GOLDEN_APPLE** | Regen + absorption | ✅ EatSettings |
| **ENCHANTED_GOLDEN_APPLE** | Max regen + resist | ✅ EatSettings |
| **POTION** | Healing/Speed/FireRes | ❌ |
| **FLINT_AND_STEEL** | Ignite TNT | ✅ BlockUtils |

---

## ✅ NHỮNG GÌ CODEBASE ĐÃ CÓ TỐT

### Combat Modules
- CrystalAura (place/explode/prediction) ✅
- KillAura (auto attack/rotate/swap) ✅
- AutoTotem (multi-trigger) ✅
- AutoArmor (sorter/priority) ✅
- Criticals (Grim mode) ✅
- Surround / PlayerTrap ✅
- FakePlayer ✅
- AutoDisconnect (10+ triggers) ✅

### Combat Utilities
- `CombatUtils.crystalDamage()` ✅
- `DamageUtils.scale()` - Full armor/enchant/shield calc ✅
- `DamageUtils.fallDamage()` - Fall prediction ✅
- `ItemStackUtils.attackDamage/attackSpeed()` ✅
- `RotationUtils.lookAtEntity/lookAtBlock()` ✅
- `TargetingSettings.CombatSettings` - FOV/priority/validation ✅
- `HotbarManager` - Silent swap system ✅
- `EnchantmentUtils` - Enchant detection ✅
- `PlayerUtils.swingHand()` - Client/Server swing ✅

### Movement (PVP)
- Velocity (KB/explosion reduction) ✅
- TargetStrafe ✅
- Speed (Grim + NCP) ✅
- BackTrack (reach advantage) ✅
- TickShift ✅
- Sprint / SafeWalk / NoFall / NoJumpCooldown ✅

### Player (PVP)
- Reach (block + entity) ✅
- AntiAim (spin/jitter/player) ✅
- InventoryMove ✅
- NoForceRotate ✅
- PacketMine / FastBreak ✅
- Interact (delay/multi-action) ✅

### Render (PVP Support)
- ESP (Shader + Box) ✅
- Tracers ✅
- Nametags (health/ping/gear) ✅
- RadiusESP ✅
- FreeLook / Freecam ✅
- NoRender / ViewModel / Zoom / Fullbright ✅

---

## ❌ NHỮNG GÌ CÒN THIẾU (PRIORITY)

### 🔴 CRITICAL (Phải có cho 1.21+ Meta)

1. **MACE SUPPORT - Auto Weapon / Auto Mace**
   - Smash attack detection (player đang rơi + target trong range)
   - Auto switch to Mace khi có thể smash
   - Fall height calculation → optimal damage
   - Wind Burst chain detection
   - Density/Breach enchant management

2. **WIND CHARGE SYNERGY**
   - Auto shoot Wind Charge → launch → Mace smash
   - Predict landing position
   - Wind Burst follow-up

3. **ARROW/BOW TRAJECTORY PREDICTION**
   - Tính toán bullet drop
   - Predict target movement
   - Cần cho cả Bow và Crossbow

### 🟡 HIGH (Quan trọng)

4. **AUTO BOW** - Bow aim assist + release timing
5. **AUTO CROSSBOW** - Piercing/Multishot management
6. **AUTO ANCHOR** - Auto charge glowstone + activate
7. **AUTO BED** - Auto place + explode bed
8. **AUTO TNT** - Auto prime TNT gần target

### 🟢 MEDIUM (Nice to have)

9. **Auto Trident** - Riptide launch + Loyalty return
10. **Jump Reset module** - Auto jump khi nhận KB
11. **Inventory management** - Auto potion, auto gap
12. **Shield management** - Auto shield + axe swap

---

## 📂 DANH SÁCH TOÀN BỘ MODULES CẦN GIỮ

> ✅ **Tất cả module dưới đây đều cần được GIỮ LẠI.** Không xóa bất kỳ module nào trừ khi có lý do chính đáng.

---

### ⚔️ COMBAT (9 modules) - GIỮ TOÀN BỘ

| STT | File | Chức năng |
|:---:|------|-----------|
| 1 | `combat/KillAura.kt` | Auto attack entities, weapon swap, rotation |
| 2 | `combat/CrystalAura.kt` | Auto place + explode end crystals, damage calc, prediction |
| 3 | `combat/AutoTotem.kt` | Auto swap totem vào offhand (health/fall/crystal triggers) |
| 4 | `combat/AutoArmor.kt` | Auto mặc giáp tốt nhất (priority: protection → blast → projectile → fire) |
| 5 | `combat/Criticals.kt` | Force critical hit (Grim mode) |
| 6 | `combat/Surround.kt` | Bao quanh chân bằng obsidian/EChest |
| 7 | `combat/PlayerTrap.kt` | Trap player khác bằng blocks |
| 8 | `combat/FakePlayer.kt` | Spawn fake player để test damage/aim |
| 9 | `combat/autodisconnect/AutoDisconnect.kt` | Auto disconnect khi nguy hiểm (10+ triggers) |

---

### 🎯 PLAYER (18 modules) - GIỮ TOÀN BỘ

| STT | File | Chức năng | Loại |
|:---:|------|-----------|:----:|
| 1 | `player/Reach.kt` | Tăng reach block + entity | 🔴 PVP |
| 2 | `player/AntiAim.kt` | Fake rotation (spin/jitter/player/custom) | 🔴 PVP |
| 3 | `player/InventoryMove.kt` | Di chuyển khi mở inventory | 🔴 PVP |
| 4 | `player/PacketMine.kt` | Instant break blocks (packet-based) | 🔴 PVP |
| 5 | `player/FastBreak.kt` | Tăng tốc độ break block | 🔴 PVP |
| 6 | `player/Interact.kt` | Custom interact delay, multi-action | 🔴 PVP |
| 7 | `player/NoForceRotate.kt` | Chống server set rotation | 🔴 PVP |
| 8 | `player/RotationLock.kt` | Lock rotation axes | 🟡 Utility |
| 9 | `player/EndermanLook.kt` | Nhìn Enderman mà không aggro | 🟡 Utility |
| 10 | `player/ToolSaver.kt` | Auto swap tool trước khi break | 🟡 Utility |
| 11 | `player/AutoEat.kt` | Auto eat khi đói | 🟢 QoL |
| 12 | `player/AutoElytraSwap.kt` | Auto swap chestplate ↔ elytra | 🟢 QoL |
| 13 | `player/InventoryTweaks.kt` | Clean inventory, shulker unstack | 🟢 QoL |
| 14 | `player/StackReplenish.kt` | Auto replenish hotbar stacks | 🟢 QoL |
| 15 | `player/InventoryResync.kt` | Resync inventory với server | 🟢 QoL |
| 16 | `player/PortalGui.kt` | Mở GUI khi ở trong portal | 🟢 QoL |
| 17 | `player/AntiAFK.kt` | Chống AFK kick | 🟢 QoL |
| 18 | `player/ClickFriend.kt` | Middle-click add friend | 🟢 QoL |
| 19 | `player/Replay.kt` | Ghi lại và replay chuyển động | 🟢 QoL |

---

### 🏃 MOVEMENT (18 modules) - GIỮ TOÀN BỘ

| STT | File | Chức năng | Loại |
|:---:|------|-----------|:----:|
| 1 | `movement/Velocity.kt` | Chống knockback/explosion | 🔴 PVP |
| 2 | `movement/TargetStrafe.kt` | Strafe quanh target | 🔴 PVP |
| 3 | `movement/BackTrack.kt` | Reach advantage (rewind position) | 🔴 PVP |
| 4 | `movement/TickShift.kt` | Shift tick để bảo toàn velocity | 🔴 PVP |
| 5 | `movement/Speed.kt` | Speed hacks (Grim + NCP modes) | 🟡 Movement |
| 6 | `movement/Sprint.kt` | Auto sprint | 🟡 Movement |
| 7 | `movement/SafeWalk.kt` | Không rơi khỏi edge | 🟡 Movement |
| 8 | `movement/NoFall.kt` | Chống fall damage | 🟡 Movement |
| 9 | `movement/NoJumpCooldown.kt` | Bỏ cooldown jump | 🟡 Movement |
| 10 | `movement/EntityControl.kt` | Điều khiển entity (horse, etc.) | 🟡 Movement |
| 11 | `movement/Blink.kt` | Delay packets, teleport effect | 🟡 Movement |
| 12 | `movement/Timer.kt` | Tăng/giảm game speed | 🟡 Movement |
| 13 | `movement/Jesus.kt` | Đi trên nước/lava | 🟡 Movement |
| 14 | `movement/AutoWalk.kt` | Auto walk forward | 🟡 Movement |
| 15 | `movement/AutoSpiral.kt` | Auto spiral movement | 🟡 Movement |
| 16 | `movement/BetterFirework.kt` | Auto use firework khi fly | 🟡 Movement |
| 17 | `movement/ElytraAltitudeControl.kt` | Kiểm soát độ cao elytra | 🟡 Movement |
| 18 | `movement/elytrafly/` (cả package) | Elytra Fly modes (Bounce/GrimControl...) | 🟡 Movement |

---

### 👁️ RENDER (21 modules) - GIỮ TOÀN BỘ

| STT | File | Chức năng | Loại |
|:---:|------|-----------|:----:|
| 1 | `render/Esp.kt` | ESP qua blocks (shader + box) | 🔴 PVP |
| 2 | `render/Tracers.kt` | Vẽ line tới entities | 🔴 PVP |
| 3 | `render/Nametags.kt` | Nametags với health/ping/gear | 🔴 PVP |
| 4 | `render/RadiusESP.kt` | ESP configurable range | 🔴 PVP |
| 5 | `render/FreeLook.kt` | Xoay camera độc lập (self) | 🔴 PVP |
| 6 | `render/Freecam.kt` | Tách camera khỏi body (spectate) | 🔴 PVP |
| 7 | `render/NoRender.kt` | Ẩn particles/fire/hurtcam | 🟡 Utility |
| 8 | `render/ViewModel.kt` | Tùy chỉnh view model | 🟡 Utility |
| 9 | `render/Zoom.kt` | Zoom + scroll to zoom | 🟡 Utility |
| 10 | `render/Fullbright.kt` | Full brightness / night vision | 🟡 Utility |
| 11 | `render/BlockOutline.kt` | Block outline highlight | 🟡 Utility |
| 12 | `render/Bobbing.kt` | Camera bobbing | 🟡 Utility |
| 13 | `render/CameraTweaks.kt` | Camera adjustments | 🟡 Utility |
| 14 | `render/Time.kt` | Fix time | 🟡 Utility |
| 15 | `render/Weather.kt` | Fix weather | 🟡 Utility |
| 16 | `render/WorldColors.kt` | Tùy chỉnh world colors | 🟡 Utility |
| 17 | `render/LightLevels.kt` | Hiển thị light level | 🟢 QoL |
| 18 | `render/Search.kt` | Search blocks by type | 🟢 QoL |
| 19 | `render/XRay.kt` | XRay view | 🟢 QoL |
| 20 | `render/ExtraTab.kt` | Tab list mở rộng | 🟢 QoL |
| 21 | `render/ContainerPreview.kt` | Xem container không cần mở | 🟢 QoL |
| 22 | `render/MapPreview.kt` | Preview map items | 🟢 QoL |

---

### 🌍 WORLD (10 modules) - GIỮ TOÀN BỘ

| STT | File | Chức năng |
|:---:|------|-----------|
| 1 | `world/Scaffold.kt` | Auto place blocks dưới chân |
| 2 | `world/Nuker.kt` | Break blocks theo shape/range |
| 3 | `world/Printer.kt` | Place blocks theo schematic |
| 4 | `world/AirPlace.kt` | Place blocks trong không khí |
| 5 | `world/AutoPortal.kt` | Auto light + enter portal |
| 6 | `world/AutoSign.kt` | Auto write sign |
| 7 | `world/HighwayTools.kt` | Highway building tools |
| 8 | `world/AutoVillagerCycle.kt` | Auto cycle villager trades |
| 9 | `world/MapDownloader.kt` | Download map from server |
| 10 | `world/StashMover.kt` | Auto move items between stashes |

---

### 💬 CHAT (5 modules) - GIỮ TOÀN BỘ

| STT | File | Chức năng |
|:---:|------|-----------|
| 1 | `chat/AntiSpam.kt` | Chống spam, filter chat |
| 2 | `chat/ChatTimestamp.kt` | Thêm timestamp vào chat |
| 3 | `chat/CustomChat.kt` | Custom chat format |
| 4 | `chat/FancyChat.kt` | Fancy/colored chat (small caps, symbol) |
| 5 | `chat/FriendHighlight.kt` | Highlight friend names trong chat |

---

### ⚙️ CLIENT (4 modules) - GIỮ TOÀN BỘ

| STT | File | Chức năng |
|:---:|------|-----------|
| 1 | `client/Client.kt` | Client settings |
| 2 | `client/Capes.kt` | Custom capes |
| 3 | `client/Discord.kt` | Discord Rich Presence |
| 4 | `client/ModuleNotifier.kt` | Notify khi toggle module |

---

### 📡 NETWORK (5 modules) - GIỮ TOÀN BỘ

| STT | File | Chức năng |
|:---:|------|-----------|
| 1 | `network/PacketLogger.kt` | Log network packets |
| 2 | `network/PacketDelay.kt` | Delay packets |
| 3 | `network/PacketLimiter.kt` | Limit packet rate |
| 4 | `network/Rubberband.kt` | Chống rubberband |
| 5 | `network/ServerSpoof.kt` | Spoof server info |

---

### 🖥️ HUD (9 modules) - GIỮ TOÀN BỘ

| STT | File | Chức năng |
|:---:|------|-----------|
| 1 | `hud/Watermark.kt` | Client logo watermark |
| 2 | `hud/Coordinates.kt` | Tọa độ XYZ |
| 3 | `hud/Fps.kt` | FPS counter |
| 4 | `hud/Tps.kt` | Server TPS |
| 5 | `hud/Speedometer.kt` | Player speed |
| 6 | `hud/Rotation.kt` | Yaw/Pitch display |
| 7 | `hud/AccountName.kt` | Account name display |
| 8 | `hud/ModuleList.kt` | Danh sách module đang bật |
| 9 | `hud/TaskFlowHud.kt` | Task execution display |

---

### 📊 HUD MODULES (Core hỗ trợ tất cả HUD modules)

| File | Chức năng |
|------|-----------|
| `module/HudModule.kt` | Base class cho HUD modules |
| `module/Module.kt` | Base class cho tất cả modules |
| `module/ModuleRegistry.kt` | Module registration + lifecycle |
| `module/tag/ModuleTag.kt` | Module categorization tags |

---

## 🛠️ UTILITIES CẦN GIỮ (Không phải module)

```
src/main/kotlin/com/minato/util/combat/           → 🔴 GIỮ (PVP damage calc, combat helpers)
src/main/kotlin/com/minato/util/item/              → 🔴 GIỮ (Item/stack utils, weapon stats)
src/main/kotlin/com/minato/util/player/            → 🔴 GIỮ (Rotation, slots, player helpers)
src/main/kotlin/com/minato/util/EnchantmentUtils.kt → 🔴 GIỮ (Enchant detection)

src/main/kotlin/com/minato/config/blocks/          → 🟡 GIỮ (Config blocks: targeting, combat, break)
src/main/kotlin/com/minato/interaction/managers/   → 🟡 GIỮ (Hotbar, rotation, break, place managers)
src/main/kotlin/com/minato/gui/                    → 🟢 GIỮ (GUI framework, ClickGui, HUD layout)
src/main/kotlin/com/minato/graphics/               → 🟢 GIỮ (Render pipeline, textures, fonts)
src/main/kotlin/com/minato/sound/                  → 🟢 GIỮ (Sound system)
src/main/kotlin/com/minato/event/                  → 🟢 GIỮ (Event system)
src/main/kotlin/com/minato/command/                → 🟢 GIỮ (Command system)
src/main/kotlin/com/minato/brigadier/              → 🟢 GIỮ (Brigadier command DSL)
src/main/kotlin/com/minato/config/                 → 🟢 GIỮ (Config system)
src/main/kotlin/com/minato/network/                → 🟢 GIỮ (Network/API)
src/main/kotlin/com/minato/task/                   → 🟢 GIỮ (Task system)
src/main/kotlin/com/minato/threading/              → 🟢 GIỮ (Threading utilities)
src/main/kotlin/com/minato/context/                → 🟢 GIỮ (SafeContext)
```

---

### 🔴 PVP = Quan trọng cho PVP
### 🟡 Utility = Hữu ích trong PVP
### 🟢 QoL = Quality of Life (không ảnh hưởng PVP)

**Lưu ý:** Tổng cộng **107 modules** (bao gồm HUD + Core) + **toàn bộ utilities và framework.** Không xóa bất kỳ module nào vì tất cả đều là một phần của client.

---

## 📊 KẾT LUẬN

1. **Codebase hiện tại có PVP coverage khoảng 60%** 
2. **Crystal PVP** là mạnh nhất - đã có đầy đủ
3. **Mace PVP (1.21+)** hoàn toàn chưa có - **cần ưu tiên phát triển**
4. **Bow/Crossbow/Anchor/Bed/TNT** cũng chưa có
5. **Các module combat hiện tại KHÔNG được xóa**
6. **Không có thay đổi PVP giữa 1.21.1 và 1.21.4** - codebase 1.21.1 vẫn compatible
