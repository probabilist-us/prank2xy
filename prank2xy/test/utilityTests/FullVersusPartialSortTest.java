/**
 * Experiment. Say k is in the dozens and n is about 2*k*k
 * There is a list of n Comparable items
 * Which is faster, 1 or 2.
 * 1. Sort all n items and pick first k
 * 2. Maintain a sorted list of the lowest k, as we run through the list.
 * The class itself is generic. 
 * Current implementation of the main class uses Dirichlet distributions in d dimensions.
 */
package utilityTests;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author rwrd
 * @since 2020
 */
public class FullVersusPartialSortTest<V extends Comparator> {

	/**
	 * 
	 */
	final int k = 64;
	final int n = 2 * k * k;
	long start;
	List<V> sample, bestK;
	SortedSet<V> runningK;
	/*
	 * The Comparator will be based on ranking induced by base point.
	 */
	V basePoint; 

	public FullVersusPartialSortTest() {
		this.sample = new ArrayList<V>();
		this.bestK = new ArrayList<V>();
		this.runningK = new TreeSet<V>();
	}
	/*
	 * Call this method before calling either of the two following ones.
	 */
	private void generateSample(Supplier<V> maker) {
		this.basePoint = maker.get();
		this.sample.clear();
		this.sample.addAll(Stream.generate(maker::get).limit(n).collect(Collectors.toList()));
	}

	private double fullSortTiming() {
		this.bestK.clear();
		this.start = System.currentTimeMillis();
		Comparator<V> cprtr = (V p, V q)->this.basePoint.compare(p, q);
		Collections.sort(this.sample, cprtr );
		this.bestK.addAll(this.sample.subList(0, k-1));
		return (0.001 * (double) (System.currentTimeMillis() - start));
	}

	private double partialSortTiming() {
		this.bestK.clear();
		this.start = System.currentTimeMillis();
		/*
		 * The first k elements of the sample are taken as initial running best
		 */
		this.runningK.addAll(this.sample.subList(0, k-1));
		Comparator<V> cprtr = (V p, V q)->this.basePoint.compare(p, q);
		/*
		 * Each of the remaining n-k elements of the sample is added if it is better than 
		 * the worst ranking element of the running best
		 */
		for(int i = k; i < n; i++) {
			
		}
		return (0.001 * (double) (System.currentTimeMillis() - start));
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
