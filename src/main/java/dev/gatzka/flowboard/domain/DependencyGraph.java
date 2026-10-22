package dev.gatzka.flowboard.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The "blocked by" links of a board laid out as a layered graph: blockers to the left, the cards they hold up to the
 * right. Only cards that take part in a dependency appear. The graph is acyclic because {@code DependencyService}
 * refuses links that would close a cycle.
 */
public record DependencyGraph(List<Node> nodes, List<Edge> edges, List<Long> criticalPath) {

    public enum Status { DONE, BLOCKED, OPEN }

    /** A card in the graph. {@code layer} is its column, {@code row} its position within the column. */
    public record Node(long id, String title, String lane, Status status, int layer, int row) {

        public boolean isResolved() {
            return status == Status.DONE;
        }
    }

    /**
     * {@code blockerId} holds up {@code cardId}; resolved once the blocker is done or archived. {@code waypoints} are
     * the rows the link passes through in the columns between the two cards, so that it is routed around other cards.
     */
    public record Edge(long blockerId, long cardId, boolean resolved, List<Integer> waypoints) {

        public Edge {
            waypoints = List.copyOf(waypoints);
        }

        public Edge(long blockerId, long cardId, boolean resolved) {
            this(blockerId, cardId, resolved, List.of());
        }
    }

    public DependencyGraph {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
        criticalPath = List.copyOf(criticalPath);
    }

    public static DependencyGraph of(BoardSnapshot board) {
        Map<Long, Node> seeds = new LinkedHashMap<>();
        List<Edge> edges = new ArrayList<>();
        Map<Long, CardSnapshot> onBoard = new HashMap<>();
        Map<Long, LaneSnapshot> laneOf = new HashMap<>();
        for (LaneSnapshot lane : board.lanes()) {
            for (CardSnapshot card : lane.cards()) {
                onBoard.put(card.id(), card);
                laneOf.put(card.id(), lane);
            }
        }
        for (LaneSnapshot lane : board.lanes()) {
            for (CardSnapshot card : lane.cards()) {
                if (card.blockers().isEmpty()) {
                    continue;
                }
                seeds.putIfAbsent(card.id(), new Node(card.id(), card.title(), lane.name(), statusOf(card, lane), 0, 0));
                for (BlockerSnapshot blocker : card.blockers()) {
                    seeds.computeIfAbsent(blocker.id(), id -> {
                        CardSnapshot blockerCard = onBoard.get(id);
                        return blockerCard == null
                                ? new Node(id, blocker.title(), "Archived", Status.DONE, 0, 0)
                                : new Node(id, blockerCard.title(), laneOf.get(id).name(), statusOf(blockerCard, laneOf.get(id)), 0, 0);
                    });
                    edges.add(new Edge(blocker.id(), card.id(), blocker.resolved()));
                }
            }
        }
        if (seeds.isEmpty()) {
            return new DependencyGraph(List.of(), List.of(), List.of());
        }
        Map<Long, List<Long>> blockersOf = new HashMap<>();
        Map<Long, List<Long>> blockedBy = new HashMap<>();
        for (Edge edge : edges) {
            blockersOf.computeIfAbsent(edge.cardId(), id -> new ArrayList<>()).add(edge.blockerId());
            blockedBy.computeIfAbsent(edge.blockerId(), id -> new ArrayList<>()).add(edge.cardId());
        }
        Map<Long, Integer> layers = new HashMap<>();
        for (Long id : seeds.keySet()) {
            layerOf(id, blockersOf, layers, new HashSet<>());
        }
        Placement placement = placeRows(seeds, layers, edges);
        return new DependencyGraph(placement.nodes(), placement.edges(), longestOpenChain(seeds, blockedBy));
    }

    /** Length of the longest chain of blockers ending in the card: sources are in layer 0. */
    private static int layerOf(long id, Map<Long, List<Long>> blockersOf, Map<Long, Integer> layers, Set<Long> visiting) {
        Integer known = layers.get(id);
        if (known != null) {
            return known;
        }
        if (!visiting.add(id)) {
            return 0; // defensive: a cycle cannot be laid out, treat the back edge as absent
        }
        int layer = 0;
        for (Long blocker : blockersOf.getOrDefault(id, List.of())) {
            layer = Math.max(layer, layerOf(blocker, blockersOf, layers, visiting) + 1);
        }
        visiting.remove(id);
        layers.put(id, layer);
        return layer;
    }

