/**
 * Points are taken to be n i.i.d. samples from a d-dimensional Dirichlet distribution.
 * Worked correctly April 5, 2020.
 * @deprecated because the KL-divergences are computed wastefullu
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
import java.util.function.ToDoubleBiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import algorithms.KNNDescent;

/**
 * @author rwrd
 *
 */
public class KNNDescentTest {

	final int d = 10;
	final int n = 2000000;
	final int k = 16;
	Random g;
	List<double[]> points;
	KNNDescent<double[]> knnd;

	/*
	 * * Generate samples from d-dimensional Dirichlet distributions
	 */
	Supplier<double[]> dirichletGenerator = () -> {
		double[] vec = g.doubles(this.d).map(x -> -Math.log(x)).toArray(); // exponential random variables
		double sum = Arrays.stream(vec).sum();
		for (int i = 0; i < this.d; i++) {
			vec[i] = vec[i] / sum;
		} // normalize so sum is 1
		return vec;
	};
	/*
	 * Kullback-Liebler divergence - for the case where V is a probability mass
	 * function
	 */
	ToDoubleBiFunction<double[], double[]> div = (p, q) -> {
		double s = 0.0;
		if (p.length == q.length) {
			for (int i = 0; i < p.length; i++) {
				s += p[i] * Math.log(p[i] / q[i]);
			}
			return s;
		} else {
			return Double.NaN;
		}
	};

	/*
	 * Comparator (associated with point x) ranks y, z according to KL Divergence
	 * from x
	 */
	Function<double[], Comparator<double[]>> klcmpr = x -> ((y,
			z) -> (int) Math.signum(div.applyAsDouble(x, y) - div.applyAsDouble(x, z)));

	public KNNDescentTest() {
		g = new Random();
		/*
		 * Generate the set of points, and the framework for K-nearest neighbor descent
		 */
		this.points = new ArrayList<double[]>();
		this.points.addAll(Stream.generate(dirichletGenerator::get).limit(this.n).collect(Collectors.toList()));
		System.out.println("# Dirichlet samples generated = " + this.points.size());
		this.knnd = new KNNDescent<>(this.points, this.klcmpr, this.k);
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		KNNDescentTest test = new KNNDescentTest();
		Runtime rt = Runtime.getRuntime();
		System.out.println("Java Runtime " + rt.toString());
		System.out.println("Available processors: " + rt.availableProcessors());
		double gB = 1074741824.0;
		System.out.println("Maximum available memory: " + (double)rt.maxMemory()/gB + " Gb.");
		System.out.println("Setting up KNN Descent test on Dirichlet samples of dimension " + test.d);
		int maxRounds = 2 * test.knnd.getExpanderBasedRoundCount();
		double sampleRate = 0.01;
		System.out.println("n = " + test.n + " points; " + test.k + " nearest neighbors.");
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
