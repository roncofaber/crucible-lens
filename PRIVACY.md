# Privacy Policy for Crucible Lens

**Effective:** August 11, 2026
**Last updated:** August 11, 2026

## Who provides this app

Crucible Lens is an open-source project developed by the Crucible open-source community, which welcomes external contributors. It is published and maintained by Fabrice Roncoroni, who is responsible for the app and is the point of contact for this policy. In this policy, "the developer" refers to him.

Crucible Lens is **not** an official product of, and is not endorsed, sponsored, or supported by, Lawrence Berkeley National Laboratory, the Molecular Foundry, the University of California, or the U.S. Department of Energy. The names "Crucible" and "Molecular Foundry" are used here only to identify, factually, the third-party system that this app connects to.

The app is non-commercial. It contains no advertising, no in-app purchases, and no paid features.

## What this policy covers, and what it does not

Crucible Lens is a client application. It is a wrapper around the Crucible API, which is operated by the Molecular Foundry at Lawrence Berkeley National Laboratory and is not controlled by the developer.

**This policy covers only the app itself:** what the app stores on your device, which servers it contacts, and what it sends to them.

**This policy does not cover the Crucible platform.** Once data reaches the Crucible server, how it is stored, retained, secured, shared, or deleted is determined by the organization that operates Crucible, and by whatever terms apply to your Crucible or Molecular Foundry account, not by this app or its developer. For questions about data held by Crucible, including access or deletion requests, contact the Crucible platform administrators or the Molecular Foundry user program directly. Using any other open-source Crucible clients (`nano-crucible` and Crucible Web) would place the same data on the same servers under the same terms.

## Who this app is for

Crucible Lens is intended for people who already hold, or can obtain, a Crucible API key. It is not a general-purpose consumer app and does not function without a valid Crucible account. It is a professional research tool and is not directed at children.

## What the app stores on your device

All of the following is stored locally, in the app's own private storage, using standard platform mechanisms (Android DataStore and app-private files; iOS `NSUserDefaults` and app-private files). None of it is transmitted to the developer.

- **Your Crucible API key.** Held only to authenticate your requests to the Crucible server. If you sign in with ORCID, this is the key issued by Crucible at the end of that flow.
- **Your Crucible profile**, as returned by the server: first name, last name, email, username, and ORCID iD. Used to display your identity within the app.
- **App settings**: theme, accent color, server URLs, pinned and hidden items, sync selections, grouping and result-limit preferences.
- **Recently viewed items**: a local list of samples, datasets, and projects you have opened, to facilitate navigation.
- **A cached copy of project records** you have access to, held for up to 24 hours and then discarded.

Other information the app displays about Crucible users, projects, samples, datasets, and instruments is held in memory only while the app is running, and is discarded when it exits.

**A note on device backups.** On Android, the app's stored data, including your API key, your profile, and the project cache, is excluded from cloud backup and from device-to-device transfer. On iOS, app preferences may be included in iCloud and computer backups. Those backups are governed by Apple's and Google's own privacy policies, not by this one.

## What the app sends, and to whom

The app contacts only the following:

- **The Crucible API server** (`crucible.lbl.gov` by default, or a server you configure yourself). Your API key is sent with each request as an `Authorization: Bearer` header. Anything you view, create, or edit in the app is exchanged with this server.
- **Google Cloud Storage**, when you upload a file or photo to a dataset. The Crucible server issues a temporary, direct upload link, and the app sends the file contents to that link. This is infrastructure of the Crucible platform, chosen by Crucible and not by the app.
- **ORCID**, if you use ORCID sign-in (see below).
- **The Crucible Web interface**, only when you explicitly choose to open an item in your browser. This hands the generated link to your browser.

Communication with remote servers uses HTTPS. The app does not permit unencrypted connections to remote hosts.

**The app sends no data to the developer.** There is no developer-operated server, no telemetry endpoint, and no account with the developer.

## Permissions the app requests

- **Camera.** Used solely to scan QR codes on Crucible sample and dataset labels. Camera frames are decoded on the device and are not saved or transmitted. The app does not record audio.
- **Photos and files.** Used only when you choose to attach a file to a dataset, whether by taking a photo, picking one from your library, or selecting a file. The app accesses only the specific item you select, and uploads it only when you confirm. It does not scan, index, or browse your library.

