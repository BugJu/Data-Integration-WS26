import re
import json
import requests
import pandas as pd
import matplotlib.pyplot as plt
from pathlib import Path


OLLAMA_URL = "http://localhost:11434/api/generate"
MODEL = "qwen2.5"

BASE_DIR = Path(__file__).resolve().parent
DATA_DIR = BASE_DIR / "Datasets"

CSV_KEY_FILE = DATA_DIR / "column_key.csv"
IMDB_CSV = DATA_DIR / "imdb_anon.csv"
RT_CSV = DATA_DIR / "rotten_tomatoes_anon.csv"

MAPPING_RESULTS_FILE = BASE_DIR / "mapping_results.json"
TRANSLATED_RESULTS_FILE = BASE_DIR / "translated_mapping_results.json"
PLOT_FILE = BASE_DIR / "precision_recall_plot.png"

BUDGETS = [3, 10, 30]

# Exakte Ground Truth (10 relevante Paare) in Klarttext
GROUND_TRUTH = {
    "Name": "Name",
    "YearRange": "Year",
    "ReleaseDate": "Release Date",
    "Director": "Director",
    "Creator": "Creator",
    "Cast": "Cast",
    "Duration": "Duration",
    "RatingValue": "RatingValue",
    "Genre": "Genre",
    "Description": "Description"
}



def generate_budget_json_files():
    """Erstellt die JSON-Prompt-Pakete für alle Budgets b."""
    print("Generiere Budget-Datensätze...")

    if not (IMDB_CSV.exists() and RT_CSV.exists()):
        print(f" CSV-Dateien in {DATA_DIR} nicht gefunden. Überspringe Generierung.")
        return

    df_a = pd.read_csv(IMDB_CSV)
    df_b = pd.read_csv(RT_CSV)

    def get_sample(series, b):
        unique_vals = series.dropna().astype(str).unique()
        if len(unique_vals) > b:
            return list(pd.Series(unique_vals).sample(n=b, random_state=42).values)
        return list(unique_vals)

    cols_a = [c for c in df_a.columns if c.startswith('A')]
    cols_b = [c for c in df_b.columns if c.startswith('B')]

    for b in BUDGETS:
        folder_name = BASE_DIR / f"budget_{b}"
        folder_name.mkdir(exist_ok=True)

        rt_samples = {cb: get_sample(df_b[cb], b) for cb in cols_b}

        for ca in cols_a:
            vals_a = get_sample(df_a[ca], b)
            data_package = {
                "budget": b,
                "first_df_column": ca,
                "second_df_column": vals_a,
                "second_df__columns": rt_samples,
            }

            file_path = folder_name / f"{ca}.json"
            with open(file_path, 'w', encoding='utf-8') as f:
                json.dump(data_package, f, indent=4, ensure_ascii=False)

    print("Budget-Dateien erfolgreich generiert.")

def run_ollama_matching():
    """Führt Schema Matching mit Ollama durch und erfasst Token & Zeiten."""
    print("\n🤖 [Schritt 2] Starte Ollama Schema Matching...")

    results = {}
    metrics_per_budget = {}

    for b in BUDGETS:
        dir_name = f"budget_{b}"
        json_path = BASE_DIR / dir_name

        if not json_path.exists():
            print(f"⚠️ Ordner {dir_name} existiert nicht. Überspringe...")
            continue

        results[dir_name] = {}

        total_prompt_tokens = 0
        total_completion_tokens = 0
        total_duration_sec = 0.0

        print(f"\n Bearbeite Ordner: {dir_name}")

        for json_file in sorted(json_path.glob("*.json")):
            with open(json_file, 'r', encoding='utf-8') as f:
                data = json.load(f)

            col_a_name = data["first_df_column"]
            col_a_values = data["second_df_column"]
            cols_b_dict = data["second_df__columns"]

            candidates_text = "".join([f"- {col_name}: {values}\n" for col_name, values in cols_b_dict.items()])

            prompt = f"""You are a database schema matching expert.
                        I have a target column '{col_a_name}' with the following sample values:
                        {col_a_values}

                        Here are the candidate columns from another dataset and their sample values:
                        {candidates_text}
                        
                        Which candidate column matches (if there is one there might be none) the target column based ONLY on the data format and meaning?
                        First think about it. Then, at the very end of your response, provide your final answer.
                        You MUST end your response with exactly the matching column name (e.g. 'B01', 'B12') or 'NONE' if no column matches.
                        """

            payload = {
                "model": MODEL,
                "prompt": prompt,
                "stream": False,
                "options": {
                    "num_ctx": 32768,
                    "temperature": 0.0
                }
            }

            try:
                response = requests.post(OLLAMA_URL, json=payload)
                if response.status_code == 200:
                    res_json = response.json()
                    raw_text = res_json.get('response', '')

                    # Token & Laufzeit erfassen
                    total_prompt_tokens += res_json.get('prompt_eval_count', 0)
                    total_completion_tokens += res_json.get('eval_count', 0)
                    total_duration_sec += res_json.get('total_duration', 0) / 1e9

                    clean_text = re.sub(r'<think>.*?</think>', '', raw_text, flags=re.DOTALL).strip()
                    matches = re.findall(r'B\d{2}|NONE', clean_text.upper())

                    final_decision = matches[-1] if matches else "NONE"
                    print(f"  -> {col_a_name} -> {final_decision}")
                    results[dir_name][col_a_name] = final_decision
                else:
                    results[dir_name][col_a_name] = "ERROR"
            except Exception as e:
                print(f" Verbindungsfehler bei {col_a_name}: {e}")
                results[dir_name][col_a_name] = "ERROR"

        metrics_per_budget[b] = {
            "prompt_tokens": total_prompt_tokens,
            "completion_tokens": total_completion_tokens,
            "total_tokens": total_prompt_tokens + total_completion_tokens,
            "duration_sec": total_duration_sec
        }

    with open(MAPPING_RESULTS_FILE, 'w', encoding='utf-8') as f:
        json.dump(results, f, indent=4, ensure_ascii=False)

    return results, metrics_per_budget


