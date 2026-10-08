import importlib.util
import json
import sys
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("security_gate.py")
SPEC = importlib.util.spec_from_file_location("security_gate", MODULE_PATH)
gate = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = gate
SPEC.loader.exec_module(gate)


SHA = "a" * 40


def finding(severity="High", rule="SAST-JAVA-002", line=10):
    return {"ruleId": rule, "fileName": "src/Main.java", "line": line, "column": 5,
            "severity": severity}


def result(findings, **overrides):
    value = {
        "analysisId": "current",
        "status": "COMPLETED",
        "stage": "COMPLETED",
        "repositoryUrl": "https://github.com/acme/demo",
        "commitSha": SHA,
        "filesTotal": 2,
        "filesProcessed": 2,
        "findings": findings,
        "semanticStatus": "DEGRADED",
        "suggestionStatus": "DEGRADED",
    }
    value.update(overrides)
    return value


class SecurityGateTest(unittest.TestCase):
    def setUp(self):
        self.config = gate.GateConfig(
            "https://sast.example", "https://github.com/acme/demo", SHA,
            "ci@example.com", "not-used-by-evaluate", 10, 1,
            Path(".sast/security-gate.json"))

    def test_blocks_new_deterministic_critical(self):
        """BDD-E-08: Critical determinístico novo bloqueia a política local."""
        summary = gate.evaluate(self.config, result([finding("Critical")]), [])
        self.assertEqual("FAIL", summary["status"])
        self.assertEqual(1, summary["newCritical"])

    def test_existing_critical_does_not_block_and_high_is_report_only(self):
        """BDD-E-09: Critical existente e High não bloqueiam a política local."""
        critical = finding("Critical", line=11)
        summary = gate.evaluate(self.config, result([critical, finding("High")]), [critical])
        self.assertEqual("PASS", summary["status"])
        self.assertEqual(0, summary["newCritical"])
        self.assertEqual(1, summary["high"])
        self.assertEqual("DEGRADED", summary["semanticStatus"])

    def test_ai_degradation_does_not_hide_deterministic_result(self):
        """BDD-E-10: IA degradada não muda a decisão determinística."""
        summary = gate.evaluate(self.config, result([finding("Low")]), [])
        self.assertEqual("PASS", summary["status"])
        self.assertEqual("DEGRADED", summary["suggestionStatus"])

    def test_fails_without_deterministic_coverage(self):
        """BDD-E-11: cobertura determinística incompleta impede aprovação."""
        with self.assertRaises(gate.GateFailure):
            gate.evaluate(self.config, result([], filesProcessed=1), [])

    def test_fails_when_commit_or_repository_does_not_match(self):
        """BDD-E-12: resultado de outra origem ou SHA impede aprovação."""
        with self.assertRaises(gate.GateFailure):
            gate.evaluate(self.config, result([], commitSha="b" * 40), [])
        with self.assertRaises(gate.GateFailure):
            gate.evaluate(self.config, result([], repositoryUrl="https://github.com/other/demo"), [])

    def test_policy_keeps_ai_out_of_the_gate(self):
        """BDD-E-10: política versionada mantém a IA fora da decisão."""
        policy = json.loads(Path(".sast/security-gate.json").read_text(encoding="utf-8"))
        self.assertTrue(policy["blocking"]["newCriticalDeterministic"])
        self.assertFalse(policy["ai"]["severityAffectsGate"])


if __name__ == "__main__":
    unittest.main()
