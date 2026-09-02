# Soccer Manager Cloud

The cloud workspace contains the collaborative React web app and the Cloudflare Worker API. It is designed for a small, invited coaching group rather than open public registration.

## Architecture

- Workers Static Assets serves the responsive React application.
- `TeamRoom`, a SQLite-backed Durable Object created per team, stores shared soccer records, revisions, published lineups, controller leases, and WebSocket presence.
- D1 stores the small cross-team directory: password accounts, sessions, memberships, paired devices, and one-time pairing codes.
- The web app authenticates users with username/email and password. Passwords use salted PBKDF2 hashes; browser sessions use hashed random tokens in Secure, HttpOnly, SameSite cookies.
- Android uses a revocable device token obtained with a one-time pairing code. Device routes remain independently bearer-authenticated.
- Resend's free transactional-email tier sends branded team invitations from `team@soccergrowthhub.com` with replies directed to the inviting coach; invitation delivery never blocks granting team access or changes the Cloudflare Workers plan.
- The web Planner uses a direct TypeScript port of the Android lineup rules engine, including full-half keepers, fixed within-half position groups, cross-half group changes when possible, season fairness, exact-position continuity, manual group/goalie/Center Defense locks, half-specific availability, custom rotation timing, row-aware benches, extra-player slots, and drag/tap edits.

## Local Development

Requirements: Node.js 20 or newer and a Cloudflare account for deployment.

```bash
cd /Users/Shared/soccer_game_management/cloud
npm install
npm run types
npx wrangler d1 migrations apply DIRECTORY_DB --local
npm run build
npx wrangler dev
```

Open `http://localhost:8787`. The first registered account becomes the bootstrap account and can create the first team.

Run verification with:

```bash
npm run typecheck
npm test
npm run build
npx wrangler deploy --dry-run
```

## First Cloudflare Deployment

1. Authenticate and create the directory database:

```bash
npx wrangler login
npx wrangler whoami
npx wrangler d1 create soccer-game-manager-collab-directory
```

2. Replace the placeholder `database_id` in `wrangler.jsonc` with the ID returned by Cloudflare.

3. Regenerate bindings and apply the remote migration:

```bash
npm run types
npx wrangler d1 migrations apply DIRECTORY_DB --remote
```

4. Build and deploy:

```bash
npm run deploy
```

5. Visit the deployed site and create the first username/email/password account using the one-time owner setup code generated during deployment. This is the bootstrap owner account.

6. Create a free [Resend](https://resend.com/) account, verify `soccergrowthhub.com`, and add the DNS records Resend provides to the domain in Cloudflare. The free tier currently supports 3,000 transactional emails per month and 100 per day.

7. Create a sending-only Resend API key and store it as an encrypted Worker secret. Never put the key in `wrangler.jsonc` or Git:

```bash
cd /Users/Shared/soccer_game_management/cloud
npx wrangler secret put RESEND_API_KEY
```

8. Create the team and roster directly in `Roster & Schedule`, then open `Access` and invite each coach's exact email address and role. The app sends a branded invitation from `team@soccergrowthhub.com` naming the inviter, team, role, and account-activation link. Replies go to the coach who sent the invitation.

9. Each invited coach selects `Activate invite` and registers the invited email with a unique username and password. Uninvited registration is rejected after the bootstrap account exists.

10. Generate an Android pairing code from the `Access` tab only when the web team is ready to download to the tablet.

## Android Pairing

In Android, open `Setup > Cloud collaboration` and enter:

- the deployed HTTPS Worker URL
- the eight-character pairing code
- a recognizable tablet name

Choose `Download cloud team`. Android creates an internal JSON backup, downloads the cloud team and roster as a separate local team, selects it, and stores the device credential using Android Keystore encryption. Existing local teams remain unchanged. Use `Publish lineup` for a numbered shared plan and `Download for match` before leaving connectivity to reserve match control for the tablet.

## Operational Notes

- Do not commit `.dev.vars`, device tokens, session cookies, passwords, Resend API keys, or Cloudflare API tokens.
- Keep the one-time owner setup code private until the first account is registered. Only its SHA-256 hash is stored in `wrangler.jsonc`.
- Use passwords of at least 10 characters. Eight failed login attempts within 15 minutes temporarily throttle that username/email.
- A stale lineup cell produces an explicit conflict; it is never overwritten silently.
- Full lineup regeneration is an atomic, game-version-checked replacement, so a failed or stale regeneration cannot partially delete the existing draft.
- Replayed mutation IDs return their original outcome and cannot duplicate goals or assignments.
- A controller lease makes the live game read-only to browsers and other devices.
- Keep the Android JSON export workflow as the long-term portable backup. Durable Object point-in-time recovery provides short-term cloud recovery.
