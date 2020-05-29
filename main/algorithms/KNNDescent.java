/**
 * Parallel stream, functional implementation of K-nearest neighbor descent. 
 * Passed test 4.3.2020.
 * Revised 4.9.20 so friend sets are always sorted.
 * Termination criterion uses friend clustering rate.
 * 
 * References:
 * [1]Jacob D. Baron; R. W. R. Darling. K-nearest neighbor approximation via the friend-of-a-friend principle. arXiv:1908.07645,
 * [2] Dong, Wei; Moses, Charikar; Li, Kai. Efficient k-nearest neighbor graph construction for generic similarity measures. 
 * Proceedings of the 20th International Conference on World Wide Web, 577--586, 2011
 */
package algorithms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.HashSet;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
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
	Map<V, NavigableSet<V>> friends; // keyset = points
	Map<V, Set<V>> coFriends; // keyset = points. cof-riends need not be sorted
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
		System.out.println("_/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ ");
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
			System.out.println("_/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ ");
		}
		System.out.println("KNN Descent terminated after " + rounds + " rounds.");
	}

	/*
	 * Supplies a random initial SORTED set of k friends to a given point.
	 */
	Function<V, NavigableSet<V>> randomKFriends = x -> {
		NavigableSet<V> reachOut = new TreeSet<>(crs.apply(x));
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
				.collect(Collectors.toMap(Function.identity(), x -> randomKFriends.apply(x))); // choice between
																								// parallel or not
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
	 * immutable. Select best k candidates from friends, co-friends, friends of
	 * friends, and friends of co-friends.
	 */
	Function<V, NavigableSet<V>> proposeNewFriendSet = x -> {
		Set<V> pool = new HashSet<>();
		for (V y : this.friends.get(x)) {
			pool.addAll(friends.get(y)); // add in friends of friends of x
		}
		for (V z : this.coFriends.get(x)) {
			pool.add(z); // add in the co-friend
			pool.addAll(friends.get(z)); // add in friends of co-friends of x
		}
		NavigableSet<V> runningK = new TreeSet<V>(crs.apply(x));// The comparator is the ranking from x
		runningK.addAll(this.friends.get(x)); // Initialize with the current friend set
		for (V p : pool) {
			/*
			 * If p is NOT one of current k best, nor equal to x, and if p is preferred to
			 * current k-th best, insert p.
			 */
			if ((crs.apply(x).compare(p, runningK.last()) < 0) && (!runningK.contains(p)) && (!p.equals(x))) {
				runningK.remove(runningK.last());
				runningK.add(p);
			}
		}
		return runningK;
	};

	/*
	 * Apply the proposeNewFriendSet function to all of the points, in parallel.
	 * This is the "master stroke".
	 */
	public void refreshAllFriendSets() {
		Map<V, NavigableSet<V>> friendUpdates = this.points.parallelStream()
				.collect(Collectors.toMap(Function.identity(), x -> proposeNewFriendSet.apply(x))); // choice between
																									// parallel or not
		this.friends.putAll(friendUpdates); // replaces previous friend sets with new ones
	}

	/*
	 * Sample a point x, and two friends y, z of x. What is the probability that y
	 * is a friend or co-friend of z? Call this the friend clustering rate. This
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
		int index0, index1;
		List<V> tempList = new ArrayList<>(); // List better than Set for drawing random samples
		int counter = 0;
		for (V x : sample) {
			tempList.clear();
			tempList.addAll(this.friends.get(x));
			/*
			 * Sample index0 and index1 uniformly from unordered pairs in {0, 1, ..., k-1}
			 */
			index0 = g.nextInt(k);
			index1 = g.nextInt(k - 1);
			if (index1 >= index0) {
				index1++; // ensures index0 and index1 are different
			}
			y = tempList.get(index0); // a uniformly selected random friend of x
			z = tempList.get(index1); // a uniformly selected random friend of x, different from y
			if (this.friends.get(y).contains(z) || this.friends.get(z).contains(y)) {
				counter++;
			}
		}
		System.out
				.println("In sampling " + sampleSize + " pairs of friends, " + counter + " were friends or cofriends.");
		return (double) counter / (double) sampleSize;
	}

	/*
	 * Samole m (6 maybe) points at random, and compute their TRUE k-NN sets.
	 * EXPENSIVE! Let s[i] denote the number of true k-NN of sample point i which
	 * appear in the friend set of the sample point. . Report
	 * DoubleSummaryStatistics of {s[0]/m, s[1]/m, ..., s[m-1]/m). It is hoped the
	 * mean is close to 1.
	 */
	public DoubleSummaryStatistics qualityAssessment(int sampleSize) {
		Set<V> sample = new HashSet<>();
		while (sample.size() < sampleSize) {
			sample.add(this.points.get(g.nextInt(points.size()))); // add randomly chosen points to the sample
		}
		List<Double> proportionCaptured = new ArrayList<>(); // p->t if exactly t of point p's true k-NN were found
		for (V x : sample) {
			NavigableSet<V> runningK = new TreeSet<V>(crs.apply(x));// The comparator is the ranking from x
			runningK.addAll(this.friends.get(x)); // has k elements
			/*
			 * Compute the TRUE k-NN set. As we run through the points, do NOT compare x to
			 * itself. Only remove the worst point only if the new candidate is currently
			 * absent from the list
			 */
			for (V p : this.points) {
				if ((crs.apply(x).compare(p, runningK.last()) < 0) && (!runningK.contains(p)) && (!p.equals(x))) {
					runningK.remove(runningK.last());
					runningK.add(p);
				}
			}
			/*
			 * How many elements of runningK are in friends.get(x)?
			 */
			long numberCaptured = runningK.stream().filter(p -> this.friends.get(x).contains(p)).count();
			if (runningK.size() == k) {
				proportionCaptured.add((double) numberCaptured / (double) this.k);
			} else {
				System.out.println("Error occurred during qualityAssessment: k-NN set did not have k elements.");
			}
		}

		return proportionCaptured.stream().mapToDouble(w -> w.doubleValue()).summaryStatistics();
	}

	/**
	 * @return the friends
	 * This getter will be used by LocalDepthCohesion<V>
	 */
	public Map<V, NavigableSet<V>> getFriends() {
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
