// SPDX-License-Identifier: Apache-2.0
package kiosk.ziggy;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Locale;

/**
 * The HUD page from the plugin's assets in a WebView. The page draws everything: the clips,
 * the rings and the words. Java only hands it the turn as it changes. The page loads from the
 * verified package and every other request is refused, so it never reaches the network.
 */
final class HudView extends FrameLayout {
    private final WebView web;
    private boolean loaded;
    /** The latest state and level, sent once the page has loaded. Main thread only. */
    private String state;
    private float level;
    private boolean levelQueued;
    /** The docked bar's size in pixels, or 0 to fill the overlay. */
    private int fixedWidth, fixedHeight;

    @SuppressLint("SetJavaScriptEnabled")
    HudView(Context context, String page, String mode) {
        super(context);
        web = new WebView(context);
        web.setBackgroundColor(Color.TRANSPARENT);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.setFocusable(false);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setDomStorageEnabled(false);
        settings.setSupportZoom(false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return true;
            }

            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if ("file".equals(request.getUrl().getScheme())) return null;
                // Only the packaged page and its files load.
                return new WebResourceResponse("text/plain", "utf-8", 403, "Blocked", null, new ByteArrayInputStream(new byte[0]));
            }

            @Override public void onPageFinished(WebView view, String url) {
                loaded = true;
                if (state != null) send(state);
                sendLevel();
            }
        });
        addView(web, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        web.loadUrl(Uri.fromFile(new File(page)).buildUpon().appendQueryParameter("mode", mode).build().toString());
    }

    /** Gives the view a size of its own, for the docked bar, whose overlay wraps its content. */
    void fixSize(int width, int height) {
        fixedWidth = width;
        fixedHeight = height;
        requestLayout();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        if (fixedWidth > 0 && fixedHeight > 0) {
            widthSpec = MeasureSpec.makeMeasureSpec(fixedWidth, MeasureSpec.EXACTLY);
            heightSpec = MeasureSpec.makeMeasureSpec(fixedHeight, MeasureSpec.EXACTLY);
        }
        super.onMeasure(widthSpec, heightSpec);
    }

    /** New words or Appearance settings, as JSON. Any thread. */
    void update(String json) {
        post(() -> {
            state = json;
            if (loaded) send(json);
        });
    }

    /** The latest voice level. Any thread. Readings arrive 30 times a second, so they're coalesced. */
    void level(float value) {
        post(() -> {
            level = value;
            if (levelQueued) return;
            levelQueued = true;
            postOnAnimation(this::sendLevel);
        });
    }

    private void sendLevel() {
        levelQueued = false;
        if (loaded) web.evaluateJavascript(String.format(Locale.ROOT, "window.ziggy&&ziggy.level(%.3f)", level), null);
    }

    private void send(String json) {
        web.evaluateJavascript("window.ziggy&&ziggy.update(" + json + ")", null);
    }

    /** KS removed the overlay. Stops the clips and frees the WebView. Main thread. */
    void release() {
        loaded = false;
        removeAllViews();
        web.stopLoading();
        web.loadUrl("about:blank");
        web.destroy();
    }
}
