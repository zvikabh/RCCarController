package zvikabh.rccarreceiver;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.List;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

public class RCCarReceiverService extends Service {
    
    public RCCarReceiverService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Receiver service onStartCommand");
        synchronized (this) {
            if (mReceiverThread != null && mReceiverThread.isAlive()) {
                // This is not supposed to happen because MainActivity stops the service before restarting it.
                makeToast("Receiver already running");
                return START_STICKY;
            } 
        }
        
        postStatusMessage("Receiver service starting");

        if (intent == null) { 
            // Attempting to restart the service. 
            // Keep the IP address and port from the previous call.
        } else {
            mIpAddress = intent.getStringExtra(MainActivity.INTENT_EXTRA_IP_ADDRESS);
            mIpPort = intent.getIntExtra(MainActivity.INTENT_EXTRA_PORT, -1);
        }
        
        if (mIpAddress == null || mIpPort == -1) {
            makeToast("IP Address or Port not received by service");
            return START_NOT_STICKY;
        }
        
        // Register the receiver which will continue the startup sequence after the user grants
        // access permissions to the USB device.
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_REQUEST_USB_PERMISSION);
        registerReceiver(mBroadcastReceiver, filter);
        
        // Find the USB device and ask the user for permission to use it.
        if (findDevice()) {
            postStatusMessage("Arduino device found, getting permission to access it");
            return START_STICKY;  // Device found. Keep the service running.
        } else {
            postStatusMessage("Arduino device not found");
            return START_NOT_STICKY;  // Device not found. Stop the service.
        }
    }
    
    @Override
    public void onDestroy() {
        postStatusMessage("Disconnecting");
        
        Log.d(TAG, "Receiver service onDestroy");
        unregisterReceiver(mBroadcastReceiver);
        
        if (mReceiverThread != null && mReceiverThread.isAlive()) {
            Log.d(TAG, "Interrupting receiver thread");
            mReceiverThread.interrupt();
        }
        
        if (mArduinoThread != null && mArduinoThread.isAlive()) {
            Log.d(TAG, "Interrupting Arduino thread");
            mArduinoThread.mHandler.getLooper().quit();
        }
        
        try {
            if (mUsbSerialPort != null) {
                mUsbSerialPort.close();
                mUsbSerialPort = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error while closing serial port: " + e.getMessage());
        }
        
        postStatusMessage("Disconnected");
        
        super.onDestroy();
    }

    /**
     * Finds the Arduino USB device and asks the user for permission to use it.
     */
    private boolean findDevice() {
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = 
                UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        
        // Find the Arduino device.
        for (UsbSerialDriver driver : availableDrivers) {
            UsbDevice device = driver.getDevice();
            int vendorId = device.getVendorId();
            int productId = device.getProductId();
            if ((vendorId == ARDUINO_USB_VENDOR_ID && productId == ARDUINO_UNO_USB_PRODUCT_ID) ||
                (vendorId == ARDUINO_CLONE_VENDOR_ID && productId == ARDUINO_CLONE_PRODUCT_ID)) {
                Log.d(TAG, "Found Arduino device!");
                
                mUsbSerialDriver = driver;
                
                getUsbPermission(new Intent(ACTION_REQUEST_USB_PERMISSION));

                Log.d(TAG, "findDevice() returns true");
                return true;
            }
        }
        
        Log.e(TAG, "No Arduino devices found");
        makeToast("No Arduino devices found");
        stopSelf();
        return false;
    }
    
    /**
     * Requests user's permission to access the device referred to by mUsbSerialDriver.
     * After the user grants (or denies) permission, processing continues by sending
     * the provided intent to mBroadcastReceiver.
     */
    void getUsbPermission(Intent intent) {
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
        UsbDevice device = mUsbSerialDriver.getDevice();
        usbManager.requestPermission(device, pendingIntent);
    }

    /**
     * Receives notification when the user grants permission to access the USB device.
     */
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(ACTION_REQUEST_USB_PERMISSION)) {
                if (!intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    makeToast("Permission denied while attempting to access USB device.");
                    Log.e(TAG, "User did not grant permission to use the device.");
                    stopSelf();
                    return;
                }
                Log.d(TAG, "User granted permission to use the device.");
                postStatusMessage("User granted permission to use the device.");
                if (openUsbSerialPort()) {
                    // Start Internet receiver thread and Arduino communicator thread.
                    makeToast("Connected to Arduino device");
                    Log.d(TAG, "Connected to Arduino device");
                    
                    synchronized (this) {
                        mArduinoThread = new ArduinoCommunicatorThread();
                        mArduinoThread.start();

                        mReceiverThread = new ReceiverThread();
                        mReceiverThread.start();
                    }
                }
            }
        }
        
    };
    
    /**
     * Completes the setup of the connection to the Arduino device,
     * after the user has granted permission to access it.
     * mUsbSerialDriver should already be set to the Arduino device.
     * Upon successful completion, mUsbSerialPort will point to the (opened) port.
     * Upon failure, mUsbSerialPort will be null.
     * @return true iff success
     */
    private boolean openUsbSerialPort() {
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        UsbDeviceConnection connection = usbManager.openDevice(mUsbSerialDriver.getDevice());
        if (connection == null) {
            Log.e(TAG, "Device disconnected or permission denied");
            makeToast("Device disconnected or permission denied");
            mUsbSerialPort = null;
            return false;
        }
        
        List<UsbSerialPort> ports = mUsbSerialDriver.getPorts();
        if (ports.size() == 0) {
            Log.e(TAG, "Device has 0 ports");
            makeToast("Device has 0 ports");
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
            makeToast("Could not open port");
            return false;
        }
        
        return true;
    }
    
    private volatile UsbSerialPort mUsbSerialPort;
    private volatile UsbSerialDriver mUsbSerialDriver;

    private volatile ReceiverThread mReceiverThread = null;
    private volatile ArduinoCommunicatorThread mArduinoThread = null;

    private class ReceiverThread extends Thread {
        
        public ReceiverThread() {
            super("receiver_thread");
        }

        @Override
        public void run() {
            Log.d(TAG, "Receiver thread started");
            
            if (!openSocket()) {
                // Failed to connect; message has already been shown to user. Thread aborts.
                return;
            }
            
            Log.d(TAG, "Connection opened");
            
            try {
                byte[] bytesRead = new byte[MESSAGE_LENGTH];
                while (true) {
                    if (interrupted()) {
                        Log.d(TAG, "Receiver thread interrupted. Stopping now.");
                        return;
                    }
                    
                    if (!receiveMessage(bytesRead)) {
                        // Connection failed and could not be restored.
                        // User has already been notified.
                        // Close thread.
                        return;
                    }

                    if (!validateMessage(bytesRead)) {
                        // User has already been notified
                        continue;
                    }
                    if (mArduinoThread == null || mArduinoThread.mHandler == null) {
                        Log.w(TAG, "Dropping message since Arduino thread is not ready yet.");
                        continue;
                    }
                    mArduinoThread.mHandler.obtainMessage(
                            ArduinoCommunicatorThread.MSG_SEND_TO_ANDROID, bytesRead).sendToTarget();

                    // Allocate a new message, so as not to overwrite the old one before
                    // the Arduino thread processes it.
                    bytesRead = new byte[MESSAGE_LENGTH];
                }
            } finally {
                closeSocket();
            }
        }

        private boolean receiveMessage(byte[] bytesRead) {
            for (int i = 0; i < MESSAGE_LENGTH; i++) {
                while (true) {
                    try {
                        // Checking for availability is required since inputStream.read() will block
                        // until more data is received, and remain blocked even if the thread is interrupted.
                        if (mInputStream.available() > 0) {
                            int inputByte = mInputStream.read();
                            if (inputByte < 0) {
                                Log.e(TAG, "End of stream reached. Attempting to re-open.");
                                closeSocket();
                                if (openSocket()) {
                                    // Successfully reconnected. Attempt to receive a new message.
                                    return receiveMessage(bytesRead);
                                } else {
                                    Log.e(TAG, "Failed to re-open stream. Aborting thread.");
                                    makeToast("Connection closed and could not be re-opened.");
                                    return false;
                                }
                            }
                            bytesRead[i] = (byte) inputByte;
                            break;
                        } else {
                            Thread.sleep(200);
                        }
                    } catch (IOException e) {
                        Log.e(TAG, e.getMessage());
                        closeSocket();
                        if (openSocket()) {
                            // Successfully reconnected. Attempt to receive a new message.
                            return receiveMessage(bytesRead);
                        } else {
                            Log.e(TAG, "Failed to re-open stream. Aborting thread.");
                            makeToast("Connection failed and could not be re-opened.");
                            return false;
                        }
                    } catch (InterruptedException e) {
                        Log.d(TAG, "Receiver thread interrupted. Stopping thread.");
                        return false;
                    }
                }
            }
            return true;
        }

        private boolean openSocket() {
            try {
                mSocket = new Socket(mIpAddress, mIpPort);
                mInputStream = mSocket.getInputStream();
            } catch (Exception e) {
                makeToast("Cannot connect to controller");
                Log.e(TAG, "Error in server socket: " + e.getMessage());
                if (mSocket != null) {
                    try {
                        mSocket.close();
                    } catch (IOException e1) {
                        Log.e(TAG, "Can't close socket: " + e1.getMessage());
                    }
                }
                return false;
            }
            
            return true;
        }
        
        private void closeSocket() {
            try {
                if (mInputStream != null) {
                    mInputStream.close();
                }
                if (mSocket != null) {
                    mSocket.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Can't close inputStream or socket: " + e);
            }
        }

        private boolean validateMessage(byte[] bytesRead) {
            if (bytesRead.length != MESSAGE_LENGTH) {
                notifyInvalidMessage(bytesRead);
                return false;
            }
            
            if (bytesRead[0] != 0x7f || bytesRead[1] != 0x7f || 
                    bytesRead[2] != (byte)0x80 || bytesRead[3] != (byte)0x80) {
                notifyInvalidMessage(bytesRead);
                return false;
            }
            
            final short leftMotorPower = (short) (((short)bytesRead[4] & 0xff) | (((short) bytesRead[5]) << 8));
            final short rightMotorPower = (short) (((short)bytesRead[6] & 0xff) | (((short) bytesRead[7]) << 8));
            
            final boolean valid = Math.abs(leftMotorPower) <= 400 && Math.abs(rightMotorPower) <= 400;
            
            if (valid) {
                postStatusMessage("L=" + leftMotorPower + ",  R=" + rightMotorPower);
                Log.d(TAG, "Received valid message: L=" + leftMotorPower + ", R=" + rightMotorPower);
            }
            
            return valid;
        }
        
        private void notifyInvalidMessage(byte[] bytesRead) {
            Log.w(TAG, "Invalid message received: " + bytesToHex(bytesRead));
            postStatusMessage("Invalid message received: " + bytesToHex(bytesRead));
        }

        private Socket mSocket;
        private InputStream mInputStream;
        
        private static final int MESSAGE_LENGTH = 8;
    }

    private class ArduinoCommunicatorThread extends Thread {

        public ArduinoCommunicatorThread() {
            super("arduino_communicator");
        }

        @SuppressLint("HandlerLeak")
        @Override
        public void run() {
            Looper.prepare();
            
            mHandler = new Handler() {

                @Override
                public void handleMessage(Message msg) {
                    if (msg.what == MSG_SEND_TO_ANDROID) {
                        final byte[] dataToSend = (byte[]) msg.obj;
                        
                        if (mUsbSerialPort == null) {
                            Log.w(TAG, "Can't write to USB port: Port not opened");
                            return;
                        } 
                        
                        try {
                            mUsbSerialPort.write(dataToSend, 200);
                        } catch (IOException e) {
                            Log.w(TAG, "Failed to write to USB serial port: " + e.getMessage());
                            makeToast("Disconnected from Arduino, please reconnect cable and click Connect.");
                        }
                    } else {
                        Log.w(TAG, "Unrecognized message: " + msg.toString());
                    }
                }
                
            };

            Looper.loop();
            
            Log.d(TAG, "Arduino thread done.");
        }
        
        public volatile Handler mHandler;
        
        public static final int MSG_SEND_TO_ANDROID = 10;
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
    
    void makeToast(final String toastText) {
        Context context = getBaseContext();
        new Handler(context.getMainLooper()).post(new Runnable() {

            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), toastText, Toast.LENGTH_SHORT).show();
            }
            
        });
    }
    
    void postStatusMessage(final String statusMessage) {
        Intent intent = new Intent(INTENT_ACTION_STATUS_UPDATE);
        intent.putExtra(INTENT_EXTRA_STATUS_MESSAGE, statusMessage);
        mLocalBroadcastManager.sendBroadcast(intent);
    }
    
    final static String INTENT_ACTION_STATUS_UPDATE = "zvikabh.rccarreceiver.INTENT_STATUS_UPDATE"; 
    final static String INTENT_EXTRA_STATUS_MESSAGE = "zvikabh.rccarreceiver.INTENT_EXTRA_STATUS_MESSAGE";
    
    private final LocalBroadcastManager mLocalBroadcastManager = LocalBroadcastManager.getInstance(this);
    
    private String mIpAddress = null;
    private int mIpPort = -1;

    private static final String TAG = "RCCarReceiverService";

    static final String ACTION_REQUEST_USB_PERMISSION = "zvikabh.rccarreceiver.REQUEST_USB_PERMISSION";
    
    // Values used in Arduino Communicator
    static final int ARDUINO_USB_VENDOR_ID = 0x2341;
    static final int ARDUINO_UNO_USB_PRODUCT_ID = 0x0001;

    // Values used by my Arduino clone
    static final int ARDUINO_CLONE_VENDOR_ID = 0x0403;  // 1027
    static final int ARDUINO_CLONE_PRODUCT_ID = 0x6001;  // 24577
}
