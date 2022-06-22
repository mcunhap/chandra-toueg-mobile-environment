package projects.chandra_toueg.models.connectivityModels;

import projects.chandra_toueg.nodes.nodeImplementations.MHNode;
import projects.chandra_toueg.nodes.nodeImplementations.MSSNode;
import sinalgo.configuration.Configuration;
import sinalgo.exception.CorruptConfigurationEntryException;
import sinalgo.models.ConnectivityModelHelper;
import sinalgo.nodes.Node;
import sinalgo.runtime.Global;
import sinalgo.runtime.Main;
import sinalgo.runtime.SinalgoRuntime;

import java.util.HashMap;

public class MSSConnection extends ConnectivityModelHelper {
    private static boolean initialized;
    private static double rMaxSquare; // we reuse the rMax value from the GeometricNodeCollection.

    public MSSConnection() throws CorruptConfigurationEntryException {
        if(!initialized) {
            double geomNodeRMax = Configuration.getDoubleParameter("GeometricNodeCollection/rMax");

            try {
                rMaxSquare = Configuration.getDoubleParameter("UDG/rMax");
            } catch (CorruptConfigurationEntryException e) {
                Global.getLog().logln(
                        "\nWARNING: Did not find an entry for UDG/rMax in the XML configuration file. Using GeometricNodeCollection/rMax.\n");
                rMaxSquare = geomNodeRMax;
            }

            if (rMaxSquare > geomNodeRMax) { // dangerous! This is probably not what the user wants!
                Main.minorError(
                        "WARNING: The maximum transmission range used for the UDG connectivity model is larger than the maximum transmission range specified for the GeometricNodeCollection.\nAs a result, not all connections will be found! Either fix the problem in the project-specific configuration file or the '-overwrite' command line argument.");
            }

            rMaxSquare = rMaxSquare * rMaxSquare;
            initialized = true;
        }
    }

    // all MSS Nodes are connected to each other
    @Override
    protected boolean isConnected(Node from, Node to) {
        if (from instanceof MSSNode && to instanceof MSSNode) {
            return true;
        } else if (from instanceof MSSNode && to instanceof MHNode || from instanceof MHNode && to instanceof MSSNode) {
            double dist = from.getPosition().squareDistanceTo(to.getPosition());
            System.out.println("From: " + from.getID() + " To: " + to.getID() + " Dist: " + dist + " RMaxSquare: " + rMaxSquare + " Connected: " + (dist < rMaxSquare));
            return dist < rMaxSquare;
        }

        return false;
    }
}
