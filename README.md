# IRC Chat Mod for Minecraft 1.21.x

A Fabric mod that enables cross-server IRC chat with Discord verification.

## Features

- Send IRC messages using `%irc <message>` in chat
- Receive messages from other players on different servers
- Discord OAuth verification to ensure only members of a specific Discord server can use the chat
- Client-sided mod (works across different Minecraft servers)

## Setup

### 1. Discord OAuth Application

1. Go to https://discord.com/developers/applications
2. Create a new application
3. Go to OAuth2 section
4. Add redirect URI: `https://your-worker.workers.dev/api/discord/callback`
5. Copy your Client ID and Client Secret
6. Note your Discord Server ID (right-click server → Copy Server ID)

### 2. Cloudflare Workers Deployment

1. Install Wrangler CLI: `npm install -g wrangler`
2. Login: `wrangler login`
3. Deploy the worker: `wrangler deploy`
4. Set environment variables in Cloudflare Workers dashboard:
   - `DISCORD_CLIENT_ID`: Your Discord OAuth client ID
   - `DISCORD_CLIENT_SECRET`: Your Discord OAuth client secret
   - `DISCORD_SERVER_ID`: Your Discord server ID

### 3. Mod Configuration

1. Launch Minecraft with the mod installed (this will create a config file)
2. Edit the config file at `.minecraft/config/irc-config.json`:
   - Set `workersUrl` to your Cloudflare Workers URL (e.g., `"https://your-worker.workers.dev"`)
3. Restart Minecraft or the config will reload on next use
4. Get the Discord OAuth URL by visiting: `https://your-worker.workers.dev/api/discord/authurl` (or the mod can fetch it)
5. Visit the Discord OAuth URL to authorize
6. After authorizing, you'll get a code on the callback page
7. Use `%irc link <code>` in Minecraft chat to complete verification

## Usage

**Discord Verification:**
- Get the Discord OAuth URL from: `https://your-worker.workers.dev/api/discord/authurl`
- Visit the URL to authorize with Discord
- Copy the authorization code from the callback page
- Type `%irc link <code>` in Minecraft chat to verify with Discord
- Once verified, you can use IRC chat

**IRC Chat:**
- Type `%irc <message>` in chat to send an IRC message
- Messages will display with your Discord username (not Minecraft username)
- Messages from other verified players will appear in chat with `[IRC]` prefix
- Works across different Minecraft servers

## Building

```bash
./gradlew build
```

The mod JAR will be in `build/libs/`

## Commands

- `%irc link <code>` - Verify with Discord using authorization code
- `%irc <message>` - Send a message to IRC chat (requires verification)

Configuration is done via the config file at `.minecraft/config/irc-config.json`

## Notes

- This is a client-side mod, so it works across different servers
- Messages are stored in memory on the Cloudflare Worker (consider using Durable Objects or KV for production)
- Discord tokens are stored locally in the config file (consider implementing token refresh)
- Discord client ID, secret, and server ID are stored on the Cloudflare Worker as environment variables
- IRC messages display Discord usernames, not Minecraft player names

