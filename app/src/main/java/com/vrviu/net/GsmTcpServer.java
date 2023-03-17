package com.vrviu.net;

import android.util.Log;

import com.vrviu.utils.JsonUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.Socket;

public class GsmTcpServer extends TcpServer {
    private static final int queryState = 0x01;
    private static final int notifyScene = 0x02;
    private static int requestId = 0;
    private static DataOutputStream dataOutputStream = null;

    public GsmTcpServer(int port) {
        super(port);
        setName(getClass().getSimpleName());
    }

    @Override
    public void interrupt() {
        if(dataOutputStream!=null) {
            try {
                dataOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            dataOutputStream = null;
        }
        super.interrupt();
    }

    public boolean send(String json) {
        Log.d("llx", json);
        byte[] buf = json.getBytes();
        if(dataOutputStream!=null) {
            try {
                dataOutputStream.writeInt(buf.length);
                dataOutputStream.write(buf);
                return true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public void reportState(int requestId) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("operationId",queryState);
            jsonObject.put("requestId",requestId);
            jsonObject.put("retCode",0);
            send(jsonObject.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void reportScene(int report) {
        int scene = report;
        int index = report>>8;

        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("operationId",notifyScene);
            jsonObject.put("requestId",requestId++);
            jsonObject.put("scene",scene);
            jsonObject.put("index",index);
            send(jsonObject.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onAccept(Socket client) {
        try {
            DataInputStream dataInputStream = new DataInputStream(client.getInputStream());
            dataOutputStream = new DataOutputStream(client.getOutputStream());

            while (dataOutputStream!=null){
                int dataLen = dataInputStream.readInt();

                try {
                    byte[] data = new byte[dataLen];
                    dataInputStream.readFully(data, 0, dataLen);

                    String jsonString = new String(data);
                    Log.d("llx",jsonString);

                    JSONObject jsonObject = new JSONObject(jsonString);
                    int operationId = JsonUtils.get(jsonObject, "operationId", -1);
                    int requestId = JsonUtils.get(jsonObject, "requestId", 0);
                    switch (operationId) {
                        case queryState:
                            reportState(requestId);
                            break;

                        default:
                            break;
                    }
                } catch (Exception e) {
                    StringWriter stringWriter = new StringWriter();
                    e.printStackTrace(new PrintWriter(stringWriter, true));
                    Log.e("llx", stringWriter.toString());
                }
            }
            dataInputStream.close();
        } catch (Exception e) {
            Log.e("llx",e.toString());
        } finally {
            try {
                dataOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            dataOutputStream = null;
        }
    }
}
