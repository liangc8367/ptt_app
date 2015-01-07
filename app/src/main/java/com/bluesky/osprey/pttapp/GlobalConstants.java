package com.bluesky.osprey.pttapp;

/**
 * Created by liangc on 24/12/14.
 */
public class GlobalConstants {
    public static final String TAG="PTTApp";
    public static final String TRUNK_CENTER_ADDR    = "10.0.2.2"; // "192.168.0.105";
    public static final int TRUNK_CENTER_PORT   = 32000;
    public static final int LOCAL_PORT   = TRUNK_CENTER_PORT + 1;

    public static final short INIT_SEQ_NUMBER = 22321;

    public static final long TGT_ID         = 0x900d000001L;
    public static final long SUB_ID         = 0xBADBEEF001L;

}
