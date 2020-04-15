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

import java.util.Collections;
import java.util.Map;
import java.util.SortedSet;
import java.util.function.Function;
import java.util.function.ToDoubleBiFunction;
import java.util.function.ToIntBiFunction;
import java.util.stream.Collectors;

import com.google.common.graph.EndpointPair;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraphBuilder;

/**
 * @author rwrd
 * @since April 2020
 */
public class CohesionGraphBuilder<V> {
	/*
	 * Key set of friends consists of the points. The friends will be passed in
	 * sorted order. Not all sets friends.get(x) need be the same size.
	 */
	final Map<V, SortedSet<V>> friends; // keyset = points.
	MutableValueGraph<V, Integer> focusGraph; // undirected edge {x,y} carries integer |V_{x,y}|
	MutableValueGraph<V, Double> cohesionGraph; // arc x->y carries cohesion value
	MutableGraph<V> clusterGraph; // select edges of cohesionGraph whose weight is above average
	double meanCohesionValue;

	/**
	 * @param neighborSets (sorted)
	 */
	public CohesionGraphBuilder(Map<V, SortedSet<V>> neighborSets) {
		/*
		 * The sorted set is cast as UNMODIFIABLE
		 */
		this.friends = neighborSets.keySet().parallelStream().collect(
				Collectors.toMap(Function.identity(), x -> Collections.unmodifiableSortedSet(neighborSets.get(x))));

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
		 * kFocusCounter
		 */
		this.focusGraph = ValueGraphBuilder.undirected().expectedNodeCount(this.friends.keySet().size()).build();
		for (V x : this.friends.keySet()) {
			for (V y : this.friends.get(x)) {
				/*
				 * Do not compute this edge weight if already {y, x} was inserted
				 */
				if (!this.focusGraph.hasEdgeConnecting(y, x)) {
					this.focusGraph.putEdgeValue(x, y, kFocusCounter.applyAsInt(x, y));
				}
			}
		}
		/////////////////////// COHESION GRAPH
		/////////////////////// //////////////////////////////////////////////////
		ToDoubleBiFunction<V, V> cohesionScore = (x, v) -> {
			// loop x-> x
			double sumInv;
			if (x.equals(v)) {
				sumInv = 0.0;
				for (V y : this.friends.get(x)) {
					sumInv += 1.0 / this.focusGraph.edgeValueOrDefault(x, y, Integer.valueOf(0));
				}
			} else {
				sumInv = 0.0;
				double summand;
				for (V y : this.friends.get(x).tailSet(v)) {
					summand = 1.0 / this.focusGraph.edgeValueOrDefault(x, y, Integer.valueOf(0));
					if (v.equals(y)) {
						sumInv += 0.5 * summand; // when y = v, only half the value is added
					} else {
						sumInv += summand;
					}
				}
			}
			return sumInv / (double) this.friends.get(x).size();
		};
		/*
		 * Build DIRECTED weighted cohesion graph, with loops
		 */
		this.cohesionGraph = ValueGraphBuilder.directed().expectedNodeCount(this.friends.keySet().size())
				.allowsSelfLoops(true).build();
		double weightedTrace = 0.0;
		double w;
		for (V x : this.friends.keySet()) {
			w = cohesionScore.applyAsDouble(x, x);
			this.cohesionGraph.putEdgeValue(x, x, w); // self-loop
			weightedTrace += w * (double) this.friends.get(x).size(); // D_{x,x} * K_x
			for (V y : this.friends.get(x)) {
				this.cohesionGraph.putEdgeValue(x, y, cohesionScore.applyAsDouble(x, y));
			}
		}
		// Divide weighted trace by the sum of the sizes of the friend sets
		double sumFriendSetSizes = (double) this.friends.values().stream().mapToInt(s -> s.size()).sum();
		this.meanCohesionValue = 0.5 * weightedTrace / sumFriendSetSizes;
		/*
		 * Build DIRECTED unweighted cluster graph, no loops. Select the edges of the
		 * cohesionGraph of above average weight
		 */
		this.clusterGraph = GraphBuilder.directed().allowsSelfLoops(false)
				.expectedNodeCount(this.friends.keySet().size()).build();
		for (EndpointPair<V> e : this.cohesionGraph.edges()) {
			// exclude loops
			if ((!e.source().equals(e.target()))
					&& (this.cohesionGraph.edgeValueOrDefault(e, 0.0) > this.meanCohesionValue)) {
				this.clusterGraph.putEdge(e);
			}
		}
	}

	/*
	 * Invert all the arrows x->y, where y is a friend of x. The co-friends of x is
	 * an unmodifiable set. NOT NEEDED.
	 * 
	 */
	/*
	 * private Map<V, Set<V>> transpose(Map<V, SortedSet<V>> outArcs) { Map<V,
	 * Set<V>> inArcs = outArcs.keySet().parallelStream()
	 * .collect(Collectors.toMap(Function.identity(), x -> new HashSet<V>())); for
	 * (V x : outArcs.keySet()) { for (V y : outArcs.get(x)) { inArcs.get(y).add(x);
	 * // since y is a friend of x, add x to co-friends of y } } return
	 * inArcs.keySet().parallelStream()
	 * .collect(Collectors.toMap(Function.identity(), y ->
	 * Collections.unmodifiableSet(inArcs.get(y)))); }
	 */

	/**
	 * @return the focusGraph
	 */
	public MutableValueGraph<V, Integer> getFocusGraph() {
		return focusGraph;
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
	public double getMeanCohesionValue() {
		return meanCohesionValue;
	}

	/**
	 * @return the clusterGraph
	 */
	public MutableGraph<V> getClusterGraph() {
		return clusterGraph;
	}

}