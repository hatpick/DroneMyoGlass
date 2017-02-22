/**
 * @author Fredia Huya-Kouadio
 * @date Sep 15, 2013
 */
package com.ne0fhyklabs.freeflight.controllers;

import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import com.ne0fhyklabs.freeflight.activities.ControlDroneActivity;
import com.ne0fhyklabs.freeflight.drone.DroneConfig;
import com.ne0fhyklabs.freeflight.sensors.DeviceOrientationManager;
import com.ne0fhyklabs.freeflight.ui.hud.Sprite;

public class Gamepad extends Controller {

    static final String TAG = Gamepad.class.getName();

    private final static int TILT_STEP = 1;

    private InputDevice mLastInputDevice;

    Gamepad(final ControlDroneActivity droneControl) {
        super(droneControl);
    }

    private boolean isSourceValid(int inputDeviceSource) {
        return (inputDeviceSource & InputDevice.SOURCE_CLASS_JOYSTICK) != 0;
    }

    /*
     * (non-Javadoc)
     *
     * @see com.parrot.freeflight.controllers.Controller#initImpl()
     */
    @Override
    protected boolean initImpl() {
        // Check to see if there's a device of class JOYSTICK
        int[] deviceIds = InputDevice.getDeviceIds();
        for ( int deviceId : deviceIds ) {
            InputDevice device = InputDevice.getDevice(deviceId);
            if ( isSourceValid(device.getSources()) )
                return true;
        }
        return false;
    }

    /*
     * (non-Javadoc)
     *
     * @see com.parrot.freeflight.controllers.Controller#onEventImpl(android.view.View,
     * android.view.MotionEvent)
     */
    @Override
    protected boolean onGenericMotionImpl(View view, MotionEvent event) {
        // Check that the event came from a joystick
        if ( isSourceValid(event.getSource()) && event.getAction() == MotionEvent.ACTION_MOVE ) {
            // Cache the most recently obtained device information.
            // The device information may change over time but it can be somewhat expensive to query
            if ( mLastInputDevice == null || mLastInputDevice.getId() != event.getDeviceId() ) {
                mLastInputDevice = event.getDevice();
                // It's possible for the device id to be invalid, in which case, getDevice() return
                // null
                if ( mLastInputDevice == null )
                    return false;
            }

            // Process all historical movement samples in the batch
            final int historySize = event.getHistorySize();
            for ( int i = 0; i < historySize; i++ ) {
                processJoystickInput(event, i);
            }

            // Process the current movement sample in the batch.
            processJoystickInput(event, -1);
            return true;
        }

        return false;
    }

    @Override
    protected boolean onKeyDownImpl(int keyCode, KeyEvent event) {
        // TODO: complete
        int eventCount = event.getRepeatCount() + 1;

        // Mapping for the SnakeByte iDroid:con controller
        switch (keyCode) {

            case KeyEvent.KEYCODE_BUTTON_6: {
                int currentTilt = mDroneControl.getDroneTilt();
                if ( currentTilt != DroneConfig.INVALID_TILT ) {
                    // Increase the tilt by 5 degrees
                    int newTilt = currentTilt + (TILT_STEP * eventCount);
                    if ( newTilt > DroneConfig.TILT_MAX )
                        newTilt = DroneConfig.TILT_MAX;

                    mDroneControl.setDroneTilt(newTilt);
                }
                return true;
            }

            case KeyEvent.KEYCODE_BUTTON_5: {
                int currentTilt = mDroneControl.getDroneTilt();
                if ( currentTilt != DroneConfig.INVALID_TILT ) {
                    // Decrease the tilt by 5 degrees
                    int newTilt = currentTilt - (TILT_STEP * eventCount);
                    if ( newTilt < DroneConfig.TILT_MIN )
                        newTilt = DroneConfig.TILT_MIN;
                    mDroneControl.setDroneTilt(newTilt);
                }
                return true;
            }

            case KeyEvent.KEYCODE_BUTTON_10:
                // Start button. trigger takeOff/Landing
                mDroneControl.triggerDroneTakeOff();
                return true;

            case KeyEvent.KEYCODE_BUTTON_2:
                // A button. Used for flip
                mDroneControl.doLeftFlip();
                return true;

            case KeyEvent.KEYCODE_BUTTON_3:
                // B button. Used to trigger camera recording
                mDroneControl.onRecord();
                return true;

            case KeyEvent.KEYCODE_BUTTON_1:
                // X button. Used to trigger pic snap
                mDroneControl.onTakePhoto();
                return true;

            case KeyEvent.KEYCODE_BUTTON_4:
                // Y button. Used to trigger camera switch (front to bottom, and vice versa)
                mDroneControl.switchDroneCamera();
                return true;

        }

        return false;
    }

