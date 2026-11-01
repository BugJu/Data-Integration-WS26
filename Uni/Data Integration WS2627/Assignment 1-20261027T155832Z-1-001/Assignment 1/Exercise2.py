import json
import re
import time
from pathlib import Path

import matplotlib.pyplot as plt
import pandas as pd
import requests


DATA_DIR = Path(__file__).resolve().parent / "Datasets"
OLLAMA_URL = "http://localhost:11434"
MODEL = "deepseek-r1:8b"
BUDGETS = [3, 10, 30]

GROUND_TRUTH_NAMES = {
    ("Name", "Name"),
    ("YearRange", "Year"),
    ("ReleaseDate", "Release Date"),
    ("Director", "Director"),
    ("Creator", "Creator"),
    ("Cast", "Cast"),
    ("Duration", "Duration"),
    ("RatingValue", "RatingValue"),
    ("Genre", "Genre"),
    ("Description", "Description"),
}

HARD_PAIRS = [
    ("Cast", "Actors"),
    ("RatingValue", "RatingCount"),
    ("YearRange", "Year"),
    ("Id", "Id"),
]


def sample_values(series, budget):
    values = series.dropna().astype(str).unique()
    count = min(budget, len(values))
    return pd.Series(values).sample(n=count, random_state=42).tolist()


def build_prompt(df_a, df_b, budget):
    # The prompt contains only opaque IDs and their sampled cell values.
    columns_a = {
        column: sample_values(df_a[column], budget) for column in df_a.columns
    }
    columns_b = {
        column: sample_values(df_b[column], budget) for column in df_b.columns
    }

    return (
        "Decide whether each column in A corresponds to each column in B using "
        "only the cell values. Return a JSON array with one row per A column and "
        "one value per B column, in the exact order shown. Use 1 for a match and "
        "0 otherwise. Return no explanation.\n"
        f"A columns and values: {json.dumps(columns_a, ensure_ascii=False)}\n"
        f"B columns and values: {json.dumps(columns_b, ensure_ascii=False)}"
    )


def parse_matrix(response_text, rows, columns):
    clean_text = re.sub(
        r"<think>.*?</think>", "", response_text, flags=re.DOTALL
    ).strip()
    fenced_json = re.search(
        r"```(?:json)?\s*(.*?)```", clean_text, flags=re.DOTALL | re.IGNORECASE
    )
    if fenced_json:
        clean_text = fenced_json.group(1).strip()

    start = clean_text.find("[")
    end = clean_text.rfind("]")
    if start == -1 or end == -1:
        raise ValueError("Ollama did not return a JSON matrix.")

    try:
        matrix = json.JSONDecoder().raw_decode(clean_text, start)[0]
    except json.JSONDecodeError as error:
        raise ValueError("Ollama returned an invalid JSON matrix.") from error
    if not isinstance(matrix, list) or len(matrix) != rows:
        raise ValueError(f"Expected {rows} rows in the model's matrix.")

    for row in matrix:
        if not isinstance(row, list) or len(row) != columns:
            raise ValueError(f"Expected {columns} decisions in every matrix row.")
        if any(value not in (0, 1, False, True) for value in row):
            raise ValueError("Matrix entries must be 0 or 1.")

    return matrix


def get_column_maps(key_df):
    imdb_key = key_df[key_df["table"] == "imdb.csv"]
    rt_key = key_df[key_df["table"] == "rotten_tomatoes.csv"]
    map_a = dict(zip(imdb_key["anonymized_column"], imdb_key["original_column"]))
    map_b = dict(zip(rt_key["anonymized_column"], rt_key["original_column"]))
    return map_a, map_b


