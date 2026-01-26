import requests
import json
url = "https://www.inberlinwohnen.de/wohnungsfinder?q=eyJpdiI6IjJJMmtnM2htUjRRVldvTDB6Tkl6Rmc9PSIsInZhbHVlIjoiT3E3QVNqOUJZcktyWENPWjViTUtuZEhrT2hvMGJZSi9MU3VKTHdQYjJpYlFaa3dPc1Q0cXZPczBoOFVmRWx1S0VYc3hkc1lNdjMzdlFza056dFd1VDNYY1lQSndZTEdnZXI5UGV5OTk0bUUyS0duVnUvdDlSak12QUUxck1UaVVjYnNnU3JuQTd6VTFtbWQ4YWVEUE81dnJzMmFZUTZDSjVBL1Z5NWE0aGMwdnArdWsvWU1mMjRUOS9WN2trM1k3TXRvNi92bnhoTVo0ejV2NEdadUswN3ZEK2poL1lUWXdMQ0s5VzNXYXJlTE1mYjRNVTJ5YXdlUm1wVHE4VXZPempTWTVOU2lZTVl3YWhQOUlNSUs4dWoyajZMNGVHRlE2TElwY1V6a0JHR3ZZbndBRllMMFRBMWRrTm4rOWIvU1ZRdE5HMnQzeC9Ya2pNdlN3L1E3M2w0OUVCUW15ZGNpWGxGVVFGL1F5MktjZHlPb29XaThrSjRCcWh1ZlRTaC9nbjBqeTRsdjRLQkZHYVJMZUpGSXF3M3NpUWxDdmpCeDFGVUg1SHJLN2JhcnFHblFuVzFjSzM1T25qNWppRHFkayIsIm1hYyI6IjA1YzIyMjExNWU0MzRhODYwMjZjZGIxNGQyMWI5ZTQxMWEwOGNjM2JlNTQxZjY1M2Q2MTEyMjVhOTZlNGI2NTQiLCJ0YWciOiIifQ%3D%3D"

def fix_sets(obj):
    """Sets → Lists für JSON"""
    if isinstance(obj, set):
        return list(obj)
    elif isinstance(obj, dict):
        return {k: fix_sets(v) for k, v in obj.items()}
    elif isinstance(obj, list):
        return [fix_sets(x) for x in obj]
    return obj


def scrape():
    session = requests.Session()
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36",
        "Referer": "https://www.inberlinwohnen.de/wohnungsfinder?q=eyJpdiI6IjJJMmtnM2htUjRRVldvTDB6Tkl6Rmc9PSIsInZhbHVlIjoiT3E3QVNqOUJZcktyWENPWjViTUtuZEhrT2hvMGJZSi9MU3VKTHdQYjJpYlFaa3dPc1Q0cXZPczBoOFVmRWx1S0VYc3hkc1lNdjMzdlFza056dFd1VDNYY1lQSndZTEdnZXI5UGV5OTk0bUUyS0duVnUvdDlSak12QUUxck1UaVVjYnNnU3JuQTd6VTFtbWQ4YWVEUE81dnJzMmFZUTZDSjVBL1Z5NWE0aGMwdnArdWsvWU1mMjRUOS9WN2trM1k3TXRvNi92bnhoTVo0ejV2NEdadUswN3ZEK2poL1lUWXdMQ0s5VzNXYXJlTE1mYjRNVTJ5YXdlUm1wVHE4VXZPempTWTVOU2lZTVl3YWhQOUlNSUs4dWoyajZMNGVHRlE2TElwY1V6a0JHR3ZZbndBRllMMFRBMWRrTm4rOWIvU1ZRdE5HMnQzeC9Ya2pNdlN3L1E3M2w0OUVCUW15ZGNpWGxGVVFGL1F5MktjZHlPb29XaThrSjRCcWh1ZlRTaC9nbjBqeTRsdjRLQkZHYVJMZUpGSXF3M3NpUWxDdmpCeDFGVUg1SHJLN2JhcnFHblFuVzFjSzM1T25qNWppRHFkayIsIm1hYyI6IjA1YzIyMjExNWU0MzRhODYwMjZjZGIxNGQyMWI5ZTQxMWEwOGNjM2JlNTQxZjY1M2Q2MTEyMjVhOTZlNGI2NTQiLCJ0YWciOiIifQ%3D%3D",
        # deine URL
        "X-Livewire": "true",
        "X-Requested-With": "XMLHttpRequest",
        "Content-Type": "application/json",
        "Accept": "*/*",
        "Accept-Language": "de-DE,de;q=0.9,en-DE;q=0.8,en;q=0.7,en-US;q=0.6"
    }

    session.headers.update(headers)

    # 1. Grundseite für Session/Cookies
    session.get("https://www.inberlinwohnen.de/wohnungsfinder")

    # 2. Livewire POST mit exakter Payload aus DevTools
    url = "https://www.inberlinwohnen.de/livewire/update"
    payload = {  # ← 1:1 aus "Request Payload" kopieren!
        "fingerprint": {...},
        "serverMemo": {...},
        "updates": [...]
    }

    response = session.post(url, json=fix_sets(payload))
    print(response.status_code)
    print(response.text[:2000])  # Hier kommen die echten Daten!

if __name__ == "__main__":
    scrape()