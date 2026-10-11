package com.jayjaytgg.agore;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.ConsoleMessage;
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

public class MainActivity extends Activity {

    private static final String HOME_URL =
            "https://agore.vercel.app/home";

    private static final String AGORE_HOST =
            "agore.vercel.app";

    private static final int FILE_CHOOSER_REQUEST_CODE = 1001;

    private WebView webView;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> fileChooserCallback;
    private int lastNightMode;
    private boolean javascriptErrorToastShown;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        lastNightMode =
                getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;

        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
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
                javascriptErrorToastShown = false;
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(0);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setProgress(100);
                progressBar.setVisibility(View.GONE);

                String diagnosticScript =
                        "(function(){"
                        + "if(window.__agoreTapProbeInstalled)"
                        + "return 'ready';"
                        + "window.__agoreTapProbeInstalled=true;"
                        + "document.addEventListener('click',function(e){"
                        + "var t=e.target;"
                        + "var el=t&&t.closest?"
                        + "t.closest('button,a,[role=button],input,"
                        + "summary,[onclick],[tabindex]'):null;"
                        + "if(!el)return;"
                        + "var label=el.innerText"
                        + "||el.getAttribute('aria-label')"
                        + "||el.getAttribute('title')"
                        + "||el.getAttribute('type')"
                        + "||el.tagName;"
                        + "label=String(label)"
                        + ".replace(/\\s+/g,' ').trim().slice(0,45);"
                        + "console.log('AGORE_TAP:'+label);"
                        + "},true);"
                        + "return 'ready';"
                        + "})()";

                view.evaluateJavascript(
                        diagnosticScript,
                        value -> {
                            if (value != null && value.contains("ready")) {
                                Toast.makeText(
                                        MainActivity.this,
                                        "Agoré tap diagnostics active.",
                                        Toast.LENGTH_SHORT
                                ).show();
                            }
                        }
                );
            }

            @Override
            public void onReceivedError(
                    WebView view,
                    WebResourceRequest request,
                    android.webkit.WebResourceError error
            ) {
                if (request.isForMainFrame()) {
                    Toast.makeText(
                            MainActivity.this,
                            "Unable to connect to Agoré. Check your connection.",
                            Toast.LENGTH_LONG
                    ).show();
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public boolean onConsoleMessage(ConsoleMessage message) {
                String text = message.message();

                if (text != null && text.startsWith("AGORE_TAP:")) {
                    Toast.makeText(
                            MainActivity.this,
                            "Tap detected: "
                                    + text.substring("AGORE_TAP:".length()),
                            Toast.LENGTH_SHORT
                    ).show();

                } else if (
                        message.messageLevel()
                                == ConsoleMessage.MessageLevel.ERROR
                        && !javascriptErrorToastShown
                ) {
                    javascriptErrorToastShown = true;

                    if (text == null || text.isEmpty()) {
                        text = "Unknown JavaScript error";
                    }

                    if (text.length() > 110) {
                        text = text.substring(0, 110) + "...";
                    }

                    Toast.makeText(
                            MainActivity.this,
                            "Agoré JavaScript error: " + text,
                            Toast.LENGTH_LONG
                    ).show();
                }

                return true;
            }

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
                    WebView webView,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams params
            ) {
                if (fileChooserCallback != null) {
                    fileChooserCallback.onReceiveValue(null);
                }

                fileChooserCallback = callback;

                try {
                    startActivityForResult(
                            params.createIntent(),
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

        if (savedInstanceState != null) {
            android.webkit.WebBackForwardList restored =
                    webView.restoreState(savedInstanceState);

            if (restored == null || restored.getSize() == 0) {
                webView.loadUrl(HOME_URL);
            }
        } else {
            webView.loadUrl(HOME_URL);
        }
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

            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                Toast.makeText(
                        this,
                        "No app is available to open this link.",
                        Toast.LENGTH_SHORT
                ).show();
            }

        } catch (ActivityNotFoundException error) {
            Toast.makeText(
                    this,
                    "Unable to open this link.",
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