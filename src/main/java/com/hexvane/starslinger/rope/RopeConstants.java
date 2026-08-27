package com.hexvane.starslinger.rope;

/** Tunable Star Slinger rope simulation and visual constants. */
public final class RopeConstants {
    public static final String ROPE_SEGMENT_MODEL_ID = "StarSlinger_RopeSegment";

    /**
     * Enough segments that rope node spacing stays below the fixed visual length of one segment prop
     * ({@link #BASE_SEGMENT_LENGTH} at unit scale). Prop entities do not reliably stretch at runtime.
     */
    public static final int SEGMENT_COUNT = 64;

    public static final int NODE_COUNT = SEGMENT_COUNT + 1;

    /** Fixed world-space length of one segment prop at unit scale (see model HitBox). */
    public static final float BASE_SEGMENT_LENGTH = 0.5f;

    /** Visible segment props per block of line length. */
    public static final int SEGMENT_VISUAL_DENSITY = 3;

    /** Gravity while swinging so the rope hangs with unused slack. */
    public static final float SWING_GRAVITY = 6.0f;

    /** Reduced gravity while launching so the rope stays pulled taut. */
    public static final float LAUNCH_GRAVITY = 1.5f;

    public static final int CONSTRAINT_ITERATIONS = 10;

    /** Rope rest length multiplier relative to hand-to-tether distance (1 = taut). */
    public static final float ROPE_SLACK_FACTOR = 1.005f;

    /** Slack while launching so the line visually tightens toward the tether. */
    public static final float LAUNCH_ROPE_SLACK_FACTOR = 1.0f;

    /** Pulls swing nodes slightly toward a straight hand-to-tether line. */
    public static final float SWING_ROPE_STRAIGHTEN = 0.05f;

    /** Pulls launch nodes toward a straight hand-to-tether line. */
    public static final float LAUNCH_ROPE_STRAIGHTEN = 0.45f;

    /** Pulls segment i&gt;0 slightly back along its edge so props overlap at rope nodes. */
    public static final float SEGMENT_JOINT_OVERLAP = 0.08f;

    /** Below this hand-to-tether distance, skip sag simulation and pin nodes in a straight line. */
    public static final float SHORT_LINE_STRAIGHTEN_BLOCKS = 4.0f;

    private RopeConstants() {}
}
