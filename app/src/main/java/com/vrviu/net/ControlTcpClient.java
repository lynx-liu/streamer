package com.vrviu.net;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Point;
import android.graphics.PointF;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.media.AudioRecordingConfiguration;
import android.net.Uri;
import android.os.Build;
import android.os.FileObserver;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static android.os.FileObserver.CLOSE_WRITE;
import static android.view.KeyEvent.KEYCODE_ALT_LEFT;
import static android.view.KeyEvent.KEYCODE_ALT_RIGHT;
import static android.view.KeyEvent.KEYCODE_BUTTON_A;
import static android.view.KeyEvent.KEYCODE_BUTTON_B;
import static android.view.KeyEvent.KEYCODE_BUTTON_L1;
import static android.view.KeyEvent.KEYCODE_BUTTON_L2;
import static android.view.KeyEvent.KEYCODE_BUTTON_R1;
import static android.view.KeyEvent.KEYCODE_BUTTON_R2;
import static android.view.KeyEvent.KEYCODE_BUTTON_SELECT;
import static android.view.KeyEvent.KEYCODE_BUTTON_START;
import static android.view.KeyEvent.KEYCODE_BUTTON_THUMBL;
import static android.view.KeyEvent.KEYCODE_BUTTON_THUMBR;
import static android.view.KeyEvent.KEYCODE_BUTTON_X;
import static android.view.KeyEvent.KEYCODE_BUTTON_Y;
import static android.view.KeyEvent.KEYCODE_CTRL_LEFT;
import static android.view.KeyEvent.KEYCODE_CTRL_RIGHT;
import static android.view.KeyEvent.KEYCODE_SHIFT_LEFT;
import static android.view.KeyEvent.KEYCODE_SHIFT_RIGHT;
import static android.view.KeyEvent.KEYCODE_UNKNOWN;
import static android.view.KeyEvent.KEYCODE_V;
import static android.view.KeyEvent.KEYCODE_ESCAPE;
import static android.view.KeyEvent.KEYCODE_BACK;
import static android.view.KeyEvent.META_ALT_ON;
import static android.view.KeyEvent.META_CAPS_LOCK_ON;
import static android.view.KeyEvent.META_CTRL_ON;
import static android.view.KeyEvent.META_NUM_LOCK_ON;
import static android.view.KeyEvent.META_SHIFT_ON;

import com.vrviu.manager.ActivityMonitor;
import com.vrviu.manager.InputModeManager;
import com.vrviu.utils.ControlUtils;
import com.vrviu.utils.SystemUtils;

public final class ControlTcpClient extends TcpClient{
    private static final int InputData = 0x0206;
    private static final int AdjustEncoderSetting = 0x0401;
    private static final int PingData = 0x0601;
    private static final int NotifyType = 0x0801;
    private static final int BUTTON_LEFT =0x01;
    private static final int BUTTON_MID =0x02;
    private static final int BUTTON_RIGHT =0x03;
    private static final int PACKET_TYPE_MOUSE_BUTTON =0x05;
    private static final int TOUCH_RELATIVE =0x06;
    private static final int PACKET_TYPE_MOUSE_MOVE =0x08;
    private static final int TOUCH_ABSOLUTE =0x09;
    private static final int PACKET_TYPE_KEYBOARD =0x0A;
    private static final int PACKET_TYPE_MULTI_CONTROLLER = 0x1E;
    private static final int PACKET_TYPE_MULTITOUCH = 0x21;
    private static final int PACKET_TYPE_TOUCH_EX=0x22;
    private static final int PACKET_TYPE_TOUCH=0x23;
    private static final int PACKET_TYPE_ANDROID_KEY=0x24;
    private static final int PACKET_TYPE_SENSOR_INFO=0x25;
    private static final int PACKET_TYPE_ROTATION = 0x26;
    private static final int PACKET_TYPE_INPUT_STRING=0x28;
    private static final int PACKET_TYPE_CONTROL_CLOUD_IME=0x29;
    private static final int PACKET_TYPE_SCENE_MODE = 0x2A;
    private static final int PACKET_TYPE_SWITCH_IME_TYPE=0x30;
    private static final int PACKET_TYPE_SCENE_EXT = 0x31;
    private static final int PACKET_TYPE_CLIPBOARD_DATA=0x32;
    private static final int PACKET_TYPE_SENSOR_ASK=0x33;
    private static final int PACKET_TYPE_MIC_CAMERA=0x34;
    private static final int PACKET_TYPE_OPEN_URL=0x35;
    private static final int PACKET_TYPE_OPEN_DOCUMENT=0x36;
    private static final int PACKET_TYPE_SET_CLIPBOARD=0x36;
    private static final int PACKET_TYPE_CLOUD_IME_MODE=0x3A;
    private static final int PACKET_TYPE_ADJUST_VOLUME=0x46;

    private static final byte URL_MODE = 0x01;
    private static final byte FILE_MODE = 0x02;
    private static final byte SHARE_MODE = 0x03;
    private static final byte SHARE_MODE_EX = 0x04;
    private static final byte FILE_MODE_EX = 0x06;

    private static final int ACTION_DOWN = 0x08;
    private static final int ACTION_UP = 0x09;
    private static final int ACTION_MOVE = 0x0A;
    private static final int ACTION_CANCEL = 0x0B;

    private static final int KEYCODE_ENTER = 888;
    private static final int KEYCODE_SCREENSHOT = 999;

    private static final int KEYCODE_GAMEPAD_DPAD_UP        = 0x0001;
    private static final int KEYCODE_GAMEPAD_DPAD_DOWN      = 0x0002;
    private static final int KEYCODE_GAMEPAD_DPAD_LEFT      = 0x0004;
    private static final int KEYCODE_GAMEPAD_DPAD_RIGHT     = 0x0008;
    private static final int KEYCODE_GAMEPAD_LTRIGGER       = 0x0400;
    private static final int KEYCODE_GAMEPAD_RTRIGGER       = 0x0800;
    private static int gamePadButtonStatus                  = 0x0000;
    private static final int[] gamePadKeyCode = new int[]{KEYCODE_UNKNOWN,KEYCODE_UNKNOWN,KEYCODE_UNKNOWN,KEYCODE_UNKNOWN,
            KEYCODE_BUTTON_START,KEYCODE_BUTTON_SELECT,KEYCODE_BUTTON_THUMBL,KEYCODE_BUTTON_THUMBR,
            KEYCODE_BUTTON_L1,KEYCODE_BUTTON_R1,KEYCODE_BUTTON_L2,KEYCODE_BUTTON_R2,
            KEYCODE_BUTTON_A,KEYCODE_BUTTON_B,KEYCODE_BUTTON_X,KEYCODE_BUTTON_Y};

    private static final int KEY_DOWN = 0x03;
    private static final int KEY_UP = 0x04;
    private static final int MOUSE_WHEEL = 0x0A;

