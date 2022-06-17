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
import sinalgo.configuration.Configuration;
import sinalgo.exception.CorruptConfigurationEntryException;
import sinalgo.exception.WrongConfigurationException;
import sinalgo.gui.transformation.PositionTransformation;
import sinalgo.nodes.Node;
import sinalgo.nodes.messages.Inbox;
import sinalgo.nodes.messages.Message;
import sinalgo.runtime.SinalgoRuntime;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

@Getter
@Setter
public class MSSNode extends Node {
    public static final int TOTAL_ROUNDS = 10;
    int round = 0;

    // TODO: get mss nodes dynamically
    int totalMSSNodes = 4;
    boolean decided = false;

    int coordinatorId = 1;
    MSSNode coordinator;
    ArrayList<ProposeValueMessage> coordinatorBuffer;
    ArrayList<AckMessage> ackBuffer;

    int proposedValue = (int) this.getID();
    int ts = 0;

    double nackProbability = 0.0;

    @Override
    public void handleMessages(Inbox inbox) {
        while (inbox.hasNext()) {
            Message msg = inbox.next();
            Node sender = inbox.getSender();

            if (msg instanceof ProposeValueMessage) {
                handleProposeValueMessage(sender, (ProposeValueMessage) msg);
            } else if (msg instanceof TryValueMessage) {
                handleTryValueMessage(sender, (TryValueMessage) msg);
            } else if (msg instanceof AckMessage) {
                handleAckMessage(sender, (AckMessage) msg);
            }
        }
    }

    private void handleProposeValueMessage(Node sender, ProposeValueMessage msg) {
        System.out.println("Node " + this.getID() + " received " + msg.getValue() + " with " + msg.getTimestamp()//
                + " from " + sender.getID());
        coordinatorBuffer.add(msg);
    }

    private void handleTryValueMessage(Node sender, TryValueMessage msg) {
        Random random = new Random();

        if (random.nextDouble() <= nackProbability) {
            send(new NackMessage(), coordinator);
        } else {
            proposedValue = msg.getValue();
            send(new AckMessage(), coordinator);
            decided = true;
        }
    }

    private void handleAckMessage(Node sender, AckMessage msg) {
        ackBuffer.add(msg);

        if (ackBuffer.size() >= (totalMSSNodes + 1) / 2) {
            ackBuffer.clear();

            broadcast(new ProposedValueDefinedMessage(proposedValue));
        }
    }

    private void handleProposedValueDefinedMessage(Node sender, ProposedValueDefinedMessage msg) {
        if (!decided) {
            proposedValue = msg.getValue();
            decided = true;
        }
    }

    private boolean tryProposeValue() {
        Random random = new Random();
        double proposeValueProbability = 0.0;

        try {
            proposeValueProbability = Configuration.getDoubleParameter("ProposeValueProbability");
        } catch (CorruptConfigurationEntryException e) {
            e.printStackTrace();
        }

        return random.nextDouble() <= proposeValueProbability;
    }

    private void proposeValue() {
        ProposeValueMessage proposeMessage = new ProposeValueMessage(proposedValue, ts);
        System.out.println("Send proposed value " + proposedValue + " to coordinator" + coordinator.getID());
        send(proposeMessage, coordinator);
    }

    @Override
    public void preStep() {
        coordinator = (MSSNode) findCoordinator();
        System.out.println("Coordinator: " + coordinator.getID());
        // update coordinator
//        coordinatorId = (round % totalMSSNodes + 1);
//        coordinator = (MSSNode) findCoordinator();

        if (tryProposeValue()) {
            proposeValue();
        }
    }

    @Override
    public void init() {
        coordinatorBuffer = new ArrayList<ProposeValueMessage>();
        ackBuffer = new ArrayList<AckMessage>();
        coordinator = (MSSNode) findCoordinator();

        try {
            nackProbability = Configuration.getDoubleParameter("NackProbability");
        } catch (CorruptConfigurationEntryException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void neighborhoodChange() {
    }

    public void start() {
    }

    @Override
    public void draw(Graphics g, PositionTransformation pt, boolean highlight) {
        super.drawNodeAsSquareWithText(g, pt, highlight, "ID: " + this.getID(), 50, Color.CYAN);
    }

    @Override
    public void postStep() {
        // broadcast defined value if coordinator buffer contains at least (n + 1) / 2 values
        if (coordinatorBuffer.size() >= (totalMSSNodes + 1) / 2) {
            int value = getMostRecentProposedValue();
            TryValueMessage valueDefinedMessage = new TryValueMessage(value);

            broadcast(valueDefinedMessage);
        }

        coordinatorBuffer.clear();

        // adjust how round works
        round++;
        if (round > TOTAL_ROUNDS) {
            round = 0;
        }

        ts++;

        System.out.println("---------");
    }

    @Override
    public String toString() {
        return "";
    }

    @Override
    public void checkRequirements() throws WrongConfigurationException {
    }

    Node findCoordinator() {
        Node coordinator = null;
        Iterator<Node> nodes = SinalgoRuntime.getNodes().iterator();

        while (nodes.hasNext()) {
            Node currentNode = nodes.next();

            if (currentNode instanceof MSSNode) {
                if (currentNode.getID() == coordinatorId) {
                    coordinator = currentNode;
                }
            } else {
                continue;
            }
        }

        return coordinator;
    }

    private int getMostRecentProposedValue() {
        coordinatorBuffer.sort(new ProposeValueComparator());

        return coordinatorBuffer.get(0).getValue();
    }
}
