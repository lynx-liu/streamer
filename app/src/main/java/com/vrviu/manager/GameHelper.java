package com.vrviu.manager;

import android.graphics.Point;
import android.util.Log;

import com.vrviu.streamer.SceneDetect;
import com.vrviu.utils.JsonUtils;
import com.vrviu.utils.SystemUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

public class GameHelper extends Thread{
    private int index = 0;
    private int count = 0;
    private Point screenSize = null;
    private JSONArray gameinfo = null;
    private JSONArray eventArray = null;
    private CaptureHelper captureHelper = null;
    private SceneDetect sceneDetect = null;
    private String packageName = null;
    private onSceneChangeListener mListener = null;

    public interface onSceneChangeListener {
        void onSceneChanged(int report);
    }

    public GameHelper(onSceneChangeListener listener) {
        setName(getClass().getSimpleName());
        mListener = listener;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setCaptureHelper(CaptureHelper captureHelper) {
        this.captureHelper = captureHelper;
        screenSize = captureHelper.getScreenSize();
        eventArray = getGameinfo(screenSize.x,screenSize.y);
        sceneDetect = null;
    }

    public boolean loadConfig(String jsonFile, String packageName) {
        this.packageName = packageName;
        try {
            String jsonString = SystemUtils.read(jsonFile);
            if(jsonString==null) return false;

            JSONObject jsonObject = new JSONObject(jsonString);
            JSONArray jsonArray = new JSONArray(jsonObject.getString("game"));
            for(int i=0;i<jsonArray.length();i++) {
                JSONObject gameObject = jsonArray.getJSONObject(i);
                if(gameObject.getString("package").contains(packageName)) {
                    gameinfo = new JSONArray(gameObject.getString("gameinfo"));
                    break;
                }
            }

            if(gameinfo!=null && gameinfo.length()>0) {
                return true;
            }
        } catch (Exception e) {
            Log.d("llx",e.toString());
        }
        return false;
    }

    private JSONArray getGameinfo(int width, int height) {
        if(gameinfo==null)
            return null;

        try {
            for (int i = 0; i < gameinfo.length(); i++) {
                JSONObject jsonObject = gameinfo.getJSONObject(i);
                JSONObject screeninfo = new JSONObject(jsonObject.getString("screenSize"));
                if(screeninfo.getInt("w")==width && screeninfo.getInt("h")==height) {
                    return new JSONArray(jsonObject.getString("event"));
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    private JSONObject getEvent(int index, JSONArray jsonArray) {
        try {
            for(int i=0;i<jsonArray.length();i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                if(jsonObject.getInt("index")==index) {
                    return jsonObject;
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void run() {
        super.run();
        while (!isInterrupted()) {
            try {
                sleep(1000);
            } catch (InterruptedException e) {
                break;
            }

            if(eventArray==null)
                continue;

            JSONObject eventObject = getEvent(index, eventArray);
            if(eventObject==null) break;

            if(sceneDetect==null) {
                String targetFile = JsonUtils.get(eventObject,"PIC", null);
                if(targetFile!=null) {
                    float threshold = (float) JsonUtils.get(eventObject, "threshold", 0.8);

                    int roiX = 0, roiY = 0, roiW = 0, roiH = 0;
                    try {
                        JSONObject rectROI = new JSONObject(eventObject.getString("ROI"));
                        roiX = rectROI.getInt("x");
                        roiY = rectROI.getInt("y");
                        roiW = rectROI.getInt("w");
                        roiH = rectROI.getInt("h");
                        Log.d("llx", "ROI {x:"+roiX+", y:"+roiY+", w:"+roiW+", h:"+roiH+"}");
                    } catch (JSONException e) {

                    }

                    sceneDetect = new SceneDetect();
                    sceneDetect.init(targetFile, threshold, roiX, roiY, roiW, roiH);
                    Log.d("llx",targetFile+", "+threshold);
                }
            }

            String cmd = JsonUtils.get(eventObject, "sh", null);
            int wait = JsonUtils.get(eventObject, "wait", 2000);
            int tryCount = JsonUtils.get(eventObject, "retry", -1);

            if(sceneDetect != null) {
                if(captureHelper==null) continue;
                int[] buffer = captureHelper.screenCap(null);
                if(buffer==null) continue;

                if(sceneDetect.detect(buffer,screenSize.x,screenSize.y)) {
                    Log.d("llx","detected: scene"+index);
                    if(cmd!=null) {
                        try {
                            shell(wait, cmd);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                    sceneDetect = null;
                    tryCount = count;
                }
            } else {
                if(tryCount<0) {
                    tryCount = 0;
                }

                if(cmd!=null) {
                    try {
                        shell(wait, cmd);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }

            count++;
            if(tryCount>=0 && count>tryCount) {
                sceneDetect = null;
                count = 0;
                index++;
            } else {
                continue;
            }

            if(sceneDetect == null) {
                try {
                    int report = eventObject.getInt("report");
                    Log.d("llx", "report:"+report);
                    if(mListener!=null) {
                        mListener.onSceneChanged(report);
                    }
                } catch (JSONException e) {

                }
            }
        }
    }

    private boolean shell(int wait, String cmd) throws InterruptedException {
        if(wait>0) {
            Log.d("llx","wait:"+wait+"ms");
            sleep(wait);
        }

        Log.d("llx",cmd);
        try {
            Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public void interrupt() {
        super.interrupt();
        mListener = null;
    }
}
