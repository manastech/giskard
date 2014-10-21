package ar.com.manas.giskard;

import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;

import android.util.Log;

import com.codeminders.ardrone.ARDrone;

public class ControllerThread extends Thread {
    ARDrone drone;
    //private float controlThreshhold = 0.5f;
    
    final Object lock = new Object();
    boolean done = false;
    
    private static final long READ_UPDATE_DELAY_MS = 5L;
    private static final long FINIS_TIMEOUT = 2000; // 2 sec.
    
    private static final String TAG = ControllerThread.class.getSimpleName();
    
    //private final AtomicBoolean flipSticks = new AtomicBoolean(false);

    private ConcurrentLinkedQueue<Integer> commandQueue;
    
    public ControllerThread(ARDrone drone) {
        super();
        commandQueue = new ConcurrentLinkedQueue<Integer>();

        this.drone = drone;
    }

    private static final int TAKE_OFF = 0;
    private static final int HOVER = 1;
    private static final int LAND = 2;

    @Override
    public void run() {

        commandQueue.add(TAKE_OFF);
        for (int i = 0; i < 1000; i++){
            commandQueue.add(HOVER);
        }
        commandQueue.add(LAND);

        try
        {
            while(!done || commandQueue == null || commandQueue.isEmpty())
            {
                if (commandQueue == null) break;

                switch (commandQueue.poll()) {
                    case TAKE_OFF:
                        drone.takeOff();
                        break;
                    case LAND:
                        drone.land();
                        break;
                    default:
                        drone.hover();
                }

                try
                {
                    Thread.sleep(READ_UPDATE_DELAY_MS);
                }
                catch(InterruptedException e)
                {
                    // Ignore
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed read data from controller" , e);
        }
        finally
        {
            try {
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
    
    public void finish()
    {
        done = true;

        if (isAlive()) {
            synchronized (lock) {
                try {
                    lock.wait(FINIS_TIMEOUT);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Finish process is interrupted" , e);
                }
            }
        }
    }
    
}
