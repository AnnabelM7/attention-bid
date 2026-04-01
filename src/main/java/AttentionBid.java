private final static String CATEGORY = "Kids";
private static double bidMultiplier = 1.0;

void main(String[] args) throws Exception {
    long budget = Long.parseLong(args[0]);
    long remaining = budget;

    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    //Chosen category at start
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

    //Calculating bid based on video and ciewer info
    long[] bid = calculateBid(fields, remaining, budget);
    System.out.println(bid[0] + " " + bid[1]);

    //Read winn or loss result
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

    double score = 1.0;

    //Category match
    String category = fields.get("video.category");
    boolean categoryMatch = CATEGORY.equals(category);
    if (categoryMatch) {
        score *= 2.0;
    }

    //Intrests
    String interests = fields.get("viewer.interests");
    if (interests != null) {
        String[] intrestsArray = interests.split(";");
        for (int i = 0; i < intrestsArray.length; i++) {
            if (CATEGORY.equals(intrestsArray[i].trim())) {
                // Interests are ordered by relevance, so first interest is worth more
                score *= (1.5 - i * 0.2); // 1.5, 1.3, 1.1 depending on position
                break;
            }
        }
    }

    //Sub boost
    boolean subscribed = "Y".equals(fields.get("viewer.subscribed"));
    if (subscribed) score *= 1.3;


    //Engagement
    try {
        long viewCount = Long.parseLong(fields.get("video.viewCount"));
        long commentCount = Long.parseLong(fields.get("video.commentCount"));
        if (viewCount > 0) {
            double engagement = (double) commentCount / viewCount;
            if (engagement > 0.05) score *= 1.5;
            else if (engagement > 0.01) score *= 1.2;
        }
    } catch (NumberFormatException e) {
        System.err.println("Error parsing view/comment count");
    }

    //Age, 18-34 age is most valuable
    String age = fields.get("viewer.age");
    if ("18-24".equals(age) || "25-34".equals(age)) {
        score *= 1.2;
    }

    double spentRatio = 1.0 - ((double) remaining / budget);
    double spendingPressure = 1.0;

    // Increase bidding
    if (spentRatio < 0.20) {
        spendingPressure = 1.25;
    } else if (spentRatio < 0.30) {
        spendingPressure = 1.10;
    }
    // If we have already spent a lot, reduce bidding a bit
    else if (spentRatio > 0.60) {
        spendingPressure = 0.90;
    }


    // Skip very low value rounds
    if (score < 1.0) {
        return new long[]{1, 2};
    }

    //Final bid calculated from score and multiplier
    long baseBid = 5;
    double adjustedMultiplier = bidMultiplier * spendingPressure;

    long maxBid = Math.min((long) (baseBid * score * 3 * adjustedMultiplier), remaining);
    long startBid = Math.min((long) (baseBid * score * adjustedMultiplier), maxBid);

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

    // Adjust bid multiplier based on how efficient our last 100 rounds were
    double efficiency;
    if (spent > 0) {
        efficiency = (double) points / spent;
    } else {
        efficiency = 0;
    }

    if (efficiency > 1.5) {
        // Doing well, bid more aggressively
        bidMultiplier = Math.min(bidMultiplier * 1.2, 3.0);
    } else if (efficiency < 0.8) {
        // Doing poorly, bid more conservatively
        bidMultiplier = Math.max(bidMultiplier * 0.8, 0.5);
    }

    System.err.println("Summary: points=" + points + " spent=" + spent
            + " efficiency=" + efficiency + " multiplier=" + bidMultiplier);
}
