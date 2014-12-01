package zvikabh.rccarcontroller;

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
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class ConnectToProxyActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect_to_proxy);
        
        ((Button) findViewById(R.id.buttonConnect)).setOnClickListener(new OnClickListener() {
            
            @Override
            public void onClick(View v) {
                EditText editTextIP = (EditText) findViewById(R.id.editTextIP);
                EditText editTextPort = (EditText) findViewById(R.id.editTextPort);
                
                String address = editTextIP.getText().toString();
                int port;
                try {
                    port = Integer.parseInt(editTextPort.getText().toString());
                } catch(NumberFormatException e) {
                    Toast.makeText(ConnectToProxyActivity.this, "Invalid port specified. Please try again.", Toast.LENGTH_LONG).show();
                    return;
                }
                
                // Start the connection handler service, which will in turn start the 
                // ControllerActivity once a connection is established.
                // The service is unbound from this activity in onDestroy, but remains running until
                // stopService is called when ControllerActivity is destroyed.
                startService(new Intent(ConnectToProxyActivity.this, ConnectionHandlerService.class));
                
                Intent intent = new Intent(ConnectToProxyActivity.this, ConnectionHandlerService.class);
                intent.putExtra(ConnectionHandlerService.ESTABLISH_CONNECTION_TYPE, 
                        ConnectionHandlerService.EstablishConnectionTypes.OUTGOING);
                intent.putExtra(ConnectionHandlerService.OUTGOING_CONNECTION_ADDRESS, address);
                intent.putExtra(ConnectionHandlerService.OUTGOING_CONNECTION_PORT, port);
                bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mServiceConnection != null) {
            unbindService(mServiceConnection);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.connect_to_proxy, menu);
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
            Log.d(TAG, "ConnectionHandlerService bound to ConnectToProxyActivity");
            mConnectionBinder = (ConnectionHandlerService.ConnectionBinder) binder;
            mConnectionBinder.setOnConnectedListener(new ConnectionHandlerService.OnConnectedListener() {
                
                @Override
                public void onConnected() {
                    // Start the controller activity
                    Log.d(TAG, "Starting ControllerActivity");
                    Intent intent = new Intent(ConnectToProxyActivity.this, ControllerActivity.class);
                    startActivity(intent);
                }
                
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "ConnectionHandlerService disconnected from ConnectToProxyActivity");
            mConnectionBinder = null;
        }
        
    };

    private ConnectionHandlerService.ConnectionBinder mConnectionBinder;
    
    private static final String TAG = "ConnectToProxyActivity";
}
