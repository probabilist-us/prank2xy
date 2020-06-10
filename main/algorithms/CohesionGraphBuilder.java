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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntBiFunction;
import java.util.stream.Collectors;

import com.google.common.graph.EndpointPair;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraphBuilder;

import utilities.GraphUtils;

/**
 * @author rwrd
 * @since May 2020 - major revision after correct formulas discovered Premise:
 *        all strangers have tied rank K+1.
 */
public class CohesionGraphBuilder<V> {
	/*
	 * Key set of friends consists of the points. The friends will be passed in
	 * sorted order. Not all sets friends.get(x) need be the same size. NavigableSet
	 * interface allows descending iterator, useful for cohesion matrix.
	 */
	final Map<V, NavigableSet<V>> friends; // keyset = points.
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
	private double nV, duration;
	private long start;

	/**
	 * @param neighborSets (sorted)
	 */
	public CohesionGraphBuilder(Map<V, NavigableSet<V>> neighborSets) {
		/*
		 * The sorted set is cast as UNMODIFIABLE
		 */
		this.friends = neighborSets.keySet().parallelStream().collect(
				Collectors.toMap(Function.identity(), x -> Collections.unmodifiableNavigableSet(neighborSets.get(x))));
		this.nV = (double) this.friends.keySet().size(); // total size of S
		/////////////////////// FOCUS GRAPH
		/////////////////////// //////////////////////////////////////////////////
		/*
		 * Given {x,y} return # elements |V_{x,y}|. Assume always that y=x, or y is a
		 * friend of x. However x need not be a friend of y. Proposition 6.1 in Darling
		 * paper
		 */
		ToIntBiFunction<V, V> kFocusCounter = (x, y) -> {
			int xRanksY = this.friends.get(x).headSet(y).size() + 1; // how x ranks y
			int counter; // intersection count
			// Case where x is NOT a friend of y; i.e. y is a friend, not a co-friend
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
			} else // case where x is a friend of y, i.e. y s a co-friend as well as a friend
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
		 * kFocusCounter. Loops are NOT allowed. At the same time determine determine
		 * the "mutual friend" subset of its edges, a subgraph in which loops are NOT
		 * allowed.
		 */
		this.start = System.currentTimeMillis();
		this.focusGraph = ValueGraphBuilder.undirected().allowsSelfLoops(false)
				.expectedNodeCount(this.friends.keySet().size()).build();
		this.mutualFriendGraph = GraphBuilder.undirected().allowsSelfLoops(false)
				.expectedNodeCount(this.friends.keySet().size()).build();
		for (V x : this.friends.keySet()) {
			for (V y : this.friends.get(x)) {
				/*
				 * If x is already a friend of y, we can declare {x, y} mutual friends
				 */
				if (this.focusGraph.hasEdgeConnecting(y, x)) {
					this.mutualFriendGraph.putEdge(x, y); // 2nd time the pair \{x, y\} has occurred
				}
				/*
				 * Otherwise, compute this edge weight & insert edge
				 */
				else {
					this.focusGraph.putEdgeValue(x, y, kFocusCounter.applyAsInt(x, y));
				}
			}
		}
		this.duration = (double) (System.currentTimeMillis() - this.start) / 1000.0;
		System.out.println("Focus graph and mutual friend graph built in " + duration + " seconds.");

		/*
		 * Cohesion Vector Scores assigns to x the map v -> (n-1)*C_{x, v}, with v
		 * sorted by rank, INCLUDING case v = x. Using descending iterator on friends of
		 * x gives factor K speed up. Only defined when v is a friend of x, or v = x.
		 */
		Function<V, Map<V, Double>> cohesionScoreMap = x -> {
			Map<V, Double> cohesionMap = new HashMap<>();
			double sumStrangers = 1.0 - ((double) this.friends.get(x).size() - 1.0) / this.nV;
			double cohesionValue = sumStrangers; // contribution from strangers
			Iterator<V> reverseIt = this.friends.get(x).descendingIterator(); // reason for NavigableSet
			V v;
			double summand; // value depends on whether x is friend of v or not
			double summandSt = 1.0 / this.nV;
			// This loop assigns a value to C_{x, v} when v is a friend of x.
			while (reverseIt.hasNext()) {
				v = reverseIt.next();
				if (this.friends.get(v).contains(x)) {
					summand = 1.0 / (double) this.focusGraph.edgeValueOrDefault(x, v, Integer.valueOf(0));
				} else { // x is not a friend of v
					summand = summandSt;
				}
				cohesionValue += summand; // Efficiency consist in the way cohesionValue is carried forward
				// Tie-break: only half the latest summand is included in cohesion value
				cohesionMap.put(v, cohesionValue - 0.5 * summand);
			}
			cohesionMap.put(x, cohesionValue); // Case v = x: effectively adds back the last tie-breaker
			return cohesionMap;
		};

		/*
		 * COHESION GRAPH (revised 5.27.20) Build DIRECTED weighted cohesion graph, with
		 * loops. Begin by computing all the cohesion scores in parallel.
		 */
		Map<V, Map<V, Double>> cohesionMatrix = this.friends.keySet().parallelStream()
				.collect(Collectors.toMap(Function.identity(), x->cohesionScoreMap.apply(x)));
		// Insert these values as eedge weights in a directed graph
		double weightedTrace = 0.0;
		this.start = System.currentTimeMillis();
		this.cohesionGraph = ValueGraphBuilder.directed().expectedNodeCount(this.friends.keySet().size())
				.allowsSelfLoops(true).build();
		for (V x : cohesionMatrix.keySet()) {
			for (V v : cohesionScoreMap.apply(x).keySet()) { // includes v = x as one of the keys
				this.cohesionGraph.putEdgeValue(x, v, cohesionMatrix.get(x).get(v)); // arc
			}
			weightedTrace += cohesionScoreMap.apply(x).get(x);// add diagonal term to trace
		}
		this.duration = (double) (System.currentTimeMillis() - this.start) / 1000.0;
		System.out.println("With parallel cohesion scoring, cohesion graph built in " + duration + " seconds.");
		// Divide weighted trace by (2 |S|)
		this.empiricalMeanCohesion = 0.5 * weightedTrace / this.nV; // half average of diagonal of cohesion matrix
		this.theoreticalMeanCohesion = this.clusterThreshold();
		// Theoretical mean cohesion from formula

		/*
		 * Build DIRECTED unweighted cluster graph, no loops. Select the edges of the
		 * cohesionGraph of above average weight. NEW 5.27.20: delete x->y unless BOTH
		 * x->y AND y->x have weights > tau
		 * WARNING: When x->y is an edge in the cohesion graph, y->x need not be.
		 */
		this.start = System.currentTimeMillis();
		this.clusterGraph = GraphBuilder.directed().allowsSelfLoops(false)
				.expectedNodeCount(this.friends.keySet().size()).build();
		V x, y;
		double w;
		for (EndpointPair<V> e : this.cohesionGraph.edges()) {
			x = e.source();
			y = e.target();
			w = Math.min(this.cohesionGraph.edgeValueOrDefault(x, y, 0.0),
					this.cohesionGraph.edgeValueOrDefault(y, x, 0.0)); //Minimum of C_{x, y} and C_{y, x}
			if ((!e.source().equals(e.target())) && (w > this.empiricalMeanCohesion)) {
				this.clusterGraph.putEdge(e);
			}
		}
		this.duration = (double) (System.currentTimeMillis() - this.start) / 1000.0;
		System.out.println("Cluster graph built  in " + duration + " seconds.");
		/*
		 * Strongly connected components of cluster graph
		 */
		if (this.clusterGraph.edges().size() > 0) {
			this.stronglyConnectedComponents = GraphUtils.findStronglyConnectedComponents(this.clusterGraph);
		} else {
			System.out.println("Cluster graph has no edges -- all vertices are isolated.");
		}
	}

