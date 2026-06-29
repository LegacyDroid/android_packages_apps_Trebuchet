package com.android.launcher3.fluid;

public class FluidMotionState {
    public final float x, vx;
    public final float y, vy;
    public final float scale, vscale;
    public final float cropBottom, vcrop;
    public final float radius, vradius;

    public FluidMotionState(float x, float vx, float y, float vy,
                            float scale, float vscale,
                            float cropBottom, float vcrop,
                            float radius, float vradius) {
        this.x = x; this.vx = vx;
        this.y = y; this.vy = vy;
        this.scale = scale; this.vscale = vscale;
        this.cropBottom = cropBottom; this.vcrop = vcrop;
        this.radius = radius; this.vradius = vradius;
    }
}
