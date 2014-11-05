package ar.com.manas.giskard

import android.os.Bundle
import android.support.v4.app.FragmentActivity
import android.util.Log
import android.view.ViewGroup.LayoutParams._
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.Button

import macroid._
import macroid.FullDsl._
import macroid.akkafragments.AkkaActivity
import macroid.util.Ui

import akka.actor.Props
import akka.actor.Kill
import akka.actor.ActorRef
import akka.event.Logging._

object GiskardTweaks {
  val gravityCenter = Tweak[LinearLayout](_.setGravity(Gravity.CENTER_HORIZONTAL))  
}

class MainActivity extends FragmentActivity with Contexts[FragmentActivity] with IdGeneration with AkkaActivity {
  import GiskardTweaks._

  val actorSystemName = "giskard"  
  
  lazy val drone = actorSystem.actorOf(Drone.props, "drone")
  lazy val square = actorSystem.actorOf(Square.props, "square") 

  System.setProperty("java.net.preferIPv4Stack", "true");
  System.setProperty("java.net.preferIPv6Addresses", "false");

  override def onCreate(savedInstanceState: Bundle) = {
    super.onCreate(savedInstanceState)

    (drone, square)

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
      w[Button] <~ text("Disconnect") <~
        On.click {
          Ui {
            drone ! Drone.Disconnect
          }
        },
      w[Button] <~ text("Print NavData") <~
        On.click {
          Ui {
            drone ! Drone.PrintNavData
          }
        },
      w[Button] <~ text("Square") <~
        On.click {
          Ui {
            square ! Square.Start
          }
        }
    ) <~ vertical <~ gravityCenter

    setContentView(getUi(view))
  }

  override def onStart() = {
    super.onStart()
  }
}
