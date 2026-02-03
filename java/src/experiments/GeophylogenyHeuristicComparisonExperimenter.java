package experiments;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import algorithms.DPGeophylogenyOrderer;
import algorithms.GeophylogenyOrderer;
import algorithms.GreedyGeophylogenyOrderOptimizer;
import algorithms.TopDownGeophylogenyOrderer;
import algorithms.DPGeophylogenyOrderer.DPStrategy;
import io.GeophylogenyIO;
import model.Geophylogeny;
import model.Leader.GeophylogenyLeaderType;
import model.Site;
import model.Tree;
import model.Vertex;

/**
 * Reads the ILP-optimal crossings from ../output/crossings.csv and computes
 * crossings for all heuristics on the generated instances in ../data/generated.
 * Outputs a new CSV with one column per heuristic.
 */
public class GeophylogenyHeuristicComparisonExperimenter {

	private static final Path INPUT_CROSSINGS = Paths.get("..", "output", "crossings.csv");
	private static final Path GENERATED_DIR = Paths.get("..", "data", "generated");
	private static final Path OUTPUT_CROSSINGS = Paths.get("..", "output", "crossings_with_heuristics.csv");

	private static final GeophylogenyLeaderType LEADER_TYPE = GeophylogenyLeaderType.S;

	private static final List<String> HEADER = List.of(
			"filename",
			"optimal",
			"optimizerOnly",
			"topDown",
			"bottomUp",
			"euclidean",
			"horizontal",
			"hop",
			"topDownPlus",
			"bottomUpPlus",
			"euclideanPlus",
			"horizontalPlus",
			"hopPlus");

