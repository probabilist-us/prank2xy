/**Passed June 4, 2020
 * @author rwdarli
 * 
 * Motivation: simulate random probability distributions.
 * Revised June 8, 2020
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
	final double eps = 1.0E-6; // do not return values less than this threshold

	public DirichletRandomVector() {
		g = new SplittableRandom();
	}

	/**
	 * 
	 * @param d dimension = # components (whose sum will be 1.0)
	 * @return d Exponential(1) random variables, divided by their sum, i.e.
	 *         Dirichlet Random Vector where all parameters are 1.0.
	 *         NOT USED in simulateWithRandomParams() method.
	 */
	public double[] simulate(int d) {
		double[] x = this.g.doubles(d).map(z -> -Math.log(z)).toArray();
		double sum = Arrays.stream(x).sum();
		return Arrays.stream(x).map(z -> z / sum).toArray();
	}
	
	/**
	 * 
	 * @param d dimension = # components (sum <= d/eps, large variance)
	 * @return a vector whose components are i.i.d. 1/U, U ~ Uniform(eps, 1)
	 * PURPOSE: generate one Dirichlet parameter vector for a whole set of points
	 */
	public double[] simulateInverseUniform(int d) {
		return this.g.doubles(d, eps, 1.0).map(z->1.0/z).toArray(); // upper bounded by 1.0/eos
	}	

	/**
	 * 
	 * @param alpha vector of d positive parameters
	 * @return Dirichlet Random Vector with parameters alpha[0],,, alpha[d-1]
	 *         Deoends on Commons Math3 Avoids returning any componet of size < eps
	 */
	public double[] simulate(double[] alpha) {
		double[] x = Arrays.stream(alpha).map(a -> gamma.applyAsDouble(a)).toArray();
		double sum = Arrays.stream(x).sum();
		double eps1 = 1.0 - eps * (double) alpha.length;
		return Arrays.stream(x).map(z -> this.eps + eps1 * z / sum).toArray();
	}

	/**
	 * 
	 * @param d dimension = # components (whose sum will be 1.0)
	 * @param n sample size (number of vectors with same Dirichlet parameters)
	 * @return n Dirichlet Random Vector with SAME parameter set, which is selected
	 *         randomly. 
	 * 
	 */
	public double[][] simulateWithRandomParams(int d, int n) {		
		double[] alpha =  this.simulateInverseUniform(d);  // random parameters, random scale
		double[][] matrix = new double[n][d];
		IntStream.range(0, n).parallel().forEach(i -> matrix[i] = this.simulate(alpha)); // each row is a new sample,																							// SAME alpha
		return matrix;
	}

}
