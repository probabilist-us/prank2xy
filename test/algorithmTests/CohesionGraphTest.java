/**
 * Design:
 * (0) Choose d, n, nc, k
 * (1) Generate samples from biased Dirichlet distributions (revised June 8, 2020)
 * (2) Apply kNNdescent
 * (3) Build focusGraph (done 4.12.2020)
 * (4) Build cohesionGraph  - done
 * (5) Build clusterGraph -done
 * (6) Compare clustering accuracy with DBSCAN, especially when cluster dispersion varies. 
 */
package algorithmTests;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntBiFunction;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import com.google.common.graph.EndpointPair;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.ImmutableValueGraph;

import algorithms.CohesionGraphBuilder;
import algorithms.KNNDescent;
import utilities.DirichletRandomVector;
import utilities.PointInSimplex;

/**
 * @author rwrd
 *
 */
public class CohesionGraphTest {
	int d, nc, n, k, numSampledPairs;
	Random g;
	DirichletRandomVector drv;
	List<PointInSimplex> points;
	KNNDescent<PointInSimplex> knnd;
	CohesionGraphBuilder<PointInSimplex> cohere;
	/*
	 * Comparator (associated with point x) ranks y, z according to KL Divergence
	 * from x
	 */
	Function<PointInSimplex, Comparator<PointInSimplex>> klcmpr = x -> x.getCprtr();

	public CohesionGraphTest(int dimension, int numGroups, int numPoints, int numNeighbors) {
		this.d = dimension;
		this.nc = numGroups;
		this.k = numNeighbors;
		g = new Random();
		this.drv = new DirichletRandomVector();
		/*
		 * Generate the sizes of the clusters, and the set of points in each cluster.
		 * Sizes proportional to inter-arrival times in a rate 1 Poisson process.
		 */
		int[] clusterSizes = Arrays.stream(this.drv.simulate(nc))
				.mapToInt(z -> (int) Math.round(z * (double) numPoints)).toArray(); /// rounding means these may not sum
																					/// exactly to n
		this.points = new ArrayList<PointInSimplex>();
		/*
		 * Generate samples from d-dimensional Dirichlet(k_1, k_2, ...k_d)
		 * distributions. Samples fall into nc groups. Within a group, samples are
		 * produced using the SAME set of Dirichlet parameters, which are randomly
		 * generated by the DirichletRandomVector class.
		 */
		for (int c = 0; c < nc; c++) {
			for (double[] p : this.drv.simulateWithRandomParams(this.d, clusterSizes[c])) {
				this.points.add(new PointInSimplex(this.d, p, c));
			}
		}
		this.n = this.points.size(); // may differ by a few from numPoints
		this.numSampledPairs = Math.min(20000, (this.n * (this.n - 1)) / 2); // for testing cluster quality
		System.out
				.println("# Dirichlet samples generated = " + this.points.size() + ": Reciprocal Uniform Parameters.");
		System.out.println("True sizes of the clusters:");
		for (int c = 0; c < nc; c++) {
			System.out.print(clusterSizes[c] + ", ");
		}
		System.out.println();
		PointInSimplex z = this.points.get(g.nextInt(n));
		System.out.println("A randomly sampled point came from template " + z.getTemplate() + ", with components");
		for (double u : z.getP()) {
			System.out.print(u + ", ");
		}
		System.out.println();
		// framework for K-nearest neighbor descent
		this.knnd = new KNNDescent<>(this.points, this.klcmpr, this.k);
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		int d = Integer.parseInt(args[0]);
		int nc = Integer.parseInt(args[1]);
		int n = Integer.parseInt(args[2]);
		int k = Integer.parseInt(args[3]);
		/*
		 * For manual checking of correctness for examples with < 100 vertices
		 */
		boolean microDiagnostics = true;
		/*
		 * Sample many pairs {x, y} of points. When x and y are in same true cluster,
		 * are they in same reported cluster?
		 */
		boolean clusterQualityReport = true;
		/*
		 * Compare local depth with alternative clustering, such as DBSCAN. For this,
		 * print points to CSV file.
		 */
		boolean writePointsToCSVFile = false; //
		// Random g = new Random();
		CohesionGraphTest test = new CohesionGraphTest(d, nc, n, k);

		Runtime rt = Runtime.getRuntime();
		System.out.println(
				"Java Runtime " + Runtime.version().toString() + "; Available processors: " + rt.availableProcessors());
		double gB = 1074741824.0;
		System.out.println("Maximum available memory: " + (double) rt.maxMemory() / gB + " Gb.");
		System.out.println("_/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ ");
		System.out.println("KNN Descent applied to Dirichlet samples of dimension " + test.d);
		int maxRounds = 2 * test.knnd.getExpanderBasedRoundCount();
		double sampleRate = 0.5;
		System.out.println("n = " + test.n + " points; " + test.k + " nearest friends.");
		long start = System.currentTimeMillis();
		test.knnd.kNNDescentAlgorithm(maxRounds, sampleRate);
		long duration = System.currentTimeMillis() - start;
		System.out.println("Total time for KNN descent: " + (0.001 * (double) duration) + " seconds.");
		System.out.println("Preparing focus graph and cohesion matrix.");
		System.out.println("_/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ ");
		start = System.currentTimeMillis();
		test.cohere = new CohesionGraphBuilder<PointInSimplex>(test.knnd.getFriends());
		duration = System.currentTimeMillis() - start;
		System.out.println("Total time for cohesion and cluster graph: " + (0.001 * (double) duration) + " seconds.");
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
		if (clusterQualityReport) {
			test.reportClusterQuality();
		}
		if (writePointsToCSVFile) {
			String fileName = "RecipUnif-d" + d + "-n" + n + "-nc" + nc + "-k" + k + "-" + System.currentTimeMillis();
			test.writePointsToCSV(fileName);
		}
	}

