package ar.com.manas.giskard;

import ar.com.manas.giskard.util.SystemUiHider;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;


/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 *
 * @see SystemUiHider
 */
public class DroneControlActivity extends Activity {
    private Giskard giskard;

    private final View.OnTouchListener takeOffHoverLand = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            Log.e("DroneControlActivity", "Handling takeOffHoverLand touch event");

            giskard.connect();

            return true;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_drone_control);

        java.lang.System.setProperty("java.net.preferIPv4Stack", "true");
        java.lang.System.setProperty("java.net.preferIPv6Addresses", "false");


        final Button takeOffHoverLandButton = (Button) findViewById(R.id.button1);

        giskard = new Giskard(this);

        takeOffHoverLandButton.setOnTouchListener(takeOffHoverLand);
    }
}
