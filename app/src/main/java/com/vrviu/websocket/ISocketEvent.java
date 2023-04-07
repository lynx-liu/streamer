package com.vrviu.websocket;

import org.java_websocket.WebSocket;

public interface ISocketEvent {
    void onOpen();
    boolean onJoin(WebSocket conn, String userId, String type, String sdp, boolean disableEncryption, boolean disableSync, boolean enableAvUpbound, int clientSupportHevc);
    void onAnswer(String userId, String sdp);
    void onClose(String str);
}
