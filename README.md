# Mekanism Laser Fix for NeoForge 1.21.1

## 中文 | Chinese

### 项目简介
这是一个 Minecraft NeoForge 1.21.1 模组项目，旨在修复 Mekanism 激光器在 Sable 物理结构上的不正确表现。

### 问题描述
截至 Mekanism-1.21.1-10.7.19.85 与 sable-neoforge-1.21.1-2.0.3，当 Mekanism 激光器放置于物理结构上且指向非坐标轴方向时，存在以下两个问题：
- 激光杀伤生物的范围为一个扇形区域，该区域由激光指向在坐标轴方向上的分量围成，而非正常的直线路径。
- 激光束粒子效果会在某一位置固定呈南北走向进行渲染，不随激光器指向变化。

### 修复内容
本项目通过对 Mekanism 的 `LaserParticle` 类和 `TileEntityBasicLaser` 类进行 Mixin 注入，修复了上述两个问题：
- 现在激光器只会杀伤指向路径上的生物（直线伤害判定）。
- 激光束粒子效果会跟随激光器的指向正确渲染，方向实时更新。

### 影响范围
- 仅影响**位于物理结构上**的激光器。
- 不在物理结构上的激光器不受影响，保持原版行为。

---

## English | 英文

### Project Description
This is a Minecraft NeoForge 1.21.1 mod project that aims to fix incorrect behaviors of Mekanism lasers when mounted on Sable physics structures.

### Issues
As of Mekanism-1.21.1-10.7.19.85 and sable-neoforge-1.21.1-2.0.3, when a Mekanism laser is placed on a physics structure and points in a non-axis-aligned direction, two issues occur:
- The laser's damage area becomes a fan‑shaped region bounded by the coordinate‑axis components of the laser's direction, instead of a straight line.
- The laser beam particle effect is rendered as fixed north‑south at a certain position, regardless of the laser's actual orientation.

### Fixes
This project uses Mixin injections on Mekanism's `LaserParticle` class and `TileEntityBasicLaser` class to resolve both issues:
- The laser now damages only entities along its direct pointing path (straight‑line hit detection).
- The laser beam particle effect correctly follows the laser's orientation and updates in real time.

### Scope
- Only affects lasers **mounted on physics structures**.
- Lasers not on physics structures remain unchanged and behave as in vanilla Mekanism.

---

## Compatibility / 兼容性
- Minecraft: 1.21.1  
- NeoForge: 1.21.1  
- Mekanism: 1.21.1-10.7.19.85+  
- Sable: neoforge-1.21.1-2.0.3+

## License / 许可
MIT
