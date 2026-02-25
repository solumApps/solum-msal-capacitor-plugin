import Foundation
import Capacitor
import MSAL

/**
 * SolumMsauth — iOS native implementation of MsAuthPlugin.
 *
 * This file is the production-hardened version used by the Solum app.
 *
 * Bug fixes applied vs the vanilla @recognizebv/capacitor-plugin-msauth:
 *
 *  FIX 1 — Silent-only mode (prompt='none'):
 *    When the caller passes prompt='none', we never fall back to
 *    interactive login.  If the token cache is empty or interaction
 *    is required, we reject so the TypeScript layer can decide what
 *    to do (e.g. show a UI-level login button instead of a surprise
 *    popup).
 *
 *  FIX 2 — loadCurrentAccount threshold (>= 1 instead of > 1):
 *    The original code called getCurrentAccount() (a network round-trip
 *    to AAD/B2C) when exactly ONE account was cached.  On first launch
 *    after interactive login this frequently returned nil for B2C tenants
 *    → fallback to interactive → surprise popup.
 *    Fixed: use the local keychain cache whenever >= 1 account exists.
 */
@objc(MsAuthPlugin)
public class MsAuthPlugin: CAPPlugin {

    // MARK: - login

    @objc func login(_ call: CAPPluginCall) {
        guard let context = createContextFromPluginCall(call) else {
            call.reject("Unable to create context, check logs")
            return
        }

        let scopes = call.getArray("scopes", String.self) ?? []

        // FIX 1: Track whether caller wants silent-only (prompt=none).
        //        When true, we never fall back to the interactive UI.
        var promptType: MSALPromptType = .selectAccount
        var isSilentOnly = false

        if let prompt = call.getString("prompt")?.lowercased() {
            switch prompt {
            case "select_account":
                promptType = .selectAccount
            case "login":
                promptType = .login
            case "consent":
                promptType = .consent
            case "none":
                // Caller wants silent-only — do NOT fall back to interactive
                promptType = .promptIfNecessary
                isSilentOnly = true
            case "create":
                promptType = .create
            default:
                print("[SolumMsauth] Unrecognized prompt option: \(prompt)")
            }
        }

        let completion: (MSALResult?) -> Void = { msalResult in
            guard let result = msalResult else {
                call.reject("Unable to obtain access token")
                return
            }
            call.resolve([
                "accessToken": result.accessToken,
                "idToken": result.idToken ?? "",
                "scopes": result.scopes
            ])
        }

        loadCurrentAccount(applicationContext: context) { account in
            guard let currentAccount = account else {
                // FIX 1: If silent-only and no cached account, reject instead of showing UI
                if isSilentOnly {
                    call.reject("No cached account found. Silent login not possible.")
                    return
                }
                self.acquireTokenInteractively(
                    applicationContext: context,
                    scopes: scopes,
                    promptType: promptType,
                    completion: completion
                )
                return
            }

            self.acquireTokenSilently(
                applicationContext: context,
                scopes: scopes,
                account: currentAccount,
                isSilentOnly: isSilentOnly,
                promptType: promptType,
                completion: completion
            )
        }
    }

    // MARK: - logout

    @objc func logout(_ call: CAPPluginCall) {
        guard let context = createContextFromPluginCall(call) else {
            call.reject("Unable to create context, check logs")
            return
        }
        guard let bridgeViewController = bridge?.viewController else {
            call.reject("Unable to get Capacitor bridge.viewController")
            return
        }

        loadCurrentAccount(applicationContext: context) { account in
            guard let currentAccount = account else {
                call.reject("Nothing to sign-out from.")
                return
            }

            let wvParameters = MSALWebviewParameters(authPresentationViewController: bridgeViewController)
            let signoutParameters = MSALSignoutParameters(webviewParameters: wvParameters)
            signoutParameters.signoutFromBrowser = false

            context.signout(with: currentAccount, signoutParameters: signoutParameters) { _, error in
                if let error = error {
                    print("[SolumMsauth] Logout error: \(error)")
                    call.reject("Unable to logout")
                    return
                }
                call.resolve()
            }
        }
    }

    // MARK: - logoutAll

