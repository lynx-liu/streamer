package com.vrviu.net;

import android.util.Log;

import com.vrviu.manager.SurfaceFlingerHelper;
import com.vrviu.opengl.RenderConfig;
import com.vrviu.utils.JsonUtils;

import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.Socket;

public class VideoTcpServer extends TcpServer {
    private static final int startStreaming = 0x01;
    private static final int stopStreaming = 0x02;
    private static final int requestIdrFrame = 0x03;
    private static final int reconfigureEncode = 0x04;
    private static final int reportFps = 0x0B;
    private static final int reportScene = 0x0C;
    private static final byte RESPONSE = (byte) 0xFF;
    private static final byte VERSION = 0x11;//协议版本
    private static final byte SUCCEEDED = 0x01;//执行成功
    private static final byte FAILED = 0x00;//执行失败
    private static final byte UNSUPPORT = (byte) 0xFE;//不支持的类型
    private static final byte ERROR = (byte) 0xFF;//版本错误
    private static final int AVC = 0;
    private static final int HEVC = 1;

    private static int nSeqnum = 0;
    private static DataOutputStream dataOutputStream = null;
    private static SurfaceFlingerHelper surfaceFlingerHelper = null;

    public interface Callback {
        boolean startStreaming(String flowId, String lsIp, boolean tcp, int lsVideoPort, int lsAudioPort, int lsControlPort,
                               int codec, String videoCodecProfile, int idrPeriod, int maxFps, int minFps, int width, int height,
                               int bitrate, int orientationType, int enableSEI, int rateControlMode,int gameMode, String packageName,
                               String downloadDir, RenderConfig config, int audioType, int defaulQP, int maxQP, int minQP,
                               boolean useLocalBrowser, String fakeVideoPath);

        void stopStreaming(boolean stopVideo, boolean stopAudio, boolean stopControl);
        void requestIdrFrame();
        boolean reconfigureEncode(int width, int height, int bitrate, int fps, int idrPeriod, int profile, int orientation, int codec,
                                  int defaulQP, int minQP, int maxQP, int rateControlMode);
    }

