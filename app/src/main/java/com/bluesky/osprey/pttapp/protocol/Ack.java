package com.bluesky.osprey.pttapp.protocol;

import java.nio.ByteBuffer;

/**
 * Created by liangc on 29/12/14.
 */
public class Ack extends ProtocolBase {
    public static final int ACKTYPE_POSITIVE    = 1;
    public static final int ACKTYPE_NEGATIVE    = 0;

    public static final int OFFSET_ACKTYPE      = 0;

    public Ack(ByteBuffer payload){
        unserialize(payload);
    }

    public void unserialize(ByteBuffer payload){
        super.unserialize(payload);
        payload = super.getPayload();
        mAckType = payload.getShort();
        payload.position(getMySize());
        mOrigPacket = new ProtocolBase(payload.slice());
    }

    public void serialize(ByteBuffer payload){
        super.serialize(payload);
        payload.putShort(mAckType);
        mOrigPacket.serialize(payload);
    }

    static public int getSize(){
        return ProtocolBase.getSize() * 2 + 1 * Short.SIZE / Byte.SIZE;
    }

    static private int getMySize(){
        return 1 * Short.SIZE / Byte.SIZE;
    }

    public short getOrigSeq(){
        return mOrigPacket.getSequence();
    }

    public short getOrigType(){
        return mOrigPacket.getType();
    }


    public void setAckType(short mAckType) {
        this.mAckType = mAckType;
    }

    public short getAckType() {
        return mAckType;
    }

    private short mAckType  = ACKTYPE_NEGATIVE;     /// ack type
    private ProtocolBase    mOrigPacket = null;
}
