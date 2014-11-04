package ar.com.manas.giskard

import android.view.{Gravity, ViewGroup, LayoutInflater}
import android.os.Bundle
import android.widget.{FrameLayout, Button}
import android.view.ViewGroup.LayoutParams._

import android.net.wifi.WifiManager

import macroid._
import macroid.FullDsl._
import macroid.contrib.ExtraTweaks._
import macroid.util.Ui
import macroid.akkafragments.AkkaFragment
import macroid.Contexts
import macroid.AutoLogTag
import macroid.akkafragments.AkkaAndroidLogger

import scala.concurrent.ExecutionContext.Implicits.global

import android.util.Log

object NetworkStyles {
  def connectButton(implicit appCtx: AppContext) =  
    text("Connect") +
    TextSize.large +
    lp[FrameLayout](WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER)
}

class NetworkManagerFragment extends AkkaFragment with Contexts[AkkaFragment] with AutoLogTag {

  import macroid.Logging._
  
  lazy val actorName = getArguments.getString("name")
  lazy val actor = Some(actorSystem.actorSelection(s"/user/$actorName"))

  var connectButton = slot[Button]

  def connect = {    
    Ui(actorSystem.actorSelection("/user/drone") ! Drone.TakeOff)  
  }

  def receive = {
    Log.e("NetworkManagerFragment", "receive")

    probeWifi
    Ui(actor.foreach(_ ! NetworkManager.WifiEnabled(true)))
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) = getUi {
    l[FrameLayout](
      w[Button] <~ wire(connectButton) <~ NetworkStyles.connectButton <~ On.click(connect)
    )
  }

  def probeWifi(implicit appCtx: AppContext) = {  
    Log.e("NetworkManagerFragment", "probeWifi")

    val wifiService = appCtx.get.getSystemService(android.content.Context.WIFI_SERVICE)
    Log.e("NetworkManagerFragment", wifiService.toString())    

    wifiService match {
       case wifi: WifiManager =>
        Log.e("NetworkManagerFragment", "wifi not enabled")
        if (!wifi.isWifiEnabled) {
          Log.e("NetworkManagerFragment", "wifi not enabled")
          throw new Exception("Wifi Not Enabled")
        }
        Log.e("NetworkManagerFragment", "wifi enabled")
       case _ =>
        Log.e("NetworkManagerFragment", "invalid wifi service")
        throw new Exception("Invalid wifi service")
     }
  }
}
