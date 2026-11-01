import re
import requests
import pandas as pd
from pathlib import Path

# ==========================================
# KONFIGURATION
# ==========================================
OLLAMA_URL = "http://localhost:11434/api/generate"
MODEL = "qwen2.5"

# Pfade anpassen, falls dein Datasets-Ordner woanders liegt
BASE_DIR = Path(__file__).resolve().parent
IMDB_CSV = BASE_DIR / "Datasets" / "imdb_anon.csv"
RT_CSV = BASE_DIR / "Datasets" / "rotten_tomatoes_anon.csv"

# Maximal b=10 Beispiele pro Spalte.
EXAMPLES = {
    "Duration": [
        "('1 hr. 38 min.' -> '98 min')",
        "('2 hr. 15 min.' -> '135 min')",
        "('1 hr. 5 min.' -> '65 min')",
        "('2 hr. 0 min.' -> '120 min')",
        "('45 min.' -> '45 min')"
    ],
    "YearRange": [
        "('1998 1999 2000' -> '1999')",
        "('2010 2011 2012' -> '2011')",
        "('1984 1985 1986' -> '1985')",
        "('1930 1931 1932' -> '1931')"
    ],
    "ReleaseDate": [
        "('1998-10-12 (USA)' -> 'Oct 12, 1998')",
        "('2023-01-05 (USA)' -> 'Jan 5, 2023')",
        "('1934-12-24 (USA)' -> 'Dec 24, 1934')"
    ],
    "RatingValue": [
        "('8.4/10' -> '84%')",
        "('7.2/10' -> '72%')",
        "('9.9/10' -> '99%')",
        "('4.5/10' -> '45%')"
    ]
}

# Zuordnung der Spaltenkürzel und Regex-Muster für die Evaluierung
EVAL_CONFIG = {
    "Duration": {"col": "A13", "rt_col": "B07", "regex": r"^\d+\s*min.$"},
    "YearRange": {"col": "A05", "rt_col": "B10", "regex": r"^\d{4}$"},
    "ReleaseDate": {"col": "A11", "rt_col": "B17", "regex": r"^[A-Z][a-z]{2}\s\d{1,2},\s\d{4}$"},
    "RatingValue": {"col": "A03", "rt_col": "B04", "regex": r"^\d{1,3}%$"}
}


def query_ollama_for_code(examples_text):
    """Sendet den Prompt an Ollama und extrahiert den Python-Code."""
    prompt = f"""Write a Python function named `transform_value(val)` that converts a string value from a source format into a target format.
Here are examples of (Source Value -> Target Value):
{examples_text}

Rules:
1. The function must be named exactly `transform_value(val)`.
2. It should handle edge cases gracefully by returning None.
3. Return ONLY valid, executable Python code. Do not include any explanations, markdown formatting, or print statements. JUST the raw python code.
"""
    payload = {
        "model": MODEL,
        "prompt": prompt,
        "stream": False,
        "options": {"temperature": 0.0}
    }

    try:
        response = requests.post(OLLAMA_URL, json=payload)
        if response.status_code == 200:
            raw_text = response.json().get('response', '')
            clean_code = raw_text.replace('```python', '').replace('```', '').strip()
            clean_code = re.sub(r'<think>.*?</think>', '', clean_code, flags=re.DOTALL).strip()
            return clean_code
    except Exception as e:
        print(f"❌ API-Fehler: {e}")
    return None


def evaluate_format_compliance(generated_code, df_imdb, column_name, regex_pattern):
    """Lädt den generierten Code dynamisch und testet ihn auf 100 Datensätzen."""
    local_scope = {}
    try:
        exec(generated_code, globals(), local_scope)
        transform_value = local_scope['transform_value']
    except Exception as e:
        print(f"❌ Syntaxfehler im generierten Code: {e}")
        return 0.0

    sample_100 = df_imdb[column_name].dropna().head(100)
    total_tested = len(sample_100)
    format_matches = 0
    print_count = 0  # Zähler für Konsolenausgabe

    for val in sample_100:
        try:
            transformed = transform_value(val)
            # Zeige die ersten 3 Transformationen als Beispiel
            if print_count < 3:
                print(f"    👀 Format-Beispiel {print_count + 1}: '{val}' --> '{transformed}'")
                print_count += 1

            if transformed is not None and re.match(regex_pattern, str(transformed), re.IGNORECASE):
                format_matches += 1
        except Exception:
            pass

    fraction = format_matches / total_tested if total_tested > 0 else 0.0
    print(f"   -> Regex Format Check: {regex_pattern}")
    print(f"   -> Conforming Fraction: {fraction:.2f} ({format_matches}/{total_tested})")
    return fraction


def evaluate_exact_matches(generated_code, df_imdb, df_rt, col_imdb, col_rt):
    """Joint auf Filmnamen und prüft auf exakte Übereinstimmung der Werte."""
    # A10 = IMDb Name, B05 = RT Name
    df_joined = pd.merge(df_imdb, df_rt, left_on="A10", right_on="B05")

    local_scope = {}
    try:
        exec(generated_code, globals(), local_scope)
        transform_value = local_scope['transform_value']
    except Exception:
        return 0.0

    exact_matches = 0
    total_valid_joins = 0
    print_count = 0  # Zähler für Konsolenausgabe

    # Nur betrachten, wo in beiden Spalten Werte vorhanden sind
    valid_pairs = df_joined[[col_imdb, col_rt]].dropna()

    for _, row in valid_pairs.iterrows():
        total_valid_joins += 1
        try:
            transformed = transform_value(row[col_imdb])
            # Zeige die ersten 3 Exact Match Vergleiche
            if print_count < 3:
                print(
                    f"    👀 Exact-Beispiel {print_count + 1}: '{row[col_imdb]}' --> '{transformed}' | Zielwert RT: '{row[col_rt]}'")
                print_count += 1

            if str(transformed).strip() == str(row[col_rt]).strip():
                exact_matches += 1
        except Exception:
            pass

    fraction = exact_matches / total_valid_joins if total_valid_joins > 0 else 0
    print(f"   -> Exact Match Fraction nach Join: {fraction:.2f} ({exact_matches}/{total_valid_joins})")
    return fraction


def main():
    print("🚀 Lade Datensätze für Evaluierung...")
    if not IMDB_CSV.exists() or not RT_CSV.exists():
        print(f"❌ Datensätze nicht gefunden. Bitte Pfade prüfen.")
        return

    df_imdb = pd.read_csv(IMDB_CSV)
    df_rt = pd.read_csv(RT_CSV)

    for task_name, examples in EXAMPLES.items():
        print("=" * 60)
        print(f"🔧 Task: Erstelle Funktion für {task_name}")
        print(f"   Anzahl der Beispiele im Prompt: {len(examples)}")

        examples_text = "\n".join(examples)
        generated_code = query_ollama_for_code(examples_text)

        if generated_code:
            print("\n🤖 VOM LLM GENERIERTER CODE:\n")
            print(generated_code)
            print("-" * 40)

            # 1. Format Compliance für alle prüfen
            config = EVAL_CONFIG[task_name]
            evaluate_format_compliance(generated_code, df_imdb, config["col"], config["regex"])

            # 2. Exact Match nur für Duration und YearRange
            if task_name in ["Duration", "YearRange"]:
                evaluate_exact_matches(generated_code, df_imdb, df_rt, config["col"], config["rt_col"])

        else:
            print("\n❌ Fehler bei der Code-Generierung.")
        print("\n")


if __name__ == "__main__":
    main()