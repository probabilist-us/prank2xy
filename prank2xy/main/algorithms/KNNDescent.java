/**
 * K-nearest neighbor descent.
 * References:
 * [1]Jacob D. Baron; R. W. R. Darling. K-nearest neighbor approximation via the friend-of-a-friend principle. arXiv:1908.07645,
 * [2] Dong, Wei; Moses, Charikar; Li, Kai. Efficient k-nearest neighbor graph construction for generic similarity measures. 
 * Proceedings of the 20th International Conference on World Wide Web, 577--586, 2011
 */
package algorithms;

import java.util.Comparator;
import java.util.HashSet;
import java.util.IntSummaryStatistics;
import java.util.Iterator;
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
public class KNNDescent<V> {
	List<V> points; // for random sampling, a list is preferable to a set.
	Function<V, Comparator<V>> crs; // concordant ranking system on the set of points
	Map<V, Set<V>> friends, coFriends; // keyset = points
	int k;
	int expanderBasedRoundCount; // plausible number of rounds, based on k and #{points}
	SplittableRandom g;

	/**
	 * @param Set<V>         dataPoints
	 * @param Comparator<V>> rankingSystem
	 * @param int            numberOfNeighbors
	 */
	public KNNDescent(List<V> dataPoints, Function<V, Comparator<V>> rankingSystem, int numberOfNeighbors) {
		this.k = numberOfNeighbors;
		this.points = dataPoints; // order is not important
		this.expanderBasedRoundCount = (int) Math.ceil(Math.log((double) dataPoints.size()) / Math.log((double) k)); // log_k(n)
		this.crs = rankingSystem;
		g = new SplittableRandom();
	}

	/**
	 * Parallel implementation of kNN Descent with an a priori bound on the number
	 * of rounds, and a convergence criterion. Plausible bound on number of rounds
	 * is 2*expanderBasedRoundCount. Sampling is used in the stopping criterion.
	 * 
	 * @param int    maxRounds
	 * @param double sampleRate
	 * 
	 */
	public void kNNDescentAlgorithm(int maxRounds, double sampleRate) {
		IntSummaryStatistics friendStatistics, coFriendStatistics;
		System.out.println("Starting KNN Descent with a maximum of " + maxRounds + " rounds.");
		long start = System.currentTimeMillis();
		this.initializeAllFriendSets();
		this.refreshAllCoFriendSets();
		/*
		 * Diagnostic reports
		 */
		coFriendStatistics = this.coFriendStats();
		System.out.println(
				"Initial friend sets chosen in " + (.001 * (double) (System.currentTimeMillis() - start)) + " secs.");
		System.out.println("Co-friend sets range in size from " + coFriendStatistics.getMin() + " to "
				+ coFriendStatistics.getMax() + ", mean " + coFriendStatistics.getAverage());
		/*
		 * Prepare iteration
		 */
		double oldClusterCoeff = Integer.MIN_VALUE;
		double newClusterCoeff = 0.0;
		int rounds = 0;
		while (rounds < maxRounds && newClusterCoeff > oldClusterCoeff) {
			start = System.currentTimeMillis();
			this.refreshAllFriendSets();
			this.refreshAllCoFriendSets();
			rounds++;
			oldClusterCoeff = newClusterCoeff;
			newClusterCoeff = this.estimateFriendClustering(sampleRate);
			/*
			 * Diagnostic reports
			 */
			coFriendStatistics = this.coFriendStats();
			friendStatistics = this.friendStats();		
			System.out.println("Round " + rounds + " of KNN Descent took "
					+ (.001 * (double) (System.currentTimeMillis() - start)) + " secs.");
			System.out.println("Friend sets range in size from " + friendStatistics.getMin() + " to "
					+ friendStatistics.getMax() + ", mean " + friendStatistics.getAverage());
			System.out.println("Co-friend sets range in size from " + coFriendStatistics.getMin() + " to "
					+ coFriendStatistics.getMax() + ", mean " + coFriendStatistics.getAverage());		
			System.out.println("Friend clustering coefficient = " + newClusterCoeff);
		}
		System.out.println("KNN Descent terminated after " + rounds + " rounds.");
	}

	/*
	 * Supplies a random initial set of k friends to a given point.
	 */
	Function<V, Set<V>> randomKFriends = x -> {
		Set<V> reachOut = new HashSet<>();
		V y;
		while (reachOut.size() < this.k) {
			y = this.points.get(g.nextInt(points.size()));
			if (!x.equals(y)) {
				reachOut.add(y); // nothing happens if y is in the set already
			}
		}
		return reachOut;
	};

