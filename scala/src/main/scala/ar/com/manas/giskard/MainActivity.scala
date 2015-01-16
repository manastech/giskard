package ar.com.manas.giskard

import android.os.Bundle
import android.support.v4.app.FragmentActivity
import android.util.Log
import android.view.ViewGroup.LayoutParams._
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.Button
import android.app.Activity
import android.os.Bundle
import android.content.Intent

import macroid._
import macroid.FullDsl._
import macroid.akkafragments.AkkaActivity
import macroid.util.Ui

import akka.actor.Props
import akka.actor.Kill
import akka.actor.ActorRef
import akka.event.Logging._

import org.opencv.android.BaseLoaderCallback
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader

object GiskardTweaks {
  val gravityCenter = Tweak[LinearLayout](_.setGravity(Gravity.CENTER_HORIZONTAL))
}

class MainActivity extends FragmentActivity with Contexts[FragmentActivity] with IdGeneration with AkkaActivity {
  import GiskardTweaks._

  val actorSystemName = "giskard"

  lazy val drone = actorSystem.actorOf(Drone.props, "drone")
  lazy val square = actorSystem.actorOf(Square.props, "square")
  lazy val camera = actorSystem.actorOf(Camera.props, "camera")
  lazy val riseAndHover = actorSystem.actorOf(RiseAndHover.props, "riseAndHover")

  System.setProperty("java.net.preferIPv4Stack", "true");
  System.setProperty("java.net.preferIPv6Addresses", "false");

  lazy val openCvLoaderCallback = new BaseLoaderCallback(this) {
    override def onManagerConnected(status: Int) = loadOpenCV(this, status)
  }

  def loadOpenCV(callback: BaseLoaderCallback, status: Int) = status match {
    case LoaderCallbackInterface.SUCCESS => Log.e("ColorBlobDetectionActivity", "OpenCV loaded successfully")
    case _ => callback.onManagerConnected(status)
  }

  override def onCreate(savedInstanceState: Bundle) = {
    super.onCreate(savedInstanceState)

    (drone, square, camera)

    // layout params
    val lps = lp[LinearLayout](MATCH_PARENT, WRAP_CONTENT, 1.0f)

    // include the two fragments
    val view = l[LinearLayout](
      w[Button] <~
        text("Connect") <~
          On.click {
            Ui {
              drone ! Drone.Init
            }
          },
      w[Button] <~ text("Poll state") <~
        On.click {
          Ui {
            drone ! Drone.PollState
          }
        },
      w[Button] <~ text("Takeoff") <~
        On.click {
          Ui {
            drone ! Drone.TakeOff
          }
        },
      w[Button] <~ text("Land") <~
        On.click {
          Ui {
            drone ! Drone.Land
          }
        },
      w[Button] <~ text("Disconnect") <~
        On.click {
          Ui {
            drone ! Drone.Disconnect
          }
        },
        w[Button] <~ text("Take pic") <~
        On.click {
          Ui {
            camera ! Camera.SaveSnapshot
          }
        },
      w[Button] <~ text("Square") <~
        On.click {
          Ui {
            square ! Square.Start
          }
        },
      w[Button] <~ text("Color Blob") <~
        On.click {
          Ui {
            var myIntent = new Intent(this, classOf[ColorBlobDetectionActivity]);
            MainActivity.this.startActivity(myIntent);
          }
        },
      w[Button] <~ text("Rise and hover") <~
        On.click {
          Ui {
            riseAndHover ! RiseAndHover.Start
          }
        },
      w[Button] <~ text("Print NavData") <~
        On.click {
          Ui {
            drone ! Drone.PrintNavData
          }
        }
    ) <~ vertical <~ gravityCenter

    setContentView(getUi(view))
  }

  override def onStart() = {
    super.onStart()
  }

  override def onResume() = {
    super.onResume()

    Log.e("MainActivity", "Loading OpenCV")

    OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this, openCvLoaderCallback)
  }
}