    @objc func logoutAll(_ call: CAPPluginCall) {
        guard let context = createContextFromPluginCall(call) else {
            call.reject("Unable to create context, check logs")
            return
        }
        guard let bridgeViewController = bridge?.viewController else {
            call.reject("Unable to get Capacitor bridge.viewController")
            return
        }

        do {
            let accounts = try context.allAccounts()
            guard !accounts.isEmpty else {
                call.reject("Nothing to sign-out from.")
                return
            }

            var completed = 0
            accounts.forEach { account in
                let wvParameters = MSALWebviewParameters(authPresentationViewController: bridgeViewController)
                let signoutParameters = MSALSignoutParameters(webviewParameters: wvParameters)
                signoutParameters.signoutFromBrowser = false

                context.signout(with: account, signoutParameters: signoutParameters) { _, error in
                    completed += 1
                    if let error = error {
                        print("[SolumMsauth] LogoutAll error: \(error)")
                        call.reject("Unable to logout")
                        return
                    }
                    if completed == accounts.count {
                        call.resolve()
                    }
                }
            }
        } catch {
            print("[SolumMsauth] LogoutAll accounts error: \(error)")
            call.reject("Unable to logout")
        }
    }

    // MARK: - Context creation

    private func createContextFromPluginCall(_ call: CAPPluginCall) -> MSALPublicClientApplication? {
        guard let clientId = call.getString("clientId") else {
            call.reject("Invalid client ID specified.")
            return nil
        }
        let domainHint  = call.getString("domainHint")
        let tenant      = call.getString("tenant")
        let authorityURL = call.getString("authorityUrl")
        let authorityTypeRaw = call.getString("authorityType") ?? "AAD"

        guard ["AAD", "B2C", "CIAM"].contains(authorityTypeRaw) else {
            call.reject("authorityType must be one of 'AAD', 'B2C', or 'CIAM'")
            return nil
        }

        guard let enumAuthorityType = AuthorityType(rawValue: authorityTypeRaw.lowercased()),
              let context = createContext(
                clientId: clientId,
                domainHint: domainHint,
                tenant: tenant,
                authorityType: enumAuthorityType,
                customAuthorityURL: authorityURL
              ) else {
            call.reject("Unable to create context, check logs")
            return nil
        }
        return context
    }

    private func createContext(
        clientId: String,
        domainHint: String?,
        tenant: String?,
        authorityType: AuthorityType,
        customAuthorityURL: String?
    ) -> MSALPublicClientApplication? {
        guard let authorityURL = URL(
            string: customAuthorityURL ?? "https://login.microsoftonline.com/\(tenant ?? "common")"
        ) else {
            print("[SolumMsauth] Invalid authorityUrl or tenant specified")
            return nil
        }

        do {
            let authority: MSALAuthority
            if authorityType == .aad || authorityType == .ciam {
                authority = try MSALAADAuthority(url: authorityURL)
            } else {
                authority = try MSALB2CAuthority(url: authorityURL)
            }

            if domainHint != nil {
                print("[SolumMsauth] Warning: domain hint is not supported on iOS.")
            }

            let config = MSALPublicClientApplicationConfig(
                clientId: clientId,
                redirectUri: nil,
                authority: authority
            )
            config.knownAuthorities = [authority]
            return try MSALPublicClientApplication(configuration: config)
        } catch {
            print("[SolumMsauth] createContext error: \(error)")
            return nil
        }
    }

    // MARK: - Account loading

    typealias AccountCompletion = (MSALAccount?) -> Void

    func loadCurrentAccount(
        applicationContext: MSALPublicClientApplication,
        completion: @escaping AccountCompletion
    ) {
        // FIX 2: Use local keychain cache when >= 1 account exists.
        //        Original code used `> 1` which caused a network round-trip
        //        (getCurrentAccount) for the single-account case, frequently
        //        returning nil on B2C → surprise interactive login popup.
        do {
            let accounts = try applicationContext.allAccounts()
            if accounts.count >= 1 {
                let authorityUrl = applicationContext.configuration.authority.url
                for account in accounts {
                    if let tenants = account.tenantProfiles {
                        for tenantProfile in tenants {
                            if let tenantId = tenantProfile.tenantId,
                               authorityUrl.absoluteString.contains(tenantId) {
                                print("[SolumMsauth] Found tenant-matched cached account for: \(authorityUrl)")
                                completion(account)
                                return
                            }
                        }
                    }
                }
                // No tenant-matched account — fall back to first cached
                print("[SolumMsauth] No tenant-matched account; using first cached account.")
                completion(accounts[0])
                return
            }
        } catch {
            print("[SolumMsauth] Unable to access cached accounts: \(error)")
        }

        // Only reached when keychain has ZERO cached accounts.
        // getCurrentAccount does a network call — acceptable here since
        // the user has never logged in yet on this device.
        print("[SolumMsauth] No cached accounts in keychain. Querying getCurrentAccount...")
        let msalParams = MSALParameters()
        msalParams.completionBlockQueue = DispatchQueue.main

        applicationContext.getCurrentAccount(with: msalParams) { currentAccount, _, error in
            if let error = error {
                print("[SolumMsauth] getCurrentAccount error: \(error)")
                completion(nil)
                return
            }
            completion(currentAccount)
        }
    }

