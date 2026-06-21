package dev.herobrine34187.meklasercompat;

import net.neoforged.fml.common.Mod;

/**
 * MekLaser-Sable Compat - Fixes Mekanism laser particle rendering direction
 * when lasers are placed on Sable physics structures (SubLevels).
 * <p>
 * Without this fix, Mekanism lasers on rotating Sable structures always point
 * along the absolute world axes (e.g., north-south), ignoring the structure's rotation.
 * <p>
 * Created 2025-06-17:16-40
 * <p>
 * MekLaser-Sable兼容模组 - 修复Mekanism激光粒子在Sable物理结构（子级）上的渲染方向。
 * 没有此修复时，位于旋转Sable结构上的Mekanism激光始终指向绝对世界轴（例如南北方向），
 * 忽略结构的旋转。
 * 创建于 2025-06-17:16-40
 */
@Mod("meklasercompat")
public class MekLaserCompat {
}
