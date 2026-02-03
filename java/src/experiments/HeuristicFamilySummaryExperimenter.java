package experiments;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Reads crossings_with_heuristics.csv and outputs CSVs per family
 * (Uniform, Coastline, Clustered) with:
 *  - approximation ratio (heuristic / optimal), as geometric mean percent
 *  - percent of optimal solutions
 * Columns are n values and rows are heuristics.
 */
public class HeuristicFamilySummaryExperimenter {

	private static final Path INPUT = Paths.get("..", "output", "crossings_with_heuristics.csv");
	private static final Path OUTPUT_DIR = Paths.get("..", "output");

	private static final String[] HEURISTICS = new String[] {
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
			"hopPlus"
	};

	private static final String FAMILY_UNIFORM = "uniform";
	private static final String FAMILY_COAST = "coast";
	private static final String FAMILY_CLUSTER = "cluster";

	public static void main(String[] args) throws IOException {
		if (!Files.exists(INPUT)) {
			throw new IOException("Input not found: " + INPUT.toAbsolutePath());
		}

		List<String> lines = Files.readAllLines(INPUT);
		if (lines.isEmpty()) {
			throw new IOException("Input is empty: " + INPUT.toAbsolutePath());
		}

		Map<String, Integer> headerIndex = parseHeader(lines.get(0));

		// family -> n -> heuristic -> stats
		Map<String, Map<Integer, Map<String, Stats>>> stats = new HashMap<>();
		for (String family : new String[] { FAMILY_UNIFORM, FAMILY_COAST, FAMILY_CLUSTER }) {
			stats.put(family, new TreeMap<>());
		}

		int skippedOptimalZero = 0;
		int skippedMissingValue = 0;
		int skippedHeuristicZero = 0;
		for (int i = 1; i < lines.size(); i++) {
			String line = lines.get(i).trim();
			if (line.isEmpty()) {
				continue;
			}
			String[] parts = line.split(",", -1);

			String filename = get(parts, headerIndex, "filename");
			String optimalText = get(parts, headerIndex, "optimal");
			if (filename == null || optimalText == null || optimalText.isEmpty()) {
				continue;
			}

			ParsedName parsed = parseFilename(filename);
			if (parsed == null || !stats.containsKey(parsed.family)) {
				continue;
			}

			double optimalValue;
			try {
				optimalValue = Double.parseDouble(optimalText.trim());
			} catch (NumberFormatException e) {
				continue;
			}

			if (optimalValue == 0.0) {
				skippedOptimalZero++;
			}

			for (String heuristic : HEURISTICS) {
				String valueText = get(parts, headerIndex, heuristic);
				if (valueText == null || valueText.trim().isEmpty()) {
					skippedMissingValue++;
					continue;
				}
				double value;
				try {
					value = Double.parseDouble(valueText.trim());
				} catch (NumberFormatException e) {
					skippedMissingValue++;
					continue;
				}

				if (value == 0.0) {
					skippedHeuristicZero++;
				}

				stats
						.get(parsed.family)
						.computeIfAbsent(parsed.n, k -> new HashMap<>())
						.computeIfAbsent(heuristic, k -> new Stats())
						.add(value, optimalValue);
			}
		}

		Files.createDirectories(OUTPUT_DIR);
		writeFamilyApproxCsv(stats.get(FAMILY_UNIFORM), "uniform_approx_ratio.csv");
		writeFamilyApproxCsv(stats.get(FAMILY_COAST), "coastline_approx_ratio.csv");
		writeFamilyApproxCsv(stats.get(FAMILY_CLUSTER), "clustered_approx_ratio.csv");

		writeFamilyOptimalCsv(stats.get(FAMILY_UNIFORM), "uniform_optimal_percent.csv");
		writeFamilyOptimalCsv(stats.get(FAMILY_COAST), "coastline_optimal_percent.csv");
		writeFamilyOptimalCsv(stats.get(FAMILY_CLUSTER), "clustered_optimal_percent.csv");

		if (skippedOptimalZero > 0) {
			System.out.println("Optimal=0 rows (excluded from ratio): " + skippedOptimalZero);
		}
		if (skippedHeuristicZero > 0) {
			System.out.println("Heuristic=0 values (excluded from ratio): " + skippedHeuristicZero);
		}
		if (skippedMissingValue > 0) {
			System.out.println("Skipped missing/non-numeric heuristic values: " + skippedMissingValue);
		}
	}

