package zvikabh.rccarcontroller;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

/**
 * Class for handling communication with a USB device.
 */
public class UsbCommHandler {

	/**
	 * Initializes communications with the specified device at the specified baudrate.
	 */
	public UsbCommHandler(Context context, UsbDevice device, int baudRate) {
		mContext = context;
		mDevice = device;
		mBaudRate = baudRate;
		
		if (!initDevice()) {
			return;
		}
		
		mSenderThread = new SenderThread();
		mSenderThread.start();
	}
	
	/**
	 * Sends the provided byte array to the connected USB device.
	 * This function returns immediately; the communications is performed in a separate thread, and
	 * may take some time to complete.
	 */
	public void sendDataToUsbDevice(byte[] data) {
		mSenderThread.mHandler.obtainMessage(10, data).sendToTarget();
	}

	private boolean initDevice() {
		
		if (mDevice == null) {
			Log.e(TAG, "mDevice is null");
			return false;
		}

        UsbManager usbManager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);
        if (usbManager == null) {
        	Log.e(TAG, "usbManager is null");
        	return false;
        }
        
        if (usbManager.hasPermission(mDevice)) {
        	Log.d(TAG, "USB Manager says we have permission to access the device.");
        } else {
        	Log.e(TAG, "USB Manager says we DON'T have permission to access the device!");
        }
        
        mUsbConnection = usbManager.openDevice(mDevice);
        if (mUsbConnection == null) {
            Log.e(TAG, "Opening USB device failed!");
            return false;
        }
        
        Log.i(TAG, "Device opened successfully! " + mDevice);
        
        UsbInterface usbInterface = mDevice.getInterface(0);
        if (!mUsbConnection.claimInterface(usbInterface, true)) {
            Log.e(TAG, "Claiming interface failed!");
            mUsbConnection.close();
            return false;
        }

        // Arduino USB serial converter setup
        // Set control line state
        mUsbConnection.controlTransfer(0x21, 0x22, 0, 0, null, 0, 0);
        // Set line encoding.
        mUsbConnection.controlTransfer(0x21, 0x20, 0, 0, getLineEncoding(mBaudRate), 7, 0);

        for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
            if (usbInterface.getEndpoint(i).getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (usbInterface.getEndpoint(i).getDirection() == UsbConstants.USB_DIR_IN) {
                    mInUsbEndpoint = usbInterface.getEndpoint(i);
                } else if (usbInterface.getEndpoint(i).getDirection() == UsbConstants.USB_DIR_OUT) {
                    mOutUsbEndpoint = usbInterface.getEndpoint(i);
                }
            }
        }

        if (mInUsbEndpoint == null) {
            Log.e(TAG, "No in endpoint found!");
            Toast.makeText(mContext, "no_in_endpoint_found", Toast.LENGTH_LONG).show();
            mUsbConnection.close();
            return false;
        }

        if (mOutUsbEndpoint == null) {
            Log.e(TAG, "No out endpoint found!");
            Toast.makeText(mContext, "no_out_endpoint_found", Toast.LENGTH_LONG).show();
            mUsbConnection.close();
            return false;
        }

        return true;
	}

    private byte[] getLineEncoding(int baudRate) {
        final byte[] lineEncodingRequest = { (byte) 0x80, 0x25, 0x00, 0x00, 0x00, 0x00, 0x08 };
        
        lineEncodingRequest[0] = (byte) (baudRate & 0xFF);
        lineEncodingRequest[1] = (byte) ((baudRate >> 8) & 0xFF);
        lineEncodingRequest[2] = (byte) ((baudRate >> 16) & 0xFF);

        return lineEncodingRequest;
    }

    private class SenderThread extends Thread {
        public Handler mHandler;

        public SenderThread() {
            super("arduino_sender");
        }
        
        @SuppressLint("HandlerLeak") 
        public void run() {
            Looper.prepare();

            mHandler = new Handler() {
                public void handleMessage(Message msg) {
                    Log.d(TAG, "handleMessage() " + msg.what);
                    if (msg.what == 10) {
                        final byte[] dataToSend = (byte[]) msg.obj;

                        Log.d(TAG, "calling bulkTransfer() out");
                        final int len = mUsbConnection.bulkTransfer(mOutUsbEndpoint, dataToSend, dataToSend.length, 0);
                        Log.d(TAG, len + " of " + dataToSend.length + " bytes sent.");
                        
                        // TODO: Do we still want the following lines?
                        //Intent sendIntent = new Intent(DATA_SENT_INTERNAL_INTENT);
                        //sendIntent.putExtra(DATA_EXTRA, dataToSend);
                        //sendBroadcast(sendIntent);
                    } else if (msg.what == 11) {
                        Looper.myLooper().quit();
                    }
                }
            };

            Looper.loop();
            Log.i(TAG, "sender thread stopped");
        }
    }
    
    private int mBaudRate;
	private Context mContext;
	
	private volatile UsbDevice mDevice;
    private volatile UsbDeviceConnection mUsbConnection = null;
    private volatile UsbEndpoint mInUsbEndpoint = null;
    private volatile UsbEndpoint mOutUsbEndpoint = null;
    
    private SenderThread mSenderThread;
    
    private static final String TAG = "UsbCommHandler";
}
