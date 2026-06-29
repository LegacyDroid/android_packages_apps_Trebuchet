package com.android.launcher3.fluid;

public class SpringSolver {
    private float mValue;
    private float mVelocity;
    private float mTarget;

    private final float mNaturalFreq;
    private final float mDampingRatio;

    public SpringSolver(float initialValue, float initialVelocity, float stiffness, float dampingRatio) {
        this.mValue = initialValue;
        this.mVelocity = initialVelocity;
        this.mTarget = initialValue;

        this.mNaturalFreq = (float) Math.sqrt(stiffness);
        this.mDampingRatio = Math.min(Math.max(dampingRatio, 0.1f), 0.999f);
    }

    public void setTarget(float target) { this.mTarget = target; }
    public void setVelocity(float velocity) { this.mVelocity = velocity; }

    public float getValue() { return mValue; }
    public float getVelocity() { return mVelocity; }

    public boolean update(float deltaTime) {
        float dt = Math.min(deltaTime, 0.064f);

        float displacement = mValue - mTarget;

        float omega_d = mNaturalFreq * (float) Math.sqrt(1 - mDampingRatio * mDampingRatio);
        float expTerm = (float) Math.exp(-mDampingRatio * mNaturalFreq * dt);
        float cosTerm = (float) Math.cos(omega_d * dt);
        float sinTerm = (float) Math.sin(omega_d * dt);

        float c1 = displacement;
        float c2 = (mVelocity + mDampingRatio * mNaturalFreq * displacement) / omega_d;

        mValue = mTarget + expTerm * (c1 * cosTerm + c2 * sinTerm);
        mVelocity = -mDampingRatio * mNaturalFreq * expTerm * (c1 * cosTerm + c2 * sinTerm)
                  + expTerm * omega_d * (-c1 * sinTerm + c2 * cosTerm);

        boolean isAtRest = Math.abs(mVelocity) < 0.5f && Math.abs(mValue - mTarget) < 0.5f;
        if (isAtRest) {
            mValue = mTarget;
            mVelocity = 0f;
        }
        return isAtRest;
    }
}
