/**
 * Goal: is faster to find a single quantile, and select all entries below
 * that quantile, than to sort a list completely
 */
package utilityTests;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;


/**
 * @author rwrd
 * @since 2020
 */
public class QuantileVersusSortTest {

	/**
	 * 
	 */
	final int n = 8192;
	final int k = 64;
	final int scale = k/n;
	final double q = (double)k / (double) n;
	long start;
	double boundary;
	Random g;
	List<Double> sample;

	public QuantileVersusSortTest() {
		this.g = new Random();
		this.sample = new ArrayList<>();
	}

	private double fullSortTiming() {
		this.sample.clear();
		this.sample.addAll(g.doubles((long)n, -1.0, 1.0).boxed().collect(Collectors.toList() ) );
		this.start = System.currentTimeMillis();
		Collections.sort(this.sample);
		return (0.001*(double)(System.currentTimeMillis() - start));
	}
	
	private double partialSortTiming() {
		this.sample.clear();
		this.sample.addAll(g.doubles(n, -1.0, 1.0).boxed().collect(Collectors.toList() ) );
		this.start = System.currentTimeMillis();	
		
		return 1.0;
	}
	

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
