package ar.com.manas.giskard;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.codeminders.ardrone.ARDrone;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Timer;
import java.util.TimerTask;


/**
 * Created by mverzilli on 10/10/14.
 */
public class Giskard {
    public static final long CONNECTION_TIMEOUT = 10000;
    public static final byte[] DEFAULT_DRONE_IP  = { (byte) 192, (byte) 168, (byte) 1, (byte) 1 };

    public static String DRONE_MAX_YAW_PARAM_NAME = "control:control_yaw";
    public static String DRONE_MAX_VERTICAL_SPEED_PARAM_NAME = "control:control_vz_max";
    public static String DRONE_MAX_EULA_ANGLE = "control:euler_angle_max";
    public static String DRONE_MAX_ALTITUDE = "control:altitude_max";
    DecimalFormat twoDForm = new DecimalFormat("#.##");

    private static final String TAG = "Giskard";

    private final Context context;

    static ARDrone drone;

    public Giskard(Context context) {
        this.context = context;
    }

    public void setDrone(ARDrone drone) {
        Giskard.drone = drone;
    }

    public ARDrone getDrone() {
        return drone;
    }

    public void connect() {
        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

        if (wifi.isWifiEnabled()) {
            Log.e("Giskard", "WIFI enabled, running DroneStarter routine");
           (new DroneStarter()).execute(this);
            Log.e("Giskard", "Routine enqueued");
        } else {
            Log.e("Giskard", "WIFI disabled, will not connect");
        }
    }

    public void droneConnected() {
        setupDroneParams();

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                takeOff();
                Timer timer = new Timer();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        land();
                    }
                }, 5000);
            }
        }, 2000);
    }

    public void takeOff() {
        try {
            drone.takeOff();
        } catch (IOException e) {
            Log.e("Giskard", "Takeoff failed", e);
        }
    }

    public void land() {
        try {
            drone.land();
        } catch (IOException e) {
            Log.e("Giskard", "Landing failed", e);
        }
    }

    private void setupDroneParams() {
        // 1.5f meters
        setDroneParam(DRONE_MAX_ALTITUDE, String.valueOf(Math.round(1.5f * 1000)));

        // 6f degrees
        setDroneParam(DRONE_MAX_EULA_ANGLE, twoDForm.format(6f * Math.PI / 180f).replace(',', '.'));

        // 1f m/s
        setDroneParam(DRONE_MAX_VERTICAL_SPEED_PARAM_NAME, String.valueOf(Math.round(1f * 1000)));

        // 50f degrees per second
        setDroneParam(DRONE_MAX_YAW_PARAM_NAME, twoDForm.format(50f * Math.PI / 180f).replace(',', '.'));
    }

    public void droneConnectionFailed() {

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