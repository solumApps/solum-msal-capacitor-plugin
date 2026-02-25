package com.solum.msauth;

import android.util.Log;
import androidx.annotation.NonNull;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.microsoft.identity.client.*;
import com.microsoft.identity.client.exception.MsalException;
import com.microsoft.identity.client.exception.MsalUiRequiredException;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@CapacitorPlugin(name = "MsAuthPlugin")
public class MsAuthPlugin extends Plugin {

    private static final String TAG = "SolumMsauth";

    @PluginMethod
    public void login(final PluginCall call) {
        try {
            ISingleAccountPublicClientApplication app = createContext(call);
            if (app == null) return;

            Prompt prompt = Prompt.SELECT_ACCOUNT;
            if (call.hasOption("prompt")) {
                switch (call.getString("prompt", "select_account").toLowerCase()) {
                    case "login":   prompt = Prompt.LOGIN;   break;
                    case "consent": prompt = Prompt.CONSENT; break;
                    case "create":  prompt = Prompt.CREATE;  break;
                    case "none":    prompt = Prompt.WHEN_REQUIRED; break;
                    default:        prompt = Prompt.SELECT_ACCOUNT; break;
                }
            }

            List<String> scopes = call.getArray("scopes").toList();
            acquireToken(app, scopes, prompt, tokenResult -> {
                if (tokenResult != null) {
                    JSObject result = new JSObject();
                    result.put("accessToken", tokenResult.getAccessToken());
                    result.put("idToken",     tokenResult.getIdToken());
                    result.put("scopes",      new JSONArray(java.util.Arrays.asList(tokenResult.getScopes())));
                    call.resolve(result);
                } else {
                    call.reject("Unable to obtain access token");
                }
            });
        } catch (Exception ex) {
            Log.e(TAG, "login error", ex);
            call.reject("Unable to fetch access token.");
        }
    }

    @PluginMethod
    public void logout(final PluginCall call) {
        try {
            ISingleAccountPublicClientApplication app = createContext(call);
            if (app == null) return;

            if (app.getCurrentAccount() == null || app.getCurrentAccount().getCurrentAccount() == null) {
                call.reject("Nothing to sign out from.");
                return;
            }

            app.signOut(new ISingleAccountPublicClientApplication.SignOutCallback() {
                @Override
                public void onSignOut() {
                    call.resolve();
                }

                @Override
                public void onError(@NonNull MsalException ex) {
                    Log.e(TAG, "Logout error", ex);
                    call.reject("Unable to sign out.");
                }
            });
        } catch (Exception ex) {
            Log.e(TAG, "logout error", ex);
            call.reject("Unable to fetch context.");
        }
    }

    @PluginMethod
    public void logoutAll(final PluginCall call) {
        logout(call);
    }

    private void acquireToken(
        ISingleAccountPublicClientApplication app,
        List<String> scopes,
        Prompt prompt,
        TokenResultCallback callback
    ) throws MsalException, InterruptedException {
        String authority = app.getConfiguration().getDefaultAuthority().getAuthorityURL().toString();
        ICurrentAccountResult currentAccountResult = app.getCurrentAccount();

        if (currentAccountResult.getCurrentAccount() != null) {
            try {
                Log.d(TAG, "Attempting silent login");
                AcquireTokenSilentParameters silentParams = new AcquireTokenSilentParameters.Builder()
                    .withScopes(scopes)
                    .fromAuthority(authority)
                    .forAccount(currentAccountResult.getCurrentAccount())
                    .build();

                IAuthenticationResult result = app.acquireTokenSilent(silentParams);
                TokenResult tokenResult = new TokenResult();
                tokenResult.setAccessToken(result.getAccessToken());
                tokenResult.setIdToken(result.getAccount().getIdToken());
                tokenResult.setScopes(result.getScope());
                callback.tokenReceived(tokenResult);
                return;
            } catch (MsalUiRequiredException ex) {
                Log.d(TAG, "Silent login failed, falling back to interactive");
            }
        }

        Log.d(TAG, "Starting interactive login");
        AcquireTokenParameters.Builder params = new AcquireTokenParameters.Builder()
            .startAuthorizationFromActivity(getActivity())
            .withScopes(scopes)
            .withPrompt(prompt)
            .withCallback(new AuthenticationCallback() {
                @Override
                public void onCancel() {
                    callback.tokenReceived(null);
                }

                @Override
                public void onSuccess(IAuthenticationResult authResult) {
                    TokenResult tokenResult = new TokenResult();
                    tokenResult.setAccessToken(authResult.getAccessToken());
                    tokenResult.setIdToken(authResult.getAccount().getIdToken());
                    tokenResult.setScopes(authResult.getScope());
                    callback.tokenReceived(tokenResult);
                }

                @Override
                public void onError(MsalException ex) {
                    Log.e(TAG, "Interactive login error", ex);
                    callback.tokenReceived(null);
                }
            });

        if (currentAccountResult.getCurrentAccount() != null) {
            params.withLoginHint(currentAccountResult.getCurrentAccount().getUsername());
        }

        app.acquireToken(params.build());
    }

