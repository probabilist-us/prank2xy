/**
 * Design:
 * (1) generate samples from biased Dirichlet distributions (d types)
 * (2) Apply kNNdescent
 * (3) Check focusGraph (done 4.12.2020)
 * (4) Check cohesionGraph - may need to drop edge directions (done 5.6.20)
 * (5) Check clusterGraph - results on 30 points in d=6 agreed well with single linkage clustering in Wolfram. (done 5.5.20)
 * Next task: tests at larger scale
 * 5.27.20 d=12, n=20000, k = 32: success in fimding 12 groups. Only 29 outlier vertices.
 * 5.29.2020: Goal output a CSV file. Compare clustering accuracy with DBSCAN
 */
package algorithmTests;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.NavigableSet;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.graph.EndpointPair;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.ImmutableValueGraph;

import algorithms.CohesionGraphBuilder;
import algorithms.KNNDescent;
import utilities.PointInSimplex;

/**
 * @author rwrd
 *
 */
public class CohesionGraphTest {
	int d, n, k;
	Random g;
	List<PointInSimplex> points;
	KNNDescent<PointInSimplex> knnd;
	CohesionGraphBuilder<PointInSimplex> cohere;
	final int dirichletP = 3; // will add dirichletP exponential r.v.s at a time
	/*
	 * Generate samples from d-dimensional Dirichlet(k_1, k_2, ...k_d)
	 * distributions. Here k_i = 3, except for one randomly chosen coordinate, where
	 * k_j = 3 * d. Such parameters were observed in Mathematica to produce d well
	 * separated groups of points on a (d-1)-dimensional simplex.
	 */
	Supplier<PointInSimplex> tiltedDirichletGenerator = () -> {
		double[] vec = new double[d];
		int boost = g.nextInt(d); // random component to be boosted
		int shape;
		double sum = 0.0;
		for (int i = 0; i < d; i++) {
			shape = (i == boost) ? this.dirichletP * (this.d - 1) : this.dirichletP; // boosted component is as heavy as
																						// all other combined.
			vec[i] = 0.0;
			// vec[i] will have a Gamma(shape, 1) distribution
			for (int j = 0; j < shape; j++) {
				vec[i] -= Math.log(g.nextDouble()); // add Exponential r.v.
			}
			sum += vec[i];
		}
		// normalize the entries in vec to sum to 1.0
		for (int i = 0; i < this.d; i++) {
			vec[i] = vec[i] / sum;
		}
		return new PointInSimplex(this.d, vec);
	};

	/*
	 * Comparator (associated with point x) ranks y, z according to KL Divergence
	 * from x
	 */
	Function<PointInSimplex, Comparator<PointInSimplex>> klcmpr = x -> x.getCprtr();

	public CohesionGraphTest(int dimension, int numPoints, int numNeighbors) {
		this.d = dimension;
		this.n = numPoints;
		this.k = numNeighbors;
		g = new Random();
		/*
		 * Generate the set of points, and the framework for K-nearest neighbor descent
		 */
		this.points = new ArrayList<PointInSimplex>();
		this.points.addAll(Stream.generate(tiltedDirichletGenerator::get).limit(this.n).collect(Collectors.toList()));
		System.out.println("# Tilted Dirichlet samples generated = " + this.points.size());
		System.out.println("Randomly sampled point: ");
		PointInSimplex z = this.points.get(g.nextInt(n));
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
		boolean microDiagnostics = false; // only do this for examples with < 100 vertices
		// Random g = new Random();
		CohesionGraphTest test = new CohesionGraphTest(d, n, k);
		Runtime rt = Runtime.getRuntime();
		System.out.println(
				"Java Runtime " + Runtime.version().toString() + "; Available processors: " + rt.availableProcessors());
		double gB = 1074741824.0;
		System.out.println("Maximum available memory: " + (double) rt.maxMemory() / gB + " Gb.");
		System.out.println("_/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ ");
		System.out.println("KNN Descent applied to tilted Dirichlet samples of dimension " + test.d);
		int maxRounds = 2 * test.knnd.getExpanderBasedRoundCount();
		double sampleRate = 0.5;
		System.out.println("n = " + test.n + " points; " + test.k + " nearest friends.");
		test.knnd.kNNDescentAlgorithm(maxRounds, sampleRate);
		System.out.println("Preparing focus graph and cohesion matrix.");
		System.out.println("_/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ ");
		test.cohere = new CohesionGraphBuilder<PointInSimplex>(test.knnd.getFriends());
		///////////////////////////////// INSPECT GRAPHS
		///////////////////////////////// /////////////////////////////////////////
		ImmutableValueGraph<PointInSimplex, Integer> focusGraph = ImmutableValueGraph
				.copyOf(test.cohere.getFocusGraph());
		ImmutableGraph<PointInSimplex> mutualFriendGraph = ImmutableGraph.copyOf(test.cohere.getMutualFriendGraph());
		System.out.println();
		System.out.println();
		System.out.println("_/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ ");
		System.out.println("Focus graph has " + focusGraph.nodes().size() + " vertices, and "
				+ focusGraph.edges().size() + " edges");
		System.out.println("Mutual friend graph has " + mutualFriendGraph.nodes().size() + " vertices, and "
				+ mutualFriendGraph.edges().size() + " edges");
		double mfprop = 2.0 * (double) mutualFriendGraph.edges().size() / (double) (k * n); // double to obtain total
																							// vertex degree
		System.out.println("Average proportion of friends which are also co-friends is " + mfprop);
		///////////////////////////////// /////////////////////////////////////////
		ImmutableValueGraph<PointInSimplex, Double> cohesionGraph = ImmutableValueGraph
				.copyOf(test.cohere.getCohesionGraph());

		System.out.println("_/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ ");
		System.out.println("Cohesion graph has " + cohesionGraph.nodes().size() + " vertices, and "
				+ cohesionGraph.edges().size() + " directed edges, including loops.");
		System.out.println("Empirical mean cohesion value: " + test.cohere.getEmpiricalMeanCohesion());
		System.out.println("Theoretical mean cohesion value: " + test.cohere.getTheoreticalMeanCohesion());
		System.out.println("_/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ ");
		///////////////////////////////// MICRO-DIAGNOSTICS - OPTIONAL
		///////////////////////////////// /////////////////////////////////////////
		if (microDiagnostics) {
			test.reportMicroDiagnostics();
		}

		///////////////////////////////// /////////////////////////////////////////
		///////////////////////////////// CLUSTER GRAPH AND ITS COMPONENTS ///////
		ImmutableGraph<PointInSimplex> clusterGraph = ImmutableGraph.copyOf(test.cohere.getClusterGraph());

		ImmutableGraph<Set<PointInSimplex>> componentGraph = ImmutableGraph
				.copyOf(test.cohere.getStronglyConnectedComponents());
		// Group components by size - important
		Map<Integer, Long> componentSizes = componentGraph.nodes().stream()
				.collect(Collectors.groupingBy(comp -> comp.size(), Collectors.counting()));
		List<Map.Entry<Integer, Long>> sortedComponentSizes = new ArrayList<>(componentSizes.entrySet());
		/*
		 * Comparator uses sizes of components
		 */
		sortedComponentSizes.sort((e0, e1) -> Integer.compare(e0.getKey(), e1.getKey()));
		// count the number of isolated components
		long numIsolatedNodes = componentGraph.nodes().stream().mapToInt(comp -> comp.size()).filter(s -> (s == 1))
				.count();
		int numNontrivialComponents = componentGraph.nodes().size() - (int) numIsolatedNodes;
		System.out.println("Cluster graph has " + clusterGraph.edges().size() + " edges, and " + numNontrivialComponents
				+ " non-trivial components.");
		System.out.println("Component size tally:");
		for (Map.Entry<Integer, Long> e : sortedComponentSizes) {
			System.out.println("Size: " + e.getKey() + ": count = " + e.getValue());
		}
	}

