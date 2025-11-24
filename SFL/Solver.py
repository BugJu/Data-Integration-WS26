def hash_two_stage(data: bytes, base=257, m1=13950851, m2=1036845127411):
    """
    Zwei-stufige Hash-Funktion
    Stufe 1: Polynomial Rolling Hash modulo m1
    Stufe 2: Quadratische Transformation modulo m2
    """
    h = 0
    pow_b = 1
    for b in data:
        h = (h + b * pow_b) % m1
        pow_b = (pow_b * base) % m1
    return (h * h + 37 * h + 61261) % m2


def find_hash_collision(target_password, max_attempts=1000000):
    """
    Sucht nach einem anderen Passwort, das denselben Hash hat.

    Strategie:
    1. Berechne den Target Hash des Original-Passworts
    2. Teste systematisch Passwörter verschiedener Längen
    3. Nutze Random Search für längere Passwörter
    """

    import string
    import random

    target_hash = hash_two_stage(target_password.encode())

    print(f"[*] Original-Passwort: {target_password}")
    print(f"[*] Target Hash: {target_hash}")
    print(f"[*] Starte Hash-Kollisions-Suche...\n")

    # Character-Set für die Suche
    charset = string.ascii_letters + string.digits + "_-!@#$%^&*()"

    # Teste verschiedene Längen
    for length in range(1, 10):
        print(f"[*] Teste Länge {length}...")

        if length <= 2:
            # Exhaustive Search für kurze Längen
            from itertools import product

            for combo in product(charset, repeat=length):
                test_str = ''.join(combo)
                test_hash = hash_two_stage(test_str.encode())

                if test_hash == target_hash and test_str != target_password:
                    return test_str, test_hash

        else:
            # Random Search für längere Passwörter
            random.seed(42 + length)

            for attempt in range(max_attempts // (length - 1)):
                test_str = ''.join(random.choice(charset) for _ in range(length))
                test_hash = hash_two_stage(test_str.encode())

                if test_hash == target_hash and test_str != target_password:
                    return test_str, test_hash

                if (attempt + 1) % 100000 == 0:
                    print(f"    {attempt + 1} versucht...", end="\r", flush=True)

            print(f"    Länge {length}: ✗")

    return None, None


def main():
    """
    Hauptprogramm zur Lösung des Operation Shadow Mirror Challenges
    """

    target_password = "7h15_p455w0rd_15_p3rf3c71y_53cur3"

    solution, solution_hash = find_hash_collision(target_password)

    if solution:
        print(f"\n[+] ✓✓✓ HASH-KOLLISION GEFUNDEN!")
        print(f"[+] Original-Passwort: {target_password}")
        print(f"[+] Kollisions-Passwort: {solution}")
        print(f"[+] Hash: {solution_hash}")

        # Verifizierung
        target_hash = hash_two_stage(target_password.encode())
        assert solution_hash == target_hash, "Hash-Verifizierung fehlgeschlagen!"

        print(f"\n[+] Sende zum Server: {solution}")
    else:
        print(f"\n[-] Keine Kollision gefunden")


if __name__ == "__main__":
    main()