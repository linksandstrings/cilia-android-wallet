package com.cilia.wallet.activity.send.helper;

/**
 * Linear algorithm for build fees items, has no changable step
 * a = (max - min) / (maxPos - minPos);
 * <p>
 * value(position) = a * position + b
 */
public class LinearAlgorithm implements FeeItemsAlgorithm {
    private int minPosition;
    private int maxPosition;

    private double a;
    private double b;

    public LinearAlgorithm(long min, int minPos, long max, int maxPos) {
        this.minPosition = minPos;
        this.maxPosition = maxPos;

        a = (max - min) / (maxPos - minPos);
        b = min - minPos * a;
    }

    @Override
    public long computeValue(int position) {
        return (long) (a * position + b);
    }

    @Override
    public int getMinPosition() {
        return minPosition;
    }

    @Override
    public int getMaxPosition() {
        return maxPosition;
    }
}
