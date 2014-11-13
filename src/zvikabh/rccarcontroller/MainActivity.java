package zvikabh.rccarcontroller;

import java.util.HashMap;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Toast;


/*
 * Main activity for controlling the RC car.
 * Portions taken from Arduino Communicator, https://github.com/jeppsson/Arduino-Communicator
 */
public class MainActivity extends Activity {
	
	private static final String TAG = "rccarcontroller.MainActivity";
    
	// Values used in Arduino Communicator
	private static final int ARDUINO_USB_VENDOR_ID = 0x2341;
    private static final int ARDUINO_UNO_USB_PRODUCT_ID = 0x01;
    
    // Values used by my Arduino clone
    private static final int ARDUINO_CLONE_VENDOR_ID = 0x0403;  // 1027
    private static final int ARDUINO_CLONE_PRODUCT_ID = 0x6001;  // 24577
    
    // SeekBars for the commands to the left and right motors.
    private SeekBar mSeekbarLeft;
    private SeekBar mSeekbarRight;
    
    // Receives notifications of changes in one of the seek bars.
    // Which seek bar has changed is irrelevant, since we check the values of both of them
    // whenever a change occurs.
    private SeekBarChangeListener mSeekBarChangeListener = new SeekBarChangeListener();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        mSeekbarLeft = (SeekBar) findViewById(R.id.seekBarLeft);
        mSeekbarRight = (SeekBar) findViewById(R.id.seekBarRight);

        mSeekbarLeft.setOnSeekBarChangeListener(mSeekBarChangeListener);
        mSeekbarRight.setOnSeekBarChangeListener(mSeekBarChangeListener);
        
        findDevice();
    }

    /**
     * Finds the Arduino USB device.
     */
    private boolean findDevice() {
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> usbDeviceList = usbManager.getDeviceList();
        
        int lastVendorId=0, lastProductId=0;
        
        for (UsbDevice usbDevice : usbDeviceList.values()) {
        	lastVendorId = usbDevice.getVendorId();
        	lastProductId = usbDevice.getProductId();
        	Log.w(TAG, "VendorId: " + lastVendorId + "   ProductId: " + lastProductId);
            if ((lastVendorId == ARDUINO_USB_VENDOR_ID && lastProductId == ARDUINO_UNO_USB_PRODUCT_ID) ||
            		(lastVendorId == ARDUINO_CLONE_VENDOR_ID && lastProductId == ARDUINO_CLONE_PRODUCT_ID)) {
        		Log.d(TAG, "Found Arduino device");
        		Toast.makeText(getBaseContext(), "Found Arduino device", Toast.LENGTH_LONG).show();

        		// Ask the user for permission to access the device.
        		// Send the response to the service, to start up the USB access.
                Intent startIntent = new Intent(getApplicationContext(), RCCarControllerService.class);
                PendingIntent pendingIntent = PendingIntent.getService(getApplicationContext(), 0, startIntent, 0);
                usbManager.requestPermission(usbDevice, pendingIntent);
        		return true;
            }
        }
        
        Toast.makeText(getBaseContext(), "No Arduino devices found, " + lastVendorId + ":" + lastProductId, Toast.LENGTH_LONG).show();
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
     * Class for handling notifications of changes to one of the motor's seek bars.
     */
    private class SeekBarChangeListener implements OnSeekBarChangeListener {
    	
		@Override
		public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
			int leftPos = mSeekbarLeft.getProgress() - mSeekbarLeft.getMax() / 2;
			int rightPos = mSeekbarRight.getProgress() - mSeekbarRight.getMax() / 2;
			
			byte[] data = new byte[4];
			data[0] = (byte) (leftPos & 0xFF);
			data[1] = (byte) ((leftPos >> 8) & 0xFF);
			data[2] = (byte) (rightPos & 0xFF);
			data[3] = (byte) ((rightPos >> 8) & 0xFF);
			
			Intent sendDataIntent = new Intent(getApplicationContext(), RCCarControllerService.class);
			sendDataIntent.setAction(RCCarControllerService.SEND_DATA_INTENT);
			sendDataIntent.putExtra(RCCarControllerService.DATA_EXTRA, data);
			startService(sendDataIntent);
		}
	
		@Override
		public void onStartTrackingTouch(SeekBar arg0) {
		}
	
		@Override
		public void onStopTrackingTouch(SeekBar arg0) {
		}
		
    }
}
