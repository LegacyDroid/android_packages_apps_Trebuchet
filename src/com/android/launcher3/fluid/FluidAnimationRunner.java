package com.android.launcher3.fluid;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.graphics.Rect;
import android.os.Handler;
import android.view.View;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAnimationRunner;
import com.android.launcher3.WrappedAnimationRunnerImpl;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;


public class FluidAnimationRunner implements WrappedAnimationRunnerImpl {

    private static final long SPRING_TIMEOUT_MS = 5000;

    private final Handler mHandler;
    private final Launcher mLauncher;
    private final View mIconView;
    private final boolean mIsOpening;

    public FluidAnimationRunner(Handler handler, Launcher launcher, View iconView, boolean isOpening) {
        this.mHandler = handler;
        this.mLauncher = launcher;
        this.mIconView = iconView;
        this.mIsOpening = isOpening;
    }

    @Override
    public Handler getHandler() {
        return mHandler;
    }

    @Override
    public void onCreateAnimation(RemoteAnimationTargetCompat[] appTargets,
                                  RemoteAnimationTargetCompat[] wallpaperTargets,
                                  LauncherAnimationRunner.AnimationResult result) {
        if (appTargets.length == 0) {
            result.setAnimation(new AnimatorSet(), mLauncher);
            return;
        }

        RemoteAnimationTargetCompat target = appTargets[0];
        int taskId = target.taskId;

        Rect screenBounds = new Rect(0, 0,
                mLauncher.getDeviceProfile().widthPx,
                mLauncher.getDeviceProfile().heightPx);

        Rect iconBounds = getIconBoundsOnScreen();

        Rect startBounds = mIsOpening ? iconBounds : screenBounds;
        Rect endBounds = mIsOpening ? screenBounds : iconBounds;

        // Watchdog animator: keeps leash alive until physics settle
        final ValueAnimator watchdog = ValueAnimator.ofFloat(0, 1);
        watchdog.setDuration(SPRING_TIMEOUT_MS);

        FluidMotionState stolenState = FluidAnimationConductor.getInstance().stealStateAndCancel(taskId);
        FluidSurfaceMorpher morpher;

        Runnable onComplete = () -> {
            // Springs settled — end the watchdog to trigger system finish callback
            if (watchdog.isStarted()) {
                watchdog.end();
            }
        };

        if (stolenState != null) {
            morpher = new FluidSurfaceMorpher(taskId, target, stolenState,
                    startBounds, endBounds, onComplete);
        } else {
            morpher = new FluidSurfaceMorpher(taskId, target,
                    startBounds, endBounds, onComplete);
        }

        // Timeout: if springs never settle, force-end everything
        watchdog.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                FluidMotionState remaining = FluidAnimationConductor.getInstance()
                        .stealStateAndCancel(taskId);
                if (remaining != null) {
                    forceFinalState(target, endBounds);
                }
            }
        });

        FluidAnimationConductor.getInstance().registerAnimation(taskId, morpher);

        AnimatorSet set = new AnimatorSet();
        set.play(watchdog);
        result.setAnimation(set, mLauncher);

        morpher.start();
    }

    private void forceFinalState(RemoteAnimationTargetCompat target, Rect endBounds) {
        try {
            android.view.SurfaceControl.Transaction t = new android.view.SurfaceControl.Transaction();
            float scale = (float) endBounds.width()
                    / mLauncher.getDeviceProfile().widthPx;
            android.view.SurfaceControl leashSc = target.leash.getSurfaceControl();
            t.setMatrix(leashSc, scale, 0, 0, scale)
                    .setPosition(leashSc, endBounds.left, endBounds.top)
                    .setWindowCrop(leashSc,
                            endBounds.width(), endBounds.height())
                    .apply();
        } catch (Exception ignored) {}
    }

    private Rect getIconBoundsOnScreen() {
        Rect bounds = new Rect();
        if (mIconView != null) {
            int[] pos = new int[2];
            mIconView.getLocationOnScreen(pos);
            bounds.set(pos[0], pos[1],
                    pos[0] + mIconView.getWidth(),
                    pos[1] + mIconView.getHeight());
        } else {
            int iconSize = mLauncher.getDeviceProfile().iconSizePx;
            int cx = mLauncher.getDeviceProfile().widthPx / 2;
            int cy = mLauncher.getDeviceProfile().heightPx / 2;
            bounds.set(cx - iconSize / 2, cy - iconSize / 2,
                    cx + iconSize / 2, cy + iconSize / 2);
        }
        return bounds;
    }
}
