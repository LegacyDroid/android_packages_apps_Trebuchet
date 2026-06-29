package com.android.launcher3.fluid;

import android.graphics.Rect;
import android.os.SystemProperties;
import android.view.Choreographer;
import android.view.SurfaceControl;

import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

public class FluidSurfaceMorpher implements Choreographer.FrameCallback {

    public static final String PROP_FLUID_ENABLED = "persist.sys.fluid_animations.enabled";

    private static final float STIFFNESS = 300f;
    private static final float DAMPING_RATIO = 0.86f;

    private final int mTaskId;
    private final SurfaceControl mLeash;
    private final SurfaceControl.Transaction mTransaction;
    private final Runnable mOnComplete;

    private final SpringSolver mCenterXSpring, mCenterYSpring, mWidthSpring, mHeightSpring, mRadiusSpring;

    private final Rect mAppBounds;
    private final Rect mCurrentCrop = new Rect();
    private long mLastFrameTimeNanos;
    private boolean mIsRunning;

    public static boolean isEnabled() {
        return SystemProperties.getBoolean(PROP_FLUID_ENABLED, false);
    }

    public FluidSurfaceMorpher(int taskId, RemoteAnimationTargetCompat target, FluidMotionState priorState,
                               Rect appBounds, Rect targetVisualBounds, float targetRadius, Runnable onComplete) {
        this.mTaskId = taskId;
        this.mLeash = target.leash.getSurfaceControl();
        this.mTransaction = new SurfaceControl.Transaction();
        this.mOnComplete = onComplete;
        this.mAppBounds = new Rect(appBounds);

        float targetCenterX = targetVisualBounds.exactCenterX();
        float targetCenterY = targetVisualBounds.exactCenterY();
        float targetWidth = targetVisualBounds.width();
        float targetHeight = targetVisualBounds.height();

        if (priorState != null) {
            mCenterXSpring = new SpringSolver(priorState.centerX, priorState.vCenterX, STIFFNESS, DAMPING_RATIO);
            mCenterYSpring = new SpringSolver(priorState.centerY, priorState.vCenterY, STIFFNESS, DAMPING_RATIO);
            mWidthSpring = new SpringSolver(priorState.width, priorState.vWidth, STIFFNESS, DAMPING_RATIO);
            mHeightSpring = new SpringSolver(priorState.height, priorState.vHeight, STIFFNESS, DAMPING_RATIO);
            mRadiusSpring = new SpringSolver(priorState.radius, priorState.vRadius, STIFFNESS, DAMPING_RATIO);
        } else {
            mCenterXSpring = new SpringSolver(appBounds.exactCenterX(), 0, STIFFNESS, DAMPING_RATIO);
            mCenterYSpring = new SpringSolver(appBounds.exactCenterY(), 0, STIFFNESS, DAMPING_RATIO);
            mWidthSpring = new SpringSolver(appBounds.width(), 0, STIFFNESS, DAMPING_RATIO);
            mHeightSpring = new SpringSolver(appBounds.height(), 0, STIFFNESS, DAMPING_RATIO);
            mRadiusSpring = new SpringSolver(0, 0, STIFFNESS, DAMPING_RATIO);
        }

        mCenterXSpring.setTarget(targetCenterX);
        mCenterYSpring.setTarget(targetCenterY);
        mWidthSpring.setTarget(targetWidth);
        mHeightSpring.setTarget(targetHeight);
        mRadiusSpring.setTarget(targetRadius);
    }

    public void start() {
        if (!mIsRunning) {
            mIsRunning = true;
            mLastFrameTimeNanos = System.nanoTime();
            Choreographer.getInstance().postFrameCallback(this);
        }
    }

    public FluidMotionState cancelAndExtractState() {
        mIsRunning = false;
        Choreographer.getInstance().removeFrameCallback(this);
        return new FluidMotionState(
                mCenterXSpring.getValue(), mCenterXSpring.getVelocity(),
                mCenterYSpring.getValue(), mCenterYSpring.getVelocity(),
                mWidthSpring.getValue(), mWidthSpring.getVelocity(),
                mHeightSpring.getValue(), mHeightSpring.getVelocity(),
                mRadiusSpring.getValue(), mRadiusSpring.getVelocity()
        );
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!mIsRunning) return;

        float dt = (frameTimeNanos - mLastFrameTimeNanos) / 1_000_000_000f;
        mLastFrameTimeNanos = frameTimeNanos;

        boolean cxSettled = mCenterXSpring.update(dt);
        boolean cySettled = mCenterYSpring.update(dt);
        boolean wSettled = mWidthSpring.update(dt);
        boolean hSettled = mHeightSpring.update(dt);
        boolean rSettled = mRadiusSpring.update(dt);

        applySpatialMorphology();

        if (cxSettled && cySettled && wSettled && hSettled && rSettled) {
            finishMorph();
        } else {
            Choreographer.getInstance().postFrameCallback(this);
        }
    }

    private void applySpatialMorphology() {
        if (mLeash == null || !mLeash.isValid()) {
            finishMorph();
            return;
        }

        try {
            float w = mWidthSpring.getValue();
            float h = mHeightSpring.getValue();
            float cx = mCenterXSpring.getValue();
            float cy = mCenterYSpring.getValue();
            float radius = mRadiusSpring.getValue();

            float appW = mAppBounds.width();

            float scale = w / appW;
            if (scale <= 0.001f) scale = 0.001f;

            float tx = cx - (w / 2f);
            float ty = cy - (h / 2f);

            mCurrentCrop.set(0, 0, (int) appW, (int) (h / scale));

            float scaledRadius = radius / scale;

            mTransaction.setMatrix(mLeash, scale, 0f, 0f, scale)
                    .setPosition(mLeash, tx, ty)
                    .setWindowCrop(mLeash, mCurrentCrop)
                    .setCornerRadius(mLeash, scaledRadius)
                    .setAlpha(mLeash, 1f)
                    .apply();

        } catch (Exception e) {
            finishMorph();
        }
    }

    private void finishMorph() {
        mIsRunning = false;
        FluidAnimationConductor.getInstance().unregisterAnimation(mTaskId);
        if (mOnComplete != null) mOnComplete.run();
    }
}
