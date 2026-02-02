import io.GeophylogenyIO;
import model.Geophylogeny;
import model.Site;
import model.Tree;
import model.Vertex;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GeoJsonToGeophylogenyConverter {

    // OMG no one told me that the code with the same function already exists.
    // it is in the python folder, it parses the tree file and the GeoJSON file, and convert it into a JSON file that this project requires

    private static final String BASE_PATH = "D:\\Alex\\TUM\\Seminar\\geophylo\\";

    // CHANGE NAME HERE
    private static final String DND_FILE_NAME = "data/realWorld/Indo-European.dnd";
    private static final String GEOJSON_FILE_NAME = "data/realWorld/Balto-Slavic.geojson";
    private static final String OUTPUT_JSON_FILE_NAME = "data/realWorld/Balto-Slavic.json";
    private static final String TREE_NAME_IN_OUTPUT = "balto-slavic";

    private static final int MAP_WIDTH = 800;
    private static final int MAP_HEIGHT = 500;
    private static final Pattern TAXON_ID_PATTERN = Pattern.compile("\\[([^\\]]+)\\]");

    private record LonLat(double lon, double lat) {}

    private static final class Node {
        private final List<Node> children;
        private final String label;
        private final Double length;

        private Node(List<Node> children, String label, Double length) {
            this.children = children;
            this.label = label;
            this.length = length;
        }

        private boolean isLeaf() {
            return children.isEmpty();
        }
    }

    private record NexusData(String newick, Map<String, String> translate) {}

    public static void main(String[] args) throws Exception {
        File dndTreeFile = new File(BASE_PATH + DND_FILE_NAME);
        File geoJsonFile = new File(BASE_PATH + GEOJSON_FILE_NAME);
        String outJsonPath = BASE_PATH + OUTPUT_JSON_FILE_NAME;

        Map<String, LonLat> byId = readPointFeaturesById(geoJsonFile);
        Tree tree = readTreeFromDnd(dndTreeFile, TREE_NAME_IN_OUTPUT, byId);

        Bounds bounds = computeBounds(byId);
        Site[] sites = buildSites(tree, byId, bounds);

        Geophylogeny geophylogeny = new Geophylogeny(tree, sites, MAP_WIDTH, MAP_HEIGHT, TREE_NAME_IN_OUTPUT);
        GeophylogenyIO.writeGeophylogenyToJSON(geophylogeny, outJsonPath);

        System.out.println("Wrote geophylogeny JSON to: " + outJsonPath);
    }

    private static Tree readTreeFromNexus(File nexusTreeFile, String treeName) throws IOException {
        NexusData data = parseNexus(nexusTreeFile);
        Node root = new NewickParser(data.newick()).parse();

        List<Node> leaves = new ArrayList<>();
        collectLeaves(root, leaves);
        Map<Node, Integer> leafIds = assignLeafIds(leaves);

        int[] nextInternalId = new int[] { leaves.size() + 1 };
        Vertex rootVertex = buildVertexTree(root, leafIds, nextInternalId, data.translate());

        Tree tree = new Tree(rootVertex, leaves.size());
        tree.setName(treeName);
        return tree;
    }

    private static Tree readTreeFromDnd(File dndTreeFile, String treeName, Map<String, LonLat> byId)
            throws IOException {
        String newick = parseDnd(dndTreeFile);
        Node root = new NewickParser(newick).parse();
        root = pruneToGeoIds(root, new HashMap<>(), byId);
        if (root == null) {
            throw new IllegalStateException("All taxa were pruned; no GeoJSON ids matched the tree.");
        }

        List<Node> leaves = new ArrayList<>();
        collectLeaves(root, leaves);
        Map<Node, Integer> leafIds = assignLeafIds(leaves);

        int[] nextInternalId = new int[] { leaves.size() + 1 };
        Vertex rootVertex = buildVertexTree(root, leafIds, nextInternalId, new HashMap<>());

        Tree tree = new Tree(rootVertex, leaves.size());
        tree.setName(treeName);
        return tree;
    }

    private static void collectLeaves(Node node, List<Node> leaves) {
        if (node.isLeaf()) {
            leaves.add(node);
            return;
        }
        for (Node child : node.children) {
            collectLeaves(child, leaves);
        }
    }

    private static Map<Node, Integer> assignLeafIds(List<Node> leaves) {
        Map<Node, Integer> leafIds = new IdentityHashMap<>();
        boolean allNumeric = true;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (Node leaf : leaves) {
            String label = leaf.label == null ? "" : leaf.label.trim();
            if (!label.matches("\\d+")) {
                allNumeric = false;
                break;
            }
            int value = Integer.parseInt(label);
            min = Math.min(min, value);
            max = Math.max(max, value);
        }

        if (allNumeric && min == 1 && max == leaves.size()) {
            for (Node leaf : leaves) {
                leafIds.put(leaf, Integer.parseInt(leaf.label.trim()));
            }
            return leafIds;
        }

        int id = 1;
        for (Node leaf : leaves) {
            leafIds.put(leaf, id++);
        }
        return leafIds;
    }

    private static Vertex buildVertexTree(Node node, Map<Node, Integer> leafIds, int[] nextInternalId,
                                          Map<String, String> translate) {
        if (node.isLeaf()) {
            int id = leafIds.get(node);
            Vertex leaf = new Vertex(id);
            String resolvedLabel = resolveLabel(node.label, translate);
            leaf.setTaxonName(extractTaxonId(resolvedLabel));
            if (node.length != null) {
                leaf.setBranchLengthIncoming(node.length);
            }
            return leaf;
        }

        if (node.children.size() < 2) {
            if (node.children.isEmpty()) {
                throw new IllegalArgumentException("Newick node has fewer than two children.");
            }
            Vertex onlyChild = buildVertexTree(node.children.get(0), leafIds, nextInternalId, translate);
            if (node.length != null) {
                double existing = onlyChild.getBranchLengthIncoming();
                if (Double.isNaN(existing)) {
                    existing = 0.0;
                }
                onlyChild.setBranchLengthIncoming(existing + node.length);
            }
            return onlyChild;
        }

        Vertex current = buildVertexTree(node.children.get(0), leafIds, nextInternalId, translate);
        for (int i = 1; i < node.children.size(); i++) {
            Vertex right = buildVertexTree(node.children.get(i), leafIds, nextInternalId, translate);
            current = new Vertex(nextInternalId[0]++, current, right);
        }
        if (node.length != null) {
            current.setBranchLengthIncoming(node.length);
        }
        String resolvedLabel = resolveLabel(node.label, translate);
        String taxonId = extractTaxonId(resolvedLabel);
        if (taxonId != null && !taxonId.isBlank()) {
            current.setTaxonName(taxonId);
        }
        return current;
    }

    private static String resolveLabel(String rawLabel, Map<String, String> translate) {
        if (rawLabel == null) {
            return null;
        }
        String trimmed = rawLabel.trim();
        if (translate.containsKey(trimmed)) {
            return translate.get(trimmed);
        }
        return trimmed;
    }

    private static String extractTaxonId(String label) {
        if (label == null) {
            return null;
        }
        Matcher matcher = TAXON_ID_PATTERN.matcher(label);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return label.trim();
    }

    private static Map<String, LonLat> readPointFeaturesById(File geoJsonFile) throws Exception {
        try (InputStream in = new FileInputStream(geoJsonFile);
             JsonReader reader = Json.createReader(in)) {

            JsonObject root = reader.readObject();
            JsonArray features = root.getJsonArray("features");
            if (features == null) {
                throw new IllegalArgumentException("GeoJSON missing 'features' array.");
            }

            Map<String, LonLat> byId = new HashMap<>();
            for (JsonValue v : features) {
                if (!(v instanceof JsonObject feature)) {
                    continue;
                }

                String id = feature.getString("id", null);
                if (id == null) {
                    JsonObject props = feature.getJsonObject("properties");
                    if (props != null) {
                        JsonObject language = props.getJsonObject("language");
                        if (language != null) {
                            id = language.getString("id", null);
                        }
                    }
                }
                if (id == null || id.isBlank()) {
                    continue;
                }

                JsonObject geom = feature.getJsonObject("geometry");
                if (geom == null) {
                    continue;
                }

                String type = geom.getString("type", "");
                if (!"Point".equals(type)) {
                    continue;
                }

                JsonArray coords = geom.getJsonArray("coordinates");
                if (coords == null || coords.size() < 2) {
                    continue;
                }

                JsonNumber lonNumber = coords.getJsonNumber(0);
                JsonNumber latNumber = coords.getJsonNumber(1);
                if (lonNumber == null || latNumber == null) {
                    continue;
                }

                byId.put(id, new LonLat(lonNumber.doubleValue(), latNumber.doubleValue()));
            }

            return byId;
        }
    }

    private record Bounds(double minX, double maxX, double minY, double maxY) {}

    private static Bounds computeBounds(Map<String, LonLat> byId) {
        if (byId.isEmpty()) {
            throw new IllegalStateException("No Point features found in GeoJSON.");
        }

        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        for (LonLat p : byId.values()) {
            double x = lonToMercatorX(p.lon);
            double y = latToMercatorY(p.lat);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }

        if (maxX == minX || maxY == minY) {
            throw new IllegalStateException("GeoJSON points have zero extent (all same lon or lat).");
        }

        return new Bounds(minX, maxX, minY, maxY);
    }

    private static Site[] buildSites(Tree tree, Map<String, LonLat> byId, Bounds bounds) {
        Vertex[] leaves = tree.getLeavesInIndexOrder();
        Site[] sites = new Site[leaves.length];
        List<String> missing = new ArrayList<>();
        double padX = MAP_WIDTH * 0.10;
        double padY = MAP_HEIGHT * 0.10;
        double usableWidth = MAP_WIDTH - 2.0 * padX;
        double usableHeight = MAP_HEIGHT - 2.0 * padY;
        double xRange = bounds.maxX - bounds.minX;
        double yRange = bounds.maxY - bounds.minY;
        double scale = Math.min(usableWidth / xRange, usableHeight / yRange);
        double extraX = (usableWidth - xRange * scale) / 2.0;
        double extraY = (usableHeight - yRange * scale) / 2.0;

        for (int i = 0; i < leaves.length; i++) {
            Vertex leaf = leaves[i];
            String key = leaf.getTaxonName();
            if (key == null || key.isBlank()) {
                throw new IllegalStateException("Leaf " + leaf.getID() + " has no taxon label.");
            }

            LonLat lonLat = byId.get(key);
            if (lonLat == null) {
                missing.add(key);
                continue;
            }

            double mercatorX = lonToMercatorX(lonLat.lon);
            double mercatorY = latToMercatorY(lonLat.lat);
            double x = padX + extraX + (mercatorX - bounds.minX) * scale;
            double y = padY + extraY + (bounds.maxY - mercatorY) * scale;

            Site site = new Site(x, y);
            site.setLeaf(leaf);
            sites[i] = site;
        }

        if (!missing.isEmpty()) {
            throw new IllegalStateException("Missing GeoJSON ids for taxa: " + String.join(", ", missing));
        }

        return sites;
    }

    private static final double MAX_MERCATOR_LAT = 85.05112878;

    private static double lonToMercatorX(double lonDeg) {
        return Math.toRadians(lonDeg);
    }

    private static double latToMercatorY(double latDeg) {
        double clamped = Math.max(-MAX_MERCATOR_LAT, Math.min(MAX_MERCATOR_LAT, latDeg));
        double rad = Math.toRadians(clamped);
        return Math.log(Math.tan(Math.PI / 4.0 + rad / 2.0));
    }

    private static Node pruneToGeoIds(Node node, Map<String, String> translate, Map<String, LonLat> byId) {
        String resolvedLabel = resolveLabel(node.label, translate);
        String taxonId = extractTaxonId(resolvedLabel);
        boolean hasGeo = taxonId != null && byId.containsKey(taxonId);

        if (node.isLeaf()) {
            if (hasGeo) {
                return new Node(new ArrayList<>(), node.label, node.length);
            }
            return null;
        }

        List<Node> kept = new ArrayList<>();
        for (Node child : node.children) {
            Node prunedChild = pruneToGeoIds(child, translate, byId);
            if (prunedChild != null) {
                kept.add(prunedChild);
            }
        }

        if (hasGeo) {
            Node selfLeaf = new Node(new ArrayList<>(), node.label, null);
            if (kept.isEmpty()) {
                return selfLeaf;
            }
            kept.add(selfLeaf);
            return new Node(kept, null, node.length);
        }

        if (kept.isEmpty()) {
            return null;
        }
        if (kept.size() == 1) {
            Node only = kept.get(0);
            Double mergedLength = mergeLengths(node.length, only.length);
            return new Node(only.children, only.label, mergedLength);
        }

        return new Node(kept, node.label, node.length);
    }

    private static Double mergeLengths(Double parentLength, Double childLength) {
        if (parentLength == null) {
            return childLength;
        }
        if (childLength == null) {
            return parentLength;
        }
        return parentLength + childLength;
    }

    private static NexusData parseNexus(File nexusTreeFile) throws IOException {
        List<String> lines = Files.readAllLines(nexusTreeFile.toPath(), StandardCharsets.UTF_8);
        Map<String, String> translate = parseTranslate(lines);
        String newick = parseFirstTreeNewick(lines);
        return new NexusData(newick, translate);
    }

    private static String parseDnd(File dndTreeFile) throws IOException {
        String content = Files.readString(dndTreeFile.toPath(), StandardCharsets.UTF_8).trim();
        if (content.isBlank()) {
            throw new IllegalArgumentException("DND file is empty.");
        }
        return content;
    }

    private static Map<String, String> parseTranslate(List<String> lines) {
        StringBuilder block = new StringBuilder();
        boolean inTranslate = false;

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            if (!inTranslate) {
                if (line.equalsIgnoreCase("translate")) {
                    inTranslate = true;
                    continue;
                }
                if (line.toLowerCase().startsWith("translate")) {
                    inTranslate = true;
                    String rest = line.substring("translate".length()).trim();
                    if (!rest.isEmpty()) {
                        block.append(' ').append(rest);
                    }
                    if (line.contains(";")) {
                        break;
                    }
                    continue;
                }
            } else {
                block.append(' ').append(line);
                if (line.contains(";")) {
                    break;
                }
            }
        }

        if (block.length() == 0) {
            return new HashMap<>();
        }

        return parseTranslateEntries(block.toString());
    }

    private static Map<String, String> parseTranslateEntries(String block) {
        Map<String, String> map = new HashMap<>();
        int i = 0;
        while (i < block.length()) {
            i = skipSeparators(block, i);
            if (i >= block.length()) {
                break;
            }

            int start = i;
            while (i < block.length() && Character.isDigit(block.charAt(i))) {
                i++;
            }
            if (start == i) {
                i++;
                continue;
            }
            String key = block.substring(start, i);

            while (i < block.length() && Character.isWhitespace(block.charAt(i))) {
                i++;
            }

            String value;
            if (i < block.length() && block.charAt(i) == '\'') {
                i++;
                StringBuilder name = new StringBuilder();
                while (i < block.length()) {
                    char ch = block.charAt(i);
                    if (ch == '\'') {
                        if (i + 1 < block.length() && block.charAt(i + 1) == '\'') {
                            name.append('\'');
                            i += 2;
                            continue;
                        }
                        i++;
                        break;
                    }
                    name.append(ch);
                    i++;
                }
                value = name.toString();
            } else {
                int nameStart = i;
                while (i < block.length()) {
                    char ch = block.charAt(i);
                    if (ch == ',' || ch == ';') {
                        break;
                    }
                    i++;
                }
                value = block.substring(nameStart, i).trim();
            }

            if (!value.isEmpty()) {
                map.put(key, value);
            }
        }

        return map;
    }

    private static int skipSeparators(String block, int i) {
        while (i < block.length()) {
            char ch = block.charAt(i);
            if (Character.isWhitespace(ch) || ch == ',' || ch == ';') {
                i++;
                continue;
            }
            return i;
        }
        return i;
    }

    private static String parseFirstTreeNewick(List<String> lines) {
        boolean inTree = false;
        StringBuilder newick = new StringBuilder();

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            String lower = line.toLowerCase();
            if (!inTree) {
                if (lower.startsWith("tree") && line.contains("=")) {
                    inTree = true;
                    newick.append(line.substring(line.indexOf('=') + 1).trim());
                    if (line.contains(";")) {
                        break;
                    }
                }
            } else {
                newick.append(' ').append(line);
                if (line.contains(";")) {
                    break;
                }
            }
        }

        String newickStr = newick.toString();
        int semicolon = newickStr.indexOf(';');
        if (semicolon >= 0) {
            newickStr = newickStr.substring(0, semicolon);
        }

        if (newickStr.isBlank()) {
            throw new IllegalArgumentException("No Newick tree found in NEXUS file.");
        }
        return newickStr;
    }

    private static final class NewickParser {
        private final String input;
        private int pos;

        private NewickParser(String input) {
            this.input = input;
        }

        private Node parse() {
            Node root = parseSubtree();
            skipIgnorables();
            if (pos < input.length() && input.charAt(pos) == ';') {
                pos++;
            }
            return root;
        }

        private Node parseSubtree() {
            skipIgnorables();
            if (pos >= input.length()) {
                throw new IllegalArgumentException("Unexpected end of Newick string.");
            }

            if (input.charAt(pos) == '(') {
                pos++;
                List<Node> children = new ArrayList<>();
                children.add(parseSubtree());
                skipIgnorables();
                while (pos < input.length() && input.charAt(pos) == ',') {
                    pos++;
                    children.add(parseSubtree());
                    skipIgnorables();
                }
                expect(')');

                String label = parseOptionalLabel();
                Double length = parseOptionalLength();
                return new Node(children, label, length);
            }

            String label = parseLabel();
            Double length = parseOptionalLength();
            return new Node(new ArrayList<>(), label, length);
        }

        private String parseOptionalLabel() {
            skipIgnorables();
            if (pos >= input.length()) {
                return null;
            }
            char c = input.charAt(pos);
            if (c == ':' || c == ',' || c == ')' || c == ';') {
                return null;
            }
            return parseLabel();
        }

        private String parseLabel() {
            skipIgnorables();
            if (pos >= input.length()) {
                return "";
            }

            char c = input.charAt(pos);
            if (c == '\'') {
                pos++;
                StringBuilder label = new StringBuilder();
                while (pos < input.length()) {
                    char ch = input.charAt(pos);
                    if (ch == '\'') {
                        if (pos + 1 < input.length() && input.charAt(pos + 1) == '\'') {
                            label.append('\'');
                            pos += 2;
                            continue;
                        }
                        pos++;
                        break;
                    }
                    label.append(ch);
                    pos++;
                }
                return label.toString();
            }

            int start = pos;
            while (pos < input.length()) {
                char ch = input.charAt(pos);
                if (ch == ':' || ch == ',' || ch == ')' || ch == '(' || ch == ';' || ch == '['
                        || Character.isWhitespace(ch)) {
                    break;
                }
                pos++;
            }
            return input.substring(start, pos);
        }

        private Double parseOptionalLength() {
            skipIgnorables();
            if (pos < input.length() && input.charAt(pos) == ':') {
                pos++;
                skipIgnorables();
                int start = pos;
                while (pos < input.length()) {
                    char ch = input.charAt(pos);
                    if ((ch >= '0' && ch <= '9') || ch == '.' || ch == '-' || ch == '+'
                            || ch == 'E' || ch == 'e') {
                        pos++;
                    } else {
                        break;
                    }
                }
                String number = input.substring(start, pos);
                if (!number.isEmpty()) {
                    return Double.parseDouble(number);
                }
            }
            return null;
        }

        private void expect(char expected) {
            skipIgnorables();
            if (pos >= input.length() || input.charAt(pos) != expected) {
                throw new IllegalArgumentException("Expected '" + expected + "' at position " + pos);
            }
            pos++;
        }

        private void skipIgnorables() {
            boolean progressed = true;
            while (progressed) {
                progressed = false;
                while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
                    pos++;
                    progressed = true;
                }
                if (pos < input.length() && input.charAt(pos) == '[') {
                    pos++;
                    while (pos < input.length() && input.charAt(pos) != ']') {
                        pos++;
                    }
                    if (pos < input.length() && input.charAt(pos) == ']') {
                        pos++;
                    }
                    progressed = true;
                }
            }
        }
    }
}
