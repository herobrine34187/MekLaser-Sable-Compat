package dev.herobrine34187.meklasercompat.mixin;

import dev.herobrine34187.meklasercompat.tools.GetLaserToVector3dTool;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.util.SableDistUtil;
import mekanism.client.particle.LaserParticle;
import mekanism.common.lib.math.Pos3D;
import net.minecraft.core.Direction;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LaserParticle.Factory.class)
public class LaserParticleFactoryMixin {

    /**
     * Fix end position initialization.
     * / 修复end值的初始化
     * <p>
     * Uses bounding-box intersection to find the SubLevel containing the laser block,
     * because chunk-based lookup ({@code getContainingClient}) fails on world-space
     * coordinates transformed by {@code fixFrom}.
     * / 使用包围盒交集查找包含激光方块的SubLevel，
     * 因为基于区块的查找（getContainingClient）无法处理fixFrom变换后的世界坐标。
     * <p>
     * Modified 2026-06-26:17-10 — replaced getContainingClient with getAllIntersecting
     * / 修改于 2026-06-26:17-10 — 将getContainingClient替换为getAllIntersecting
     * */
    @Redirect(
            method = "createParticle(Lmekanism/common/particle/LaserParticleData;Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDD)Lmekanism/client/particle/LaserParticle;",
            at = @At(
                    value = "INVOKE",
                    target = "Lmekanism/common/lib/math/Pos3D;translate(Lnet/minecraft/core/Direction;D)Lmekanism/common/lib/math/Pos3D;",
                    ordinal = 0
            )
    )
    private Pos3D meklas$fixEnd(Pos3D start, Direction direction, double amount) {
        // Query a small bounding box around the start position to find intersecting SubLevels
        // / 在start位置创建一个小的包围盒来查找相交的SubLevel
        BoundingBox3d queryBox = new BoundingBox3d(
                start.x - 0.5, start.y - 0.5, start.z - 0.5,
                start.x + 0.5, start.y + 0.5, start.z + 0.5
        );
        for (SubLevel subLevel : Sable.HELPER.getAllIntersecting(SableDistUtil.getClientLevel(), queryBox)) {
            if (!subLevel.isRemoved()) {
                Vector3d target = GetLaserToVector3dTool.getDirectionTrans(subLevel, direction, amount);
                return start.translate(target.x, target.y, target.z);
            }
        }
        return start.translate(direction, amount);
    }
}
