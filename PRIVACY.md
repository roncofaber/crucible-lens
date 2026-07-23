# Privacy Policy for Crucible Lens

**Last updated:** July 2026

Crucible Lens ("the app") is a mobile client for the Molecular Foundry's Crucible research data platform at Lawrence Berkeley National Laboratory. This policy explains what data the app accesses, how it is used, and how it is stored.

## Who this app is for

Crucible Lens is intended for Crucible users — researchers and staff at the Molecular Foundry — who already hold or can obtain a Crucible API key. It is not a general public app; it does not work without a valid Crucible account.

## Data the app accesses

### Account credentials
The app stores your Crucible API key and, if you sign in with ORCID, the API key retrieved through that sign-in flow. This key is stored locally on your device and is sent only to the Crucible API server (`crucible.lbl.gov` by default, or a custom server URL you configure) as a Bearer token to authenticate your requests. It is never sent anywhere else and is excluded from Android's automatic device backups.

### Profile information
When you sign in, the app fetches and locally caches your Crucible profile: first name, last name, email, username, and ORCID iD. This information is used only to personalize the app (e.g., showing your name and username) and is not shared with any third party.

### Camera
The app requests camera access solely to scan QR codes printed on Crucible sample and dataset labels. Camera frames are processed on-device to decode the QR code and are not stored, saved, or transmitted anywhere.

### Photos and files you choose to upload
When you add a file to a dataset, the app lets you take a new photo, pick an existing photo from your device's photo library, or choose a file from local storage. A file or photo you select this way is uploaded only when you explicitly do so, directly to the Crucible API server, and is not stored, cached, or transmitted anywhere else by the app.

### Browsing history
The app keeps a local list of recently viewed samples, datasets, and projects so you can quickly return to them. This history is stored only on your device and is never transmitted to us or anyone else.

### ORCID sign-in
Signing in with ORCID opens ORCID's own login page inside the app. Your ORCID credentials are entered directly on ORCID's page and are never seen or stored by Crucible Lens. After a successful login, the app reads the resulting page to retrieve the Crucible API key issued by the Crucible server, and discards the page content immediately afterward.

## What we do not do

- We do not use analytics, crash-reporting, or advertising services of any kind.
- We do not sell, rent, or share your data with third parties.
- We do not collect location data.
- We do not track you across other apps or websites.

## Data storage and retention

All data described above (API key, cached profile, browsing history) is stored locally on your device using standard platform storage (Android DataStore / iOS NSUserDefaults). You can clear this data at any time by signing out of the app or clearing the app's cache/data through your device's system settings. Uninstalling the app removes all locally stored data.

## Network communication

The app communicates only with:
- The Crucible API server (`crucible.lbl.gov` by default, or a custom server you configure in Settings)
- Google Cloud Storage, when uploading a file or photo you've chosen to attach to a dataset — the Crucible API server issues a temporary, direct upload link for this purpose
- The Crucible Graph Explorer web interface, when you choose to open a resource in the browser
- ORCID's own servers, during ORCID sign-in

All network communication uses HTTPS.

## Children's privacy

Crucible Lens is a professional research tool and is not directed at or intended for use by children.

## Changes to this policy

If this policy changes, the updated version will be posted at this same URL with a revised "Last updated" date.

## Contact

Questions about this policy or the app's data practices can be sent to **crucible-dev@lbl.gov**.
