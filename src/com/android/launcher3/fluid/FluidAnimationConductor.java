package com.android.launcher3.fluid;

import java.util.concurrent.ConcurrentHashMap;

public class FluidAnimationConductor {
    private static FluidAnimationConductor sInstance;
    private final ConcurrentHashMap<Integer, FluidSurfaceMorpher> mActiveAnimations = new ConcurrentHashMap<>();

    private FluidAnimationConductor() {}

    public static synchronized FluidAnimationConductor getInstance() {
        if (sInstance == null) sInstance = new FluidAnimationConductor();
        return sInstance;
    }

    public FluidMotionState stealStateAndCancel(int taskId) {
        FluidSurfaceMorpher activeMorpher = mActiveAnimations.remove(taskId);
        if (activeMorpher != null) {
            return activeMorpher.cancelAndExtractState();
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
