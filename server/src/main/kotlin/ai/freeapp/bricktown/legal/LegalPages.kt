package ai.freeapp.bricktown.legal

/**
 * Server-rendered legal pages. Every claim here is about what this server and
 * the template app actually do; the legal-pages skill's rule is that the copy
 * follows the code, never a generator.
 *
 * EXTENSION POINT: when the app starts storing something new (a table, an
 * upload, a third-party SDK, a manifest permission), add it to [privacy] in the
 * same change, and bump [LegalInfo.EFFECTIVE_DATE].
 */
object LegalPages {

    fun privacy(info: LegalInfo): String {
        val app = info.appName.html()
        val company = info.companyName.html()
        val email = mailto(info.contactEmail)
        return page(info, "Privacy policy", """
<h1>Privacy policy</h1>
<p class="meta">Effective ${LegalInfo.EFFECTIVE_DATE}. Covers the $app app and the server it talks to.</p>

<h2>Who is responsible</h2>
<p>$company ("we", "us") runs $app and is responsible for the personal data described here.
Write to $email with any privacy question or request. We answer within 30 days.</p>

<h2>What the app sends to our server</h2>
<ul>
<li>Your email address, name and password when you create an account, and your email address and password when you sign in.</li>
<li>Your town: its name, budget settings, towers, bricks, notes and gold days, so they are there when you sign in again and so the person you invite can see them.</li>
<li>A sign-in token with each request, which proves the request comes from your account.</li>
</ul>
<p>Nothing else leaves the app for us. It contains no analytics, advertising or crash-reporting service.</p>

<h2>What stays on your device</h2>
<ul>
<li>Your sign-in tokens, so you stay signed in.</li>
<li>A copy of your town, so the app can show it without a connection.</li>
<li>Bank statements you import. A CSV file is read on your phone and never leaves it. A PDF is sent to our server, read in memory and never stored or logged; only the rows you choose to add are saved, as bricks.</li>
</ul>
<p>Signing out removes the tokens. Uninstalling the app or clearing its data removes both.</p>

<h2>What our server stores</h2>
<ul>
<li><strong>Email address and name</strong>, to identify your account, sign you in and address you in the app.</li>
<li><strong>Your password, only as a bcrypt hash.</strong> A bcrypt hash is one-way: the password cannot be read back from it. The password itself is never stored or logged.</li>
<li><strong>Sign-in sessions.</strong> For each device you sign in on: a SHA-256 hash of its refresh token (never the token itself), when it expires, and when it was signed out.</li>
<li><strong>Your town</strong>, with its towers, bricks and notes, linked to your account. People you invite to the town can see all of it, and the town and its bricks are removed with the account of the person who owns it.</li>
<li><strong>When the account was created.</strong></li>
<li><strong>Request logs.</strong> Each request is logged with its time, method, path and result, and the web server in front of ours also records the IP address it came from. Logs hold no passwords and none of your town.</li>
</ul>

<h2>Cookies</h2>
<p>Neither these pages nor the app set cookies, and nothing tracks you across websites or apps.</p>

<h2>How long we keep it</h2>
<ul>
<li>Account data and your town: until you delete the account.</li>
<li>Sign-in sessions: at most 30 days. A session that was signed out or replaced is removed the next time you sign in, every session is removed once its 30 days are up, and all of them go with the account.</li>
<li>Request logs: as long as our host's log rotation keeps them, typically days to weeks. They are not linked to your account.</li>
</ul>

<h2>Why we may use it</h2>
<ul>
<li><strong>Contract:</strong> account data, sessions and your town, because the app cannot work for you without them.</li>
<li><strong>Legitimate interest:</strong> request logs, to keep the service secure and running.</li>
</ul>
<p>We do not sell your data or use it for advertising.</p>

<h2>Who else handles it</h2>
<p>The server and its database run with a hosting provider that stores this data for us and may not use it for anything else.
If that provider stores it outside the European Economic Area, the transfer relies on the European Commission's Standard Contractual Clauses.
No other company receives your data: there is no analytics, advertising, email or payment service in between.</p>

<h2>Deleting your account</h2>
<p>In the app, open Settings and choose Delete account. Without the app, use <a href="/account/delete">the account deletion page</a> with your email address and password.</p>
<p>Either way the deletion is immediate and permanent. Your account, email address, name, password hash, every sign-in session and every record you created are removed from the database at the same moment.
If our host keeps database backups, the deleted data disappears from them when those backups expire.</p>

<h2>Your rights</h2>
<p>You can ask for a copy of your data, have it corrected, have it deleted, restrict or object to how it is used, and receive it in a machine-readable form.
You can also complain to the data protection authority where you live. Deletion is available directly, as above; for everything else write to $email.</p>

<h2>Permissions</h2>
<p>The Android app uses the internet permission to reach this server, and asks for no other permission.</p>

<h2>Children</h2>
<p>$app is not meant for children under 16, and we do not knowingly collect their data. If you believe a child has given us data, write to $email and we delete it.</p>

<h2>Changes</h2>
<p>We publish any change to this policy on this page, with a new date at the top.</p>
""")
    }

