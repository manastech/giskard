package ar.com.manas.giskard;

import android.util.Log;

import com.codeminders.ardrone.ARDrone;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by mverzilli on 10/10/14.
 */
public class ControlThread extends Thread {
    ARDrone drone;

    Boolean done;

    public ControlThread(ARDrone drone) {
        super();
        this.drone = drone;
        this.done = false;
    }

    @Override
    public void run() {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                land();
            }
        }, 5000);

        while (!done) {
            hover();
        }
    }

    private void hover() {
        try {
            drone.hover();
        } catch (IOException e) {
            Log.e("ControlThread", "Something went wrong hovering", e);
        }
    }

    public void land() {
        try {
            drone.land();
            done = true;
        } catch (IOException e) {
            Log.e("ControlThread", "Something went wrong landing", e);
        }
    }
}
