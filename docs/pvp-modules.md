# Minato Client - Tài Liệu PVP Modules

> **Ghi chú:** Một số module có thể đã bị xóa (BaritoneTest, Baritone HUD, BaritoneHandler). 
> Các module được liệt kê bên dưới là những module còn hoạt động.

---

## 🗂 Mục Lục
1. [Combat Modules](#combat-modules)
2. [Player Modules](#player-modules)
3. [Movement Modules (PVP-related)](#movement-modules-pvp-related)
4. [Render Modules (PVP Support)](#render-modules-pvp-support)
5. [Network Modules (Utility)](#network-modules-utility)

---

## Combat Modules

### 1. KillAura
- **File:** `combat/KillAura.kt`
- **Tag:** `COMBAT`
- **Mô tả:** Tự động tấn công entity
- **Settings:**
  - `Rotate` - Tự động xoay người về phía mục tiêu
  - `Swap` - Tự động chuyển sang item có damage cao nhất
  - `Disable While Gliding` - Tắt khi đang bay elytra
  - `Damage Mode` - Chế độ tính damage: `Dps` (Damage Per Second) hoặc `Total` (Hit Damage)
  - `Attack Mode` - Chế độ tấn công: `Cooldown` (dựa trên cooldown) hoặc `Delay` (delay cố định)
  - `Cooldown Offset` - Giảm cooldown cần thiết (0-5 ticks)
  - `Hit Delay 1` / `Hit Delay 2` - Khoảng delay ngẫu nhiên giữa các đòn đánh
  - **Targeting:** Cấu hình targeting riêng

### 2. CrystalAura
- **File:** `combat/CrystalAura.kt`
- **Tag:** `COMBAT`
- **Mô tả:** Tự động tấn công entities bằng end crystals (Place & Explode)
- **Settings:**
  - **General:** `Rotate`, `Update Mode` (Async/Ticked), `Update Delay`, `Max Updates Per Frame`, `Debug`
  - **Placement:** `Place Range` (1-7 blocks), `Place Delay`, `Swap`, `Swap Hand`, `Crystal Priority` (Damage/Advantage), `Min Damage Advantage`, `Min Target Damage`, `Max Self Damage`, `Min Place Health`, `Prevent Death`, `1.12 Placement`
  - **Exploding:** `Explode Range`, `Explode Delay`
  - **Prediction:** `Prediction Mode` (None/Packet/Deferred/Mixed), `Packet Predictions`, `Place Post Pause`, `Place Predictions`, `Packet Lifetime`
  - **Targeting:** Cấu hình targeting riêng

### 3. AutoTotem
- **File:** `combat/AutoTotem.kt`
- **Tag:** `COMBAT`
- **Mô tả:** Tự động chuyển totem vào offhand
- **Settings:**
  - `Always` - Luôn giữ totem trong offhand
  - `Ignore When Holding` - Bỏ qua nếu đã cầm totem
  - `Min Health` - Ngưỡng máu tối thiểu để swap (6-36 half-hearts)
  - `Falls` - Swap khi sắp chết vì rơi
  - `Fall Distance` - Khoảng cách rơi để swap
  - `Crystals` - Swap khi crystal explosion gây chết
  - `Creepers` - Swap khi creeper gần
  - `Players` - Swap khi player gần
  - `Player Distance`, `Friends` - Cấu hình phát hiện player

### 4. AutoArmor
- **File:** `combat/AutoArmor.kt`
- **Tag:** `COMBAT`
- **Mô tả:** Tự động mặc giáp dựa trên sức mạnh và enchant
- **Settings:**
  - `Elytra Priority` - Ưu tiên elytra hơn chestplate
  - `Toggle Elytra Priority` - Keybind chuyển đổi ưu tiên
  - `Min Durability` - Độ bền tối thiểu trước khi thay
  - `Preferred Protection` cho từng slot: Protection/Blast/Projectile/Fire Protection
  - `Ignore Binding` - Bỏ qua Curse of Binding
  - **Sorter:** So sánh armor dựa trên: durability → elytra priority → armor value → toughness → enchant → unbreaking/mending

### 5. Criticals
- **File:** `combat/Criticals.kt`
- **Tag:** `COMBAT`
- **Mô tả:** Force các đòn đánh thành critical hit
- **Settings:**
  - `Mode` - Hiện chỉ có `Grim` mode (gửi position packet với offset nhỏ để trigger critical)

### 6. Surround
- **File:** `combat/Surround.kt`
- **Tag:** `COMBAT`
- **Mô tả:** Bao quanh chân player bằng blocks (obsidian, ender chest, crying obsidian)
- **Settings:**
  - `Blocks` - Danh sách block được dùng
  - Tự động xây blocking pattern xung quanh player

### 7. PlayerTrap
- **File:** `combat/PlayerTrap.kt`
- **Tag:** `COMBAT`
- **Mô tả:** Bao quanh player khác bằng blocks (trap opponent)
- **Settings:**
  - `Blocks` - Danh sách block được dùng
  - `Friends` - Có trap friends không
  - `Self` - Trap chính mình
  - Sử dụng `getTrapPositions()` để tính toán vị trí bao quanh

### 8. FakePlayer
- **File:** `combat/FakePlayer.kt`
- **Tag:** `COMBAT`
- **Mô tả:** Spawn fake player để test combat
- **Settings:**
  - `Name` - Tên của fake player
  - Fetch profile từ Mojang API
  - Tự động copy inventory và position từ player thật

### 9. AutoDisconnect
- **File:** `combat/autodisconnect/AutoDisconnect.kt`
- **Tag:** `COMBAT`
- **Mô tả:** Tự động disconnect khi nguy hiểm (low health, crystal, creeper, player gần...)
- **Settings (Triggers tab):**
  - `Health` - Disconnect khi máu thấp (Smart Toggle, Re-enable Threshold)
  - `Y Level` - Disconnect khi xuống quá sâu
  - `Falls` - Disconnect khi rơi
  - `Crystals` - Disconnect khi crystal gần (Player Near / Projectile Near)
  - `Creepers` - Disconnect khi creeper gần
  - `Totem` - Disconnect khi hết totem
  - `Players` - Disconnect khi player gần (Ignore Friends)
  - `Armor` - Disconnect khi giáp hết durability
  - `Entity` - Disconnect khi entity type cụ thể gần
  - `Damage` - Disconnect khi nhận damage type cụ thể
- **Settings (General tab):**
  - `Packet Disconnect Methods`: Invalid Hotbar Slot, Attack Self, Impossible Chat Timestamp
- **Settings (Display tab):**
  - Hiển thị disconnect screen chi tiết (coordinates, time, inventory, nearby players/entities, effects, server TPS/ping...)

---

## Player Modules

### 1. Reach
- **File:** `player/Reach.kt`
- **Tag:** `PLAYER`
- **Mô tả:** Tăng tầm với của player
- **Settings:**
  - `Block Reach` (0-10.0) - Tầm với block
  - `Entity Reach` (0-10.0) - Tầm với entity

### 2. AntiAim
- **File:** `player/AntiAim.kt`
- **Tag:** `MOVEMENT`
- **Mô tả:** Xoay người theo cấu hình để chống aim (anti-cheat bypass/aim assist counter)
- **Settings (Yaw):**
  - `Yaw Mode`: `None`, `Spin`, `Jitter`, `Sideways`, `Backwards`, `Custom`, `Player`
  - `Spin Mode`: Left/Right
  - `Side Mode`: Left/Right
  - `Custom Yaw` (-179..180)
  - `Yaw Player Mode`: Closest/Farthest/Random
  - `Yaw Speed` (1-90°/tick)
- **Settings (Pitch):**
  - `Pitch Mode`: `None`, `UpAndDown`, `Jitter`, `Vertical`, `Custom`, `Player`
  - `Vertical Mode`: Up/Down
  - `Custom Pitch` (-90..90)
  - `Pitch Player Mode`: Closest/Farthest/Random
  - `Pitch Speed` (1-90°/tick)

### 3. PacketMine
- **File:** `player/PacketMine.kt`
- **Tag:** `PLAYER`
- **Mô tả:** Break block nhanh hơn qua packet, queue blocks, hỗ trợ rebreak
- **Settings:**
  - `Ignore When Holding` - Items không kích hoạt break khi cầm
  - `Rebreak Mode`: Manual/Auto
  - `Break Radius` (0-5) - Break nhiều block cùng lúc
  - `Flatten` - Không break block dưới chân
  - `Queue`/`Queue Order` - Queue blocks để break
  - **Renders:** Rebreak color, Queue renders (State/Box, dynamic colors)

### 4. FastBreak
- **File:** `player/FastBreak.kt`
- **Tag:** `PLAYER`
- **Mô tả:** Break block siêu tốc (bypass vanilla check)
- **Settings:**
  - Tự động cancel break events để xử lý nhanh hơn
  - Hỗ trợ simulation và automation config

### 5. Interact
- **File:** `player/Interact.kt`
- **Tag:** `PLAYER`
- **Mô tả:** Modify tương tác với thế giới
- **Settings:**
  - `Item Use / Place Delay` (0-20 ticks) - Delay giữa các lần place/use
  - `Multi Action` - Cho phép dùng item trong khi breaking

### 6. InventoryMove
- **File:** `player/InventoryMove.kt`
- **Tag:** `PLAYER`
- **Mô tả:** Di chuyển khi đang mở GUI
- **Settings:**
  - `ClickGui` - Cho phép di chuyển khi mở ClickGUI
  - `Disable Sneak` - Tắt sneak khi di chuyển trong GUI
  - `Arrow Keys` - Xoay camera bằng phím mũi tên
  - `Rotation Speed` (1-20°/tick)
  - Rotation mode: Instant Lock

### 7. NoForceRotate
- **File:** `player/NoForceRotate.kt`
- **Tag:** `PLAYER`
- **Mô tả:** Ngăn server set rotation của player

### 8. RotationLock
- **File:** `player/RotationLock.kt`
- **Tag:** `PLAYER`
- **Mô tả:** Khóa rotation của player

### 9. EndermanLook
- **File:** `player/EndermanLook.kt`
- **Tag:** `PLAYER`
- **Mô tả:** Ngăn enderman aggro khi nhìn vào

### 10. ToolSaver
- **File:** `player/ToolSaver.kt`
- **Tag:** `PLAYER`
- **Mô tả:** Tự động chuyển tool trước khi hết durability

### 11. AutoEat
- **File:** `player/AutoEat.kt`
- **Tag:** `PLAYER`
- **Mô tả:** Tự động ăn khi đói

### 12. AutoElytraSwap
- **File:** `player/AutoElytraSwap.kt`
- **Tag:** `PLAYER`
- **Mô tả:** Tự động swap elytra/chestplate

---

## Movement Modules (PVP-related)

### 1. Velocity
- **File:** `movement/Velocity.kt`
- **Tag:** `MOVEMENT`
- **Mô tả:** Modify velocity của player
- **Settings:**
  - `Pushed` - Chống đẩy từ entity
  - `Knockback` - Chống knockback khi bị đánh
  - `Explosion` - Chống knockback từ explosion

### 2. TargetStrafe
- **File:** `movement/TargetStrafe.kt`
- **Tag:** `MOVEMENT`
- **Mô tả:** Tự động strafe quanh entity (kết hợp với KillAura)
- **Settings:**
  - `Strafe Distance` (0-5.0)
  - `Jitter Compensation` (0-1.0)
  - `Stabilize`: None/Weak/Normal/Strong
  - Tự động đổi hướng khi collide với wall

### 3. Speed
- **File:** `movement/Speed.kt`
- **Tag:** `MOVEMENT`
- **Mô tả:** Tăng tốc độ đi bộ (Grim Strafe + NCP Strafe)
- **Settings:**
  - `Mode`: GrimStrafe / NcpStrafe
  - **Grim:** `Diagonal`, `Boat Boost`
  - **NCP:** `Strict`, `Lower Jump`, `Auto Jump`, `Timer Boost`
  - NCP có state machine riêng cho strafe timing

### 4. Sprint
- **File:** `movement/Sprint.kt`
- **Tag:** `MOVEMENT`
- **Mô tả:** Tự động sprint

### 5. SafeWalk
- **File:** `movement/SafeWalk.kt`
- **Tag:** `MOVEMENT`
- **Mô tả:** Ngăn rơi khỏi edge (shift tự động)

### 6. NoFall
- **File:** `movement/NoFall.kt`
- **Tag:** `MOVEMENT`
- **Mô tả:** Ngăn damage từ rơi

### 7. NoJumpCooldown
- **File:** `movement/NoJumpCooldown.kt`
- **Tag:** `MOVEMENT`
- **Mô tả:** Bỏ cooldown jump

### 8. TickShift
- **File:** `movement/TickShift.kt`
- **Tag:** `MOVEMENT`
- **Mô tả:** Tích trữ ticks khi đứng yên, xả ra khi di chuyển để tăng tốc

### 9. Blink
- **File:** `movement/Blink.kt`
- **Tag:** `MOVEMENT`
- **Mô tả:** Delay packets để tạo blink effect

### 10. BackTrack
- **File:** `movement/BackTrack.kt`
- **Tag:** `MOVEMENT`
- **Mô tả:** Delay packets để tạo reach advantage (giữ entity ở vị trí cũ)
- **Settings:**
  - `Outbound` - Delay outbound packets
  - `Mode`: Fixed/Ranged/Adaptive
  - `Delay` (100-2000ms)
  - `Max Delay` (100-2000ms)
  - `Distance` (1-5.0)
  - Tương thích với KillAura target

### 11. ElytraFly + ElytraAltitudeControl + BetterFirework
- **File:** `movement/elytrafly/ElytraFly.kt` + liên quan
- **Tag:** `MOVEMENT`
- **Mô tả:** Fly với elytra, altitude control, firework optimization

### 12. AutoWalk
- **File:** `movement/AutoWalk.kt`
- **Tag:** `MOVEMENT`
- **Mô tả:** Tự động đi forward

### 13. Jesus
- **File:** `movement/Jesus.kt`
- **Tag:** `MOVEMENT`
- **Mô tả:** Đi trên nước

---

## Render Modules (PVP Support)

### 1. ESP
- **File:** `render/Esp.kt`
- **Tag:** `RENDER`
- **Mô tả:** Highlight entities với smooth interpolated rendering
- **Settings:**
  - `Mode`: Shader / Box
  - `Depth Test`
  - **Shader:** Outline Style Config
  - **Box:** Fill/Outline alpha, width
  - **Entities/Colors:** Entity Selection, Entity Color Settings
  - Hỗ trợ block entities

### 2. Tracers
- **File:** `render/Tracers.kt`
- **Tag:** `RENDER`
- **Mô tả:** Vẽ đường đến entities
- **Settings:**
  - `Target`: Feet/Middle/Eyes
  - `Stem` - Vẽ đường từ chân đến mắt
  - Friends line / Other line config
  - Entity selection & colors
  - Hỗ trợ dash style

### 3. Nametags
- **File:** `render/Nametags.kt`
- **Tag:** `RENDER`
- **Mô tả:** Hiển thị thông tin entity trên đầu (tên, health, ping, gear, enchant)
- **Settings:**
  - **General:** `Text Size`, `Item Scale`, `Y Offset`, `Spacing`, `Health`, `Ping`, `Gear`, `Main Item`, `Offhand Item`, `Item Name`, `Item Name Scale`, `Item Count`, `Durability Mode`
  - **Entity:** Entity Selection (no block entities)
  - **Text:** Friends/Other text style
  - **Background:** Background color/size
  - Hiển thị armor + hand items như mini-inventory

### 4. RadiusESP
- **File:** `render/RadiusESP.kt`
- **Tag:** `RENDER`
- **Mô tả:** Hiển thị radius của beacon và spawner
- **Settings:**
  - `Beacons` / `Show Max Beacon Range`
  - `Spawners`
  - Colors, fill/outline config

### 5. Search
- **File:** `render/Search.kt`
- **Tag:** `RENDER`
- **Mô tả:** Tìm và highlight block cụ thể trong world

### 6. BlockOutline
- **File:** `render/BlockOutline.kt`
- **Tag:** `RENDER`
- **Mô tả:** Custom block outline

### 7. FreeLook
- **File:** `render/FreeLook.kt`
- **Tag:** `PLAYER`
- **Mô tả:** Nhìn xung quanh mà không xoay người (camera control)

### 8. Zoom
- **File:** `render/Zoom.kt`
- **Tag:** `RENDER`
- **Mô tả:** Zoom camera

### 9. NoRender
- **File:** `render/NoRender.kt`
- **Tag:** `RENDER`
- **Mô tả:** Tắt render không cần thiết (tối ưu performance, tắt entities, particles...)

### 10. ViewModel
- **File:** `render/ViewModel.kt`
- **Tag:** `RENDER`
- **Mô tả:** Modify view model (hand position, swing animation)

### 11. XRay
- **File:** `render/XRay.kt`
- **Tag:** `RENDER`
- **Mô tả:** Xuyên thấu blocks (tìm ores, chests...)

### 12. Fullbright
- **File:** `render/Fullbright.kt`
- **Tag:** `RENDER`
- **Mô tả:** Tăng độ sáng (gamma)

### 13. CameraTweaks
- **File:** `render/CameraTweaks.kt`
- **Tag:** `RENDER`
- **Mô tả:** Tinh chỉnh camera (FOV, distance, clip)

### 14. Freecam
- **File:** `render/Freecam.kt`
- **Tag:** `RENDER`
- **Mô tả:** Free camera mode (tách camera khỏi player)
- **Settings:**
  - `Mode`: Free / FollowPlayer
  - `Speed`, `Sprint Multiplier`, `Reach`, `Rotate Mode`, `Relative`, `Keep Y Level`
  - **FollowPlayer:** String Length, Track Player

### 15. ExtraTab
- **File:** `render/ExtraTab.kt`
- **Tag:** `RENDER`
- **Mô tả:** Mở rộng player tab list

---

## Network Modules (Utility)

### 1. Rubberband
- **File:** `network/Rubberband.kt`
- **Tag:** `NETWORK`
- **Mô tả:** Hiển thị thông tin về rubberbands (position revert)
- **Settings:**
  - `Show Last Packet`, `Show Connection State`, `Show Rubberband Info`
  - Lưu 100 position packets gần nhất

### 2. PacketLogger
- **File:** `network/PacketLogger.kt`
- **Tag:** `NETWORK`
- **Mô tả:** Log packets

### 3. PacketLimiter
- **File:** `network/PacketLimiter.kt`
- **Tag:** `NETWORK`
- **Mô tả:** Giới hạn packets để tránh flag

### 4. PacketDelay
- **File:** `network/PacketDelay.kt`
- **Tag:** `NETWORK`
- **Mô tả:** Delay packets

### 5. ServerSpoof
- **File:** `network/ServerSpoof.kt`
- **Tag:** `NETWORK`
- **Mô tả:** Spoof server info

---

## HUD Modules (Thông tin PVP)

| Module | Mô tả |
|--------|-------|
| **Speedometer** | Hiển thị speed hiện tại |
| **Coordinates** | Tọa độ |
| **Fps** | FPS counter |
| **Rotation** | Yaw/Pitch hiện tại |
| **ModuleList** | Danh sách module đang bật |
| **AccountName** | Tên account |
| **Tps** | Server TPS |
| **Watermark** | Watermark client |

---

## Ghi chú

- **Targeting Settings:** Nhiều module combat sử dụng chung `TargetingSettings.CombatSettings` để filter target (players, mobs, invis, friends...)
- **Automation Config:** Hầu hết module combat đều có `AutomationConfig` riêng để cấu hình build/break/hotbar/inventory behavior
- **Rotation Manager:** Tất cả rotation đều qua `RotationManager` support silent rotation, lock, và smooth transition
