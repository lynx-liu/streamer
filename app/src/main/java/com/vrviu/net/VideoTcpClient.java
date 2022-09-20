package com.vrviu.net;

import android.util.Log;

import com.vrviu.utils.JsonUtils;

import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;

public abstract class VideoTcpClient extends TcpClient {
    private static final int startStreaming = 0x01;
    private static final int stopStreaming = 0x02;
    private static final int requestIdrFrame = 0x03;
    private static final int reconfigureEncode = 0x04;
    private static final int HEARTBEAT_TIME = 5000;
    private static final byte[] heartbeat = new byte[]{0x00,0x00,0x00,0x08,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00};
    private static DataOutputStream dataOutputStream = null;

    public abstract boolean startStreaming(String flowId,  String lsIp, boolean tcp,
                                           int lsVideoPort, int lsAudioPort, int lsControlPort,
                                           boolean h264, String videoCodecProfile, int idrPeriod,
                                           int maxFps, int minFps, int width, int height,
                                           int bitrate, int orientationType, int enableSEI,
                                           int rateControlMode, int gameMode,
                                           String packageName, String downloadDir);
    public abstract void stopStreaming(boolean stopVideo, boolean stopAudio, boolean stopControl);
    public abstract void requestIdrFrame();
    public abstract boolean reconfigureEncode(int width,int height,int bitrate,int fps,int frameInterval,int profile,int orientation,int codec);

    private final Timer timer = new Timer();
    private TimerTask timerTask = new TimerTask() {
        @Override
        public void run() {
            if(dataOutputStream!=null) {
                try {
                    dataOutputStream.write(heartbeat);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    public VideoTcpClient(String ip, int port){
        super(ip,port);
        timer.schedule(timerTask,HEARTBEAT_TIME,HEARTBEAT_TIME);
    }

    private boolean onStartStreaming(final String jsonString) {
        Log.d("llx",jsonString);
        try {
            JSONObject jsonObject = new JSONObject(jsonString);
            String flowId = JsonUtils.get(jsonObject, "flowId", "");
            String lsIp = JsonUtils.get(jsonObject, "lsIp", "10.0.2.2");
            String lsAVProtocol = JsonUtils.get(jsonObject, "lsAVProtocol", "tcp");
            int lsVideoPort = JsonUtils.get(jsonObject, "lsVideoPort", -1);//51898
            int lsAudioPort = JsonUtils.get(jsonObject, "lsAudioPort", -1);//51897
            int lsControlPort = JsonUtils.get(jsonObject, "lsControlPort", -1);//5000
            String codec = JsonUtils.get(jsonObject, "codec", "h264");
            String videoCodecProfile = JsonUtils.get(jsonObject, "videoCodecProfile", "baseline");
            int idrPeriod = JsonUtils.get(jsonObject, "idrPeriod", 3600);
            int maxFps = JsonUtils.get(jsonObject, "maxFps", 30);
            int minFps = JsonUtils.get(jsonObject, "minFps", 0);
            int width = JsonUtils.get(jsonObject, "width", 1280);
            int height = JsonUtils.get(jsonObject, "height", 720);
            int bitrate = JsonUtils.get(jsonObject, "bitrate", 4096);
            int orientationType = JsonUtils.get(jsonObject, "orientationType", 0);
            int enableSEI = JsonUtils.get(jsonObject, "enableSEI", 0);
            int rateControlMode = JsonUtils.get(jsonObject, "rateControlMode", 1);
            int gameMode = JsonUtils.get(jsonObject, "gameMode", 0);
            String packageName = JsonUtils.get(jsonObject, "packageName", null);
            String downloadDir = JsonUtils.get(jsonObject, "downloadDir", null);

            return startStreaming(flowId,lsIp,lsAVProtocol.equals("tcp"),lsVideoPort,lsAudioPort,lsControlPort,codec.equals("h264"),videoCodecProfile,idrPeriod,maxFps,minFps,width,height,bitrate,orientationType,enableSEI,rateControlMode,gameMode,packageName,downloadDir);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean onStopStreaming(final String jsonString) {
        try {
            JSONObject jsonObject = new JSONObject(jsonString);
            int video = JsonUtils.get(jsonObject, "video", -1);//51898
            int audio = JsonUtils.get(jsonObject, "audio", -1);//51897
            int control = JsonUtils.get(jsonObject, "control", -1);//5000
            //==0说明不希望停止对应流
            stopStreaming(video!=0,audio!=0,control!=0);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private final boolean onReconfigureEncode(final byte[] data) {
        int width = (short) (((data[0]&0xFF)<<8)|(data[1]&0xFF));
        int height = (short) (((data[2]&0xFF)<<8)|(data[3]&0xFF));
        int bitrate = ((data[4]&0xFF)<<24)|((data[5]&0xFF)<<16)|((data[6]&0xFF)<<8)|(data[7]&0xFF);
        int fps = data[8];
        int profile = data[9];
        int orientation = data[10];
        int codec = data[11];
        int frameInterval = ((data[12]&0xFF)<<24)|((data[13]&0xFF)<<16)|((data[14]&0xFF)<<8)|(data[15]&0xFF);
        return reconfigureEncode(width,height,bitrate,fps,frameInterval,profile,orientation,codec);
    }

    @Override
    public void onConnected(Socket client) {
        try {
            DataInputStream dataInputStream = new DataInputStream(client.getInputStream());
            dataOutputStream = new DataOutputStream(client.getOutputStream());

            byte[] header = new byte[12];
            while (!isInterrupted()){
                dataInputStream.readFully(header);
                int dataLen = ((header[0]&0xFF)<<24)|((header[1]&0xFF)<<16)|((header[2]&0xFF)<<8)|(header[3]&0xFF)-8;
                byte version = header[4];
                switch(version) {
                    case 0x11: {
                        byte type = header[5];
//                      int extsize = ((header[6]&0xFF)<<8)|(header[7]&0xFF);
//                      long seqnum = ((header[8]&0xFF)<<24)|((header[9]&0xFF)<<16)|((header[10]&0xFF)<<8)|(header[11]&0xFF);

                        try {
                            byte[] data = new byte[dataLen];
                            dataInputStream.readFully(data, 0, dataLen);

                            switch (type) {
                                case startStreaming:
                                    onStartStreaming(new String(data));
                                    break;

                                case stopStreaming:
                                    onStopStreaming(new String(data));
                                    break;

                                case requestIdrFrame:
                                    requestIdrFrame();
                                    break;

                                case reconfigureEncode:
                                    onReconfigureEncode(data);
                                    break;
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                }
            }
            dataInputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
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
