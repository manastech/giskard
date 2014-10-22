package ar.com.manas.giskard;

import android.os.AsyncTask;
import android.util.Log;

import com.codeminders.ardrone.ARDrone;

import java.io.IOException;
import java.net.InetAddress;

/**
 * Created by mverzilli on 10/22/14.
 */
public class DroneStarter extends AsyncTask<ARDrone, Integer, Boolean> {

    private static final String TAG = "DroneStarter";
    private final MainActivity mainActivity;

    public DroneStarter(MainActivity mainActivity){
        this.mainActivity = mainActivity;
    }

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
        mainActivity.onDroneStarterFinished(success);
    }
}