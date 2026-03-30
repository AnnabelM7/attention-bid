private final static String CATEGORY = "Music";

void main(String[] args) throws Exception {
    long budget = Long.parseLong(args[0]);
    long remaining = budget;

    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    System.out.println(CATEGORY);

    while (true) {
        String line = br.readLine();
        if (line == null) {
            break;
        }
        line = line.trim();
        if (line.isEmpty()) continue;

        if (line.startsWith("video.") || line.startsWith("viewer.")) {
            remaining = doRound(br, line, remaining, budget);
        } else if (line.startsWith("S ")) {
            handleSummary(line, remaining);
        } else {
            System.err.println("Unknown input: " + line);
        }

    }

}

private static long doRound(BufferedReader br, String line, long remaining, long budget) throws Exception {
    Map<String, String> fields = parseFields(line);

    long[] bid = calculateBid(fields, remaining, budget);
    System.out.println(bid[0] + " " + bid[1]);

    String result = br.readLine();
    if (result == null) return remaining;
    result = result.trim();

    if (result.startsWith("W ")) {
        long cost = Long.parseLong(result.substring(2).trim());
        remaining -= cost;
        System.err.println("WIN cost=" + cost + " remaining=" + remaining);
    } else {
        System.err.println("LOSE remaining=" + remaining);
    }

    return remaining;
}

private static long[] calculateBid(Map<String, String> fields, long remaining, long budget) {
    if (remaining <= 0) return new long[]{0, 0};

    long startBid = 1;
    long maxBid = 10;

    return new long[]{startBid, maxBid};
}

private static Map<String, String> parseFields(String line) {
    Map<String, String> map = new HashMap<>();
    String[] pairs = line.split(",");
    for (String pair : pairs) {
        int eq = pair.indexOf('=');
        if (eq > 0) {
            map.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
        }
    }
    return map;
}


private static void handleSummary(String line, long remaining) {
    String[] parts = line.split(" ");
    long points = Long.parseLong(parts[1]);
    long spent = Long.parseLong(parts[2]);
    System.err.println("Summary: points=" + points + " spent=" + spent + " remaining=" + remaining);
}
