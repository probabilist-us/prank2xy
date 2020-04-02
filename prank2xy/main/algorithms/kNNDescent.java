/**
 * K-nearest neighbor descent.
 * References:
 * [1]Jacob D. Baron; R. W. R. Darling. K-nearest neighbor approximation via the friend-of-a-friend principle. arXiv:1908.07645,
 * [2] Dong, Wei; Moses, Charikar; Li, Kai. Efficient k-nearest neighbor graph construction for generic similarity measures. 
 * Proceedings of the 20th International Conference on World Wide Web, 577--586, 2011
 */
package algorithms;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.SplittableRandom;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author rwrd
 *
 */
public class kNNDescent<V> {
	List<V> points;
	Map<V, Comparator<V>> crs; // concordant ranking system on the set of points
	Map<V, Set<V>> friends, coFriends;
	int k;
	SplittableRandom g;
	Map<V, Boolean> noChangePossible; // when we try to refresh friends, no new candidates are offered

	/**
	 * @param Set<V>         dataPoints
	 * @param Comparator<V>> rankingSystem
	 * @param int            numberOfNeighbors
	 */
	public kNNDescent(List<V> dataPoints, Map<V, Comparator<V>> rankingSystem, int numberOfNeighbors) {
		this.k = numberOfNeighbors;
		this.points = dataPoints;
		Collections.shuffle(this.points); // This random order will be maintained through all refresh cycles
		this.crs = rankingSystem;
		this.noChangePossible = this.points.stream().collect(Collectors.toMap(Function.identity(), x -> Boolean.FALSE));
		g = new SplittableRandom();

	}

	public void initializeFriends() {
		// TODO
	}

	/*
	 * During the execution of this function, the sets friends.get(x) and
	 * coFriends.get(x) are immutable
	 */
	Function<V, Set<V>> refreshOne = x -> {
		Set<V> pool = new HashSet<>();
		for (V y : this.friends.get(x)) {
			pool.addAll(friends.get(y)); // add in friends of friends of x
		}
		for (V z : this.coFriends.get(x)) {
			pool.add(z); // add in the co-friend
			pool.addAll(friends.get(z)); // add in friends of co-friends of x
		}
		SortedSet<V> runningK = new TreeSet<V>(crs.get(x));// The comparator is the ranking from x
		runningK.addAll(this.friends.get(x)); // Initialize with the current friend set
		for (V p : pool) {
			if (crs.get(x).compare(p, runningK.last()) < 0) {
				runningK.remove(runningK.last());
				runningK.add(p);
			}
		}
		return runningK;
	};

	/*
	 * Apply the refreshOne function to all of the points, in parallel
	 */
	public void refreshAllInParallel() {
		Map<V, Set<V>> friendUpdates = this.points.parallelStream()
				.collect(Collectors.toMap(Function.identity(), x -> refreshOne.apply(x)));
		this.friends.clear();
		/*
		 * Make a shallow copy of friendUpdates, and call this the new friend Map.
		 */
		this.friends = friendUpdates.entrySet().stream()
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	public void assignCoFriends() {
		this.coFriends.clear();
		// TODO
	}

}
