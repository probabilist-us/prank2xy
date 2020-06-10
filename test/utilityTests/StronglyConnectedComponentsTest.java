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
		 * Data created from d = 6, n = 30, k = 8 run of CohesionGraphTest
		 */
		int[][] arcs = new int[][] { { 626, 892 }, { 626, 297 }, { 626, 299 }, { 626, 154 }, { 626, 528 }, { 892, 626 },
				{ 892, 229 }, { 892, 116 }, { 892, 448 }, { 892, 154 }, { 892, 528 }, { 299, 297 }, { 299, 626 },
				{ 299, 229 }, { 299, 116 }, { 299, 154 }, { 299, 528 }, { 297, 626 }, { 297, 448 }, { 297, 299 },
				{ 154, 892 }, { 154, 626 }, { 154, 229 }, { 154, 116 }, { 154, 299 }, { 154, 528 }, { 528, 892 },
				{ 528, 626 }, { 528, 63 }, { 528, 229 }, { 528, 299 }, { 528, 154 }, { 40, 822 }, { 40, 885 },
				{ 40, 58 }, { 40, 583 }, { 40, 198 }, { 885, 40 }, { 885, 122 }, { 885, 138 }, { 885, 58 },
				{ 885, 583 }, { 58, 182 }, { 58, 40 }, { 58, 122 }, { 58, 138 }, { 58, 885 }, { 58, 448 }, { 583, 321 },
				{ 583, 40 }, { 583, 885 }, { 583, 736 }, { 583, 448 }, { 583, 198 }, { 198, 321 }, { 198, 40 },
				{ 198, 583 }, { 822, 40 }, { 593, 354 }, { 593, 816 }, { 816, 354 }, { 816, 593 }, { 354, 593 },
				{ 354, 816 }, { 63, 448 }, { 63, 528 }, { 448, 892 }, { 448, 297 }, { 448, 63 }, { 229, 892 },
				{ 229, 116 }, { 229, 299 }, { 229, 154 }, { 229, 528 }, { 116, 892 }, { 116, 229 }, { 116, 299 },
				{ 116, 154 }, { 138, 884 }, { 138, 182 }, { 138, 325 }, { 138, 122 }, { 138, 885 }, { 138, 58 },
				{ 122, 885 }, { 122, 58 }, { 122, 138 }, { 448, 884 }, { 448, 182 }, { 448, 325 }, { 448, 648 },
				{ 448, 736 }, { 448, 58 }, { 448, 583 }, { 182, 884 }, { 182, 648 }, { 182, 138 }, { 182, 736 },
				{ 182, 448 }, { 182, 58 }, { 321, 216 }, { 321, 325 }, { 321, 198 }, { 321, 583 }, { 884, 216 },
				{ 884, 182 }, { 884, 325 }, { 884, 648 }, { 884, 138 }, { 884, 736 }, { 884, 448 }, { 325, 884 },
				{ 325, 321 }, { 325, 216 }, { 325, 648 }, { 325, 138 }, { 325, 736 }, { 325, 448 }, { 736, 884 },
				{ 736, 182 }, { 736, 325 }, { 736, 648 }, { 736, 381 }, { 736, 448 }, { 736, 583 }, { 216, 884 },
				{ 216, 321 }, { 216, 325 }, { 216, 648 }, { 648, 884 }, { 648, 216 }, { 648, 182 }, { 648, 325 },
				{ 648, 736 }, { 648, 448 }, { 381, 736 } };
		MutableGraph<Integer> graph = GraphBuilder.directed().allowsSelfLoops(false).build();
		for (int a = 0; a < arcs.length; a++) {
			graph.putEdge(arcs[a][0], arcs[a][1]);
		}
		return ImmutableGraph.copyOf(graph);
	};

}
