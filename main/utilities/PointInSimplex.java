/**
 * 
 * Represents a probability distribution on d points
 * as a point in interior of d-dimensional simplex in R^(d+1).
 * The divergence method uses Kullback-Liebler divergence.
 * Since many computations may occur, the logs of the probabilities
 * p_1, ...p_d are precomputed and saved.
 * Also the associated comparator.
 * 
 * For testing clustering algorithms, we record the index of the template
 * used to build the point; typically this means a vector of d parameters for
 * a Dirichlet distribution.
 * @since June 1, -2020
 * 
 */
package utilities;

import java.util.Arrays;
import java.util.Comparator;
import java.util.function.ToDoubleBiFunction;

/**
 * @author rwrd
 *
 */
public class PointInSimplex {

	final int d; // dimension
	final double[] p; // all > 0
	final int template; // which template was used to generate p? Use Integer.MIN_VALUE if irrelevant.
	double[] logp;
	Comparator<PointInSimplex> cprtr;

	/**
	 * 
	 */
	public PointInSimplex(int dimension, double[] probabilities, int myTemplate) {
		this.d = dimension;
		this.p = probabilities;
		this.template = myTemplate;
		this.logp = Arrays.stream(this.p).map(z -> Math.log(z)).toArray();
		/*
		 * Refer to this object as x. rank_x(y) < rank_x(z) means D(x | y) < D(x | z).
		 * Revised June 4, 2020.
		 */
		this.cprtr = (y, z) -> Double.compare(div.applyAsDouble(this, y), div.applyAsDouble(this, z));
	}

	/*
	 * Kullback-Liebler divergence. Efficiency occurs because each PointInSimplex
	 * object only computes its log probabilities ONCE. The divergence computation
	 * involves addition and multiplication only
	 */
	ToDoubleBiFunction<PointInSimplex, PointInSimplex> div = (x, y) -> {
		double s = 0.0;
		if (x.d == y.d) {
			for (int i = 0; i < x.d; i++) {
				s += x.p[i] * (x.logp[i] - y.logp[i]); //arithmetic
			}
			return s;
		} else {
			return Double.NaN;
		}
	};

	/*
	 * @param PointInSimplex Equality means equality of the probability vectors
	 */
	public boolean equals(PointInSimplex y) {
		if (!(this.d == y.d)) {
			return false;
		} else {
			int counter = this.d; // in how many components do the probabilities disagree?
			for (int i = 0; i < this.d; i++) {
				if (this.p[i] == y.p[i]) {
					counter--;
				}
			}
			return (counter == 0) ? true : false; // true means no disagreements
		}
	}

	/**
	 * @return the cprtr
	 */
	public Comparator<PointInSimplex> getCprtr() {
		return cprtr;
	}

	/**
	 * @return the p
	 */
	public double[] getP() {
		return p;
	}

	/**
	 * @return the template
	 */
	public int getTemplate() {
		return template;
	}

}
