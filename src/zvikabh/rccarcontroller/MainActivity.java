package zvikabh.rccarcontroller;

import java.util.HashMap;

import android.app.Activity;
import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;


/*
 * Main activity for controlling the RC car.
 * Portions taken from Arduino Communicator, https://github.com/jeppsson/Arduino-Communicator
 */
public class MainActivity extends Activity {
	
	private static final String TAG = "MainActivity";
    
	// Values used in Arduino Communicator
	private static final int ARDUINO_USB_VENDOR_ID = 0x2341;
    private static final int ARDUINO_UNO_USB_PRODUCT_ID = 0x01;
    
    // Values used by my Arduino clone
    private static final int ARDUINO_CLONE_VENDOR_ID = 0x0403;
    private static final int ARDUINO_CLONE_PRODUCT_ID = 0x6001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
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
        	Log.d(TAG, "VendorId: " + lastVendorId);
            if ((lastVendorId == ARDUINO_USB_VENDOR_ID && lastProductId == ARDUINO_UNO_USB_PRODUCT_ID) ||
            		(lastVendorId == ARDUINO_CLONE_VENDOR_ID && lastProductId == ARDUINO_CLONE_PRODUCT_ID)) {
        		Log.d(TAG, "Found Arduino device");
        		Toast.makeText(getBaseContext(), "Found Arduino device", Toast.LENGTH_LONG).show();
        		// TODO: Get permission for device, and pass it to the service.
        		// See ArduinoCommunicatorActivity.java:115.
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
}
