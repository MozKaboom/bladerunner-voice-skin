// SPDX-License-Identifier: Apache-2.0
package kiosk.ziggy;

import android.content.Context;
import android.view.View;
import java.util.Collections;
import java.util.Map;
import me.jxl.kiosk.plugins.KioskPlugin;
import me.jxl.kiosk.plugins.KsTheme;
import me.jxl.kiosk.plugins.OverlayFactory;
import me.jxl.kiosk.plugins.OverlaySpec;
import me.jxl.kiosk.plugins.PluginHost;

/**
 * Ziggy, a voice skin. Pick it in Voice Satellite > Appearance > Assistant 1 skin or Assistant 2
 * skin and it draws the turn: a HUD of glowing rings over Ziggy clips that pulses with the voice,
 * with what was said and the answer beside it. Full screen it covers the kiosk. Docked it is a
 * small bar at the bottom that leaves the dashboard in view.
 */
public final class ZiggyPlugin implements KioskPlugin {
    static final String KEY = "ziggy";
    static final String PAGE = "hud/index.html";
    static final String[] EVENTS = {"voice.skin", "voice.overlay", "voice.level", "voice.text", "voice.appearance"};
    /** The docked bar: its widest, its height at Text size 1 and its gap from the bottom edge, in dp. */
    static final int DOCK_WIDTH = 640, DOCK_HEIGHT = 128, DOCK_INSET = 24;

    private PluginHost host;
    private final SkinState state = new SkinState();
    private int generation;
    /** What it draws: null, "full" or "docked". Guarded by this. */
    private String drawn;
    /** The showing view. Set and cleared on the main thread, read anywhere. */
    private volatile HudView view;

    @Override
    public synchronized void start(PluginHost host, Map<String, Object> settings) {
        this.host = host;
        int current = ++generation;
        if (host.isHeadless()) return;
        try {
            for (String event : EVENTS) host.subscribe(event);
        } catch (IllegalArgumentException | UnsupportedOperationException olderHost) {
            host.status("This Kiosk Satellite version has no voice skins", true);
            return;
        }
        host.status("Pick Ziggy in Voice Satellite > Appearance", false);
        read(current);
    }

    private void read(int current) {
        // Events win over these reads: they are newer.
        host.executeCommand("getVoiceState", Collections.<String, Object>emptyMap(), (ok, data, error) -> {
            if (!ok || !(data instanceof Map)) return;
            synchronized (this) {
                if (current != generation) return;
                state.readState((Map<?, ?>) data);
                update();
            }
        });
        host.executeCommand("getVoiceAppearance", Collections.<String, Object>emptyMap(), (ok, data, error) -> {
            if (!ok || !(data instanceof Map)) return;
            synchronized (this) {
                if (current != generation) return;
                state.readAppearance((Map<?, ?>) data);
                push();
            }
        });
        host.executeCommand("getVoiceText", Collections.<String, Object>emptyMap(), (ok, data, error) -> {
            if (!ok || !(data instanceof Map)) return;
            synchronized (this) {
                if (current != generation) return;
                state.readWords((Map<?, ?>) data);
                push();
            }
        });
    }

    @Override
    public void configure(Map<String, Object> settings) {}

    @Override
    public void execute(String command, Map<String, Object> arguments) {}

    @Override
    public synchronized void onEvent(String event, Map<String, Object> payload) {
        if (host == null) return;
        switch (state.onEvent(event, payload)) {
            case MODE:
                update();
                break;
            case LEVEL:
                HudView current = view;
                if (current != null) current.level(state.level());
                break;
            case WORDS:
                push();
                break;
            case APPEARANCE:
                // Text size changes the docked bar's height, so it is laid out again.
                if ("docked".equals(drawn)) {
                    drawn = null;
                    update();
                } else {
                    push();
                }
                break;
            default:
                break;
        }
    }

    /** Shows the look KS wants while the skin is picked, and nothing otherwise. */
    private void update() {
        String want = state.drawn();
        if (want == null ? drawn == null : want.equals(drawn)) return;
        drawn = want;
        if (want == null) {
            host.hideOverlay(KEY);
        } else if ("full".equals(want)) {
            host.showOverlay(KEY, animated(OverlaySpec.fullScreen().onTop(true).touchable(false).closeOnBack(false),
                OverlaySpec.FADE, 300), new Hud("full"));
        } else {
            // A bar at the bottom. Touches reach the dashboard around and under it.
            host.showOverlay(KEY, animated(OverlaySpec.at(OverlaySpec.BOTTOM).inset(DOCK_INSET).onTop(true).touchable(false).closeOnBack(false),
                OverlaySpec.SLIDE, 250), new Hud("docked"));
        }
    }

    private void push() {
        HudView current = view;
        if (current != null) current.update(state.json());
    }

    static OverlaySpec animated(OverlaySpec spec, String transition, int ms) {
        try {
            return spec.enter(transition).exit(transition).transitionDuration(ms);
        } catch (LinkageError olderHost) {
            return spec;
        }
    }

    @Override
    public synchronized void stop() {
        if (host != null && drawn != null) {
            try {
                host.hideOverlay(KEY);
            } catch (RuntimeException ignored) {
                // KS removes a stopped plugin's overlays itself.
            }
        }
        host = null;
        generation++;
        drawn = null;
        state.reset();
    }

    /** Builds the HUD for one look. KS may build it again, for example after a UI scale change. */
    private final class Hud implements OverlayFactory {
        private final String mode;

        Hud(String mode) { this.mode = mode; }

        @Override public View create(Context context, KsTheme theme) {
            PluginHost current = host;
            String page = current == null ? null : current.assetPath(PAGE);
            HudView hud = new HudView(context, page, mode);
            if ("docked".equals(mode)) {
                int screen = context.getResources().getDisplayMetrics().widthPixels;
                int width = Math.min(theme.px(DOCK_WIDTH), screen - theme.px(DOCK_INSET * 2));
                int height = theme.px(DOCK_HEIGHT * Math.max(1, state.textScale()));
                hud.fixSize(width, height);
            }
            hud.update(state.json());
            hud.level(state.level());
            view = hud;
            return hud;
        }

        @Override public void onDestroy(View destroyed) {
            if (view == destroyed) view = null;
            ((HudView) destroyed).release();
        }
    }
}