    private record Placement(List<Node> nodes, List<Edge> edges) {
    }

    /** One slot in a column: a card, or the point where a link spanning several columns passes through. */
    private record Slot(Node node, Edge edge) {

        boolean isCard() {
            return node != null;
        }

        String title() {
            return node != null ? node.title() : "";
        }
    }

    /**
     * Orders each column so that cards sit near the blockers they depend on (barycenter heuristic). Links that skip
     * columns occupy a slot in each column they cross, which keeps them from being drawn through unrelated cards.
     */
    private static Placement placeRows(Map<Long, Node> seeds, Map<Long, Integer> layers, List<Edge> edges) {
        int columns = layers.values().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
        Map<Long, Integer> cardRows = new HashMap<>();
        Map<Edge, List<Integer>> waypoints = new LinkedHashMap<>();
        edges.forEach(edge -> waypoints.put(edge, new ArrayList<>()));
        Map<Long, List<Edge>> incoming = new HashMap<>();
        edges.forEach(edge -> incoming.computeIfAbsent(edge.cardId(), id -> new ArrayList<>()).add(edge));
        List<Node> placed = new ArrayList<>();
        for (int column = 0; column < columns; column++) {
            int layer = column;
            List<Slot> slots = new ArrayList<>();
            seeds.values().stream().filter(node -> layers.get(node.id()) == layer).forEach(node -> slots.add(new Slot(node, null)));
            for (Edge edge : edges) {
                if (layers.get(edge.blockerId()) < layer && layer < layers.get(edge.cardId())) {
                    slots.add(new Slot(null, edge));
                }
            }
            Map<Slot, Double> centers = new HashMap<>();
            for (Slot slot : slots) {
                List<Edge> feeding = slot.isCard() ? incoming.getOrDefault(slot.node().id(), List.of()) : List.of(slot.edge());
                centers.put(slot, feeding.stream()
                        .mapToInt(edge -> rowBefore(edge, layer, layers, cardRows, waypoints))
                        .average().orElse(Double.MAX_VALUE));
            }
            slots.sort(Comparator.comparingDouble((Slot slot) -> centers.get(slot))
                    .thenComparing(Slot::isCard, Comparator.reverseOrder())
                    .thenComparing(Slot::title, String.CASE_INSENSITIVE_ORDER));
            for (int row = 0; row < slots.size(); row++) {
                Slot slot = slots.get(row);
                if (slot.isCard()) {
                    Node seed = slot.node();
                    cardRows.put(seed.id(), row);
                    placed.add(new Node(seed.id(), seed.title(), seed.lane(), seed.status(), layer, row));
                } else {
                    waypoints.get(slot.edge()).add(row);
                }
            }
        }
        List<Edge> routed = edges.stream()
                .map(edge -> new Edge(edge.blockerId(), edge.cardId(), edge.resolved(), waypoints.get(edge)))
                .toList();
        return new Placement(placed, routed);
    }

    /** The row the link occupies in the column before {@code layer}: its blocker, or the slot it passed through. */
    private static int rowBefore(Edge edge, int layer, Map<Long, Integer> layers, Map<Long, Integer> cardRows,
            Map<Edge, List<Integer>> waypoints) {
        int blockerLayer = layers.get(edge.blockerId());
        if (blockerLayer == layer - 1) {
            return cardRows.get(edge.blockerId());
        }
        return waypoints.get(edge).get(layer - 1 - blockerLayer - 1);
    }