def main():
    df_a = pd.read_csv(DATA_DIR / "imdb_anon.csv")
    df_b = pd.read_csv(DATA_DIR / "rotten_tomatoes_anon.csv")
    key_df = pd.read_csv(DATA_DIR / "column_key.csv")
    columns_a = df_a.columns.tolist()
    columns_b = df_b.columns.tolist()

    model_response = requests.get(f"{OLLAMA_URL}/api/tags", timeout=10)
    model_response.raise_for_status()
    installed_models = model_response.json()["models"]
    model_info = next(
        (model for model in installed_models if model["name"] == MODEL), None
    )
    if model_info is None:
        raise RuntimeError(f"Model {MODEL!r} is not installed in Ollama.")

    map_a, map_b = get_column_maps(key_df)
    ground_truth = {
        (column_a, column_b)
        for column_a in columns_a
        for column_b in columns_b
        if (map_a[column_a], map_b[column_b]) in GROUND_TRUTH_NAMES
    }

    details = model_info.get("details", {})
    print(
        f"Model: {model_info['name']} | Provider: Ollama (local) | "
        f"Parameters: {details.get('parameter_size', 'unknown')} | "
        f"Quantization: {details.get('quantization_level', 'unknown')} | "
        f"Digest: {model_info.get('digest', 'unknown')}"
    )
    print("Sampling: up to b distinct values per column, random_state=42.")
    print("Prompt strategy: one prompt per budget; output is a 13 x 17 binary matrix.")

    results = []
    predictions_by_budget = {}
    for budget in BUDGETS:
        prompt = build_prompt(df_a, df_b, budget)
        started = time.perf_counter()
        response = requests.post(
            f"{OLLAMA_URL}/api/generate",
            json={
                "model": MODEL,
                "prompt": prompt,
                "stream": False,
                "options": {"temperature": 0, "seed": 42},
                "format": "json",
            },
            timeout=900,
        )
        response.raise_for_status()
        answer = response.json()
        elapsed = time.perf_counter() - started

        matrix = parse_matrix(answer["response"], len(columns_a), len(columns_b))
        predictions = {
            (column_a, column_b): bool(matrix[i][j])
            for i, column_a in enumerate(columns_a)
            for j, column_b in enumerate(columns_b)
        }
        predictions_by_budget[budget] = predictions

        true_positives = sum(
            predictions[pair] for pair in ground_truth
        )
        predicted_count = sum(predictions.values())
        precision = true_positives / predicted_count if predicted_count else 0
        recall = true_positives / len(ground_truth) if ground_truth else 0

        prompt_tokens = answer["prompt_eval_count"]
        completion_tokens = answer["eval_count"]
        inference_seconds = answer.get("total_duration", int(elapsed * 1e9)) / 1e9
        results.append(
            {
                "Budget": budget,
                "Precision": precision,
                "Recall": recall,
                "Prompt tokens": prompt_tokens,
                "Completion tokens": completion_tokens,
                "Inference time (s)": inference_seconds,
            }
        )

    results_df = pd.DataFrame(results)
    print("\nResults (precision and recall use all 221 candidate pairs):")
    print(results_df.to_string(index=False, formatters={
        "Precision": "{:.3f}".format,
        "Recall": "{:.3f}".format,
        "Inference time (s)": "{:.2f}".format,
    }))

    results_df.to_csv(DATA_DIR.parent / "llm_matching_results.csv", index=False)
    all_pairs = pd.DataFrame(
        [
            {
                "Budget": budget,
                "A column": column_a,
                "B column": column_b,
                "Predicted match": int(prediction),
            }
            for budget, predictions in predictions_by_budget.items()
            for (column_a, column_b), prediction in predictions.items()
        ]
    )
    all_pairs.to_csv(DATA_DIR.parent / "llm_matching_all_pairs.csv", index=False)
    plt.plot(results_df["Budget"], results_df["Precision"], marker="o", label="Precision")
    plt.plot(results_df["Budget"], results_df["Recall"], marker="o", label="Recall")
    plt.xticks(BUDGETS)
    plt.ylim(0, 1.05)
    plt.xlabel("Values shown per column (b)")
    plt.ylabel("Score")
    plt.title(f"Schema matching with {MODEL}")
    plt.grid(True, alpha=0.3)
    plt.legend()
    plt.tight_layout()
    plot_path = DATA_DIR.parent / "llm_matching_precision_recall.png"
    plt.savefig(plot_path, dpi=150)
    plt.close()

    print("\nHard-pair decisions (expected correspondence vs. model decision):")
    for budget in BUDGETS:
        predictions = predictions_by_budget[budget]
        for name_a, name_b in HARD_PAIRS:
            pair = next(
                (a, b)
                for a in columns_a
                for b in columns_b
                if map_a[a] == name_a and map_b[b] == name_b
            )
            expected = pair in ground_truth
            print(
                f"b={budget:2}: {name_a} / {name_b} "
                f"({pair[0]} / {pair[1]}): "
                f"expected={int(expected)}, predicted={int(predictions[pair])}"
            )

    print(
        f"\nSaved results to {DATA_DIR.parent / 'llm_matching_results.csv'}"
        f"\nSaved all pair decisions to {DATA_DIR.parent / 'llm_matching_all_pairs.csv'}"
        f"\nSaved plot to {plot_path}"
    )


if __name__ == "__main__":
    main()