/**
 * Partitioned local depth.
 * GENERAL CASE: not all sets friends.get(x) need be the same size.
 * This may be applied to KNN approximation from KNNDescent class.
 * Refs:
 * [1] Kenneth S. Berenhaut1,*, Katherine E. Moore1, Ryan L. Melvin1,2.
 * Communities in Data: A Socially-Motivated Perspective on Cohesion and Clustering, 2020
 * [2] R. W. R. DARLING, EFFICIENT  LOW  DIMENSIONAL  EMBEDDING  OF  CONCORDANT RANKING  SYSTEMS, 2020
 */
package algorithms;

import com.google.common.graph.*;
import utilities.GraphUtils;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.function.Function;
import java.util.function.ToDoubleBiFunction;
import java.util.function.ToIntBiFunction;
import java.util.stream.Collectors;

/**
 * @author rwrd
 * @since May 2020 - major revision after correct formulas discovered
 * Premise: all strangers have tied rank K+1.
 */
public class CohesionGraphBuilder<V> {
    /*
     * Key set of friends consists of the points. The friends will be passed in
     * sorted order. Not all sets friends.get(x) need be the same size.
     */
    final Map<V, SortedSet<V>> friends; // keyset = points.
    /*
     * undirected edge {x,y} carries integer |V_{x,y}|, when y is a friend of x
     */
    MutableValueGraph<V, Integer> focusGraph;
    /*
     * Edge {x, y} in mutual friend graph indicates x & y are mutual friends. Degree
     * of vertex x counts mutual friends of x
     */
    MutableGraph<V> mutualFriendGraph;
    /*
     * Allows loops. Arc x->y carries cohesion value
     */
    MutableValueGraph<V, Double> cohesionGraph; // Cohesion values are rescaled by (n-1) factor
    MutableGraph<V> clusterGraph; // select edges of cohesionGraph whose weight is above average
    Graph<Set<V>> stronglyConnectedComponents; // strong components of cluster graph (each node = one component)
    double empiricalMeanCohesion, theoreticalMeanCohesion; // rescaled by (n-1) factor
    double nV;

