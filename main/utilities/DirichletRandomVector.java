/**Passed June 4, 2020
 * @author rwdarli
 * 
 * Motivation: simulate random probability distributions.
 * 
 */
package utilities;

import java.util.Arrays;
import java.util.SplittableRandom;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.IntStream;

import org.apache.commons.math3.distribution.GammaDistribution;

public class DirichletRandomVector {
	SplittableRandom g;
	// Sample a Gamma(shape, 1) random variable
	static DoubleUnaryOperator gamma = shape -> {
		GammaDistribution dist = new GammaDistribution(shape, 1.0);
		return dist.sample();
	};

	public DirichletRandomVector() {
		g = new SplittableRandom();
	}

	/**
	 * 
	 * @param d dimension = # components (whose sum will be 1.0)
	 * @return d Exponential(1) random variables, divided by their sum, i.e.
	 *         Dirichlet Random Vector where all parameters are 1.0.
	 */
	public double[] simulate(int d) {
		double[] x = this.g.doubles(d).map(z -> -Math.log(z)).toArray();
		double sum = Arrays.stream(x).sum();
		return Arrays.stream(x).map(z -> z / sum).toArray();
	}

	/**
	 * 
	 * @param alpha vector of d positive parameters
	 * @return Dirichlet Random Vector with parameters alpha[0],,, alpha[d-1]
	 *         Deoends on Commons Math3
	 */
	public double[] simulate(double[] alpha) {
		double[] x = Arrays.stream(alpha).map(a -> gamma.applyAsDouble(a)).toArray();
		double sum = Arrays.stream(x).sum();
		return Arrays.stream(x).map(z -> z / sum).toArray();
	}

	/**
	 * 
	 * @param d dimension = # components (whose sum will be 1.0)
	 * @param n sample size (number of vectors with same Dirichlet parameters)
	 * @return n Dirichlet Random Vector with SAME parameter set, which is selected randomly. Each
	 *         such parameter has a mean of 1.0.
	 * 
	 */
	public double[][] simulateWithRandomParams(int d, int n) {
		double[] dirichlet1 = this.simulate(d); // parameters sum to 1, random ratios
		double lambda = -(double) d * Math.log(this.g.nextDouble()); // Exponential R.V, mean d
		double[] alpha = Arrays.stream(dirichlet1).map(z -> z * lambda).toArray(); // random parameters, random scale
		double[][] matrix = new double[n][d];
		IntStream.range(0, n).parallel().forEach(i -> matrix[i] = this.simulate(alpha)); // each row is a new sample, SAME alpha
		return matrix;
	}

}
