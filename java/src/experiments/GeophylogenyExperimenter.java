package experiments;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

import algorithms.DPGeophylogenyOrderer;
import algorithms.GeophylogenyOrderer;
import algorithms.GreedyGeophylogenyOrderOptimizer;
import algorithms.TopDownGeophylogenyOrderer;
import algorithms.DPGeophylogenyOrderer.DPStrategy;
import io.GeophylogenyDrawer;
import io.GeophylogenyIO;
import model.Geophylogeny;
import model.Leader;
import model.Leader.GeophylogenyLeaderType;
import model.Site;
import model.Tree;
import model.Vertex;

/**
 * This class provides a method to test the heuristics on generated examples and
 * the real-world examples.
 * 
 * To use, set parameters and paths as wanted, comment in/out different
 * heuristics, and output. The experiment results are printed to the console.
 *
 * @author Jonathan Klawitter
 */
@SuppressWarnings("unused")
public class GeophylogenyExperimenter {
	
	public enum ExperimentType {
		GENERATED_INSTANCE, REAL_WORLD_INSTANCE
	}

	public enum GenerateType {
		UNIFORM, COASTLINE, CLUSTERED
	}

	private static final int START_N = 10;
	private static final int END_N = 30;
	private static final int STEP_N = 10;
	private static final int REPEATS = 10;
	private static final GeophylogenyLeaderType LEADER_TYPE = GeophylogenyLeaderType.S;

	private static final ExperimentType EXPERIMENT_TYPE = ExperimentType.REAL_WORLD_INSTANCE;
	private static final GenerateType GENERATE_TYPE = GenerateType.CLUSTERED;

	private static final String FILE_PATH = "D:\\Alex\\TUM\\Seminar\\geophylo\\output_new\\";
    private static final String FILE_INPUT_PATH = "D:\\Alex\\TUM\\Seminar\\geophylo\\data/realWorld/";
	private static final String FILE_RW_FROGS = "rwExampleShrubFrogs.json";
	private static final String FILE_RW_FISH = "rwExampleFish.json";
	private static final String FILE_RW_LIZARD = "rwExampleGreenLizards.json";
	private static final double RW_SCALE_FISH = 1.0;
	private static final double RW_SCALE_LIZARD = 30.0;
	private static final double RW_SCALE_FROGS = 15.0;

	private static StringBuilder size = new StringBuilder(String.format("%-17s", "size: "));
	// heuristics on their own
	private static final StringBuilder optimizerOnly = new StringBuilder(String.format("%-17s", "greedyOptimizer: ")); // String.format is to left-justify text
	private static final StringBuilder topDown = new StringBuilder(String.format("%-17s", "topDown: "));
	private static final StringBuilder bottomUp = new StringBuilder(String.format("%-17s", "buttomUp: "));
	private static final StringBuilder euclidean = new StringBuilder(String.format("%-17s", "euclidean: "));
	private static final StringBuilder horizontal = new StringBuilder(String.format("%-17s", "horizontal: "));
	private static final StringBuilder hop = new StringBuilder(String.format("%-17s", "hop: "));
	// heuristics + greedy optimizer (hill climbing)
	private static final StringBuilder topDownP = new StringBuilder(String.format("%-17s", "topDown+: "));
	private static final StringBuilder bottomUpP = new StringBuilder(String.format("%-17s", "buttomUp+: "));
	private static final StringBuilder euclideanP = new StringBuilder(String.format("%-17s", "euclidean+: "));
	private static final StringBuilder horizontalP = new StringBuilder(String.format("%-17s", "horizontal+: "));
	private static final StringBuilder hopP = new StringBuilder(String.format("%-17s", "hop+: "));

	private static class BestDrawing {
		private final Geophylogeny geophylogeny;
		private final int crossings;
		private final String filename;

		private BestDrawing(Geophylogeny geophylogeny, int crossings, String filename) {
			this.geophylogeny = geophylogeny;
			this.crossings = crossings;
			this.filename = filename;
		}
	}