    private final Callback mCallback;
    public VideoTcpServer(Callback callback, int port){
        super(port);
        setName(getClass().getSimpleName());
        mCallback = callback;
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

    private boolean onStartStreaming(final String jsonString) {
        Log.d("llx",jsonString);
        try {
            RenderConfig renderConfig = new RenderConfig();
            JSONObject jsonObject = new JSONObject(jsonString);
            String flowId = JsonUtils.get(jsonObject, "flowId", "");
            String lsIp = JsonUtils.get(jsonObject, "lsIp", "10.0.2.2");
            String lsAVProtocol = JsonUtils.get(jsonObject, "lsAVProtocol", "tcp");
            int lsVideoPort = JsonUtils.get(jsonObject, "lsVideoPort", -1);//51898
            int lsAudioPort = JsonUtils.get(jsonObject, "lsAudioPort", -1);//51897
            int lsControlPort = JsonUtils.get(jsonObject, "lsControlPort", -1);//5000
            int codec = JsonUtils.get(jsonObject, "codec", "h264").equals("h264")?AVC:HEVC;
            int videoCodec = JsonUtils.get(jsonObject, "videoCodec", -1);
            String videoCodecProfile = JsonUtils.get(jsonObject, "videoCodecProfile", "baseline");
            int idrPeriod = JsonUtils.get(jsonObject, "idrPeriod", 3600);
            int maxFps = JsonUtils.get(jsonObject, "maxFps", 30);
            int minFps = JsonUtils.get(jsonObject, "minFps", 0);
            renderConfig.dynamicFps = JsonUtils.get(jsonObject, "dynamicFps", 0)!=0;
            int width = JsonUtils.get(jsonObject, "width", 1280);
            int height = JsonUtils.get(jsonObject, "height", 720);
            int bitrate = JsonUtils.get(jsonObject, "bitrate", 4096);
            int orientationType = JsonUtils.get(jsonObject, "orientationType", 0);
            int enableSEI = JsonUtils.get(jsonObject, "enableSEI", 0);
            int rateControlMode = JsonUtils.get(jsonObject, "rateControlMode", 1);
            int gameMode = JsonUtils.get(jsonObject, "gameMode", 0);
            String packageName = JsonUtils.get(jsonObject, "packageName", null);
            String downloadDir = JsonUtils.get(jsonObject, "downloadDir", null);
            renderConfig.sharp = (float) JsonUtils.get(jsonObject, "sharp",0.0);
            renderConfig.showText = JsonUtils.get(jsonObject, "showText", 0)!=0;
            int audioType = JsonUtils.get(jsonObject, "audioType", 0);
            int defaulQP = JsonUtils.get(jsonObject, "defaulQP", 0);
            int maxQP = JsonUtils.get(jsonObject, "maxQP", 0);
            int minQP = JsonUtils.get(jsonObject, "minQP", 0);
            boolean useLocalBrowser = JsonUtils.get(jsonObject, "useLocalBrowser", 0)!=0;
            String fakeVideoPath = JsonUtils.get(jsonObject, "fakeVideoPath", null);

            String startupArgs = JsonUtils.get(jsonObject, "startupArgs", null);
            if(startupArgs!=null) {
                JSONObject startupArgsObject = new JSONObject(startupArgs);
                renderConfig.brightness = JsonUtils.get(startupArgsObject, "brightness", 0);
                renderConfig.contrast = JsonUtils.get(startupArgsObject, "contrast", 0);
                renderConfig.saturation = JsonUtils.get(startupArgsObject, "saturation", 0);
            }

            String viewName = JsonUtils.get(jsonObject,"viewName",null);
            if(viewName!=null && !viewName.trim().isEmpty()) {
                if(surfaceFlingerHelper!=null) {
                    surfaceFlingerHelper.interrupt();
                    surfaceFlingerHelper = null;
                }
                surfaceFlingerHelper = new SurfaceFlingerHelper(viewName, fps -> {
                    Log.d("llx","fps:"+fps);
                    reportFps(fps);
                    return false;
                });
            }

            if(videoCodec<0) videoCodec = codec;
            return mCallback.startStreaming(flowId,lsIp,lsAVProtocol.equals("tcp"),lsVideoPort,lsAudioPort,lsControlPort,
                    videoCodec,videoCodecProfile,idrPeriod,maxFps,minFps,width,height,bitrate,orientationType, enableSEI,
                    rateControlMode,gameMode,packageName,downloadDir,renderConfig,audioType,defaulQP,maxQP,minQP,
                    useLocalBrowser,fakeVideoPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean onStopStreaming(final String jsonString) {
        Log.d("llx",jsonString);
        if(surfaceFlingerHelper!=null) {
            surfaceFlingerHelper.interrupt();
            surfaceFlingerHelper = null;
        }

        try {
            JSONObject jsonObject = new JSONObject(jsonString);
            int video = JsonUtils.get(jsonObject, "video", -1);//51898
            int audio = JsonUtils.get(jsonObject, "audio", -1);//51897
            int control = JsonUtils.get(jsonObject, "control", -1);//5000
            //==0说明不希望停止对应流
            mCallback.stopStreaming(video!=0,audio!=0,control!=0);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean onReconfigureEncode(final byte[] data) {
        int width = (short) (((data[0]&0xFF)<<8)|(data[1]&0xFF));
        int height = (short) (((data[2]&0xFF)<<8)|(data[3]&0xFF));
        int bitrate = ((data[4]&0xFF)<<24)|((data[5]&0xFF)<<16)|((data[6]&0xFF)<<8)|(data[7]&0xFF);
        int fps = data[8];
        int profile = data[9];
        int orientation = data[10];
        int codec = data[11];
        int idrPeriod = ((data[12]&0xFF)<<24)|((data[13]&0xFF)<<16)|((data[14]&0xFF)<<8)|(data[15]&0xFF);
//        int maxBitrate = ((data[16]&0xFF)<<24)|((data[17]&0xFF)<<16)|((data[18]&0xFF)<<8)|(data[19]&0xFF);

        int defaulQP = -1;
        int minQP = -1;
        int maxQP = -1;
        int rateControlMode = -1;
        if(data.length>20) {
            defaulQP = data[20];
            minQP = data[21];
            maxQP = data[22];
            rateControlMode = data[23];
        }
        return mCallback.reconfigureEncode(width,height,bitrate,fps,idrPeriod,profile,orientation,codec,defaulQP,minQP,maxQP,rateControlMode);
    }

    private void response(final byte type, final int seqnum, final byte result) {
        byte[] response = new byte[]{
                0x00,0x00,0x00,0x0A,
                VERSION,RESPONSE,0x00,0x00,
                (byte) ((seqnum>>24)&0xFF),
                (byte) ((seqnum>>16)&0xFF),
                (byte) ((seqnum>>8)&0xFF),
                (byte) (seqnum&0xFF),
                type,result
        };

        if(dataOutputStream!=null) {
            try {
                dataOutputStream.write(response);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void reportFps(byte fps) {
        byte[] response = new byte[]{
                0x00,0x00,0x00,0x09,
                VERSION,reportFps,0x00,0x00,
                (byte) ((nSeqnum>>24)&0xFF),
                (byte) ((nSeqnum>>16)&0xFF),
                (byte) ((nSeqnum>>8)&0xFF),
                (byte) (nSeqnum++&0xFF),
                fps
        };

        if(dataOutputStream!=null) {
            try {
                dataOutputStream.write(response);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void reportScene(int report) {
        byte scene = (byte) report;
        byte index = (byte) (report>>8);
        byte[] response = new byte[]{
                0x00,0x00,0x00,0x0A,
                VERSION,reportScene,0x00,0x00,
                (byte) ((nSeqnum>>24)&0xFF),
                (byte) ((nSeqnum>>16)&0xFF),
                (byte) ((nSeqnum>>8)&0xFF),
                (byte) (nSeqnum++&0xFF),
                scene, index
        };

        if(dataOutputStream!=null) {
            try {
                dataOutputStream.write(response);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onAccept(Socket client) {
        try {
            DataInputStream dataInputStream = new DataInputStream(client.getInputStream());
            dataOutputStream = new DataOutputStream(client.getOutputStream());

            byte[] header = new byte[12];
            while (dataOutputStream!=null){
                dataInputStream.readFully(header);

                int dataLen = (((header[0]&0xFF)<<24)|((header[1]&0xFF)<<16)|((header[2]&0xFF)<<8)|(header[3]&0xFF))-8;
                byte version = header[4];
                byte type = header[5];
//              int extsize = ((header[6]&0xFF)<<8)|(header[7]&0xFF);
                int seqnum = ((header[8]&0xFF)<<24)|((header[9]&0xFF)<<16)|((header[10]&0xFF)<<8)|(header[11]&0xFF);

                if (version == VERSION) {
                    try {
                        byte[] data = new byte[dataLen];
                        dataInputStream.readFully(data, 0, dataLen);

                        switch (type) {
                            case startStreaming: {
                                boolean result = onStartStreaming(new String(data));
                                response(type, seqnum, result ? SUCCEEDED : FAILED);
                            }
                            break;

                            case stopStreaming: {
                                boolean result = onStopStreaming(new String(data));
                                response(type, seqnum, result ? SUCCEEDED : FAILED);
                            }
                            break;

                            case requestIdrFrame:
                                mCallback.requestIdrFrame();
                                break;

                            case reconfigureEncode:
                                onReconfigureEncode(data);
                                break;

                            default:
                                response(type, seqnum, UNSUPPORT);
                        }
                    } catch (Exception e) {
                        StringWriter stringWriter = new StringWriter();
                        e.printStackTrace(new PrintWriter(stringWriter, true));
                        Log.e("llx", stringWriter.toString());
                    }
                } else {
                    response(type, seqnum, ERROR);
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
            mCallback.stopStreaming(true,true,true);
        }
    }
}
