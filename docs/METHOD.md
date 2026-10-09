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

Under its name and tier, each page says how far FinePrint has looked at the app, in words, never a colour:

- Checked by FinePrint: On the date shown, a person looked for reported, alleged and adjudicated matters about the app and filled On the record. It never changes a tier.
- Their words only — a reviewer hasn't looked at this app yet: FinePrint has a record of what the app and its company say, but no one has looked for those matters yet.
- No record yet — these lines come from the trackers found in its code. An app that came with your phone may add its maker's policy: No record yet — these lines come from Google's policy and the trackers found in its code.

On a page that reads Their words only or No record yet, a button, Ask FinePrint to review this app, asks for a reviewer. Before anything opens, it says: This opens GitHub in your browser with the app's name and package in the address. FinePrint itself sends nothing. Nothing is posted until you submit it there, and the post is public under your GitHub account. Open GitHub then hands the address to your browser; Cancel closes it. A request never changes a tier. The address fills GitHub's review-request form by its field names: checked against GitHub's documentation on 2026-10-08, a form's text fields fill from URL parameters named by their ids, while a labels parameter would show a 404 to anyone who can't label issues, so the form applies its review-request label itself.

The home shows the same on each app in small text, such as Checked by FinePrint · 2026-10-04; its filters include Checked, Their words only and No record yet; and At a glance counts them, such as Records: 4 checked, 0 their words only, 6 no record yet.

When FinePrint has an app's store description on record, its page opens with it, above the sections:

- Their words: The app's own short description on its Google Play listing, word for word.
- The fine print: At most four of FinePrint's own lines, chosen the same way for every app.

The fine print lists FinePrint's lines in the reading order set out under Where data goes: at most four of them, from what goes to other companies and what the app's own company uses it for. What the app collects to run itself follows as one line, Also collected to run the app, which opens to those lines, each with its badge. An app without a store description on record opens with its Summary.

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

FinePrint's lines are always read in the same order, on every page:

- First: data that goes to other companies for more than running the app, such as ads, profiling, resale and government access, with sensitive data first: the same kinds the tiers treat as sensitive.
- Then: data the app's own company uses for more than running the app.
- Last: data collected to run and maintain the app, such as crash reports, usage analytics and service providers. The fine print shows these as one line, Also collected to run the app, which opens to the full list.

Within each, a current practice comes before a past one and sensitive data comes first; then the company's own account, a ruling, a report, an allegation, and a line inferred from tracker code. Each group under Where it goes lists its lines in this order; the groups keep their order and colours. The summary of an app without a record names its trackers in the same order. The order changes nothing else: tiers and Reviewed marks read the lines, not their order.

A reviewed line's purpose is the one its record states, which also sets its group. A line inferred from tracker code takes its purpose from the tracker's own record, when that says what the SDK is for with sources of its own, or else from its εxodus category: crash reporting and analytics are for running the app; advertising, ad measurement, profiling, location and identification are other companies' uses. A tracker's company counts as the app's own when it also makes the apps the tracker is usually in, or works for the app's developer as its processor, as Crashlytics does. A tracker with neither a record nor a category comes first, as What it's used for isn't recorded, under its own heading after the three groups:

- Purpose not recorded: Tracker code FinePrint has no record of and no category for, so it can't say where its data goes.

Some lines depend on a setting FinePrint can't see, such as whether the app's developer has turned on data sharing. Such a line says so, followed by: FinePrint can't see that setting. It sits in its group by what the data is for, and it never counts toward a tier, the headline, the chips or the flows you've limited.

On the home screen, At a glance's headline counts the apps whose page has at least one current line that can go to other companies for more than running the app, the first in the reading order; its chips count an app in a group when its page has at least one current line there. Lines count whether they come from FinePrint's record or are inferred from tracker code (Auto); lines about a past practice don't, and neither does a tracker whose purpose isn't recorded: it comes first in the order, but FinePrint can't say its data is used for more than running the app. Tracker code shows where data can go, not that it went, so the headline says FinePrint lists data that "can go" to other companies. An app with no line in a group isn't said to send nothing there.

A line can also say that the app doesn't do it unless you act:

- Off by default: The app doesn't do this unless you turn a setting on.
- Only if you opt in: The app asks before it does this; it doesn't happen unless you agree.

