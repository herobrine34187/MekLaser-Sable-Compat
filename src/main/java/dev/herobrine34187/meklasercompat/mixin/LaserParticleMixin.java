package dev.herobrine34187.meklasercompat.mixin;

import dev.ryanhcode.sable.api.particle.ParticleSubLevelKickable;
import mekanism.client.particle.LaserParticle;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复激光束粒子效果表现异常，会牺牲一部分性能
 */
@Mixin(LaserParticle.class)
public abstract class LaserParticleMixin implements ParticleSubLevelKickable {

    @Shadow
    public abstract void setPos(double x, double y, double z);

    @Unique
    private Vec3 meklas$localStart;
    @Unique
    private Vec3 meklas$localEnd;
    @Unique
    private Vec3 meklas$worldPos;

    /**
     * Finds the Sable sub-level containing the laser and stores the
     * LOCAL-space midpoint for later world-position computation.
     * <p>查找包含激光器的Sable子级，存储局部空间中点供后续计算。</p>
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void meklas$resolveSubLevelAtConstruction(
            ClientLevel level, Vec3 start, Vec3 end,
            Direction direction, float energyScale,
            CallbackInfo ci) {

        // Find the sub-level containing the laser origin

        this.meklas$localStart = start;
        this.meklas$localEnd = end;

        // Compute current interpolated world position from the sub-level's pose
        this.meklas$worldPos = this.meklas$localEnd.add(this.meklas$localStart).scale(0.5);
        // Directly set Particle's x/y/z (shadowed from protected fields)
        this.setPos(this.meklas$worldPos.x, this.meklas$worldPos.y, this.meklas$worldPos.z);
    }

    /**
     * 随游戏刻更新而更新位置
     * */
    @Inject(method = "render", at = @At("HEAD"))
    private void meklas$fixPositionBeforeRender(CallbackInfo ci) {
        // Compute current interpolated world position from the sub-level's pose
        meklas$worldPos = meklas$localEnd.add(meklas$localStart).scale(0.5);
        // Directly set Particle's x/y/z (shadowed from protected fields)
        this.setPos(meklas$worldPos.x, meklas$worldPos.y, meklas$worldPos.z);
    }

    /**
     * <p>修复激光束的指向问题。</p>
     * <p>粒子效果的旋转原本为originalRotation参数应用后的旋转，但是该参数只有六种取值。此方法会将该参数修改为对应的连续取值。</p>
     * <p>注意，为了保证可靠，此方法会牺牲掉部分性能与光束粒子的长轴自旋。</p>
     * <p>由于未知原因，有时通过事件总线传递参数会传递出空参数，故弃用事件方式。</p>
     * <p>部分算法由DeepSeek提供。</p>
     * */
    @ModifyVariable(method = "render", at = @At("STORE"), ordinal = 0)
    private Quaternionf meklas$applySubLevelRotationToLaserDirection(Quaternionf originalRotation) {
        Vec3 A = this.meklas$localStart;
        Vec3 B = this.meklas$localEnd;

        // 计算目标方向向量：to = B - A （注意不是 A - B）
        Vec3 targetDir = B.subtract(A);
        targetDir.normalize();

        // 原方向向量
        Quaternionf result = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), targetDir.toVector3f());
        return result.normalize();
    }

    /*
    * 以下均为粒子运动过程的视觉补偿
    * */

    @Redirect(method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Mth;lerp(DDD)D",
                    ordinal = 0
            )
    )
    private double meklas$fixNewX(double ticks, double xo, double x) {
        return Mth.lerp(ticks, this.meklas$worldPos.x, x);
    }

    @Redirect(method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Mth;lerp(DDD)D",
                    ordinal = 1
            )
    )
    private double meklas$fixNewY(double ticks, double yo, double y) {
        return Mth.lerp(ticks, this.meklas$worldPos.y, y);
    }

    @Redirect(method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Mth;lerp(DDD)D",
                    ordinal = 2
            )
    )
    private double meklas$fixNewZ(double ticks, double zo, double z) {
        return Mth.lerp(ticks, this.meklas$worldPos.z, z);
    }

    /* 以下为性能修复 */

    @Override
    public boolean sable$shouldCollideWithTrackingSubLevel() {
        return false;  // 激光是纯视觉粒子，不需要碰撞
    }

    @Override
    public boolean sable$shouldCareAboutIntersectingSubLevels() {
        return false;  // 也不需要关注与其他SubLevel的交互
    }
}
