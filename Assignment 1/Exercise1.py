import math

import pandas as pd
from pathlib import Path
import Levenshtein

data_dir = Path(__file__).resolve().parent / "Datasets"

imdb_df = pd.read_csv(data_dir / "imdb.csv",
                      delimiter=',',
                      encoding="utf_8", )


rt_df = pd.read_csv(data_dir / "rotten_tomatoes.csv",
                    delimiter=',',
                    encoding="utf_8", )


def main():
    # exercise1(imdb_df, rt_df)
    exercise2(imdb_df, rt_df)


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
                matches.append([header1, header2, round(jaccard_sim,5)])
    print(matches)

def exercise3(df1, df2):


if __name__ == "__main__":
    main()
