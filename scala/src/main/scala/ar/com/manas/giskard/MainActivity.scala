package ar.com.manas.giskard

import android.os.Bundle
import android.widget.LinearLayout
import android.view.ViewGroup.LayoutParams._
import android.support.v4.app.FragmentActivity

import macroid._
import macroid.FullDsl._
import macroid.akkafragments.AkkaActivity

class MainActivity extends FragmentActivity with Contexts[FragmentActivity] with IdGeneration with AkkaActivity {

  val actorSystemName = "giskard"  

  lazy val drone = actorSystem.actorOf(Drone.props, "drone")
  lazy val network = actorSystem.actorOf(NetworkManager.props, "network")

  lazy val ping = actorSystem.actorOf(RacketActor.props, "ping")
  lazy val pong = actorSystem.actorOf(RacketActor.props, "pong")

  System.setProperty("java.net.preferIPv4Stack", "true");
  System.setProperty("java.net.preferIPv6Addresses", "false");

  override def onCreate(savedInstanceState: Bundle) = {
    super.onCreate(savedInstanceState)

    // initialize the actors
    (drone, network, ping, pong)

    // layout params
    val lps = lp[LinearLayout](MATCH_PARENT, WRAP_CONTENT, 1.0f)

    // include the two fragments
    val view = l[LinearLayout](
      // we pass a name for the actor, and id+tag for the fragment
      f[RacketFragment].pass("name" → "ping").framed(Id.ping, Tag.ping) <~ lps,
      f[RacketFragment].pass("name" → "pong").framed(Id.pong, Tag.pong) <~ lps,
      f[NetworkManagerFragment].pass("name" → "network").framed(Id.network, Tag.network) <~ lps
    ) <~ vertical

    setContentView(getUi(view))

    drone ! Drone.Init
  }

  override def onStart() = {
    super.onStart()

    // start the game
    ping.tell(RacketActor.Ball, pong)    
  }
}