	public static void main(String[] args) {

		if (EXPERIMENT_TYPE == ExperimentType.GENERATED_INSTANCE) {
			int numClusters = 2;

			for (int i = START_N; i <= END_N; i += STEP_N) {
				Random seedGenerator = new Random(i);
				if (i % 10 == 0) {
					numClusters++;
				}

				for (int j = 0; j < REPEATS; j++) {
					size.append(i).append(", ");

					long seed = seedGenerator.nextLong();


					Geophylogeny geophylo = null;

					/* Use generated instance */
					String name = GENERATE_TYPE.toString().toLowerCase() +
							"-n" + i + "-r" + j + "-s" + seed +
							"-" + LEADER_TYPE.toString().toLowerCase() + "-d"
							+ GeophylogenyInstanceCreater.EXPONENT;

					switch (GENERATE_TYPE) {
						case UNIFORM -> geophylo = GeophylogenyInstanceCreater.generateUniformInstance(500, 300, i,
								name, seed);
						case COASTLINE -> geophylo = GeophylogenyInstanceCreater.generateCoastlineInstance(500, 300, i,
								name, seed);
						case CLUSTERED -> geophylo = GeophylogenyInstanceCreater.generateClusteredInstance(500, 300, i,
								numClusters, name, seed);
					}

					geophylo.setLeaderType(LEADER_TYPE);

					/* to store generated instance, comment in the following */
					// GeophylogenyIO.writeGeophylogenyToJSON(geophylo, FILE_PATH +
					// name + ".json");

					runExperimentOnInstance(geophylo, name, false);
				}
			}
		} else if (EXPERIMENT_TYPE == ExperimentType.REAL_WORLD_INSTANCE) {
			// // Comment out either set to choose between the author's RW instances and mine
			//String[] rwFiles = { FILE_RW_FISH, FILE_RW_LIZARD, FILE_RW_FROGS, "EasternBaltic.json"};
			//double[] rwScales = { RW_SCALE_FISH, RW_SCALE_LIZARD, RW_SCALE_FROGS, 1 };
			String[] rwFiles = { "Balto-Slavic.json", "Formosan.json"};
			double[] rwScales = {1, 1};
			for (int i = 0; i < rwFiles.length; i++) {
				String rwFile = rwFiles[i];
				double rwScale = rwScales[i];

				Random seedGenerator = new Random(rwFile.hashCode());
				BestDrawing bestForInstance = null;

				for (int j = 0; j < REPEATS; j++) {
					long seed = seedGenerator.nextLong();

					Geophylogeny geophylo = GeophylogenyIO.readGeophylogenyFromJSON(FILE_INPUT_PATH + rwFile);
					geophylo.scale(rwScale);
					geophylo.setLeaderType(LEADER_TYPE);
					String name = rwFile.replace(".json", "") + "-r" + j + "-" +
							LEADER_TYPE.toString().toLowerCase();
					size.append(geophylo.getSites().length).append(", ");

					BestDrawing bestForRun = runExperimentOnInstance(geophylo, name, true);
					if (bestForRun != null && (bestForInstance == null || bestForRun.crossings < bestForInstance.crossings)) {
						bestForInstance = bestForRun;
					}
				}

				if (bestForInstance != null) {
					saveDrawing(bestForInstance.geophylogeny, bestForInstance.filename);
				}
			}
		}

		System.out.println(size);
		System.out.println("# of crossings:");
		System.out.println(optimizerOnly);
		System.out.println(topDown);
		System.out.println(bottomUp);
		System.out.println(euclidean);
		System.out.println(horizontal);
		System.out.println(hop);
		System.out.println(topDownP);
		System.out.println(bottomUpP);
		System.out.println(euclideanP);
		System.out.println(horizontalP);
		System.out.println(hopP);
	}

