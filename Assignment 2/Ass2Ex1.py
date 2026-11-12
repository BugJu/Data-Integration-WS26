import json
import time
import pandas as pd
import requests
import config


def evaluate_ground_truth(countries_list, ground_truth):
    total_evaluated = 0
    correct_capitals = 0
    correct_codes = 0
    mismatches = []

    for row in countries_list:
        country_name = str(row[0]).strip()
        city = "" if row[1] is None else str(row[1]).strip()
        code = "" if row[2] is None else str(row[2]).strip()

        if country_name in ground_truth:
            total_evaluated += 1
            gt_info = ground_truth[country_name]

            # WICHTIG: Keys in ground_truth.json sind großgeschrieben ("City" und "Code")
            expected_capital = str(gt_info.get("City", "")).strip()
            expected_code = str(gt_info.get("Code", "")).strip()

            capital_match = city.lower() == expected_capital.lower()
            code_match = code.upper() == expected_code.upper()

            if capital_match:
                correct_capitals += 1
            if code_match:
                correct_codes += 1

            if not (capital_match and code_match):
                mismatches.append({
                    "country": country_name,
                    "expected": {"City": expected_capital, "Code": expected_code},
                    "actual": {"City": city, "Code": code},
                })

    print("\n" + "=" * 45)
    print("           EVALUATION RESULTS            ")
    print("=" * 45)
    print(f"Gesamt ausgewertete Länder: {total_evaluated}")

    if total_evaluated > 0:
        cap_acc = (correct_capitals / total_evaluated) * 100
        code_acc = (correct_codes / total_evaluated) * 100
        total_acc = ((correct_capitals + correct_codes) / (total_evaluated * 2)) * 100
        print(f"Genauigkeit Hauptstädte:   {correct_capitals}/{total_evaluated} ({cap_acc:.2f}%)")
        print(f"Genauigkeit Ländercodes:   {correct_codes}/{total_evaluated} ({code_acc:.2f}%)")
        print(
            f"Gesamtgenauigkeit (Zellen): {correct_capitals + correct_codes}/{total_evaluated * 2} ({total_acc:.2f}%)")

    if mismatches:
        print(f"\nAbweichungen ({len(mismatches)} insgesamt):")
        for m in mismatches:
            print(f" - {m['country']}: Expektiert={m['expected']} | Erhalten={m['actual']}")
    print("=" * 45)


def main():
    countries_df = pd.read_csv("data/countries.csv")
    countries_df = countries_df.fillna("")

    with open("ground_truth/ground_truth.json", "r", encoding="utf-8") as f:
        ground_truth = json.load(f)

    countries_list = countries_df.to_numpy()
    more_records = True
    all_countries = {}
    limit = 100
    offset = 0
    url = "https://api.restcountries.com/countries/v5"
    headers = {"Authorization": f"Bearer {config.RESTCOUNTRIES_API_KEY}"}

    while more_records:
        params = {
            "limit": limit,
            "offset": offset,
            "response_fields": "names.common,codes.alpha_3,capitals",
        }
        response = requests.get(url, params=params, headers=headers)
        if response.status_code == 200:
            res_data = response.json().get("data", {})
            objects = res_data.get("objects", [])

            for item in objects:
                name = item.get("names", {}).get("common")
                code = item.get("codes", {}).get("alpha_3")
                capitals = item.get("capitals", [])
                capital = capitals[0].get("name") if capitals else ""
                all_countries[name] = {"capital": capital, "code": code}

            # WICHTIG: "more" steckt in "meta"
            more_records = res_data.get("meta", {}).get("more", False)
            offset += limit
            time.sleep(0.5)
        else:
            break

    # Fehlende Felder befüllen
    for i, country in enumerate(countries_list):
        country_name = country[0]
        city = country[1]
        code = country[2]

        if country_name in all_countries:
            info = all_countries[country_name]

            if city == "":
                countries_list[i][1] = info["capital"]

            if code == "":
                countries_list[i][2] = info["code"]

    # Evaluierung ausführen
    evaluate_ground_truth(countries_list, ground_truth)

    # Ergebnisse speichern
    with open("json_dumps/all_countries_filled.json", "w", encoding="utf-8") as f:
        json.dump(countries_list.tolist(), f, ensure_ascii=False, indent=2)


if __name__ == "__main__":
    main()