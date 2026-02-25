/**
 * ============================================================
 * @solum/capacitor-msauth — TypeScript Definitions
 * ============================================================
 * Drop-in replacement for @recognizebv/capacitor-plugin-msauth.
 * All parameter names are intentionally kept identical so that
 * no changes are needed in the consuming app's TypeScript code.
 */
export interface MsAuthLoginOptions {
    /**
     * Azure AD / B2C Application (client) ID.
     */
    clientId: string;
    /**
     * Tenant name or ID, e.g. "solumb2c.onmicrosoft.com".
     */
    tenant?: string;
    /**
     * OAuth2 scopes to request access for.
     */
    scopes: string[];
    /**
     * Authority type: 'AAD', 'B2C', or 'CIAM'.
     * Defaults to 'AAD'.
     */
    authorityType?: 'AAD' | 'B2C' | 'CIAM';
    /**
     * Full custom authority URL. Required for B2C.
     * e.g. "https://solumb2c.b2clogin.com/solumb2c.onmicrosoft.com/b2c_1_signupsignin1"
     */
    authorityUrl?: string;
    /**
     * Android keystore key hash (Base64-encoded SHA1).
     * Required for Android to register the redirect URI.
     */
    keyHash?: string;
    /**
     * Domain hint passed to the authority (iOS not supported).
     */
    domainHint?: string;
    /**
     * Prompt behaviour:
     *  - "select_account" (default) – show account picker
     *  - "login"                    – force re-login
     *  - "consent"                  – force consent screen
     *  - "none"                     – silent-only, rejects if interaction needed
     *  - "create"                   – create account flow
     */
    prompt?: 'select_account' | 'login' | 'consent' | 'none' | 'create';
}
export interface MsAuthLogoutOptions {
    clientId: string;
    tenant?: string;
    authorityType?: 'AAD' | 'B2C' | 'CIAM';
    authorityUrl?: string;
    keyHash?: string;
    domainHint?: string;
}
export interface MsAuthLoginResult {
    /** OAuth2 access token (JWT). */
    accessToken: string;
    /** OIDC ID token (JWT). */
    idToken: string;
    /** Granted scopes. */
    scopes: string[];
}
export interface MsAuthPlugin {
    /**
     * Perform an interactive (or silent) login and obtain tokens.
     *
     * When `prompt = 'none'` the plugin attempts a silent token
     * acquisition only. If the token cache is empty or interaction
     * is required, the call rejects — giving the caller a chance to
     * retry with interaction explicitly (avoids the double-popup bug).
     */
    login(options: MsAuthLoginOptions): Promise<MsAuthLoginResult>;
    /**
     * Sign the current user out and clear the MSAL cache.
     */
    logout(options: MsAuthLogoutOptions): Promise<void>;
    /**
     * Sign ALL cached accounts out.
     */
    logoutAll(options: MsAuthLogoutOptions): Promise<void>;
}
