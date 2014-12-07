package zvikabh.rccarcontroller;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

public class ConnectionHandlerService extends Service {

    @Override
    public void onCreate() {
        Log.d(TAG, "ConnectionHandlerService created");
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "ConnectionHandlerService destroyed");
        if (isCommThreadAlive()) {
            mCommThread.interrupt();
        }
    }
    
    public boolean isCommThreadAlive() {
        return mCommThread != null && mCommThread.isAlive();
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "ConnectionHandlerService.onBind");
        
        EstablishConnectionTypes bindingMode = (EstablishConnectionTypes) intent.getSerializableExtra(ESTABLISH_CONNECTION_TYPE);
        switch(bindingMode) {
        case NONE:
            if (!isCommThreadAlive()) {
                Log.e(TAG, "Binding to service with ESTABLISH_CONNECTION_TYPE=NONE, but connection has not yet been created.");
                return null;
            }
            break;
            
        case INCOMING:
            if (isCommThreadAlive()) {
                Log.e(TAG, "Starting incoming connection handler, but comm thread is already running.");
                return null;
            }
            mCommThread = new IncomingConnectionThread();
            mCommThread.start();
            break;
            
        case OUTGOING:
            if (isCommThreadAlive()) {
                Log.e(TAG, "Starting outgoing connection handler, but comm thread is already running.");
                return null;
            }
            final String address = intent.getStringExtra(OUTGOING_CONNECTION_ADDRESS);
            final int port = intent.getIntExtra(OUTGOING_CONNECTION_PORT, 5006);
            mCommThread = new OutgoingConnectionThread(address, port);
            mCommThread.start();
            break;
        }
        
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    public class ConnectionBinder extends Binder {
        
        public boolean isConnected() {
            return ConnectionHandlerService.this.mCommThread != null &&
                    ConnectionHandlerService.this.mCommThread.isConnected();
        }
        
        public boolean sendData(byte[] data) {
            if (!isConnected()) {
                Log.e(TAG, "Attempting to send data when socket is not connected");
                return false;
            }
            ConnectionHandlerService.this.mCommThread.mHandler.obtainMessage(SEND_DATA, data).sendToTarget();
            return true;
        }
        
        public void setOnConnectedListener(OnConnectedListener listener) {
            mListener = listener;
        }
        
        void notifyConnected() {
            if (mListener != null) {
                mListener.onConnected();
            }
        }
        
        private OnConnectedListener mListener;
    }

    /**
     * Interface for receiving a notification when the service has connected to a remote client.
     */
    interface OnConnectedListener {
        /**
         * Called when the service has connected to a remote client.
         */
        void onConnected();
    }

    /**
     * Abstract base class for handling the connection which sends instructions to the receiver.
     * Implementations of this class can use either incoming connections (as in the direct connection mode)
     * or outgoing connections (as in the proxy mode).
     */
    private abstract class ConnectionThread extends Thread {

        @SuppressLint("HandlerLeak")
        @Override
        public void run() {
            Looper.prepare();
            
            mHandler = new Handler() {
                public void handleMessage(Message msg) {
                    if (msg.what == SEND_DATA) {
                        final byte[] dataToSend = (byte[]) msg.obj;
                        try {
                            if (isConnectionShutdown()) {
                                makeToast("Connection has been shut down.");
                                mHandler.getLooper().quit();
                            }
                            Log.d(TAG, "Sending message: " + bytesToHex(dataToSend));
                            mOutputStream.write(dataToSend);
                        } catch (IOException e) {
                            makeToast("Connection lost, attempting to reconnect.");
                            Log.e(TAG, "IOException while writing data to socket stream: " + e.getMessage());
                            closeConnection();
                            int i;
                            for (i = 0; !createConnection(); ++i) {
                                Log.e(TAG, "Connection still lost, will attempt to reconnect again in 5 seconds (attempt " + i + ")");
                                closeConnection();
                                try {
                                    Thread.sleep(5000);
                                } catch (InterruptedException e1) {
                                    Log.e(TAG, "Thread interrupted while attempting to reopen connection: " + e.getMessage());
                                    return;
                                }
                            }
                            Log.i(TAG, "Connection restored after " + i + " attempts");
                            makeToast("Connection restored!");
                        }
                    }
                }
            };
            
            if (!createConnection()) {
                makeToast("Failed to connect.");
                stopSelf();
                return;
            }
            
            mIsConnected = true;
            mBinder.notifyConnected();

            Looper.loop();
            
            closeConnection();
        }
        
        public boolean isConnected() {
            return mIsConnected;
        }
        
        protected boolean isConnectionShutdown() {
            if (mSocket == null) {
                return true;
            }
            return mSocket.isOutputShutdown();
        }
        
        protected abstract boolean createConnection();
        protected abstract void closeConnection();
        
        protected Socket mSocket;
        protected OutputStream mOutputStream;

        public volatile Handler mHandler = null;
        private volatile boolean mIsConnected = false;
    }
    
    /**
     * Handles communication with an incoming connection.
     */
    private class IncomingConnectionThread extends ConnectionThread {

        @Override
        protected boolean createConnection() {
            try {
                mServerSocket = new ServerSocket(PORT);
                Log.d(TAG, "Waiting for incoming connection");
                mSocket = mServerSocket.accept();
                Log.d(TAG, "Incoming connection accepted");
                mOutputStream = mSocket.getOutputStream();
            } catch (IOException e) {
                makeToast("Failed to create incoming connection.");
                Log.e(TAG, "Failed to create incoming connection: " + e.getMessage());
                return false;
            }
            return true;
        }

        @Override
        protected void closeConnection() {
            try {
                if (mOutputStream != null)
                    mOutputStream.close();
                if (mSocket != null)
                    mSocket.close();
                if (mServerSocket != null)
                    mServerSocket.close();    
            } catch (IOException e) {
                Log.e(TAG, "IOException while freeing resources: " + e.getMessage());
            }
            mOutputStream = null;
            mSocket = null;
            mServerSocket = null;
        }
        
        private ServerSocket mServerSocket;
    }
    
    /**
     * Handles communication with an outgoing connection.
     * The target IP address and port are specified in the constructor.
     */
    private class OutgoingConnectionThread extends ConnectionThread {
        public OutgoingConnectionThread(String address, int port) {
            mAddress = address;
            mPort = port;
        }
        
        @Override
        protected boolean createConnection() {
            try {
                mSocket = new Socket(mAddress, mPort);
                mOutputStream = mSocket.getOutputStream();
            } catch(Exception e) {
                makeToast("Could not create connection to " + mAddress + ":" + mPort);
                Log.e(TAG, "Could not create connection to " + mAddress + ":" + mPort + ":" + e.getMessage());
                return false;
            }
            return true;
        }
        
        @Override
        protected void closeConnection() {
            try {
                if (mOutputStream != null)
                    mOutputStream.close();
                if (mSocket != null)
                    mSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "IOException while freeing resources: " + e.getMessage());
            }
            mOutputStream = null;
            mSocket = null;
        }
        
        private String mAddress;
        private int mPort;
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
    
    public static final int PORT = 1893;
    public static final int SEND_DATA = 10;
    
    public static final String ESTABLISH_CONNECTION_TYPE = "zvikabh.rccarcontroller.ESTABLISH_CONNECTION_TYPE";
    public static enum EstablishConnectionTypes {
        NONE,       // Do not establish a new connection. A previous bind should have created the connection.
        INCOMING,   // Establish an incoming connection (used for a direct receiver-controller connection).
        OUTGOING    // Establish an outgoing connection (used for connecting through a proxy).
                    // The outgoing address and port should be specified in OUTGOING_CONNECTION_ADDRESS and
                    // OUTGOING_CONNECTION_PORT extras, respectively. 
    }
    
    public static final String OUTGOING_CONNECTION_ADDRESS = "zvikabh.rccarcontroller.OUTGOING_CONNECTION_ADDRESS";
    public static final String OUTGOING_CONNECTION_PORT = "zvikabh.rccarcontroller.OUTGOING_CONNECTION_PORT";
    
    private static final String TAG = "ConnectionHandlerService";
    
    private final ConnectionBinder mBinder = new ConnectionBinder();
    private ConnectionThread mCommThread;
}
