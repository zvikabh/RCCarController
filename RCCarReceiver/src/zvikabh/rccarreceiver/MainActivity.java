package zvikabh.rccarreceiver;

import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBarActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;


public class MainActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        mStatusTextView = (TextView) findViewById(R.id.textViewStatus);
        
        Button buttonConnect = (Button) findViewById(R.id.buttonConnect);
        buttonConnect.setOnClickListener(new ConnectClickListener());
        
        Button buttonDisconnect = (Button) findViewById(R.id.buttonDisconnect);
        buttonDisconnect.setOnClickListener(new OnClickListener() {
            
            @Override
            public void onClick(View view) {
                stopService(new Intent(getApplicationContext(), RCCarReceiverService.class));
            }
        });
        
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(RCCarReceiverService.INTENT_ACTION_STATUS_UPDATE);
        mBroadcastManager.registerReceiver(mStatusUpdateReceiver, intentFilter);
    }
    
    
    
    @Override
    protected void onDestroy() {
        mBroadcastManager.unregisterReceiver(mStatusUpdateReceiver);
        super.onDestroy();
    }

    private class ConnectClickListener implements OnClickListener {
        
        @Override
        public void onClick(View button) {
            String ipAddress = ((EditText) findViewById(R.id.editTextControllerIP)).getText().toString();
            int port;
            try {
                port = Integer.parseInt(((EditText) findViewById(R.id.editTextControllerPort)).getText().toString());
                if (port < 0 || port > 32767) {
                    throw new NumberFormatException("Port value out of range");
                }
            } catch (NumberFormatException e) {
                Log.d(TAG, "Invalid port specified: " + e);
                Toast.makeText(MainActivity.this, "Invalid port specified. Please try again.", Toast.LENGTH_LONG).show();
                return;
            }
            
            // Stop any previously running versions of the service.
            stopService(new Intent(getApplicationContext(), RCCarReceiverService.class));
            
            // Start the service.
            Intent serviceIntent = new Intent(getApplicationContext(), RCCarReceiverService.class);
            serviceIntent.putExtra(INTENT_EXTRA_IP_ADDRESS, ipAddress);
            serviceIntent.putExtra(INTENT_EXTRA_PORT, port);
            startService(serviceIntent);
        }
        
    }
    
    private BroadcastReceiver mStatusUpdateReceiver = new BroadcastReceiver() {
        
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(RCCarReceiverService.INTENT_ACTION_STATUS_UPDATE)) {
                final String status = intent.getStringExtra(RCCarReceiverService.INTENT_EXTRA_STATUS_MESSAGE);
                mStatusTextView.setText(status);
            }
        }
    };

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
    
    private LocalBroadcastManager mBroadcastManager = LocalBroadcastManager.getInstance(this);
    private TextView mStatusTextView;
    
    private static final String TAG = "rccarreceiver.MainActivity";
    
    static final String INTENT_EXTRA_IP_ADDRESS = "zvikabh.rccarreceiver.intent.extra.IP_ADDRESS";
    static final String INTENT_EXTRA_PORT = "zvikabh.rccarreceiver.intent.extra.PORT";
}