    fun terms(info: LegalInfo): String {
        val app = info.appName.html()
        val company = info.companyName.html()
        val email = mailto(info.contactEmail)
        return page(info, "Terms of use", """
<h1>Terms of use</h1>
<p class="meta">Effective ${LegalInfo.EFFECTIVE_DATE}.</p>

<h2>Agreement</h2>
<p>These terms are an agreement between you and $company ("we", "us") about using $app and its server. By creating an account or using the app you accept them.</p>

<h2>The service</h2>
<p>$app lets you create an account and keep your town on our server, so it is there whenever you sign in and so you can share it with one other person.
The app is free unless its store listing says otherwise. Anything bought through an app store is paid to and refunded by that store under its own rules.</p>

<h2>Your account</h2>
<p>Use an email address you can read, keep your password to yourself, and tell us at $email if you think someone else has used your account.
You are responsible for what happens through your account.</p>

<h2>Acceptable use</h2>
<ul>
<li>Do not break the law with the app or store anything you have no right to store.</li>
<li>Do not try to reach other people's accounts or data.</li>
<li>Do not probe, overload or disrupt the server, or get around its security.</li>
</ul>

<h2>Your content</h2>
<p>What you create stays yours. You allow us only to store it and show it back to you, as far as running the service needs.</p>

<h2>Ending</h2>
<p>You can stop at any time by deleting your account in the app or on <a href="/account/delete">the account deletion page</a>. That removes your data as the <a href="/privacy">privacy policy</a> describes.
We may suspend or close an account that breaks these terms. If the problem can be fixed, we tell you first and give you time to fix it.</p>

<h2>Availability</h2>
<p>We work to keep the service running, but we do not promise it is always available or free of errors, and we may change or end features.</p>

<h2>Warranties</h2>
<p>The app is provided "as is", without any implied warranty that it is fit for a particular purpose, except where the law does not allow such warranties to be excluded.</p>

<h2>Liability</h2>
<p>As far as the law allows, our total liability to you is limited to what you paid us for the service in the 12 months before the claim.
Nothing here limits liability that the law does not allow to be limited.</p>

<h2>Privacy</h2>
<p>The <a href="/privacy">privacy policy</a> is part of these terms.</p>

<h2>Changes and governing law</h2>
<p>We publish changes to these terms on this page with a new date, and they never apply to the past.
These terms are governed by the law of the country where $company is established. If you are a consumer, you keep the protection of the mandatory law of the country where you live.</p>

<h2>Contact</h2>
<p>$email</p>
""")
    }

    fun deleteForm(info: LegalInfo, email: String = "", error: String? = null): String {
        val app = info.appName.html()
        val errorBlock = error?.let { """<p class="error" role="alert">${it.html()}</p>""" }.orEmpty()
        return page(info, "Delete your account", """
<h1>Delete your $app account</h1>
<p>This deletes your account and everything stored with it, immediately and permanently: your email address, name, password hash, every sign-in session and every record you created. It cannot be undone.</p>
<p>You can also do this in the app: open Settings and choose Delete account.</p>
$errorBlock
<form method="post" action="/account/delete">
<label for="email">Email address</label>
<input id="email" name="email" type="email" autocomplete="email" required value="${email.html()}">
<label for="password">Password</label>
<input id="password" name="password" type="password" autocomplete="current-password" required>
<button type="submit">Delete my account permanently</button>
</form>
""")
    }

    fun deleted(info: LegalInfo): String {
        val app = info.appName.html()
        return page(info, "Account deleted", """
<h1>Your account has been deleted</h1>
<p>$app no longer holds your account or any record that was linked to it. Devices that were signed in are signed out the next time they connect.</p>
""")
    }

    private fun page(info: LegalInfo, title: String, body: String): String {
        val app = info.appName.html()
        val templateNotice = if (info.usesTemplateValues) {
            """<p class="notice">Template values: set APP_NAME, CONTACT_EMAIL and COMPANY_NAME on the server before publishing these pages.</p>"""
        } else {
            ""
        }
        return """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${title.html()} | $app</title>
<style>
:root { color-scheme: light dark; --fg: #1d1d1f; --muted: #5f6368; --bg: #ffffff; --line: #d0d4da; --accent: #0b57d0; --danger: #b3261e; }
@media (prefers-color-scheme: dark) { :root { --fg: #e8eaed; --muted: #a8adb3; --bg: #121314; --line: #3c4043; --accent: #8ab4f8; --danger: #f2b8b5; } }
* { box-sizing: border-box; }
body { margin: 0; background: var(--bg); color: var(--fg); font: 17px/1.6 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; }
main, footer { max-width: 42rem; margin: 0 auto; padding: 1.5rem 1rem; }
h1 { font-size: 1.75rem; line-height: 1.25; margin: 0 0 .5rem; }
h2 { font-size: 1.2rem; margin: 2rem 0 .5rem; }
a { color: var(--accent); }
.meta { color: var(--muted); }
ul { padding-left: 1.25rem; }
li { margin: .35rem 0; }
form { display: grid; gap: .5rem; margin-top: 1.5rem; }
label { font-weight: 600; margin-top: .5rem; }
input { font: inherit; min-height: 48px; padding: .5rem .75rem; border: 1px solid var(--line); border-radius: 8px; background: transparent; color: inherit; width: 100%; }
button { font: inherit; font-weight: 600; min-height: 48px; margin-top: 1rem; border: 0; border-radius: 8px; background: var(--danger); color: var(--bg); cursor: pointer; }
.error { color: var(--danger); font-weight: 600; }
footer { border-top: 1px solid var(--line); color: var(--muted); font-size: .95rem; }
footer nav { display: flex; flex-wrap: wrap; gap: .25rem 1rem; }
.notice { color: var(--danger); }
</style>
</head>
<body>
<main>
$body
</main>
<footer>
<nav><a href="/privacy">Privacy</a><a href="/terms">Terms</a><a href="/account/delete">Delete account</a>${mailto(info.contactEmail)}</nav>
<p>$app</p>
$templateNotice
</footer>
</body>
</html>
"""
    }

    private fun mailto(address: String): String {
        val safe = address.html()
        return """<a href="mailto:$safe">$safe</a>"""
    }

    private fun String.html(): String = buildString(length) {
        for (c in this@html) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(c)
            }
        }
    }
}
