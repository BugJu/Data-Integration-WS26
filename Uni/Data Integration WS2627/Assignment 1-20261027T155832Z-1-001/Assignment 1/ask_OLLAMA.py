import json
import requests
import re
from pathlib import Path

# --- Konfiguration ---
OLLAMA_URL = "http://localhost:11434/api/generate"
MODEL = "qwen2.5"
BASE_DIR = Path(__file__).resolve().parent
JSON_DIR_30 = BASE_DIR / "budget_30"
JSON_DIR_10 = BASE_DIR / "budget_10"
JSON_DIR_3 = BASE_DIR / "budget_3"
OUTPUT_FILE = BASE_DIR / "mapping_results.json"  # Ziel-Datei für die Ergebnisse
JSON_DIRS = [JSON_DIR_3, JSON_DIR_10, JSON_DIR_30]


def main():

    results = {}

    for JSON_PATH in JSON_DIRS:
        dir_name = JSON_PATH.name
        results[dir_name] = {}
        print(f"\n bearbeite Ordner: {dir_name}")

        for JSON_FILE in JSON_PATH.glob("*.json"):
            with open(JSON_FILE, 'r', encoding='utf-8') as f:
                data = json.load(f)

            col_a_name = data["first_df_column"]
            col_a_values = data["second_df_column"]
            cols_b_dict = data["second_df__columns"]

            # 2. B-Spalten für den Prompt formatieren
            candidates_text = ""
            for col_name, values in cols_b_dict.items():
                candidates_text += f"- {col_name}: {values}\n"

            # 3. Den Prompt bauen
            prompt = f"""You are a database schema matching expert.
        I have a target column '{col_a_name}' with the following sample values:
        {col_a_values}

        Here are the candidate columns from another dataset and their sample values:
        {candidates_text}

        Which candidate column matches (if there is one there might be none) the target column based ONLY on the data format and meaning?
        First think about it. Then, at the very end of your response, provide your final answer.
        You MUST end your response with exactly the matching column name (e.g. 'B01', 'B12') or 'NONE' if no column matches.
        """

            print(f"Sende Anfrage für Spalte {col_a_name} ({JSON_FILE.name}) aus {dir_name} an Ollama...")

            # Anfrgae an Ollama senden
            payload = {
                "model": MODEL,
                "prompt": prompt,
                "stream": False,
                "temperature": 0.0
            }

            try:
                response = requests.post(OLLAMA_URL, json=payload)

                if response.status_code == 200:
                    result = response.json()
                    raw_text = result['response']

                    # 5. Antwort säubern und extrahieren
                    clean_text = re.sub(r'<think>.*?</think>', '', raw_text, flags=re.DOTALL).strip()
                    matches = re.findall(r'B\d{2}|NONE', clean_text.upper())

                    if matches:
                        final_decision = matches[-1]
                        print(f"ERGEBNIS: {col_a_name} -> {final_decision}")
                        results[dir_name][col_a_name] = final_decision
                    else:
                        print(f" Keine eindeutige Spalte für {col_a_name} gefunden.")
                        results[dir_name][col_a_name] = "UNKNOWN"
                else:
                    print(f"Fehler bei Ollama ({response.status_code}) für {col_a_name}")
                    results[dir_name][col_a_name] = "ERROR"

            except Exception as e:
                print(f"Verbindungsfehler bei {col_a_name}: {e}")
                results[dir_name][col_a_name] = "ERROR"

    # 6. Gesammelte Ergebnisse ganz am Ende in die JSON-Datei schreiben
    with open(OUTPUT_FILE, 'w', encoding='utf-8') as f:
        json.dump(results, f, indent=4, ensure_ascii=False)

    print(f"\n Speichern abgeschlossen! Ergebnisse in '{OUTPUT_FILE.name}' geschrieben.")


if __name__ == "__main__":
    main()