def translate_results(results):
    """Übersetzt Anonymisierte Kürzel (A01, B06) in echte Spaltennamen."""
    print("\n🔄 [Schritt 3] Übersetze Spaltennamen mittels column_key.csv...")

    if not CSV_KEY_FILE.exists():
        print(f" Fehler: CSV key file nicht gefunden unter {CSV_KEY_FILE}")
        return results

    df_key = pd.read_csv(CSV_KEY_FILE)
    key_map = dict(zip(df_key['anonymized_column'], df_key['original_column']))

    translated_results = {}
    for budget_dir, mappings in results.items():
        translated_results[budget_dir] = {}
        for col_a, col_b in mappings.items():
            translated_a = key_map.get(col_a, col_a)
            translated_b = key_map.get(col_b, col_b)
            translated_results[budget_dir][translated_a] = translated_b

    with open(TRANSLATED_RESULTS_FILE, 'w', encoding='utf-8') as f:
        json.dump(translated_results, f, indent=4, ensure_ascii=False)

    print(f" Übersetzte Ergebnisse gespeichert unter: '{TRANSLATED_RESULTS_FILE.name}'")
    return translated_results

def evaluate_and_plot(translated_results, metrics_per_budget):
    """Berechnet Precision/Recall gegen Ground Truth und erstellt Tabelle + Plot."""
    print("\n [Schritt 4] Evaluierung & Visualisierung...")

    summary_table = []

    for b in BUDGETS:
        dir_name = f"budget_{b}"
        predictions = translated_results.get(dir_name, {})

        tp = 0  # True Positives
        fp = 0  # False Positives
        fn = 0  # False Negatives

        # Ground Truth Evaluierung gegen die 10 echten Matches
        for target_col, true_match in GROUND_TRUTH.items():
            pred_match = predictions.get(target_col, "NONE")

            if pred_match != "NONE":
                if pred_match == true_match:
                    tp += 1
                else:
                    fp += 1
            else:
                fn += 1

        # Zusätzliche FPs für Spalten ohne Match (z.B. Id, Url, ContentRating), falls ihnen etwas zugeordnet wurde
        for pred_col, pred_match in predictions.items():
            if pred_col not in GROUND_TRUTH and pred_match != "NONE":
                fp += 1

        precision = tp / (tp + fp) if (tp + fp) > 0 else 0.0
        recall = tp / len(GROUND_TRUTH)  # |G| = 10

        b_metrics = metrics_per_budget.get(b, {})
        summary_table.append({
            "Budget (b)": b,
            "Precision": precision,
            "Recall": recall,
            "Prompt Tokens": b_metrics.get("prompt_tokens", 0),
            "Completion Tokens": b_metrics.get("completion_tokens", 0),
            "Total Tokens": b_metrics.get("total_tokens", 0),
            "Inference Time (s)": round(b_metrics.get("duration_sec", 0.0), 2)
        })

    df_summary = pd.DataFrame(summary_table)

    # 1. Ergebnistabelle drucken
    print("\n" + "=" * 90)
    print(" ERGEBNISSUMMATION SCHEMA MATCHING OLLAMA")
    print("=" * 90)
    print(df_summary.to_string(index=False))
    print("=" * 90)

    # 2. Plot erstellen
    plt.figure(figsize=(8, 5))
    plt.plot(df_summary["Budget (b)"], df_summary["Precision"], marker='o', linewidth=2.5, label='Precision')
    plt.plot(df_summary["Budget (b)"], df_summary["Recall"], marker='s', linewidth=2.5, label='Recall')

    plt.title('Precision and Recall as a Function of Budget (b)', fontsize=13, fontweight='bold')
    plt.xlabel('Budget (b)', fontsize=11)
    plt.ylabel('Score', fontsize=11)
    plt.xticks(BUDGETS)
    plt.ylim(-0.05, 1.05)
    plt.grid(True, linestyle='--', alpha=0.7)
    plt.legend(fontsize=11)
    plt.tight_layout()

    plt.savefig(PLOT_FILE, dpi=300)
    print(f"\n Diagramm erfolgreich gespeichert unter: '{PLOT_FILE.name}'")

def main():
    # 1. Budget JSON-Dateien aus CSV-Dateien bauen
    generate_budget_json_files()

    # 2. Ollama ausführen
    results, metrics_per_budget = run_ollama_matching()

    # 3. Spalten übersetzen
    translated_results = translate_results(results)

    # 4. Metriken evaluieren, Tabelle drucken & Diagramm plotten
    evaluate_and_plot(translated_results, metrics_per_budget)


if __name__ == "__main__":
    main()