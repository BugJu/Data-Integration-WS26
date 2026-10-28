import json
import requests
import re
from pathlib import Path

# --- Konfiguration ---
OLLAMA_URL = "http://localhost:11434/api/generate"
MODEL = "qwen2.5"
JSON_FILE = Path(__file__).resolve().parent / "budget_30" / "A10.json"


def main():
    # 1. JSON-Datei laden
    with open(JSON_FILE, 'r', encoding='utf-8') as f:
        data = json.load(f)

    # Daten auslesen (Achtung: basierend auf deinen JSON-Keys)
    col_a_name = data["first_df_column"]
    col_a_values = data["second_df_column"]  # In deinem JSON sind das die Werte für A
    cols_b_dict = data["second_df__columns"]  # Das Dictionary mit allen B-Spalten

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

    print(f"Sende Anfrage für Spalte {col_a_name} an Ollama...")

    # 4. Anfrage an Ollama senden
    payload = {
        "model": MODEL,
        "prompt": prompt,
        "stream": False,
        "temperature": 0.0  # 0.0 macht die Antworten analytischer und konstanter
    }

    response = requests.post(OLLAMA_URL, json=payload)

    if response.status_code == 200:
        result = response.json()
        raw_text = result['response']

        # 5. Antwort säubern und extrahieren
        # Den "Gedanken"-Block von DeepSeek entfernen
        clean_text = re.sub(r'<think>.*?</think>', '', raw_text, flags=re.DOTALL).strip()

        # Wir suchen am Ende des Textes nach einem B gefolgt von 2 Zahlen (z.B. B06) oder NONE
        matches = re.findall(r'B\d{2}|NONE', clean_text.upper())

        if matches:
            final_decision = matches[-1]  # Das allerletzte gefundene Kürzel nehmen
            print(f"\n✅ ERGEBNIS: {col_a_name} matcht mit -> {final_decision}")
        else:
            print(f"\n⚠️ Keine eindeutige Spalte in der Antwort gefunden. Text war:\n{clean_text}")

    else:
        print(f"Fehler bei der Verbindung zu Ollama: {response.status_code}")


if __name__ == "__main__":
    main()