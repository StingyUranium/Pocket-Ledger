"""Merge device-generated, app-only profile rules. Never import device databases."""
import argparse
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    baseline, startup = set(), set()
    for profile in args.directory.rglob("*-prof*.txt"):
        rules = {
            line.strip() for line in profile.read_text(encoding="utf-8").splitlines()
            if "Lin/thomas/pocketledger/" in line and "SeedActivity" not in line
        }
        baseline.update(rules)
        if "startup-prof" in profile.name:
            startup.update(rules)
    if not baseline or not startup:
        raise SystemExit("Both generated baseline/startup rules are required")
    output = Path(__file__).resolve().parent.parent / "app/src/main"
    for name, rules in (("baseline-prof.txt", baseline), ("startup-prof.txt", startup)):
        (output / name).write_text("\n".join(sorted(rules)) + "\n", encoding="utf-8")
        print(f"{name}: {len(rules)} synthetic-journey rules")


if __name__ == "__main__":
    main()