## Status badges

Every line carries a badge that says how strong the evidence is. Tap a badge to see what it means.

- Self-disclosed: The company says so itself, in its privacy policy, labels or filings, or a law says so in its own text.
- Reported: Reported by journalists or researchers; no court or regulator has ruled on it.
- Alleged: Claimed in a lawsuit or complaint; not proven in court, or, before a regulator, not yet decided.
- Adjudicated: Decided by a court or regulator, or settled.
- Auto: Inferred by FinePrint from tracker code in the app; no person has reviewed it.
- Historical: Describes a past practice, not a current one.

An alleged line always says it is not proven in court, or, when a regulator rather than a court is to decide it, that it is not yet decided. A legal claim can also say where the case stands, such as a dismissal or an appeal, with sources of its own.

Tap Sources under a line to see every source behind it: its title, its type, its date (or, for an undated page, when FinePrint read it), its status, the exact words that support the line, and a button that opens it. The first source listed is the primary one.

## Tiers

Every app gets a tier, worked out the same way for every app:

- Flagged: Sensitive data goes to other companies by the app's own account or a ruling, or a court has ruled on, or let proceed, a case over this app's data.
- Caution: Data is used beyond running the app or goes to other companies, a regulator has opened a formal proceeding over this app's data, or the app can reach deep into the phone.
- Expected: FinePrint's reviewed record finds nothing beyond what running the app needs.

An app is Flagged if any of these is true:

- Sensitive data goes elsewhere, and the app's maker says so or a court or regulator has decided it. Sensitive data means location, health, financial, contacts, children's, biometric and precise movement data.
- A court or regulator has ruled on this app's data, or a settlement or order concerns it.
- A lawsuit over this app's data has survived a motion to dismiss: a judge has let it go ahead. It is still not proven.

Otherwise, an app gets Caution if any of these is true:

- Data is used for more, and the app's maker says so, two independent sources report it, or a court or regulator has decided it.
- Any other data goes elsewhere. That includes sensitive data with less evidence than Flagged needs, and lines FinePrint inferred from tracker code.
- A regulator has opened a formal proceeding over this app's data. It is not yet decided.
- The app can act as an accessibility service, read your notifications, be a device administrator or run a VPN.

Otherwise, an app with a reviewed record is Expected.

Five limits apply to every app:

- A claim reported by only one independent source never raises a tier. A re-report of the same story, or several reports that rest on one investigation, count as one source.
- An app without a reviewed record is never Flagged or Expected. It gets Caution at most; otherwise it shows No record yet, with what the scan found, such as no third-party trackers found · 12 permissions.
- A ruling, settlement or lawsuit counts only while it is ongoing (an order still in force, a case still pending, or a decision under appeal), or if it ended within the last three years; when the record doesn't say when it ended, its own date is used. Older ones stay under On the record, under Past, and never change a tier.
- Filing alone never raises a tier: a lawsuit counts once a judge lets it go ahead, a complaint to a regulator once the regulator opens a formal proceeding, and an alleged line from the same point. Until then it is shown, but it doesn't count.
- A line that is Off by default, when the app has a setting that controls it, doesn't count toward the tier. A line that is Only if you opt in still counts.

A lawsuit or ruling concerns this app's data when FinePrint's record ties it to this app: the app's own record lists it, or a company's record names this app. On the record also shows the developer's other actions; one that doesn't name this app is marked about the company, and it doesn't change the app's tier. For an app without a record of its own, it shows the actions on record against the companies behind its trackers, marked the same way. Under each tier, one line names the finding that set it; when a ruling, a lawsuit or a regulator's proceeding sets it, that line names it. When a current flow the app's maker discloses and a ruling both qualify, the line names the flow.

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
- Changed since you reviewed: Since you marked it reviewed, something has changed in what FinePrint's record says the app does with your data, in what's on the record about it, in the permissions you've granted, or in the app's tracker code or device access.

