package ar.com.manas.giskard;

import java.io.IOException;

import android.util.Log;

import com.codeminders.ardrone.ARDrone;

public class ControllerThread extends Thread {
    ARDrone drone;

    final Object lock = new Object();

    private static final String TAG = ControllerThread.class.getSimpleName();

    public ControllerThread(ARDrone drone) {
        super();
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
                Log.e(TAG, "Failed to disconnect from drone" , e);
            }
            
            synchronized (lock) {
                lock.notify();
            }
        }
    }

    public void setDrone(ARDrone drone) {
        this.drone = drone;
    }
}
