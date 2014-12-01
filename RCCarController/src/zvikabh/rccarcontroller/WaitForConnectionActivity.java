package zvikabh.rccarcontroller;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class WaitForConnectionActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wait_for_connection);

        mIPAddressesView = (ListView) findViewById(R.id.ipAddressesList);
        findIPAddresses();
        
        // Start the connection handler service, which will in turn start the 
        // ControllerActivity once a connection is established.
        // The service is unbound from this activity in onDestroy, but remains running until
        // stopService is called when ControllerActivity is destroyed.
        startService(new Intent(this, ConnectionHandlerService.class));
        bindService(new Intent(this, ConnectionHandlerService.class), mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mServiceConnection != null) {
            unbindService(mServiceConnection);
        }
    }

    private void findIPAddresses() {
        ArrayList<NetworkInterface> networkInterfaces = new ArrayList<NetworkInterface>();
        final ArrayAdapter<String> displayData = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
        try {
            networkInterfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
        } catch (SocketException e) {
            Log.e(TAG, "Cannot get network interfaces: " + e.getMessage());
            displayData.add("Cannot find IP addresses");
        }
        
        for (NetworkInterface networkInterface : networkInterfaces) {
            for (InetAddress ipAddress : Collections.list(networkInterface.getInetAddresses())) {
                if (ipAddress.isLoopbackAddress()) {
                    continue;
                }
                displayData.add(ipAddress.getHostAddress() + ":" + ConnectionHandlerService.PORT);
            }
        }
        
        mIPAddressesView.setAdapter(displayData);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.wait_for_connection, menu);
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
    
    private ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            Log.d(TAG, "ConnectionHandlerService bound to WaitForConnectionActivity");
            mConnectionBinder = (ConnectionHandlerService.ConnectionBinder) binder;
            mConnectionBinder.setOnConnectedListener(new ConnectionHandlerService.OnConnectedListener() {
                
                @Override
                public void onConnected() {
                    // Start the controller activity
                    Log.d(TAG, "Starting ControllerActivity");
                    Intent intent = new Intent(WaitForConnectionActivity.this, ControllerActivity.class);
                    startActivity(intent);
                }
                
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "ConnectionHandlerService disconnected from WaitForConnectionActivity");
            mConnectionBinder = null;
        }
        
    };

    private ListView mIPAddressesView;
    private ConnectionHandlerService.ConnectionBinder mConnectionBinder;

    private static final String TAG = "WaitForConnectionActivity";
}
