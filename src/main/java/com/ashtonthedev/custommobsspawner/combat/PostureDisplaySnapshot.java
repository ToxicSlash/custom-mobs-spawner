package com.ashtonthedev.custommobsspawner.combat;

/** One server-authoritative display snapshot for both owner and tracking clients. */
public record PostureDisplaySnapshot(int posturePercent, boolean visible, boolean staggered,
                                     int staggerPercent, boolean vulnerable) {
    public static PostureDisplaySnapshot of(float fraction, boolean visible, boolean staggered, boolean vulnerable) {
        if (!visible) return new PostureDisplaySnapshot(100, false, false, 0, false);
        // Gameplay clears accumulated damage on stagger; the UI must still display broken posture.
        if (staggered) return new PostureDisplaySnapshot(0, true, true, 100, vulnerable);
        int percent = Math.round(Math.max(0.0F, Math.min(1.0F, fraction)) * 100.0F);
        return new PostureDisplaySnapshot(percent, true, false, 0, vulnerable);
    }
}
