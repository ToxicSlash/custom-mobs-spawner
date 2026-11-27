package com.ashtonthedev.custommobsspawner.combat;

public final class PostureDisplaySnapshotTest {
    private static void expect(PostureDisplaySnapshot actual, int percent, boolean visible,
                               boolean staggered, int red, boolean vulnerable) {
        PostureDisplaySnapshot expected = new PostureDisplaySnapshot(percent, visible, staggered, red, vulnerable);
        if (!expected.equals(actual)) throw new AssertionError(expected + " != " + actual);
    }

    public static void main(String[] args) {
        expect(PostureDisplaySnapshot.of(1, true, false, false), 100, true, false, 0, false);
        expect(PostureDisplaySnapshot.of(.374f, true, false, true), 37, true, false, 0, true);
        expect(PostureDisplaySnapshot.of(1, true, true, false), 0, true, true, 100, false);
        expect(PostureDisplaySnapshot.of(.3f, true, true, true), 0, true, true, 100, true);
        expect(PostureDisplaySnapshot.of(.3f, false, true, true), 100, false, false, 0, false);
        expect(PostureDisplaySnapshot.of(-.5f, true, false, false), 0, true, false, 0, false);
        expect(PostureDisplaySnapshot.of(1.5f, true, false, false), 100, true, false, 0, false);
        System.out.println("CMOBS_POSTURE_DISPLAY_SNAPSHOT_OK");
    }
}