	/*
	 * Every point is assigned a random set of k friends, in parallel. The
	 * co-friends are initially empty. The computation is postponed to the next
	 * method.
	 */
	public void initializeAllFriendSets() {
		this.friends = this.points.parallelStream()
				.collect(Collectors.toMap(Function.identity(), x -> randomKFriends.apply(x)));
		this.coFriends = this.points.parallelStream()
				.collect(Collectors.toMap(Function.identity(), x -> new HashSet<V>()));
	}

	/*
	 * Conceptually similar to transposing a sparse matrix. Parallelize using
	 * groupingBy()?
	 */
	public void refreshAllCoFriendSets() {
		for (V z : this.coFriends.keySet()) {
			this.coFriends.get(z).clear(); // remove cofriends of z, if any (redundant when first called)
		}
		for (V x : this.friends.keySet()) {
			for (V y : this.friends.get(x)) {
				this.coFriends.get(y).add(x); // since y is a friend of x, add x to co-friends of y
			}
		}
	}

	/*
	 * This is the central algorithm of k-nearest neighbor descent. During the
	 * execution of this function, the sets friends.get(x) and coFriends.get(x) are
	 * immutable. Select best k candidates from co-friends, friends of friends, and
	 * friends of co-friends.
	 * TODO
	 * Error here - the size of friend sets is not staying fixed at k
	 */
	Function<V, Set<V>> refreshOneFriendSet = x -> {
		Set<V> pool = new HashSet<>();
		for (V y : this.friends.get(x)) {
			pool.addAll(friends.get(y)); // add in friends of friends of x
		}
		for (V z : this.coFriends.get(x)) {
			pool.add(z); // add in the co-friend
			pool.addAll(friends.get(z)); // add in friends of co-friends of x
		}
		SortedSet<V> runningK = new TreeSet<V>(crs.apply(x));// The comparator is the ranking from x
		runningK.addAll(this.friends.get(x)); // Initialize with the current friend set
		for (V p : pool) {
			if (crs.apply(x).compare(p, runningK.last()) < 0) {
				runningK.remove(runningK.last());
				runningK.add(p);
			}
		}
		return runningK;
	};

	/*
	 * Apply the refreshOneFriendSet function to all of the points, in parallel.
	 */
	public void refreshAllFriendSets() {
		Map<V, Set<V>> friendUpdates = this.points.parallelStream()
				.collect(Collectors.toMap(Function.identity(), x -> refreshOneFriendSet.apply(x)));
		this.friends.putAll(friendUpdates); // replaces previous friend sets with new ones
	}

	/*
	 * Sample a point x, and two co-friends y, z of x. What is the probability that
	 * y is a friend or co-friend of z? Call this the friend clustering rate. This
	 * function estimates the friend clustering rate as a diagnostic for progress in
	 * kNN descent. It should be close to zero at the outset, and should increase
	 * during the algorithm. If it is less after r+1 rounds than it was after r
	 * rounds (probably due to sampling errors at the plateau) this is a sign to
	 * stop.
	 */
	double estimateFriendClustering(double sampleRate) {
		Set<V> sample = new HashSet<>();
		int sampleSize = (int) Math.ceil(sampleRate * (double) this.points.size());
		while (sample.size() < sampleSize) {
			sample.add(this.points.get(g.nextInt(points.size()))); // add randomly chosen points to the sample
		}
		V y, z;
		int counter = 0;
		for (V x : sample) {
			Iterator<V> it = this.friends.get(x).iterator(); // loops through k values
			y = it.next(); // a friend of x
			z = it.next(); // another friend of x
			if (this.friends.get(y).contains(z) || this.friends.get(z).contains(y)) {
				counter++;
			}
		}
		System.out
				.println("In sampling " + sampleSize + " pairs of friends, " + counter + " were friends or cofriends.");
		return (double) counter / (double) sampleSize;
	}

	/**
	 * @return the friends
	 */
	public Map<V, Set<V>> getFriends() {
		return friends;
	}

	/**
	 * @return the coFriends
	 */
	public Map<V, Set<V>> getCoFriends() {
		return coFriends;
	}

	/*
	 * For diagnostics
	 */
	public IntSummaryStatistics friendStats() {
		return this.friends.entrySet().stream().mapToInt(e -> e.getValue().size()).summaryStatistics();
	}
	/*
	 * For diagnostics
	 */

	public IntSummaryStatistics coFriendStats() {
		return this.coFriends.entrySet().stream().mapToInt(e -> e.getValue().size()).summaryStatistics();
	}

	/**
	 * @return the expanderBasedRoundCount
	 */
	public int getExpanderBasedRoundCount() {
		return expanderBasedRoundCount;
	}

}
