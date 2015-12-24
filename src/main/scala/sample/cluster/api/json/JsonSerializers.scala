package sample.cluster.api.json

import akka.cluster.MemberStatus
import org.json4s.CustomSerializer
import org.json4s.JsonAST.JString

trait JsonSerializers {
  val memberStatusSerializer = new CustomSerializer[MemberStatus](format => ( {
    case JString("Exiting") => MemberStatus.Exiting
    case JString("Up") => MemberStatus.Up
    case JString("WeaklyUp") => MemberStatus.WeaklyUp
    case JString("Joining") => MemberStatus.Joining
    case JString("Leaving") => MemberStatus.Leaving
    case JString("Removed") => MemberStatus.Removed
    case JString("Down") => MemberStatus.Down
  }, {
    case x: MemberStatus =>
      JString(x.toString)
  }
    ))
}
