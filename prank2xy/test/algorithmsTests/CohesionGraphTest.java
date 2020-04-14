/**
 * Design:
 * (1) generate samples from biased Dirichlet distributions (d types)
 * (2) Apply kNNdescent
 * (3) Check focusGraph (done 4.12.2020)
 * (4) TODO Check cohesionGraph
 */
package algorithmsTests;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.SortedSet;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.graph.ImmutableValueGraph;

import algorithms.CohesionGraphBuilder;
import algorithms.KNNDescent;
import utilities.pointInSimplex;

/**
 * @author rwrd
 *
 */
public class CohesionGraphTest {
	int d, n, k;
	Random g;
	List<pointInSimplex> points;
	KNNDescent<pointInSimplex> knnd;
	CohesionGraphBuilder<pointInSimplex> cohere;
	/*
	 * Generate samples from d-dimensional Dirichlet distributions, with one
	 * component magnified d times. This should make the points cluster into d
	 * groups.
	 */
	Supplier<pointInSimplex> tiltedDirichletGenerator = () -> {
		double[] vec = g.doubles(this.d).map(x -> -Math.log(x)).toArray(); // exponential random variables
		int boost = g.nextInt(d); // component to be boosted
		vec[boost] *= (double) d; // one component becomes d times bigger
		double sum = Arrays.stream(vec).sum();
		for (int i = 0; i < this.d; i++) {
			vec[i] = vec[i] / sum;
		} // normalize so sum is 1
		return new pointInSimplex(this.d, vec);
	};

	/*
	 * Comparator (associated with point x) ranks y, z according to KL Divergence
	 * from x
	 */
	Function<pointInSimplex, Comparator<pointInSimplex>> klcmpr = x -> x.getCprtr();

	public CohesionGraphTest(int dimension, int numPoints, int numNeighbors) {
		this.d = dimension;
		this.n = numPoints;
		this.k = numNeighbors;
		g = new Random();
		/*
		 * Generate the set of points, and the framework for K-nearest neighbor descent
		 */
		this.points = new ArrayList<pointInSimplex>();
		this.points.addAll(Stream.generate(tiltedDirichletGenerator::get).limit(this.n).collect(Collectors.toList()));
		System.out.println("# Tilted Dirichlet samples generated = " + this.points.size());
		System.out.println("Randomly sampled point: ");
		pointInSimplex z = this.points.get(g.nextInt(n));
		for (double u : z.getP()) {
			System.out.print(u + ", ");
		}
		System.out.println();
		this.knnd = new KNNDescent<>(this.points, this.klcmpr, this.k);
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		int d = Integer.parseInt(args[0]);
		int n = Integer.parseInt(args[1]);
		int k = Integer.parseInt(args[2]);
		boolean microDiagnostics = true; // only do this for examples with < 100 vertices
		// Random g = new Random();
		CohesionGraphTest test = new CohesionGraphTest(d, n, k);
		Runtime rt = Runtime.getRuntime();
		System.out.println(
				"Java Runtime " + Runtime.version().toString() + "; Available processors: " + rt.availableProcessors());
		double gB = 1074741824.0;
		System.out.println("Maximum available memory: " + (double) rt.maxMemory() / gB + " Gb.");
		System.out.println("KNN Descent applied to tilted Dirichlet samples of dimension " + test.d);
		int maxRounds = 2 * test.knnd.getExpanderBasedRoundCount();
		double sampleRate = 0.5;
		System.out.println("n = " + test.n + " points; " + test.k + " nearest friends.");
		test.knnd.kNNDescentAlgorithm(maxRounds, sampleRate);
		System.out.println("Building focus graph and cohesion matrix.");
		test.cohere = new CohesionGraphBuilder<pointInSimplex>(test.knnd.getFriends());
		///////////////////////////////// INSPECT GRAPHS /////////////////////////////////////////
		ImmutableValueGraph<pointInSimplex, Integer> focusGraph = ImmutableValueGraph
				.copyOf(test.cohere.getFocusGraph());
		System.out.println();
		System.out.println();
		System.out.println("_/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ ");
		System.out.println("Focus graph has " + focusGraph.nodes().size() + " vertices, and "
				+ focusGraph.edges().size() + " edges");
		
		ImmutableValueGraph<pointInSimplex, Double> cohesionGraph = ImmutableValueGraph
				.copyOf(test.cohere.getCohesionGraph());
		System.out.println("_/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ ");
		System.out.println("Cohesion graph has " + cohesionGraph.nodes().size() + " vertices, and "
				+ cohesionGraph.edges().size() + " directed edges, including loops.");
		System.out.println("Mean cohesion value: " + test.cohere.getMeanCohesionValue());
		System.out.println("_/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ ");
		///////////////////////////////// MICRO-DIAGNOSTICS/////////////////////////////////////////
		if (microDiagnostics) {
			test.reportMicroDiagnostics();
		}
		System.out.println("_/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ ");
		System.out.println();
	}

	///////////////////////////////// FOCUS GRAPH
	///////////////////////////////// MICRO-DIAGNOSTICS////////////////////////////
	private void reportMicroDiagnostics() {
		int n2p = n * n + 1; // base for modular arithmetic, to improve readability
		System.out.println("List of all the friend sets: ");
		System.out.println();
		boolean listIncidenteEges = true;
		for (Map.Entry<pointInSimplex, SortedSet<pointInSimplex>> e : this.knnd.getFriends().entrySet()) {
			System.out.print("Base Point: " + (e.getKey().hashCode() % n2p) + " (mod " + n2p + ") has friends ");
			for (pointInSimplex x : e.getValue()) {
				System.out.print((x.hashCode() % n2p) + ", ");
			}
			System.out.println();
			/*
			 * For the first point in the list, study weights of all incident edges
			 */
			int u0, u1, w;
			if (listIncidenteEges) {
				System.out.println("Edges incident to first vertex: ");
				u0 = e.getKey().hashCode() % n2p;
				for (pointInSimplex x : this.cohere.getFocusGraph().adjacentNodes(e.getKey())) {
					u1 = x.hashCode() % n2p;
					w = this.cohere.getFocusGraph().edgeValueOrDefault(e.getKey(), x, Integer.MIN_VALUE);
					System.out.println("Edge {" + u0 + ", " + u1 + "}: " + w);
				}
				System.out.println();
			}
			listIncidenteEges = false; // only do the list once
		}
	}

}
