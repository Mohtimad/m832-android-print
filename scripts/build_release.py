#!/usr/bin/env python3
"""Build a signed release using the private files kept outside the repository."""

import os
from pathlib import Path
import subprocess
import sys


def main() -> None:
    credentials = Path(sys.argv[1]) if len(sys.argv) > 1 else Path.home() / "Downloads/M832Print-release-credentials.txt"
    values = dict(
        line.split(": ", 1)
        for line in credentials.read_text(encoding="utf-8").splitlines()
        if ": " in line
    )
    env = os.environ.copy()
    env.update(
        M832_RELEASE_KEYSTORE=str(credentials.parent / values["Keystore file"]),
        M832_RELEASE_STORE_PASSWORD=values["Keystore password"],
        M832_RELEASE_KEY_ALIAS=values["Key alias"],
        M832_RELEASE_KEY_PASSWORD=values["Key password"],
    )
    repo = Path(__file__).resolve().parent.parent
    subprocess.run([str(repo / "gradlew"), ":app:assembleRelease"], cwd=repo, env=env, check=True)


if __name__ == "__main__":
    main()
