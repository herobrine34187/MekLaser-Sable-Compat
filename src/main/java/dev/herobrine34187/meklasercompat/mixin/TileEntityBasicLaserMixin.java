package dev.herobrine34187.meklasercompat.mixin;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.common.config.MekanismConfig;
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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;


@Mixin(TileEntityBasicLaser.class)
public abstract class TileEntityBasicLaserMixin {
    @Shadow
    protected abstract AABB getLaserBox(Direction direction, Vec3 from, Vec3 to, float energyScale);

    @Shadow
    protected abstract float getEnergyScale(long energy);

    @Unique
    private BlockPos meklas$laserPos;
    // 最大射程
    @Unique
    private final double MAX_DETECT_RANGE = MekanismConfig.general.laserRange.get();

    @Unique
    private TileEntityBasicLaser meklas$laser;
    @Unique
    private float meklas$RADIUS;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void meklas$init(Holder<Block> blockProvider, BlockPos pos, BlockState state, CallbackInfo ci) {
        meklas$laserPos = pos;
        meklas$laser = (TileEntityBasicLaser) (Object) this;
        meklas$RADIUS = this.getEnergyScale(meklas$laser.getEnergyContainer().extract(Long.MAX_VALUE, Action.SIMULATE, AutomationType.INTERNAL));
    }

    @Redirect(
            method = "onUpdateServer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"
            )
    )
    private List<Entity> meklas$reOnUpdateServer(Level level, Class<Entity> clazz, AABB aabb) {
        SubLevel phys = Sable.HELPER.getContaining(level, meklas$laserPos);
        // 偏转角
        Direction direction = meklas$laser.facingSupplier.get();
        // 非物理结构激光器处理
        if (phys == null || phys.isRemoved()) {
            Pos3D from = Pos3D.create(meklas$laser).centre().translate(direction, 0.501);
            Pos3D to = from.translate(direction, (double) MekanismConfig.general.laserRange.get() - 0.002);
            return level.getEntitiesOfClass(clazz, this.getLaserBox(direction, from, to, (float) MAX_DETECT_RANGE));
        }

        // 确定激光杀伤方向与范围
        Vector3d vector3dTo = switch (direction) {
            case UP -> new Vector3d(0, 1, 0);
            case DOWN -> new Vector3d(0, -1, 0);
            case EAST -> new Vector3d(1, 0, 0);
            case WEST -> new Vector3d(-1, 0, 0);
            case SOUTH -> new Vector3d(0, 0, 1);
            case NORTH -> new Vector3d(0, 0, -1);
        };
        phys.logicalPose().transformNormal(vector3dTo);
        vector3dTo.mul(MAX_DETECT_RANGE);
        Vec3 start = phys.logicalPose().transformPosition(meklas$laserPos.getCenter());
        Vec3 end = new Vec3(start.x + vector3dTo.x, start.y + vector3dTo.y, start.z + vector3dTo.z);

        // 收集候选实体
        List<Entity> candidates = level.getEntities(
                (Entity) null, aabb,
                entity -> entity instanceof LivingEntity
        );
        double closestDist = Double.MAX_VALUE;

        List<Entity> closest = new ArrayList<>();

        // 处理
        for (Entity entity : candidates) {
            // 实体 AABB 与射线求交（ProjectileUtilMixin 已处理跨 SubLevel 变换）
            AABB entityBox = entity.getBoundingBox().inflate(meklas$RADIUS); // 宽容一点
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
}
