# FinePrint's privacy

Last changed: 2026-10-22.

This page is about FinePrint itself: what it keeps, what it downloads and what it asks Android for. It describes the
app as it's built from this repository's `main` branch.

## What FinePrint collects

Nothing. FinePrint has no accounts, no analytics, no crash reporting, no ads and no third-party SDKs.

FinePrint is built with AndroidX and Kotlin's libraries, as most Android apps are. It also uses dexlib2, which reads
apps' code. None of them sends anything about you or your apps.

## What stays on your phone

FinePrint reads the apps on your phone: their names, versions, permissions and code. It looks for trackers in that
code, on the phone. What it finds is never sent. It's held in memory while FinePrint runs, and saved only with a
Reviewed mark (below).

FinePrint saves a few things on your phone:

- your Reviewed marks. Each keeps the day you made it and what FinePrint found then, such as the app's trackers.
  That's how it can tell you what has changed since;
- the settings you've ticked under What you can do, with the day you ticked each;
- the knowledge bundle it last downloaded, so it works offline;
- which sections you've opened or closed, on the home and under Where it goes;
- whether you've seen the introduction.

FinePrint turns off Android's backup, and its transfer to a new phone, for all of it. When you uninstall FinePrint,
Android deletes all of it.

The preview build keeps its theme choice (System, Light or Dark) in memory only. It's gone when the preview closes.

## What FinePrint downloads

FinePrint downloads one thing: its knowledge bundle. That's three files, the same for everyone: `bundle.json`,
`trackers.json` and `jurisdictions.json`. It downloads them once each time it starts, and again when you tap Update
now in About.

It always downloads the files whole. It never asks a server about a particular app, so no server learns which apps
you have.

The test build downloads them from GitHub, at `https://github.com/LonglifeIO/FinePrint/releases/latest/download/`.
GitHub sees this download as it sees any other. It sees your phone's internet address, the time and FinePrint's
version. The request doesn't name your phone's model, its Android version or your apps. GitHub's privacy statement
says what it does with what it sees:
<https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement>.

FinePrint makes no other network request, ever.

You can check this. A debug build, made from the source code, logs each download as it happens. Android's own check
(StrictMode) also logs any other connection the app opens. Run `adb logcat -s FinePrint StrictMode` while it runs.

## Buttons that open GitHub

Some buttons open GitHub in your browser. FinePrint itself sends nothing. Your browser opens the page, and GitHub's
privacy statement applies there.

- In About: Source code, Report an error, Ask for a correction and Privacy. Their addresses hold nothing about you or
  your apps.
- On the page of an app FinePrint hasn't checked: Ask FinePrint to check this app. Its address holds the app's name
  and package, and GitHub sees them as soon as the page opens. FinePrint says so before it opens the browser.

Nothing is posted until you submit a form on GitHub. What you post there is public, under your GitHub account.

## Permissions

FinePrint asks Android for two permissions:

- `QUERY_ALL_PACKAGES`, to list the apps on your phone. Since Android 11, an app needs it to see every other app.
- `INTERNET`, to download the knowledge bundle.

Android grants both when you install FinePrint, without asking you. FinePrint reads each app's code from its
installed files, which Android lets any app read.

FinePrint asks for nothing else. It never asks for your location, contacts, files, camera or microphone. It never
reads other apps' screens.

Its manifest also names one permission that AndroidX adds to every app built with it:
`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. It's FinePrint's own. It stops other apps from signalling FinePrint's
internal parts.

A debug build also asks for local network access (`ACCESS_LOCAL_NETWORK`). It needs that to reach a bundle server on
its builder's own network. The test build doesn't ask for it.

The preview build asks for neither `QUERY_ALL_PACKAGES` nor `INTERNET`. It shows sample apps, and its bundle is built
in.

## Children

FinePrint collects nothing from anyone, so it has nothing to handle differently for children.

## Contact

Open an issue on GitHub: <https://github.com/LonglifeIO/FinePrint/issues>. What you post there is public.

For anything private: **[PLACEHOLDER: the private contact the owner names]**.

## Changes to this page

Each change to this page is dated at the top. The repository keeps every earlier version:
<https://github.com/LonglifeIO/FinePrint/commits/main/docs/privacy.md>.
