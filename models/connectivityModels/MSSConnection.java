package projects.chandra_toueg.models.connectivityModels;

import sinalgo.exception.CorruptConfigurationEntryException;
import sinalgo.models.ConnectivityModelHelper;
import sinalgo.nodes.Node;
import sinalgo.runtime.SinalgoRuntime;

import java.util.HashMap;

public class MSSConnection extends ConnectivityModelHelper {
    private static boolean initialized;

    public MSSConnection() throws CorruptConfigurationEntryException {
        if(!initialized) {
            initialized = true;
        }
    }

    // all MSS Nodes are connected to each other
    @Override
    protected boolean isConnected(Node from, Node to) { return true; }
}
