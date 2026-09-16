import sys
from datetime import datetime

# This script checks that the submission has a timestamp between start and end.
#
# It is strongly recommended to be conservative with initially rejecting submissions.
# A human should check that these submissions are actually invalid, one might have
# a mistake in the validation script.
#
# This script only rejects truly invalid submissions (more than one line, has whitespace)
# while out of range timestamps are just printed to stdout (the print could be replaced with a call to submit).

start = datetime(2026, 4, 29, 12, 0, 0)
end = datetime(2026, 5, 15, 6, 0, 0)

def eprint(*args, **kwargs):
    print(*args, file=sys.stderr, **kwargs)

def submit(points: float, message: str):
    eprint(f"{points} - {message}")
    sys.exit(0)

def validate_text(text: str):
    if len(text.strip().splitlines()) != 1:
        submit(0.0, "Die Ausgabe sollte eine Zeile der Form '<timestamp>' sein")
        return

    parts: list[str] = text.strip().split(" ")

    if len(parts) != 1:
        submit(0.0, "Die Ausgabe sollte eine Zeile der Form '<timestamp>' sein")
        return

    timestamp = parts[0]

    try:
        timestamp = float(timestamp)
    except ValueError:
        return

    if timestamp < start.timestamp() * 1000.0 or timestamp > end.timestamp() * 1000.0:
        print(
            "error: Timestamp out of range: "
            + str(timestamp)
            + " vs "
            + str(start.timestamp() * 1000.0)
            + " vs "
            + str(end.timestamp() * 1000.0)
        )
        return

    submit(2.0, "Super gemacht :)")



text = sys.stdin.read()
validate_text(text)
