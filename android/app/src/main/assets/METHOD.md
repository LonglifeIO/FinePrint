# How to read FinePrint

FinePrint shows, for each app on your phone, what data it collects, who gets it, and whether it's used beyond running the app. Every claim it makes about an app comes from a dated source that a person has checked.

## What FinePrint is, and isn't

- It checks the apps on your phone, on your phone. Nothing about your apps, their permissions or what FinePrint finds ever leaves the device.
- It downloads its knowledge bundle whole. It never asks a server about a particular app, so no one learns which apps you have.
- It has no accounts, no analytics, no crash reporting and no ads.
- It finds tracker code by matching the app's code against tracker signatures from εxodus Privacy, plus a few of FinePrint's own. Finding the code shows the app contains a tracker; it doesn't show the tracker sending data at this moment.
- It isn't a permission manager or a virus scanner. To change what an app can do, use Android's settings; each app's page links to them.

## An app's page, section by section

Every app's page has the same sections, in the same order. A section with nothing to show is left out, except What it collects.

- Summary: In plain words, from FinePrint's reviewed record of this app. For an app without a record: No reviewed record yet: inferred from the tracker code found in this app.
- What it collects: Data this app takes from your phone, in plain terms.
- Where it goes: Who gets that data, and whether it's used beyond running the app.
- This applies to you because: Permissions you've actually granted that feed the above.
- What you can do: Settings that limit the flows above. FinePrint can't change anything; it shows what Android reports and lets you record what you've changed inside the app.
- Device access: Extra powers this app has registered, beyond ordinary permissions.
- On the record: What regulators and courts have said. FinePrint relays the public record; it doesn't judge.
- Also reported: Reported by journalists, researchers or breach trackers; no court or regulator has ruled on it.
- Evidence: The trackers and permissions behind the sections above.

On the record and Evidence start collapsed. On the record gives each action one line, newest first: when, who acted, what came of it, and its status. It shows the latest three, then See all; tap a line for its details and sources. It includes actions against the company that makes the app; a line about the company rather than the app says so, for example about Google.

## Where data goes

Each line under Where it goes says what data goes to whom, and why. Lines fall into three groups, always in this order:

- Stays here: Used only to run or improve this app.
- Used for more: The same company uses it for ads, profiling, or other products.
- Goes elsewhere: Shared with, licensed to, or sold to other companies.

Each group has its own colour and icon, and its name is always written out, so colour is never the only clue.

## Status badges

Every line carries a badge that says how strong the evidence is. Tap a badge to see what it means.

- Self-disclosed: The app's maker says so in its own privacy policy or labels.
- Reported: Reported by journalists or researchers; no court or regulator has ruled on it.
- Alleged: Claimed in a lawsuit or complaint; not proven in court.
- Adjudicated: Decided by a court or regulator, or settled.
- Auto: Inferred by FinePrint from tracker code in the app; no person has reviewed it.
- Historical: Describes a past practice, not a current one.

An alleged line always says it is not proven in court. A legal claim can also say where the case stands, such as a dismissal or an appeal, with sources of its own.

Tap Sources under a line to see every source behind it: its title, its type, its date (or, for an undated page, when FinePrint read it), its status, the exact words that support the line, and a button that opens it. The first source listed is the primary one.

## Tiers

Every app gets a tier, worked out the same way for every app:

- Flagged: Sensitive data goes to other companies by the app's own account or a ruling, or a court has ruled on, or let proceed, a case over this app's data.
- Caution: Data is used beyond running the app or goes to other companies, a lawsuit over this app's data has been filed, or the app can reach deep into the phone.
- Expected: FinePrint's reviewed record finds nothing beyond what running the app needs.

An app is Flagged if any of these is true:

- Sensitive data goes elsewhere, and the app's maker says so or a court or regulator has decided it. Sensitive data means location, health, financial, contacts, children's, biometric and precise movement data.
- A court or regulator has ruled on this app's data, or a settlement or order concerns it.
- A lawsuit over this app's data has survived a motion to dismiss: a judge has let it go ahead. It is still not proven.

Otherwise, an app gets Caution if any of these is true:

- Data is used for more, and the app's maker says so, two independent sources report it, or a court or regulator has decided it.
- Any other data goes elsewhere. That includes sensitive data with less evidence than Flagged needs, and lines FinePrint inferred from tracker code.
- A lawsuit over this app's data has been filed and hasn't yet survived a motion to dismiss.
- The app can act as an accessibility service, read your notifications, be a device administrator or run a VPN.

Otherwise, an app with a reviewed record is Expected.

Two limits apply to every app:

