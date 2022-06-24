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
import projects.chandra_toueg.LogL;
import sinalgo.configuration.Configuration;
import sinalgo.exception.CorruptConfigurationEntryException;
import sinalgo.exception.SinalgoFatalException;
import sinalgo.exception.WrongConfigurationException;
import sinalgo.gui.transformation.PositionTransformation;
import sinalgo.nodes.Node;
import sinalgo.nodes.messages.Inbox;
import sinalgo.nodes.messages.Message;
import sinalgo.runtime.SinalgoRuntime;
import sinalgo.tools.logging.Logging;

import java.awt.*;
import java.nio.Buffer;
import java.util.*;

@Getter
@Setter
public class MSSNode extends Node {
    private static int radius;
    int round = 0;

    int totalMSSNodes;
    int totalMHNodes;

    boolean decided = false;
    boolean propose = false;
    boolean allMHSent = false;
    boolean coordinatorAlreadyProposedValue = false;

    int coordinatorId = 1;
    MSSNode coordinator;
    ArrayList<ProposeValueMessage> coordinatorBuffer;
    ArrayList<AckMessage> ackBuffer;
    ArrayList<NackMessage> nackBuffer;
    ArrayList<MHValueMessage> mssBuffer;

    Map<Integer, Integer> mssBuffersMap;

    int proposedValue;
    int ts = 0;

    double nackProbability = 0.0;

    Logging logger = Logging.getLogger("mss_logfile.txt");

