###################################################################
# Lecture: Clinical Research Informatics - Sommer Semester 2026
#
# Exercise Sheet: Week 04 - Coding Ex. 01
# Student Name: K. Otte
# Matriculation Number: 42231337
#
# Additional information:
#   used additional packages: None
#   dataset used: Marica887_Mendy939_Jenkins714_4984c480-6442-e575-bd6a-fbfdc8ec49dd.json
###################################################################

# Please add this header to all your submissions and adapt it to your information and submission.
# The following structure is a recommendation, but not mandatory to use

import json
import os.path

from fhir.resources.R4B.bundle import Bundle
from fhir.resources.R4B.patient import Patient
from fhir.resources.R4B.condition import Condition
from datetime import datetime
import pandas as pd


# --------------------------------------------------
# Functions
# --------------------------------------------------

def load_bundle(path):
    with open(path, 'r') as h:
        pjs = json.load(h)
    bundle = Bundle.parse_obj(pjs)
    return bundle


def get_resources(bundle: Bundle):
    resources = []
    if bundle.entry is not None:
        for entry in bundle.entry:
            resources.append(entry.resource)
    return resources


def get_patient_info(bundle: Bundle):
    resources = get_resources(bundle)
    assert resources[0].resource_type == 'Patient'
    patient = Patient.parse_obj(resources[0])
    return patient


def get_conditions(bundle: Bundle):
    resources = get_resources(bundle)
    conditions = []
    for ressource in resources:
        if ressource.resource_type == 'Condition':
            conditions.append(Condition.parse_obj(ressource))
    return conditions


def get_condition_table(conditions: list) -> pd.DataFrame:
    cond_code = []
    cond_text = []
    cond_id = []
    cond_date = []

    for condition in conditions:
        cond_id.append(condition.id)
        cond_code.append(condition.code.coding[0].code)
        cond_text.append(condition.code.text)
        cond_date.append(condition.onsetDateTime.date())

    df = pd.DataFrame()
    df['ID'] = cond_id
    df['OnsetDate'] = cond_date
    df['Condition'] = cond_text
    df['Condition_Code'] = cond_code
    return df


def calc_current_age(patient: Patient):
    today = datetime.today().date()
    birthday = patient.birthDate
    age = int((today - birthday).days / 365.2425)
    return age


def get_age_of_diagnosis(conditions: list, diagnosis: str):
    conditions_df = get_condition_table(conditions)
    conditions_df["age"] = pd.to_timedelta(conditions_df["OnsetDate"] - patient.birthDate)
    conditions_df["age"] = conditions_df["age"].dt.days / 365.2425
    return conditions_df[conditions_df["Condition"] == diagnosis]["age"].values


# --------------------------------------------------
# Main Script
# --------------------------------------------------

if __name__ == "__main__":
    filepath = os.path.join("Data", "Coding Data 01", "Colton403_Rogahn59_f7dad9a0-4f10-380c-7ef9-f03643298494.json")
    bundle = load_bundle(filepath)

    # 1.1	Select and download one of the provided patient files from Moodle. This file is a FHIR bundle resource in
    # the Version v4.3.0 (R4B) and contains information on a single patient. Please import the file, provide a print
    # statement, that shows all unique top-level resource types included in the bundle.
    resources = get_resources(bundle)
    unique_resources = set([x.resource_type for x in resources])
    print(f"Unique Resource Types: {unique_resources}")

    # 1.2	From the FHIR resource, retrieve and print the patient information like their name and date of birth.
    patient = get_patient_info(bundle)
    age = calc_current_age(patient)
    print(f"Patient name: {patient.name[0].given[0]} {patient.name[0].family}")
    print(f"Birthday: {patient.birthDate}")
    print(f"Gender: {patient.gender}")

    # 2.1	From the FHIR resource, retrieve and print information about the patient’s conditions like their diagnoses.
    conditions = get_conditions(bundle)
    unique_conditions = set([x.code.text for x in conditions])
    print(f"Number of Conditions: {len(conditions)}")
    print(f"Unique Conditions: {unique_conditions}")


    # 2.2	Calculate and print the patient’s age at the time of the first asthma diagnosis.
    age_at_diagnosis = get_age_of_diagnosis(conditions, "Childhood asthma")[0]
    print(f"Current Age: {age}")
    print(f"Age of Asthma diagnosis: {age_at_diagnosis:.0f}")

    # 3.1	Select one of the included resource types (e.g. condition, observation, encounter, …). Transform this
    # resource into a pandas DataFrame, where each row represents a single resource entry. Print the first 5 rows of
    # the resulting table.
    conditions_df = get_condition_table(conditions)
    pd.set_option('display.max_columns', 50)
    pd.set_option('display.width', 500)
    print("First 5 conditions:")
    print(conditions_df.head())
