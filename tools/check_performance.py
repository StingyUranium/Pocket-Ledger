"""Gate AndroidX benchmark JSON without confusing frame CPU time with presentation.

Usage: python tools/check_performance.py results.json --hz 120
Requires a separately supplied FrameTimeline summary to certify the jank gate.
"""
import argparse
import json
import sys


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("report")
    parser.add_argument("--hz", required=True, type=int, choices=(60, 120))
    parser.add_argument("--frame-timeline", help="JSON: frames, missedDeadlines, p99OverrunMs, observedHz")
    parser.add_argument("--baseline", help="Same-device previous benchmark JSON for the 5%% regression gate")
    args = parser.parse_args()
    data = json.load(open(args.report, encoding="utf-8"))
    failures = []
    checks = 0
    previous = {}
    if args.baseline:
        previous = {b["name"]: b for b in json.load(open(args.baseline, encoding="utf-8")).get("benchmarks", [])}
    for benchmark in data.get("benchmarks", []):
        metrics = benchmark.get("metrics", {})
        for name, values in metrics.items():
            if name in ("timeToInitialDisplayMs", "timeToFullDisplayMs") and values.get("maximum", 0) >= 500:
                failures.append(f"{benchmark['name']}: {name} >=500ms")
            if name == "timeToInitialDisplayMs":
                checks += 1
        sampled = benchmark.get("sampledMetrics", {})
        cpu = sampled.get("frameDurationCpuMs", {})
        if "P99" in cpu:
            checks += 1
            if cpu["P99"] >= 1000 / args.hz:
                failures.append(f"{benchmark['name']}: p99 CPU over budget")
        overrun = sampled.get("frameOverrunMs", {})
        if overrun.get("P99", 0) > 0:
            failures.append(f"{benchmark['name']}: p99 presentation overrun >0")
        old = previous.get(benchmark["name"], {}).get("sampledMetrics", {}).get("frameDurationCpuMs", {})
        for percentile in ("P95", "P99"):
            if old.get(percentile, 0) > 0 and cpu.get(percentile, 0) > old[percentile] * 1.05:
                failures.append(f"{benchmark['name']}: {percentile} CPU regression >5%")
    if not checks:
        failures.append("No supported startup/frame metrics found")
    if not args.frame_timeline:
        failures.append("FrameTimeline summary missing: jank gate is unverified")
    else:
        frames = json.load(open(args.frame_timeline, encoding="utf-8"))
        if frames.get("observedHz") != args.hz:
            failures.append("Observed refresh rate does not match requested test rate")
        count = frames.get("frames", 0)
        if count < 10_000:
            failures.append("Fewer than 10,000 interaction frames: sample is inconclusive")
        elif frames.get("missedDeadlines", count) / count >= .005:
            failures.append("Missed-deadline rate >=0.5%")
        if frames.get("p99OverrunMs", 1) > 0:
            failures.append("FrameTimeline p99 overrun >0")
    for failure in failures:
        print(f"FAIL: {failure}")
    if not failures:
        print("PASS: absolute frame/startup gates")
    return bool(failures)


if __name__ == "__main__":
    sys.exit(main())
