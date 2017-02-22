/*
 * Copyright (C) 2014 Thalmic Labs Inc.
 * Distributed under the Myo SDK license agreement. See LICENSE.txt for details.
 */

package com.ne0fhyklabs.freeflight.controllers;

import android.app.Instrumentation;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;

import com.ne0fhyklabs.freeflight.activities.ControlDroneActivity;
import com.thalmic.myo.AbstractDeviceListener;
import com.thalmic.myo.Arm;
import com.thalmic.myo.Hub;
import com.thalmic.myo.Myo;
import com.thalmic.myo.Pose;
import com.thalmic.myo.Quaternion;
import com.thalmic.myo.XDirection;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

//
// This sample demonstrates how to connect to a Myo and use it to trigger touch pad motion
// events that Google Glass recognizes as taps and swipes.
//
// Due to Android security restrictions, the motion events dispatched by this service will only be
// sent to activities in the same application.
//
public class MyoGlassService extends Service {
    private static final String TAG = "MyoGlassService";

    private static final String PREF_MAC_ADDRESS = "PREF_MAC_ADDRESS";
    private boolean isFisted = false;

    private Hub mHub;
    private SharedPreferences mPrefs;
    private boolean mActivityActive;
    private MyoListener mListener = new MyoListener();

    private ControlDroneActivity controlDroneActivity;

    public ControlDroneActivity getControlDroneActivity() {
        return controlDroneActivity;
    }

    public void setControlDroneActivity(ControlDroneActivity controlDroneActivity) {
        this.controlDroneActivity = controlDroneActivity;
    }

    // Return an interface to use to communicate with the service.
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private final IBinder mBinder = new MBinder();

    // The Binder class clients will use to communicate with this service. We know clients in this
    // sample will always run in the same process as the service, so we don't need to deal with IPC.
    public class MBinder extends Binder {
        public MyoGlassService getService() {
            return MyoGlassService.this;
        }
    }

    // Set the active state of the activity.
    public void setActivityActive(boolean active) {
        mActivityActive = active;
    }

