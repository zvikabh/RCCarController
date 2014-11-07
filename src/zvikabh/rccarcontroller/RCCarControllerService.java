package zvikabh.rccarcontroller;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

public class RCCarControllerService extends Service {
	
	private static final String TAG = "RCCarControllerService";
	static final String SEND_DATA_INTENT = "zvikabh.rccarcontroller.intent.SEND_DATA";
	static final String DATA_EXTRA = "zvikabh.rccarcontroller.extra.DATA";

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

                // TODO: Send data in sender thread.
                // mSenderThread.mHandler.obtainMessage(10, dataToSend).sendToTarget();
        	}
        }		
	};

}
