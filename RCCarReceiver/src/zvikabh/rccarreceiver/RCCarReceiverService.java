package zvikabh.rccarreceiver;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
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
import android.util.Log;
import android.widget.Toast;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

public class RCCarReceiverService extends Service {
    
    public static final int PORT = 14902;

    public RCCarReceiverService() {
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        synchronized (this) {
            if (mReceiverMasterThread != null) {
                Log.w(TAG, "Receiver service already running");
                return START_STICKY;
            } 
        }
        
        // Register the receiver which will resume the startup sequence after the user grants
        // access permissions to the USB device.
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        registerReceiver(mBroadcastReceiver, filter);

        // Find the USB device and ask the user for permission to use it.
        if (findDevice()) {
            return START_STICKY;  // Device found. Keep the service running.
        } else {
            return START_NOT_STICKY;  // Device not found. Stop the service.
        }
    }
    
    @Override
    public void onDestroy() {
        unregisterReceiver(mBroadcastReceiver);
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
                
                // Request user's permission to access the device.
                // Processing continues in mBroadcastReceiver after user grants permission.
                PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
                usbManager.requestPermission(device, pendingIntent);
                return true;
            }
        }
        
        Log.e(TAG, "No Arduino devices found");
        Toast.makeText(this, "No Arduino devices found", Toast.LENGTH_LONG).show();
        stopSelf();
        return false;
    }

    /**
     * Receives notification when the user grants permission to access the USB device.
     */
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(ACTION_USB_PERMISSION)) {
                if (!intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    Log.e(TAG, "User did not grant permission to use the device.");
                    stopSelf();
                    return;
                }
                Log.d(TAG, "User granted permission to use the device.");
                if (openUsbSerialPort()) {
                    // Start Internet receiver thread and Arduino communicator thread.
                    synchronized (this) {
                        mArduinoThread = new ArduinoCommunicatorThread();
                        mArduinoThread.start();

                        mReceiverMasterThread = new ReceiverMasterThread();
                        mReceiverMasterThread.start();
                    }
                }
            }
        }
        
    };
    
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
        
        return true;
    }
    
    private volatile UsbSerialPort mUsbSerialPort;
    private volatile UsbSerialDriver mUsbSerialDriver;

    private volatile ReceiverMasterThread mReceiverMasterThread = null;
    private volatile ArduinoCommunicatorThread mArduinoThread = null;

    private class ReceiverMasterThread extends Thread {
        
        public ReceiverMasterThread() {
            super("receiver_master_thread");
        }

        @Override
        public void run() {
            Log.d(TAG, "Receiver thread started");
            
            try {
                mServerSocket = new ServerSocket(PORT);
                while(true) {
                    Socket socket = mServerSocket.accept();
                    new ReceiverSocketThread(socket, mNumSockets++).run();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error in server socket: " + e.toString());
            }
        }
        
        private ServerSocket mServerSocket;
        private int mNumSockets;
    }
    
    private class ReceiverSocketThread extends Thread {
        
        public ReceiverSocketThread(Socket socket, int nSocket) {
            super("reciever_socket_" + nSocket);
            mSocket = socket;
        }

        @Override
        public void run() {
            try {
                InputStream inputStream = mSocket.getInputStream();
                byte[] bytesRead = new byte[MESSAGE_LENGTH];
                while (true) {
                    for (int i = 0; i < MESSAGE_LENGTH; i++) {
                        int inputByte = inputStream.read();
                        if (inputByte < 0) {
                            Log.i(TAG, "End of stream reached - closing thread");
                            return;
                        }
                        bytesRead[i] = (byte) inputByte;
                    }
                    
                    if (!validateMessage(bytesRead)) {
                        Log.w(TAG, "Invalid message received: " + bytesToHex(bytesRead));
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
            } catch (IOException e) {
                Log.e(TAG, "ReceiverSocketThread failed: " + e.toString());
            }
        }
        
        private boolean validateMessage(byte[] bytesRead) {
            if (bytesRead.length != 4) {
                return false;
            }
            
            final short leftMotorPower = (short) (((short)bytesRead[0] & 0xff) | (((short) bytesRead[1]) << 8));
            final short rightMotorPower = (short) (((short)bytesRead[2] & 0xff) | (((short) bytesRead[3]) << 8));
            
            return Math.abs(leftMotorPower) <= 400 && Math.abs(rightMotorPower) <= 400;
        }

        private final Socket mSocket;
        private static final int MESSAGE_LENGTH = 4;
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
                    Log.d(TAG, "Received message: " + msg.what);
                    
                    if (msg.what == MSG_SEND_TO_ANDROID) {
                        final byte[] dataToSend = (byte[]) msg.obj;
                        
                        if (mUsbSerialPort == null) {
                            Log.w(TAG, "Can't write to USB: Port not opened.");
                        } else {
                            try {
                                mUsbSerialPort.write(dataToSend, 200);
                            } catch (IOException e) {
                                Log.w(TAG, "Failed to write to USB serial port: " + e);
                            }
                        }
                    } else {
                        Log.w(TAG, "Unrecognized message: " + msg.toString());
                    }
                }
                
            };

            Looper.loop();
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

    private static final String TAG = "RCCarReceiverService";

    private static final String ACTION_USB_PERMISSION = "zvikabh.rccarcontroller.USB_PERMISSION";
    
    // Values used in Arduino Communicator
    private static final int ARDUINO_USB_VENDOR_ID = 0x2341;
    private static final int ARDUINO_UNO_USB_PRODUCT_ID = 0x0001;

    // Values used by my Arduino clone
    private static final int ARDUINO_CLONE_VENDOR_ID = 0x0403;  // 1027
    private static final int ARDUINO_CLONE_PRODUCT_ID = 0x6001;  // 24577
}
