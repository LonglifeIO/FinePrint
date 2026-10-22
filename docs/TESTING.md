# Testing FinePrint

Last changed: 2026-10-22.

This page is for invited testers. It says how to install FinePrint, what it does and doesn't do, and what to send
back. Thank you for trying it.

## Install

The test build is an APK file that you install yourself, outside Google Play.

1. On your Android phone, open the release page: **[PLACEHOLDER: the release URL, once the signed build is
   published]**.
2. Download the APK file.
3. Open it. Android asks whether your browser, or your Files app, may install apps. Allow it for this install.
4. Google Play Protect may warn you before it installs FinePrint, because it doesn't know the app yet. Expect that
   until FinePrint is listed on Google Play. If it offers to scan the app first, you can let it.

FinePrint has no icon of its own yet, so your phone shows Android's default icon for it.

The first time you open it, FinePrint shows a three-page introduction. Then it lists your apps. While it checks their
code, the top of the list says "Checking app code for trackers", with a count.

## What FinePrint does

- It lists the apps on your phone and reads their code, on the phone, for trackers it knows.
- For each app, it shows what data it collects, who gets it, and whether it's used beyond running the app.
- Each claim cites a dated source and quotes the words that support it.
- Each time it starts, it downloads its knowledge bundle, under 1 MB. It downloads it whole, so no server learns which
  apps you have.

## What it doesn't do

- It doesn't watch what your apps send. Tracker code shows where data can go, not that it went.
- It doesn't change your settings. What you can do shows the settings that limit where data goes, and you change
  them yourself.
- It doesn't send anything about you or your apps. [privacy.md](privacy.md) says what it keeps and what it downloads.
- It isn't legal advice, and it never suggests uninstalling an app.

## Known limits

- Four apps are Checked by FinePrint so far: Facebook, Google Maps, Life360 and TikTok.
- Most of your apps will read Not checked yet. What FinePrint shows for them comes from the trackers in their code.
  Those claims carry the badge Auto: no person has checked them.
- An app FinePrint hasn't checked is never Flagged or Expected; at most it's Caution.
- On a small phone, TikTok's page opens with a long header: the reason for its tier runs to three lines.
- Apps that came with your phone aren't listed until you tick Show system apps, in the menu (⋮).

## What to send back

Send what FinePrint shows, not what's on your phone:

- how long the first check took, from opening FinePrint until "Checking app code for trackers" finished;
- how many apps FinePrint lists: At a glance says, such as "For 5 of your 9 apps";
- the app version and the knowledge bundle's, both in About FinePrint (in the menu, ⋮);
- your phone's model and Android version;
- anything that's wrong, unclear or hard to read, and which screen it's on.

Never send a list of your apps. The home screen shows them by name, so crop a screenshot of it, or describe it
instead. A screenshot of one app's page shows that you have that app: send it only if you're happy to.

Where to send it:

- On GitHub, where what you post is public. Report an error, in About, is for something FinePrint says about an app.
  For anything else, open an issue: <https://github.com/LonglifeIO/FinePrint/issues/new/choose>.
- Privately: **[PLACEHOLDER: the private channel the owner names]**.

## Uninstall

Uninstall FinePrint as you would any app: in Settings, open Apps, then FinePrint, then Uninstall.

Android deletes everything FinePrint saved. That includes your Reviewed marks and ticks. FinePrint keeps them out of
Android's backup, so they don't come back if you install it again.
