package de.dafuqs.spectrum.blocks.pastel_network.network;

import de.dafuqs.spectrum.blocks.pastel_network.nodes.*;
import de.dafuqs.spectrum.helpers.ColorHelper;
import de.dafuqs.spectrum.helpers.*;
import net.minecraft.block.entity.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import org.jetbrains.annotations.*;
import org.jgrapht.*;
import org.jgrapht.graph.*;

import java.util.*;
import java.util.concurrent.*;

public class PastelNetwork {
    
    protected final Map<PastelNodeType, Set<PastelNodeBlockEntity>> nodes = new ConcurrentHashMap<>();
    protected final Set<PastelNodeBlockEntity> priorityNodes = new HashSet<>();
    protected final Set<PastelNodeBlockEntity> highPriorityNodes = new HashSet<>();
    protected @Nullable Graph<PastelNodeBlockEntity, DefaultEdge> graph;
	protected final World world;
	protected final UUID uuid;
	protected final SchedulerMap<PastelTransmission> transmissions = new SchedulerMap<>();

    public enum Priority {
        GENERIC,
        MODERATE,
        HIGH
    }
	
	public PastelNetwork(World world, @Nullable UUID uuid) {
		this.world = world;
		this.uuid = uuid == null ? UUID.randomUUID() : uuid;
		for (PastelNodeType type : PastelNodeType.values()) {
			this.nodes.put(type, new HashSet<>());
		}
	}

    public void incorporate(PastelNetwork networkToIncorporate) {
        for (Map.Entry<PastelNodeType, Set<PastelNodeBlockEntity>> nodesToIncorporate : networkToIncorporate.getNodes().entrySet()) {
            PastelNodeType type = nodesToIncorporate.getKey();
            for (PastelNodeBlockEntity nodeToIncorporate : nodesToIncorporate.getValue()) {
                this.nodes.get(type).add(nodeToIncorporate);
                nodeToIncorporate.setParentNetwork(this);
                updateNodePriority(nodeToIncorporate, nodeToIncorporate.getPriority());
            }
        }
        this.graph = null;
    }

    public World getWorld() {
        return this.world;
    }

    public Graph<PastelNodeBlockEntity, DefaultEdge> getGraph() {
        if (this.graph == null) {
            this.graph = buildGraph(this);
        }
        return this.graph;
    }

    private static @NotNull SimpleGraph<PastelNodeBlockEntity, DefaultEdge> buildGraph(@NotNull PastelNetwork network) {
        SimpleGraph<PastelNodeBlockEntity, DefaultEdge> g = new SimpleGraph<>(DefaultEdge.class);

        for (PastelNodeBlockEntity node : network.getAllNodes()) {
            g.addVertex(node);
        }

        for (PastelNodeBlockEntity node : network.getAllNodes()) {
            for (PastelNodeBlockEntity node2 : network.getAllNodes()) {
                if (node == node2) {
                    continue;
                }
                if (node.canConnect(node2)) {
                    g.addEdge(node, node2);
                }
            }
        }

        return g;
    }

    public void addNode(PastelNodeBlockEntity node) {
        if (!this.nodes.get(node.getNodeType()).add(node)) {
            return;
        }

        // calculate connections for new node
        if (this.graph != null) {
            this.graph.addVertex(node);
            for (PastelNodeBlockEntity existingNode : this.getAllNodes()) {
                if (node == existingNode) {
                    continue;
                }
                if (node.canConnect(existingNode)) {
                    this.graph.addEdge(node, existingNode);
                }
            }
        }

        // check for priority
        addPriorityNode(node);
    }

    private void addPriorityNode(PastelNodeBlockEntity node) {
        switch (node.getPriority()) {
            case MODERATE -> priorityNodes.add(node);
            case HIGH -> highPriorityNodes.add(node);
        }
    }

    public void updateNodePriority(PastelNodeBlockEntity node, Priority oldPriority) {
        removePriorityNode(node, oldPriority);
        addPriorityNode(node);
    }

