private final static String CATEGORY = "Kids";
private static double thresholdAdjust = 0.0;
private static double bidAdjust = 1.0;

void main(String[] args) throws Exception {
    // Total budget given by the system
    long budget = Long.parseLong(args[0]);
    // Total budget given by the system
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

        // New round with video and viewer data
        if (line.startsWith("video.") || line.startsWith("viewer.")) {
            remaining = doRound(br, line, remaining, budget);
        }
        // Summary after some rounds
        else if (line.startsWith("S ")) {
            handleSummary(line, remaining);
        } else {
            System.err.println("Unknown input: " + line);
        }

    }

}

/**
 * Processes a single auction round.
 *
 * Reads the impression fields, calculates a bid, prints it, then reads
 * and logs the win/loss result returned by the system.
 *
 * @param br        input stream from the auction system
 * @param line      the first field line already read from stdin
 * @param remaining budget remaining before this round
 * @param budget    total initial budget (used for spend-floor logic)
 * @return          updated remaining budget after the round
 */

private static long doRound(BufferedReader br, String line, long remaining, long budget) throws Exception {
    // Convert input line into key value pairs
    Map<String, String> fields = parseFields(line);

    //Calculating bid based on video and viewer info
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

/**
 * Calculates the (startBid, maxBid) pair for an impression opportunity.
 *
 * Scoring factors (multiplicative):
 *  - Category match:        ×2.0
 *  - Viewer interest match: ×1.5 / 1.3 / 1.1 (1st / 2nd / 3rd position)
 *  - Subscriber:            ×1.3
 *  - High engagement:       ×1.5 (comment/view > 5 %)
 *  - Medium engagement:     ×1.2 (comment/view > 1 %)
 *  - Prime age (18–34):     ×1.2
 *  - Category + sub combo:  ×1.15 extra
 *
 * If the final score is below the (adaptive) threshold the round is skipped.
 * A minimum-spend floor lowers the threshold slightly when the agent risks
 * finishing below 30 % of total budget spent.
 *
 * @param fields    parsed key-value pairs from the auction system
 * @param remaining budget remaining
 * @param budget    total initial budget
 * @return          long[]{startBid, maxBid}, both 0 to pass/skip
 */
private static long[] calculateBid(Map<String, String> fields, long remaining, long budget) {
    // If no money left, do nothing
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

    // Extra boost if both category and subscription match
    if (categoryMatch && subscribed) {
        score *= 1.15;
    }

    // We want to spend at least 30 percent of budget
    long minSpend = budget * 30 / 100;
    long spent = budget - remaining;
    long stillNeeded = minSpend - spent;

    double threshold = 1.2 + thresholdAdjust;
    long baseBid = (long)(5 * bidAdjust);

    if (stillNeeded > 0) {
        threshold = 1.1 + thresholdAdjust;
        baseBid = (long)(7 * bidAdjust);
    }

    // If score is too low, do not bet
    if (score < threshold) {
        return new long[]{0, 0};
    }

    long maxBid = Math.min((long) (baseBid * score * 2.5), remaining);
    long startBid = Math.min((long) (baseBid * score), maxBid);

    return new long[]{startBid, maxBid};
}

/**
 * Parses a comma-separated "key=value,key=value,..." line into a map.
 *
 * @param line raw input line from the auction system
 * @return     map of field names to their values
 */
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


/**
 * Processes a summary line from the auction system and self-tunes the
 * bidding parameters for subsequent rounds.
 *
 * Efficiency = points / spent.
 *  - High efficiency (> 0.40): raise threshold (be more selective),
 *    lower bids (we do not need to over-pay).
 *  - Low efficiency (< 0.28):  lower threshold (bid on more), raise bids.
 *  - Zero spend:               emergency loosening — we are not bidding
 *    at all and must correct immediately.
 *
 * Both parameters are clamped after adjustment to prevent runaway drift.
 *
 * @param line      the "S <points> <spent>" line from the system
 * @param remaining current remaining budget (logged only)
 */
private static void handleSummary(String line, long remaining) {
    String[] parts = line.split(" ");
    long points = Long.parseLong(parts[1]);
    long spent = Long.parseLong(parts[2]);

    double efficiency = spent > 0 ? (double) points / spent : 0.0;

    if (efficiency > 0.40) {
        // Performing well — tighten selection criteria and lower spend.
        thresholdAdjust += 0.03;
        bidAdjust *= 0.97;
    } else if (efficiency < 0.28 && spent > 0) {
        // Poor returns — loosen criteria and bid higher.
        thresholdAdjust -= 0.03;
        bidAdjust *= 1.03;
    }

    if (spent == 0) {
        // We did not spend anything this period — emergency correction.
        thresholdAdjust -= 0.04;
        bidAdjust *= 1.05;
    }

    // Clamp thresholdAdjust to [-0.15, +0.15].
    if (thresholdAdjust > 0.15) thresholdAdjust = 0.15;
    if (thresholdAdjust < -0.15) thresholdAdjust = -0.15;

    // Clamp bidAdjust to [0.8, 1.3].
    if (bidAdjust > 1.3) bidAdjust = 1.3;
    if (bidAdjust < 0.8) bidAdjust = 0.8;

    System.err.println("Summary: points=" + points
            + " spent=" + spent
            + " efficiency=" + efficiency
            + " thresholdAdjust=" + thresholdAdjust
            + " bidAdjust=" + bidAdjust);
}
