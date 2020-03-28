/**
 * @deprecated
 * Good stuff moved to 
 * FullVersusPartialSortTest
 * 
 * This object is a finite probability mass function on {0, 1, ...d-1}
 * which gives positive mass to every atom.
 * Map each object x to a comparator which says y < z if D(x|y) < D(x|z).
 * Here D(x|y) is the Kullback-Liebler divergence of y from x.
 * See https://en.wikipedia.org/wiki/Kullback%E2%80%93Leibler_divergence
 * 
 */
package utilities;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleBiFunction;

/**
 * @author rwrd
 *
 */
public class ProbabilityMassFunction implements Comparator<List<Double>> {
	int d;
	List<Double> pmf;
	/*
	 * Kullback-Liebler divergence
	 */
	ToDoubleBiFunction<List<Double>, List<Double>> div = (p, q) -> {
		double s = 0.0;
		if (p.size() == q.size()) {
			for (int i = 0; i < p.size(); i++) {
				s += p.get(i) * Math.log(p.get(i) / q.get(i));
			}
			return s;
		}

		else {
			return Double.NaN;
		}
	};

	/**
	 * 
	 */
	public ProbabilityMassFunction(Double[] probabilities) {
		this.d = probabilities.length;
		this.pmf = Arrays.asList(probabilities);

	}

	public int compare(List<Double> y, List<Double> z) {
		double dy = div.applyAsDouble(this.pmf, y);
		double dz = div.applyAsDouble(this.pmf, z);
		return (int) Math.signum(dy - dz);
	}

}
