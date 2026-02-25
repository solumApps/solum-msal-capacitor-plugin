# @solum/capacitor-msauth

[![GitHub](https://img.shields.io/badge/GitHub-solumApps%2Fsolum--msal--capacitor--plugin-blue?logo=github)](https://github.com/solumApps/solum-msal-capacitor-plugin)
[![Platform](https://img.shields.io/badge/platform-iOS%20%7C%20Android-lightgrey)]()
[![Capacitor](https://img.shields.io/badge/Capacitor-7.x-3880ff)]()

> **Drop-in replacement** for `@recognizebv/capacitor-plugin-msauth` — with the same simple setup and all **production iOS bug fixes** built in.

---

## Why this plugin?

Same simple setup as `@recognizebv/capacitor-plugin-msauth`, plus production iOS bug fixes:

| Improvement over the original plugin |
|---|
| ✅ iOS double login popup fixed: `prompt='none'` rejects silently; TS layer controls fallback |
| ✅ iOS B2C silent login fixed: uses local keychain cache for cached accounts |
| ✅ MSAL 5.10.0 (latest) with all transitive dependencies resolved |

---

## Installation

### Step 1 — Install the plugin

```bash
# From GitHub (recommended for production):
npm install github:solumApps/solum-msal-capacitor-plugin

# OR from a local path (during development):
npm install file:../solum-msal-capacitor-plugin
```

### Step 2 — Sync native projects

```bash
npx cap sync
```

That's it for Android! The plugin automatically merges `BrowserTabActivity` into your app's `AndroidManifest.xml` and resolves the MSAL Maven dependencies. Zero manual edits needed.

### Step 3 — iOS setup (4 steps, one-time per Xcode project)

> These are Xcode project settings and only need to be done once when setting up the iOS project. They are stored in the Xcode project file and don't need repeating on fresh clones.

#### 3a) Add URL scheme to `Info.plist`

In `ios/App/App/Info.plist`, add:

```xml
<key>CFBundleURLTypes</key>
<array>
    <dict>
        <key>CFBundleURLSchemes</key>
        <array>
            <string>msauth.$(PRODUCT_BUNDLE_IDENTIFIER)</string>
        </array>
    </dict>
</array>
<key>LSApplicationQueriesSchemes</key>
<array>
    <string>msauthv2</string>
    <string>msauthv3</string>
</array>
```

#### 3b) Add Keychain Sharing in Xcode

1. Open the project in Xcode
2. Select your **App target** → **Signing & Capabilities**
3. Click **+ Capability** → **Keychain Sharing**
4. Add the group: `com.microsoft.adalcache`

#### 3c) Verify `AppDelegate.swift`

If your `AppDelegate.swift` already has an `application(_:open:options:)` method, add inside it:

```swift
if MsAuthPlugin.checkAppOpen(url: url, options: options) {
    return true
}
```

If you **don't** have that method, the plugin's `UIApplicationDelegate` extension handles it automatically — no changes needed.

#### 3d) Run `pod install`

```bash
cd ios && pod install && cd ..
```

---

## Usage

The API is **100% identical** to `@recognizebv/capacitor-plugin-msauth`. Only the import path changes.

### Update your imports (2 files)

**`src/app/providers/global/global.service.ts`** — line 8:
```typescript
// Remove:
import { MsAuthPlugin } from '@recognizebv/capacitor-plugin-msauth';

// Add:
import { MsAuthPlugin } from '@solum/capacitor-msauth';
```

**`src/app/pages/server-selection/server-selection.page.ts`** — line 6:
```typescript
// Remove:
import { MsAuthPlugin } from '@recognizebv/capacitor-plugin-msauth';

// Add:
import { MsAuthPlugin } from '@solum/capacitor-msauth';
```

### All call patterns remain unchanged

```typescript
// Interactive login (shows Microsoft login UI)
const result = await MsAuthPlugin.login({
  clientId: 'your-client-id',
  tenant: 'solumb2c.onmicrosoft.com',
  scopes: ['https://solumb2c.onmicrosoft.com/api/demo.read'],
  authorityType: 'B2C',
  authorityUrl: 'https://solumb2c.b2clogin.com/solumb2c.onmicrosoft.com/b2c_1_signupsignin1',
  keyHash: 'YjJKs6ZnnuUT7eag1UhbBKej/1o='
});
console.log(result.accessToken);  // JWT access token
console.log(result.idToken);      // OIDC ID token
console.log(result.scopes);       // Granted scopes

// Silent login (uses cached token — no UI shown)
const result = await MsAuthPlugin.login({
  // ... same options ...
  prompt: 'none'  // Silent only — rejects if interaction is required
});

// Logout
await MsAuthPlugin.logout({
  clientId: 'your-client-id',
  tenant: 'solumb2c.onmicrosoft.com',
  authorityType: 'B2C',
  authorityUrl: 'https://...',
  keyHash: 'YjJKs6ZnnuUT7eag1UhbBKej/1o='
});
```

---

## Migrating from `@recognizebv/capacitor-plugin-msauth`

```bash
# 1. Remove the old plugin
npm uninstall @recognizebv/capacitor-plugin-msauth

# 2. Install this plugin
npm install github:solumApps/solum-msal-capacitor-plugin

# 3. Update import lines (see "Usage" section above)

# 4. Sync
npx cap sync

# 5. Important: Remove manual Android steps!
#    If you previously added Duo-SDK-Feed to android/build.gradle, remove it.
#    If you previously added BrowserTabActivity to AndroidManifest.xml, remove it.
#    The plugin handles these automatically now.
```

---

## API Reference

### `MsAuthPlugin.login(options): Promise<MsAuthLoginResult>`

| Option | Type | Required | Description |
|---|---|---|---|
| `clientId` | `string` | ✅ | Azure AD / B2C application (client) ID |
| `scopes` | `string[]` | ✅ | OAuth2 scopes to request |
| `tenant` | `string` | | Tenant name, e.g. `solumb2c.onmicrosoft.com` |
| `authorityType` | `'AAD' \| 'B2C' \| 'CIAM'` | | Authority type — defaults to `'AAD'` |
| `authorityUrl` | `string` | | Full custom authority URL (required for B2C) |
| `keyHash` | `string` | Android | Base64-encoded SHA1 of signing keystore |
| `prompt` | `'select_account' \| 'login' \| 'consent' \| 'none' \| 'create'` | | Login prompt behaviour |
| `domainHint` | `string` | | Domain hint (Android only) |

**Returns:**
```typescript
{
  accessToken: string;  // JWT Bearer token for API calls
  idToken: string;      // OIDC ID token
  scopes: string[];     // Granted scopes
}
```

### `MsAuthPlugin.logout(options): Promise<void>`

Signs out the current account and clears the MSAL token cache.

| Option | Type | Required | Description |
|---|---|---|---|
| `clientId` | `string` | ✅ | Application client ID |
| `tenant` | `string` | | Tenant name |
| `authorityType` | `'AAD' \| 'B2C' \| 'CIAM'` | | Authority type |
| `authorityUrl` | `string` | | Full authority URL |
| `keyHash` | `string` | Android | Signing keystore key hash |

### `MsAuthPlugin.logoutAll(options): Promise<void>`

Signs out ALL cached accounts. Same options as `logout`.

---

## Generating Your Android Key Hash

```bash
# Debug keystore (for development builds):
keytool -exportcert -alias androiddebugkey \
  -keystore ~/.android/debug.keystore \
  -storepass android | openssl sha1 -binary | openssl base64

# Release keystore (use the same keystore you sign your APK with):
keytool -exportcert -alias YOUR_KEY_ALIAS \
  -keystore /path/to/your-release.keystore \
  -storepass YOUR_STORE_PASSWORD | openssl sha1 -binary | openssl base64
```

The output (e.g. `YjJKs6ZnnuUT7eag1UhbBKej/1o=`) is what you register in the Azure portal  
**and** set as `MSAUTH_KEY_HASH` in `capacitor.config.ts`.

---

## How the Android Auto-Injection Works

The plugin uses advanced Gradle and manifest features to eliminate all manual Android setup:

1. **Activity Injection**: The plugin's `AndroidManifest.xml` uses `${applicationId}` and `pathPattern=".*"` to dynamically inject the `BrowserTabActivity` into your app without needing variables or manual edits.
2. **Maven Repository**: The plugin's `build.gradle` uses `rootProject.allprojects` to automatically push the required Azure DevOps Maven feed to the host app.

When you call `MsAuthPlugin.login()`, the JS passes the required `keyHash` via the options object, and the native bridge dynamically requests:
```
msauth://<your.package.name>/<urlEncodedKeyHash>
```

---

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Android: Auth redirect fails / activity not found | Conflicting hardcoded entry in manifest | Remove any manual `BrowserTabActivity` from your app's `AndroidManifest.xml` |
| Android: MSAL init error / config error | Invalid `authorityUrl`, `clientId`, or `keyHash` | Double-check values against Azure AD portal settings |
| iOS: Login popup appears twice | Old `@recognizebv` plugin still installed | Uninstall old plugin, clean Xcode derived data (`Cmd+Shift+K`) |
| iOS: `No cached account after re-launch` | Keychain group missing | Add `com.microsoft.adalcache` in Xcode Signing & Capabilities → Keychain Sharing |
| `UNIMPLEMENTED` error on login/logout | Objective-C bridge out of sync | Run `npx cap sync`, then clean and rebuild in Xcode |

---

## Project Structure

```
solum-msal-capacitor-plugin/
├── src/
│   ├── definitions.ts     # TypeScript interfaces (MsAuthPlugin, options, result)
│   ├── index.ts           # registerPlugin entry point
│   └── web.ts             # Web stub (throws unimplemented)
├── ios/
│   └── Plugin/
│       ├── MsAuthPlugin.swift   # Native iOS implementation (with bug fixes)
│       └── MsAuthPlugin.m       # Objective-C bridge registration
├── android/
│   ├── build.gradle             # MSAL Maven repo + plugin variable injection
│   └── src/main/
│       ├── AndroidManifest.xml  # BrowserTabActivity — auto-merged by Gradle
│       └── java/com/solum/msauth/
│           └── MsAuthPlugin.java  # Native Android implementation
├── SolumMsauth.podspec    # CocoaPods spec (declares MSAL pod dependency)
└── package.json           # Plugin manifest with capacitor.variables declaration
```

---

## License

MIT — © Solum Mobile App Team