    private ISingleAccountPublicClientApplication createContext(PluginCall call)
        throws MsalException, InterruptedException, IOException, JSONException {

        String clientId         = call.getString("clientId");
        String tenant           = call.getString("tenant");
        String keyHash          = call.getString("keyHash");
        String authorityUrl     = call.getString("authorityUrl");
        String authorityTypeStr = call.getString("authorityType", "AAD");
        String domainHint       = call.getString("domainHint");
        Boolean brokerRedirect  = call.getBoolean("brokerRedirectUriRegistered", false);

        if (clientId == null || clientId.isEmpty()) { call.reject("clientId is required"); return null; }
        if (keyHash  == null || keyHash.isEmpty())  { call.reject("keyHash is required");  return null; }

        String tenantId     = (tenant != null ? tenant : "common");
        String effectiveUrl = (authorityUrl != null && !authorityUrl.isEmpty())
            ? authorityUrl : "https://login.microsoftonline.com/" + tenantId;
        String encodedHash  = URLEncoder.encode(keyHash, "UTF-8");
        String redirectUri  = "msauth://" + getActivity().getApplicationContext().getPackageName() + "/" + encodedHash;

        JSONObject config    = new JSONObject();
        JSONObject authority = new JSONObject();

        switch (authorityTypeStr.toUpperCase()) {
            case "B2C":
                authority.put("type", "B2C");
                authority.put("authority_url", effectiveUrl);
                authority.put("default", "true");
                break;
            case "CIAM":
                authority.put("type", "CIAM");
                authority.put("authority_url", effectiveUrl);
                break;
            default:
                authority.put("type", "AAD");
                authority.put("authority_url", effectiveUrl);
                authority.put("audience",
                    new JSONObject().put("type", "AzureADMultipleOrgs").put("tenant_id", tenantId));
                config.put("broker_redirect_uri_registered", brokerRedirect);
                break;
        }

        config.put("client_id",                clientId);
        config.put("domain_hint",              domainHint);
        config.put("authorization_user_agent", "DEFAULT");
        config.put("redirect_uri",             redirectUri);
        config.put("account_mode",             "SINGLE");
        config.put("authorities",              new JSONArray().put(authority));

        File configFile = writeConfig(config);
        ISingleAccountPublicClientApplication app =
            PublicClientApplication.createSingleAccountPublicClientApplication(
                getContext().getApplicationContext(),
                configFile
            );

        if (!configFile.delete()) Log.w(TAG, "Unable to delete temp config file");
        return app;
    }

    private File writeConfig(JSONObject data) throws IOException {
        File config = new File(getActivity().getFilesDir() + "auth_config.json");
        try (FileWriter writer = new FileWriter(config, false)) {
            writer.write(data.toString());
            writer.flush();
        }
        return config;
    }
}