    /** The longest chain of cards that are not done yet, following "blocks" links; empty unless at least two cards long. */
    private static List<Long> longestOpenChain(Map<Long, Node> seeds, Map<Long, List<Long>> blockedBy) {
        Map<Long, List<Long>> best = new HashMap<>();
        List<Long> longest = List.of();
        for (Long id : seeds.keySet()) {
            List<Long> chain = openChainFrom(id, seeds, blockedBy, best, new HashSet<>());
            if (chain.size() > longest.size()) {
                longest = chain;
            }
        }
        return longest.size() >= 2 ? longest : List.of();
    }

    private static List<Long> openChainFrom(long id, Map<Long, Node> seeds, Map<Long, List<Long>> blockedBy,
            Map<Long, List<Long>> best, Set<Long> visiting) {
        if (seeds.get(id).isResolved()) {
            return List.of();
        }
        List<Long> known = best.get(id);
        if (known != null) {
            return known;
        }
        if (!visiting.add(id)) {
            return List.of();
        }
        List<Long> tail = List.of();
        for (Long next : blockedBy.getOrDefault(id, List.of())) {
            List<Long> candidate = openChainFrom(next, seeds, blockedBy, best, visiting);
            if (candidate.size() > tail.size()) {
                tail = candidate;
            }
        }
        visiting.remove(id);
        List<Long> chain = new ArrayList<>(tail.size() + 1);
        chain.add(id);
        chain.addAll(tail);
        best.put(id, chain);
        return chain;
    }

    private static Status statusOf(CardSnapshot card, LaneSnapshot lane) {
        if (lane.done()) {
            return Status.DONE;
        }
        return card.isBlocked() ? Status.BLOCKED : Status.OPEN;
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    public Optional<Node> node(long id) {
        return nodes.stream().filter(node -> node.id() == id).findFirst();
    }

    public int columns() {
        return nodes.stream().mapToInt(Node::layer).max().orElse(-1) + 1;
    }

    public int rows() {
        return nodes.stream().mapToInt(Node::row).max().orElse(-1) + 1;
    }

    public long blockedCount() {
        return nodes.stream().filter(node -> node.status() == Status.BLOCKED).count();
    }

    public long openLinkCount() {
        return edges.stream().filter(edge -> !edge.resolved()).count();
    }

    /** The titles along the critical path, in order. */
    public List<String> criticalTitles() {
        return criticalPath.stream().map(id -> node(id).map(Node::title).orElse("?")).toList();
    }

    /** The unresolved card that, directly or through others, holds up the most cards. */
    public Optional<Node> keyBlocker() {
        Map<Long, Set<Long>> reach = new HashMap<>();
        Map<Long, List<Long>> blockedBy = new HashMap<>();
        for (Edge edge : edges) {
            blockedBy.computeIfAbsent(edge.blockerId(), id -> new ArrayList<>()).add(edge.cardId());
        }
        return nodes.stream()
                .filter(node -> !node.isResolved())
                .map(node -> Map.entry(node, reachable(node.id(), blockedBy, reach, new HashSet<>()).size()))
                .filter(entry -> entry.getValue() > 0)
                .max(Comparator.comparingInt((Map.Entry<Node, Integer> entry) -> entry.getValue())
                        .thenComparing(entry -> entry.getKey().title(), Comparator.reverseOrder()))
                .map(Map.Entry::getKey);
    }

    /** How many cards the given one holds up, directly or through others. */
    public int holdsUp(long id) {
        Map<Long, List<Long>> blockedBy = new HashMap<>();
        for (Edge edge : edges) {
            blockedBy.computeIfAbsent(edge.blockerId(), blocker -> new ArrayList<>()).add(edge.cardId());
        }
        return reachable(id, blockedBy, new HashMap<>(), new HashSet<>()).size();
    }

    private static Set<Long> reachable(long id, Map<Long, List<Long>> blockedBy, Map<Long, Set<Long>> memo, Set<Long> visiting) {
        Set<Long> known = memo.get(id);
        if (known != null) {
            return known;
        }
        Set<Long> result = new HashSet<>();
        if (visiting.add(id)) {
            for (Long next : blockedBy.getOrDefault(id, List.of())) {
                result.add(next);
                result.addAll(reachable(next, blockedBy, memo, visiting));
            }
            visiting.remove(id);
        }
        memo.put(id, result);
        return result;
    }
}
