package com.bluesky.osprey.pttapp.protocol;

import java.lang.Short;
import java.nio.ByteBuffer;

/**
 * base of protocol packet
 * Created by liangc on 29/12/14.
 */
public class ProtocolBase {
    /** sequence # */
    public static final int OFFSET_SEQ         = 0;
    /** packet type */
    public static final int PTYPE_INVALID      = 0;
    public static final int PTYPE_ACK          = 1;
    public static final int PTYPE_REGISTRATION = 3;
    public static final int PTYPE_CALL_INIT    = 4;
    public static final int PTYPE_CALL_DATA    = 5;
    public static final int PTYPE_CALL_TERM    = 6;

    public static final int OFFSET_PTYPE       = 2;

    public ProtocolBase(){

    }

    /** ctor using received payload
     * @param payload
     */
    public ProtocolBase(ByteBuffer payload){
        unserialize(payload);
    }

    /** unserialize from ByteBuffer
     *
     * @param payload
     */
    public void unserialize(ByteBuffer payload){
        mPayload    = payload;
        mSequence = mPayload.getShort(OFFSET_SEQ);
        mType = mPayload.getShort(OFFSET_PTYPE);
    }

    /** serialize to ByteBuffer,
     *
     * @param payload, position will be changed afterwards
     */
    public void serialize(ByteBuffer payload){
        payload.putShort(mSequence);
        payload.putShort(mType);
    }

    /** get size of the proto
     *
     * @return
     * TODO: in future, we can subclass ByteBuffer as Sizer class, then
     * we call serializer() with a Sizer instance, and get the size from
     * the Sizer afterwards.
     *
     */
    public int getSize(){
        return 2 * Short.SIZE / Byte.SIZE;
    }

    static short peepType(ByteBuffer payload){
        return payload.getShort(OFFSET_PTYPE);
    }

    public short getType(){
        return mType;
    }

    public short getSequence(){
        return mSequence;
    }



    public ByteBuffer getPayload(){
        mPayload.position(getSize());
        ByteBuffer payload = mPayload.slice();
        mPayload.position(0);
        return payload;
    }


    public void setSequence(short mSequence) {
        this.mSequence = mSequence;
    }

    public void setType(short mType) {
        this.mType = mType;
    }

    /** private members */
    private short mSequence = 0;

    private short mType     = PTYPE_INVALID;
    ByteBuffer    mPayload  = null;
}
