# 🧪 Weird & Wild Ideas (The Mad Lab)

> "Crazy ideas that might just redefine LAN apps and Android UX."

---

## 1. 🟢 The Omniverse Dial (Omnitrix Hub)
*Inspired by Ben 10 Omniverse dial selector.*

### 💡 Concept:
Instead of scrolling a boring row of 8+ icons on the TopAppBar (which is unreachable with one hand), tapping the **Hub / Apps** button launches an **Omnitrix-style Radial Wheel**:

1. **Background**: Deep frosted blur (Glassmorphism + dim).
2. **Thumb Zone Dial**: A circular dial appears at the bottom-right/bottom-center where the thumb naturally rests.
3. **Rotary Haptics**: Swiping or rotating the dial produces crisp mechanical haptic clicks (*tick-tick-tick*).
4. **Holographic Selection**:
   - As the wheel turns, the active feature pops into 3D scale with glowing neon ring (Intra Purple / Alien Green).
   - Icons cycle: `Live TV` ➔ `WebDrop` ➔ `IntraDrive` ➔ `SMB Explorer` ➔ `Smart Home` ➔ `Settings`.
   - Name flashes in modern futuristic typography in the dial core.
5. **Slam / Tap to Launch**: Tapping the dial center "slams" it down and launches the feature instantly with a sleek expand animation.

### 🛠️ Technical Feasibility:
- **Jetpack Compose**: `Canvas` + `pointerInput` with `detectDragGestures` to calculate angular rotation `atan2(y, x)`.
- **HapticFeedback**: `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.TextHandleMove)`.
- **Graphics**: `graphicsLayer` rotation and scale animation with Spring spec.

---

## 2. 📡 Radar PING / Sonar LAN Discovery
- Instead of static progress spinner during IP scan, display a **Subnet Sonar Radar** sweeping across 360 degrees.
- Devices light up as glowing green blips with IP / Hostname floating next to them.

---

## 3. 🛸 WebDrop Beam (AirDrop with Physics)
- Dragging a file towards the top or edge of the screen gives an inertia-based "flick" launch into the LAN wormhole.
- Particle effects when a peer receives it.
