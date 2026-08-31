package com.mdvcraft.mdvcrates.util;

import org.bukkit.util.Vector;

public final class VecMath {
    private VecMath() {}

    public static Vector rotateXYZ(Vector v, double xRad, double yRad, double zRad) {
        double x = v.getX(), y = v.getY(), z = v.getZ();

        double cx = Math.cos(xRad), sx = Math.sin(xRad);
        double y1 = y * cx - z * sx;
        double z1 = y * sx + z * cx;
        y = y1; z = z1;

        double cy = Math.cos(yRad), sy = Math.sin(yRad);
        double x1 = x * cy + z * sy;
        z1 = -x * sy + z * cy;
        x = x1; z = z1;

        double cz = Math.cos(zRad), sz = Math.sin(zRad);
        x1 = x * cz - y * sz;
        y1 = x * sz + y * cz;
        return new Vector(x1, y1, z);
    }
}
