package dev.herobrine34187.meklasercompat.mixin;

import dev.herobrine34187.meklasercompat.tools.GetLaserToVector3dTool;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.common.lib.math.Pos3D;
import mekanism.common.tile.laser.TileEntityBasicLaser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * 修复服务端异常
 */
@Mixin(TileEntityBasicLaser.class)
public abstract class TileEntityBasicLaserMixin {

    @Shadow
    protected abstract float getEnergyScale(long energy);

    // 所在的物理结构
    @Unique
    private SubLevel meklas$phys;
    // 激光束影响半径
    @Unique
    private float meklas$Radius;
    // 本体
    @Unique
    private TileEntityBasicLaser meklas$laser;
    @Unique
    private Pos3D meklas$fixedFrom;
    @Unique
    private Pos3D meklas$fixedTo;

    /**
     * 初始化
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void meklas$init(Holder<Block> blockProvider, BlockPos pos, BlockState state, CallbackInfo ci) {
        this.meklas$laser = (TileEntityBasicLaser) (Object) this;
        this.meklas$Radius = this.getEnergyScale(this.meklas$laser.getEnergyContainer().extract(Long.MAX_VALUE, Action.SIMULATE, AutomationType.INTERNAL));
    }

    /**
     * Refresh fields that cannot be initialized in the constructor.
     * / 一部分无法直接初始化的量，在onUpdateServer()被调用时触发初始化或刷新
     * <p>
     * Modified 2026-06-26:17-00 — removed event posting (SubLevel is now looked up per-particle on client)
     * / 修改于 2026-06-26:17-00 — 移除事件发布（客户端改为逐粒子直接查找SubLevel）
     */
    @Inject(method = "onUpdateServer", at = @At("HEAD"))
    private void meklas$update(CallbackInfoReturnable<Boolean> cir) {
        this.meklas$phys = Sable.HELPER.getContaining(this.meklas$laser);
        this.meklas$Radius = this.getEnergyScale(this.meklas$laser.getEnergyContainer().extract(Long.MAX_VALUE, Action.SIMULATE, AutomationType.INTERNAL));
    }

    /**
     * <p>修复过大杀伤范围，不可删除！</p>
     * <p>原因：如果对getLaserBox注入，返回值终究为一个AABB，在杀伤实体时仍然会从该AABB构成的大范围内进行选择</p>
     * <p>这里相当于对实体列表进行了过滤。</p>
     */
    @Redirect(
            method = "onUpdateServer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"
            )
    )
    private List<Entity> meklas$reOnUpdateServer(Level level, Class<Entity> clazz, AABB aabb) {
        // 非物理结构激光器处理
        if (this.meklas$phys == null || this.meklas$phys.isRemoved()) {
            return level.getEntitiesOfClass(clazz, aabb);
        }

        Vec3 start = this.meklas$fixedFrom;
        Vec3 end = this.meklas$fixedTo;
        AABB safeAABB = new AABB(start, end);
        // 收集候选实体
        List<Entity> candidates = level.getEntities(
                (Entity) null, safeAABB,
                entity -> entity instanceof LivingEntity
        );
        double closestDist = Double.MAX_VALUE;

        List<Entity> closest = new ArrayList<>();

        // 处理
        for (Entity entity : candidates) {
            // 实体 AABB 与射线求交（ProjectileUtilMixin 已处理跨 SubLevel 变换）
            AABB entityBox = entity.getBoundingBox().inflate(this.meklas$Radius); // 宽容一点
            Vec3 clip = entityBox.clip(start, end).orElse(null);
            if (clip == null) continue;

            double dist = start.distanceToSqr(clip);
            if (dist < closestDist) {
                closestDist = dist;
                closest.add(entity);
            }
        }
        return closest;
    }

    /**
     * from初始值修复（返回修复）
     */
    @ModifyVariable(
            method = "onUpdateServer",
            at = @At(value = "STORE", ordinal = 0),
            name = "from"
    )
    private Pos3D meklas$fixFrom(Pos3D from) {
        if (this.meklas$phys != null && !this.meklas$phys.isRemoved()) {
            Vec3 v3 = this.meklas$phys.logicalPose().transformPosition(from);
            from = new Pos3D(v3);
            this.meklas$fixedFrom = from;
        }
        return from;
    }

    /**
     * 修复to值初始化（涉及到方向，不可删除）
     */
    @Redirect(
            method = "onUpdateServer",
            at = @At(
                    value = "INVOKE",
                    target = "Lmekanism/common/lib/math/Pos3D;translate(Lnet/minecraft/core/Direction;D)Lmekanism/common/lib/math/Pos3D;",
                    ordinal = 1
            )
    )
    private Pos3D meklas$fixTo(Pos3D from, Direction direction, double amount) {
        if (this.meklas$phys != null && !this.meklas$phys.isRemoved()) {
            Vector3d target = GetLaserToVector3dTool.getDirectionTrans(this.meklas$phys, direction);
            Pos3D result = from.translate(target.x, target.y, target.z);
            this.meklas$fixedTo = result;
            return result;
        }
        return from.translate(direction, amount);
    }

    /**
     * 修复to值的重新赋值（涉及到方向，不可删除）
     */
    @Redirect(
            method = "onUpdateServer",
            at = @At(
                    value = "INVOKE",
                    target = "Lmekanism/common/lib/math/Pos3D;adjustPosition(Lnet/minecraft/core/Direction;Lnet/minecraft/world/entity/Entity;)Lmekanism/common/lib/math/Pos3D;"
            )
    )
    private Pos3D meklas$fixResetTo(Pos3D from, Direction direction, Entity entity) {
        if (this.meklas$phys != null && !this.meklas$phys.isRemoved()) {
            Pos3D result = new Pos3D(entity.getX(), entity.getY(), entity.getZ());
            this.meklas$fixedTo = result;
            return result;
        }
        return from.adjustPosition(direction, entity);
    }

    @ModifyVariable(method = "onUpdateServer", at = @At(value = "STORE", ordinal = 1), name = "to")
    private Pos3D meklas$fixLaserHitPhys(Pos3D to) {
        SubLevel targetLevel = Sable.HELPER.getContaining(this.meklas$phys.getLevel(), to);
        if (targetLevel != null && !targetLevel.isRemoved()) {
            Pos3D result = new Pos3D(targetLevel.logicalPose().transformPosition(to));
            this.meklas$fixedTo = result;
            return result;
        }
        return to;
    }
}