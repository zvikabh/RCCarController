package zvikabh.rccarreceiver;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import android.support.v7.app.ActionBarActivity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;


public class MainActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        mIPAddressesView = (ListView) findViewById(R.id.ipAddressesList);
        findIPAddresses();
        
        Intent serviceIntent = new Intent(getApplicationContext(), RCCarReceiverService.class);
        startService(serviceIntent);
    }

    private void findIPAddresses() {
        ArrayList<NetworkInterface> networkInterfaces = new ArrayList<NetworkInterface>();
        final ArrayAdapter<String> displayData = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
        try {
            networkInterfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
        } catch (SocketException e) {
            Log.e(TAG, "Cannot get network interfaces: " + e);
            displayData.add("Cannot find IP addresses");
        }
        
        for (NetworkInterface networkInterface : networkInterfaces) {
            Log.d(TAG, "Found network interface: " + networkInterface.getDisplayName());
            for (InetAddress ipAddress : Collections.list(networkInterface.getInetAddresses())) {
                if (ipAddress.isLoopbackAddress()) {
                    continue;
                }
                Log.d(TAG, "Found ip address: " + ipAddress.getHostAddress());
                displayData.add(ipAddress.getHostAddress() + ":" + RCCarReceiverService.PORT);
            }
        }
        
        mIPAddressesView.setAdapter(displayData);
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
    
    private ListView mIPAddressesView;
    
    private static final String TAG = "rccarreceiver.MainActivity";
}