	///////////////////////////////// FOCUS GRAPH
	///////////////////////////////// MICRO-DIAGNOSTICS////////////////////////////
	private void reportMicroDiagnostics() {
		int n2p = n * n + 1; // base for modular arithmetic, to improve readability
		System.out.println("_/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ ");
		System.out.println("List of points and their coordinates: ");
		for (PointInSimplex x : this.points) {
			System.out.print("{" + (x.hashCode() % n2p) + " ");
			for (double u : x.getP()) {
				System.out.print(", " + u);
			}
			System.out.print("},");
			System.out.println();
		}
		System.out.println("_/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ ");
		System.out.println("List of all the friend sets: ");
		System.out.println();
		boolean listIncidenteEges = true;
		for (Map.Entry<PointInSimplex, NavigableSet<PointInSimplex>> e : this.knnd.getFriends().entrySet()) {
			System.out.print("Base Point: " + (e.getKey().hashCode() % n2p) + " (mod " + n2p + ") has friends ");
			for (PointInSimplex x : e.getValue()) {
				System.out.print((x.hashCode() % n2p) + ", ");
			}
			System.out.println();
			/*
			 * For the first point in the list, study weights of all incident edges
			 */
			int u0, u1, w;
			if (listIncidenteEges) {
				System.out.println("Edges incident to first vertex, and size of conflict focus V_{x, y} ");
				u0 = e.getKey().hashCode() % n2p;
				for (PointInSimplex x : this.cohere.getFocusGraph().adjacentNodes(e.getKey())) {
					u1 = x.hashCode() % n2p;
					w = this.cohere.getFocusGraph().edgeValueOrDefault(e.getKey(), x, Integer.MIN_VALUE);
					System.out.println("Edge {" + u0 + ", " + u1 + "}: " + w);
				}
				System.out.println();
			}
			listIncidenteEges = false; // only do the list once
		}
		System.out.println();
		System.out.println("_/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ ");
		System.out.println("Cohesion graph: edge weights, omitting loops");
		int counter = 0;
		int u, v;
		double w;
		for (EndpointPair<PointInSimplex> pair : this.cohere.getCohesionGraph().edges()) {
			u = pair.source().hashCode() % n2p;
			v = pair.target().hashCode() % n2p;
			w = this.cohere.getCohesionGraph().edgeValueOrDefault(pair, 0.0);
			// Omit self-loops
			if (u != v) {
				System.out.print("{" + u + ", " + v + ", " + w + "}, ");
			}
			counter++;
			if (counter % 5 == 0) {
				System.out.println();
			}
		}
		System.out.println();

		System.out.println();
		System.out.println("_/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ ");
		System.out.println("Cluster graph: edge list");
		counter = 0;
		for (EndpointPair<PointInSimplex> pair : this.cohere.getClusterGraph().edges()) {
			u = pair.source().hashCode() % n2p;
			v = pair.target().hashCode() % n2p;
			System.out.print("{" + u + ", " + v + "}, ");
			counter++;
			if (counter % 10 == 0) {
				System.out.println();
			}
		}
		System.out.println();
		System.out.println();
		System.out.println("_/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ ");
		System.out.println("Cluster graph: strong components");
		for (Set<PointInSimplex> component : this.cohere.getStronglyConnectedComponents().nodes()) {
			System.out.print("Component: {");
			for (PointInSimplex vertex : component) {
				System.out.print((vertex.hashCode() % n2p) + ", ");
			}
			System.out.println("}");
		}

	}

}