	private static BestDrawing runExperimentOnInstance(Geophylogeny geophylo, String name, boolean trackBest) {
		GeophylogenyOrderer optimizer = new GreedyGeophylogenyOrderOptimizer(geophylo);
		BestDrawing best = null;
		int crossings;

		// 1. Optimizer Only (Greedy Hill Climbing)
		// Hill climbing: 只看當下周圍的一小步範圍，如果某個方向會更好 (如減少 crossings)，就往那邊走一步
		optimizer.orderLeaves();
		crossings = geophylo.computeNumberOfCrossings();
		optimizerOnly.append(crossings).append(", ");
		best = updateBest(best, geophylo, crossings, name + "-optimizerOnly", trackBest);
		// saveDrawing(geophylo, name + "-optimizerOnly");

		// 2. TopDown
		GeophylogenyOrderer ordered = new TopDownGeophylogenyOrderer(geophylo);
		ordered.orderLeaves();
		crossings = geophylo.computeNumberOfCrossings();
		topDown.append(crossings).append(", ");
		best = updateBest(best, geophylo, crossings, name + "-topDown", trackBest);
		// saveDrawing(geophylo, name + "-topDown");

		optimizer.orderLeaves();
		crossings = geophylo.computeNumberOfCrossings();
		topDownP.append(crossings).append(", ");
		best = updateBest(best, geophylo, crossings, name + "-topDownPlus", trackBest);
		// saveDrawing(geophylo, name + "-topDownPlus");

		// 3. BottomUp (DP Crossings)
		ordered = new DPGeophylogenyOrderer(geophylo, DPStrategy.Crossings);
		ordered.orderLeaves();
		crossings = geophylo.computeNumberOfCrossings();
		bottomUp.append(crossings).append(", ");
		best = updateBest(best, geophylo, crossings, name + "-bottomUp", trackBest);
		// saveDrawing(geophylo, name + "-bottomUp");

		optimizer.orderLeaves();
		crossings = geophylo.computeNumberOfCrossings();
		bottomUpP.append(crossings).append(", ");
		best = updateBest(best, geophylo, crossings, name + "-bottomUpPlus", trackBest);
		// saveDrawing(geophylo, name + "-bottomUpPlus");

		// 4. Quality Measure: Euclidean (also DP)
		ordered = new DPGeophylogenyOrderer(geophylo, DPStrategy.EuclideanDistance);
		ordered.orderLeaves();
		crossings = geophylo.computeNumberOfCrossings();
		euclidean.append(crossings).append(", ");
		best = updateBest(best, geophylo, crossings, name + "-euclidean", trackBest);
		// saveDrawing(geophylo, name + "-euclidean");

		optimizer.orderLeaves();
		crossings = geophylo.computeNumberOfCrossings();
		euclideanP.append(crossings).append(", ");
		best = updateBest(best, geophylo, crossings, name + "-euclideanPlus", trackBest);
		// saveDrawing(geophylo, name + "-euclideanPlus");

		// 5. Quality Measure: Horizontal (XOffset) (also DP)
		ordered = new DPGeophylogenyOrderer(geophylo, DPStrategy.HorizontalDistance);
		ordered.orderLeaves();
		crossings = geophylo.computeNumberOfCrossings();
		horizontal.append(crossings).append(", ");
		best = updateBest(best, geophylo, crossings, name + "-horizontal", trackBest);
		// saveDrawing(geophylo, name + "-horizontal");

		optimizer.orderLeaves();
		crossings = geophylo.computeNumberOfCrossings();
		horizontalP.append(crossings).append(", ");
		best = updateBest(best, geophylo, crossings, name + "-horizontalPlus", trackBest);
		// saveDrawing(geophylo, name + "-horizontalPlus");

		// 6. Quality Measure: Hop (IndexOffset) (also DP)
		ordered = new DPGeophylogenyOrderer(geophylo, DPStrategy.Hops);
		ordered.orderLeaves();
		crossings = geophylo.computeNumberOfCrossings();
		hop.append(crossings).append(", ");
		best = updateBest(best, geophylo, crossings, name + "-hop", trackBest);
		// saveDrawing(geophylo, name + "-hop");

		optimizer.orderLeaves();
		crossings = geophylo.computeNumberOfCrossings();
		hopP.append(crossings).append(", ");
		best = updateBest(best, geophylo, crossings, name + "-hopPlus", trackBest);

		return best;
	}

