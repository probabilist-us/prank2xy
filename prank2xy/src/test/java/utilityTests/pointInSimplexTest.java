/**
 *
 */
package utilityTests;

import java.util.Arrays;
import java.util.Random;
import java.util.function.Supplier;

import utilities.pointInSimplex;

/**
 * @author rwrd
 *
 */
public class pointInSimplexTest {

    Random g;
    pointInSimplex x, y, z;

    public pointInSimplexTest(int d) {
        g = new Random();
        Supplier<double[]> dirichletGenerator = () -> {
            double[] vec = g.doubles(d).map(x -> -Math.log(x)).toArray(); // exponential random variables
            double sum = Arrays.stream(vec).sum();
            for (int i = 0; i < d; i++) {
                vec[i] = vec[i] / sum;
            } // normalize so sum is 1
            return vec;
        };
        x = new pointInSimplex(d, dirichletGenerator.get());
        y = new pointInSimplex(d, dirichletGenerator.get());
        z = new pointInSimplex(d, dirichletGenerator.get());
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
        int d = Integer.parseInt(args[0]);
        pointInSimplexTest test = new pointInSimplexTest(d);
        System.out.println("point x: " + Arrays.stream(test.x.getP()).summaryStatistics().toString());
        System.out.println("point y: " + Arrays.stream(test.y.getP()).summaryStatistics().toString());
        System.out.println("point z: " + Arrays.stream(test.z.getP()).summaryStatistics().toString());
        System.out.println("From point x, compare y and z. " + test.x.getCprtr().compare(test.y, test.z) );

    }

}

