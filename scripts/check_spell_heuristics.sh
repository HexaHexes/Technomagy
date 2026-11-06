#!/usr/bin/env bash
# Compile and run the pure Java heuristic checks without Minecraft or external dependencies.
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
checks_dir="$(mktemp -d "${TMPDIR:-/tmp}/technomagy-heuristics.XXXXXXXX")"
trap 'rm -rf -- "$checks_dir"' EXIT
main_dir="$project_dir/src/main/java/zone/moddev/mc/technomagy/mana"
test_dir="$project_dir/src/pureTest/java/zone/moddev/mc/technomagy/mana"
javac --release 21 -d "$checks_dir" \
    "$main_dir"/Spell*.java \
    "$main_dir/StreamChemistry.java" \
    "$test_dir"/*Checks.java
for checks in SpellHeuristicsChecks SpellStreamChecks SpellInterpreterChecks StreamChemistryChecks SpellEvaluationChecks SpellEngineChecks; do
    java -cp "$checks_dir" "zone.moddev.mc.technomagy.mana.$checks"
done