    /**
     * @param neighborSets (sorted)
     */
    public CohesionGraphBuilder(Map<V, SortedSet<V>> neighborSets) {
        /*
         * The sorted set is cast as UNMODIFIABLE
         */
        this.friends = neighborSets.keySet().parallelStream().collect(
                Collectors.toMap(Function.identity(), x -> Collections.unmodifiableSortedSet(neighborSets.get(x))));
        this.nV = (double) this.friends.keySet().size(); // total size of S
        /////////////////////// FOCUS GRAPH
        /////////////////////// //////////////////////////////////////////////////
        /*
         * Given {x,y} return # elements |V_{x,y}|. Assume always that y is a friend of
         * x. However x need not be a friend of y. Proposition 4.1 in Darling paper
         */
        ToIntBiFunction<V, V> kFocusCounter = (x, y) -> {
            int xRanksY = this.friends.get(x).headSet(y).size() + 1; // how x ranks y
            int counter; // intersection count
            // Case where x is NOT a friend of y
            if (!this.friends.get(x).contains(y)) {
                /*
                 * Among z for which r_x(z) < r_z(y), how many are friends of y (even though x
                 * is not a friend of y)?
                 */
                counter = 0;
                for (V z : this.friends.get(x).headSet(y)) {
                    if (this.friends.get(y).contains(z)) {
                        counter++;
                    }
                }
                return Integer.valueOf(xRanksY + this.friends.get(y).size() + 1 - counter);
            } else // case where y is a friend of x
            {
                int yRanksX = this.friends.get(y).headSet(x).size() + 1; // how y ranks x
                /*
                 * Among z for which r_x(z) < r_z(y), how many also satisfy r_y(z) < r_y(x)?
                 */
                counter = 0;
                for (V z : this.friends.get(x).headSet(y)) {
                    if (this.friends.get(y).headSet(x).contains(z)) {
                        counter++;
                    }
                }
                return Integer.valueOf(xRanksY + yRanksX - counter);
            }
        };
        /*
         * Build UNDIRECTED focus graph, with integer weights |V_{x,y}| computed by
         * kFocusCounter. At the same time determine determine the "mutual friend"
         * subset of its edges.
         */
        this.focusGraph = ValueGraphBuilder.undirected().expectedNodeCount(this.friends.keySet().size()).build();
        this.mutualFriendGraph = GraphBuilder.undirected().expectedNodeCount(this.friends.keySet().size()).build();
        for (V x : this.friends.keySet()) {
            for (V y : this.friends.get(x)) {
                /*
                 * If x is already a friend of y, we can declare {x, y} mutual friends
                 */
                if (this.focusGraph.hasEdgeConnecting(y, x)) {
                    this.mutualFriendGraph.putEdge(x, y);
                }
                /*
                 * Otherwise, compute this edge weight & insert edge
                 */
                else {
                    this.focusGraph.putEdgeValue(x, y, kFocusCounter.applyAsInt(x, y));
                }
            }
        }
        /////////////////////// COHESION GRAPH (revised 5.21.20)
        /////////////////////// //////////////////////////////////////////////////
        ToDoubleBiFunction<V, V> cohesionScore = (x, v) -> {
            double nStrangers = nV - (double) this.friends.get(x).size() - 1.0;
            // loop x-> x
            double sumInv;
            if (x.equals(v)) {
                sumInv = 0.0;
                /*
                 * First sum over the MUTUAL friends y of x.
                 */
                for (V y : this.mutualFriendGraph.adjacentNodes(x)) {
                    sumInv += 1.0 / this.focusGraph.edgeValueOrDefault(x, y, Integer.valueOf(0));
                }
                /*
                 * Add contributions from strangers, and friends y which are NOT mutual. Depends
                 * on #friends - #mutualfriends
                 */
                int nonmutuals = this.friends.get(x).size() - this.mutualFriendGraph.degree(x);
                sumInv += (nStrangers + (double) nonmutuals) / nV;
            } else { // v is different to x, but is a friend of x
                sumInv = 0.0;
                double summand;
                for (V y : this.friends.get(x).tailSet(v)) {
                    /*
                     * NEW 5.21.20 Value of summand depends on whether x and y are mutual friends
                     */
                    if (this.mutualFriendGraph.hasEdgeConnecting(x, y)) {
                        summand = 1.0 / this.focusGraph.edgeValueOrDefault(x, y, Integer.valueOf(0));
                    } else {
                        summand = 1.0 / nV;
                    }
                    // Tie-breaker in case of equality with v
                    if (v.equals(y)) {
                        sumInv += 0.5 * summand; // when y = v, only half the value is added
                    } else {
                        sumInv += summand;
                    }
                }
                /*
                 * Add in contribution from strangers of x
                 */
                sumInv += nStrangers / nV;
            }
            return sumInv; // actually (n-1)* cohesion
        };
        /*
         * Build DIRECTED weighted cohesion graph, with loops
         */
        double weightedTrace = 0.0;
        double w;
        this.cohesionGraph = ValueGraphBuilder.directed().expectedNodeCount(this.friends.keySet().size())
                .allowsSelfLoops(true).build();
        for (V x : this.friends.keySet()) {
            w = cohesionScore.applyAsDouble(x, x);
            this.cohesionGraph.putEdgeValue(x, x, w); // self-loop
            weightedTrace += w;// add diagonal term to trace
            for (V v : this.friends.get(x)) {
                this.cohesionGraph.putEdgeValue(x, v, cohesionScore.applyAsDouble(x, v)); // arc
            }
        }
        // Divide weighted trace by (2 |S|)
        this.empiricalMeanCohesion = 0.5 * weightedTrace *(this.nV - 1.0)/this.nV; //rescaled for readability
        this.theoreticalMeanCohesion = this.clusterThreshold();
        // Theoretical mean cohesion from formula

        /*
         * Build DIRECTED unweighted cluster graph, no loops. Select the edges of the
         * cohesionGraph of above average weight
         */
        this.clusterGraph = GraphBuilder.directed().allowsSelfLoops(false)
                .expectedNodeCount(this.friends.keySet().size()).build();
        for (EndpointPair<V> e : this.cohesionGraph.edges()) {
            // exclude loops
            if ((!e.source().equals(e.target()))
                    && (this.cohesionGraph.edgeValueOrDefault(e, 0.0) > this.empiricalMeanCohesion)) {
                this.clusterGraph.putEdge(e);
            }
        }
        /*
         * Strongly connected components of cluster graph
         */
        this.stronglyConnectedComponents = GraphUtils.findStronglyConnectedComponents(this.clusterGraph);
    }
    /*
     * Returns the value of (n-1)*tau
     */
    private double clusterThreshold(){
        // sum reciprocals of K-focus sizes, over pairs of mutual friends
        double sumMF = this.focusGraph.edges().stream().filter(epp->this.mutualFriendGraph.hasEdgeConnecting(epp))
                .mapToDouble(epp->1.0/(double)this.focusGraph.edgeValueOrDefault(epp, 1)).sum();
        double numMF= (double)this.mutualFriendGraph.edges().size(); // #  pairs of mutual friends
        double tau = 0.5 + (sumMF - 0.5)/this.nV - numMF/(this.nV * this.nV);// 2nd term is O(K) in size
        return tau;
    }
    /**
     * @return the focusGraph
     */
    public MutableValueGraph<V, Integer> getFocusGraph() {
        return focusGraph;
    }

    /**
     * @return the mutualFriendGraph
     */
    public MutableGraph<V> getMutualFriendGraph() {
        return mutualFriendGraph;
    }

    /**
     * @return the cohesionGraph
     */
    public MutableValueGraph<V, Double> getCohesionGraph() {
        return cohesionGraph;
    }

    /**
     * @return the friends
     */
    public Map<V, SortedSet<V>> getFriends() {
        return friends;
    }

    /**
     * @return the meanCohesionValue
     */
    public double getEmpiricalMeanCohesion() {
        return empiricalMeanCohesion;
    }

    /**
     * @return the theoreticalMeanCohesion
     */
    public double getTheoreticalMeanCohesion() {
        return theoreticalMeanCohesion;
    }

    /**
     * @return the clusterGraph
     */
    public MutableGraph<V> getClusterGraph() {
        return clusterGraph;
    }

    public Graph<Set<V>> getStronglyConnectedComponents() {
        return stronglyConnectedComponents;
    }



}
