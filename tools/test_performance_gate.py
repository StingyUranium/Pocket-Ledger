import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


class PerformanceGateTest(unittest.TestCase):
    def run_gate(self, cpu=6, missed=20, frames=10_000, hz=120, include_timeline=True):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            report = path / "report.json"
            report.write_text(json.dumps({"benchmarks": [{"name": "synthetic", "sampledMetrics": {
                "frameDurationCpuMs": {"P99": cpu}, "frameOverrunMs": {"P99": -1}
            }}]}))
            timeline = path / "frames.json"
            timeline.write_text(json.dumps({"frames": frames, "missedDeadlines": missed,
                                           "p99OverrunMs": -1, "observedHz": hz}))
            command = [sys.executable, str(Path(__file__).with_name("check_performance.py")), str(report), "--hz", "120"]
            if include_timeline:
                command += ["--frame-timeline", str(timeline)]
            return subprocess.run(command, capture_output=True, text=True).returncode

    def test_requires_presentation_evidence(self):
        self.assertNotEqual(0, self.run_gate(include_timeline=False))

    def test_rejects_cpu_overrun(self):
        self.assertNotEqual(0, self.run_gate(cpu=9))

    def test_rejects_half_percent_jank(self):
        self.assertNotEqual(0, self.run_gate(missed=50))

    def test_rejects_wrong_actual_rate_and_insufficient_samples(self):
        self.assertNotEqual(0, self.run_gate(hz=60))
        self.assertNotEqual(0, self.run_gate(frames=9_999))

    def test_accepts_complete_under_budget_evidence(self):
        self.assertEqual(0, self.run_gate())


if __name__ == "__main__":
    unittest.main()
