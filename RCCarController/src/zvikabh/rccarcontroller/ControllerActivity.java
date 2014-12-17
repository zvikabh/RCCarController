package zvikabh.rccarcontroller;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import zvikabh.views.speedcontroller.SpeedControllerView;

/**
 * Activity for controlling the RC car.
 */
public class ControllerActivity extends Activity {

    private static final String TAG = "ControllerActivity";

    private SpeedControllerView mViewSpeedController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "onCreate");

        setContentView(R.layout.activity_main);

        Intent intent = new Intent(this, ConnectionHandlerService.class);
        intent.putExtra(ConnectionHandlerService.ESTABLISH_CONNECTION_TYPE,
                ConnectionHandlerService.EstablishConnectionTypes.NONE);
        bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);

        mViewSpeedController = (SpeedControllerView) findViewById(R.id.viewSpeedController);
        mViewSpeedController.setThrottleChangedListener(new ThrottleChangeListener());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mServiceConnection != null) {
            unbindService(mServiceConnection);
        }
        stopService(new Intent(this, ConnectionHandlerService.class));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Class for handling notifications of user throttle changes.
     */
    private class ThrottleChangeListener implements SpeedControllerView.ThrottleChangedListener {

        @Override
        public void throttleChanged(float x, float y) {
            if (mConnectionBinder == null) {
                Log.w(TAG, "Cannot transmit data - service binder not ready.");
                return;
            }

            // Maximum power to deliver to each motor, on a scale of [0,1].
            final double maxPower = Math.min(1, Math.sqrt(x*x + y*y));

            // Angle in which car should move:
            // -pi:   turn left
            // -pi/2: backward
            // 0:     turn right
            // pi/2:  forward
            // pi:    turn left
            final double angle = Math.atan2(-y, x);

            final double pi = Math.PI;

            // Fraction of max power to deliver to the left and right motors.
            // Each motor (separately) is in the range [-1,1].
            double leftPower, rightPower;
            if (angle < -pi/2) {
                // angle is in [-pi, -pi/2].
                leftPower = -1.0;
                rightPower = -(angle + pi*0.75) / (pi/4);
            } else if (angle < 0) {
                // angle is in [-pi/2, 0].
                leftPower = (angle + pi/4) / (pi/4);
                rightPower = -1.0;
            } else if (angle < pi/2) {
                // angle is in [0, pi/2].
                leftPower = 1.0;
                rightPower = (angle - pi/4) / (pi/4);
            } else {
                // angle is in [pi/2, pi].
                leftPower = -(angle - pi*0.75) / (pi/4);
                rightPower = 1.0;
            }

            final short leftPowerLevel = (short) (400 * leftPower * maxPower);
            final short rightPowerLevel = (short) (400 * rightPower * maxPower);

            final byte[] data = new byte[] { 0x7F, 0x7F, (byte)0x80, (byte)0x80, 0x00, 0x00, 0x00, 0x00 };
            data[4] = (byte) (leftPowerLevel & 0xFF);
            data[5] = (byte) ((leftPowerLevel >> 8) & 0xFF);
            data[6] = (byte) (rightPowerLevel & 0xFF);
            data[7] = (byte) ((rightPowerLevel >> 8) & 0xFF);

            mConnectionBinder.sendData(data);
        }

    }

    private ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            Log.d(TAG, "ConnectionHandlerService bound to ControllerActivity");
            mConnectionBinder = (ConnectionHandlerService.ConnectionBinder) binder;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "ConnectionHandlerService disconnected from ControllerActivity");
            mConnectionBinder = null;
        }
        
    };

    private volatile ConnectionHandlerService.ConnectionBinder mConnectionBinder;
}
