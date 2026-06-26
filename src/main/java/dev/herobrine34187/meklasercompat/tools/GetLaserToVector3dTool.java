package dev.herobrine34187.meklasercompat.tools;

import dev.ryanhcode.sable.sublevel.SubLevel;
import mekanism.common.config.MekanismConfig;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;

public class GetLaserToVector3dTool {

    // 最大射程
    public static final double MAX_DETECT_RANGE = MekanismConfig.general.laserRange.get();

    public static Vector3d getDirectionTrans(SubLevel subLevel, Direction direction) {
        return getVector3d(subLevel, direction, MAX_DETECT_RANGE);
    }

    public static Vector3d getDirectionTrans(SubLevel subLevel, Direction direction, double product) {
        return getVector3d(subLevel, direction, product);
    }

    @NotNull
    private static Vector3d getVector3d(SubLevel subLevel, Direction direction, double product) {
        Vector3d vector3dTo = switch (direction) {
            case UP -> new Vector3d(0, 1, 0);
            case DOWN -> new Vector3d(0, -1, 0);
            case EAST -> new Vector3d(1, 0, 0);
            case WEST -> new Vector3d(-1, 0, 0);
            case SOUTH -> new Vector3d(0, 0, 1);
            case NORTH -> new Vector3d(0, 0, -1);
        };
        vector3dTo.mul(product);
        if (subLevel != null && !subLevel.isRemoved()) {
            subLevel.logicalPose().transformNormal(vector3dTo);
        }
        return vector3dTo;
    }
}
