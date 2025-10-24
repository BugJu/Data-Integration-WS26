import subprocess
import os

def Analysis(project, domain, benchmark):
    cmd = f'sbt "runMain Benchmark file={project} domain={domain} benchmark={benchmark}"'

    # output
    try:
        # subprocess.run führt den Befehl aus und wartet auf das Ergebnis
        result = subprocess.run(cmd, timeout=60, shell=True, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

        # Die Ausgabe von sbt wird hier ausgegeben
        #print(result.stdout.decode())

    except subprocess.TimeoutExpired:
        print("Der Befehl hat das Zeitlimit überschritten!")

    except subprocess.CalledProcessError as e:
        # Fehlerausgabe
        print("Fehler beim Ausführen des Befehls:", e.stderr.decode())


# Alle Dateien und Verzeichnisse im angegebenen Verzeichnis auflisten
verzeichnis = "src/main/scala/Results"

verzeichnisse = [f for f in os.listdir(verzeichnis) if os.path.isdir(os.path.join(verzeichnis, f))]

id = 1
while(True):
    if verzeichnisse.count(f"Benchmark{id}") == 0:
        break
    else:
        id += 1

# Datei im Lesemodus öffnen
with open('Benchmark.txt', 'r') as file:
    # Dateiinhalt lesen
    inhalt = file.read()
    names = True
    project_Arrays = []
    domain_Arrays = []
    for zeile in inhalt.splitlines():
        if names:
            if zeile == "":
                names = False
            else:
                project_Arrays.append(zeile)
        else:
            domain_Arrays.append(zeile)

index = 1
for project in project_Arrays:
    for domain in domain_Arrays:
        print(f"Analyse {index}/{len(project_Arrays)*len(domain_Arrays)} ({project}, {domain})")
        index += 1
        Analysis(project, domain, f"Benchmark{id}")