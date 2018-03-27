package com.austinmzhang.gpstracker;

/**
 * Created by Austin on 3/21/2018.
 */

public final class Constants {

    // Milliseconds per second
    private static final int MILLISECONDS_PER_SECOND = 1000;
    // Update frequency in seconds
    private static final int UPDATE_INTERVAL_IN_SECONDS = 900;
    // Update frequency in milliseconds
    public static final long UPDATE_INTERVAL = MILLISECONDS_PER_SECOND * UPDATE_INTERVAL_IN_SECONDS;
    // The fastest update frequency, in seconds
    private static final int FASTEST_INTERVAL_IN_SECONDS = 300;//0;
    // A fast frequency ceiling in milliseconds

    private static final int FASTEST_INTERVAL_IN_SECONDS_TRACKING = 30;
    private static final int UPDATE_INTERVAL_IN_SECONDS_TRACKING = 45;


    public static final long FASTEST_INTERVAL = MILLISECONDS_PER_SECOND * FASTEST_INTERVAL_IN_SECONDS;

    public static final long UPDATE_INTERVAL_TRACKING = MILLISECONDS_PER_SECOND * UPDATE_INTERVAL_IN_SECONDS_TRACKING;

    public static final long FASTEST_INTERVAL_TRACKING = MILLISECONDS_PER_SECOND *FASTEST_INTERVAL_IN_SECONDS_TRACKING;

    public static final String RUNNING = "runningInBackground"; // Recording data in background


    /**
     * Suppress default constructor for noninstantiability
     */
    private Constants() {
        throw new AssertionError();
    }
}
