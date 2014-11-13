package zvikabh.rccarcontroller;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

public class RCCarControllerService extends Service {
	
	private static final String TAG = "RCCarControllerService";
	static final String SEND_DATA_INTENT = "zvikabh.rccarcontroller.intent.SEND_DATA";
    final static String DATA_SENT_INTERNAL_INTENT = "zvikabh.rccarcontroller.internal.intent.DATA_SENT";
	static final String DATA_EXTRA = "zvikabh.rccarcontroller.extra.DATA";
	
	static final int BAUD_RATE = 19200;

	// Is the service currently running?
    private volatile boolean mIsRunning = false;
    
    // The Arduino USB device.
    private volatile UsbDevice mUsbDevice = null;
    private volatile UsbDeviceConnection mUsbConnection = null;
    private volatile UsbEndpoint mInUsbEndpoint = null;
    private volatile UsbEndpoint mOutUsbEndpoint = null;
    
    // Thread used for sending data to the Arduino.
    private SenderThread mSenderThread;

    @Override
	public IBinder onBind(Intent arg0) {
		return null;
	}
	
	@Override
	public void onCreate() {
		Log.d(TAG, "onCreate");
		super.onCreate();
		
        IntentFilter filter = new IntentFilter();
        filter.addAction(SEND_DATA_INTENT);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mReceiver, filter);
	}
	
    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }
    
    @Override
	public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand() " + intent + " " + flags + " " + startId);
        
        if (mIsRunning) {
            Log.d(TAG, "Service already running.");
            return Service.START_REDELIVER_INTENT;
        }
        
        Log.d(TAG, "Starting service");

        mIsRunning = true;
        
        if (!intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
            Log.w(TAG, "Permission to access USB device was denied");
            Toast.makeText(getBaseContext(), "Permission to access USB device was denied", Toast.LENGTH_LONG).show();
            stopSelf();
            return Service.START_REDELIVER_INTENT;
        }

        mUsbDevice = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        Log.d(TAG, "Permission granted: mUsbDevice=" + mUsbDevice);
        
        if (!initDevice()) {
            Log.e(TAG, "Init of device failed!");
            Toast.makeText(getBaseContext(), "Init of device failed!", Toast.LENGTH_LONG).show();
            stopSelf();
            return Service.START_REDELIVER_INTENT;
        }
        
        startSenderThread();

        Log.d(TAG, "Service started successfully!");

        // Indicates that the service should be restarted repeatedly as long as it has intents to process.
        return Service.START_REDELIVER_INTENT;
	}
    
    private boolean initDevice() {
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        mUsbConnection = usbManager.openDevice(mUsbDevice);
        if (mUsbConnection == null) {
            Log.e(TAG, "Opening USB device failed!");
            return false;
        }
        UsbInterface usbInterface = mUsbDevice.getInterface(1);
        if (!mUsbConnection.claimInterface(usbInterface, true)) {
            Log.e(TAG, "Claiming interface failed!");
            mUsbConnection.close();
            return false;
        }

        // Arduino USB serial converter setup
        // Set control line state
        mUsbConnection.controlTransfer(0x21, 0x22, 0, 0, null, 0, 0);
        // Set line encoding.
        mUsbConnection.controlTransfer(0x21, 0x20, 0, 0, getLineEncoding(BAUD_RATE), 7, 0);

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
            Toast.makeText(getBaseContext(), "no_in_endpoint_found", Toast.LENGTH_LONG).show();
            mUsbConnection.close();
            return false;
        }

        if (mOutUsbEndpoint == null) {
            Log.e(TAG, "No out endpoint found!");
            Toast.makeText(getBaseContext(), "no_out_endpoint_found", Toast.LENGTH_LONG).show();
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

    private void startSenderThread() {
    	if (mSenderThread != null) {
    		Toast.makeText(getBaseContext(), "Sender thread started twice!", Toast.LENGTH_LONG).show();
    	}
        mSenderThread = new SenderThread();
        mSenderThread.start();
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
                        Log.d(TAG, len + " of " + dataToSend.length + " sent.");
                        Intent sendIntent = new Intent(DATA_SENT_INTERNAL_INTENT);
                        sendIntent.putExtra(DATA_EXTRA, dataToSend);
                        sendBroadcast(sendIntent);
                    } else if (msg.what == 11) {
                        Looper.myLooper().quit();
                    }
                }
            };

            Looper.loop();
            Log.i(TAG, "sender thread stopped");
        }
    }
    
	BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
        	String action = intent.getAction();
        	Log.d(TAG, "BroadcastReceive: " + action);
        	
        	if (action.equals(UsbManager.ACTION_USB_ACCESSORY_DETACHED)) {
        		Toast.makeText(context, "Arduino disconnected", Toast.LENGTH_LONG).show();
        		stopSelf();
        	} else if (action.equals(SEND_DATA_INTENT)) {
                byte[] dataToSend = intent.getByteArrayExtra(DATA_EXTRA);
                
                if (dataToSend == null) {
                    Log.e(TAG, "No " + DATA_EXTRA + " extra in intent!");
                    Toast.makeText(context, "No data extra in intent", Toast.LENGTH_LONG).show();
                    return;
                }

                // Send data in sender thread.
                mSenderThread.mHandler.obtainMessage(10, dataToSend).sendToTarget();
        	}
        }		
	};

}
