package com.solum.msauth;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.microsoft.identity.client.AcquireTokenParameters;
import com.microsoft.identity.client.AcquireTokenSilentParameters;
import com.microsoft.identity.client.AuthenticationCallback;
import com.microsoft.identity.client.IAccount;
import com.microsoft.identity.client.IAuthenticationResult;
import com.microsoft.identity.client.IMultipleAccountPublicClientApplication;
import com.microsoft.identity.client.IPublicClientApplication;
import com.microsoft.identity.client.Prompt;
import com.microsoft.identity.client.PublicClientApplication;
import com.microsoft.identity.client.exception.MsalException;
import com.microsoft.identity.client.exception.MsalUiRequiredException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * SolumMsauth — Android native implementation of MsAuthPlugin.
 *
 * Responsibilities:
 *  1. Build an MSAL PublicClientApplication config dynamically from the
 *     options passed by the JavaScript layer (clientId, tenant, authority
 *     URL, scopes, etc.).
 *  2. Perform interactive or silent token acquisition.
 *  3. Handle logout for multi-account scenarios.
 */
@CapacitorPlugin(name = "MsAuthPlugin")
public class MsAuthPlugin extends Plugin {

    private static final String TAG = "SolumMsauth";

    // ---------------------------------------------------------------
    // login
    // ---------------------------------------------------------------

    @PluginMethod
    public void login(PluginCall call) {
        String clientId = call.getString("clientId");
        if (clientId == null || clientId.isEmpty()) {
            call.reject("clientId is required");
            return;
        }

        String tenant        = call.getString("tenant", "common");
        String authorityType = call.getString("authorityType", "AAD");
        String authorityUrl  = call.getString("authorityUrl");
        String keyHash       = call.getString("keyHash", "");
        String promptStr     = call.getString("prompt", "select_account");

        JSArray scopesArr = call.getArray("scopes");
        List<String> scopes = new ArrayList<>();
        if (scopesArr != null) {
            try {
                for (int i = 0; i < scopesArr.length(); i++) {
                    scopes.add(scopesArr.getString(i));
                }
            } catch (JSONException e) {
                call.reject("Invalid scopes array");
                return;
            }
        }

        String configJson;
        try {
            configJson = buildMsalConfig(clientId, tenant, authorityType, authorityUrl, keyHash);
        } catch (Exception e) {
            Log.e(TAG, "Failed to build MSAL config", e);
            call.reject("Failed to build MSAL config: " + e.getMessage());
            return;
        }

        File configFile;
        try {
            configFile = writeTempConfig(configJson);
        } catch (IOException e) {
            Log.e(TAG, "Failed to write MSAL config file", e);
            call.reject("Failed to write MSAL config: " + e.getMessage());
            return;
        }

        Prompt prompt = resolvePrompt(promptStr);
        boolean isSilentOnly = "none".equalsIgnoreCase(promptStr);

        Activity activity = getActivity();
        Context context   = getContext();

        PublicClientApplication.createMultipleAccountPublicClientApplication(
            context,
            configFile,
            new IPublicClientApplication.ApplicationCreatedListener() {
                @Override
                public void onCreated(IPublicClientApplication application) {
                    IMultipleAccountPublicClientApplication multiApp =
                        (IMultipleAccountPublicClientApplication) application;
                    performMultiAccountLogin(call, multiApp, scopes, prompt, isSilentOnly, activity);
                }

                @Override
                public void onError(MsalException exception) {
                    Log.e(TAG, "MSAL application creation error", exception);
                    call.reject("MSAL init error: " + exception.getMessage());
                }
            }
        );
    }

    // ---------------------------------------------------------------
    // logout
    // ---------------------------------------------------------------

