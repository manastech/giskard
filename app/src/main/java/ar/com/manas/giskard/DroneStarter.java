package ar.com.manas.giskard;

import android.os.AsyncTask;
import android.util.Log;

import com.codeminders.ardrone.ARDrone;

import java.net.InetAddress;

/**
 * Created by mverzilli on 10/10/14.
 *
 * Initially based on private class defined on:
 * https://code.google.com/p/javadrone/source/browse/controltower-android/src/com/codeminders/ardrone/MainActivity.java
 *
 */
public class DroneStarter extends AsyncTask<Giskard, Integer, Boolean> {

    private static final String TAG = "DroneStarter";

    private Giskard giskard;

    @Override
    protected Boolean doInBackground(Giskard... giskards) {
        giskard = giskards[0];
        ARDrone drone = giskard.getDrone();
        giskard.setDrone(drone);

        try {
            Log.e(TAG, "Creating ARDrone instance");
            drone = new ARDrone(InetAddress.getByAddress(Giskard.DEFAULT_DRONE_IP), 10000, 60000);

            giskard.setDrone(drone);

            Log.e(TAG, "Connecting to drone");
            drone.connect();
            drone.clearEmergencySignal();
            drone.trim();
            drone.waitForReady(Giskard.CONNECTION_TIMEOUT);
            drone.playLED(1, 10, 4);
            drone.selectVideoChannel(ARDrone.VideoChannel.HORIZONTAL_ONLY);
            drone.setCombinedYawMode(true);
            return true;
        } catch (Exception e) {
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

    //This runs on the UI thread
    protected void onPostExecute(Boolean success) {
        if (success) {
            Log.e(TAG, "SUCCESS Connecting to drone will run droneConnected");
            giskard.droneConnected();
        } else {
            Log.e(TAG, "FAILED Connecting to drone will run droneConnectionFailed");
            giskard.droneConnectionFailed();
        }
    }
}
