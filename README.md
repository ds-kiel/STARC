# STARC

This repository is based on [A2-Synchroton](https://github.com/iot-chalmers/a2-synchrotron) and contains an implementation of STARC (Synchronous Transmissions for Autonomous Reservation Coordination).
STARC is a decentralized protocol for intersection management of autonomous vehicles. The movement is coordinated using reservations to guarantee safe crossings. Chaos constitutes the foundation of the protocol. STARC extends Chaos with
dynamic network support as well as an election and handover mechanism to
cooperatively manage the intersection in a distributed manner.

The STARC protocol itself is implemented in Contiki and is accompanied by a VANET plugin for Cooja. 
Its current implementation in Contiki OS targets the TelosB Sky platform.


## How do I get set up?

Please start with the basic Contiki installation at [https://github.com/contiki-ng/contiki-ng/wiki](https://github.com/contiki-ng/contiki-ng/wiki). The docker setup might be a good way to get things working quickly.
Afterward, just clone this repository and execute the following steps (inside the container if you are using docker).

We first need to build the Cooja JAR:
```
cd a2-synchrotron/a2-synchrotron-contiki/tools/cooja && ant jar && cd -
```

We are then able to build the VANET plugin:
```
cd a2-synchrotron/a2-synchrotron-contiki/tools/cooja/apps/cooja-vanet-plugin && ant jar && cd -
```

Add the plugin to the Cooja project dirs in the ~/.cooja.user.properties or activate the plugin manually in Cooja.
```
DEFAULT_PROJECTDIRS=...;[APPS_DIR]/cooja-vanet-plugin
```

Then run Cooja:
```
cd a2-synchrotron/a2-synchrotron-contiki/tools/cooja
```
```
ant run
```

You can then just load the wanted simulation from the root folder.
Use [sim-starc.csc](sim-starc.csc) for the STARC and [sim-traffic-lights.csc](sim-traffic-lights.csc)for the traffic lights scenario.

As the simulation does not contain any nodes, you may need to start the simulation with *CTRL+S*.

### Simulation Parameters

You can alter simulation params manually in the simulation files:
```
<plugin>
    org.contikios.cooja.plugins.Vanet
    <plugin_config>
      <screen_export_dir value="" />
      <log_dir value="" />
      <vehicles_per_hour value="1000" />
      <network_width value="1" />
      <network_height value="1" />
      <intersection_type value="0" />
      <chaos_initiator_timeout value="5000" />
      <chaos_max_platoon_size value="-1" />
    </plugin_config>
    ...
    </plugin>
```

The following table describes the available parameters:

| Parameter               	| Description                                                                        	| Values                                                                     	|
|-------------------------	|------------------------------------------------------------------------------------	|----------------------------------------------------------------------------	|
| vehicles_per_hour       	| Specifies the number of vehicles approaching the intersection                      	| 1-6000                                                                     	|
| intersection_type       	| The type of the intersection                                                       	| 0: STARC, 1: Traffic Lights                                                	|
| chaos_max_platoon_size  	| The maximum platoon size using STARC.                                              	| 1: STARC without platoons, -1: unlimited, direct limit otherwise (e.g. 25) 	|
| left_turn_rate          	| Probability that a car wants to turn left                                          	| Default: 0.15                                                              	|
| right_turn_rate         	| Probability that a car wants to turn left                                          	| Default: 0.15                                                              	|
| screen_export_dir       	| Absolute directory to save screen exports (as JPEG) each simulation step (20ms).   	| Default: "" (disabled)                                                     	|
| log_dir                 	| Absolute directory to save statistics of the vehicles and Chaos in CSV files.      	| Default: "" (disabled)                                                     	|
| timeout                 	| Timeout of the simulation in seconds. Needed for simulation runs without any UI.   	| Default: 0 (disabled)                                                      	|
| chaos_initiator_timeout 	| Timeout to create a new network (depends on the Chaos interval). Change with care. 	| Default: 5000                                                              	|
| network_width           	| Width of a network of intersections (currently not supported, congestion not handled)                      	| Default: 1                                                                 	|
| network_height          	| Height of a network of intersections (currently not supported, congestion not handled)                     	| Default: 1                                                                 	|

#### Changing the Chaos Interval

The Chaos interval is fixed at compile time. The compile command therefore needs to be adjusted. An example for 2 seconds:
```
make intersection-node.sky TARGET=sky chaos_interval=2
```

#### Adding Radio Failures
Radio failures are injected at compile time. The compile command also needs to be changed.
To inject failures, add another failures argument to the compile command:
```
make intersection-node.sky TARGET=sky failures=0
```
You can specify failures using the inverse of the failure rate.
To inject failures with a probability of 0.001 you would use 
```
make intersection-node.sky TARGET=sky failures=1000
```


## Overview

The STARC protocol is implemented in two separate components.
The first one is the driving control that is part of the VANET plugin for Cooja (written in Java). The second is the intersection node based on Chaos/A2-Synchroton written in C for ContikiOS.
They communicate using a serial connection with custom encoding to send binary data.


## Intersection Node
The intersection node runs the STARC protocol and handles the Chaos communication.
You can find the relevant code in [a2-synchrotron-contiki/apps/chaos/intersection/intersection-node.c](a2-synchrotron-contiki/apps/chaos/intersection/intersection-node.c).
The next table gives an overview of the different classes used.


| File                                               	| Description                                                                                                                                                                                                                                                                                                                      	|
|----------------------------------------------------	|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------	|
| [apps/chaos/intersection/intersection-node.c](a2-synchrotron-contiki/apps/chaos/intersection/intersection-node.c)       	| Handles the communication with the VANET plugin, initializes relevant values for the Chaos communication (such leaving/joining the network, the reservation). This file also specifies how the results of the Chaos rounds are used. If a reservation was accepted, the vehicle control is notified using the serial connection. 	|
| [core/net/mac/chaos/lib/merge-commit/merge-commit.c](a2-synchrotron-contiki/core/net/mac/chaos/lib/merge-commit/merge-commit.c) 	| Basic support for a merge-commit application. Specifies how the Chaos packets are handled. This includes the coordination round as well as the election and handover round. Join and leave is supported together with the resulting configuration numbers (dissemination and comparison). A callback manages the merging. |
| [core/net/mac/chaos/service/joinleave/join.c](a2-synchrotron-contiki/core/net/mac/chaos/service/joinleave/join.c)        	| Basic methods to manage the Chaos network. Adding / Removing nodes (as the initiator), for example.                                                                                                                                                                                                                             |
| [apps/chaos/intersection/project-conf.h](a2-synchrotron-contiki/apps/chaos/intersection/project-conf.h)             	| Configuration file. Specifies Chaos parameters such as the number of slots and the round interval. It also defines the merge-commit value content.                                                                                                                                                                              	|



### VANET Plugin for Cooja
The VANET plugin simulates the dynamic behavior of the vehicles. For each vehicle, a Contiki node is created,
that is simulated by the main Contiki simulation. Each Contiki node is linked to
its vehicle and acts as a transmitter for the STARC protocol. The plugin handles
the driving and the resulting physics and synchronizes the position of the node
with the position of the car to create the corresponding radio environment. We
thus simulate the effect of the changing positions due to the vehicles’ movement on
the signal strength. A serial connection enables the communication between the
node and the vehicle. A node in Cooja represents a radio transmitter module of its
vehicle, while the plugin handles the autonomous driving of the car.
The main simulation of Cooja is simulated in steps of milliseconds. Cooja executes
the simulation of the plugin every 20 ms. Cars, sensors, and the node positions are
hence updated 50 times in a simulated second.

You find the VANET plugin at [a2-synchrotron-contiki/tools/cooja/apps/cooja-vanet-plugin](a2-synchrotron-contiki/tools/cooja/apps/cooja-vanet-plugin).
The most important files are listed below.


| File                                                                                           	| Description                                                                                                                                             	|
|------------------------------------------------------------------------------------------------	|---------------------------------------------------------------------------------------------------------------------------------------------------------	|
| [Vanet.java](a2-synchrotron-contiki/tools/cooja/apps/cooja-vanet-plugin/java/org/contikios/cooja/plugins/Vanet.java)  	| Main plugin file that initializes the whole plugin. Executes the simulation every 20ms.                                                                 	|
| [vanet/world/World.java](a2-synchrotron-contiki/tools/cooja/apps/cooja-vanet-plugin/java/org/contikios/cooja/plugins/vanet/world/World.java)  	| Simulates the different components of the protocol (physics, sensors, vehicle behavior). Keeps the car positions in sync with the Cooja node positions. 	|
| [vanet/world/physics/Physics.java](a2-synchrotron-contiki/tools/cooja/apps/cooja-vanet-plugin/java/org/contikios/cooja/plugins/vanet/world/physics/Physics.java)  	| Basic physics simulation and collision detection.                                                                                                       	|
| [vanet/transport_network/intersection/Intersection.java](a2-synchrotron-contiki/tools/cooja/apps/cooja-vanet-plugin/java/org/contikios/cooja/plugins/vanet/transport_network/intersection/Intersection.java)  	| The main intersection containing the lane layout with start, end and target lanes.                                                                      	|
| [vanet/transport_network/intersection/TrafficLightIntersection.java](a2-synchrotron-contiki/tools/cooja/apps/cooja-vanet-plugin/java/org/contikios/cooja/plugins/vanet/transport_network/intersection/TrafficLightIntersection.java) 	| An extended intersection with a traffic light schedule.                                                                                                 	|
| [vanet/transport_network/TransportNetwork.java](a2-synchrotron-contiki/tools/cooja/apps/cooja-vanet-plugin/java/org/contikios/cooja/plugins/vanet/transport_network/TransportNetwork.java)  	| Responsible for connecting multiple intersections to a network.                                                                                         	|
| [vanet/vehicle/VehicleManager.java](a2-synchrotron-contiki/tools/cooja/apps/cooja-vanet-plugin/java/org/contikios/cooja/plugins/vanet/vehicle/VehicleManager.java)  	| Manages the active vehicles and their vehicle ids.                                                                                                      	|
| [vanet/vehicle/ChaosVehicle.java](a2-synchrotron-contiki/tools/cooja/apps/cooja-vanet-plugin/java/org/contikios/cooja/plugins/vanet/vehicle/ChaosVehicle.java)  	| The vehicle implementation for cars running STARC.                                                                                                      	|
| [vanet/vehicle/TrafficLightVehicle.java](a2-synchrotron-contiki/tools/cooja/apps/cooja-vanet-plugin/java/org/contikios/cooja/plugins/vanet/vehicle/TrafficLightVehicle.java)  	| The vehicle implementation for a traffic lights intersection.                                                                                           	|
| [vanet/vehicle/platoon/ChaosPlatoon.java](a2-synchrotron-contiki/tools/cooja/apps/cooja-vanet-plugin/java/org/contikios/cooja/plugins/vanet/vehicle/platoon/ChaosPlatoon.java)  	| Enables virtual platooning for the STARC protocol.                                                                                                      	|
| [vanet/log/Logger.java](a2-synchrotron-contiki/tools/cooja/apps/cooja-vanet-plugin/java/org/contikios/cooja/plugins/vanet/log/Logger.java)  	| Logs statistis to CSV files.                                                                                                                            	|
| [vanet/vehicle/ChaosStatsHandler.java](a2-synchrotron-contiki/tools/cooja/apps/cooja-vanet-plugin/java/org/contikios/cooja/plugins/vanet/vehicle/ChaosStatsHandler.java)  	| Collects and saves Chaos statistics from the serial input.                                                                                              	|
| [skins/VanetVisualizerSkin.java](a2-synchrotron-contiki/tools/cooja/apps/cooja-vanet-plugin/java/org/contikios/cooja/plugins/skins/VanetVisualizerSkin.java) 	| Handles the rendering of the intersection and the screen export.                                                                                        	|
| [vanet/vehicle/MessageProxy.java](a2-synchrotron-contiki/tools/cooja/apps/cooja-vanet-plugin/java/org/contikios/cooja/plugins/vanet/vehicle/MessageProxy.java)  	| Handles the serial connection to the intersection nodes including the de- and encoding.                                                                 	|
| [vanet/vehicle/physics/VehicleBody.java](a2-synchrotron-contiki/tools/cooja/apps/cooja-vanet-plugin/java/org/contikios/cooja/plugins/vanet/vehicle/physics/VehicleBody.java)  	| The vehicle physics of a car.                                                                                                                           	|
| [vanet/vehicle/physics/DirectionalDistanceSensor.java](a2-synchrotron-contiki/tools/cooja/apps/cooja-vanet-plugin/java/org/contikios/cooja/plugins/vanet/vehicle/physics/DirectionalDistanceSensor.java)  	| A simulated distance sensor in the forward direction of the vehicle.                                                                                    	|

## Who do I talk to?
For questions regarding A2-Synchroton, we refer to [the official A2-Synchroton repository](https://github.com/iot-chalmers/a2-synchrotron).
In case of any further questions, don't hesitate to write an email to starc(at)patrickrathje.de
