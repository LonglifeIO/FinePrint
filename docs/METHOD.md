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

When FinePrint has an app's store description on record, its page opens with it, above the sections:

- Their words: The app's own short description on its Google Play listing, word for word.
- The fine print: At most four of FinePrint's own lines, chosen the same way for every app.

The fine print starts with the line that set the app's tier: a flow, or the ruling or lawsuit the tier names. Then, for each place its data goes that has lines (Stays here, Used for more, Goes elsewhere, in that order), it adds the first line the tier rules would name that isn't already shown: a current practice before a past one; the company's own account, then a ruling, a report, an allegation, then a line inferred from tracker code; sensitive data first. An app without a store description on record opens with its Summary.

Each sourced line on the page ends in a small number. The numbers match the list at the end of the page, one number per document and section, in the order they first appear; to read a source, open the line's Sources row.

- Summary: In plain words, from FinePrint's reviewed record of this app. For an app without a record: No reviewed record yet: inferred from the tracker code found in this app. For an app that came with your phone and inherits its maker's lines: No reviewed record of this app yet: from its maker's privacy policy, which covers it, and the tracker code found in it.
- What it collects: Data this app takes from your phone, in plain terms.
- Where it goes: Who gets that data, and whether it's used beyond running the app.
- This applies to you because: Permissions you've actually granted that feed the above.
- What you can do: Settings that limit the flows above. FinePrint can't change anything; it shows what Android reports and lets you record what you've changed inside the app.
- Device access: Extra powers this app has registered, beyond ordinary permissions.
- On the record: What regulators and courts have said. FinePrint relays the public record; it doesn't judge.
- Also reported: Reported by journalists, researchers or breach trackers; no court or regulator has ruled on it.
- Evidence: The trackers and permissions behind the sections above.
- Sources: Every number on this page is one of these, in order.

On the record and Evidence start collapsed. On the record gives each action one line: when, who acted, what came of it, and its status. The lines fall into two groups, each newest first, followed by what others have reported:

- Ongoing: Orders still in force, cases still pending, and decisions under appeal.
- Past: Matters that have ended. One that ended more than three years ago never changes a tier.

It shows the latest three, then See all; tap a line for its details and sources. It includes actions against the company that makes the app; a line about the company rather than the app says so, for example about Google.

## Where data goes

Each line under Where it goes says what data goes to whom, and why. Lines fall into three groups, always in this order:

- Stays here: Used only to run or improve this app.
- Used for more: The same company uses it for ads, profiling, or other products.
- Goes elsewhere: Shared with, licensed to, or sold to other companies.

Each group has its own colour and icon, and its name is always written out, so colour is never the only clue. The tiers share the three colours, following the rules that set them: Flagged takes Goes elsewhere's, Caution takes Used for more's and Expected takes Stays here's; No record yet is grey, with a dashed edge.

Lines about a tracker come from FinePrint's record of it when there is one, and are otherwise inferred from its code (Auto). One record can explain several trackers in εxodus's list that come from the same company, such as Meta's Facebook Ads, Facebook Analytics and Facebook Login. When the app's own maker also owns a tracker in it, that tracker's data doesn't go elsewhere: its lines go under Used for more where its record says how the maker uses the data, and are otherwise left to the app's own record.

On the home screen, At a glance counts an app in a group when its page has at least one current line there, whether from FinePrint's record or inferred from tracker code (Auto); lines about a past practice don't count. Tracker code shows where data can go, not that it went, so the count says FinePrint lists data that "can go" to other companies. An app with no line in a group isn't said to send nothing there.

A line can also say that the app doesn't do it unless you act:

- Off by default: The app doesn't do this unless you turn a setting on.
- Only if you opt in: The app asks before it does this; it doesn't happen unless you agree.

## Status badges

Every line carries a badge that says how strong the evidence is. Tap a badge to see what it means.

- Self-disclosed: The company says so itself, in its privacy policy, labels or filings, or a law says so in its own text.
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

Four limits apply to every app:

- A claim reported by only one independent source never raises a tier. A re-report of the same story, or several reports that rest on one investigation, count as one source.
- An app without a reviewed record is never Flagged or Expected. It gets Caution at most; otherwise it shows No record yet, with what the scan found, such as no third-party trackers found · 12 permissions.
- A ruling, settlement or lawsuit counts only while it is ongoing (an order still in force, a case still pending, or a decision under appeal), or if it ended within the last three years; when the record doesn't say when it ended, its own date is used. Older ones stay under On the record, under Past, and never change a tier.
- A line that is Off by default, when the app has a setting that controls it, doesn't count toward the tier. A line that is Only if you opt in still counts.

A lawsuit or ruling concerns this app's data when FinePrint's record ties it to this app: the app's own record lists it, or a company's record names this app. On the record also shows the developer's other actions; one that doesn't name this app is marked about the company, and it doesn't change the app's tier. For an app without a record of its own, it shows the actions on record against the companies behind its trackers, marked the same way. Under each tier, one line names the finding that set it; when a ruling or lawsuit sets it, that line names the ruling or lawsuit. When a current flow the app's maker discloses and a ruling both qualify, the line names the flow.

No record yet: FinePrint hasn't reviewed this app. What it shows is inferred from the tracker code in the app: it can be rated Caution, but never Flagged or Expected.