	///////////////////////////////// FOCUS GRAPH
	///////////////////////////////// MICRO-DIAGNOSTICS////////////////////////////
	/*
	 * Method of the class CohesionGraphTest
	 */
	private void reportMicroDiagnostics() {
		int n2p = 10 * n * n + 1; // base for modular arithmetic, to improve readability
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

	/////////////////////////// DIAGNOSE CLUSTER QUALITY/////////////
	/*
	 * return codes: 0 if x, y in same template, reported in same component (TP). 1
	 * if x, y in same template, reported in different components (FN). 2 if x, y in
	 * different corners, reported in same component (FP). 3 if x, y in different
	 * corners, reported in different components (TN).
	 */
	ToIntBiFunction<PointInSimplex, PointInSimplex> clusterValidator = (x, y) -> {
		boolean sameTemplate = (Integer.compare(x.getTemplate(), y.getTemplate()) == 0); // fields of x, y
		// Find connected component of the cluster graph which contains x.
		Set<PointInSimplex> componentX = this.cohere.getStronglyConnectedComponents().nodes().stream()
				.filter(component -> component.contains(x)).findFirst().orElse(new HashSet<>());
		boolean sameComponent = componentX.contains(y);
		int templateBit = sameTemplate ? 0 : 2;
		int reportBit = sameComponent ? 0 : 1;
		return templateBit + reportBit;
	};

	/*
	 * Method of the class CohesionGraphTest
	 */
	private void reportClusterQuality() {
		int[] tPfNfPtN = new int[4]; // components count TP, FN, FP, tN, respectively
		int xr, yr; // random indices
		int outcome;
		Arrays.fill(tPfNfPtN, 0);
		for (int i = 0; i < this.numSampledPairs; i++) {
			xr = g.nextInt(this.n);
			yr = g.nextInt(this.n - 1);
			yr = (yr >= xr) ? yr + 1 : yr; // ensures pair {x, y} are distinct and uniform
			outcome = this.clusterValidator.applyAsInt(this.points.get(xr), this.points.get(yr));
			tPfNfPtN[outcome]++;
		}
		System.out.println("_/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ _/ ");
		System.out.println("Confusion Matrix: ");
		System.out.println("[" + tPfNfPtN[0] + " " + tPfNfPtN[2] + "]");
		System.out.println("[" + tPfNfPtN[1] + " " + tPfNfPtN[3] + "]");
		System.out.println(this.numSampledPairs + " pairs of points were sampled.");
		int numSameTemplate = tPfNfPtN[0] + tPfNfPtN[1];
		double falseNegativeRate = 100.0 * (double) tPfNfPtN[1] / (double) numSameTemplate;
		System.out.println("Out of " + numSameTemplate + " in the same template, " + falseNegativeRate
				+ "% were falsely reported in different components.");
		int numDiffTemplate = tPfNfPtN[2] + tPfNfPtN[3];
		double falsePositiveRate = 100.0 * (double) tPfNfPtN[2] / (double) numDiffTemplate;
		System.out.println("Out of " + numDiffTemplate + " from different templates, " + falsePositiveRate
				+ "% were falsely reported in SAME component.");
	}

	private void writePointsToCSV(String fileName) {
		try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(fileName + ".csv"));
				CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT);) {
			int n2p = n * n + 1; // base for modular arithmetic, to improve readability
			int lines = 0;
			for (PointInSimplex x : this.points) {
				csvPrinter.print(Integer.toString(x.hashCode() % n2p)); // point ID
				csvPrinter.print(Integer.toString(x.getTemplate())); // template it belongs to
				for (double z : x.getP()) {
					csvPrinter.print(Double.toString(z)); // components of the vector
				}
				csvPrinter.println();
				lines++;
			}
			System.out.println("CSV file created with " + lines + " lines, called " + fileName + ".csv");
			csvPrinter.flush();
			writer.flush();
			writer.close();
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}
}