- A claim reported by only one independent source never raises a tier. A re-report of the same story, or several reports that rest on one investigation, count as one source.
- An app without a reviewed record is never Flagged or Expected. It gets Caution at most; otherwise it shows No record yet, with what the scan found, such as no third-party trackers found · 12 permissions.

A lawsuit or ruling concerns this app's data when FinePrint's record ties it to this app: the app's own record lists it, or a company's record names this app. On the record also shows the developer's other actions; one that doesn't name this app is marked about the company, and it doesn't change the app's tier. Under each tier, one line names the finding that set it; when a ruling or lawsuit sets it, that line names the ruling or lawsuit.

No record yet: FinePrint hasn't reviewed this app. What it shows is inferred from the tracker code in the app: it can be rated Caution, but never Flagged or Expected.

Stale: Last reviewed more than 180 days ago; it may be out of date.

## What you can do

Each app's page lists the settings that limit where its data goes. FinePrint can't change any of them. For an Android permission, it shows what Android reports, Off ✓ or On ○; tap the item to open the app's Android settings. Settings inside the app, and the advertising ID, which applies to every app, are things FinePrint can't see: each has a checkbox you tick yourself once you've changed it. A setting inside the app says what turning it off changes, quoting the app where it says, or that the app doesn't say. When FinePrint infers that a setting covers a flow its sources don't name outright, the item says so.

The line under each item says which kind it is:

- Checked automatically — Android shows this is off
- Checked automatically — Android shows this is still on
- Check this yourself — FinePrint can't see settings inside other apps.
- Check this yourself — FinePrint can't see this Android setting.

The page and the list say how many of the app's current flows your settings limit, such as 3 of 7 flows limited by your settings. Each flow counts once: a line FinePrint inferred from tracker code counts only when it adds data that the reviewed lines don't already cover. Flows that stay with the app, and past practices, aren't counted.

- Limited: A flow counts as limited when a setting you've changed applies to it: an Android permission turned off, or an in-app setting ticked. Limited doesn't mean stopped.

## Your Reviewed marks

When you've read an app's page, you can mark it reviewed at the bottom of the page. On its page, a reviewed app's badge then reads, for example, Flagged · Reviewed; in the list, the badge shows a check. It moves below the apps of the same tier you haven't reviewed. Nothing you do changes a tier.

- Reviewed: You've marked this app reviewed. The mark stays on this phone and never changes the tier.
- Changed since you reviewed: FinePrint's record, the permissions you've granted or the tracker code in the app has changed since you marked it reviewed.

A changed app moves back up the list with one line saying what changed, and keeps the date you reviewed it. Your marks and ticks stay on this phone: they aren't part of the bundle, they aren't backed up, and they are never sent anywhere.

## How records are made

- Each record starts as a draft prepared with AI-assisted research, with a source for every claim.
- A person checks every claim against its source, sets its status and wording, and approves it. Nothing reaches the bundle without that review.
- Every source carries the exact words that support the claim, and a date.
- The status follows the evidence: an app's own policy is Self-disclosed, journalism is Reported, and a lawsuit stays Alleged until a court or regulator decides it.

Records follow these rules for wording:

- A summary opens with what the app does for you and what it needs to do it.
- A claim from a privacy policy says so: "according to its privacy policy (date)".
- A legal matter gets at most one clause in a summary; the details go under On the record.
- A summary sentence about the company's past has that company's record behind it, shown under On the record.
- A privacy policy's own words are quoted, not paraphrased: "their own monetization purposes", not "make money for themselves".

## Governments

FinePrint doesn't show government access yet. When it does, one rule will apply to every government: FinePrint never ranks governments; it ranks evidence. Each line will name one way a government can get an app's data, with the usual status badge and sources:

- Can compel: a company in a country is subject to a law that lets that country's government demand the data. FinePrint cites the law.
- Has bought: a documented government purchase of this kind of data.
- Has used: documented government use of this kind of data, reported by at least two sources.

You'll be able to choose which countries, and which of these three, to highlight. Three claims always stay separate: where a company is headquartered, which country's law it is subject to, and where its servers are.

## Reporting an error

If something is wrong or out of date, open an issue on GitHub: https://github.com/LonglifeIO/FinePrint/issues/new?template=record-error.md

Say which app, what's wrong and, if you can, where the right information is. The link opens an empty form; FinePrint never sends anything about your apps.

## Licences

- FinePrint's code: AGPL-3.0-or-later.
- FinePrint's records (bundle.json): CC BY 4.0, attribution FinePrint.
- Tracker list (trackers.json): Open Database License (ODbL) 1.0, from the εxodus tracker database (https://reports.exodus-privacy.eu.org/); individual contents under the Database Contents License (DbCL) 1.0.
- dexlib2 (smali): Apache License 2.0.
- Icons: Material Icons, Apache License 2.0.
