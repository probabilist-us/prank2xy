/**
 * Working 3.28.2020
 * Partial sort is 10 times faster.
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
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToDoubleBiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author rwrd
 * @since 2020
 */
public class FullVersusPartialSortTest<V> {

    /**
     *
     */
    final int k = 64;
    final int n = 2 * k * k;
    long start;
    List<V> sample, bestK;

    /*
     * The Comparator will be based on ranking induced by base point.
     */
    V basePoint;

    public FullVersusPartialSortTest() {
        this.sample = new ArrayList<V>();
        this.bestK = new ArrayList<V>();

    }

    /*
     * Call this method before calling either of the two following ones.
     */
    private double generateSample(Supplier<V> maker) {
        this.start = System.currentTimeMillis();
        this.basePoint = maker.get();
        this.sample.clear();
        this.sample.addAll(Stream.generate(maker::get).limit(n).collect(Collectors.toList()));
        return (0.001 * (double) (System.currentTimeMillis() - start));
    }

    private double fullSortTiming(Comparator<V> cprtr) {
        this.bestK.clear();
        this.start = System.currentTimeMillis();
        //Comparator<V> cprtr = (V p, V q) -> this.basePoint.compare(p, q);
        Collections.sort(this.sample, cprtr);
        this.bestK.addAll(this.sample.subList(0, k - 1));
        return (0.001 * (double) (System.currentTimeMillis() - start));
    }

    private double partialSortTiming(Comparator<V> cprtr) {
        this.bestK.clear();
        this.start = System.currentTimeMillis();
        /*
         * The first k elements of the sample are taken as initial running best
         */
        SortedSet<V> runningK = new TreeSet<V>(cprtr);
        runningK.addAll(this.sample.subList(0, k - 1));
        /*
         * Each of the remaining n-k elements of the sample is added if it is better
         * than the worst ranking element of the running best
         */
        for (V p : this.sample.subList(k, n - 1)) {
            if (cprtr.compare(p, runningK.last()) < 0) {
                runningK.remove(runningK.last());
                runningK.add(p);
            }
        }
        this.bestK.addAll(runningK);
        return (0.001 * (double) (System.currentTimeMillis() - start));
    }
    /*
     * Kullback-Liebler divergence - for the case where V is a probability mass function
     */
    ToDoubleBiFunction<double[], double[]> div = (p, q) -> {
        double s = 0.0;
        if (p.length == q.length ) {
            for (int i = 0; i < p.length;  i++) {
                s += p[i] * Math.log(p[i] / q[i]);
            }
            return s;
        }

        else {
            return Double.NaN;
        }
    };

    /*
     * Comparator associated with pmf x ranks y, z according to KL Divergence from x
     */
    Function<double[], Comparator<double[]>> klcmpr = x->(
            (y, z)->(int)Math.signum(div.applyAsDouble(x, y) - div.applyAsDouble(x, z) ) );

    /**
     * @param args
     */
    public static void main(String[] args) {
        /*
         * Choose type V to be List<Double>
         */
        FullVersusPartialSortTest<double[]> test = new FullVersusPartialSortTest<>();
        Random g = new Random();
        long d = 100;
        /*
         * Generate samples from d-dimensional Dirichlet distributions
         */
        Supplier<double[]> dirichletGenerator = ()->{
            double[] vec = g.doubles(d).map(x->-Math.log(x)).toArray(); // exponential random variables
            double sum = Arrays.stream(vec).sum();
            for(int i = 0; i < d; i++) {
                vec[i]=vec[i]/sum;
            } // normalize so sum is 1
            return vec;
        };
        double t1 = test.generateSample(dirichletGenerator);
        System.out.println("Time to generate sample; " + t1 + " secs.");
        /*
         * Choose KL divergence from base point as the Comparator
         */
        double t2 = test.fullSortTiming(test.klcmpr.apply(test.basePoint));
        System.out.println("Base point:");
        for(double x : test.basePoint) {
            System.out.print(x + ", ");
        }
        System.out.println();
        System.out.println("Time using full sort; " + t2 + " secs.");
        System.out.println("Nearest neighbor:");
        for(Double y : test.bestK.get(0)) {
            System.out.print(y + ", ");
        }
        System.out.println();
        /*
         * Choose KL divergence from base point as the Comparator
         */
        double t3 = test.partialSortTiming(test.klcmpr.apply(test.basePoint));
        System.out.println("Time using partial sort and insertion; " + t3 + " secs.");
        System.out.println("Are the results the same?");
        System.out.println("Nearest neighbor:");
        for(Double y : test.bestK.get(0)) {
            System.out.print(y + ", ");
        }
        System.out.println();


    }

}
