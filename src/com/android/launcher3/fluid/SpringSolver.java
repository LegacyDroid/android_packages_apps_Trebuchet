package com.android.launcher3.fluid;

public class SpringSolver {
    private float mValue;
    private float mVelocity;
    private float mTarget;
    private float mStiffness;
    private float mDamping;

    public SpringSolver(float initialValue, float initialVelocity, float stiffness, float dampingRatio) {
        this.mValue = initialValue;
        this.mVelocity = initialVelocity;
        this.mStiffness = stiffness;
        this.mDamping = dampingRatio * 2f * (float) Math.sqrt(stiffness);
    }

    public void setTarget(float target) { this.mTarget = target; }
    public void setVelocity(float velocity) { this.mVelocity = velocity; }

    public float getValue() { return mValue; }
    public float getVelocity() { return mVelocity; }

    public boolean update(float deltaTime) {
        float displacement = mValue - mTarget;
        float springForce = -mStiffness * displacement;
        float dampingForce = -mDamping * mVelocity;
        float acceleration = springForce + dampingForce;
        mVelocity += acceleration * deltaTime;
        mValue += mVelocity * deltaTime;

        boolean isAtRest = Math.abs(mVelocity) < 0.5f && Math.abs(displacement) < 0.5f;
        if (isAtRest) {
            mValue = mTarget;
            mVelocity = 0f;
        }
        return isAtRest;
    }
}
