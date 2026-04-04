# BidAgent — Ad Auction Bidding Agent

An automated bidding agent for a real-time ad auction system.  
Targets the **Kids** content category and self-tunes its aggressiveness based on efficiency summaries.

---

## How it works

### Startup

The agent reads its total budget from `args[0]` and immediately prints the chosen category (`Kids`) to stdout.

### Per-round flow

```
System  →  "video.category=Kids,viewer.age=18-24,..."
Agent   →  "startBid maxBid"    (both 0 = pass/skip this round)
System  →  "W <cost>" | "L"
```

### Scoring model

Each impression is scored with multiplicative factors:

| Signal | Condition | Multiplier |
|---|---|---|
| Category match | `video.category == "Kids"` | ×2.0 |
| 1st viewer interest | interest[0] == "Kids" | ×1.5 |
| 2nd viewer interest | interest[1] == "Kids" | ×1.3 |
| 3rd viewer interest | interest[2] == "Kids" | ×1.1 |
| Subscriber | `viewer.subscribed == "Y"` | ×1.3 |
| High engagement | comments/views > 5 % | ×1.5 |
| Medium engagement | comments/views > 1 % | ×1.2 |
| Prime age bracket | 18–24 or 25–34 | ×1.2 |
| Category + subscriber combo | both match | ×1.15 extra |

If the final score is below the (adaptive) threshold the round is skipped.

### Budget floor

The agent tries to spend at least **30 %** of its total budget.  
When it risks falling short, the threshold is relaxed and the base bid is raised.

### Self-tuning (summary checkpoints)

After every `S <points> <spent>` line the agent adjusts two parameters:

| Parameter | High efficiency (> 0.40) | Low efficiency (< 0.28) | Zero spend |
|---|---|---|---|
| `thresholdAdjust` | +0.03 | -0.03 | -0.04 |
| `bidAdjust` | ×0.97 | ×1.03 | ×1.05 |

Both are clamped: `thresholdAdjust ∈ [-0.15, +0.15]`, `bidAdjust ∈ [0.8, 1.3]`.

---

## Project structure

```
BidAgent.java   — the bidding agent (single-file, no dependencies)
README.md       — this file
```

---

## AI assistance

Comments and parts of this code were written with the help of [Claude](https://claude.ai) (Anthropic).