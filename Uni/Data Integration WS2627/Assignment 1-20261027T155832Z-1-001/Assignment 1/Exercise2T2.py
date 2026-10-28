from pathlib import Path

import pandas as pd
import json
import os

# Konfiguration
BUDGETS = [3, 10, 30]
DATA_DIR = Path(__file__).resolve().parent / "Datasets"

# Daten laden
df_a =  pd.read_csv(DATA_DIR / "imdb_anon.csv")
df_b = pd.read_csv(DATA_DIR / "rotten_tomatoes_anon.csv")


def get_sample(series, b):
    unique_vals = series.dropna().astype(str).unique()
    if len(unique_vals) > b:
        return list(pd.Series(unique_vals).sample(n=b, random_state=42).values)
    return list(unique_vals)


cols_a = [c for c in df_a.columns if c.startswith('A')]
cols_b = [c for c in df_b.columns if c.startswith('B')]

for b in BUDGETS:
    # Ordner für das Budget erstellen
    folder_name = f"budget_{b}"
    os.makedirs(folder_name, exist_ok=True)

    # Werte für alle Rotten Tomatoes (B) Spalten einmal pro Budget berechnen
    rt_samples = {}
    rt_prompt_text = ""
    for cb in cols_b:
        vals_b = get_sample(df_b[cb], b)
        rt_samples[cb] = vals_b
        rt_prompt_text += f"{cb}: {vals_b}\n"

    # Für jede IMDb (A) Spalte eine JSON-Datei erstellen
    for ca in cols_a:
        vals_a = get_sample(df_a[ca], b)

        # Den perfekten Prompt direkt mit generieren
        prompt = (
            f"You are a database expert. I have one column from Dataset A and several columns from Dataset B. "
            f"Compare the values and tell me which column(s) from Dataset B match the Dataset A column.\n\n"
            f"Dataset A Column '{ca}':\n{vals_a}\n\n"
            f"Dataset B Columns:\n{rt_prompt_text}\n"
            f"Return ONLY a JSON array with the names of the matching Dataset B columns. "
            f"Example: [\"B02\", \"B12\"] or [] if none match."
        )

        # Das Datenpaket für dich
        data_package = {
            "budget": b,
            "first_df_column": ca,
            "second_df_column": vals_a,
            "second_df__columns": rt_samples,
        }

        # JSON speichern
        file_path = os.path.join(folder_name, f"{ca}.json")
        with open(file_path, 'w', encoding='utf-8') as f:
            json.dump(data_package, f, indent=4, ensure_ascii=False)
