package zvikabh.rccarcontroller;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;


/**
 * Main activity for controlling the RC car.
 * Portions taken from Arduino Communicator, https://github.com/jeppsson/Arduino-Communicator
 */
public class MainActivity extends Activity {
	
	private static final String TAG = "rccarcontroller.MainActivity";
	
	private static final String ACTION_USB_PERMISSION = "zvikabh.rccarcontroller.USB_PERMISSION";
    
	// Values used in Arduino Communicator
	private static final int ARDUINO_USB_VENDOR_ID = 0x2341;
	private static final int ARDUINO_UNO_USB_PRODUCT_ID = 0x0001;

	// Values used by my Arduino clone
	private static final int ARDUINO_CLONE_VENDOR_ID = 0x0403;  // 1027
	private static final int ARDUINO_CLONE_PRODUCT_ID = 0x6001;  // 24577

	/**
     * SeekBars for the commands to the left and right motors.
     */
    private TextView mTextviewArduinoResponse;
    private SpeedControllerView mViewSpeedController;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "onCreate");

        setContentView(R.layout.activity_main);
        
        mTextviewArduinoResponse = (TextView) findViewById(R.id.textviewArduinoResponse);
        mViewSpeedController = (SpeedControllerView) findViewById(R.id.viewSpeedController);
        mViewSpeedController.setThrottleChangedListener(new ThrottleChangeListener());

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        registerReceiver(mBroadcastReceiver, filter);

        findDevice();
    }

	@Override
	protected void onDestroy() {
		if (mUsbSerialPort != null) {
			try {
				mUsbSerialPort.close();
			} catch (IOException e) {
				Log.e(TAG, "Exception while closing port: " + e);
			}
		}
		
		mUsbSerialPort = null;
		
		unregisterReceiver(mBroadcastReceiver);
		
		super.onDestroy();
	}

	/**
     * Finds the Arduino USB device and asks the user for permission to use it.
     */
    private boolean findDevice() {
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        
        // Find the Arduino device.
        for (UsbSerialDriver driver : availableDrivers) {
        	UsbDevice device = driver.getDevice();
        	int vendorId = device.getVendorId();
        	int productId = device.getProductId();
        	if ((vendorId == ARDUINO_USB_VENDOR_ID && productId == ARDUINO_UNO_USB_PRODUCT_ID) ||
        		(vendorId == ARDUINO_CLONE_VENDOR_ID && productId == ARDUINO_CLONE_PRODUCT_ID)) {
        		Log.d(TAG, "Found Arduino device!");
        		
        		mUsbSerialDriver = driver;
        		
        		// Request user's permission to access the device.
        		// Processing continues in mBroadcastReceiver after user grants permission.
        		PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        		usbManager.requestPermission(device, pendingIntent);
        		return true;
        	}
        }
        
    	Log.e(TAG, "No Arduino devices found");
    	Toast.makeText(this, "No Arduino devices found", Toast.LENGTH_LONG).show();
        return false;

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
			if (mUsbSerialPort == null) {
				Log.w(TAG, "Cannot update Arduino - connection to Arduino was unsuccessful.");
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
			
			mTextviewArduinoResponse.setText("L="+leftPowerLevel+"  R="+rightPowerLevel);

			try {
				mUsbSerialPort.write(data, 500);
			} catch (IOException e) {
				Log.w(TAG, "Failed to write to USB serial port: " + e.toString());
			}
		}
    	
    }
    
	final private static char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
	private static String bytesToHex(byte[] bytes) {
	    char[] hexChars = new char[bytes.length * 2];
	    for ( int j = 0; j < bytes.length; j++ ) {
	        int v = bytes[j] & 0xFF;
	        hexChars[j * 2] = HEX_ARRAY[v >>> 4];
	        hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
	    }
	    return new String(hexChars);
	}

	private volatile UsbSerialPort mUsbSerialPort;
	private volatile UsbSerialDriver mUsbSerialDriver;
	
	/**
	 * Completes the setup of the connection to the Arduino device,
	 * after the user has granted permission to access it.
	 * At this point, mUsbSerialDriver is set to the Arduino device.
	 * Upon successful completion, mUsbSerialPort will point to the (opened) port.
	 * Upon failure, mUsbSerialPort will be null.
	 * @return true iff success
	 */
	private boolean openUsbSerialPort() {
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        UsbDeviceConnection connection = usbManager.openDevice(mUsbSerialDriver.getDevice());
        if (connection == null) {
        	// Strange - we were supposed to have already gotten permission.
        	Log.e(TAG, "No permission to access device");
        	Toast.makeText(this, "No permission to access device", Toast.LENGTH_LONG).show();
        	mUsbSerialPort = null;
        	return false;
        }
        
        List<UsbSerialPort> ports = mUsbSerialDriver.getPorts();
        if (ports.size() == 0) {
        	Log.e(TAG, "Device has 0 ports");
        	Toast.makeText(this, "Device has 0 ports", Toast.LENGTH_LONG).show();
        	mUsbSerialPort = null;
        	return false;
        }
        
        mUsbSerialPort = ports.get(0);
        
        try {
			mUsbSerialPort.open(connection);
			mUsbSerialPort.setParameters(19200, UsbSerialPort.DATABITS_8, 
					UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
		} catch (IOException e) {
			mUsbSerialPort = null;
        	Log.e(TAG, "Could not open port");
        	Toast.makeText(this, "Could not open port", Toast.LENGTH_LONG).show();
        	return false;
		}
        
        // Start receiver thread.
        mReceiverThread.start();
        
        return true;
	}
	
	private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			if (action.equals(ACTION_USB_PERMISSION)) {
				if (!intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
					Log.e(TAG, "User did not grant permission to use the device.");
					return;
				}
				openUsbSerialPort();
			}
		}
		
	};
	
	private final Thread mReceiverThread = new Thread() {
		
		public void run() {
			while (mUsbSerialPort != null) {
				byte[] data = new byte[4096];
				int nBytesRead = 0;
				try {
					nBytesRead = mUsbSerialPort.read(data, 1000);
				} catch (IOException e) {
					Log.w(TAG, "Error reading from USB serial port: " + e);
					continue;
				}
				if (nBytesRead > 0) {
					String receivedString;
					try {
						receivedString = new String(data, "UTF-8");
					} catch (UnsupportedEncodingException e) {
						receivedString = "Invalid UTF-8 received";
					}
					final String arduinoResponse = receivedString;
					mTextviewArduinoResponse.post(new Runnable() {
						public void run() {
							mTextviewArduinoResponse.setText(arduinoResponse);
						}
					});
				}
			}
			Log.d(TAG, "Receiver thread stopped: mUsbSerialPort == null");
		}
		
	};
}
