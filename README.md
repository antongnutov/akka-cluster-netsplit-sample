# akka-cluster-netsplit-sample
Sample of akka-cluster network split handling

* First node in the list of `sample.nodes` in config is used as seed-node
* Every node answers current cluster state on `sample.api.port` port
* Network split is detected by loosing seed-node from cluster
* When split happens every node searches other cluster parts via http
* When other part of split cluster is found these parts are compared alphabetically
* Leader node of split-parts comparison winner becomes seed-node for loser split-part
* Loser-part shuts itself down and connects to winner-part
* If any node except seed-node detects it is alone it shuts itself down and tries to reconnect to seed-node