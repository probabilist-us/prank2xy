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
	ToIntBiFunction<V, V> kFocus;
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
		this.focusGraph = ValueGraphBuilder.undirected().expectedNodeCount(neighborSets.keySet().size()).build();
		this.buildFocusGraph(); // contains statistics needed for cohesion graph building
		this.cohesionGraph = ValueGraphBuilder.directed().expectedNodeCount(neighborSets.keySet().size()).build();
		/*
		 * Given {x,y} return # elements |V_{x,y}|.
		 * Compute this
		 * i
		 */
		this.kFocus = (x, y) -> {
			if (!this.friends.get(x).contains(y)) {
				return Integer.valueOf(1 + this.k);
			} else {
				return Integer.valueOf(2 + this.friends.get(x).headSet(y).size());
			}
		};
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

	/*
	 * Since graph is undirected, it suffices to traverse unordered pairs {x, y}
	 * UNFINISHED
	 */
	private void buildFocusGraph() {
		int xRanksY, yRanksX;
		for (V x : this.friends.keySet()) {
			xRanksY = 0; // how many friends z satisfy r_x(z) < r_x(y), excluding x
			for (V y : this.friends.get(x)) {
				// Case 0: x is not a friend of y
				if(!this.friends.get(y).contains(x)) {
					
				}
					
					// how many z satisfy r_y(z) < r_y(x), excluding y
				yRanksX = this.friends.get(y).contains(x)? this.friends.get(y).headSet(x).size() : this.k;
				
				
				this.focusGraph.putEdgeValue(x, y, 0);
				xRanksY++;
			}
		}
	}

}