    @PluginMethod
    public void logout(PluginCall call) {
        String clientId = call.getString("clientId");
        if (clientId == null || clientId.isEmpty()) {
            call.reject("clientId is required");
            return;
        }
        String tenant        = call.getString("tenant", "common");
        String authorityType = call.getString("authorityType", "AAD");
        String authorityUrl  = call.getString("authorityUrl");
        String keyHash       = call.getString("keyHash", "");

        String configJson;
        try {
            configJson = buildMsalConfig(clientId, tenant, authorityType, authorityUrl, keyHash);
        } catch (Exception e) {
            call.reject("Failed to build MSAL config: " + e.getMessage());
            return;
        }

        File configFile;
        try {
            configFile = writeTempConfig(configJson);
        } catch (IOException e) {
            call.reject("Failed to write MSAL config: " + e.getMessage());
            return;
        }

        Context context = getContext();

        PublicClientApplication.createMultipleAccountPublicClientApplication(
            context,
            configFile,
            new IPublicClientApplication.ApplicationCreatedListener() {
                @Override
                public void onCreated(IPublicClientApplication application) {
                    IMultipleAccountPublicClientApplication multiApp =
                        (IMultipleAccountPublicClientApplication) application;
                    try {
                        List<IAccount> accounts = multiApp.getAccounts();
                        if (accounts.isEmpty()) {
                            call.reject("Nothing to sign out from.");
                            return;
                        }
                        multiApp.removeAccount(accounts.get(0), new IMultipleAccountPublicClientApplication.RemoveAccountCallback() {
                            @Override
                            public void onRemoved() {
                                call.resolve();
                            }

                            @Override
                            public void onError(MsalException exception) {
                                Log.e(TAG, "Logout error", exception);
                                call.reject("Logout failed: " + exception.getMessage());
                            }
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "getAccounts error during logout", e);
                        call.reject("Logout error: " + e.getMessage());
                    }
                }

                @Override
                public void onError(MsalException exception) {
                    Log.e(TAG, "MSAL init error during logout", exception);
                    call.reject("MSAL init error: " + exception.getMessage());
                }
            }
        );
    }

    // ---------------------------------------------------------------
    // logoutAll
    // ---------------------------------------------------------------

    @PluginMethod
    public void logoutAll(PluginCall call) {
        String clientId = call.getString("clientId");
        if (clientId == null || clientId.isEmpty()) {
            call.reject("clientId is required");
            return;
        }
        String tenant        = call.getString("tenant", "common");
        String authorityType = call.getString("authorityType", "AAD");
        String authorityUrl  = call.getString("authorityUrl");
        String keyHash       = call.getString("keyHash", "");

        String configJson;
        try {
            configJson = buildMsalConfig(clientId, tenant, authorityType, authorityUrl, keyHash);
        } catch (Exception e) {
            call.reject("Failed to build MSAL config: " + e.getMessage());
            return;
        }

        File configFile;
        try {
            configFile = writeTempConfig(configJson);
        } catch (IOException e) {
            call.reject("Failed to write MSAL config: " + e.getMessage());
            return;
        }

        Context context = getContext();

        PublicClientApplication.createMultipleAccountPublicClientApplication(
            context,
            configFile,
            new IPublicClientApplication.ApplicationCreatedListener() {
                @Override
                public void onCreated(IPublicClientApplication application) {
                    IMultipleAccountPublicClientApplication multiApp =
                        (IMultipleAccountPublicClientApplication) application;
                    try {
                        List<IAccount> accounts = multiApp.getAccounts();
                        if (accounts.isEmpty()) {
                            call.reject("Nothing to sign out from.");
                            return;
                        }
                        final int[] remaining = { accounts.size() };
                        for (IAccount account : accounts) {
                            multiApp.removeAccount(account, new IMultipleAccountPublicClientApplication.RemoveAccountCallback() {
                                @Override
                                public void onRemoved() {
                                    remaining[0]--;
                                    if (remaining[0] == 0) {
                                        call.resolve();
                                    }
                                }

                                @Override
                                public void onError(MsalException exception) {
                                    Log.e(TAG, "logoutAll account error", exception);
                                    call.reject("Logout error: " + exception.getMessage());
                                }
                            });
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "getAccounts error during logoutAll", e);
                        call.reject("LogoutAll error: " + e.getMessage());
                    }
                }

                @Override
                public void onError(MsalException exception) {
                    Log.e(TAG, "MSAL init error during logoutAll", exception);
                    call.reject("MSAL init error: " + exception.getMessage());
                }
            }
        );
    }

    // ---------------------------------------------------------------
    // Multi-account login flow
    // ---------------------------------------------------------------

    private void performMultiAccountLogin(
        PluginCall call,
        IMultipleAccountPublicClientApplication app,
        List<String> scopes,
        Prompt prompt,
        boolean isSilentOnly,
        Activity activity
    ) {
        try {
            List<IAccount> accounts = app.getAccounts();
            if (!accounts.isEmpty()) {
                IAccount account = accounts.get(0);
                AcquireTokenSilentParameters silentParams = new AcquireTokenSilentParameters.Builder()
                    .fromAuthority(account.getAuthority())
                    .forAccount(account)
                    .withScopes(scopes)
                    .withCallback(new AuthenticationCallback() {
                        @Override
                        public void onSuccess(IAuthenticationResult authResult) {
                            resolveCall(call, authResult);
                        }

                        @Override
                        public void onError(MsalException exception) {
                            if (exception instanceof MsalUiRequiredException) {
                                if (isSilentOnly) {
                                    call.reject("Silent login failed: interaction required but silent-only mode active.");
                                    return;
                                }
                                acquireTokenInteractively(call, app, scopes, prompt, activity);
                            } else {
                                Log.e(TAG, "Silent token error", exception);
                                call.reject("Token error: " + exception.getMessage());
                            }
                        }

                        @Override
                        public void onCancel() {
                            call.reject("Login cancelled by user");
                        }
                    })
                    .build();

                app.acquireTokenSilentAsync(silentParams);
            } else {
                if (isSilentOnly) {
                    call.reject("No cached account found. Silent login not possible.");
                    return;
                }
                acquireTokenInteractively(call, app, scopes, prompt, activity);
            }
        } catch (Exception e) {
            Log.e(TAG, "performMultiAccountLogin error", e);
            call.reject("Login error: " + e.getMessage());
        }
    }

    private void acquireTokenInteractively(
        PluginCall call,
        IMultipleAccountPublicClientApplication app,
        List<String> scopes,
        Prompt prompt,
        Activity activity
    ) {
        AcquireTokenParameters tokenParams = new AcquireTokenParameters.Builder()
            .startAuthorizationFromActivity(activity)
            .withScopes(scopes)
            .withPrompt(prompt)
            .withCallback(new AuthenticationCallback() {
                @Override
                public void onSuccess(IAuthenticationResult authResult) {
                    resolveCall(call, authResult);
                }

                @Override
                public void onError(MsalException exception) {
                    Log.e(TAG, "Interactive token error", exception);
                    call.reject("Login failed: " + exception.getMessage());
                }

                @Override
                public void onCancel() {
                    call.reject("Login cancelled by user");
                }
            })
            .build();

        app.acquireToken(tokenParams);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private String buildMsalConfig(
        String clientId,
        String tenant,
        String authorityType,
        String authorityUrl,
        String keyHash
    ) throws JSONException {
        JSONObject config = new JSONObject();
        config.put("client_id", clientId);
        config.put("authorization_user_agent", "DEFAULT");
        config.put("redirect_uri", "msauth://" + getContext().getPackageName() + "/" + keyHash);
        config.put("broker_redirect_uri_registered", false);
        config.put("account_mode", "MULTIPLE");

        JSONObject authority = new JSONObject();
        String effectiveAuthorityUrl;

        if (authorityUrl != null && !authorityUrl.isEmpty()) {
            effectiveAuthorityUrl = authorityUrl;
        } else {
            effectiveAuthorityUrl = "https://login.microsoftonline.com/" + tenant;
        }

        if ("B2C".equalsIgnoreCase(authorityType)) {
            authority.put("type", "B2C");
            authority.put("authority_url", effectiveAuthorityUrl);
            authority.put("default", true);
        } else if ("CIAM".equalsIgnoreCase(authorityType)) {
            authority.put("type", "CIAM");
            authority.put("authority_url", effectiveAuthorityUrl);
            authority.put("default", true);
        } else {
            authority.put("type", "AAD");
            JSONObject jAudience = new JSONObject();
            jAudience.put("type", "AzureADMyOrg");
            jAudience.put("tenant_id", tenant);
            authority.put("audience", jAudience);
            authority.put("default", true);
        }

        JSONArray authorities = new JSONArray();
        authorities.put(authority);
        config.put("authorities", authorities);

        return config.toString();
    }

    private File writeTempConfig(String json) throws IOException {
        File cacheDir = getContext().getCacheDir();
        File configFile = new File(cacheDir, "msal_config_" + System.currentTimeMillis() + ".json");
        try (FileWriter fw = new FileWriter(configFile)) {
            fw.write(json);
        }
        return configFile;
    }

    private Prompt resolvePrompt(String promptStr) {
        if (promptStr == null) return Prompt.SELECT_ACCOUNT;
        switch (promptStr.toLowerCase()) {
            case "login":   return Prompt.LOGIN;
            case "consent": return Prompt.CONSENT;
            case "create":  return Prompt.CREATE;
            case "none":
            case "select_account":
            default:        return Prompt.SELECT_ACCOUNT;
        }
    }

    private void resolveCall(PluginCall call, IAuthenticationResult authResult) {
        JSObject ret = new JSObject();
        ret.put("accessToken", authResult.getAccessToken());
        String idToken = authResult.getAccount().getIdToken();
        ret.put("idToken", idToken != null ? idToken : "");

        JSArray scopesArray = new JSArray();
        for (String s : authResult.getScope()) {
            scopesArray.put(s);
        }
        ret.put("scopes", scopesArray);

        call.resolve(ret);
    }
}
