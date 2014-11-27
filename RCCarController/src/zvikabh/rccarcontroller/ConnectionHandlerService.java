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
        mCommThread.start();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "ConnectionHandlerService destroyed");
        if (mCommThread != null && mCommThread.isAlive()) {
            mCommThread.interrupt();
        }
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "ConnectionHandlerService.onBind");
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
                Log.e(TAG, "Attempting to send data before socket is connected");
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
     * Waits for an incoming connection on port PORT.
     * When a connection is received, accepts it, sends the socket to the main thread, and terminates.
     */
    private class ConnectionThread extends Thread {

        @SuppressLint("HandlerLeak")
        @Override
        public void run() {
            try {
                Looper.prepare();
                
                mHandler = new Handler() {
                    public void handleMessage(Message msg) {
                        if (msg.what == SEND_DATA) {
                            final byte[] dataToSend = (byte[]) msg.obj;
                            try {
                                if (mSocket.isOutputShutdown()) {
                                    // TODO reestablish connection?
                                }
                                Log.d(TAG, "Sending message: " + bytesToHex(dataToSend));
                                mOutputStream.write(dataToSend);
                            } catch (IOException e) {
                                Log.e(TAG, "IOException while writing data to socket stream: " + e.getMessage());
                                makeToast("Disconnected from RC receiver");
                                mHandler.getLooper().quit();
                            }
                        }
                    }
                };
                
                mServerSocket = new ServerSocket(PORT);
                mSocket = mServerSocket.accept();
                mOutputStream = mSocket.getOutputStream();
                
                mIsConnected = true;
                mBinder.notifyConnected();

                Looper.loop();
            } catch (IOException e) {
                Log.e(TAG, "IncomingConnectionWaiter received IOException: " + e.getMessage());
            }
            finally {
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
            }
        }
        
        public boolean isConnected() {
            return mIsConnected;
        }
        
        public volatile Handler mHandler = null;
        private volatile boolean mIsConnected = false;
        private ServerSocket mServerSocket;
        private Socket mSocket;
        private OutputStream mOutputStream;
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
    
    private static final String TAG = "ConnectionHandlerService";
    
    private final ConnectionBinder mBinder = new ConnectionBinder();
    private final ConnectionThread mCommThread = new ConnectionThread();
}
