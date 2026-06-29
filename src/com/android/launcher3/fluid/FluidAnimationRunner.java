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
    private static final float ICON_CORNER_RADIUS = 60f;

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
        if (appTargets == null || appTargets.length == 0) {
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
        float targetRadius = mIsOpening ? 0f : ICON_CORNER_RADIUS;

        final ValueAnimator systemSyncAnimator = ValueAnimator.ofFloat(0, 1);
        systemSyncAnimator.setDuration(SPRING_TIMEOUT_MS);

        final boolean[] morphCompleted = new boolean[]{false};

        Runnable onComplete = () -> {
            morphCompleted[0] = true;
            if (systemSyncAnimator.isRunning()) {
                systemSyncAnimator.cancel();
            }
        };

        FluidMotionState stolenState = FluidAnimationConductor.getInstance().stealStateAndCancel(taskId);

        FluidSurfaceMorpher morpher = new FluidSurfaceMorpher(
                taskId, target, stolenState,
                screenBounds,
                endBounds,
                targetRadius, onComplete);

        systemSyncAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!morphCompleted[0]) {
                    FluidMotionState remaining = FluidAnimationConductor.getInstance().stealStateAndCancel(taskId);
                    if (remaining != null) {
                        forceFinalState(target, endBounds, targetRadius);
                    }
                }
            }
        });

        FluidAnimationConductor.getInstance().registerAnimation(taskId, morpher);

        AnimatorSet syncSet = new AnimatorSet();
        syncSet.play(systemSyncAnimator);
        result.setAnimation(syncSet, mLauncher);

        morpher.start();
    }

    private void forceFinalState(RemoteAnimationTargetCompat target, Rect endBounds, float targetRadius) {
        try {
            android.view.SurfaceControl.Transaction t = new android.view.SurfaceControl.Transaction();
            float scale = (float) endBounds.width() / mLauncher.getDeviceProfile().widthPx;
            android.view.SurfaceControl leashSc = target.leash.getSurfaceControl();

            Rect crop = new Rect(0, 0, mLauncher.getDeviceProfile().widthPx,
                                (int) (endBounds.height() / scale));

            t.setMatrix(leashSc, scale, 0, 0, scale)
                    .setPosition(leashSc, endBounds.left, endBounds.top)
                    .setWindowCrop(leashSc, crop)
                    .setCornerRadius(leashSc, targetRadius / scale)
                    .apply();
        } catch (Exception ignored) {}
    }

    private Rect getIconBoundsOnScreen() {
        Rect bounds = new Rect();
        if (mIconView != null && mIconView.isAttachedToWindow()) {
            int[] pos = new int[2];
            mIconView.getLocationOnScreen(pos);
            bounds.set(pos[0], pos[1], pos[0] + mIconView.getWidth(), pos[1] + mIconView.getHeight());
        } else {
            int iconSize = mLauncher.getDeviceProfile().iconSizePx;
            int cx = mLauncher.getDeviceProfile().widthPx / 2;
            int cy = mLauncher.getDeviceProfile().heightPx / 2;
            bounds.set(cx - iconSize / 2, cy - iconSize / 2, cx + iconSize / 2, cy + iconSize / 2);
        }
        return bounds;
    }
}
