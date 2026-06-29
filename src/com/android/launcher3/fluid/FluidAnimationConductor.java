package com.android.launcher3.fluid;

import android.util.SparseArray;

public class FluidAnimationConductor {
    private static FluidAnimationConductor sInstance;
    private final SparseArray<FluidSurfaceMorpher> mActiveAnimations = new SparseArray<>();

    private FluidAnimationConductor() {}

    public static FluidAnimationConductor getInstance() {
        if (sInstance == null) sInstance = new FluidAnimationConductor();
        return sInstance;
    }

    public FluidMotionState stealStateAndCancel(int taskId) {
        FluidSurfaceMorpher activeMorpher = mActiveAnimations.get(taskId);
        if (activeMorpher != null) {
            FluidMotionState interruptedState = activeMorpher.cancelAndExtractState();
            mActiveAnimations.remove(taskId);
            return interruptedState;
        }
        return null;
    }

    public void registerAnimation(int taskId, FluidSurfaceMorpher morpher) {
        mActiveAnimations.put(taskId, morpher);
    }

    public void unregisterAnimation(int taskId) {
        mActiveAnimations.remove(taskId);
    }
}
