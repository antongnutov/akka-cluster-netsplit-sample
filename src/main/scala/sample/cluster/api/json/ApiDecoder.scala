package sample.cluster.api.json

import org.json4s.NoTypeHints
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.write
import org.json4s.jackson.Serialization.read

import scala.util.Try

object ApiDecoder extends JsonSerializers {
  implicit val formats = Serialization.formats(NoTypeHints) + memberStatusSerializer

  def encode(state: ClusterState): String = write(state)

  def decodeState(json: String): Try[ClusterState] = Try(read[ClusterState](json))
}
