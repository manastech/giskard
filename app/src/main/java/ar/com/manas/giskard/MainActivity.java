package ar.com.manas.giskard;

import java.io.IOException;
import java.net.InetAddress;
import java.text.DecimalFormat;

import com.codeminders.ardrone.ARDrone;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
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

    ControllerThread ctrThread;

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
                ctrThread.start();
            }
        });

        connectButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startARDroneConnection(connectButton);
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

    private void startARDroneConnection(final Button btnConnect) {
        WifiManager connManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        
        if (connManager.isWifiEnabled()) {
            state.setTextColor(Color.RED);
            state.setText("Connecting..." +  connManager.getConnectionInfo().getSSID());
            (new DroneStarter()).execute(MainActivity.drone); 
        }
    }       
    
    private void droneOnConnected() {
        Log.d(TAG, "DRONE CONNECTED");

        ctrThread = new ControllerThread(drone);
        ctrThread.setName("Controll Thread");

        state.setTextColor(Color.GREEN);
        state.setText("Connected");

        setDefaultSettings();

        if (null != ctrThread) {
            ctrThread.setDrone(drone);
        }
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
        if (null != ctrThread && ctrThread.isAlive()) { 
            //handle this
        }
        if (null != drone) {
            try {
                drone.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "failed to stop ar. drone", e);
            }
        }
    }

private class DroneStarter extends AsyncTask<ARDrone, Integer, Boolean> {
    
    @Override
    protected Boolean doInBackground(ARDrone... drones) {
        ARDrone drone = drones[0];
        try {
            drone = new ARDrone(InetAddress.getByAddress(Giskard.DEFAULT_DRONE_IP), 10000, 60000);
            MainActivity.drone = drone;
            drone.connect();
            drone.clearEmergencySignal();
            drone.trim();
            drone.waitForReady(Giskard.CONNECTION_TIMEOUT);
            drone.playLED(1, 10, 4);
            drone.selectVideoChannel(ARDrone.VideoChannel.HORIZONTAL_ONLY);
            drone.setCombinedYawMode(true);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to connect to drone", e);
            try {
                drone.clearEmergencySignal();
                drone.clearImageListeners();
                drone.clearNavDataListeners();
                drone.clearStatusChangeListeners();
                drone.disconnect();
            } catch (Exception ex) {
                Log.e(TAG, "Failed to clear drone state", ex);
            }
          
        }
        return false;
    }

    protected void onPostExecute(Boolean success) {
        if (success) {
            droneOnConnected();
        } else {
            state.setTextColor(Color.RED);
            state.setText("Error");
            connectButton.setEnabled(true);
        }
    }
   }

    public static String DRONE_MAX_YAW_PARAM_NAME = "control:control_yaw";
    public static String DRONE_MAX_VERT_SPEED_PARAM_NAME = "control:control_vz_max";
    public static String DRONE_MAX_EULA_ANGLE = "control:euler_angle_max";
    public static String DRONE_MAX_ALTITUDE = "control:altitude_max";
    DecimalFormat twoDForm = new DecimalFormat("#.##");

    private void setDefaultSettings() {
        setDroneParam(DRONE_MAX_ALTITUDE, String.valueOf(Math.round(1.5f * 1000)));
        setDroneParam(DRONE_MAX_EULA_ANGLE, twoDForm.format(6f * Math.PI / 180f).replace(',', '.'));
        setDroneParam(DRONE_MAX_VERT_SPEED_PARAM_NAME, String.valueOf(Math.round(1f * 1000)));
        setDroneParam(DRONE_MAX_YAW_PARAM_NAME, twoDForm.format(50f * Math.PI / 180f).replace(',', '.'));
    }
    
    private void setDroneParam(final String name, final String value) {
     new Thread(new Runnable() {  
            @Override
            public void run() {
                try {
                    drone.setConfigOption(name, value);
                    Log.d(TAG, "Drone parameter (" + name + ") is SET to value: " + value);
                } catch (IOException ex) {
                    Log.e(TAG, "Failed to set drone parameter (" + name + ") to value: " + value , ex);
                }
                
            }
        }).start();
    }

}
