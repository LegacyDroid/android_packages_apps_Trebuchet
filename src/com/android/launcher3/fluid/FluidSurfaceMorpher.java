package com.android.launcher3.fluid;

import android.graphics.Rect;
import android.os.SystemProperties;
import android.view.Choreographer;
import android.view.SurfaceControl;

import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.SurfaceControlCompat;

import java.io.PrintWriter;
import java.io.StringWriter;

public class FluidSurfaceMorpher implements Choreographer.FrameCallback {

    public static final String PROP_FLUID_ENABLED = "persist.sys.fluid_animations.enabled";

    private static final float STIFFNESS = 300f;
    private static final float DAMPING_RATIO = 0.85f;

    private final int mTaskId;
    private final SurfaceControl mLeash;
    private final SurfaceControl.Transaction mTransaction;
    private final Runnable mOnComplete;

    private final SpringSolver mXSpring, mYSpring, mScaleSpring, mCropBottomSpring, mRadiusSpring;
    private final Rect mCurrentCrop = new Rect();
    private final Rect mStartBounds;
    private final Rect mTargetBounds;

    private long mLastFrameTimeNanos;
    private boolean mIsRunning;
    private boolean mIsOpening;

    public static boolean isEnabled() {
        return SystemProperties.getBoolean(PROP_FLUID_ENABLED, false);
    }

    public FluidSurfaceMorpher(int taskId, RemoteAnimationTargetCompat target,
                               Rect startBounds, Rect targetBounds, Runnable onComplete) {
        this.mTaskId = taskId;
        this.mLeash = target.leash.getSurfaceControl();
        this.mTransaction = new SurfaceControl.Transaction();
        this.mOnComplete = onComplete;
        this.mStartBounds = new Rect(startBounds);
        this.mTargetBounds = new Rect(targetBounds);
        this.mIsOpening = target.mode == RemoteAnimationTargetCompat.MODE_OPENING;

        float targetScale = (float) targetBounds.width() / startBounds.width();
        float targetCropBottom = targetBounds.height() / targetScale;

        mXSpring = new SpringSolver(startBounds.left, 0, STIFFNESS, DAMPING_RATIO);
        mYSpring = new SpringSolver(startBounds.top, 0, STIFFNESS, DAMPING_RATIO);
        mScaleSpring = new SpringSolver(1.0f, 0, STIFFNESS, DAMPING_RATIO);
        mCropBottomSpring = new SpringSolver(startBounds.bottom, 0, STIFFNESS, DAMPING_RATIO);
        mRadiusSpring = new SpringSolver(0f, 0, STIFFNESS, DAMPING_RATIO);

        mXSpring.setTarget(targetBounds.left);
        mYSpring.setTarget(targetBounds.top);
        mScaleSpring.setTarget(targetScale);
        mCropBottomSpring.setTarget(targetCropBottom);
        mRadiusSpring.setTarget(60f);

        mCurrentCrop.set(0, 0, startBounds.right, startBounds.bottom);
    }

    public FluidSurfaceMorpher(int taskId, RemoteAnimationTargetCompat target,
                               FluidMotionState state,
                               Rect startBounds, Rect targetBounds,
                               Runnable onComplete) {
        this.mTaskId = taskId;
        this.mLeash = target.leash.getSurfaceControl();
        this.mTransaction = new SurfaceControl.Transaction();
        this.mOnComplete = onComplete;
        this.mStartBounds = new Rect(startBounds);
        this.mTargetBounds = new Rect(targetBounds);
        this.mIsOpening = target.mode == RemoteAnimationTargetCompat.MODE_OPENING;

        float targetScale = (float) targetBounds.width() / startBounds.width();
        float targetCropBottom = targetBounds.height() / targetScale;

        mXSpring = new SpringSolver(state.x, state.vx, STIFFNESS, DAMPING_RATIO);
        mYSpring = new SpringSolver(state.y, state.vy, STIFFNESS, DAMPING_RATIO);
        mScaleSpring = new SpringSolver(state.scale, state.vscale, STIFFNESS, DAMPING_RATIO);
        mCropBottomSpring = new SpringSolver(state.cropBottom, state.vcrop, STIFFNESS, DAMPING_RATIO);
        mRadiusSpring = new SpringSolver(state.radius, state.vradius, STIFFNESS, DAMPING_RATIO);

        mXSpring.setTarget(targetBounds.left);
        mYSpring.setTarget(targetBounds.top);
        mScaleSpring.setTarget(targetScale);
        mCropBottomSpring.setTarget(targetCropBottom);
        mRadiusSpring.setTarget(60f);

        mCurrentCrop.set(0, 0, startBounds.right, (int) state.cropBottom);
        applyToSurface();
    }

    public void addGestureVelocityY(float velocityY) {
        mYSpring.setVelocity(velocityY);
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
                mXSpring.getValue(), mXSpring.getVelocity(),
                mYSpring.getValue(), mYSpring.getVelocity(),
                mScaleSpring.getValue(), mScaleSpring.getVelocity(),
                mCropBottomSpring.getValue(), mCropBottomSpring.getVelocity(),
                mRadiusSpring.getValue(), mRadiusSpring.getVelocity()
        );
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!mIsRunning) return;

        float dt = (frameTimeNanos - mLastFrameTimeNanos) / 1_000_000_000f;
        mLastFrameTimeNanos = frameTimeNanos;
        if (dt > 0.032f) dt = 0.032f;

        boolean xSettled = mXSpring.update(dt);
        boolean ySettled = mYSpring.update(dt);
        boolean scaleSettled = mScaleSpring.update(dt);
        boolean cropSettled = mCropBottomSpring.update(dt);
        mRadiusSpring.update(dt);

        applyToSurface();

        if (xSettled && ySettled && scaleSettled && cropSettled) {
            mIsRunning = false;
            FluidAnimationConductor.getInstance().unregisterAnimation(mTaskId);
            if (mOnComplete != null) mOnComplete.run();
        } else {
            Choreographer.getInstance().postFrameCallback(this);
        }
    }

    private void applyToSurface() {
        try {
            float scale = mScaleSpring.getValue();
            float x = mXSpring.getValue();
            float y = mYSpring.getValue();

            mCurrentCrop.bottom = (int) mCropBottomSpring.getValue();

            mTransaction.setMatrix(mLeash,
                            scale, 0f,
                            0f, scale)
                    .setPosition(mLeash, x, y)
                    .setWindowCrop(mLeash, mCurrentCrop)
                    .setCornerRadius(mLeash, mRadiusSpring.getValue())
                    .apply();
        } catch (Exception e) {
            mIsRunning = false;
            FluidAnimationConductor.getInstance().unregisterAnimation(mTaskId);
            if (mOnComplete != null) mOnComplete.run();
        }
    }
}
