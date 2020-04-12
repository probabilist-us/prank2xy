/**
 * This test exemplifies the pattern that the point type should have a Comparator built in.
 * 
 * Points are of type pointInSimplex.
 * Points represent n i.i.d. samples from a d-dimensional Dirichlet distribution.
 * Worked correctly April ?, 2020.
 */
package algorithmsTests;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import algorithms.KNNDescent;
import utilities.pointInSimplex;

/**
 * @author rwrd
 *
 */
public class KNNDescentOnSimplexTest {

	int d, n, k;
	Random g;
	List<pointInSimplex> points;
	KNNDescent<pointInSimplex> knnd;

	/*
	 * Generate samples from d-dimensional Dirichlet distributions
	 */
	Supplier<pointInSimplex> dirichletGenerator = () -> {
		double[] vec = g.doubles(this.d).map(x -> -Math.log(x)).toArray(); // exponential random variables
		double sum = Arrays.stream(vec).sum();
		for (int i = 0; i < this.d; i++) {
			vec[i] = vec[i] / sum;
		} // normalize so sum is 1
		return new pointInSimplex(this.d, vec);
	};

	/*
	 * Comparator (associated with point x) ranks y, z according to KL Divergence
	 * from x
	 */
	Function<pointInSimplex, Comparator<pointInSimplex>> klcmpr = x -> x.getCprtr();

	public KNNDescentOnSimplexTest(int dimension, int numPoints, int numNeighbors) {
		this.d = dimension;
		this.n = numPoints;
		this.k = numNeighbors;
		g = new Random();
		/*
		 * Generate the set of points, and the framework for K-nearest neighbor descent
		 */
		this.points = new ArrayList<pointInSimplex>();
		this.points.addAll(Stream.generate(dirichletGenerator::get).limit(this.n).collect(Collectors.toList()));
		System.out.println("# Dirichlet samples generated = " + this.points.size());
		this.knnd = new KNNDescent<>(this.points, this.klcmpr, this.k);
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		int d = Integer.parseInt(args[0]);
		int n = Integer.parseInt(args[1]);
		int k = Integer.parseInt(args[2]);
		KNNDescentOnSimplexTest test = new KNNDescentOnSimplexTest(d, n, k);
		Runtime rt = Runtime.getRuntime();
		System.out.println("Java Runtime " + Runtime.version().toString());
		System.out.println("Available processors: " + rt.availableProcessors());
		double gB = 1074741824.0;
		System.out.println("Maximum available memory: " + (double) rt.maxMemory() / gB + " Gb.");
		System.out.println("Setting up KNN Descent test on Dirichlet samples of dimension " + test.d);
		int maxRounds = 2 * test.knnd.getExpanderBasedRoundCount();
		double sampleRate = 0.1;
		System.out.println("n = " + test.n + " points; " + test.k + " nearest friends.");
		test.knnd.kNNDescentAlgorithm(maxRounds, sampleRate);
		/*
		 * Quality of approximation
		 */
		int ss = 6;
		System.out.println("Proportion of true k-NN found: sample of size " + ss);
		DoubleSummaryStatistics quality = test.knnd.qualityAssessment(ss);
		System.out.println(quality.toString());

	}

}
