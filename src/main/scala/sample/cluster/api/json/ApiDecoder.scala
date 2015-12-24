package sample.cluster.api.json

import org.json4s.NoTypeHints
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.write

object ApiDecoder extends JsonSerializers {
  implicit val formats = Serialization.formats(NoTypeHints) + memberStatusSerializer

  def encode(state: ClusterState): String = {
    write(state)
  }
}