	/*
	 * Returns the value of (n-1)*tau
	 */
	private double clusterThreshold() {
		// sum reciprocals of K-focus sizes, over pairs of mutual friends
		double sumMF = this.focusGraph.edges().stream().filter(epp -> this.mutualFriendGraph.hasEdgeConnecting(epp))
				.mapToDouble(epp -> 1.0 / (double) this.focusGraph.edgeValueOrDefault(epp, 1)).sum();
		double numMF = (double) this.mutualFriendGraph.edges().size(); // # pairs of mutual friends
		double tau = 0.5 + (sumMF - 0.5) / this.nV - numMF / (this.nV * this.nV);// 2nd term is O(K) in size
		return tau;
	}

	/*
	 * OLD WAY (inefficient) ToDoubleBiFunction<V, V> cohesionScore = (x, v) -> {
	 * double sumFriends = 0.0; // Value of summand depends on whether x and y are
	 * mutual friends double summand; // Formula when v = x if (x.equals(v)) { for
	 * (V y : this.friends.get(x)) { if (this.friends.get(y).contains(x)) {
	 * sumFriends += 1.0 / (double) this.focusGraph.edgeValueOrDefault(x, y,
	 * Integer.valueOf(0)); } else { sumFriends += 1.0 / nV; } } } else {// v is
	 * different to x, but is a friend of x for (V y :
	 * this.friends.get(x).tailSet(v)) { if (this.friends.get(y).contains(x)) {
	 * summand = 1.0 / (double) this.focusGraph.edgeValueOrDefault(x, y,
	 * Integer.valueOf(0)); } else { summand = 1.0 / nV; } if (v.equals(y)) {
	 * sumFriends += 0.5 * summand; // tie-breaker } else { sumFriends += summand; }
	 * } }
	 */
	/*
	 * Add in contribution from strangers of x.
	 */
	/*
	 * double sumStrangers = 1.0 - ((double) this.friends.get(x).size() - 1.0) /
	 * this.nV; return (sumFriends + sumStrangers); // actually (n-1)* cohesion };
	 */

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
	public Map<V, NavigableSet<V>> getFriends() {
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