    // MARK: - Token acquisition

    func acquireTokenInteractively(
        applicationContext: MSALPublicClientApplication,
        scopes: [String],
        promptType: MSALPromptType,
        completion: @escaping (MSALResult?) -> Void
    ) {
        guard let bridgeViewController = bridge?.viewController else {
            print("[SolumMsauth] Unable to get Capacitor bridge.viewController")
            completion(nil)
            return
        }

        let wvParameters = MSALWebviewParameters(authPresentationViewController: bridgeViewController)
        let parameters   = MSALInteractiveTokenParameters(scopes: scopes, webviewParameters: wvParameters)
        parameters.promptType = promptType

        applicationContext.acquireToken(with: parameters) { result, error in
            if let error = error {
                print("[SolumMsauth] Interactive token error: \(error)")
                completion(nil)
                return
            }
            guard let result = result else {
                print("[SolumMsauth] Empty result from interactive token.")
                completion(nil)
                return
            }
            completion(result)
        }
    }

    // FIX 1: Added isSilentOnly + promptType parameters so that when
    //        silent acquisition fails with interactionRequired we can
    //        reject instead of showing the interactive UI.
    func acquireTokenSilently(
        applicationContext: MSALPublicClientApplication,
        scopes: [String],
        account: MSALAccount,
        isSilentOnly: Bool = false,
        promptType: MSALPromptType = .selectAccount,
        completion: @escaping (MSALResult?) -> Void
    ) {
        let parameters = MSALSilentTokenParameters(scopes: scopes, account: account)

        applicationContext.acquireTokenSilent(with: parameters) { result, error in
            if let error = error {
                let nsError = error as NSError

                if nsError.domain == MSALErrorDomain,
                   nsError.code == MSALError.interactionRequired.rawValue {

                    // FIX 1: If caller passed prompt='none', do NOT show UI.
                    if isSilentOnly {
                        print("[SolumMsauth] Silent token failed (interactionRequired), isSilentOnly=true — rejecting.")
                        completion(nil)
                        return
                    }

                    // Default behaviour: fall back to interactive login
                    DispatchQueue.main.async {
                        self.acquireTokenInteractively(
                            applicationContext: applicationContext,
                            scopes: scopes,
                            promptType: promptType,
                            completion: completion
                        )
                    }
                    return
                }

                print("[SolumMsauth] Silent token error: \(error)")
                completion(nil)
                return
            }

            guard let result = result else {
                print("[SolumMsauth] Empty result from silent token.")
                completion(nil)
                return
            }
            completion(result)
        }
    }

    // MARK: - URL handling (AppDelegate / SceneDelegate integration)

    @objc public static func checkAppOpen(
        url: URL,
        options: [UIApplication.OpenURLOptionsKey: Any] = [:]
    ) -> Bool {
        MSALPublicClientApplication.handleMSALResponse(
            url,
            sourceApplication: options[UIApplication.OpenURLOptionsKey.sourceApplication] as? String
        )
    }
}

// MARK: - Supporting types

enum AuthorityType: String {
    case aad
    case b2c
    case ciam
}

// MARK: - AppDelegate extension

extension UIApplicationDelegate {
    func application(
        _ app: UIApplication,
        open url: URL,
        options: [UIApplication.OpenURLOptionsKey: Any] = [:]
    ) -> Bool {
        MsAuthPlugin.checkAppOpen(url: url, options: options)
    }
}

// MARK: - SceneDelegate extension

@available(iOS 13.0, *)
extension UISceneDelegate {
    func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) {
        guard let urlContext = URLContexts.first else { return }
        MSALPublicClientApplication.handleMSALResponse(
            urlContext.url,
            sourceApplication: urlContext.options.sourceApplication
        )
    }
}
