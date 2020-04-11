/**
 * Partitioned local depth, applied to KNN approximation from KNNDescent class.
 * REfs:
 * [1] Kenneth S. Berenhaut1,*, Katherine E. Moore1, Ryan L. Melvin1,2.
 * Communities in Data: A Socially-Motivated Perspective on Cohesion and Clustering, 2020
 * [2] R. W. R. DARLING, EFFICIENT  LOW  DIMENSIONAL  EMBEDDING  OF  CONCORDANT RANKING  SYSTEMS, 2020
 */
package algorithms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntBiFunction;
import java.util.stream.Collectors;

import com.google.common.graph.EndpointPair;
import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraphBuilder;

/**
 * @author rwrd
 *
 */
public class CohesionGraphBuilder<V> {
	/*
	 * Key set of friends consist of the points The friends will be passed in
	 * unsorted. Sorting will occur
	 */
	final List<V> points; // remove later
	final Map<V, SortedSet<V>> friends; // keyset = points; each value set has exactly K elements
	final Map<V, Set<V>> coFriends;
	final int k;
	Function<V, Comparator<V>> crs; // concordant ranking system on the set of points
	MutableValueGraph<V, Integer> focusGraph; // undirected edge {x,y} carries # elements |V_{x,y}|
	MutableValueGraph<V, Double> cohesionGraph; // arc x->y carries cohesion value

	// V_{x, y} K-focus counts

	/**
	 * 
	 */
	public CohesionGraphBuilder(Map<V, SortedSet<V>> neighborSets, Function<V, Comparator<V>> rankingSystem) {
		/*
		 * The sorted set is cast as UNMODIFIABLE
		 */
		this.points = new ArrayList<V>(neighborSets.keySet()); // toss this
		this.friends = neighborSets.keySet().parallelStream().collect(
				Collectors.toMap(Function.identity(), x -> Collections.unmodifiableSortedSet(neighborSets.get(x))));
		this.k = this.friends.get(this.points.get(0)).size();
		this.crs = rankingSystem;
		// Initialize
		this.coFriends = this.transpose(neighborSets);
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
				return Integer.valueOf(xRanksY + this.k + 1 - counter);
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
		this.focusGraph = ValueGraphBuilder.undirected().expectedNodeCount(neighborSets.keySet().size()).build();
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

		this.cohesionGraph = ValueGraphBuilder.directed().expectedNodeCount(neighborSets.keySet().size()).build();
	}

	ToDoubleFunction<EndpointPair<V>> cohesion = e -> {
		return 0.0;
	};

	/*
	 * Invert all the arrows x->y, where y is a friend of x. The co-friends of x is
	 * an unmodifiable set.
	 * 
	 */
	private Map<V, Set<V>> transpose(Map<V, SortedSet<V>> outArcs) {
		Map<V, Set<V>> inArcs = outArcs.keySet().parallelStream()
				.collect(Collectors.toMap(Function.identity(), x -> new HashSet<V>()));
		for (V x : outArcs.keySet()) {
			for (V y : outArcs.get(x)) {
				inArcs.get(y).add(x); // since y is a friend of x, add x to co-friends of y
			}
		}
		return inArcs.keySet().parallelStream()
				.collect(Collectors.toMap(Function.identity(), y -> Collections.unmodifiableSet(inArcs.get(y))));
	}

}