	private static void saveDrawing(Geophylogeny geophylo, String filename) {
		ensureOutputDir();
		geophylo.computeXCoordinates();
		GeophylogenyDrawer drawer = new GeophylogenyDrawer(geophylo, FILE_PATH + filename + "-" + getFormattedTimeNow() + ".svg");
		drawer.drawGeophylogeny();
		// System.out.println("Drawing saved to: " + FILE_PATH + filename + ".svg");
	}

	private static void ensureOutputDir() {
		File outputDir = new File(FILE_PATH);
		if (!outputDir.exists()) {
			outputDir.mkdirs();
		}
	}

	private static BestDrawing updateBest(BestDrawing currentBest, Geophylogeny geophylo, int crossings, String filename, boolean trackBest) {
		if (!trackBest) {
			return currentBest;
		}
		if (currentBest == null || crossings < currentBest.crossings) {
			return new BestDrawing(cloneGeophylogeny(geophylo), crossings, filename);
		}
		return currentBest;
	}

	private static Geophylogeny cloneGeophylogeny(Geophylogeny original) {
		Tree originalTree = original.getTree();
		Vertex rootCopy = copyVertex(originalTree.getRoot());
		Tree treeCopy = new Tree(rootCopy, originalTree.getNumberOfLeaves(), originalTree.getStateNumber());
		treeCopy.setName(originalTree.getName());

		Site[] originalSites = original.getSites();
		Site[] sitesCopy = new Site[originalSites.length];
		for (int i = 0; i < originalSites.length; i++) {
			Site originalSite = originalSites[i];
			Site siteCopy = new Site(originalSite.getX(), originalSite.getY());
			siteCopy.setCluster(originalSite.getCluster());
			sitesCopy[i] = siteCopy;
		}

		Vertex[] leaves = treeCopy.getLeavesInIndexOrder();
		for (int i = 0; i < leaves.length; i++) {
			sitesCopy[i].setLeaf(leaves[i]);
		}

		Geophylogeny copy = new Geophylogeny(treeCopy, sitesCopy, original.getMapWidth(), original.getMapHeight(), original.getName(), original.getLeaderType());
		if (original.hasClusters()) {
			Vertex[] originalLeaves = originalTree.getLeavesInIndexOrder();
			int[] clusterOfLeaf = new int[originalLeaves.length];
			for (int i = 0; i < originalLeaves.length; i++) {
				clusterOfLeaf[i] = original.getClusterOfVertex(originalLeaves[i]);
			}
			copy.setClustersByMapping(clusterOfLeaf);
		}

		return copy;
	}

	private static Vertex copyVertex(Vertex original) {
		Vertex copy;
		if (original.isLeaf()) {
			copy = new Vertex(original.getID());
		} else {
			Vertex leftCopy = copyVertex(original.getLeftChild());
			Vertex rightCopy = copyVertex(original.getRightChild());
			copy = new Vertex(original.getID(), leftCopy, rightCopy);
		}

		copy.setTaxonName(original.getTaxonName());
		copy.setBranchLengthIncoming(original.getBranchLengthIncoming());
		copy.setPopulationSize(original.getPopulationSize());
		copy.setDiscreteDepth(original.getDiscreteDepth());
		copy.setDepth(original.getDepth());
		copy.setHeight(original.getHeight());
		if (original.isFixed()) {
			copy.setFixed();
		}

		return copy;
	}

	private static String getFormattedTimeNow() {
		LocalDateTime now = LocalDateTime.now();

		DateTimeFormatter formatter =
				DateTimeFormatter.ofPattern("MMdd'T'HHmm");

        return now.format(formatter);
	}
}
