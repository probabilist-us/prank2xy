/**
 * Kosaraju's algorithm for strongly connected components of a directed graph.
 * 
 * https://en.wikipedia.org/wiki/Kosaraju%27s_algorithm
 * @author  WorldSEnder, 2017
 * Richard Darling adds:
 * StronglyConnectedComponentsTest verified it on 5.6.2020
 */
package utilities;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.graph.ElementOrder;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.MutableGraph;

public class GraphUtils {
    private GraphUtils() {
    }

    /**
     * Guarantees: the graph will be directed and forest-like without self loops.
     * 
     * @param graph
     * @return the SCC graph. each node contains all the nodes in the CC of the original graph
     */
    public static <T> Graph<Set<T>> findStronglyConnectedComponents(Graph<T> graph) {
        if (graph.nodes().isEmpty()) {
            throw new IllegalArgumentException("Can't find components in an empty graph");
        }
        final MutableGraph<Set<T>> result = GraphBuilder.directed().allowsSelfLoops(false)
                .nodeOrder(ElementOrder.insertion()).build();
        // Kosaraju's algorithm

        final Map<T, Set<T>> ccStore = new HashMap<>(graph.nodes().size());
        // Step 1
        final ImmutableList<T> topologicalOrder = GraphUtils.traverse(graph).postOrderTraversal(graph.nodes()).toList()
                .reverse();
        // Step 2
        final Graph<T> transposeGraph = Graphs.transpose(graph);
        // Step 3
        for (T node : topologicalOrder) {
            if (ccStore.keySet().contains(node)) {
                continue;
            }
            final Set<T> connectedComponent = new HashSet<>();
            final Set<T> hitExistingNodes = new HashSet<>();

            GraphUtils.traverse(transposeGraph)
                    .postOrderTraversal(Collections.singleton(node), ccStore.keySet(), hitExistingNodes::add)
                    .forEach(connectedComponent::add);

            result.addNode(connectedComponent);
            hitExistingNodes.forEach(n -> {
                // We encounter a connection between connected components
                Set<T> existingCC = ccStore.get(n);
                result.putEdge(existingCC, connectedComponent);
            });
            connectedComponent.forEach(n -> {
                ccStore.put(n, connectedComponent);
            });
        }

        return result;
    }

    public static <T> GraphTraverser<T> traverse(Graph<T> graph) {
        return new GraphTraverser<>(graph);
    }
}
