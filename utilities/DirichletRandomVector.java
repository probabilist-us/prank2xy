package utilities;

import java.util.Arrays;
import java.util.Random;

public class DirichletRandomVector {
	static Random g;

	public DirichletRandomVector() {

	}

	/**
	 * 
	 * @param d dimension = # components, whose sum is 1.0
	 * @return d Exponentisl(1) random variables, divided by their sum, i.e.
	 * Dirichlet Random Vector where all parameters are 1.0.
	 */
	public static double[] simulate(int d) {
		double[] x = g.doubles(d).map(z -> -Math.log(z)).toArray();
		double sum = Arrays.stream(x).sum();
		return Arrays.stream(x).map(z -> z / sum).toArray();
	}

	/**
	 * 
	 * @param alpha vector of d positive parameters
	 * @return Dirichlet Random Vector with parameters alpha[0],,, alpha[d-1]
	 */
	public static double[] simulate(double[] alpha) {
		return new double[] {0.0, 0.0};//TODO
	}

}
