/**
 *
 * Represents a probability distribution on d points
 * as a point in interior of d-dimensional simplex in R^(d+1).
 * The divergence method uses Kullback-Liebler divergence.
 * Since many computations may occur, the logs of the probabilities
 * p_1, ...p_d are precomputed and saved.
 * Also the associated comparator.
 * @since 06-Apr-2020
 */
package utilities;

import java.util.Arrays;
import java.util.Comparator;
import java.util.function.ToDoubleBiFunction;

/**
 * @author rwrd
 *
 */
public class pointInSimplex {

    final int d; // dimension
    final double[] p; // all > 0
    double[] logp;
    Comparator<pointInSimplex> cprtr;

    /**
     *
     */
    public pointInSimplex(int dimension, double[] probabilities) {
        this.d = dimension;
        this.p = probabilities;
        this.logp = Arrays.stream(this.p).map(z -> Math.log(z)).toArray();
        /*
         * Refer to this object as x. rank_x(y) < rank_x(z) means D(x | y) - D(x | z) <
         * 0.
         */
        this.cprtr = (y, z) -> (int) Math.signum(div.applyAsDouble(this, y) - div.applyAsDouble(this, z));
    }

    /*
     * Kullback-Liebler divergence. Efficiency occurs because each pointInSimplex
     * object only computes its log probabilities ONCE. The divergence computation
     * involves addition and multiplication only
     */
    ToDoubleBiFunction<pointInSimplex, pointInSimplex> div = (x, y) -> {
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
     * @param pointInSimplex Equality means equality of the probability vectors
     */
    public boolean equals(pointInSimplex y) {
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
    public Comparator<pointInSimplex> getCprtr() {
        return cprtr;
    }

    /**
     * @return the p
     */
    public double[] getP() {
        return p;
    }

}