    @Override
    protected boolean onKeyUpImpl(int keyCode, KeyEvent event) {
        // TODO: complete
        return false;
    }

    @Override
    protected boolean onTouchImpl(View view, MotionEvent event) {
        return false;
    }

    private void processJoystickInput(MotionEvent event, int historyPos) {
        // Get joystick position
        // Many game pads with two joysticks report the position of the second joystick
        // using the Z, and RZ axes so we also handle those.
        // TODO: allow configuration of joystick

        // Left joystick control roll and pitch
        float leftX = getCenteredAxis(event, mLastInputDevice, MotionEvent.AXIS_X, historyPos);
        if ( leftX == 0 )
            leftX = getCenteredAxis(event, mLastInputDevice, MotionEvent.AXIS_HAT_X, historyPos);

        float leftY = getCenteredAxis(event, mLastInputDevice, MotionEvent.AXIS_Y, historyPos);
        if ( leftY == 0 )
            leftY = getCenteredAxis(event, mLastInputDevice, MotionEvent.AXIS_HAT_Y, historyPos);

        // Right joystick controls gaz, and yaw
        float rightX = getCenteredAxis(event, mLastInputDevice, MotionEvent.AXIS_Z, historyPos);
        float rightY = getCenteredAxis(event, mLastInputDevice, MotionEvent.AXIS_RZ, historyPos);

        final boolean enableProgressiveCommand = leftX != 0 || leftY != 0;
        final boolean enableProgressiveCommandCombinedYaw = rightX != 0 || rightY != 0;

        if ( enableProgressiveCommand ) {
            mDroneControl.setDroneProgressiveCommandEnabled(true);
            if ( enableProgressiveCommandCombinedYaw )
                mDroneControl.setDroneProgressiveCommandCombinedYawEnabled(true);
        }

        mDroneControl.setDroneRoll(leftX);
        mDroneControl.setDronePitch(leftY);

        mDroneControl.setDroneYaw(rightX);
        mDroneControl.setDroneGaz(-rightY);

        if ( !enableProgressiveCommand ) {
            mDroneControl.setDroneProgressiveCommandEnabled(false);
            mDroneControl.setDroneProgressiveCommandCombinedYawEnabled(false);
        }

        if ( !enableProgressiveCommandCombinedYaw ) {
            mDroneControl.setDroneProgressiveCommandCombinedYawEnabled(false);
        }
    }

    private static float getCenteredAxis(MotionEvent event, InputDevice device, int axis,
                                         int historyPos) {
        final InputDevice.MotionRange range = device.getMotionRange(axis, event.getSource());
        if ( range != null ) {
            final float flat = range.getFlat();
            final float value = historyPos < 0 ? event.getAxisValue(axis) : event
                    .getHistoricalAxisValue(axis, historyPos);

            // Ignore axis values that are within the 'flat' region of the joystick axis center.
            // A joystick at rest does not always report an absolute position of (0,0).
            if ( Math.abs(value) > flat ) {
                return value;
            }
        }
        return 0;
    }

    /*
     * (non-Javadoc)
     *
     * @see com.parrot.freeflight.controllers.Controller#resumeImpl()
     */
    @Override
    protected void resumeImpl() {
        // Nothing to do
    }

    /*
     * (non-Javadoc)
     *
     * @see com.parrot.freeflight.controllers.Controller#pauseImpl()
     */
    @Override
    protected void pauseImpl() {
        // Nothing to do
    }

    /*
     * (non-Javadoc)
     *
     * @see com.parrot.freeflight.controllers.Controller#destroyImpl()
     */
    @Override
    protected void destroyImpl() {
        // Nothing to do
    }
}