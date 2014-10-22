package ar.com.manas.giskard;

import android.util.Log;

import com.codeminders.ardrone.ARDrone;

import java.io.IOException;

/**
 * Created by mverzilli on 10/22/14.
 */
public class TakeOffHoverAndLand implements Runnable {
    private static final String TAG = "TakeOffHoverAndLand";
    private final ARDrone drone;

    public TakeOffHoverAndLand(ARDrone drone) {
        this.drone = drone;
    }

    @Override
    public void run() {
        try
        {
            Log.e(TAG, "CONTROLLER THREAD Taking off...");
            drone.takeOff();

            try
            {
                Log.e(TAG, "CONTROLLER THREAD Going to sleep for 5 seconds...");
                Thread.sleep(10000);
            }
            catch(InterruptedException e)
            {
                // Ignore
            }

            Log.e(TAG, "CONTROLLER THREAD Landing...");
            drone.land();

            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignored) {
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed read data from controller" , e);
        }
        finally
        {
            try {
                Log.e(TAG, "Disconnecting from drone");
                drone.disconnect();
            } catch (IOException e) {
                Log.e(TAG, "Failed to disconnect from drone", e);
            }
        }
    }
}
