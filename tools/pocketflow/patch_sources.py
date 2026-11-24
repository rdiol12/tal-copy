#!/usr/bin/env python3
"""Copies the PocketFlow Java sources that are converted for iOS into a work directory,
applying the few edits that remove Android-only pieces (Google sign-in for Drive).

    patch_sources.py <upstream java dir> <work dir> <Name>...

Every edit must match exactly once, so a new upstream version that moves the code fails loudly."""
import re, sys, pathlib

# (file, regular expression, replacement)
EDITS = [
    # The Google Drive card needs Google Play services; manual file backup stays.
    ("MainActivity", r"\n\s*googleDriveCard\(\);", "\n"),
    ("MainActivity", r"\n\s*int availability=com\.google\.android\.gms\.common\.GoogleApiAvailability[^\n]*\n[^\n]*GoogleApiAvailability\.getInstance\(\)\.getErrorDialog[^\n]*", "\n"),
    ("MainActivity", r"\n\s*if\(result\.resolution!=null\)\{\n[^\n]*\n[^\n]*startIntentSenderForResult[^\n]*\n\s*return;\n\s*\}", "\n"),
    ("MainActivity", r"String token=DriveTask\.token\(com\.google\.android\.gms\.auth\.api\.identity\.Identity\.getAuthorizationClient\(this\)\.getAuthorizationResultFromIntent\(data\)\);startDrive\(action,id,token,generation\);", "startDrive(action,id,null,generation);"),
    # Reminders are scheduled through the platform hook instead of Android's JobScheduler.
    ("MainActivity", r"\(\(android\.app\.job\.JobScheduler\)getSystemService\(JOB_SCHEDULER_SERVICE\)\)\.cancel\(7002\);", "ReminderJob.cancel(this);"),
    # The reader look-alike always decodes UTF-8 and takes no JDK charset.
    ("ExchangeRates", r"new InputStreamReader\(connection\.getInputStream\(\),StandardCharsets\.UTF_8\)", "new InputStreamReader(connection.getInputStream())"),
]

def main():
    source, work, names = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2]), sys.argv[3:]
    work.mkdir(parents=True, exist_ok=True)
    for name in names:
        text = (source / f"{name}.java").read_text(encoding="utf-8")
        for target, pattern, replacement in EDITS:
            if target != name: continue
            text, count = re.subn(pattern, lambda _: replacement, text)
            if count != 1: sys.exit(f"{name}.java: expected exactly one match for {pattern!r}, found {count}")
        (work / f"{name}.java").write_text(text, encoding="utf-8")

if __name__ == "__main__": main()