Only changes that feed the tier or the settings count, the same ones FinePrint uses to say whether a record got better or worse: a flow beyond running the app added or removed, moved to a different place, turned on or off by default, or resting on stronger or weaker evidence (such as a report confirmed by a second independent source); a kind of data added or removed; a tracker added to or removed from the record; a setting that limits a flow added or removed; and a ruling, lawsuit, order or settlement naming the app added, removed or changed in status (such as a lawsuit surviving a motion to dismiss, or a matter closed). The same holds for the records of the trackers found in the app. On this phone, a permission you grant or take back, and tracker code or device access the app gains or loses, count too. A reworded line, another source for the same claim and the app's store description don't count.

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
- A company's record can list its changes of ownership, such as an acquisition or a merger, each with its date and sources.
- When something in a record is uncertain, such as who owns a tracker's code, the record says why, with sources.

FinePrint re-reads the pages it quotes, and privacy regulators' feeds of findings and rulings, on a schedule. When a quote is no longer on its page, the words around it change, a page moves, or a regulator publishes something about a company FinePrint has a record of, a person is told. Nothing in the app changes until a person has reviewed it and the bundle is rebuilt. Sources whose sites refuse automated checks are listed in every digest and checked by hand before each release.

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

- Improved: The record shows less data collected or shared, a new way to limit it, or a legal matter or evidence that lowered the tier.
- Worsened: The record shows more data collected or shared, a way to limit it removed, or a legal matter or evidence that raised the tier.
- Neutral: The wording changed, or a legal matter or evidence changed without moving the tier; what's collected and shared didn't.

A change is Worsened when it adds a flow beyond running the app, moves a flow away from Stays here, adds a kind of data, adds a tracker, removes a setting that limits a flow, or makes a flow happen by default. It is Improved when it removes such a flow or makes it a past practice, moves a flow toward Stays here, drops a kind of data, removes a tracker, adds such a setting, or makes a flow Off by default or Only if you opt in. A change that does both is Worsened: FinePrint never offsets one against the other. A ruling, lawsuit, order or settlement naming the app, or a flow's evidence, counts by what it did to the tier: Worsened if the tier went up, Improved if it went down, Neutral if it stayed. For such a change the reviewer records the tier before and after, and the build fails without them. Where the reviewer recorded it, a change also shows how the tier moved, such as Caution → Flagged.

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

## Before each release

FinePrint checks every release against this list, from its design brief; a release that fails one doesn't ship.

- No screen uses red as a status colour, and no screen is mostly one warning colour.
- Every coloured element also carries an icon and a word, so each screen still makes sense in greyscale; the build fails if any two of the tier and bucket colours look alike to someone with red-green colour blindness.
- No single score, grade, gauge, ring or letter appears anywhere it could be read as a verdict on an app.
- No scan, sweep or "analysing" animation that doesn't stand for real work: progress names the real step and counts it.
- No notification that creates urgency; Changed since you reviewed appears only inside the app.
- No words like danger, threat, spying, creepy, infected, risk score or unsafe in the app, its store listing or its screenshots; the build checks the app's own text.
- Every claim has a date and a source one tap away, and every Alleged line reads not proven in court, at every text size and to a screen reader.
- No suggestion or button to uninstall anything.
- Progress counts only flows you actually limited, and keeps what Android reports apart from what you ticked yourself.
- The ad tile can't be mistaken for a real ad: no Sponsored label, no tracking, no network call, and it tells screen readers it's a joke.
- Other apps' icons come from your phone when the app runs, never bundled; other apps' names only identify them.
- The store listing has no best, #1, top or free in its title, icon or developer name, and no emoji there; every store screenshot shows what the app really does.
- Store screenshots use made-up demo apps, not real brands, so no company looks judged.
- The Play data safety form and what the app says agree: FinePrint collects nothing.
- Touch targets are 48dp or larger, text sizes follow your font size, every icon-only control has a description, and the accessibility checks pass.

## Licences

- FinePrint's code: AGPL-3.0-or-later.
- FinePrint's records (bundle.json and jurisdictions.json): CC BY 4.0, attribution FinePrint.
- Tracker list (trackers.json): Open Database License (ODbL) 1.0, from the εxodus tracker database (https://reports.exodus-privacy.eu.org/); individual contents under the Database Contents License (DbCL) 1.0.
- dexlib2 (smali): Apache License 2.0.
- Icons: Material Symbols and Material Icons (Google), Apache License 2.0.
- Fonts: Atkinson Hyperlegible Next and Fraunces, SIL Open Font License 1.1.