## ORCID sign-in

Choosing ORCID sign-in opens ORCID's own login page inside the app. You enter your ORCID credentials on ORCID's page. The app does not read, store, or transmit your ORCID username or password, and does not retain ORCID session cookies. After you log in, the app reads the resulting page only to extract the Crucible API key that the Crucible server issues at the end of the flow, and discards the page content immediately afterward.

## Information about other Crucible users

The app displays names, usernames, and ORCID iDs of project leads, members, owners, and join requesters, and lets you search for Crucible users by name. This information comes from the Crucible API, and is shown to you only because the Crucible server has determined that you are authorized to see it. The app does not gather this information independently, does not build profiles from it, and does not send it anywhere other than back to the Crucible server when you act on it, for example by adding a member to a project.

## What the app does not do

- It contains no analytics, crash-reporting, advertising, or attribution libraries of any kind.
- It does not sell or rent your data, and does not transmit your data to the developer or to any party other than those listed above.
- It does not collect location data.
- It does not track you across other apps or websites.
- It does not read your contacts, calendar, messages, or call history.

## Using a custom server

The app lets you point it at a Crucible API server other than the default. **If you do, your API key and everything you view or submit go to that server instead.** The developer has no control over, and accepts no responsibility for, a server you configure yourself. Only change this setting if you know and trust the operator of the server you are pointing at.

## Deleting your data

- **Signing out** clears your stored API key from the device.
- **Clearing the app's storage** through your device's system settings removes all locally stored data, including settings, history, and the project cache.
- **Uninstalling the app** removes all data the app has stored on your device.

Deleting data locally does not delete anything held by the Crucible server. For that, contact the Crucible platform administrators.

## Security

Data the app stores is kept in the app's private storage area, which the operating system isolates from other apps. Connections to remote servers use HTTPS. No system is perfectly secure, and the developer cannot guarantee the security of data once it has been transmitted to the Crucible server, or of data held on a device you control. If you believe your Crucible API key has been exposed, revoke and regenerate it through Crucible.

If you discover a security or privacy problem in the app, please report it using the contact below.

## Children's privacy

Crucible Lens is not directed at children and is not intended for use by anyone under 16. The developer does not knowingly collect personal information from children. If you believe a child has provided personal information through this app, contact the developer and it will be deleted.

## Your choices and rights

Because the app stores data only on your device, and operates no developer-controlled server, you can inspect, change, or erase everything the app holds by using the app's settings, signing out, or uninstalling it, as described above.

Rights of access, correction, erasure, restriction, portability, or objection with respect to data held by the **Crucible platform** must be exercised against the organization that operates Crucible and controls that data. The developer of this app is not that organization, cannot action such requests, and has no ability to modify or delete records on the Crucible server on your behalf. Neither can the app's open-source contributors, who write code but operate no Crucible server and hold no data.

If you are unsure where to send such a request, the Crucible project can point you to the right contact: open an issue at <https://github.com/roncofaber/crucible-lens/issues> and you will be directed to the Crucible platform administrators, who are the only party able to act on it.

## Changes to this policy

This policy is published at <https://roncofaber.github.io/crucible-lens/privacy/>. If it changes, an updated version will be posted at that URL with a revised "Last updated" date, and material changes will also be noted in the app's release notes. Because the app is open source, the full revision history of this policy is public at <https://github.com/roncofaber/crucible-lens/commits/main/PRIVACY.md>.

## Contact

Questions about this policy, or about the app's data practices, can be raised by opening an issue at <https://github.com/roncofaber/crucible-lens/issues>. Note that issues are publicly visible, so do not include personal information in one.

To report a security or privacy vulnerability privately, use GitHub's private vulnerability reporting at <https://github.com/roncofaber/crucible-lens/security/advisories/new>, or contact the developer through their GitHub profile at <https://github.com/roncofaber>.

For questions about data held by Crucible itself, including retention and deletion, contact the Crucible platform administrators. The developer cannot action those requests.