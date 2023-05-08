package com.vrviu.websocket;

import android.util.Log;

import com.vrviu.utils.JsonUtils;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class MyWebSocket extends WebSocketServer {
    private final static String TAG = "WebSocket";
    private final ISocketEvent socketEvent;
    private static final String charSet ="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    public MyWebSocket(int port, ISocketEvent event) {
        super(new InetSocketAddress("127.0.0.1", port));
        this.socketEvent = event;
    }

    private static String getRandomString(int lenth) {
        Random random=new Random();
        StringBuilder sb=new StringBuilder(lenth);

        for(int i=0;i<lenth;i++) {
            sb.append(charSet.charAt(random.nextInt(charSet.length())));
        }
        return sb.toString();
    }

    private boolean dispatchJoin(WebSocket conn, JSONObject msg) throws JSONException {
        String uid = JsonUtils.get(msg,"uid",getRandomString(8));
        boolean disableEncryption = JsonUtils.get(msg,"disableEncryption",false);
        boolean enableAvUpbound = JsonUtils.get(msg,"enableAvUpbound",false);
        boolean disableSync = JsonUtils.get(msg,"disableSync",true);
        int clientSupportHevc = JsonUtils.get(msg,"clientSupportHevc",-1);
        String join = JsonUtils.get(msg,"join",null);
        if(join==null) return false;

        JSONObject joinObject = new JSONObject(join);
        String offer = JsonUtils.get(joinObject, "offer", null);
        if(offer==null) return false;

        JSONObject offerObject = new JSONObject(offer);
        String sdp = JsonUtils.get(offerObject,"sdp",null);
        if(sdp==null) return false;

        String type = JsonUtils.get(offerObject,"type", "offer");
        return socketEvent.onJoin(conn, uid, type, sdp, disableEncryption, disableSync, enableAvUpbound, clientSupportHevc);
    }

    public void sendJoin(WebSocket conn, String uid, String type, String sdp, boolean disableEncryption, boolean disableSync, boolean enableAvUpbound, int clientSupportHevc) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", "onjoin");
        map.put("event", "onJoined");
        map.put("uid", uid);
        JSONObject object = new JSONObject(map);
        String jsonString = object.toString();
        Log.d(TAG, "send-->" + jsonString);
        conn.send(jsonString);
    }

    public void sendOffer(WebSocket conn, String uid, String destUid, String sdp) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", "onOffer");
        map.put("uid", uid);//自己的uid
        map.put("peer", destUid);//对方的uid

        Map<String, Object> childMap = new HashMap<>();
        childMap.put("type", "offer");
        childMap.put("sdp", sdp);
        map.put("offer", childMap);

        JSONObject object = new JSONObject(map);
        String jsonString = object.toString();
        Log.d(TAG, "send-->" + jsonString);
        conn.send(jsonString);
    }

    private boolean dispatchAnswer(WebSocket conn, JSONObject msg) throws JSONException {
        String uid = JsonUtils.get(msg,"uid", null);
        if(uid==null) return false;

        String answer = JsonUtils.get(msg,"answer", null);
        if(answer==null) return false;

        JSONObject answerObject = new JSONObject(answer);
        String type = JsonUtils.get(answerObject,"type",null);
        if(type==null) return false;

        String sdp = JsonUtils.get(answerObject, "sdp", null);
        if(sdp==null) return false;

        this.socketEvent.onAnswer(uid, sdp);
        return true;
    }

    public void sendIceCandidate(WebSocket conn, String uid, String destUid, String sdpMid, String sdpMLineIndex, String candidate) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", "onCandidate");
        map.put("uid", uid);//自己的uid
        map.put("peer", destUid);//对方的uid

        Map<String, Object> childMap = new HashMap<>();
        childMap.put("sdpMid", sdpMid);
        childMap.put("sdpMLineIndex", sdpMLineIndex);
        childMap.put("candidate", candidate);
        map.put("candidate", childMap);

        JSONObject object = new JSONObject(map);
        String jsonString = object.toString();
        Log.d(TAG, "send-->" + jsonString);
        conn.send(jsonString);
    }

    private boolean dispatchIceCandidate(WebSocket conn, JSONObject msg) throws JSONException {
        String uid = JsonUtils.get(msg,"uid", null);
        if(uid==null) return false;

        String candidate = JsonUtils.get(msg,"candidate", null);
        if(candidate==null) return false;

        JSONObject candidateObject = new JSONObject(candidate);
        String sdpMid = JsonUtils.get(candidateObject,"sdpMid",null);
        if(sdpMid==null || sdpMid.isEmpty()) return false;

        String child_candidate = JsonUtils.get(candidateObject, "candidate", null);
        if(child_candidate==null || child_candidate.isEmpty()) return false;

        int sdpMLineIndex = JsonUtils.get(candidateObject, "sdpMLineIndex", 0);
        return true;
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        Log.e("llx", "onOpen");
        this.socketEvent.onOpen();
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        Log.e("llx", "onClose:" + reason + "remote:" + remote);
        this.socketEvent.onClose(reason);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        Log.d(TAG, message);

        try {
            JSONObject msg = new JSONObject(message);

            String type = (String) msg.get("type");
            switch (type) {
                case "join":
                    dispatchJoin(conn, msg);
                    break;

                case "answer":
                    dispatchAnswer(conn, msg);
                    break;

                case "candidate":
                case "trickle":
                    dispatchIceCandidate(conn, msg);
                    break;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        Log.e("llx", "onError:" + ex.toString());
        this.socketEvent.onClose(ex.toString());
    }

    @Override
    public void onStart() {

    }
}
