import json
from pathlib import Path

from SPARQLWrapper import SPARQLWrapper, JSON

# Setup Sparqle Query
sparql = SPARQLWrapper("https://dbpedia.org/sparql")

# OPTIONAL entfernt und Längen-Filter hinzugefügt, um ungültige Werte wie "omit" und historische Staaten ohne ISO zu filtern
sparql.setQuery("""
    PREFIX dbo: <http://dbpedia.org/ontology/>
    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
    PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>

    SELECT DISTINCT ?countryLabel ?isoCode WHERE {
      ?country rdf:type dbo:Country ;
               rdfs:label ?countryLabel ;
               dbo:iso31661Code ?isoCode .

      FILTER (LANG(?countryLabel) = "en")
      FILTER (STRLEN(?isoCode) <= 3)
    }
""")

sparql.setReturnFormat(JSON)
results = sparql.query().convert()
out_dir = Path(__file__).resolve().parent / "json_dumps"
out_dir.mkdir(parents=True, exist_ok=True)

with open(out_dir / "all_countries_filled_sparql.json", "w", encoding="utf-8") as f:
    json.dump(results, f, ensure_ascii=False, indent=2)

for result in results["results"]["bindings"]:
    print(result["countryLabel"]["value"], result.get("isoCode", {}).get("value", ""))


def evaluate_ground_truth(dump_path, ground_truth):
    """Geht alle Ground-Truth-Einträge durch und prüft, ob sie im JSON-Dump existieren und gleich sind."""
    with open(dump_path, "r", encoding="utf-8") as f:
        dump = json.load(f)

    # Lookup: Ländername -> Code aus dem SPARQL-Dump
    dump_codes = {
        b["countryLabel"]["value"].strip(): b.get("isoCode", {}).get("value", "").strip()
        for b in dump["results"]["bindings"]
    }

    total = 0
    found = 0
    correct_codes = 0
    not_found = []
    mismatches = []

    for country_name, gt_info in ground_truth.items():
        total += 1
        expected_code = str(gt_info.get("Code", "")).strip()

        if country_name not in dump_codes:
            not_found.append(country_name)
            continue

        found += 1
        actual_code = dump_codes[country_name]

        if actual_code.upper() == expected_code.upper():
            correct_codes += 1
        else:
            mismatches.append({"country": country_name, "expected": expected_code, "actual": actual_code})

    # Korrigierte String-Multiplikation für die Trennstriche
    print("\n" + "=" * 45)
    print("           EVALUATION RESULTS            ")
    print("=" * 45)
    print(f"Gesamt Ground-Truth-Einträge: {total}")
    print(f"Im Dump gefunden:             {found}/{total} ({found / total * 100:.2f}%)")
    if found > 0:
        print(f"Codes gleich:                 {correct_codes}/{found} ({correct_codes / found * 100:.2f}%)")
    print("Hauptstädte:                  nicht im Dump enthalten")

    if not_found:
        print(f"\nNicht im Dump gefunden ({len(not_found)}):")
        for name in not_found:
            print(f" - {name}")

    if mismatches:
        print(f"\nAbweichungen ({len(mismatches)} insgesamt):")
        for m in mismatches:
            print(f" - {m['country']}: Erwartet={m['expected']} | Erhalten={m['actual']}")
    print("=" * 45)


# Ausführung der Evaluation
ground_truth_path = Path(__file__).resolve().parent / "ground_truth" / "ground_truth.json"

if ground_truth_path.exists():
    with open(ground_truth_path, "r", encoding="utf-8") as f:
        ground_truth = json.load(f)

    evaluate_ground_truth(out_dir / "all_countries_filled_sparql.json", ground_truth)
else:
    print(f"\nFehler: Konnte die Ground-Truth-Datei unter {ground_truth_path} nicht finden.")