    // Unpair with the currently paired Myo, if any, and pair with a new one.
    public void pairWithNewMyo() {
        // Unpair with the previously paired Myo, if it exists.
        mHub.unpair(mPrefs.getString(PREF_MAC_ADDRESS, ""));

        // Clear the saved Myo mac address.
        mPrefs.edit().putString(PREF_MAC_ADDRESS, "").apply();

        // Begin looking for an adjacent Myo to pair with.
        mHub.pairWithAdjacentMyo();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // First, we initialize the Hub singleton with an application identifier.
        mHub = Hub.getInstance();
        if (!mHub.init(this, getPackageName())) {
            Log.e(TAG, "Could not initialize the Hub.");
            stopSelf();
            return;
        }

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Register for DeviceListener callbacks.
        mHub.addListener(mListener);

        // If there is no connected Myo, try to pair with one.
        if (mHub.getConnectedDevices().isEmpty()) {
            String myoAddress = mPrefs.getString(PREF_MAC_ADDRESS, "");

            // If we have a saved Myo MAC address then connect to it, otherwise look for one nearby.
            if (TextUtils.isEmpty(myoAddress)) {
                mHub.pairWithAdjacentMyo();
            } else {
                mHub.pairByMacAddress(myoAddress);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Release any resources held by the Hub and MyoListener.
        mHub.shutdown();
        mListener.shutdown();
    }

    // Classes that inherit from AbstractDeviceListener can be used to receive events from Myo devices.
    // If you do not override an event, the default behavior is to do nothing.
    private class MyoListener extends AbstractDeviceListener {
        private static final long LAUNCH_HOLD_DURATION = 1000;

        private Handler mHandler = new Handler();
        private Instrumentation mInstrumentation = new Instrumentation();
        private ExecutorService mExecutor = Executors.newSingleThreadExecutor();

        // The arm that Myo is on is unknown until the arm recognized event is received.
        private Arm mArm = Arm.UNKNOWN;

        private Runnable mLaunchRunnable = new Runnable() {
            @Override
            public void run() {
                // Start the immersion activity. FLAG_ACTIVITY_NEW_TASK is needed to start an
                // Activity from a Service.
                Intent intent = new Intent(MyoGlassService.this, ControlDroneActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        };

        public void shutdown() {
            mExecutor.shutdown();
        }

        // onPair() is called whenever a Myo has been paired.
        @Override
        public void onPair(Myo myo, long timestamp) {
            // Store the MAC address of the paired Myo so we can automatically pair with it
            // the next time the app starts.
            mPrefs.edit().putString(PREF_MAC_ADDRESS, myo.getMacAddress()).apply();
        }

        // onArmRecognized() is called whenever Myo has recognized a setup gesture after someone has put it on their
        // arm. This lets Myo know which arm it's on and which way it's facing.
        @Override
        public void onArmRecognized(Myo myo, long timestamp, Arm arm, XDirection xDirection) {
            // Save the arm the Myo is on so that we can use it in the pose events.
            mArm = arm;
            mXDirection = xDirection;
        }

        // onArmLost() is called whenever Myo has detected that it was moved from a stable position on a person's arm after
        // it recognized the arm. Typically this happens when someone takes Myo off of their arm, but it can also happen
        // when Myo is moved around on the arm.
        @Override
        public void onArmLost(Myo myo, long timestamp) {
            mArm = Arm.UNKNOWN;
            mXDirection = XDirection.UNKNOWN;
        }

        private XDirection mXDirection = XDirection.UNKNOWN;

        /*@Override
        public void onOrientationData(Myo myo, long timestamp, Quaternion rotation) {
            // Calculate Euler angles (roll, pitch, and yaw) from the quaternion.
            float roll = (float) Math.toDegrees(Quaternion.roll(rotation));
            float pitch = (float) Math.toDegrees(Quaternion.pitch(rotation));
            float yaw = (float) Math.toDegrees(Quaternion.yaw(rotation));

//            float mDeviceTiltMax = getControlDroneActivity().getDeviceTilt();
            float mDroneYawSpeed = getControlDroneActivity().getDroneYawSpeed();
//
//
//            // Adjust roll and pitch for the orientation of the Myo on the arm.
//            if (mXDirection == XDirection.TOWARD_ELBOW) {
//                roll *= -1;
//                pitch *= -1;
//            }
//
//            float rollRatio = roll / mDeviceTiltMax;
//
//            if (rollRatio > 1f)
//                rollRatio = 1f;
//            else if (rollRatio < -1f)
//                rollRatio = -1f;
//
//            float pitchAngle = pitch;
//            float pitchRatio = pitchAngle / mDeviceTiltMax;
//
//            if (pitchRatio > 1f)
//                pitchRatio = 1f;
//            else if (pitchRatio < -1f)
//                pitchRatio = -1f;
//
//            // Set the tilt angle
//            getControlDroneActivity().setDroneRoll(rollRatio);
//            getControlDroneActivity().setDronePitch(pitchRatio);
//            getControlDroneActivity().getHudView().setPitchRoll(pitchRatio, -rollRatio);
            if(isFisted) {
                float angSpeedRatio = (yaw * 4) / mDroneYawSpeed;

                if (angSpeedRatio > 1f)
                    angSpeedRatio = 1f;
                else if (angSpeedRatio < -1f)
                    angSpeedRatio = -1f;

                //Set the yaw speed
                getControlDroneActivity().setDroneYaw(-angSpeedRatio);
            }
        }*/

        // onPose() is called whenever a Myo provides a new pose.
        @Override
        public void onPose(Myo myo, long timestamp, Pose pose) {
            Log.i(TAG, "pose: " + pose);

            if (!mActivityActive) {
                if (pose == Pose.THUMB_TO_PINKY) {
                    mHandler.postDelayed(mLaunchRunnable, LAUNCH_HOLD_DURATION);
                } else {
                    mHandler.removeCallbacks(mLaunchRunnable);
                }
            } else {
                if (mArm == Arm.LEFT) {
                    if (pose == Pose.WAVE_IN) {
                        pose = Pose.WAVE_OUT;
                    } else if (pose == Pose.WAVE_OUT) {
                        pose = Pose.WAVE_IN;
                    }
                }

                // Dispatch touch pad events for the standard navigation controls based on the
                // current pose.
                switch (pose) {
                    case FIST:
                        isFisted = true;
                        break;
                    case FINGERS_SPREAD:
                        Log.i(TAG, "takeoff");
                        getControlDroneActivity().triggerDroneTakeOff();
                        break;
                    case WAVE_IN:
                        //sendEvents(MotionEventGenerator.getSwipeRightEvents());
                        getControlDroneActivity().setDronePitch(0);
                        getControlDroneActivity().setDroneRoll(0);
                        getControlDroneActivity().setDroneYaw(0);
                        break;
                    case WAVE_OUT:
                        //sendEvents(MotionEventGenerator.getSwipeLeftEvents());
                        break;
                }
            }
        }

        // Dispatch a list of events using Instrumentation. Due to Android security restrictions,
        // the events will only be sent to activities in the same application.
        private void sendEvents(final List<MotionEvent> events) {
            if (mExecutor.isShutdown()) {
                Log.w(TAG, "Executor shutdown. Can't send event.");
                return;
            }

            for (final MotionEvent event : events) {
                // Post the event dispatch to a background thread, as sendPointerSync can not be
                // called from the main thread.
                mExecutor.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            mInstrumentation.sendPointerSync(event);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed sending motion event." , e);
                        }
                    }
                });
            }
        }
    }
}
