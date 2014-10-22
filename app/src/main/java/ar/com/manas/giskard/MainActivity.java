package ar.com.manas.giskard;

import java.io.IOException;
import java.text.DecimalFormat;

import com.codeminders.ardrone.ARDrone;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity {
    static ARDrone drone;
    
    TextView state;
    Button connectButton;
    Button btnTakeOffOrLand;
    Button resetWatchdog;
    
    private static final String TAG = "AR.Drone";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setJavaSystemProperties();
        bindControls();
        setupButtons();
    }

    private void setupButtons() {
        btnTakeOffOrLand.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
            new Thread(new TakeOffHoverAndLand(drone)).start();
            }
        });

        connectButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startARDroneConnection();
            }
        });
    }

    private void bindControls() {
        state = (TextView) findViewById(R.id.state);
        connectButton = (Button) findViewById(R.id.connect);

        btnTakeOffOrLand = (Button) findViewById(R.id.takeOffOrland);

        resetWatchdog = (Button) findViewById(R.id.resetWatchdog);
    }

    private void setJavaSystemProperties() {
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("java.net.preferIPv6Addresses", "false");
    }

    private void startARDroneConnection() {
        WifiManager connManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        
        if (connManager.isWifiEnabled()) {
            state.setTextColor(Color.RED);
            state.setText("Connecting..." +  connManager.getConnectionInfo().getSSID());
            (new DroneStarter(this)).execute(MainActivity.drone);
        }
    }       
    
    private void droneOnConnected() {
        Log.d(TAG, "DRONE CONNECTED");

        state.setTextColor(Color.GREEN);
        state.setText("Connected");

        Giskard.setDefaultSettings(drone);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (null != drone) {
            drone.resumeNavData();
            drone.resumeVideo();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (null != drone) {
            drone.pauseNavData();
            drone.pauseVideo();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseResources();
    }

    private void releaseResources() {
        if (null != drone) {
            try {
                drone.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "failed to stop ar. drone", e);
            }
        }
    }

    public void onDroneStarterFinished(Boolean success) {
        if (success) {
            droneOnConnected();
        } else {
            state.setTextColor(Color.RED);
            state.setText("Error");
            connectButton.setEnabled(true);
        }
    }
}
