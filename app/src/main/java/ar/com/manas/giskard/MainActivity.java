package ar.com.manas.giskard;

import java.io.IOException;
import java.net.InetAddress;
import java.text.DecimalFormat;

import com.codeminders.ardrone.ARDrone;
import com.codeminders.ardrone.ARDrone.State;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity {
    
    private static final long CONNECTION_TIMEOUT = 10000;

    final static byte[] DEFAULT_DRONE_IP  = { (byte) 192, (byte) 168, (byte) 1, (byte) 1 };
    static ARDrone drone;
    
    TextView state;
    Button connectButton;
    Button btnTakeOffOrLand;
    
    private static final String TAG = "AR.Drone";

    boolean isVisible = true; 

    private Builder turnOnWiFiDialog;

    ControllerThread ctrThread;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
       
        setContentView(R.layout.activity_main);

        java.lang.System.setProperty("java.net.preferIPv4Stack", "true");
        java.lang.System.setProperty("java.net.preferIPv6Addresses", "false");

        state = (TextView) findViewById(R.id.state);
        connectButton = (Button) findViewById(R.id.connect);
        
        btnTakeOffOrLand = (Button) findViewById(R.id.takeOffOrland);
        btnTakeOffOrLand.setEnabled(false);

        btnTakeOffOrLand.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                ctrThread.start();
                return false;
            }
        });
        
        turnOnWiFiDialog = new AlertDialog.Builder(this);
        turnOnWiFiDialog.setMessage("Please turn on WiFi and connect to AR.Drone wireless accsess point");
        turnOnWiFiDialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int id) {
            dialog.dismiss();
        }});

        connectButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startARDroneConnection(connectButton);
            }
        });
    }
    
    private void startARDroneConnection(final Button btnConnect) {
        WifiManager connManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        
        if (connManager.isWifiEnabled()) {
            state.setTextColor(Color.RED);
            state.setText("Connecting..." +  connManager.getConnectionInfo().getSSID());
            btnConnect.setEnabled(false);
            (new DroneStarter()).execute(MainActivity.drone); 
        } else {
            turnOnWiFiDialog.show();
        }
    }       
    
    private void droneOnConnected() {
        ctrThread = new ControllerThread(drone);
        ctrThread.setName("Controll Thread");
        loadControllerDeadZone();

        state.setTextColor(Color.GREEN);
        state.setText("Connected");
        loadDroneSettingsFromPref();
        connectButton.setEnabled(false);

        if (null != ctrThread) {
            ctrThread.setDrone(drone);
        }
        
        if (btnTakeOffOrLand != null) {
            btnTakeOffOrLand.setVisibility(View.VISIBLE);
            btnTakeOffOrLand.setClickable(true);
            btnTakeOffOrLand.setEnabled(true);
            btnTakeOffOrLand.setOnClickListener(new View.OnClickListener()  {
                public void onClick(View v) {
                    
                    if (null == drone || drone.getState() == State.DISCONNECTED) {
                        state.setText("Disconnected");
                        state.setTextColor(Color.RED);
                        connectButton.setEnabled(true);
                        return;
                    }
                    
                    if (btnTakeOffOrLand.getText().equals(getString(R.string.btn_land))) {
                        try
                        {
                            drone.land();
                        } catch(Throwable e)
                        {
                            Log.e(TAG, "Faliled to execute take off command" , e);
                        }
                        
                        btnTakeOffOrLand.setText(R.string.btn_take_off);
                    } else  {                        
                        try
                        {
                            drone.clearEmergencySignal();
                            drone.trim(); 
                            drone.takeOff();
                        } catch(Throwable e)
                        {
                            Log.e(TAG, "Faliled to execute take off command" , e);
                        }
                        btnTakeOffOrLand.setText(R.string.btn_land);
                    }
                }
            });
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        isVisible = true;
        if (null != drone) {
            drone.resumeNavData();
            drone.resumeVideo();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isVisible = false;
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
            ctrThread.finish();
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
            //foo
            drone = new ARDrone(InetAddress.getByAddress(DEFAULT_DRONE_IP), 10000, 60000);
            MainActivity.drone = drone;
            drone.connect();
            drone.clearEmergencySignal();
            drone.trim();
            drone.waitForReady(CONNECTION_TIMEOUT);
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

    private void loadDroneSettingsFromPref() {
            droneLoadMaxAltitude();
            droneLoadMaxAngle();
            droneLoadMaxVerticalSpeed();
            drobeLoadMaxRotationSpeed();
            loadControllerDeadZone();
    }
            
    private void drobeLoadMaxRotationSpeed() {
        //if (null != drone && prefs.contains(PREF_MAX_ROTATION_SPEED)) {
            setDroneParam(DRONE_MAX_YAW_PARAM_NAME, twoDForm.format(50f * Math.PI / 180f).replace(',', '.'));
        //}
    }

    private void loadControllerDeadZone() {
        //if (null != ctrThread && prefs.contains(PREF_MAX_CONTROLLER_DEDZONE)) {
            //ctrThread.setControlThreshhold(prefs.getFloat(PREF_MAX_ROTATION_SPEED, 30f) / 100f);
        //}
        
    }

    private void droneLoadMaxVerticalSpeed() {
        //if (null != drone && prefs.contains(PREF_MAX_VERICAL_SPEED)) {
            setDroneParam(DRONE_MAX_VERT_SPEED_PARAM_NAME, String.valueOf(Math.round(1f * 1000)));
        //}
    }

    private void droneLoadMaxAngle() {
        //if (null != drone && prefs.contains(PREF_MAX_ANGLE)) {
            setDroneParam(DRONE_MAX_EULA_ANGLE, twoDForm.format(6f * Math.PI / 180f).replace(',', '.'));
        //}
    }

    private void droneLoadMaxAltitude() {
        //if (null != drone && prefs.contains(PREF_MAX_ALTITUDE)) {
            setDroneParam(DRONE_MAX_ALTITUDE, String.valueOf(Math.round(1.5f * 1000)));
        //}
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
