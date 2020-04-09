/**
 * Partitioned local depth, applied to KNN approximation from KNNDescent class.
 * REfs:
 * [1] Kenneth S. Berenhaut1,*, Katherine E. Moore1, Ryan L. Melvin1,2.
 * Communities in Data: A Socially-Motivated Perspective on Cohesion and Clustering, 2020
 * [2] R. W. R. DARLING, EFFICIENT  LOW  DIMENSIONAL  EMBEDDING  OF  CONCORDANT RANKING  SYSTEMS, 2020
 */
package algorithms;

import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.SortedSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author rwrd
 *
 */
public class LocalDepthCohesion<V> {
	/*
	 * Key set of neighbors consist of the points The neighbors will be passed in
	 * unsorted. Sorting will occur
	 */
	final Map<V, SortedSet<V>> neighbors; // keyset = points; each value set has exactly K elements
	Function<V, Comparator<V>> crs; // concordant ranking system on the set of points
	// V_{x, y} K-focus counts

	/**
	 * 
	 */
	public LocalDepthCohesion(Map<V, SortedSet<V>> neighborSets, Function<V, Comparator<V>> rankingSystem) {
		/*
		 * The sorted set is cast as UNMODIFIABLE
		 */
		this.neighbors = neighborSets.keySet().parallelStream().collect(
				Collectors.toMap(Function.identity(), x -> Collections.unmodifiableSortedSet(neighborSets.get(x))));
		this.crs = rankingSystem;
	}
	
	

}
