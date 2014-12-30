package com.bluesky.osprey.pttapp.protocol;

import java.nio.ByteBuffer;
/**
 *
 * Created by liangc on 29/12/14.
 */
public class ProtocolFactory {

   /** unserialize protocol */
   static public ProtocolBase getProtocol(ByteBuffer payload){
       short protoType = ProtocolBase.peepType(payload);
       ProtocolBase proto;
       switch(protoType){
           case ProtocolBase.PTYPE_ACK: {
               Ack p = new Ack();
               p.unserialize(payload);
               proto = p;
               break;
           }
           case ProtocolBase.PTYPE_REGISTRATION: {
               Registration p = new Registration();
               p.unserialize(payload);
               proto = p;
               break;
           }
           default:
               throw new IllegalArgumentException();
       }
       return proto;
   }
}
