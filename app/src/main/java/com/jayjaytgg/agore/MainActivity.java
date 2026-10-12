package com.jayjaytgg.agore;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.json.JSONObject;

public class MainActivity extends Activity {

    private static final String HOME_URL =
            "https://agore.vercel.app/home";

    private static final String AUTH_URL =
            "https://agore.vercel.app/auth";

    private static final String AGORE_HOST =
            "agore.vercel.app";

    private static final String OAUTH_CALLBACK_SCHEME =
            "com.jayjaytgg.agore";

    private static final String OAUTH_CALLBACK_HOST =
            "auth-callback";

    private static final String NOTIFICATION_CHANNEL_ID =
            "agore_updates";

    private static final int FILE_CHOOSER_REQUEST_CODE = 1001;

    private WebView webView;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> fileChooserCallback;
    private Uri pendingOAuthCallback;

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback connectivityCallback;

    private String failedMainFrameUrl;

    private int lastNightMode;
    private boolean hasValidatedInternet;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        lastNightMode =
                getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;

        pendingOAuthCallback = extractOAuthCallback(getIntent());

        createNotificationChannel();

        Window window = getWindow();

        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        if (Build.VERSION.SDK_INT >= 29) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }

        FrameLayout root = new FrameLayout(this);

        root.setBackgroundColor(
                isNightMode()
                        ? Color.parseColor("#101522")
                        : Color.parseColor("#F6F8FC")
        );

        if (Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                view.setPadding(
                        insets.getSystemWindowInsetLeft(),
                        insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(),
                        insets.getSystemWindowInsetBottom()
                );

                return insets.consumeSystemWindowInsets();
            });
        }

        webView = new WebView(this);

        webView.setBackgroundColor(
                isNightMode()
                        ? Color.parseColor("#101522")
                        : Color.parseColor("#F6F8FC")
        );

        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);

        settings.setUserAgentString(
                settings.getUserAgentString() + " AgoreAndroid"
        );

        if (Build.VERSION.SDK_INT >= 26) {
            settings.setSafeBrowsingEnabled(true);
        }

        if (Build.VERSION.SDK_INT >= 21) {
            settings.setMixedContentMode(
                    WebSettings.MIXED_CONTENT_NEVER_ALLOW
            );
        }

        CookieManager.getInstance().setAcceptCookie(true);

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    WebResourceRequest request
            ) {
                return handleUrl(request.getUrl());
            }

            @Override
            public void onPageStarted(
                    WebView view,
                    String url,
                    android.graphics.Bitmap favicon
            ) {
                /*
                 * If the user navigates to another page, discard the
                 * previous failed URL. Recovery must not unexpectedly
                 * take the user back to an old page.
                 */
                if (failedMainFrameUrl != null
                        && !failedMainFrameUrl.equals(url)) {
                    failedMainFrameUrl = null;
                }

                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(0);
            }

            @Override
            public void onPageFinished(
                    WebView view,
                    String url
            ) {
                progressBar.setProgress(100);
                progressBar.setVisibility(View.GONE);

                if (pendingOAuthCallback != null
                        && isAuthUrl(url)) {
                    deliverPendingOAuthCallback();
                }
            }

            @Override
            public void onReceivedError(
                    WebView view,
                    WebResourceRequest request,
                    android.webkit.WebResourceError error
            ) {
                if (!request.isForMainFrame()) {
                    return;
                }

                String failedUrl = request.getUrl().toString();

                if (isAgoreUrl(failedUrl)) {
                    failedMainFrameUrl = failedUrl;
                }

                Toast.makeText(
                        MainActivity.this,
                        "Unable to load Agoré. "
                                + "Check your connection.",
                        Toast.LENGTH_LONG
                ).show();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public void onProgressChanged(
                    WebView view,
                    int newProgress
            ) {
                progressBar.setProgress(newProgress);

                progressBar.setVisibility(
                        newProgress >= 100
                                ? View.GONE
                                : View.VISIBLE
                );
            }

            @Override
            public boolean onShowFileChooser(
                    WebView view,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams params
            ) {
                if (fileChooserCallback != null) {
                    fileChooserCallback.onReceiveValue(null);
                }

                fileChooserCallback = callback;

                try {
                    Intent intent = params.createIntent();

                    startActivityForResult(
                            intent,
                            FILE_CHOOSER_REQUEST_CODE
                    );

                    return true;

                } catch (
                        ActivityNotFoundException
                        | SecurityException error
                ) {
                    fileChooserCallback = null;
                    callback.onReceiveValue(null);

                    Toast.makeText(
                            MainActivity.this,
                            "Unable to open file picker.",
                            Toast.LENGTH_SHORT
                    ).show();

                    return true;
                }
            }
        });

        FrameLayout.LayoutParams webParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                );

        root.addView(webView, webParams);

        progressBar = new ProgressBar(
                this,
                null,
                android.R.attr.progressBarStyleHorizontal
        );

        progressBar.setMax(100);

        progressBar.setProgressTintList(
                android.content.res.ColorStateList.valueOf(
                        Color.parseColor("#2458C6")
                )
        );

        FrameLayout.LayoutParams progressParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        dp(3),
                        Gravity.TOP
                );

        root.addView(progressBar, progressParams);

        setContentView(root);

        if (pendingOAuthCallback != null) {
            webView.loadUrl(AUTH_URL);

        } else if (savedInstanceState != null) {
            android.webkit.WebBackForwardList restored =
                    webView.restoreState(savedInstanceState);

            if (restored == null || restored.getSize() == 0) {
                webView.loadUrl(HOME_URL);
            }

        } else {
            webView.loadUrl(HOME_URL);
        }
    }

    /*
     * Register only while the activity is visible. On returning to
     * Agoré, the initial connectivity check can retry a failed page
     * if internet access has been restored.
     */
    @Override
    protected void onStart() {
        super.onStart();
        registerConnectivityCallback();
    }

    @Override
    protected void onStop() {
        unregisterConnectivityCallback();
        super.onStop();
    }

    private void registerConnectivityCallback() {
        if (connectivityManager == null) {
            connectivityManager =
                    getSystemService(ConnectivityManager.class);
        }

        if (connectivityManager == null
                || connectivityCallback != null) {
            return;
        }

        hasValidatedInternet = false;

        connectivityCallback =
                new ConnectivityManager.NetworkCallback() {

            @Override
            public void onAvailable(Network network) {
                runOnUiThread(() ->
                        updateConnectivityState(
                                hasValidatedInternet(network)
                        )
                );
            }

            @Override
            public void onCapabilitiesChanged(
                    Network network,
                    NetworkCapabilities capabilities
            ) {
                boolean validated =
                        hasValidatedInternet(capabilities);

                runOnUiThread(() ->
                        updateConnectivityState(validated)
                );
            }

            @Override
            public void onLost(Network network) {
                runOnUiThread(() -> {
                    if (connectivityManager == null) {
                        updateConnectivityState(false);
                        return;
                    }

                    Network activeNetwork =
                            connectivityManager.getActiveNetwork();

                    updateConnectivityState(
                            hasValidatedInternet(activeNetwork)
                    );
                });
            }
        };

        try {
            connectivityManager.registerDefaultNetworkCallback(
                    connectivityCallback
            );

        } catch (RuntimeException error) {
            connectivityCallback = null;
            return;
        }

        Network activeNetwork =
                connectivityManager.getActiveNetwork();

        updateConnectivityState(
                hasValidatedInternet(activeNetwork)
        );
    }

    private void unregisterConnectivityCallback() {
        if (connectivityManager != null
                && connectivityCallback != null) {

            try {
                connectivityManager.unregisterNetworkCallback(
                        connectivityCallback
                );

            } catch (RuntimeException ignored) {
                // The callback may already have been unregistered.
            }
        }

        connectivityCallback = null;
        hasValidatedInternet = false;
    }

    private boolean hasValidatedInternet(Network network) {
        if (connectivityManager == null || network == null) {
            return false;
        }

        NetworkCapabilities capabilities =
                connectivityManager.getNetworkCapabilities(network);

        return hasValidatedInternet(capabilities);
    }

    private boolean hasValidatedInternet(
            NetworkCapabilities capabilities
    ) {
        return capabilities != null
                && capabilities.hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_INTERNET
                )
                && capabilities.hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_VALIDATED
                );
    }

    private void updateConnectivityState(boolean available) {
        boolean connectionRestored =
                available && !hasValidatedInternet;

        hasValidatedInternet = available;

        if (connectionRestored) {
            retryFailedPage();
        }
    }

    private void retryFailedPage() {
        if (webView == null || failedMainFrameUrl == null) {
            return;
        }

        String retryUrl = failedMainFrameUrl;

        if (!isAgoreUrl(retryUrl)) {
            failedMainFrameUrl = null;
            return;
        }

        String currentUrl = webView.getUrl();

        /*
         * Do not redirect users who have moved to another page since
         * the original load failed.
         */
        if (currentUrl == null || !currentUrl.equals(retryUrl)) {
            failedMainFrameUrl = null;
            return;
        }

        failedMainFrameUrl = null;

        Toast.makeText(
                this,
                "Connection restored. Reloading Agoré…",
                Toast.LENGTH_SHORT
        ).show();

        webView.reload();
    }

    private boolean isAgoreUrl(String value) {
        if (value == null) {
            return false;
        }

        Uri uri = Uri.parse(value);

        return "https".equalsIgnoreCase(uri.getScheme())
                && AGORE_HOST.equalsIgnoreCase(uri.getHost());
    }

    private Uri extractOAuthCallback(Intent intent) {
        if (intent == null
                || !Intent.ACTION_VIEW.equals(intent.getAction())) {
            return null;
        }

        Uri uri = intent.getData();

        if (uri == null
                || !OAUTH_CALLBACK_SCHEME.equals(uri.getScheme())
                || !OAUTH_CALLBACK_HOST.equals(uri.getHost())) {
            return null;
        }

        boolean hasCode =
                uri.getQueryParameter("code") != null;

        boolean hasError =
                uri.getQueryParameter("error") != null
                || uri.getQueryParameter("error_description") != null;

        return hasCode || hasError ? uri : null;
    }

    private boolean isAuthUrl(String value) {
        if (value == null) {
            return false;
        }

        Uri uri = Uri.parse(value);

        return "https".equalsIgnoreCase(uri.getScheme())
                && AGORE_HOST.equalsIgnoreCase(uri.getHost())
                && "/auth".equals(uri.getPath());
    }

    private void deliverPendingOAuthCallback() {
        if (webView == null || pendingOAuthCallback == null) {
            return;
        }

        String callbackJson =
                JSONObject.quote(pendingOAuthCallback.toString());

        pendingOAuthCallback = null;

        String script =
                "(function(){"
                + "var callbackUrl=" + callbackJson + ";"
                + "window.__AGORE_NATIVE_OAUTH_CALLBACK=callbackUrl;"
                + "window.dispatchEvent(new CustomEvent("
                + "'agore-native-oauth-callback',"
                + "{detail:callbackUrl}));"
                + "})();";

        webView.evaluateJavascript(script, null);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        setIntent(intent);

        Uri callback = extractOAuthCallback(intent);

        if (callback == null) {
            return;
        }

        pendingOAuthCallback = callback;

        if (webView == null) {
            return;
        }

        String currentUrl = webView.getUrl();

        if (!isAuthUrl(currentUrl)) {
            webView.loadUrl(AUTH_URL);
        } else {
            deliverPendingOAuthCallback();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager notificationManager =
                getSystemService(NotificationManager.class);

        if (notificationManager == null) {
            return;
        }

        if (notificationManager.getNotificationChannel(
                NOTIFICATION_CHANNEL_ID
        ) != null) {
            return;
        }

        Uri soundUri = Uri.parse(
                "android.resource://"
                        + getPackageName()
                        + "/"
                        + R.raw.agore_notification
        );

        AudioAttributes audioAttributes =
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(
                                AudioAttributes.CONTENT_TYPE_SONIFICATION
                        )
                        .build();

        NotificationChannel channel =
                new NotificationChannel(
                        NOTIFICATION_CHANNEL_ID,
                        "Agoré updates",
                        NotificationManager.IMPORTANCE_DEFAULT
                );

        channel.setDescription(
                "Notifications for Agoré messages and social activity."
        );

        channel.setSound(soundUri, audioAttributes);
        channel.enableVibration(true);
        channel.setVibrationPattern(
                new long[]{0, 120, 60, 160}
        );
        channel.setLightColor(Color.parseColor("#2458C6"));
        channel.setShowBadge(true);

        notificationManager.createNotificationChannel(channel);
    }

    private boolean isNightMode() {
        int mode =
                getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;

        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    private boolean handleUrl(Uri uri) {
        String scheme = uri.getScheme();
        String host = uri.getHost();

        if (scheme == null) {
            return true;
        }

        if ("https".equalsIgnoreCase(scheme)
                && AGORE_HOST.equalsIgnoreCase(host)) {
            return false;
        }

        if ("http".equalsIgnoreCase(scheme)
                && AGORE_HOST.equalsIgnoreCase(host)) {
            return false;
        }

        if ("https".equalsIgnoreCase(scheme)
                || "http".equalsIgnoreCase(scheme)
                || "mailto".equalsIgnoreCase(scheme)
                || "tel".equalsIgnoreCase(scheme)
                || "sms".equalsIgnoreCase(scheme)) {

            openExternalUrl(uri);
            return true;
        }

        return true;
    }

    private void openExternalUrl(Uri uri) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addCategory(Intent.CATEGORY_BROWSABLE);

            startActivity(intent);

        } catch (
                ActivityNotFoundException
                | SecurityException error
        ) {
            Toast.makeText(
                    this,
                    "Unable to open this link: " + uri.getScheme(),
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FILE_CHOOSER_REQUEST_CODE
                && fileChooserCallback != null) {

            Uri[] results =
                    resultCode == RESULT_OK
                            ? WebChromeClient.FileChooserParams
                                    .parseResult(resultCode, data)
                            : null;

            fileChooserCallback.onReceiveValue(results);
            fileChooserCallback = null;
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        int newNightMode =
                newConfig.uiMode
                & Configuration.UI_MODE_NIGHT_MASK;

        if (newNightMode != lastNightMode) {
            lastNightMode = newNightMode;
            recreate();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) {
            webView.saveState(outState);
        }

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (webView != null) {
            webView.onResume();
        }
    }

    @Override
    protected void onPause() {
        if (webView != null) {
            webView.onPause();
        }

        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        unregisterConnectivityCallback();

        if (fileChooserCallback != null) {
            fileChooserCallback.onReceiveValue(null);
            fileChooserCallback = null;
        }

        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density
        );
    }
}