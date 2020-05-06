/*
 * On a 28-vertex digraph, same result as for Mathematica strong components. 5.6.2020
 */
package utilityTests;

import java.util.Set;

import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.MutableGraph;

import utilities.GraphUtils;

public class StronglyConnectedComponentsTest {
	Graph<Integer> digraph;
	Graph<Set<Integer>> strongComps;

	public StronglyConnectedComponentsTest() {
		super();
		this.digraph = this.toyGraph();
		this.strongComps = GraphUtils.findStronglyConnectedComponents(this.digraph);
	}

	public static void main(String[] args) {
		System.out.println("Reading in the toy graph, and computing strongly connected components");
		StronglyConnectedComponentsTest test = new StronglyConnectedComponentsTest();
		for (Set<Integer> component : test.strongComps.nodes()) {
			System.out.print("Component: {");
			for (Integer vertex : component) {
				System.out.print(vertex + ", ");
			}
			System.out.println("}");
		}

	}

	private ImmutableGraph<Integer> toyGraph() {
		/*
		 * Data created from 6 = 6, n = 30, k = 8 run of CohesionGraphTest
		 */
		int[][] arcs = new int[][] { { 310, 604 }, { 310, 175 }, { 604, 310 }, { 604, 786 }, { 604, 175 }, { 175, 604 },
				{ 175, 786 }, { 175, 712 }, { 712, 310 }, { 712, 786 }, { 712, 175 }, { 715, 604 }, { 715, 786 },
				{ 786, 604 }, { 786, 175 }, { 786, 715 }, { 681, 62 }, { 401, 230 }, { 401, 396 }, { 524, 62 },
				{ 524, 835 }, { 524, 230 }, { 62, 835 }, { 62, 524 }, { 835, 62 }, { 835, 515 }, { 835, 606 },
				{ 515, 835 }, { 515, 524 }, { 515, 606 }, { 62, 835 }, { 62, 235 }, { 62, 606 }, { 235, 62 },
				{ 235, 835 }, { 606, 62 }, { 606, 835 }, { 606, 515 }, { 38, 350 }, { 38, 778 }, { 350, 778 },
				{ 350, 25 }, { 778, 38 }, { 778, 350 }, { 25, 818 }, { 25, 350 }, { 818, 350 }, { 818, 25 },
				{ 230, 517 }, { 230, 896 }, { 896, 814 }, { 896, 230 }, { 517, 259 }, { 517, 230 }, { 230, 401 },
				{ 230, 727 }, { 230, 524 }, { 727, 259 }, { 727, 230 }, { 396, 401 }, { 396, 230 }, { 596, 396 },
				{ 259, 120 }, { 259, 517 }, { 120, 259 }, { 120, 727 }, { 814, 230 }, { 814, 896 } };
		MutableGraph<Integer> graph = GraphBuilder.directed().allowsSelfLoops(false).build();
		for (int a = 0; a < arcs.length; a++) {
			graph.putEdge(arcs[a][0], arcs[a][1]);
		}
		return ImmutableGraph.copyOf(graph);
	};

}