    private static final int APPEND_TEXT = 0x00;
    private static final int REPLASE_TEXT = 0x01;

    private static final int LOCAL_IME = 0x00;
    private static final int CLOUD_IME = 0x01;

    private static final byte NumState=0x08;
    private static final byte CapsState=0x10;
    private static final int TouchPacketSize=20;

    private static final int SENSOR_TYPE_ACCELEROMETER = 1;
    private static final int SENSOR_TYPE_MAGNETIC_FIELD = 2;
    private static final int SENSOR_TYPE_GYROSCOPE = 3;
    private static final int SENSOR_TYPE_GPS = 4;
    private static final int SENSOR_TYPE_BDS = 5;

    private DataInputStream dataInputStream;
    private DataOutputStream dataOutputStream;

    private final PointF lastPoint = new PointF();
    private boolean isRightButtonPress=false;
    private boolean isLeftButtonPress=false;
    private boolean isHoverEnter=false;
    private final boolean isGameMode;
    private final AtomicLong controlTs;

    private long lastTouchDownTime;
    private long lastMouseDownTime;

    private boolean altLeft=false;
    private boolean altRight=false;
    private boolean shiftLeft=false;
    private boolean shiftRight=false;
    private boolean ctrlLeft=false;
    private boolean ctrlRight=false;

    private static boolean isMicOn = false;
    private static boolean isCameraOn =false;

    private static final Point screenSize = new Point();
    private static Handler handler = null;
    private Context mContext = null;
    private final ClipboardManager clipboardManager;
    private final AudioManager audioManager;
    private final CameraManager cameraManager;
    private FileObserver fileObserver;
    private final ControlUtils controlUtils;
    private boolean useLocalBrowser;
    private ActivityMonitor activityMonitor;
    private final InputModeManager inputModeManager;
    private InputMethodReceiver inputMethodReceiver;
    private AccessibilityNodeInfo accessibilityNodeInfo = null;