    static {
        try {
            radius = Configuration.getIntegerParameter("UDG/rMax");
        } catch (CorruptConfigurationEntryException e) {
            throw new SinalgoFatalException(e.getMessage());
        }
    }

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
            } else if (msg instanceof NackMessage) {
                handleNackMessage(sender, (NackMessage) msg);
            } else if (msg instanceof ProposedValueDefinedMessage) {
                handleProposedValueDefinedMessage(sender, (ProposedValueDefinedMessage) msg);
            } else if (msg instanceof NextRoundMessage) {
                handleNextRoundMessage(sender, (NextRoundMessage) msg);
            } else if (msg instanceof MHValueMessage) {
                handleMHValueMessage(sender, (MHValueMessage) msg);
            } else if (msg instanceof BufferSizeMessage) {
                handleBufferSizeMessage(sender, (BufferSizeMessage) msg);
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
            logger.logln(LogL.infoLog, "[MSSNode " + this.getID() + "] sending NACK to coordinator");
            send(new NackMessage(), coordinator);
        } else {
            logger.logln(LogL.infoLog, "[MSSNode " + this.getID() + "] sending ACK to coordinator");
            send(new AckMessage(), coordinator);
        }
    }

    private void handleAckMessage(Node sender, AckMessage msg) {
        ackBuffer.add(msg);

        if (ackBuffer.size() >= (totalMSSNodes + 1) / 2) {
            logger.logln(LogL.infoLog, "[Coordinator " + this.getID() + "] Message accepted! Broadcasting value defined: " + proposedValue);
            ackBuffer.clear();
            nackBuffer.clear();

            ProposedValueDefinedMessage proposedValueDefinedMessage = new ProposedValueDefinedMessage(proposedValue);

            broadcast(proposedValueDefinedMessage);
            handleProposedValueDefinedMessage(this, proposedValueDefinedMessage);
        }
    }

    private void handleNackMessage(Node sender, NackMessage msg) {
        nackBuffer.add(msg);

        if (nackBuffer.size() >= (totalMSSNodes + 1) / 2) {
            logger.logln(LogL.infoLog, "[Coordinator " + this.getID() + "] Message not accepted! Skip round...");
            ackBuffer.clear();
            nackBuffer.clear();

            broadcastNextRound();
        }
    }

    private void handleProposedValueDefinedMessage(Node sender, ProposedValueDefinedMessage msg) {
        if (!decided) {
            logger.logln(LogL.infoLog, "[MSSNode " + this.getID() + "] consensus reached at " + ts);
        }

        decided = true;
        proposedValue = msg.getValue();
        propose = false;
        coordinatorBuffer.clear();
    }

    private void handleNextRoundMessage(Node sender, NextRoundMessage msg) {
        initialState();
        updateRound();
        updateCoordinator();
    }

    private void handleMHValueMessage(Node sender, MHValueMessage msg) {
        mssBuffer.add(msg);
        mssBuffersMap.put((int) this.getID(), mssBuffer.size());
    }

    private void handleBufferSizeMessage(Node sender, BufferSizeMessage msg) {
        mssBuffersMap.put(msg.getId(), msg.getBufferSize());
    }

    private void broadcastNextRound() {
        NextRoundMessage nextRoundMessage = new NextRoundMessage();

        handleNextRoundMessage(this, nextRoundMessage);
        broadcast(nextRoundMessage);
    }

    private void proposeValue() {
        Map<String, Integer> proposedMessage = getMostRecentMHMessage();
        proposedValue = proposedMessage.get("value");
        ProposeValueMessage proposeMessage = new ProposeValueMessage(proposedValue, proposedMessage.get("timestamp"));
        logger.logln(LogL.infoLog, "[MSSNode " + this.getID() + "] Send proposed value " + proposedValue + " to coordinator " + coordinator.getID());
        propose = true;

        send(proposeMessage, coordinator);

        if (this.getID() == coordinatorId) {
            handleProposeValueMessage(this, proposeMessage);
        }
    }

    @Override
    public void preStep() {
        totalMSSNodes = discoverTotalMSSNodes();
        totalMHNodes = discoverTotalMHNodes();
        coordinator = (MSSNode) findCoordinator();

        if (!propose && allMHSent && !decided) {
            if (!mssBuffer.isEmpty()) {
                proposeValue();
            }
        }

        if (decided) {
            broadcast(new ProposedValueDefinedMessage(proposedValue));
        }
    }

    @Override
    public void init() {
        coordinatorBuffer = new ArrayList<ProposeValueMessage>();
        ackBuffer = new ArrayList<AckMessage>();
        nackBuffer = new ArrayList<NackMessage>();
        mssBuffer = new ArrayList<MHValueMessage>();
        mssBuffersMap = new HashMap<>();
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
        Color bckup = g.getColor();
        Color nodeColor;

        if (!decided) {
            nodeColor = Color.CYAN;
        } else {
            nodeColor = Color.MAGENTA;
        }

        super.drawNodeAsSquareWithText(g, pt, highlight, "ID: " + this.getID() + "|R: " + round + "|P: " + proposedValue, 50, nodeColor);

        g.setColor(Color.LIGHT_GRAY);
        pt.translateToGUIPosition(this.getPosition());
        int r = (int) (radius * pt.getZoomFactor());
        g.drawOval(pt.getGuiX() - r, pt.getGuiY() - r, r * 2, r * 2);
        g.setColor(bckup);
    }

    @Override
    public void postStep() {
        // broadcast defined value if coordinator buffer contains at least (n + 1) / 2 values
        if (coordinatorBuffer.size() >= (totalMSSNodes + 1) / 2 && !coordinatorAlreadyProposedValue) {
            int value = getMostRecentProposedValue();
            TryValueMessage tryValueMessage = new TryValueMessage(value);

            coordinatorAlreadyProposedValue = true;
            proposedValue = value;
            coordinatorBuffer.clear();

            logger.logln(LogL.infoLog, "[Coordinator " + this.getID() +"] Proposing try value " + tryValueMessage.getValue());
            broadcast(tryValueMessage);
        }

        if (!allMHSent) {
            updateMSSNeighboursBufferSize();
        }

        checkAllMHSent();

        // Always broadcast notify round message. When any MH node receive this message it update round there and know if can propose another value
        broadcast(new NotifyRoundMessage(round));

        ts++;
    }

    private void updateMSSNeighboursBufferSize() {
        broadcast(new BufferSizeMessage(mssBuffer.size(), (int) this.getID()));
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

    private Map<String, Integer> getMostRecentMHMessage() {
        mssBuffer.sort(new MHValueComparator());
        Map<String, Integer> mostRecentMessage = new HashMap<>();

        mostRecentMessage.put("value", mssBuffer.get(0).getValue());
        mostRecentMessage.put("timestamp", mssBuffer.get(0).getValue());
        return mostRecentMessage;
    }

    private int getMostRecentProposedValue() {
        coordinatorBuffer.sort(new ProposeValueComparator());

        return coordinatorBuffer.get(0).getValue();
    }

    private void initialState() {
        propose = false;
        proposedValue = 0;
        allMHSent = false;
        coordinatorAlreadyProposedValue = false;
        coordinatorBuffer.clear();
        ackBuffer.clear();
        nackBuffer.clear();
        mssBuffer.clear();
        mssBuffersMap.clear();
    }

    private void updateRound() {
        round++;
        logger.logln(LogL.infoLog, "Skip to round: " + round);
    }

    private void updateCoordinator() {
        coordinatorId = (round % totalMSSNodes + 1);
        coordinator = (MSSNode) findCoordinator();
        logger.logln(LogL.infoLog, "New coordinator with ID: " + coordinatorId);
    }

    private int discoverTotalMSSNodes() {
        int totalNodes = 0;
        Iterator<Node> nodes = SinalgoRuntime.getNodes().iterator();

        while (nodes.hasNext()) {
            Node currentNode = nodes.next();

            if (currentNode instanceof MSSNode) { totalNodes++; }
        }

        return totalNodes;
    }

    private int discoverTotalMHNodes() {
        int totalNodes = 0;
        Iterator<Node> nodes = SinalgoRuntime.getNodes().iterator();

        while (nodes.hasNext()) {
            Node currentNode = nodes.next();

            if (currentNode instanceof MHNode) { totalNodes++; }
        }

        return totalNodes;
    }

    private void checkAllMHSent() {
        int totalBuffersSize = 0;
        for (int size : mssBuffersMap.values()) {
            totalBuffersSize += size;
        }

        if (totalBuffersSize == totalMHNodes) {
            allMHSent = true;
        }
    }
}
