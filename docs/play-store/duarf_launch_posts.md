# DUARF launch posts

## LinkedIn (attach duarf_launch.mp4)

A fake app landed on my mom's WhatsApp.
She sent it to me before opening it. I kept thinking about the day she wouldn't.

It was an .apk file, the kind dressed up as a bill or a traffic challan. Install it and it can read your OTPs. She did the right thing. But I can't sit next to every message she gets, and most of us with parents on WhatsApp can't either.

The obvious fix was an app that reads her messages and checks them. Then I hit the part that bothered me. Most ways of doing that mean her private chats leave her phone. A scam filter that ships your family's messages to a server is a new privacy problem.

So I built one that doesn't.

DUARF ("fraud", spelled backwards) checks incoming WhatsApp and SMS notifications on the phone itself:
→ No internet permission. The app has no network access of its own.
→ It flags fake APKs, OTP and UPI PIN asks, "digital arrest" threats and "your account will be blocked" pressure.
→ It explains why in plain words and says what to do next, so she learns the pattern, not just the warning.
→ English, Hindi and Hinglish, plus 9 more Indian languages in beta.

It's open source under GPL-3.0, and the full code is on GitHub (link in the first comment). A privacy promise you can't check is just a promise. Anyone can read the code and confirm there's no internet permission in the manifest. Issues and pull requests are welcome, especially from native speakers of the beta languages.

I built it the way I build most things now: an AI coding agent wrote the code, and I did the product, the reviews and the testing. The hard part wasn't the code. It was deciding what not to build: no contacts access, no cloud, no "we'll send you a report".

It will miss some scams. Real messages from beta testers are how it gets better.

DUARF is in closed testing on Google Play and launches soon. I'm looking for Android beta testers, especially people whose parents live on WhatsApp. DM me and I'll add you.

For my mom, and every parent who forwards you a message asking "is this real?"

#OpenSource #Privacy #Android

First comment: Code (GPL-3.0): https://github.com/Gourav-IITR/DUARF · Privacy policy: https://gourav-iitr.github.io/DUARF/

## X thread (attach duarf_launch.mp4 to post 1)

1/ My mom got a fake app on WhatsApp. She asked me before opening it.

I kept thinking about the day she wouldn't.

So I built DUARF ("fraud" backwards): scam warnings that run entirely on the phone. Now in closed beta 🧵

2/ The trick: an .apk file dressed up as a bill or a traffic challan. Install it and it can read your OTPs.

Its cousins: "share the OTP", "enter your UPI PIN to receive money", "your account will be blocked today".

3/ The obvious fix is an app that reads your messages and checks them.

The catch: most ways of doing that send your family's private chats to someone's server.

A scam filter shouldn't be a new privacy problem.

4/ DUARF checks incoming WhatsApp and SMS notifications on the device:
- No internet permission
- Flags fake APKs, OTP/PIN asks, "digital arrest" threats
- Explains why, in plain words
- English, Hindi, Hinglish + 9 Indian languages in beta

5/ It's open source (GPL-3.0). A privacy promise you can't check is just a promise.

Built with an AI coding agent. My job was product, review and saying no: no contacts access, no cloud, no "reports".

6/ In closed testing on Google Play, launching soon.

Looking for Android beta testers, especially if your parents live on WhatsApp. DM me and I'll add you.

Code: https://github.com/Gourav-IITR/DUARF

For my mom, and every parent who forwards you a message asking "is this real?"
