/**
 * @since June 4, 2020
 */
package utilityTests;

import utilities.DirichletRandomVector;

/**
 * @author engli Passed June 4, 2020
 */
public class DirichletRandomVectorTest {

	DirichletRandomVector drv;

	public DirichletRandomVectorTest() {
		this.drv = new DirichletRandomVector();
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		DirichletRandomVectorTest test = new DirichletRandomVectorTest();
		int d = 8;
		System.out.println("Simulate " + d + "-dimensional standard Dirichlet vector.");
		double[] x = test.drv.simulate(d);
		for (double z : x) {
			System.out.print(z + ", ");
		}
		System.out.println();
		System.out.println("_/ _/ _/ ");
		System.out.println("Simulate " + d + " reciprocals of Uniform r.v..");
		double[] x1 = test.drv.simulateInverseUniform(d);
		for (double z : x1) {
			System.out.print(z + ", ");
		}
		System.out.println();
		System.out.println("_/ _/ _/ ");
		System.out.println();
		double[] alpha = new double[] { 1.0, 2.0, 3.0, 4.0 };
		System.out.println("Simulate 4- dimensional Dirichlet vector, params 1, 2, 3, 4.");
		double[] y = test.drv.simulate(alpha);
		for (double z : y) {
			System.out.print(z + ", ");
		}
		System.out.println();
		System.out.println("_/ _/ _/ ");
		System.out.println();
		int n = 4;
		System.out.println(n + " times, simulate " + d + "-dimensional Dirichlet vector, same randomized params.");
		double[][] r = test.drv.simulateWithRandomParams(d, n);
		for (int i = 0; i < n; i++) {
			for (double z : r[i]) {
				System.out.print(z + ", ");
			}
			System.out.println();
		}
		n = 1000000;
		System.out.println("Timing test: n = " + n);
		long start = System.currentTimeMillis();
		double[][] r2 = test.drv.simulateWithRandomParams(d, n);
		long duration = System.currentTimeMillis() - start;
		System.out.println((double)duration/1000.0 + " seconds");
	}

}
