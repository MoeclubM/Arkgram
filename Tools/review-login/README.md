# ArkGram review login service

This small Telethon/aiohttp service approves Telegram QR login tokens using one
real, preconfigured review account. It does not emulate Telegram, share an auth
key with clients, read chats or accept a fixed Telegram SMS code.

## Configuration and deployment

On Debian, install the matching `python3-venv` package first. Install
`requirements.txt` into `/opt/arkgram-review/venv` and copy this directory
to `/opt/arkgram-review`. Run the supplied systemd service as `arkgram-review`.
The writable Telegram session directory is `/var/lib/arkgram-review`.
Telethon's pure Python `pyaes` dependency is distributed as source, so the workflow
builds its portable wheel in CI. Download `ArkGram-review-login-pure-wheels` from
the successful run and pass its directory as `deploy.py --wheel-dir <directory>`.
The deployer installs only wheels, including published ARM64 aiohttp wheels;
application builds and tests are not run on the workstation or deployment host.

Create `/etc/arkgram-review.env` with mode `0600`, owned by root:

```
TG_API_ID=<application API ID>
TG_API_HASH=<application API hash>
TG_PHONE=<international number, digits only>
TG_SESSION=/var/lib/arkgram-review/telegram
OWNER_PASSWORD=<separate random owner password>
REVIEW_PASSWORD=<random review access code>
PORT=22901
```

The listener binds only to loopback. Install `proxy.conf` in an existing HTTPS
virtual host's includes. Owner and reviewer credentials are intentionally
separate: reviewers can authorize a device, while only the owner can initialize
the account. Authentication protects account access and cannot be disabled;
rotate a credential by changing the environment file and restarting the service.
No extra request rate limits or alternative login success paths are added.

At `https://<host>/arkgram-review/admin/`, authenticate as `owner` and enter
`OWNER_PASSWORD`. Request a Telegram login code, enter it on this HTTPS page and
complete two-step verification if Telegram requests it. The service verifies
that the authorized account matches `TG_PHONE`; a different account is logged
out. Codes and the Telegram password are not persisted by this application.
Session keys stay on the server and must never be committed or placed in CI
artifacts. Losing/revoking the server session requires the owner to sign in again.

## Reviewer API

- `GET /healthz`: process/MTProto connection only; does not prove account login.
- `GET /api/status`: verifies that the configured account is actually signed in;
  returns `503` when owner initialization is still required.
- `POST /api/authorize`, JSON `{"token":"<base64url or tg://login?token=...>"}`:
  calls the real `auth.acceptLoginToken`. Returns the Telegram authorization hash
  on success. RPC failures return `502` with their exception type/message and
  remain visible in the journal. Invalid input and unauthorized access are errors.

API authentication: `Authorization: Bearer <REVIEW_PASSWORD>`, or HTTP Basic with
username `reviewer`. The reviewer webpage at `/arkgram-review/` uses HTTP Basic.
Browser POSTs require JSON so a cross-site HTML form cannot trigger an account
operation using cached HTTP Basic credentials. The service does not enable CORS.

Android still needs a visible reviewer login entry that exports its own token,
submits it to this API, and completes Telegram's official QR login flow (including
DC migration and any required two-step verification). Deploying this server alone
does not add this entry or prove that Google Play can review every app feature.

## Verification

Run the `ArkGram review login service` GitHub Actions workflow. It tests a real
unauthorized Telegram connection, HTTP authentication, input errors and explicit
not-ready responses. It never requests an account login code or sends messages.
Owner sign-in and real token acceptance must additionally be verified against
the deployed service after the owner initializes the account.
Pass the deployed base URL to the workflow's `deployment_url` input to verify
public HTTPS health and that owner/reviewer endpoints require credentials.

References:
- https://core.telegram.org/api/qr-login
- https://docs.telethon.dev/en/stable/
- https://github.com/mcp-telegram/mcp-telegram-cloud/blob/main/src/routes/review.tsx

The MCP project is a design reference for dedicated reviewer credentials; this
service creates real client device authorizations instead of sharing its browser
OAuth session. No approval by an application store is claimed.