An app that came with your phone can show its maker's lines instead. When it has no record of its own and its package name starts with a company's, such as com.google. for Google, FinePrint shows the lines that company's privacy policy gives for all its apps, and the app reads No record yet · from Google's policy. Each of those lines says: From Google's privacy policy, which covers this app. FinePrint does this only for apps that came with the phone, since an app installed later could borrow a package name. Like any app without a record, it is rated Caution at most.

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
- Changed since you reviewed: What FinePrint's record says the app does with your data, the permissions you've granted or the tracker code in the app has changed since you marked it reviewed.

Only changes to what the app does with your data count, the same ones FinePrint uses to say whether a record got better or worse: a flow beyond running the app added or removed, a flow moved to a different place, a flow turned on or off by default, a kind of data added or removed, a tracker added to or removed from the record, or a setting that limits a flow added or removed. The same holds for the records of the trackers found in the app. A reworded line, a new or changed source, a ruling or lawsuit, and the app's store description don't count; a ruling or lawsuit shows under On the record instead.

A changed app moves back up the list with one line saying what changed, and keeps the date you reviewed it. Your marks and ticks stay on this phone: they aren't part of the bundle, they aren't backed up, and they are never sent anywhere.

## Apps that came with your phone

With Show system apps on, the apps that came with your phone join the list, each marked:

- System: It came with your phone: Android lists it as a system app.

The System filter lists only those apps, grouped by maker, such as Google · 14 apps. The lines a maker's privacy policy gives for all its apps show once, at the top of its group: From Google's privacy policy, which covers these apps. The rest come last, under Other preinstalled apps: FinePrint can't tell from their package names who made these.

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
- Some apps publish a different privacy policy for each region. A record that follows one region's version says so under the summary, for example: This record follows TikTok's privacy policy for one region: United States. Where you live, a different policy may apply.

## Changes to a record

When FinePrint changes its record of an app, the page says so. The latest change shows under the summary, and On the record lists every change with its sources.

- Recent changes: The latest change to FinePrint's record of this app, and whether it's better or worse for you.
- History: Every change to FinePrint's record of this app, newest first.

Whether a change is better or worse for you isn't anyone's call: FinePrint works it out from what changed in the record's structure, never from how it's worded.

- Improved: The record shows less data collected or shared, or a new way to limit it.
- Worsened: The record shows more data collected or shared, or a way to limit it removed.
- Neutral: The wording changed; what's collected and shared didn't.

A change is Worsened when it adds a flow beyond running the app, moves a flow away from Stays here, adds a kind of data, adds a tracker, removes a setting that limits a flow, or makes a flow happen by default. It is Improved when it removes such a flow or makes it a past practice, moves a flow toward Stays here, drops a kind of data, removes a tracker, adds such a setting, or makes a flow Off by default or Only if you opt in. A change that does both is Worsened: FinePrint never offsets one against the other. Where the reviewer recorded it, a change also shows how the tier moved, such as Caution → Flagged.

## Governments

One rule applies to every government: FinePrint never ranks governments; it ranks evidence. Every country gets the same wording, in alphabetical order, with no ranking and no adjectives.

Under Where it goes, one line says where the companies that get the app's data are based:

- Jurisdictions: Where the companies that get this data are based, and the laws there that let a government demand it.

A company is based where it has its head office, or, when FinePrint has no source for that, where it's registered. Tap the line to see each country: the companies headquartered there or subject to its law, and each way its government can get the data, with the usual status badge and sources:

- Can compel: A company in a country is subject to a law that lets that country's government demand the data. FinePrint cites the law.
- Has bought: A documented government purchase of this kind of data.
- Has used: Documented government use of this kind of data, reported by at least two sources.

A law is quoted from its own text, so its badge reads Self-disclosed. A company registered or headquartered in a country is subject to its law. Three claims always stay separate: where a company is headquartered, which country's law it is subject to, and where its servers are. FinePrint shows the first two, from company records and the laws themselves; where servers are would take traffic seen from the phone, so FinePrint doesn't show it. Lines about governments don't change an app's tier.

Under each law, a line says who it binds, such as anyone who holds the data, or only certain kinds of provider; its sources are under Who it binds. FinePrint doesn't check, company by company, whether a provider qualifies.

Each law also shows when FinePrint last reviewed it. After 180 days it's marked Stale, as a record is.

A law's Sources end with its Current status: where the law stands, such as a repeal or the date it starts to apply. When a law stops the company telling you it handed your data over, the law's line or its Current status says so, and how: automatically, by a judge's order, or on the government's objection.

When FinePrint can't place every recipient, the line says: Some recipients aren't named or have no record, so FinePrint can't say where they're based. When it can't place any: FinePrint can't say where the companies that get this data are based. A country whose laws FinePrint hasn't reviewed says: FinePrint hasn't reviewed this country's laws yet.

## Reporting an error

If something is wrong or out of date, open an issue on GitHub: https://github.com/LonglifeIO/FinePrint/issues/new?template=record-error.md

Say which app, what's wrong and, if you can, where the right information is. The link opens an empty form; FinePrint never sends anything about your apps.

## Licences

- FinePrint's code: AGPL-3.0-or-later.
- FinePrint's records (bundle.json and jurisdictions.json): CC BY 4.0, attribution FinePrint.
- Tracker list (trackers.json): Open Database License (ODbL) 1.0, from the εxodus tracker database (https://reports.exodus-privacy.eu.org/); individual contents under the Database Contents License (DbCL) 1.0.
- dexlib2 (smali): Apache License 2.0.
- Icons: Material Icons, Apache License 2.0.
