#!/usr/bin/env python3
"""
Standalone CLI validation script for the IELTS Vocabulary seed dataset (JSON or CSV).
Checks:
1. Required fields
2. Duplicate headwords and normalized forms
3. Invalid topic references against the 15 official IELTS Academic topics
4. Empty or malformed entries
5. Invalid JSON or CSV syntax
"""
import csv
import json
import re
import sys
from pathlib import Path

VALID_TOPIC_IDS = {
    "topic_1_environment",
    "topic_2_education",
    "topic_3_technology",
    "topic_4_health",
    "topic_5_society",
    "topic_6_work",
    "topic_7_economy",
    "topic_8_government",
    "topic_9_urbanisation",
    "topic_10_transport",
    "topic_11_globalisation",
    "topic_12_media",
    "topic_13_science",
    "topic_14_crime",
    "topic_15_food",
}

REQUIRED_FIELDS = [
    "id",
    "headword",
    "part_of_speech",
    "vietnamese_meaning",
    "english_definition",
    "example_sentence",
    "vietnamese_example_translation",
    "topic_ids",
    "difficulty_level",
    "source_name",
    "verification_status",
]


def normalize_headword(text: str) -> str:
    return re.sub(r"\s+", " ", (text or "").strip()).lower()


def validate_entries(entries):
    errors = []
    seen_normalized = set()
    seen_ids = set()
    topic_counts = {tid: 0 for tid in VALID_TOPIC_IDS}

    for idx, entry in enumerate(entries):
        if not isinstance(entry, dict):
            errors.append(f"Row {idx}: Entry is not an object")
            continue
        headword = str(entry.get("headword", "")).strip()
        for req in REQUIRED_FIELDS:
            val = entry.get(req)
            if val is None or (isinstance(val, str) and not val.strip()) or (isinstance(val, list) and len(val) == 0):
                errors.append(f"Row {idx} ('{headword}'): Missing required field '{req}'")

        norm = normalize_headword(entry.get("normalized_headword") or headword)
        if norm in seen_normalized:
            errors.append(f"Row {idx} ('{headword}'): Duplicate normalized headword '{norm}'")
        seen_normalized.add(norm)

        entry_id = str(entry.get("id", "")).strip()
        if entry_id in seen_ids:
            errors.append(f"Row {idx} ('{headword}'): Duplicate id '{entry_id}'")
        seen_ids.add(entry_id)

        topic_ids = entry.get("topic_ids", [])
        if isinstance(topic_ids, str):
            topic_ids = [t.strip() for t in re.split(r"[|;]", topic_ids) if t.strip()]
        for tid in topic_ids:
            if tid not in VALID_TOPIC_IDS:
                errors.append(f"Row {idx} ('{headword}'): Invalid topic reference '{tid}'")
            else:
                topic_counts[tid] += 1

    return errors, len(seen_normalized), topic_counts


def main():
    path = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("app/src/main/assets/seed_vocabulary.json")
    if not path.exists():
        print(f"ERROR: Seed file not found: {path}", file=sys.stderr)
        sys.exit(1)

    try:
        if path.suffix.lower() == ".json":
            data = json.loads(path.read_text(encoding="utf-8"))
            entries = data["entries"] if isinstance(data, dict) and "entries" in data else data
        elif path.suffix.lower() == ".csv":
            with path.open("r", encoding="utf-8") as f:
                reader = csv.DictReader(f)
                entries = list(reader)
        else:
            print(f"ERROR: Unsupported file extension: {path.suffix}", file=sys.stderr)
            sys.exit(1)
    except Exception as exc:
        print(f"ERROR: Failed to parse {path}: {exc}", file=sys.stderr)
        sys.exit(1)

    errors, unique_count, topic_counts = validate_entries(entries)
    print(f"Validated {len(entries)} records ({unique_count} unique headwords) from {path}")
    for tid in sorted(VALID_TOPIC_IDS):
        print(f"  - {tid}: {topic_counts[tid]} entries")

    if errors:
        print("\nVALIDATION FAILED:", file=sys.stderr)
        for err in errors:
            print(f"  * {err}", file=sys.stderr)
        sys.exit(1)

    print("\nVALIDATION PASSED: 0 errors found.")


if __name__ == "__main__":
    main()