    public ControlTcpClient(final Context context, final String ip, final int port, final boolean isGameMode, final String downloadDir, final String packageName, final boolean useLocalBrowser, ActivityMonitor activityMonitor, AtomicLong controlTs) {
        super(ip,port);
        setName(getClass().getSimpleName());

        this.isGameMode = isGameMode;
        this.controlTs=controlTs;

        mContext = context;
        handler = new Handler(context.getMainLooper());
        controlUtils = new ControlUtils(context);

        clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        isMicOn = audioManager.getMode() == AudioManager.MODE_IN_COMMUNICATION;

        MonitorFiles(context,downloadDir,packageName);

        this.useLocalBrowser = useLocalBrowser;
        this.activityMonitor = activityMonitor;
        this.activityMonitor.addActionChangeListener(actionChangeListener);
        inputModeManager = new InputModeManager(context,activityMonitor) {
            @Override
            public void onInputModeChange(int mode) {
                new Thread(() -> sendInputModeChanged(mode)).start();
            }
        };

        inputMethodReceiver = new InputMethodReceiver();
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_INPUT_METHOD_CHANGED);
        mContext.registerReceiver(inputMethodReceiver, intentFilter);
    }

    ActivityMonitor.ActionChangeListener actionChangeListener = (intent, pkg) -> {
        String action = intent.getAction();
        if(action!=null) {
            Log.d("llx", "action:"+action+", pkg:"+pkg);

            switch (action) {
                case Intent.ACTION_GET_CONTENT:
                case Intent.ACTION_PICK:
                case Intent.ACTION_OPEN_DOCUMENT://原神
                    new Thread(() -> sendStartDocuments()).start();
                    break;

                case Intent.ACTION_CHOOSER:
                    break;

                case Intent.ACTION_VIEW:
                    if(useLocalBrowser) {
                        String url = intent.getDataString();
                        Log.d("llx","url:"+url);
                        if(url!=null) new Thread(() -> sendFilePath(url,URL_MODE)).start();
                        return false;//disable browser
                    }
                    return true;

                case ActivityMonitor.ACTION_REQUEST_PERMISSIONS:
                    new Thread(() -> sendSensorAsk(new byte[]{SENSOR_TYPE_GPS})).start();
                    break;
            }
        }
        return true;
    };

    private boolean MonitorFiles(final Context context, final String downloadDir, final String packageName) {
        SystemUtils.clearImage(context,downloadDir);
        if(downloadDir==null || downloadDir.isEmpty())
            return false;

        File file = new File(downloadDir);
        if (!file.exists()) {
            Log.d("llx", "mkdir:" + downloadDir);
            file.mkdirs();
        }

        fileObserver = new FileObserver(file.getPath(), CLOSE_WRITE) {
            @Override
            public void onEvent(int event, String filename) {
                if(filename!=null) {
                    if (SystemUtils.isTopPackage(context, packageName)) {
                        new Thread(() -> sendFilePath(filename, FILE_MODE)).start();
                    } else {
                        File imageFile = new File(file, filename);
                        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                        intent.setData(Uri.fromFile(imageFile));
                        context.sendBroadcast(intent);
                        Log.d("llx", "refresh picture:" + filename);
                    }
                }
            }
        };

        fileObserver.startWatching();
        return true;
    }

    CameraManager.AvailabilityCallback availabilityCallback = new CameraManager.AvailabilityCallback() {
        @Override
        public void onCameraAvailable(String cameraId) {
            super.onCameraAvailable(cameraId);
            isCameraOn = false;

            Log.d("llx","camera is "+isCameraOn);
            new Thread(() -> sendMicCameraState(isMicOn?1:0, isCameraOn?1:0)).start();
        }

        @Override
        public void onCameraUnavailable(String cameraId) {
            super.onCameraUnavailable(cameraId);
            isCameraOn = true;

            Log.d("llx","camera is "+isCameraOn);
            new Thread(() -> sendMicCameraState(isMicOn?1:0, isCameraOn?1:0)).start();
        }
    };

    AudioManager.AudioRecordingCallback audioRecordingCallback = new AudioManager.AudioRecordingCallback() {
        @Override
        public void onRecordingConfigChanged(List<AudioRecordingConfiguration> configs) {
            super.onRecordingConfigChanged(configs);
            try {
                isMicOn = configs.get(0) != null;
            } catch (Exception e) {
                isMicOn = false;
            }

            Log.d("llx","microphone is "+isMicOn);
            new Thread(() -> sendMicCameraState(isMicOn?1:0, isCameraOn?1:0)).start();
        }
    };

    ClipboardManager.OnPrimaryClipChangedListener onPrimaryClipChangedListener = () -> {
        final String text = getClipboardText();
        if(text!=null){
            new Thread(() -> sendClipboard(text)).start();
        }
    };

    public void setDisplayRotation(Point realSize) {
        screenSize.set(realSize.x,realSize.y);
        new Thread(() -> sendRotationChanged((byte) (screenSize.x>=screenSize.y?0:1))).start();
    }

    @Override
    public void interrupt() {
        if(inputMethodReceiver != null) {
            mContext.unregisterReceiver(inputMethodReceiver);
            inputMethodReceiver = null;
        }

        if(activityMonitor != null) {
            activityMonitor.removeActionChangeListener(actionChangeListener);
            activityMonitor = null;
        }

        inputModeManager.Release();
        if(fileObserver!=null) {
            fileObserver.stopWatching();
            fileObserver = null;
        }
        super.interrupt();
    }

    private int getMetaState(int action, int keyCode, byte modifers){
        switch (keyCode){
            case KEYCODE_ALT_LEFT:
                altLeft= action == KeyEvent.ACTION_DOWN;
                break;
            case KEYCODE_ALT_RIGHT:
                altRight= action == KeyEvent.ACTION_DOWN;
                break;
            case KEYCODE_SHIFT_LEFT:
                shiftLeft= action == KeyEvent.ACTION_DOWN;
                break;
            case KEYCODE_SHIFT_RIGHT:
                shiftRight= action == KeyEvent.ACTION_DOWN;
                break;
            case KEYCODE_CTRL_LEFT:
                ctrlLeft= action == KeyEvent.ACTION_DOWN;
                break;
            case KEYCODE_CTRL_RIGHT:
                ctrlRight= action == KeyEvent.ACTION_DOWN;
                break;
        }

        int meta=0;
        if(altLeft)
            meta|=META_ALT_ON;
        if(altRight)
            meta|=META_ALT_ON;
        if(shiftLeft)
            meta|=META_SHIFT_ON;
        if(shiftRight)
            meta|=META_SHIFT_ON;
        if(ctrlLeft)
            meta|=META_CTRL_ON;
        if(ctrlRight)
            meta|=META_CTRL_ON;

        if((modifers&NumState)==NumState){
            meta|=META_NUM_LOCK_ON;
        }

        if((modifers&CapsState)==CapsState){
            meta|=META_CAPS_LOCK_ON;
        }
        return meta;
    }

    private boolean invalidKey(final int key){
        switch (key) {
            case KeyEvent.KEYCODE_POWER:
            case KeyEvent.KEYCODE_EXPLORER:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
            case KeyEvent.KEYCODE_CALL:
            case KeyEvent.KEYCODE_ENDCALL:
            case KeyEvent.KEYCODE_SEARCH:
            case KeyEvent.KEYCODE_CAMERA:
            case KeyEvent.KEYCODE_FOCUS:
            case KeyEvent.KEYCODE_NOTIFICATION:
            case KeyEvent.KEYCODE_MUTE:
            case KeyEvent.KEYCODE_ENVELOPE: {
                return true;
            }

            case KeyEvent.KEYCODE_HOME:
            case KeyEvent.KEYCODE_APP_SWITCH:
            case KeyEvent.KEYCODE_TAB: {
                return isGameMode;
            }

            default: {
                return false;
            }
        }
    }

    private boolean onKeyEvent(final byte[] buf, final int action) {
        int key = ((buf[6] & 0xFF) << 8) | (buf[5] & 0xFF);
        if(invalidKey(key))
            return true;

        if(key == KEYCODE_ENTER) {
            if (inputModeManager.isSimpleInputMethodEnable()) {
                key = KeyEvent.KEYCODE_ENTER;
            } else {
                return true;
            }
        } else if(key == KEYCODE_ESCAPE) {
            key = KEYCODE_BACK;
        }

        byte modifiers = buf[7];
        int metaState = getMetaState(action, key, modifiers);
        return controlUtils.injectKeyEvent(action, key, 0, metaState);
    }

    private boolean onKeyClick(final int key) {
        if(invalidKey(key))
            return true;

        switch (key) {
            case KEYCODE_ENTER: {
                if (inputModeManager.isSimpleInputMethodEnable()) {
                    return controlUtils.injectKeycode(KeyEvent.KEYCODE_ENTER);
                } else {
                    return true;
                }
            }

            case KEYCODE_ESCAPE:
                return controlUtils.injectKeycode(KEYCODE_BACK);

            case KEYCODE_SCREENSHOT:
                return controlUtils.injectTowKeycode(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_POWER);

            default:
                return controlUtils.injectKeycode(key);
        }
    }

    private void onGamePad(final byte[] buf) {/*
        int controllerNumber = ((buf[7]&0xFF)<<8)|(buf[6]&0xFF);
        int activeGamepadMask = ((buf[9]&0xFF)<<8)|(buf[8]&0xFF);
        int midB = ((buf[11]&0xFF)<<8)|(buf[10]&0xFF);*/
        int buttonFlags = ((buf[13]&0xFF)<<8)|(buf[12]&0xFF);
        int leftTrigger = buf[14]&0xFF;
        int rightTrigger = buf[15]&0xFF;
        short leftStickX = (short) (((buf[17]&0xFF)<<8)|(buf[16]&0xFF));
        short leftStickY = (short) (((buf[19]&0xFF)<<8)|(buf[18]&0xFF));
        short rightStickX = (short) (((buf[21]&0xFF)<<8)|(buf[20]&0xFF));
        short rightStickY = (short) (((buf[23]&0xFF)<<8)|(buf[22]&0xFF));
//        Ln.d("buttonFlags:"+String.format("0x%02X",buttonFlags)+", leftTrigger:"+String.format("0x%02X",leftTrigger)+", rightTrigger:"+String.format("0x%02X",rightTrigger)+", leftStickX:"+String.format("0x%02X",leftStickX)+", leftStickY:"+String.format("0x%02X",leftStickY)+", rightStickX:"+String.format("0x%02X",rightStickX)+", rightStickY:"+String.format("0x%02X",rightStickY));

        if(leftTrigger>0) buttonFlags|=KEYCODE_GAMEPAD_LTRIGGER;
        else buttonFlags&=(~KEYCODE_GAMEPAD_LTRIGGER);

        if(rightTrigger>0) buttonFlags|=KEYCODE_GAMEPAD_RTRIGGER;
        else buttonFlags&=(~KEYCODE_GAMEPAD_RTRIGGER);

//        Ln.d(String.format("shiftFlags:0x%02X",shiftFlags));

        int absHat0Y = 0;
        if((buttonFlags&KEYCODE_GAMEPAD_DPAD_UP) == KEYCODE_GAMEPAD_DPAD_UP)
            absHat0Y = -1;
        else if((buttonFlags&KEYCODE_GAMEPAD_DPAD_DOWN) == KEYCODE_GAMEPAD_DPAD_DOWN) {
            absHat0Y = 1;
        }

        int absHat0X = 0;
        if((buttonFlags&KEYCODE_GAMEPAD_DPAD_LEFT) == KEYCODE_GAMEPAD_DPAD_LEFT)
            absHat0X = -1;
        else if((buttonFlags&KEYCODE_GAMEPAD_DPAD_RIGHT) == KEYCODE_GAMEPAD_DPAD_RIGHT) {
            absHat0X = 1;
        }
        controlUtils.injectAxis(leftStickX, -leftStickY, rightStickX, -rightStickY, absHat0X, absHat0Y);
        buttonFlags&=0xFFF0;

        int shiftFlags = buttonFlags^gamePadButtonStatus;
        gamePadButtonStatus = buttonFlags;

        int index = 0;
        while (shiftFlags>0) {
            if((shiftFlags&0x01)>0) {
                int action = (buttonFlags&0x01)>0?KeyEvent.ACTION_DOWN:KeyEvent.ACTION_UP;
                int keycode = gamePadKeyCode[index];
                if(keycode!=KEYCODE_UNKNOWN) {
                    controlUtils.injectGamePad(action, gamePadKeyCode[index]);
                }
            }
            shiftFlags>>=1;
            buttonFlags>>=1;
            index++;
        }
    }

    private boolean onTouch(final byte[] buf, int packetLen) {
        if(controlTs!=null && packetLen>=TouchPacketSize){
            int index = packetLen-1;
            long timestamp = ((buf[index--]& 0xFFL)<<56)|((buf[index--]&0xFFL)<<48)
                    |((buf[index--]&0xFFL)<<40)|((buf[index--]&0xFFL)<<32)
                    |((buf[index--]&0xFFL)<<24)|((buf[index--]&0xFFL)<<16)
                    |((buf[index--]&0xFFL)<<8)|(buf[index--]&0xFFL);
            controlTs.set(timestamp);
        }

        int buttonIndex = buf[1];
        int action = buf[2];
        int absolute = buf[3];

        float dltX = ((buf[4]&0xFF)<<8)|(buf[5]&0xFF);
        float dltY = ((buf[6]&0xFF)<<8)|(buf[7]&0xFF);
        if(packetLen>TouchPacketSize) {
            dltX = Float.intBitsToFloat(((buf[4] & 0xFF) << 24) | ((buf[5] & 0xFF) << 16) | ((buf[6] & 0xFF) << 8) | (buf[7] & 0xFF));
            dltY = Float.intBitsToFloat(((buf[8] & 0xFF) << 24) | ((buf[9] & 0xFF) << 16) | ((buf[10] & 0xFF) << 8) | (buf[11] & 0xFF));
        }

        int touchType=buttonIndex&0xf0;
        if(touchType==0x90&&!inputModeManager.isActivityIndex()){
            return true;//键盘转换的消息,弹出输入框时丢弃
        }

        switch (action) {
            case ACTION_DOWN:
                action = MotionEvent.ACTION_DOWN;
                lastTouchDownTime= SystemClock.uptimeMillis();
                break;
            case ACTION_UP:
            case ACTION_CANCEL:
                action = MotionEvent.ACTION_UP;
                break;
            case ACTION_MOVE:
                action = MotionEvent.ACTION_MOVE;
                break;
            default:
                return true;//TOUCH状态错误,丢弃
        }

        PointF point=pointN2L(dltX,dltY);
        if(absolute== TOUCH_ABSOLUTE){
            lastPoint.set(point.x,point.y);
        }else if(absolute== TOUCH_RELATIVE){
            lastPoint.offset(point.x,point.y);
        }
        return controlUtils.injectTouch(action,buttonIndex&0x0f,new PointF(lastPoint.x,lastPoint.y),1.0f,0);
    }

    private void onMultiTouch(final byte[] buf, int packetLen) {
        if(controlTs!=null && packetLen>=TouchPacketSize){
            int index = packetLen-1;
            long timestamp = ((buf[index--]& 0xFFL)<<56)|((buf[index--]&0xFFL)<<48)
                    |((buf[index--]&0xFFL)<<40)|((buf[index--]&0xFFL)<<32)
                    |((buf[index--]&0xFFL)<<24)|((buf[index--]&0xFFL)<<16)
                    |((buf[index--]&0xFFL)<<8)|(buf[index--]&0xFFL);
            controlTs.set(timestamp);
        }

        int index = 0;
        boolean floatType = buf[index++]!=0;
        int multiTouchCnt = buf[index++]&0xFF;
        for(int i=0;i<multiTouchCnt;i++) {
            int buttonIndex = buf[index++];
            int action = buf[index++];
            int absolute = buf[index++];

            int bitsX = ((buf[index++] & 0xFF) << 24) | ((buf[index++] & 0xFF) << 16) | ((buf[index++] & 0xFF) << 8) | (buf[index++] & 0xFF);
            int bitsY = ((buf[index++] & 0xFF) << 24) | ((buf[index++] & 0xFF) << 16) | ((buf[index++] & 0xFF) << 8) | (buf[index++] & 0xFF);
            float dltX = floatType?Float.intBitsToFloat(bitsX)*screenSize.x:(bitsX*screenSize.x>>16);
            float dltY = floatType?Float.intBitsToFloat(bitsY)*screenSize.y:(bitsY*screenSize.y>>16);

            int touchType = buttonIndex & 0xf0;
            if (touchType == 0x90 &&!inputModeManager.isActivityIndex()) {
                continue;//键盘转换的消息,弹出输入框时丢弃
            }

            switch (action) {
                case ACTION_DOWN:
                    action = MotionEvent.ACTION_DOWN;
                    lastTouchDownTime= SystemClock.uptimeMillis();
                    break;
                case ACTION_UP:
                case ACTION_CANCEL:
                    action = MotionEvent.ACTION_UP;
                    break;
                case ACTION_MOVE:
                    action = MotionEvent.ACTION_MOVE;
                    break;
            }

            PointF point = new PointF(dltX, dltY);
            if (absolute == TOUCH_ABSOLUTE) {
                lastPoint.set(point.x, point.y);
            } else if (absolute == TOUCH_RELATIVE) {
                lastPoint.offset(point.x, point.y);
            }
            controlUtils.injectTouch(action, buttonIndex & 0x0f, new PointF(lastPoint.x,lastPoint.y), 1.0f, 0);
        }
    }

    private void onMouseDown(final int button) {
        switch (button) {
            case  BUTTON_LEFT: {
                isLeftButtonPress = true;
                lastTouchDownTime = SystemClock.uptimeMillis();
                if (isHoverEnter) {
                    controlUtils.injectMouse(MotionEvent.ACTION_HOVER_EXIT, lastMouseDownTime, lastTouchDownTime, lastPoint, MotionEvent.BUTTON_PRIMARY);
                    isHoverEnter = false;
                }
                controlUtils.injectTouchForMouse(MotionEvent.ACTION_DOWN, lastTouchDownTime, lastTouchDownTime, lastPoint);
            }
            break;

            case BUTTON_RIGHT: {
                isRightButtonPress = true;
                lastMouseDownTime = SystemClock.uptimeMillis();
                if (isHoverEnter) {
                    controlUtils.injectMouse(MotionEvent.ACTION_HOVER_EXIT, lastMouseDownTime, lastMouseDownTime, lastPoint, MotionEvent.BUTTON_SECONDARY);
                    isHoverEnter = false;
                }
                controlUtils.injectMouse(MotionEvent.ACTION_DOWN, lastMouseDownTime, lastMouseDownTime, lastPoint, MotionEvent.BUTTON_SECONDARY);
                controlUtils.injectMouse(MotionEvent.ACTION_BUTTON_PRESS, lastMouseDownTime, lastMouseDownTime, lastPoint, MotionEvent.BUTTON_SECONDARY);
            }
            break;

            case BUTTON_MID:
            default:
                break;
        }
    }

    private void onMouseUp(final int button) {
        switch (button) {
            case BUTTON_LEFT:
                isLeftButtonPress = false;
                controlUtils.injectTouchForMouse(MotionEvent.ACTION_UP,lastTouchDownTime,SystemClock.uptimeMillis(),lastPoint);
                break;

            case BUTTON_RIGHT:
                isRightButtonPress = false;
                long eventTime = SystemClock.uptimeMillis();
                controlUtils.injectMouse(MotionEvent.ACTION_BUTTON_RELEASE,lastMouseDownTime,eventTime,lastPoint,0);
                controlUtils.injectMouse(MotionEvent.ACTION_UP,lastMouseDownTime,eventTime,lastPoint,0);
                break;

            case BUTTON_MID:
            default:
                break;
        }
    }

    private boolean onMouseMove(final byte[] buf) {
        int magic= ((buf[3]&0xFF)<<24)|((buf[2]&0xFF)<<16)|((buf[1]&0xFF)<<8)|(buf[0]&0xFF);
        int dltX = ((buf[4]&0xFF)<<8)|(buf[5]&0xFF);
        int dltY = ((buf[6]&0xFF)<<8)|(buf[7]&0xFF);
        PointF point=pointN2L(dltX,dltY);

        if(magic== TOUCH_ABSOLUTE){
            lastPoint.set(point.x,point.y);
        }else if(magic== TOUCH_RELATIVE){
            lastPoint.offset(point.x,point.y);
        }

        long eventTime = SystemClock.uptimeMillis();
        if(isRightButtonPress){
            return controlUtils.injectMouse(MotionEvent.ACTION_MOVE,lastMouseDownTime,eventTime,lastPoint,MotionEvent.BUTTON_SECONDARY);
        }else if(isLeftButtonPress){
            return controlUtils.injectTouchForMouse(MotionEvent.ACTION_MOVE,lastTouchDownTime,eventTime,lastPoint);
        }else{
            if(!isHoverEnter){
                isHoverEnter=true;
                controlUtils.injectMouse(MotionEvent.ACTION_HOVER_ENTER,lastMouseDownTime,eventTime,lastPoint,0);
            }
            return controlUtils.injectMouse(MotionEvent.ACTION_HOVER_MOVE,lastMouseDownTime,eventTime,lastPoint,0);
        }
    }

    public String getClipboardText() {
        if (clipboardManager == null) {
            return null;
        }

        ClipData clipData = clipboardManager.getPrimaryClip();
        if (clipData == null || clipData.getItemCount() == 0) {
            return null;
        }

        CharSequence s = clipData.getItemAt(0).getText();
        if (s == null) {
            return null;
        }
        return s.toString();
    }

    public boolean setClipboardText(String text) {
        if (clipboardManager == null) {
            return false;
        }

        String currentClipboard = getClipboardText();
        if (currentClipboard != null && currentClipboard.equals(text)) {
            // The clipboard already contains the requested text.
            // Since pasting text from the computer involves setting the device clipboard, it could be set twice on a copy-paste. This would cause
            // the clipboard listeners to be notified twice, and that would flood the Android keyboard clipboard history. To workaround this
            // problem, do not explicitly set the clipboard text if it already contains the expected content.
            return false;
        }

        ClipData clipData = ClipData.newPlainText(null, text);
        clipboardManager.setPrimaryClip(clipData);
        return true;
    }

    private boolean onPaste(final String text) {
        if(!setClipboardText(text))
            return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            return controlUtils.injectKeycode(KeyEvent.KEYCODE_PASTE);

        return controlUtils.injectKeyEvent(KeyEvent.ACTION_DOWN, KEYCODE_V, 0, META_CTRL_ON)
                && controlUtils.injectKeyEvent(KeyEvent.ACTION_UP, KEYCODE_V, 0, META_CTRL_ON);
    }

    private void onSensorInfo(final byte[] buf) {
//      int samplePeriod = ((buf[0]&0xFF)<<8)|(buf[1]&0xFF);
        int sensorType = buf[2];
//      int len = buf[3]&0xFF;
        float data0 = Float.intBitsToFloat(((buf[4]&0xFF)<<24)|((buf[5]&0xFF)<<16)|((buf[6]&0xFF)<<8)|(buf[7]&0xFF));
        float data1 = Float.intBitsToFloat(((buf[8]&0xFF)<<24)|((buf[9]&0xFF)<<16)|((buf[10]&0xFF)<<8)|(buf[11]&0xFF));
//      float data2 = Float.intBitsToFloat(((buf[12]&0xFF)<<24)|((buf[13]&0xFF)<<16)|((buf[14]&0xFF)<<8)|(buf[15]&0xFF));

        switch (sensorType) {
            case SENSOR_TYPE_ACCELEROMETER:
                break;

            case SENSOR_TYPE_MAGNETIC_FIELD:
                break;

            case SENSOR_TYPE_GYROSCOPE:
                break;

            case SENSOR_TYPE_GPS:
            case SENSOR_TYPE_BDS:
                SystemUtils.setProperty("fake.gps.location",data0+","+data1);

                if(SystemUtils.isTopPackage(mContext,"com.android.permissioncontroller")) {
                    if (data0 != 0 || data1 != 0) {
                        controlUtils.injectTowKeycode(KeyEvent.KEYCODE_ENTER,KeyEvent.KEYCODE_ENTER);
                    } else {
                        controlUtils.injectKeycode(KEYCODE_BACK);
                    }
                }
                break;
        }
    }

    private void onInputString(final byte[] buf) {
        if(!inputModeManager.isStartInput())
            return;

        int mode=buf[0];
        int length=((buf[1]&0xFF)<<8)|(buf[2]&0xFF);
        String text = new String(buf,3,length, StandardCharsets.UTF_8);
        if(mode==REPLASE_TEXT && accessibilityNodeInfo!=null){
            if(length>0){
                Log.d("llx","input string:"+text);
                accessibilityNodeInfo.setText(text);
            }else{
                accessibilityNodeInfo.setText("");
                Log.e("llx","input string error length:"+length);
            }
        }else if(mode==APPEND_TEXT){
            onPaste(text);
        }
    }

    private void onControlCloudIME(final byte[] buf) {
        int command=buf[0];
        Log.d("llx","onControlCloudIME:"+command);
        switch (command) {
            case 0: {
                int length=((buf[1]&0xFF)<<8)|(buf[2]&0xFF);
                String text = new String(buf,3,length, StandardCharsets.UTF_8);
                Log.d("llx","text:"+text);

                Intent intent = new Intent();
                intent.setAction("com.vrviu.intent.INPUTSTRING");
                intent.putExtra("text", text);
                mContext.sendBroadcast(intent);
                break;
            }

            case 1: {
                Intent intent = new Intent();
                intent.setAction("com.vrviu.intent.FINISHINPUT");
                mContext.sendBroadcast(intent);
                break;
            }

            case 2: {
                Intent intent = new Intent();
                intent.setAction("com.vrviu.intent.HIDESOFT");
                mContext.sendBroadcast(intent);
                break;
            }
            case 3: {
                int keyboardHeight=((buf[1]&0xFF)<<8)|(buf[2]&0xFF);
                Log.d("llx","keyboardHeight:"+keyboardHeight);
                Intent intent = new Intent();
                intent.setAction("com.vrviu.intent.SET_KEYBOARD_HEIGHT");
                intent.putExtra("keyboardHeight", keyboardHeight);
                mContext.sendBroadcast(intent);
                break;
            }
            case 4: {
                int keyboardHeightRatio=((buf[1]&0xFF)<<8)|(buf[2]&0xFF);
                Log.d("llx","keyboardHeightRatio:"+keyboardHeightRatio);
                Intent intent = new Intent();
                intent.setAction("com.vrviu.intent.SET_KEYBOARD_HEIGHT_RATIO");
                intent.putExtra("keyboardHeightRatio", keyboardHeightRatio);
                mContext.sendBroadcast(intent);
                break;
            }
        }
    }

    private void onSwitchIMEType(final byte[] buf) {
        int command=buf[0];
        Log.d("llx","onSwitchIMEType:"+command);
        if(inputModeManager.isCloudIME())
            return;

        switch (command) {
            case LOCAL_IME:
                Settings.Secure.putString(mContext.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD, "com.simple.inputmethod/.SimpleInputMethodService");
                break;

            case CLOUD_IME:
                Intent intent = new Intent();
                intent.setAction("com.vrviu.intent.HIDESOFT");
                mContext.sendBroadcast(intent);

                Settings.Secure.putString(mContext.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD, "com.iflytek.inputmethod/.FlyIME");
                break;
        }
    }

    private void onKeyBoard(final byte[] buf) {
        int action = buf[0];
        switch (action) {
            case KEY_DOWN:
                onKeyEvent(buf,KeyEvent.ACTION_DOWN);
                break;

            case KEY_UP:
                onKeyEvent(buf,KeyEvent.ACTION_UP);
                break;

            case MOUSE_WHEEL:
                int scrollAmt1 = ((short)(((buf[5]&0xFF)<<8)|(buf[4]&0xFF))) / -6000;
                controlUtils.injectScroll(lastPoint, lastMouseDownTime, SystemClock.uptimeMillis(), scrollAmt1, scrollAmt1);
                break;
        }
    }

    private void onInputData(final byte[] buffer) {
        int packetLen= ((buffer[3]&0xFF)<<24)|((buffer[2]&0xFF)<<16)|((buffer[1]&0xFF)<<8)|(buffer[0]&0xFF);//小端
        int packetType=((buffer[4]&0xFF)<<24)|((buffer[5]&0xFF)<<16)|((buffer[6]&0xFF)<<8)|(buffer[7]&0xFF);
        packetLen= Math.min((buffer.length - 8), packetLen);
        byte[] object = new byte[packetLen];
        System.arraycopy(buffer, 8, object, 0, object.length);

        switch (packetType) {
            case PACKET_TYPE_MULTI_CONTROLLER:
                onGamePad(object);
                break;

            case PACKET_TYPE_MULTITOUCH:
                onMultiTouch(object,packetLen);
                break;

            case PACKET_TYPE_TOUCH_EX:
            case PACKET_TYPE_TOUCH:
                onTouch(object,packetLen);
                break;

            case PACKET_TYPE_ANDROID_KEY:
                int sendKey = ((object[0]&0xFF)<<8)|(object[1]&0xFF);
                onKeyClick(sendKey);
                break;

            case PACKET_TYPE_KEYBOARD:
                onKeyBoard(object);
                break;

            case PACKET_TYPE_CLIPBOARD_DATA:
                onPaste(new String(object,StandardCharsets.UTF_8));
                break;

            case PACKET_TYPE_SET_CLIPBOARD:
                setClipboardText(new String(object,StandardCharsets.UTF_8));
                break;

            case PACKET_TYPE_SENSOR_INFO:
                onSensorInfo(object);
                break;

            case PACKET_TYPE_INPUT_STRING:
                onInputString(object);
                break;

            case PACKET_TYPE_CONTROL_CLOUD_IME:
                onControlCloudIME(object);
                break;

            case PACKET_TYPE_SWITCH_IME_TYPE:
                onSwitchIMEType(object);
                break;

            case PACKET_TYPE_MOUSE_MOVE:
                onMouseMove(object);
                break;

            case PACKET_TYPE_MOUSE_BUTTON:
                int action = object[0];
                int button = ((object[1]&0xFF)<<24)|((object[2]&0xFF)<<16)|((object[3]<<8)&0xFF)|(object[4]&0xFF);
                if(action==ACTION_DOWN){
                    onMouseDown(button);
                }else if(action==ACTION_UP){
                    onMouseUp(button);
                }
                break;
        }
    }

    private void onAdjustEncoderSetting(final byte[] buffer) {
        int packetLen= ((buffer[3]&0xFF)<<24)|((buffer[2]&0xFF)<<16)|((buffer[1]&0xFF)<<8)|(buffer[0]&0xFF);//小端
        int packetType=((buffer[4]&0xFF)<<24)|((buffer[5]&0xFF)<<16)|((buffer[6]&0xFF)<<8)|(buffer[7]&0xFF);
        packetLen= Math.min((buffer.length - 8), packetLen);
        byte[] object = new byte[packetLen];
        System.arraycopy(buffer, 8, object, 0, object.length);

        if (packetType == PACKET_TYPE_ADJUST_VOLUME) {
            byte volume = object[0];
            int soundLevel = volume * 15 / 100;
            setMediaVolume(soundLevel);
        }
    }

    @Override
    public void onConnected(Socket client) {
        try {
            dataInputStream = new DataInputStream(client.getInputStream());
            dataOutputStream = new DataOutputStream(client.getOutputStream());

            clipboardManager.addPrimaryClipChangedListener(onPrimaryClipChangedListener);
            audioManager.registerAudioRecordingCallback(audioRecordingCallback, handler);
            cameraManager.registerAvailabilityCallback(availabilityCallback, handler);

            checkCloudInputType(mContext);
            sendRotationChanged((byte) (screenSize.x>=screenSize.y?0:1));
            sendInputModeChanged(inputModeManager.getInputMode());

            byte[] header = new byte[4];
            while (!isInterrupted()){
                dataInputStream.readFully(header);
                int type=((header[1]&0xFF)<<8)|(header[0]&0xFF);
                int dataLen=((header[3]&0xFF)<<8)|(header[2]&0xFF);

                byte[] buffer = new byte[dataLen];
                dataInputStream.readFully(buffer,0,dataLen);

                switch (type) {
                    case PingData:
                        dataOutputStream.write(SystemUtils.byteMerger(header,buffer));
                        break;

                    case InputData:
                        onInputData(buffer);
                        break;

                    case AdjustEncoderSetting:
                        onAdjustEncoderSetting(buffer);
                        break;
                }
            }
        } catch (Exception e) {
            Log.d("llx","loop Exception"+ e);
        } finally {
            cameraManager.unregisterAvailabilityCallback(availabilityCallback);
            audioManager.unregisterAudioRecordingCallback(audioRecordingCallback);
            clipboardManager.removePrimaryClipChangedListener(onPrimaryClipChangedListener);

            try {
                dataOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            dataOutputStream = null;

            try {
                dataInputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            dataInputStream = null;
        }
    }

    private void setMediaVolume(int soundLevel){
        String cmd="su root media volume --set "+soundLevel;
        try {
            java.lang.Process pro=Runtime.getRuntime().exec(cmd);
            pro.waitFor();
        } catch (Exception e) {
            Log.e("llx","runCmd Exception",e);
        }
    }

    private PointF pointN2L(float x, float y){
        return new PointF(x*screenSize.x/65535,y*screenSize.y/65535);
    }

    private void sendRotationChanged(byte rotation) {
        if(dataOutputStream==null)
            return;

        int payloadByteSize = 5;
        byte[] buf = new byte[4+payloadByteSize];
        buf[0] = NotifyType&0xFF;
        buf[1] = (NotifyType>>8)&0xFF;
        buf[2] = (byte) (payloadByteSize&0xFF);
        buf[3] = (byte) ((payloadByteSize>>8)&0xFF);
        buf[4] = (PACKET_TYPE_ROTATION>>24)&0xFF;
        buf[5] = (PACKET_TYPE_ROTATION>>16)&0xFF;
        buf[6] = (PACKET_TYPE_ROTATION>>8)&0xFF;
        buf[7] = PACKET_TYPE_ROTATION&0xFF;
        buf[8] = rotation;//0横屏，1竖屏
        try {
            dataOutputStream.write(buf);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.d("llx","sendRotationChanged:"+rotation+", threadID:"+Thread.currentThread().getId());
    }

    private void sendInputModeChanged(int mode) {
        if(dataOutputStream==null || mode<0)
            return;

        String inputTxt= "";
        if(mode==InputModeManager.START_INPUT && accessibilityNodeInfo!=null){
            inputTxt = getEditTextValue(accessibilityNodeInfo);
        }

        if(inputTxt==null) inputTxt = "";
        byte[] txtBytes = inputTxt.getBytes(StandardCharsets.UTF_8);
        int payloadByteSize = 5+txtBytes.length;
        byte[] buf = new byte[4+payloadByteSize];
        buf[0] = NotifyType&0xFF;
        buf[1] = (NotifyType>>8)&0xFF;
        buf[2] = (byte) (payloadByteSize&0xFF);
        buf[3] = (byte) ((payloadByteSize>>8)&0xFF);
        buf[4] = (PACKET_TYPE_SCENE_MODE>>24)&0xFF;
        buf[5] = (PACKET_TYPE_SCENE_MODE>>16)&0xFF;
        buf[6] = (PACKET_TYPE_SCENE_MODE>>8)&0xFF;
        buf[7] = PACKET_TYPE_SCENE_MODE&0xFF;
        buf[8] = (byte) mode;
        System.arraycopy(txtBytes, 0, buf, 9, txtBytes.length);
        try {
            dataOutputStream.write(buf);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.d("llx","sendInputModeChanged:"+mode+", text:"+inputTxt+", threadID:"+Thread.currentThread().getId());
    }

    private void sendClipboard(String txt) {
        if(dataOutputStream==null)
            return;

        byte[] txtBytes = txt.getBytes(StandardCharsets.UTF_8);
        int payloadByteSize = 4+txtBytes.length;
        byte[] buf = new byte[4+payloadByteSize];
        buf[0] = NotifyType&0xFF;
        buf[1] = (NotifyType>>8)&0xFF;
        buf[2] = (byte) (payloadByteSize&0xFF);
        buf[3] = (byte) ((payloadByteSize>>8)&0xFF);
        buf[4] = (PACKET_TYPE_CLIPBOARD_DATA>>24)&0xFF;
        buf[5] = (PACKET_TYPE_CLIPBOARD_DATA>>16)&0xFF;
        buf[6] = (PACKET_TYPE_CLIPBOARD_DATA>>8)&0xFF;
        buf[7] = PACKET_TYPE_CLIPBOARD_DATA&0xFF;
        System.arraycopy(txtBytes, 0, buf, 8, txtBytes.length);
        try {
            dataOutputStream.write(buf);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.d("llx","sendClipboard end threadID:"+Thread.currentThread().getId());
    }

    public void sendSceneMode(int report) {
        if(dataOutputStream==null)
            return;

        int payloadByteSize = 10;
        byte[] buf = new byte[4+payloadByteSize];
        buf[0] = NotifyType&0xFF;
        buf[1] = (NotifyType>>8)&0xFF;
        buf[2] = (byte) (payloadByteSize&0xFF);
        buf[3] = (byte) ((payloadByteSize>>8)&0xFF);
        buf[4] = (PACKET_TYPE_SCENE_EXT>>24)&0xFF;
        buf[5] = (PACKET_TYPE_SCENE_EXT>>16)&0xFF;
        buf[6] = (PACKET_TYPE_SCENE_EXT>>8)&0xFF;
        buf[7] = PACKET_TYPE_SCENE_EXT&0xFF;
        buf[8] = (byte) report;//SceneMode
        buf[9] = (byte) (report>>8);//Index
        buf[10] = 0;//reserved
        buf[11] = 0;
        buf[12] = 0;
        buf[13] = 0;

        try {
            dataOutputStream.write(buf);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.d("llx","sendSceneMode end threadID:"+Thread.currentThread().getId());
    }

    private void sendFilePath(String path, byte mode) {
        if(dataOutputStream==null)
            return;

        byte[] pathBytes = path.getBytes(StandardCharsets.UTF_8);
        int payloadByteSize = 5+pathBytes.length;
        byte[] buf = new byte[4+payloadByteSize];
        buf[0] = NotifyType&0xFF;
        buf[1] = (NotifyType>>8)&0xFF;
        buf[2] = (byte) (payloadByteSize&0xFF);
        buf[3] = (byte) ((payloadByteSize>>8)&0xFF);
        buf[4] = (PACKET_TYPE_OPEN_URL>>24)&0xFF;
        buf[5] = (PACKET_TYPE_OPEN_URL>>16)&0xFF;
        buf[6] = (PACKET_TYPE_OPEN_URL>>8)&0xFF;
        buf[7] = PACKET_TYPE_OPEN_URL&0xFF;
        buf[8] = mode;//1:URL, 2:file/path, 3:share
        System.arraycopy(pathBytes, 0, buf, 9, pathBytes.length);
        try {
            dataOutputStream.write(buf);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.d("llx","sendFilePath end threadID:"+Thread.currentThread().getId());
    }

    private void sendSensorAsk(byte[] sensorType) {
        if(dataOutputStream==null)
            return;

        int payloadByteSize = 5+sensorType.length;
        byte[] buf = new byte[4+payloadByteSize];
        buf[0] = NotifyType&0xFF;
        buf[1] = (NotifyType>>8)&0xFF;
        buf[2] = (byte) (payloadByteSize&0xFF);
        buf[3] = (byte) ((payloadByteSize>>8)&0xFF);
        buf[4] = (PACKET_TYPE_SENSOR_ASK>>24)&0xFF;
        buf[5] = (PACKET_TYPE_SENSOR_ASK>>16)&0xFF;
        buf[6] = (PACKET_TYPE_SENSOR_ASK>>8)&0xFF;
        buf[7] = PACKET_TYPE_SENSOR_ASK&0xFF;
        buf[8] = (byte) sensorType.length;

        System.arraycopy(sensorType,0,buf,9,sensorType.length);
        try {
            dataOutputStream.write(buf);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.d("llx","sendSensorAsk end threadID:"+Thread.currentThread().getId());
    }

    private void sendMicCameraState(int mic, int camera) {
        if(dataOutputStream==null)
            return;

        int payloadByteSize = 6;
        byte[] buf = new byte[4+payloadByteSize];
        buf[0] = NotifyType&0xFF;
        buf[1] = (NotifyType>>8)&0xFF;
        buf[2] = (byte) (payloadByteSize&0xFF);
        buf[3] = (byte) ((payloadByteSize>>8)&0xFF);
        buf[4] = (PACKET_TYPE_MIC_CAMERA>>24)&0xFF;
        buf[5] = (PACKET_TYPE_MIC_CAMERA>>16)&0xFF;
        buf[6] = (PACKET_TYPE_MIC_CAMERA>>8)&0xFF;
        buf[7] = PACKET_TYPE_MIC_CAMERA&0xFF;
        buf[8] = (byte) mic;//0（缺省，关闭）/1（开启）
        buf[9] = (byte) camera;//0（缺省，关闭）/1（开启）
        try {
            dataOutputStream.write(buf);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.d("llx","sendMicCameraState end threadID:"+Thread.currentThread().getId());
    }

    private void sendStartDocuments() {
        if(dataOutputStream==null)
            return;

        int payloadByteSize = 4;
        byte[] buf = new byte[4+payloadByteSize];
        buf[0] = NotifyType&0xFF;
        buf[1] = (NotifyType>>8)&0xFF;
        buf[2] = (byte) (payloadByteSize&0xFF);
        buf[3] = (byte) ((payloadByteSize>>8)&0xFF);
        buf[4] = (PACKET_TYPE_OPEN_DOCUMENT>>24)&0xFF;
        buf[5] = (PACKET_TYPE_OPEN_DOCUMENT>>16)&0xFF;
        buf[6] = (PACKET_TYPE_OPEN_DOCUMENT>>8)&0xFF;
        buf[7] = PACKET_TYPE_OPEN_DOCUMENT&0xFF;
        try {
            dataOutputStream.write(buf);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.d("llx","sendStartDocuments end threadID:"+Thread.currentThread().getId());
    }

    private void sendCloudInputType(int type) {
        if(dataOutputStream==null)
            return;

        int payloadByteSize = 5;
        byte[] buf = new byte[4+payloadByteSize];
        buf[0] = NotifyType&0xFF;
        buf[1] = (NotifyType>>8)&0xFF;
        buf[2] = (byte) (payloadByteSize&0xFF);
        buf[3] = (byte) ((payloadByteSize>>8)&0xFF);
        buf[4] = (PACKET_TYPE_CLOUD_IME_MODE>>24)&0xFF;
        buf[5] = (PACKET_TYPE_CLOUD_IME_MODE>>16)&0xFF;
        buf[6] = (PACKET_TYPE_CLOUD_IME_MODE>>8)&0xFF;
        buf[7] = PACKET_TYPE_CLOUD_IME_MODE&0xFF;
        buf[8] = (byte) type;
        try {
            dataOutputStream.write(buf);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.d("llx","sendCloudInputType: "+ type+" end, threadID:"+Thread.currentThread().getId());
    }

    public void onAccessibility(AccessibilityNodeInfo nodeInfo, int eventType) {
        switch (eventType) {
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                if(inputModeManager!=null)
                    inputModeManager.checkInputMode();
                break;

            case AccessibilityEvent.TYPE_VIEW_CLICKED:
            case AccessibilityEvent.TYPE_VIEW_FOCUSED:
                accessibilityNodeInfo = nodeInfo;
                if(inputModeManager!=null)
                    inputModeManager.checkInputMode();
                break;
        }
    }

    private String getEditTextValue(AccessibilityNodeInfo node){
        String text = "";
        if(node == null || node.isPassword()){
            return text;
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && node.isShowingHintText())
            return text;

        CharSequence equenceText = node.getText();
        if(equenceText != null) {
            text = String.valueOf(equenceText);
        }

        return text;
    }

    private void checkCloudInputType(Context context) {
        if(inputModeManager.isCloudIME())
            return;

        String defaultInputMethod = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        if(defaultInputMethod.equals("com.simple.inputmethod/.SimpleInputMethodService")) {
            new Thread(() -> sendCloudInputType(LOCAL_IME)).start();
        } else if(defaultInputMethod.equals("com.iflytek.inputmethod/.FlyIME")) {
            new Thread(() -> sendCloudInputType(CLOUD_IME)).start();
        }
    }

    private class InputMethodReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_INPUT_METHOD_CHANGED)) {
                checkCloudInputType(context);
            }
        }
    }
}