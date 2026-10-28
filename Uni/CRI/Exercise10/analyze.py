# imports
from tabulate import tabulate
import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
from scipy.stats import t as t_dist

# data paths to CSV's and create dataframes
patient_data_csv = "../Exercise7/week_6_data.csv"
patients_df = pd.read_csv(patient_data_csv)
anonymized_data_csv = pd.read_csv("hu-assignment-08-example-anonymized.csv")

# main program 
def main():
    print("Hello World")
    analyze_patient_data()
    merged_df = merge_patient_data_and_pollution_data()
    describe_data(merged_df)
    calculate_corr_pre(merged_df)
    describe_anonymized_data(anonymized_data_csv)
    calculate_corr_pre_anon(anonymized_data_csv)

# descirptive visuals/key figures for anonymized data
def describe_anonymized_data(anon_data: pd.DataFrame):
    desc_anon = anon_data.describe(include="all")
    print("\nAnonymized describe:\n", desc_anon)

    #show statistics

    cats = anon_data["Measurement_avg pm25_2024"].dropna().value_counts()

    plt.figure(figsize=(8,5))
    cats.plot(kind="bar", edgecolor="black")
    plt.title("Measurement_avg pm25_2024 anonymized")
    plt.xlabel("PM2.5-Intervall")
    plt.ylabel("Häufigkeit")

    plt.tight_layout()
    plt.show()



# descirptive visuals/key figures for unanonymized data
def describe_data(merged_df: pd.DataFrame):
    # descriptive Statistics
    pm_cols = [c for c in merged_df.columns if c.startswith("Measurement_avg pm25_")]
    merged_df["pm25_mean_2017_2024"] = merged_df[pm_cols].mean(axis=1)
    if "Measurement_avg pm25" not in merged_df.columns:
        merged_df["Measurement_avg pm25"] = merged_df["pm25_mean_2017_2024"]
    demographic_cols = ["CURRENT_AGE", "GENDER", "RACE", "ETHNICITY"]
    print(merged_df[demographic_cols].describe(include="all"))
    print(merged_df[["Measurement_avg pm25", "TOTAL_ENCOUNTER"]].describe())

    cols = [
        "CURRENT_AGE",
        "ASTHMA_REASON_ENCOUNTER",
        "EMERGENCY_ENCOUNTER",
        "Measurement_avg pm25",
    ]
    # set quartiles to filter for
    sub = merged_df[cols]
    q_low = sub.quantile(0.05)
    q_high = sub.quantile(0.95)
    # show as boxplots
    print("5th percentiles:\n", q_low)
    print("\n95th percentiles:", q_high)
    fig, axes = plt.subplots(2, 2, figsize=(10, 8))
    for ax, col in zip(axes.flatten(), cols):
        ax.boxplot(sub[col].dropna())
        ax.set_title(col)
    plt.tight_layout()

# some data for the report
def analyze_patient_data():
    average_age = patients_df["CURRENT_AGE"].mean()
    print(f"Average age of the patients is {average_age:.2f}.")
    genders = patients_df["GENDER"].value_counts()
    print(genders)
    print(f"unique States: {patients_df["STATE"].value_counts()}")
    print(f"unique cities: {patients_df['CITY'].nunique()}")

#merge data
def merge_patient_data_and_pollution_data():
    pm_cols = [c for c in patients_df.columns if c.startswith("Measurement_avg pm25_")]
    if "Measurement_avg pm25" not in patients_df.columns:
        patients_df["Measurement_avg pm25"] = patients_df[pm_cols].mean(axis=1)
    return patients_df

# calculate spearman correlation for the merged unanonymized data
def calculate_corr_pre(merged_df: pd.DataFrame):
    valid = merged_df[["ASTHMA_REASON_ENCOUNTER", "Measurement_avg pm25_2024"]].dropna()
    measurement = valid["Measurement_avg pm25_2024"]
    asthma_reason = valid["ASTHMA_REASON_ENCOUNTER"]

    # check the distribution of both variables
    fig, axes = plt.subplots(1, 2, figsize=(10,4))
    axes[0].hist(measurement, bins=20)
    axes[0].set_title("PM2.5 2021")
    axes[1].hist(asthma_reason, bins=20)
    axes[1].set_title("Asthma Reason Encounters")
    plt.tight_layout()
    plt.show()

    # check spearman-Correlation based on ranks
    r_measurement = measurement.rank(method="average")
    r_asthma_reason = asthma_reason.rank(method="average")
    # calculate pearson correlation
    r = np.corrcoef(r_measurement, r_asthma_reason)[0,1]
    print("Spearman r:", r)

    n = len(valid)
    if n < 3 or np.isnan(r):
        print("Nicht genug vollständige Beobachtungen oder Korrelation konnte nicht berechnet werden.")
        return

    # approximate t value from Spearman r
    t = r * np.sqrt((n - 2) / (1 - r**2))
    # calculate degrees of freedom
    df = n - 2
    p_value = 2 * t_dist.sf(abs(t), df)
    print("t-Wert:", t)
    print("df:", df)
    print("p-value:", p_value)
    merged_df.to_csv("merged_df_export.csv", index=False)

# calculate spearman correlation for the anonymized data
def calculate_corr_pre_anon(anon_df: pd.DataFrame):
    def parse_lower_bound(bin_label: str):
        try:
            value = bin_label.split(",")[0].strip().lstrip("[(")
            return float(value)
        except Exception:
            return np.nan

    valid = anon_df[["ASTHMA_REASON_ENCOUNTER", "Measurement_avg pm25_2024"]].dropna()
    measurement_bins = valid["Measurement_avg pm25_2024"].astype(str)
    asthma_bins = valid["ASTHMA_REASON_ENCOUNTER"].astype(str)

    measurement_categories = sorted(measurement_bins.unique(), key=parse_lower_bound)
    asthma_categories = sorted(asthma_bins.unique(), key=parse_lower_bound)

    measurement_codes = pd.Categorical(
        measurement_bins,
        categories=measurement_categories,
        ordered=True,
    ).codes
    asthma_codes = pd.Categorical(
        asthma_bins,
        categories=asthma_categories,
        ordered=True,
    ).codes

    measurement_rank = pd.Series(measurement_codes).rank(method="average")
    asthma_rank = pd.Series(asthma_codes).rank(method="average")

    r = np.corrcoef(measurement_rank, asthma_rank)[0,1]
    print("Spearman r (anonymized):", r)

    n = len(valid)

    t = r * np.sqrt((n - 2) / (1 - r**2))
    df = n - 2
    p_value = 2 * t_dist.sf(abs(t), df)
    print("t-Wert (anonymized):", t)
    print("df (anonymized):", df)
    print("p-value (anonymized):", p_value)


if __name__ == "__main__":
    main()
