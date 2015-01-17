package com.bluesky.osprey.pttapp;

import java.util.concurrent.TimeUnit;

/**
 * Created by liangc on 24/12/14.
 */
public class GlobalConstants {
    public static final String TAG="PTTApp";

    /** connectivity parameters */
    public static final String TRUNK_CENTER_ADDR    = "192.168.0.105";  //"10.0.2.2"; //
    public static final int TRUNK_CENTER_PORT   = 32000;
    public static final int LOCAL_PORT   = TRUNK_CENTER_PORT + 1;

    /** call timing parameters */
    public final static int REGISTRATION_RETRY_TIME    = 10 * 1000;  // 10s
    public final static int REGISTRATION_MAX_RETRY     = 0;    // infinit
    public final static int KEEPALIVE_PERIOD           = 10 * 1000;    // 10s TODO: STUN parameter?

    public final static int CALL_PACKET_INTERVAL       = 20; // 20ms TODO: consider 40ms
    public final static int CALL_PREAMBLE_NUMBER      = 3;  // 3 preambles (3 * 20);
    public final static TimeUnit TIME_UNIT                   = TimeUnit.MILLISECONDS;
    public final static int JITTER_DEPTH                = 6;

    public final static int CALL_HANG_PERIOD            = 5 * 1000; // 5 second
    public final static int CALL_TERMINATOR_NUMBER      = 3; // 3 call terminator (3 * 20)

    /** tentative subscriber parameters */
    public static final short INIT_SEQ_NUMBER = 22321;

    public static final long TGT_ID         = 0x900d000001L;
    public static final long SUB_ID         = 0xBADBEEF001L;

}
