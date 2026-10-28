import json
import os
import random
import time

import pandas as pd
from pathlib import Path
import Levenshtein
import ollama

data_dir = Path(__file__).resolve().parent / "Datasets"

imdb_df = pd.read_csv(data_dir / "imdb.csv",
                      delimiter=',',
                      encoding="utf_8")

rt_df = pd.read_csv(data_dir / "rotten_tomatoes.csv",
                    delimiter=',',
                    encoding="utf_8")

imdb_anon_df = pd.read_csv(data_dir / "imdb_anon.csv",
                           delimiter=',',
                           encoding="utf_8")

rt_anon_df = pd.read_csv(data_dir / "rotten_tomatoes_anon.csv",
                         delimiter=',',
                         encoding="utf_8")

MODEL = os.getenv("OLLAMA_MODEL", "deepseek-r1:8b")
BUDGET_VALUES = (3, 10, 30)
RANDOM_SEED = 42


def main():
    # exercise1(imdb_df, rt_df)
    # exercise2(imdb_df, rt_df)
    exercise3(imdb_anon_df, rt_anon_df)


def exercise1(df1, df2):
    threshold = 3
    df1_headers = df1.columns.tolist()
    df2_headers = df2.columns.tolist()
    cross_headers_equal = []
    for header1 in df1_headers:
        for header2 in df2_headers:
            levenshtein = Levenshtein.distance(header1, header2)
            if levenshtein < threshold:
                cross_headers_equal.append([header1, header2, levenshtein])
    print(cross_headers_equal)


def exercise2(df1, df2):
    threshold = 0.05
    matches = []
    df1_headers = df1.columns.tolist()
    df2_headers = df2.columns.tolist()
    for header1 in df1_headers:
        set1 = set(df1[header1].tolist())
        for header2 in df2_headers:
            set2 = set(df2[header2].tolist())

            intersection_size = len(set1.intersection(set2))
            union_size = len(set1.union(set2))

            if union_size == 0:
                jaccard_sim = 0
            else:
                jaccard_sim = intersection_size / union_size

            if jaccard_sim >= threshold:
                matches.append([header1, header2, round(jaccard_sim, 5)])
    print(matches)


def get_sampled_values(
        series: pd.Series, b: int, strategy: str = "random", seed: int | str = RANDOM_SEED
) -> list:
    if b < 0:
        raise ValueError("The value budget must not be negative")

    clean_series = series.dropna().astype(str)

    if strategy == "random":
        values = clean_series.tolist()
        random.Random(seed).shuffle(values)
        return values[:b]
    if len(clean_series) <= b:
        return clean_series.tolist()

    if strategy == "unique":
        return clean_series.unique()[:b].tolist()
    if strategy == "frequent":
        return clean_series.value_counts().index[:b].tolist()
    if strategy == "longest":
        sorted_by_len = clean_series.tolist()
        sorted_by_len.sort(key=len, reverse=True)
        return sorted_by_len[:b]
    raise ValueError(f"Unbekannte Strategie: {strategy}")


def build_column_prompt(left_samples: dict, right_id: str, right_values: list) -> str:
    payload = {
        "left_columns": [
            {"id": column_id, "values": values}
            for column_id, values in left_samples.items()
        ],
        "right_column": {"id": right_id, "values": right_values},
    }
    return (
        "For each left column, decide independently whether it represents the same "
        "underlying attribute as the right column, using only the supplied values. "
        "Do not infer a match from the identifiers. Return exactly one JSON object "
        'with this shape: {"decisions":[{"left":"A01","right":"B01",'
        '"match":true}]}. Include exactly one decision for every left column, '
        "with the right identifier shown in the input, and no other text.\n"
        f"Input values:\n{json.dumps(payload, ensure_ascii=False)}"
    )


def parse_column_decisions(response_text: str, left_ids: list, right_id: str) -> list:
    response = json.loads(response_text)
    if not isinstance(response, dict) or set(response) != {"decisions"}:
        raise ValueError("The model response must contain only a 'decisions' list")

    decisions = response["decisions"]
    if not isinstance(decisions, list) or len(decisions) != len(left_ids):
        raise ValueError(f"Expected exactly {len(left_ids)} pair decisions")

    parsed = []
    seen_left_ids = set()
    for decision in decisions:
        if not isinstance(decision, dict) or set(decision) != {"left", "right", "match"}:
            raise ValueError("Each decision must contain left, right, and match fields")
        left_id = decision["left"]
        if left_id not in left_ids or left_id in seen_left_ids:
            raise ValueError(f"Unexpected or duplicate left identifier: {left_id!r}")
        if decision["right"] != right_id:
            raise ValueError(f"Expected right identifier {right_id!r}")
        if type(decision["match"]) is not bool:
            raise ValueError("Each match decision must be a JSON boolean")
        seen_left_ids.add(left_id)
        parsed.append(decision)

    if seen_left_ids != set(left_ids):
        raise ValueError("The model did not return a decision for every left column")
    return parsed


def exercise3(df1, df2):
    left_ids = df1.columns.tolist()
    right_ids = df2.columns.tolist()
    expected_left_ids = {f"A{i:02}" for i in range(1, 14)}
    expected_right_ids = {f"B{i:02}" for i in range(1, 18)}
    if set(left_ids) != expected_left_ids:
        raise ValueError("The left dataframe must contain exactly the opaque A01-A13 columns")
    if set(right_ids) != expected_right_ids:
        raise ValueError("The right dataframe must contain exactly the opaque B01-B17 columns")

    results = []
    metrics = []
    for budget in BUDGET_VALUES:
        left_samples = {
            col: get_sampled_values(df1[col], b=budget, strategy="random", seed=f"{RANDOM_SEED}:{col}")
            for col in left_ids
        }
        budget_started = time.perf_counter()
        prompt_tokens = 0
        completion_tokens = 0

        for right_id in right_ids:
            right_values = get_sampled_values(
                df2[right_id], b=budget, strategy="random", seed=f"{RANDOM_SEED}:{right_id}"
            )
            prompt = build_column_prompt(left_samples, right_id, right_values)
            response = ollama.chat(
                model=MODEL,
                messages=[{"role": "user", "content": prompt}],
                format="json",
                options={"temperature": 0},
                think=False,
            )
            response_decisions = parse_column_decisions(
                response.message.content, left_ids, right_id
            )
            results.extend(
                {"budget": budget, "left": item["left"], "right": item["right"], "match": item["match"]}
                for item in response_decisions
            )
            prompt_tokens += response.prompt_eval_count or 0
            completion_tokens += response.eval_count or 0

        metrics.append({
            "budget": budget,
            "prompts": len(right_ids),
            "prompt_tokens": prompt_tokens,
            "completion_tokens": completion_tokens,
            "inference_time_seconds": round(time.perf_counter() - budget_started, 3),
        })

    output_dir = Path(__file__).resolve().parent / "results"
    output_dir.mkdir(exist_ok=True)
    pd.DataFrame(results).to_csv(output_dir / "exercise3_decisions.csv", index=False)
    pd.DataFrame(metrics).to_csv(output_dir / "exercise3_metrics.csv", index=False)
    print(f"Provider: Ollama (local); model: {MODEL}")
    print(f"Sampling: {len(right_ids)} requests per budget, {len(results)} pair decisions total")
    print(pd.DataFrame(metrics).to_string(index=False))



if __name__ == "__main__":
    main()
