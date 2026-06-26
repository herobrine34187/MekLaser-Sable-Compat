package dev.herobrine34187.meklasercompat.mixin;

import dev.ryanhcode.sable.api.particle.ParticleSubLevelKickable;
import mekanism.client.particle.LaserParticle;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

    // Pre-computed from constructor parameters — avoids shadowing
    // private fields from the inheritance chain.
    // / 从构造器参数预计算 — 避免从继承链Shadow私有字段
    // Modified 2026-06-26:23-10
    @Unique
    private float meklas$quadSize;
    @Unique
    private float meklas$halfLength;

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

        // Store endpoints for render-time orientation and frustum-AABB computation
        // / 存储端点供渲染时方向和视锥体AABB计算使用
        this.meklas$localStart = start;
        this.meklas$localEnd = end;

        // Pre-compute halfLength and quadSize from constructor parameters
        // to avoid shadowing private fields from Particle/LaserParticle.
        // / 从构造器参数预计算halfLength和quadSize，避免Shadow继承链中的私有字段
        // Modified 2026-06-26:23-10
        this.meklas$quadSize = energyScale;
        this.meklas$halfLength = (float) (end.distanceTo(start) / 2.0);

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

    // ==================== Oriented AABB for frustum culling ====================
    // / ==================== 视锥体裁剪定向包围盒修复 ====================
    // Modified 2026-06-26:22-26
    //
    // Two problems cause the particle to disappear at certain angles:
    // / 两个问题导致特定角度下粒子消失：
    //
    // (a) updateBoundingBox() uses the cardinal Direction enum to allocate
    //     axis extents, but the mixin replaces the render quaternion with an
    //     arbitrary direction → AABB no longer encloses the rendered quads.
    // / (a) updateBoundingBox()使用Direction枚举分配轴跨度，Mixin将四元数改为
    //     任意方向 → AABB不再包裹实际渲染的四边形。
    //
    // (b) Sable's sable$initialKickOut() runs AFTER our constructor and may
    //     double-transform the already-world-space position, corrupting the
    //     AABB center before ParticleEngine's frustum check calls
    //     getRenderBoundingBox().
    // / (b) Sable的sable$initialKickOut()在构造后运行，可能对世界坐标做二次变换，
    //     在ParticleEngine调用getRenderBoundingBox()做视锥体检查前污染AABB中心。
    //
    // Solution: override getRenderBoundingBox() to compute the AABB directly
    // from the stored endpoints (meklas$localStart / meklas$localEnd), which
    // are immune to both orientation and position corruption.
    // / 方案：覆盖getRenderBoundingBox()，直接从存储端点计算AABB，免疫方向和位置污染。

    /**
     * Half-extent of the AABB on a single world axis.
     * / AABB在单个世界轴上的半跨度。
     *
     * <p><b>Formula / 公式:</b>
     * {@code quadSize · √(1 − beamComp²) + halfLength · |beamComp|}
     * </p>
     *
     * <p>The first term is the maximum projection of the two perpendicular
     * quads (width ±quadSize) onto the axis within the plane orthogonal to
     * the beam. The second term is the beam-length projection.
     * / 第一项是两个垂直四边形宽度在垂直于光束平面的轴投影最大值；
     * 第二项是光束长度的投影。</p>
     */
    @Unique
    private double meklas$halfExtent(double beamComp, double qs, double hl) {
        double perpProjection = qs * Math.sqrt(Math.max(0.0, 1.0 - beamComp * beamComp));
        double beamProjection = hl * Math.abs(beamComp);
        return Math.max(perpProjection + beamProjection, qs);
    }

    /**
     * Build a correct AABB from the stored endpoints.
     * / 从存储端点构建正确的AABB。
     *
     * <p>Independent of {@code this.x/y/z} — uses only the constructor-stored
     * {@code meklas$localStart/End}, {@code meklas$quadSize}, and
     * {@code meklas$halfLength}.
     * / 不依赖this.x/y/z——仅使用构造时存储的meklas$localStart/End、
     * meklas$quadSize和meklas$halfLength。</p>
     */
    @Unique
    private AABB meklas$computeAABB() {
        Vec3 beamDir = this.meklas$localEnd.subtract(this.meklas$localStart);
        double len = beamDir.length();
        if (len < 1e-6) {
            // Degenerate — fall back to a small box around the midpoint
            // / 退化情况 — 退回中点周围的极小包围盒
            Vec3 mid = this.meklas$localEnd.add(this.meklas$localStart).scale(0.5);
            float qs = this.meklas$quadSize;
            return new AABB(mid.x - qs, mid.y - qs, mid.z - qs,
                           mid.x + qs, mid.y + qs, mid.z + qs);
        }
        double invLen = 1.0 / len;
        double bx = beamDir.x * invLen;
        double by = beamDir.y * invLen;
        double bz = beamDir.z * invLen;

        double qs = this.meklas$quadSize;
        double hl = this.meklas$halfLength;
        double dx = meklas$halfExtent(bx, qs, hl);
        double dy = meklas$halfExtent(by, qs, hl);
        double dz = meklas$halfExtent(bz, qs, hl);

        Vec3 center = this.meklas$localEnd.add(this.meklas$localStart).scale(0.5);
        return new AABB(
                center.x - dx, center.y - dy, center.z - dz,
                center.x + dx, center.y + dy, center.z + dz
        );
    }

    /**
     * Override {@code getRenderBoundingBox(float)} to return an AABB
     * computed directly from the stored endpoints.
     * / 覆盖getRenderBoundingBox(float)，返回从存储端点直接计算的AABB。
     *
     * <p>This is the single point where {@code ParticleEngine} queries the
     * bounding box for frustum culling. By bypassing
     * {@code this.x/y/z} and the vanilla {@code updateBoundingBox()},
     * both the orientation mismatch and the position-corruption problems
     * are solved with one injection.
     * / 这是ParticleEngine查询包围盒做视锥体裁剪的唯一入口。绕过this.x/y/z和
     * 原版updateBoundingBox()，一次注入同时解决方向不匹配和位置污染两个问题。</p>
     */
    @Inject(method = "getRenderBoundingBox", at = @At("HEAD"), cancellable = true)
    private void meklas$fixRenderBoundingBox(float partialTicks, CallbackInfoReturnable<AABB> cir) {
        if (this.meklas$localStart == null || this.meklas$localEnd == null) {
            return; // not yet initialized, fall through to original / 尚未初始化
        }
        cir.setReturnValue(meklas$computeAABB());
    }
}
