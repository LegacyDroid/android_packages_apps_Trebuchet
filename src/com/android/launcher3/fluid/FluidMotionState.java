package com.android.launcher3.fluid;

public class FluidMotionState {
    public final float centerX, vCenterX;
    public final float centerY, vCenterY;
    public final float width, vWidth;
    public final float height, vHeight;
    public final float radius, vRadius;

    public FluidMotionState(float centerX, float vCenterX, float centerY, float vCenterY,
                            float width, float vWidth, float height, float vHeight,
                            float radius, float vRadius) {
        this.centerX = centerX;     this.vCenterX = vCenterX;
        this.centerY = centerY;     this.vCenterY = vCenterY;
        this.width = width;         this.vWidth = vWidth;
        this.height = height;       this.vHeight = vHeight;
        this.radius = radius;       this.vRadius = vRadius;
    }
}
