/**
 * 
 */
package utilityTests;

import java.util.Arrays;
import java.util.Random;
import java.util.function.Supplier;

import utilities.PointInSimplex;

/**
 * @author rwrd
 *
 */
public class PointInSimplexTest {

	Random g;
	PointInSimplex x, y, z;

	public PointInSimplexTest(int d) {
		g = new Random();
		Supplier<double[]> dirichletGenerator = () -> {
			double[] vec = g.doubles(d).map(x -> -Math.log(x)).toArray(); // exponential random variables
			double sum = Arrays.stream(vec).sum();
			for (int i = 0; i < d; i++) {
				vec[i] = vec[i] / sum;
			} // normalize so sum is 1
			return vec;
		};
		x = new PointInSimplex(d, dirichletGenerator.get(), Integer.MIN_VALUE);
		y = new PointInSimplex(d, dirichletGenerator.get(), Integer.MIN_VALUE);
		z = new PointInSimplex(d, dirichletGenerator.get(), Integer.MIN_VALUE);
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		int d = Integer.parseInt(args[0]);
		PointInSimplexTest test = new PointInSimplexTest(d);
		System.out.println("point x: " + Arrays.stream(test.x.getP()).summaryStatistics().toString());
		System.out.println("point y: " + Arrays.stream(test.y.getP()).summaryStatistics().toString());
		System.out.println("point z: " + Arrays.stream(test.z.getP()).summaryStatistics().toString());
		System.out.println("From point x, compare y and z. " + test.x.getCprtr().compare(test.y, test.z) );

	}

}
