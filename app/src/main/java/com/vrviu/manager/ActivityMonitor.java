package com.vrviu.manager;

import android.app.IActivityController;
import android.app.IProcessObserver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;

import com.vrviu.utils.SystemUtils;

import java.util.ArrayList;
import java.util.List;

public class ActivityMonitor {
    private static final long delayMillis = 50;
    private static Handler mHandler = null;
    private Context mContext = null;
    public static final String ACTION_REQUEST_PERMISSIONS = "android.content.pm.action.REQUEST_PERMISSIONS";
    private static final List<ActivityChangeListener> activityChangeListeners=new ArrayList<>();
    private static final List<ActionChangeListener> actionChangeListeners=new ArrayList<>();

    public void addActivityChangeListener(ActivityChangeListener activityChangeListener) {
        activityChangeListeners.add(activityChangeListener);
    }

    public void removeActivityChangeListener(ActivityChangeListener activityChangeListener) {
        activityChangeListeners.remove(activityChangeListener);
    }

    public void addActionChangeListener(ActionChangeListener actionChangeListener) {
        actionChangeListeners.add(actionChangeListener);
    }

    public void removeActionChangeListener(ActionChangeListener actionChangeListener) {
        actionChangeListeners.remove(actionChangeListener);
    }

    public interface ActivityChangeListener {
        void onActivityChanged(ComponentName componentName);
    }

    public interface ActionChangeListener {
        boolean onActionChanged(String action, String pkg);
    }

    public ActivityMonitor(Context context, Handler handler) {
        super();
        mContext = context;
        mHandler = handler;

        SystemUtils.registerProcessObserver(iProcessObserver);
        SystemUtils.setActivityController(iActivityController,true);
    }

    private final Runnable runnable = new Runnable() {
        @Override
        public void run() {
            ComponentName componentName = SystemUtils.getTopActivity(mContext);
            if(componentName==null) {
                mHandler.postDelayed(runnable,delayMillis);
            } else {
                Log.d("llx", "Runnable:" +componentName.toShortString());
                for (ActivityChangeListener activityChangeListener:activityChangeListeners) {
                    activityChangeListener.onActivityChanged(componentName);
                }
            }
        }
    };

    IActivityController iActivityController = new IActivityController.Stub() {
        @Override
        public boolean activityStarting(Intent intent, String pkg) {
            mHandler.removeCallbacks(runnable);
            mHandler.postDelayed(runnable, delayMillis);

            boolean diableStart = false;
            String action = intent.getAction();
            for (ActionChangeListener actionChangeListener:actionChangeListeners) {
                if(!actionChangeListener.onActionChanged(action, pkg))
                    diableStart = true;
            }

            if(diableStart)
                return false;
            return true;
        }

        @Override
        public boolean activityResuming(String pkg) {
            mHandler.removeCallbacks(runnable);
            mHandler.postDelayed(runnable, delayMillis);
            return true;
        }

        @Override
        public boolean appCrashed(String processName, int pid, String shortMsg, String longMsg, long timeMillis, String stackTrace) {
            return true;
        }

        @Override
        public int appEarlyNotResponding(String processName, int pid, String annotation) {
            return 0;
        }

        @Override
        public int appNotResponding(String processName, int pid, String processStats) {
            return 0;
        }

        @Override
        public int systemNotResponding(String msg) {
            return 0;
        }
    };

    IProcessObserver iProcessObserver = new IProcessObserver.Stub() {
        @Override
        public void onForegroundActivitiesChanged(int pid, int uid, boolean foregroundActivities) {
            mHandler.removeCallbacks(runnable);
            mHandler.postDelayed(runnable, delayMillis);
        }

        @Override
        public void onProcessStateChanged(int pid, int uid, int procState) {
        }

        @Override
        public void onForegroundServicesChanged(int pid, int uid, int serviceTypes) throws RemoteException {
        }

        @Override
        public void onProcessDied(int pid, int uid) {
            mHandler.removeCallbacks(runnable);
            mHandler.postDelayed(runnable, delayMillis);
        }
    };

    public void Release() {
        actionChangeListeners.clear();
        activityChangeListeners.clear();
        mHandler.removeCallbacks(runnable);
        SystemUtils.unregisterProcessObserver(iProcessObserver);
        SystemUtils.setActivityController(null,false);
    }
}
