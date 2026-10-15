"""Contract validation regression tests. Requires jsonschema>=4.18."""
import json
import unittest
from pathlib import Path
from jsonschema import Draft202012Validator, FormatChecker

SCHEMA = json.loads((Path(__file__).resolve().parents[1] / "contracts/v1/tool-proposal.schema.json").read_text())
VALIDATOR = Draft202012Validator(SCHEMA, format_checker=FormatChecker())

def proposal(tool, arguments):
    return {"schema_version": "1.0", "tool": tool, "arguments": arguments}

class ToolProposalContractTests(unittest.TestCase):
    def test_schema_itself_is_valid(self):
        Draft202012Validator.check_schema(SCHEMA)

    def test_allowlisted_tools(self):
        examples = [
            proposal("apps.open", {"app_name": "YouTube"}),
            proposal("maps.navigate", {"destination": "Home"}),
            proposal("web.search", {"query": "weather"}),
            proposal("reminder.create", {"title": "Call", "due_at": "2026-10-17T18:00:00+05:30"}),
            proposal("reminder.list", {}),
            proposal("reminder.cancel", {"reminder_id": "123e4567-e89b-12d3-a456-426614174000"}),
            proposal("briefing.generate", {}),
            proposal("memory.propose", {"category": "preference", "value": "Use Maps"}),
            proposal("message.prepare", {"recipient": "Alex", "body": "Running late"})
        ]
        for item in examples:
            with self.subTest(tool=item["tool"]):
                self.assertTrue(VALIDATOR.is_valid(item), list(VALIDATOR.iter_errors(item)))

    def test_reject_unsafe_and_malformed(self):
        invalid = [
            proposal("accessibility.tap", {"x": 1, "y": 2}),
            proposal("message.send", {"recipient": "Alex", "body": "Hi"}),
            proposal("apps.open", {"app_name": "YouTube", "shell": "rm -rf /"}),
            proposal("apps.open", {"destination": "Home"}),
            proposal("reminder.create", {"title": "Call", "due_at": "tomorrow"}),
            proposal("memory.propose", {"category": "password", "value": "123"}),
            proposal("briefing.generate", {"read_all_messages": True}),
            {**proposal("apps.open", {"app_name": "Maps"}), "authorized": True},
            {**proposal("apps.open", {"app_name": "Maps"}), "schema_version": "2.0"}
        ]
        for item in invalid:
            with self.subTest(item=item):
                self.assertFalse(VALIDATOR.is_valid(item))

if __name__ == "__main__":
    unittest.main()