	public static void main(String[] args) throws IOException {
		LinkedHashMap<String, Integer> optimalByFile = readOptimalCrossings(INPUT_CROSSINGS);
		Map<String, Path> generatedFiles = indexGeneratedFiles(GENERATED_DIR);

		StringBuilder output = new StringBuilder(String.join(",", HEADER));
		output.append(System.lineSeparator());

		for (Map.Entry<String, Integer> entry : optimalByFile.entrySet()) {
			String filename = entry.getKey();
			int optimal = entry.getValue();

			Path instancePath = generatedFiles.get(filename);
			if (instancePath == null) {
				System.err.println("Missing generated instance for: " + filename);
				output.append(filename).append(",").append(optimal);
				for (int i = 0; i < HEADER.size() - 2; i++) {
					output.append(",");
				}
				output.append(System.lineSeparator());
				continue;
			}

			Geophylogeny geophylogeny = GeophylogenyIO.readGeophylogenyFromJSON(instancePath.toString());
			geophylogeny.setLeaderType(LEADER_TYPE);

			HeuristicResults results = computeHeuristics(geophylogeny);
			output.append(filename).append(",").append(optimal).append(",")
					.append(results.optimizerOnly).append(",")
					.append(results.topDown).append(",")
					.append(results.bottomUp).append(",")
					.append(results.euclidean).append(",")
					.append(results.horizontal).append(",")
					.append(results.hop).append(",")
					.append(results.topDownPlus).append(",")
					.append(results.bottomUpPlus).append(",")
					.append(results.euclideanPlus).append(",")
					.append(results.horizontalPlus).append(",")
					.append(results.hopPlus)
					.append(System.lineSeparator());
		}

		Files.createDirectories(OUTPUT_CROSSINGS.getParent());
		Files.writeString(OUTPUT_CROSSINGS, output.toString(),
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

		System.out.println("Wrote: " + OUTPUT_CROSSINGS.toAbsolutePath());
	}

	private static LinkedHashMap<String, Integer> readOptimalCrossings(Path path) throws IOException {
		if (!Files.exists(path)) {
			throw new IOException("Crossings CSV not found: " + path.toAbsolutePath());
		}

		LinkedHashMap<String, Integer> map = new LinkedHashMap<>();
		for (String line : Files.readAllLines(path)) {
			String trimmed = line.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			String[] parts = trimmed.split(",");
			if (parts.length < 2) {
				System.err.println("Skipping malformed line: " + line);
				continue;
			}

			String filename = parts[0].trim();
			if (filename.equalsIgnoreCase("filename")) {
				continue;
			}
			String crossingsText = parts[1].trim();
			try {
				double crossingsValue = Double.parseDouble(crossingsText);
				int crossings = (int) Math.round(crossingsValue);
				if (Math.abs(crossingsValue - crossings) > 1e-5) {
					System.err.println("Non-integer crossings value, rounding: " + line);
				}
				if (map.containsKey(filename)) {
					System.err.println("Duplicate entry in crossings CSV: " + filename);
					continue;
				}
				map.put(filename, crossings);
			} catch (NumberFormatException e) {
				System.err.println("Skipping line with non-numeric crossings: " + line);
			}
		}

		return map;
	}

	private static Map<String, Path> indexGeneratedFiles(Path dir) throws IOException {
		if (!Files.exists(dir)) {
			throw new IOException("Generated instances folder not found: " + dir.toAbsolutePath());
		}

		Map<String, Path> map = new HashMap<>();
		try (Stream<Path> paths = Files.walk(dir)) {
			paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".json"))
					.forEach(path -> {
						String name = path.getFileName().toString();
						if (map.containsKey(name)) {
							System.err.println("Duplicate generated filename: " + name
									+ " -> " + map.get(name) + " and " + path);
						} else {
							map.put(name, path);
						}
					});
		}
		return map;
	}

	private static HeuristicResults computeHeuristics(Geophylogeny original) {
		int optimizerOnly = runOptimizerOnly(cloneForExperiment(original));

		HeuristicPair topDown = runHeuristicWithPlus(original,
				geophylogeny -> new TopDownGeophylogenyOrderer(geophylogeny));
		HeuristicPair bottomUp = runHeuristicWithPlus(original,
				geophylogeny -> new DPGeophylogenyOrderer(geophylogeny, DPStrategy.Crossings));
		HeuristicPair euclidean = runHeuristicWithPlus(original,
				geophylogeny -> new DPGeophylogenyOrderer(geophylogeny, DPStrategy.EuclideanDistance));
		HeuristicPair horizontal = runHeuristicWithPlus(original,
				geophylogeny -> new DPGeophylogenyOrderer(geophylogeny, DPStrategy.HorizontalDistance));
		HeuristicPair hop = runHeuristicWithPlus(original,
				geophylogeny -> new DPGeophylogenyOrderer(geophylogeny, DPStrategy.Hops));

		return new HeuristicResults(
				optimizerOnly,
				topDown.base,
				bottomUp.base,
				euclidean.base,
				horizontal.base,
				hop.base,
				topDown.plus,
				bottomUp.plus,
				euclidean.plus,
				horizontal.plus,
				hop.plus);
	}

	private static int runOptimizerOnly(Geophylogeny geophylogeny) {
		GeophylogenyOrderer optimizer = new GreedyGeophylogenyOrderOptimizer(geophylogeny);
		optimizer.orderLeaves();
		return geophylogeny.computeNumberOfCrossings();
	}

	private static HeuristicPair runHeuristicWithPlus(
			Geophylogeny original,
			Function<Geophylogeny, GeophylogenyOrderer> ordererFactory) {
		Geophylogeny geophylogeny = cloneForExperiment(original);
		GeophylogenyOrderer orderer = ordererFactory.apply(geophylogeny);
		orderer.orderLeaves();
		int base = geophylogeny.computeNumberOfCrossings();

		GeophylogenyOrderer optimizer = new GreedyGeophylogenyOrderOptimizer(geophylogeny);
		optimizer.orderLeaves();
		int plus = geophylogeny.computeNumberOfCrossings();

		return new HeuristicPair(base, plus);
	}

	private static Geophylogeny cloneForExperiment(Geophylogeny original) {
		Geophylogeny copy = cloneGeophylogeny(original);
		copy.computeXCoordinates();
		return copy;
	}

	private static Geophylogeny cloneGeophylogeny(Geophylogeny original) {
		Tree originalTree = original.getTree();
		Vertex rootCopy = copyVertex(originalTree.getRoot());
		Tree treeCopy = new Tree(rootCopy, originalTree.getNumberOfLeaves(),
				originalTree.getStateNumber());
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

		Geophylogeny copy = new Geophylogeny(treeCopy, sitesCopy, original.getMapWidth(),
				original.getMapHeight(), original.getName(), original.getLeaderType());
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

	private static final class HeuristicPair {
		private final int base;
		private final int plus;

		private HeuristicPair(int base, int plus) {
			this.base = base;
			this.plus = plus;
		}
	}

	private static final class HeuristicResults {
		private final int optimizerOnly;
		private final int topDown;
		private final int bottomUp;
		private final int euclidean;
		private final int horizontal;
		private final int hop;
		private final int topDownPlus;
		private final int bottomUpPlus;
		private final int euclideanPlus;
		private final int horizontalPlus;
		private final int hopPlus;

		private HeuristicResults(
				int optimizerOnly,
				int topDown,
				int bottomUp,
				int euclidean,
				int horizontal,
				int hop,
				int topDownPlus,
				int bottomUpPlus,
				int euclideanPlus,
				int horizontalPlus,
				int hopPlus) {
			this.optimizerOnly = optimizerOnly;
			this.topDown = topDown;
			this.bottomUp = bottomUp;
			this.euclidean = euclidean;
			this.horizontal = horizontal;
			this.hop = hop;
			this.topDownPlus = topDownPlus;
			this.bottomUpPlus = bottomUpPlus;
			this.euclideanPlus = euclideanPlus;
			this.horizontalPlus = horizontalPlus;
			this.hopPlus = hopPlus;
		}
	}
}
