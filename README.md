# Solving the consensus problem in a Mobile Environment

### Introduction

This project it's a possible solution to solve the consensus problem in a mobile environment. Our architecture is based on fixed Mobile Station Nodes (MSS) that have fixed coverage radius and Mobile Host Nodes (MH) that act like mobile nodes and can enter/exit MSS radius.  

Each MH Node must propose a value to one of the MSS Node, and when all MH Nodes have proposed the MSS Nodes try to reach consensus using chandra-toueg algorithm [1][2]. If consensus is reached all MH Nodes are notified to use the consensus value, but if consensus is not reached all MH Nodes are notified that there is no consensus and they can propose another value.  

To implement this solution it was used [Sinalgo](https://sinalgo.github.io/), which is a simulation framework for testing and validating network algorithms. To understand better how Sinalgo work and how to use it I trully recommend read the [tutorial](https://sinalgo.github.io/tutorial/Documentation.html) in Sinalgo's website.

### Implementation

At this report will not be explained how chandra&toueg consensus algorithm work, just some parts that is relevant to explain how it was implemented. To fully understand how it works I recommend read the papers [1][2].

#### Connectivity Model

It was used a single connectivity model to represent the connection between all nodes. First of all it was necessary to connect all MSS Nodes to each other, regardless the distance between then. So, when we are build the connection between nodes, if both nodes are MSS Nodes, than we connect them.  

Than it was necessary to build connection between MH Nodes and MSS Nodes based on their distances. To do that it was configurated a radius coverage for the MSS Nodes and when evaluate the connection between a MSS Node and a MH Node it was checked if the distance between them is lower than the radius coverage from this MSS Node.  

Note that for solution we assume that there is zones where none MSS Node can coverage, and there is no zone where more than one MSS Node can converage.

```
@Override
protected boolean isConnected(Node from, Node to) {
    if (from instanceof MSSNode && to instanceof MSSNode) {
        return true;
    } else if (from instanceof MSSNode && to instanceof MHNode || from instanceof MHNode && to instanceof MSSNode) {
        double dist = from.getPosition().squareDistanceTo(to.getPosition());
        return dist < rMaxSquare;
    }

    return false;
}
```


#### Messages

It was implemented one model for each message. There is messages implemented following `chandra&toueg` algorithm and messages to syncronize nodes states. Below there is the message list and a short explanation about each one.

> Propose value message: send to coordinator when a MSS Node want to propose a value to consensus (chandra&toueg). 

> Try message: broadcast from coordinator to all MSS Nodes when want to try a value proposed by one of the MSS Nodes (chandra&toueg)
 
> ACK message: send to coordinator when a MSS Node accept the try value from coordinator (chandra&toueg)

> NACK message: send to coordinator when a MSS Node not accept the try value from coordinator (chandra&toueg)

> Propose value defined message: broadcast from coordinator when consensus reached (chandra&toeug)

> Next round message: broadcast from coordinator to MSS Nodes when consensus failed and needs to go to next round with another coordinator (chandra&toueg)

> MH value message: send from MH Node to MSS Node to propose a value

> Buffer size message: send between MSS Nodes to syncronize buffer size and know if all MH Nodes has proposed values

> Notify round message: broadcast from MSS Nodes to all MH Nodes in range to notify that consensus failed and needs to go to next round. With this message MH Nodes know that they can propose another value again.
	
It is possible to access each model in `nodes/messages` directory.

#### Nodes

##### MSS Node

##### MH Node


##### Display

To help understand what is happening at Sinalgo's interface were setted texts, custom colors and shapes.  

Squares represents MSS Nodes. Inside there is three informations: the node's id `[ID]`, in which round the node is `[R]` and which value the node propose `[P]`. When the node reach consensus the color change from blue to magenta.  

Circles represents MH Nodes. Inside there is three informations: the node's id `[MH]`, in which round the node is `[R]` and which value the node propose to one MSS Node. Before the node propose any value his color is red, after propose change to green and after received consensus message change to magenta.  

<p align="center"> 
	<img src="./images/chandra-toueg-before-consensus" alt="drawing" width="250"/>
	<img src="./images/chandra-toueg-mss-consensus" alt="drawing" width="250"/>
	<img src="./images/chandra-toueg-all-consensus" alt="drawing" width="250"/>
</p>

### Configuration

In `Config.xml` file is possible to edit some Sinalgo's configuration and our custom configuration for this project.  

  - NackProbability: set the probability do MSS Node send NACK message when coordinator propose value
  - UDG: set MSS Node radius
  - RandomWayPoint: set MH Node mobility configuration

```
<Custom>
  <RandomMessageTransmission distribution="Uniform" min="1" max="1"/>
  <Node defaultSize="10"/>
  <GeometricNodeCollection rMax="10000"/>
  <UDG rMax="300"/>
  <NackProbability value="0.5"/>

  <RandomWayPoint>
      <Speed distribution="Gaussian" mean="10" variance="20" />
      <WaitingTime distribution="Poisson" lambda="10" />
  </RandomWayPoint>
</Custom>
```

### Logging

It is possible to enable info logging  in `LogL` file. Just set `infoLog` to true if want to enable or false to disable. By default the logfile will be located in the logs folder inside sinalgo hidden directory in user's home. 


```
public static final boolean infoLog = true
```

This will enable log for MSS Nodes and MH Nodes. Each kind of node has his own log file: `mss_logfile.txt` for MSS Nodes; and `mh_logfile.txt` for MH Node.  

In MSS Node log is possible to verify `chandra&toueg` algorithm execution, with MSS Node proposing values to coordinator and sending ACK or NACK to accept the value or not. It will be also possible to see when consensus reach, and in which round each node received consensus message.  
In MH Node log is possible to verify when each MH node propose a value to a MSS Node and when each node received consensus message.  

### Scenarios:


# How to execute

- Copy models and node folders, ConfigGlobal and LogL to Sinalgo project template
- Copy Config.xml to resources/projects/<custom project> directory
- Run Sinalgo and chose your new custom project to run

### References

[1] [Chandra,Toueg,94]. Chandra, Toueg: Unreliable Failure Detectors for Reliable Distributed Systems (1994), Journal of the ACM, 1994.
[2] [Chandra,Hadzilacos,Toueg,96] The weakest failure detector for solving
Consensus, (1996), http://citeseer.ist.psu.edu/chandra96weakest.html
