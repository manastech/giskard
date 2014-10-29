package ar.com.manas.giskard

import android.view.{Gravity, ViewGroup, LayoutInflater}
import android.os.Bundle
import android.widget.{FrameLayout, Button}
import android.view.ViewGroup.LayoutParams._

import macroid._
import macroid.FullDsl._
import macroid.contrib.ExtraTweaks._
import macroid.util.Ui
import macroid.akkafragments.AkkaFragment

import scala.concurrent.ExecutionContext.Implicits.global

/** Styles for our widgets */
object NetworkStyles {
  def connectButton(implicit appCtx: AppContext) =  
    text("Connect") +
    TextSize.large +
    lp[FrameLayout](WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER)
}

/** Our UI fragment */
class NetworkManagerFragment extends AkkaFragment with Contexts[AkkaFragment] {
  // get actor name from arguments
  lazy val actorName = getArguments.getString("name")

  // actor for this fragment
  lazy val actor = Some(actorSystem.actorSelection(s"/user/$actorName"))

  // a slot for the racket button
  var connectButton = slot[Button]

  def connect =
    Ui(actor.foreach(_ ! NetworkManager.Connect))

  // trigger the fadeIn effect
  def receive = ()

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) = getUi {
    l[FrameLayout](
      w[Button] <~ wire(connectButton) <~ NetworkStyles.connectButton <~ On.click(connect)
    )
  }
}
