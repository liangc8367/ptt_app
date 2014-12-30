package com.bluesky.osprey.pttapp.protocol;

import java.nio.ByteBuffer;

/**
 * Created by liangc on 29/12/14.
 */
public class Registration extends ProtocolBase {

    public Registration(){
    }

    public Registration(ByteBuffer payload){
        unserialize(payload);
    }

    @Override
    public void unserialize(ByteBuffer payload){
        super.unserialize(payload);
        payload = super.getPayload();
        mSUID = payload.getLong();
    }

    @Override
    public void serialize(ByteBuffer payload){
        super.serialize(payload);
        payload.putLong(mSUID);
    }

    @Override
    public int getSize() {
        return super.getSize() + Long.SIZE / Byte.SIZE;
    }

    public long getSUID() {
        return mSUID;
    }

    public void setSUID(long mSUID) {
        this.mSUID = mSUID;
    }

    private long    mSUID   = 0; // subscriber id

}
