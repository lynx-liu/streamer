package com.vrviu.websocket;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.java_websocket.WebSocket;

public class SocketManager implements ISocketEvent {
    private final static String TAG = "SocketManager";
    private MyWebSocket webSocket;
    private static final int XRtcSignalingPort = 47996;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public SocketManager() {
        if (webSocket == null) {
            webSocket = new MyWebSocket(XRtcSignalingPort, this);
        }
    }

    public void start() {
        if(webSocket!=null) {
            webSocket.start();
        }
    }

    public void stop() {
        if(webSocket!=null) {
            try {
                webSocket.stop();
            } catch (Exception e) {
                e.printStackTrace();
            }
            webSocket = null;
        }
    }

    @Override
    public void onOpen() {
        Log.i(TAG, "socket is open!");
    }

    @Override
    public boolean onJoin(WebSocket conn, String uid, String type, String sdp, boolean disableEncryption, boolean disableSync, boolean enableAvUpbound, int clientSupportHevc) {
        if (webSocket != null) {
            webSocket.sendJoin(conn, uid, type, sdp, disableEncryption, disableSync, enableAvUpbound, clientSupportHevc);
            return true;
        }
        return false;
    }

    @Override
    public void onAnswer(String userId, String sdp) {
        handler.post(() -> {

        });
    }

    @Override
    public void onClose(String str) {
        Log.i(TAG, "socket is close!");
    }
}
