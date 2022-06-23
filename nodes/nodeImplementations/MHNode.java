/*
BSD 3-Clause License

Copyright (c) 2007-2013, Distributed Computing Group (DCG)
                         ETH Zurich
                         Switzerland
                         dcg.ethz.ch
              2017-2018, André Brait

All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.

* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.

* Neither the name of the copyright holder nor the names of its
  contributors may be used to endorse or promote products derived from
  this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package projects.chandra_toueg.nodes.nodeImplementations;

import lombok.Getter;
import lombok.Setter;
import projects.chandra_toueg.nodes.messages.*;
import sinalgo.exception.WrongConfigurationException;
import sinalgo.gui.transformation.PositionTransformation;
import sinalgo.nodes.Node;
import sinalgo.nodes.edges.Edge;
import sinalgo.nodes.messages.Inbox;
import sinalgo.nodes.messages.Message;
import sinalgo.tools.storage.ReusableListIterator;

import java.awt.*;

@Getter
@Setter
public class MHNode extends Node {
    public boolean proposed = false;

    int ts = 0;
    int round = 0;
    int proposedValue = (int) this.getID();

    @Override
    public void handleMessages(Inbox inbox) {
        while (inbox.hasNext()) {
            Message msg = inbox.next();
            Node sender = inbox.getSender();

            if (msg instanceof NotifyRoundMessage) {
                handleNotifyRoundMessage(sender, (NotifyRoundMessage) msg);
            }
        }
    }

    private void handleNotifyRoundMessage(Node sender, NotifyRoundMessage msg) {
        if (msg.getRound() != round) {
            round = msg.getRound();
            proposed = false;
        }
    }

    @Override
    public void preStep() {
        MSSNode mssNodeConnectedTo = getMSSConnected();
        if (!proposed && mssNodeConnectedTo != null) {
            proposeValueToMSS(mssNodeConnectedTo);
        }
    }

    private void proposeValueToMSS(MSSNode mssNode) {
        System.out.println("MHNode: " + this.getID() + " propose " + proposedValue + " to " + mssNode.getID());
        proposed = true;
        send(new MHValueMessage(proposedValue, ts), mssNode);
    }

    @Override
    public void init() {

    }

    @Override
    public void neighborhoodChange() {
    }

    public void start() {
    }

    @Override
    public void draw(Graphics g, PositionTransformation pt, boolean highlight) {
        Color color;

        if (proposed) {
            color = Color.GREEN;
        } else {
            color = Color.RED;
        }

        super.drawNodeAsDiskWithText(g, pt, highlight, "MH: " + this.getID() + "|R: " + round, 25, color);
    }

    @Override
    public void postStep() {
        ts++;
    }

    @Override
    public String toString() {
        return "";
    }

    @Override
    public void checkRequirements() throws WrongConfigurationException {
    }

    private MSSNode getMSSConnected() {
        ReusableListIterator<Edge> connections = this.getOutgoingConnections().iterator();

        while (connections.hasNext()) {
            Edge connection = connections.next();

            if (connection.getEndNode() instanceof MSSNode) {
//                System.out.println("Node: " + this.getID() + " Connected to MSS: " + connection.getEndNode().getID());
                return (MSSNode) connection.getEndNode();
            }
        }

        return null;
    }

    public boolean alreadyProposed() {
        return this.proposed;
    }
}
