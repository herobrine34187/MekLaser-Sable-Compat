package dev.herobrine34187.meklasercompat.mixin;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import mekanism.client.particle.LaserParticle;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Quaterniondc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复激光束粒子效果表现异常
 */
@Mixin(LaserParticle.class)
public abstract class LaserParticleMixin {

    @Shadow
    public abstract void setPos(double x, double y, double z);

    /**
     * Sub-level the laser resides on. Resolved at construction via laser origin.
     */
    @Unique
    private ClientSubLevel meklas$subLevel;

    @Unique
    private Vec3 meklas$localStart;
    @Unique
    private Vec3 meklas$localEnd;
    @Unique
    private Vec3 meklas$worldPos;

    // ============================================================
    // Injection 1: Resolve sub-level at construction time
    // ============================================================

    /**
     * Finds the Sable sub-level containing the laser and stores the
     * LOCAL-space midpoint for later world-position computation.
     * <p>
     * 查找包含激光器的Sable子级，存储局部空间中点供后续计算。
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void meklas$resolveSubLevelAtConstruction(
            ClientLevel level, Vec3 start, Vec3 end,
            Direction direction, float energyScale,
            CallbackInfo ci) {

        this.meklas$localStart = start;
        this.meklas$localEnd = end;

        // Find the sub-level containing the laser origin
        ClientSubLevel found = Sable.HELPER.getContainingClient(start.x, start.z);
        if (found != null && !found.isRemoved()) {
            this.meklas$subLevel = found;
        } else {
            this.meklas$subLevel = null;
        }
    }

    // ============================================================
    // Injection 2: Replace stale position with current world position
    // ============================================================

    @Inject(method = "render", at = @At("HEAD"))
    private void meklas$fixPositionBeforeRender(CallbackInfo ci) {
        if (this.meklas$subLevel != null && !this.meklas$subLevel.isRemoved()) {
            Vec3 localPos = meklas$localEnd.add(meklas$localStart).scale(0.5);
            // Compute current interpolated world position from the sub-level's pose
            meklas$worldPos = this.meklas$subLevel.renderPose().transformPosition(localPos);
            // Directly set Particle's x/y/z (shadowed from protected fields)
            this.setPos(meklas$worldPos.x, meklas$worldPos.y, meklas$worldPos.z);
        }
    }

    @Redirect(method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Mth;lerp(DDD)D",
                    ordinal = 0
            )
    )
    private double meklas$fixNewX(double ticks, double xo, double x) {
        return Mth.lerp(ticks, meklas$worldPos.x, x);
    }

    @Redirect(method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Mth;lerp(DDD)D",
                    ordinal = 1
            )
    )
    private double meklas$fixNewY(double ticks, double yo, double y) {
        return Mth.lerp(ticks, meklas$worldPos.y, y);
    }

    @Redirect(method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Mth;lerp(DDD)D",
                    ordinal = 2
            )
    )
    private double meklas$fixNewZ(double ticks, double xo, double z) {
        return Mth.lerp(ticks, meklas$worldPos.z, z);
    }

    // ============================================================
    // Injection 3: Apply sub-level rotation to laser direction
    // ============================================================

    /**
     * Multiplies the laser direction quaternion by the sub-level's orientation.
     * <p>
     * The laser block has a facing DIRECTION in local space. The sub-level has
     * an ORIENTATION (quaternion). The world-space laser direction is:
     * {@code worldDirection = subLevelOrientation * localDirection}
     * <p>
     * 将激光方向四元数乘以子级朝向。
     * 激光方块有局部空间朝向(Direction)，子级有朝向(四元数)。
     * 世界空间激光方向 = 子级朝向 * 局部方向。
     */
    @ModifyVariable(method = "render", at = @At("STORE"), ordinal = 0)
    private Quaternionf meklas$applySubLevelRotationToLaserDirection(Quaternionf quaternion) {
        if (this.meklas$subLevel != null && !this.meklas$subLevel.isRemoved()) {
            Quaterniondc subLevelOrientation = this.meklas$subLevel.renderPose().orientation();
            // worldDirection = subLevelOrientation * localDirection
            Quaternionf corrected = new Quaternionf(subLevelOrientation);
            corrected.mul(quaternion);
            return corrected;
        }
        return quaternion;
    }
}
