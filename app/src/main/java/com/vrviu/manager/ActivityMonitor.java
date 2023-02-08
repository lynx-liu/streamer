package com.vrviu.manager;

import android.app.IActivityController;
import android.app.IProcessObserver;
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
        void onActivityChanged(String topActivity);
    }

    public interface ActionChangeListener {
        void onActionChanged(String action, String pkg);
    }

    public ActivityMonitor(Handler handler) {
        super();
        mHandler = handler;

        SystemUtils.registerProcessObserver(iProcessObserver);
        SystemUtils.setActivityController(iActivityController,true);
    }

    private static final Runnable runnable = new Runnable() {
        @Override
        public void run() {
            String topActivity = SystemUtils.getTopActivity();
            if(topActivity==null) {
                mHandler.postDelayed(runnable,delayMillis);
            } else {
                Log.d("llx", "Runnable:" +topActivity);
                for (ActivityChangeListener activityChangeListener:activityChangeListeners) {
                    activityChangeListener.onActivityChanged(topActivity);
                }
            }
        }
    };

    IActivityController iActivityController = new IActivityController.Stub() {
        @Override
        public boolean activityStarting(Intent intent, String pkg) {
            mHandler.removeCallbacks(runnable);
            mHandler.postDelayed(runnable, delayMillis);

            String action = intent.getAction();
            for (ActionChangeListener actionChangeListener:actionChangeListeners) {
                actionChangeListener.onActionChanged(action, pkg);
            }
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