	private static Map<String, Integer> parseHeader(String headerLine) {
		String[] header = headerLine.split(",", -1);
		Map<String, Integer> index = new HashMap<>();
		for (int i = 0; i < header.length; i++) {
			index.put(header[i].trim(), i);
		}
		return index;
	}

	private static String get(String[] parts, Map<String, Integer> index, String key) {
		Integer idx = index.get(key);
		if (idx == null || idx < 0 || idx >= parts.length) {
			return null;
		}
		return parts[idx];
	}

	private static void writeFamilyApproxCsv(Map<Integer, Map<String, Stats>> familyStats, String fileName)
			throws IOException {
		TreeSet<Integer> nValues = new TreeSet<>(familyStats.keySet());
		List<String> lines = new ArrayList<>();

		StringBuilder header = new StringBuilder("heuristic");
		for (Integer n : nValues) {
			header.append(",n").append(n);
		}
		lines.add(header.toString());

		for (String heuristic : HEURISTICS) {
			StringBuilder row = new StringBuilder(heuristic);
			for (Integer n : nValues) {
				Stats stats = familyStats.get(n).get(heuristic);
				if (stats == null || !stats.hasRatio()) {
					row.append(",");
				} else {
					row.append(",").append(format(stats.geometricMeanPercent()));
				}
			}
			lines.add(row.toString());
		}

		Path output = OUTPUT_DIR.resolve(fileName);
		Files.write(output, lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		System.out.println("Wrote: " + output.toAbsolutePath());
	}

	private static void writeFamilyOptimalCsv(Map<Integer, Map<String, Stats>> familyStats, String fileName)
			throws IOException {
		TreeSet<Integer> nValues = new TreeSet<>(familyStats.keySet());
		List<String> lines = new ArrayList<>();

		StringBuilder header = new StringBuilder("heuristic");
		for (Integer n : nValues) {
			header.append(",n").append(n);
		}
		lines.add(header.toString());

		for (String heuristic : HEURISTICS) {
			StringBuilder row = new StringBuilder(heuristic);
			for (Integer n : nValues) {
				Stats stats = familyStats.get(n).get(heuristic);
				if (stats == null || !stats.hasAny()) {
					row.append(",");
				} else {
					row.append(",").append(format(stats.percentOptimal()));
				}
			}
			lines.add(row.toString());
		}

		Path output = OUTPUT_DIR.resolve(fileName);
		Files.write(output, lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		System.out.println("Wrote: " + output.toAbsolutePath());
	}

	private static ParsedName parseFilename(String filename) {
		int nIndex = filename.indexOf("-n");
		if (nIndex < 0) {
			return null;
		}
		String family = filename.substring(0, nIndex);
		int start = nIndex + 2;
		int end = start;
		while (end < filename.length() && Character.isDigit(filename.charAt(end))) {
			end++;
		}
		if (end == start) {
			return null;
		}
		int n;
		try {
			n = Integer.parseInt(filename.substring(start, end));
		} catch (NumberFormatException e) {
			return null;
		}
		return new ParsedName(family, n);
	}

	private static String format(double value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}

	private static final class ParsedName {
		private final String family;
		private final int n;

		private ParsedName(String family, int n) {
			this.family = family;
			this.n = n;
		}
	}

	private static final class Stats {
		private static final double EPS = 1e-9;
		private double logSum = 0.0;
		private int ratioCount = 0;
		private int total = 0;
		private int optimal = 0;

		private void add(double value, double optimalValue) {
			total++;
			double diff = Math.abs(value - optimalValue);
			double scale = Math.max(1.0, Math.abs(optimalValue));
			if (diff <= EPS * scale) {
				optimal++;
			}
			if (optimalValue > 0.0 && value > 0.0) {
				double ratio = value / optimalValue;
				logSum += Math.log(ratio);
				ratioCount++;
			}
		}

		private boolean hasRatio() {
			return ratioCount > 0;
		}

		private boolean hasAny() {
			return total > 0;
		}

		private double geometricMeanPercent() {
			return ratioCount == 0 ? 0.0 : Math.exp(logSum / ratioCount) * 100.0;
		}

		private double percentOptimal() {
			return total == 0 ? 0.0 : (optimal * 100.0) / total;
		}
	}
}
