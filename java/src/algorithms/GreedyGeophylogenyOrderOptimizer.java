package algorithms;

import java.util.Random;

import model.Vertex;
import model.Geophylogeny;

/**
 * This class implements the greedy hill climbing heuristic to improve the leaf
 * order of a geophylogeny in order to minimize leader crossings.
 *
 * @author Jonathan Klawitter
 */
public class GreedyGeophylogenyOrderOptimizer extends GeophylogenyOrderer {

	public GreedyGeophylogenyOrderOptimizer(Geophylogeny geophylogeny) {
		super(geophylogeny);
	}

	/**
	 * Optimizes the order of this optimizers geophylogeny as long as it finds
	 * improvements.
	 */
	@Override
	public void orderLeaves() {
		int[] vertexTestOrder = createRandomVertexTestOrder();

		int improvement;
		int totalImprovement = 0;
		do {
			improvement = 0;
			improvement = optimizeOneRound(vertexTestOrder);
			totalImprovement += improvement;
		} while (improvement > 0);

		// System.out.println("total improvement: " + totalImprovement); # the "+": how much can a greedy hill climbing improve?
	}

	/**
	 * Optimize the leaf order for one round (each vertex once) in a random
	 * order.
	 * 
	 * @return the number of crossings saved
	 */
	public int optimizeOneRound() {
		return optimizeOneRound(createRandomVertexTestOrder());
	}

	private int optimizeOneRound(int[] vertexTestOrder) {
		Vertex[] vertices = tree.getInnnerVertices();
		int currentNumberOfCrossings = geophylogeny.computeNumberOfCrossings();
		int improvement = 0;
		for (int i = 0; i < vertexTestOrder.length; i++) {
			Vertex vertex = vertices[vertexTestOrder[i]];
			vertex.rotate();
			this.geophylogeny.computeXCoordinates();
			int rotatedNumberOfCrossings = geophylogeny.computeNumberOfCrossings();
			if (rotatedNumberOfCrossings < currentNumberOfCrossings) {
				improvement += (currentNumberOfCrossings - rotatedNumberOfCrossings);
				currentNumberOfCrossings = rotatedNumberOfCrossings;
			} else {
				vertex.rotate();
				this.geophylogeny.computeXCoordinates();
			}
		}
		return improvement;
	}

	private int[] createRandomVertexTestOrder() {
		Random random = new Random();

		int[] vertexTestOrder = new int[tree.getNumberOfVertices() - tree.getNumberOfLeaves()];
		for (int i = 0; i < vertexTestOrder.length; i++) {
			vertexTestOrder[i] = i;
		}

		// Fisher-Yades shuffle
		for (int i = vertexTestOrder.length - 1; i >= 1; i--) {
			int j = random.nextInt(i);
			int temp = vertexTestOrder[i];
			vertexTestOrder[i] = vertexTestOrder[j];
			vertexTestOrder[j] = temp;
		}

		return vertexTestOrder;
	}

}