    protected boolean removeNode(PastelNodeBlockEntity node, NodeRemovalReason reason) {
        boolean hadNode = this.nodes.get(node.getNodeType()).remove(node);
        if (!hadNode) {
            return false;
        }

        if (this.graph != null) {
            // delete the now removed node from this networks graph
            this.graph.removeVertex(node);
        }

        removePriorityNode(node, node.getPriority());

        return true;
    }

    private void removePriorityNode(PastelNodeBlockEntity node, Priority priority) {
        switch (priority) {
            case MODERATE -> priorityNodes.remove(node);
            case HIGH -> highPriorityNodes.remove(node);
        }
    }

    public boolean hasNodes() {
        for (Set<PastelNodeBlockEntity> nodeList : this.nodes.values()) {
            if (!nodeList.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public Set<PastelNodeBlockEntity> getNodes(PastelNodeType type) {
        return getNodes(type, Priority.GENERIC);
    }

    public Set<PastelNodeBlockEntity> getNodes(PastelNodeType type, Priority priority) {
        return switch (priority) {
            case GENERIC -> this.nodes.get(type);
            case MODERATE -> this.priorityNodes;
            case HIGH -> this.highPriorityNodes;
        };
    }

    public Map<PastelNodeType, Set<PastelNodeBlockEntity>> getNodes() {
        return this.nodes;
    }

    public int getNodeCount() {
        int nodes = 0;
        for (Set<PastelNodeBlockEntity> nodeList : this.nodes.values()) {
            nodes += nodeList.size();
        }
        return nodes;
    }

    public List<PastelNodeBlockEntity> getAllNodes() {
        List<PastelNodeBlockEntity> nodes = new ArrayList<>();
        for (Map.Entry<PastelNodeType, Set<PastelNodeBlockEntity>> nodeList : this.nodes.entrySet()) {
            nodes.addAll(this.nodes.get(nodeList.getKey()));
        }
        return nodes;
    }

    public boolean canConnect(PastelNodeBlockEntity newNode) {
        if (newNode.getWorld() != this.getWorld()) {
            return false;
        }

        for (Set<PastelNodeBlockEntity> nodeList : this.nodes.values()) {
            for (PastelNodeBlockEntity currentNode : nodeList) {
                if (currentNode.canConnect(newNode)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void tick() {
        transmissions.tick();
    }



    public UUID getUUID() {
        return this.uuid;
    }

    public void addTransmission(PastelTransmission transmission, int travelTime) {
        transmission.setNetwork(this);
        this.transmissions.put(transmission, travelTime);
    }

    public int getColor() {
        return ColorHelper.getRandomColor(this.uuid.hashCode());
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof PastelNetwork p) {
            return this.uuid.equals(p.uuid);
        }
        return false;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(this.uuid.toString());
        for (PastelNodeType type : PastelNodeType.values()) {
            builder.append("-").append(getNodes(type).size());
        }
        return builder.toString();
    }

    public String getNodeDebugText() {
        return "Prov: " +
                getNodes(PastelNodeType.PROVIDER).size() +
                " - Send: " +
                getNodes(PastelNodeType.SENDER).size() +
                " - Gath: " +
                getNodes(PastelNodeType.GATHER).size() +
                " - Stor: " +
                getNodes(PastelNodeType.STORAGE).size() +
                " - Buff: " +
                getNodes(PastelNodeType.BUFFER).size() +
                " - Conn: " +
                getNodes(PastelNodeType.CONNECTION).size();
    }

    public PastelNodeBlockEntity getNodeAt(BlockPos blockPos) {
        BlockEntity blockEntity = this.getWorld().getBlockEntity(blockPos);
        if (blockEntity instanceof PastelNodeBlockEntity pastelNodeBlockEntity) {
            return pastelNodeBlockEntity;
        }
        return null;
    }

}
