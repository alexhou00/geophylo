package experiments;

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

	private static final ExperimentType EXPERIMENT_TYPE = ExperimentType.GENERATED_INSTANCE;
	private static final GenerateType GENERATE_TYPE = GenerateType.CLUSTERED;

	private static final String FILE_PATH = "D:\\Alex\\TUM\\Seminar\\geophylo\\output_serious\\";
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

					runExperimentOnInstance(geophylo, name);
				}
			}
		} else if (EXPERIMENT_TYPE == ExperimentType.REAL_WORLD_INSTANCE) {
			String[] rwFiles = { FILE_RW_FISH, FILE_RW_LIZARD, FILE_RW_FROGS };
			double[] rwScales = { RW_SCALE_FISH, RW_SCALE_LIZARD, RW_SCALE_FROGS };
			for (int i = 0; i < rwFiles.length; i++) {
				String rwFile = rwFiles[i];
				double rwScale = rwScales[i];

				Random seedGenerator = new Random(rwFile.hashCode());

				for (int j = 0; j < REPEATS; j++) {
					long seed = seedGenerator.nextLong();

					Geophylogeny geophylo = GeophylogenyIO.readGeophylogenyFromJSON(FILE_INPUT_PATH + rwFile);
					geophylo.scale(rwScale);
					geophylo.setLeaderType(LEADER_TYPE);
					String name = rwFile.replace(".json", "") + "-r" + j + "-" +
							LEADER_TYPE.toString().toLowerCase();
					size.append(geophylo.getSites().length).append(", ");

					runExperimentOnInstance(geophylo, name);
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

	private static void runExperimentOnInstance(Geophylogeny geophylo, String name) {
		GeophylogenyOrderer optimizer = new GreedyGeophylogenyOrderOptimizer(geophylo);

		// 1. Optimizer Only (Greedy Hill Climbing)
		// Hill climbing: 只看當下周圍的一小步範圍，如果某個方向會更好 (如減少 crossings)，就往那邊走一步
		optimizer.orderLeaves();
		optimizerOnly.append(geophylo.computeNumberOfCrossings()).append(", ");
		// saveDrawing(geophylo, name + "-optimizerOnly");

		// 2. TopDown
		GeophylogenyOrderer ordered = new TopDownGeophylogenyOrderer(geophylo);
		ordered.orderLeaves();
		topDown.append(geophylo.computeNumberOfCrossings()).append(", ");
		// saveDrawing(geophylo, name + "-topDown");

		optimizer.orderLeaves();
		topDownP.append(geophylo.computeNumberOfCrossings()).append(", ");
		// saveDrawing(geophylo, name + "-topDownPlus");

		// 3. BottomUp (DP Crossings)
		ordered = new DPGeophylogenyOrderer(geophylo, DPStrategy.Crossings);
		ordered.orderLeaves();
		bottomUp.append(geophylo.computeNumberOfCrossings()).append(", ");
		// saveDrawing(geophylo, name + "-bottomUp");

		optimizer.orderLeaves();
		bottomUpP.append(geophylo.computeNumberOfCrossings()).append(", ");
		// saveDrawing(geophylo, name + "-bottomUpPlus");

		// 4. Quality Measure: Euclidean (also DP)
		ordered = new DPGeophylogenyOrderer(geophylo, DPStrategy.EuclideanDistance);
		ordered.orderLeaves();
		euclidean.append(geophylo.computeNumberOfCrossings()).append(", ");
		// saveDrawing(geophylo, name + "-euclidean");

		optimizer.orderLeaves();
		euclideanP.append(geophylo.computeNumberOfCrossings()).append(", ");
		// saveDrawing(geophylo, name + "-euclideanPlus");

		// 5. Quality Measure: Horizontal (XOffset) (also DP)
		ordered = new DPGeophylogenyOrderer(geophylo, DPStrategy.HorizontalDistance);
		ordered.orderLeaves();
		horizontal.append(geophylo.computeNumberOfCrossings()).append(", ");
		// saveDrawing(geophylo, name + "-horizontal");

		optimizer.orderLeaves();
		horizontalP.append(geophylo.computeNumberOfCrossings()).append(", ");
		// saveDrawing(geophylo, name + "-horizontalPlus");

		// 6. Quality Measure: Hop (IndexOffset) (also DP)
		ordered = new DPGeophylogenyOrderer(geophylo, DPStrategy.Hops);
		ordered.orderLeaves();
		hop.append(geophylo.computeNumberOfCrossings()).append(", ");
		// saveDrawing(geophylo, name + "-hop");

		optimizer.orderLeaves();
		hopP.append(geophylo.computeNumberOfCrossings()).append(", ");
		saveDrawing(geophylo, name + "-hopPlus");
	}

	private static void saveDrawing(Geophylogeny geophylo, String filename) {
		geophylo.computeXCoordinates();
		GeophylogenyDrawer drawer = new GeophylogenyDrawer(geophylo, FILE_PATH + filename + "-" + getFormattedTimeNow() + ".svg");
		drawer.drawGeophylogeny();
		// System.out.println("Drawing saved to: " + FILE_PATH + filename + ".svg");
	}

	private static String getFormattedTimeNow() {
		LocalDateTime now = LocalDateTime.now();

		DateTimeFormatter formatter =
				DateTimeFormatter.ofPattern("MMdd'T'HHmm");

        return now.format(formatter);
	}
}
