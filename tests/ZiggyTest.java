// SPDX-License-Identifier: Apache-2.0
package kiosk.ziggy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import me.jxl.kiosk.plugins.OverlayFactory;
import me.jxl.kiosk.plugins.OverlaySpec;
import me.jxl.kiosk.plugins.PluginHost;

/** Exercises when the skin shows and what it tells the page, without Android views. */
public final class ZiggyTest {
    static Map<String, Object> map(Object... pairs) {
        Map<String, Object> out = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) out.put((String) pairs[i], pairs[i + 1]);
        return out;
    }

    static final class Host implements PluginHost {
        final List<String> calls = Collections.synchronizedList(new ArrayList<>());
        final Map<String, Object> voiceState = map("skinSelected", true, "overlay", "hidden");
        public void showWindow(String title, String message, String button) {}
        public void hideWindow() {}
        public void log(String message) {}
        public void status(String message, boolean error) {}
        public void subscribe(String event) { calls.add("subscribe " + event); }
        public void executeCommand(String command, Map<String, Object> arguments, CommandCallback callback) {
            calls.add("read " + command);
            if ("getVoiceState".equals(command)) callback.onResult(true, voiceState, null);
            else callback.onResult(true, Collections.emptyMap(), null);
        }
        public void showOverlay(String key, OverlaySpec spec, OverlayFactory factory) {
            calls.add("show " + key + (spec.width() == OverlaySpec.FILL ? " full" : " " + spec.anchor()));
        }
        public void hideOverlay(String key) { calls.add("hide " + key); }
    }

    public static void main(String[] args) throws Exception {
        // The state alone.
        SkinState state = new SkinState();
        assert state.drawn() == null;
        state.onEvent("ks.voice.overlay", map("overlay", "full"));
        assert state.drawn() == null : "Not picked yet";
        assert state.onEvent("ks.voice.skin", map("selected", true)) == SkinState.Change.MODE;
        assert "full".equals(state.drawn());
        state.onEvent("ks.voice.overlay", map("overlay", "docked"));
        assert "docked".equals(state.drawn());
        state.onEvent("ks.voice.overlay", map("overlay", "bogus"));
        assert state.drawn() == null;
        state.onEvent("ks.voice.overlay", map("overlay", "hidden"));
        // A read never undoes an event: the skin stays picked, and the overlay was only hidden.
        state.readState(map("skinSelected", false, "overlay", "full"));
        assert "full".equals(state.drawn()) : "Overlay from the read, selection from the event";

        assert state.onEvent("ks.voice.level", map("level", 1.7)) == SkinState.Change.LEVEL;
        assert state.level() == 1f;
        assert "listening".equals(state.phase());
        state.onEvent("ks.voice.text", map("command", "What time is it?", "answer", "", "tools", Arrays.asList("Got the time"), "streaming", false));
        assert "thinking".equals(state.phase());
        state.onEvent("ks.voice.text", map("command", "What time is it?", "answer", "It's <noon>", "streaming", true));
        assert "answering".equals(state.phase());
        String json = state.json();
        assert json.contains("\"answer\":\"It's \\u003cnoon\\u003e\"") || json.contains("\"answer\":\"It's \\u003Cnoon\\u003E\"") : json;
        assert json.contains("\"streaming\":true") && json.contains("\"tools\":[]") && json.contains("\"background\":null") : json;
        assert ((Map<?, ?>) Json.parse(json)).get("command").equals("What time is it?");

        // Appearance: Reactive off silences the level, settings are clamped, reads don't undo events.
        state.onEvent("ks.voice.appearance", map("theme", "dark", "background", 0.5, "textScale", 9, "reactive", false));
        assert state.level() == 0f && state.textScale() == 2f;
        state.readAppearance(map("textScale", 1));
        assert state.textScale() == 2f;
        assert ((Map<?, ?>) Json.parse(state.json())).get("background").equals(0.5);
        state.onEvent("ks.voice.text", map("announcement", true));
        assert "answering".equals(state.phase());
        // With the voice state, the clips wait for the answer to play.
        state.onEvent("ks.voice.text", map("command", "Play some Bowie", "answer", "Playing Starman", "streaming", false));
        state.onEvent("ks.voice.state", map("state", "processing"));
        assert "thinking".equals(state.phase()) : "Answer text alone doesn't start the clips";
        state.onEvent("ks.voice.state", map("state", "responding"));
        assert "answering".equals(state.phase());
        state.onEvent("ks.voice.state", map("state", "idle"));
        assert "done".equals(state.phase()) : "The answer stays on screen, still";
        state.onEvent("ks.voice.text", map());
        assert "answering".equals(state.phase()) : "Preview: no turn, the whole look shows";
        state.readState(map("state", "listening"));
        assert "answering".equals(state.phase()) : "An event beats the read";
        state.reset();
        assert state.drawn() == null && "listening".equals(state.phase()) && state.level() == 0f;

        // The plugin shows, switches and hides the overlay as KS asks.
        Host host = new Host();
        ZiggyPlugin plugin = new ZiggyPlugin();
        plugin.start(host, Collections.<String, Object>emptyMap());
        for (String event : ZiggyPlugin.EVENTS) assert host.calls.contains("subscribe " + event) : host.calls;
        assert host.calls.contains("subscribe voice.state");
        assert host.calls.contains("read getVoiceState") && host.calls.contains("read getVoiceText");
        assert !host.calls.toString().contains("show") : "Picked, but no overlay yet";
        plugin.onEvent("ks.voice.overlay", map("overlay", "full"));
        assert host.calls.contains("show ziggy full") : host.calls;
        int shown = host.calls.size();
        plugin.onEvent("ks.voice.overlay", map("overlay", "full"));
        plugin.onEvent("ks.voice.level", map("level", 0.4));
        plugin.onEvent("ks.voice.text", map("command", "Hi"));
        assert host.calls.size() == shown : "Same look, no new overlay";
        plugin.onEvent("ks.voice.overlay", map("overlay", "docked"));
        assert host.calls.get(host.calls.size() - 1).equals("show ziggy bottom") : host.calls;
        // Text size lays the docked bar out again.
        plugin.onEvent("ks.voice.appearance", map("textScale", 1.5));
        assert host.calls.get(host.calls.size() - 1).equals("show ziggy bottom") && host.calls.size() == shown + 2 : host.calls;
        plugin.onEvent("ks.voice.skin", map("selected", false));
        assert host.calls.get(host.calls.size() - 1).equals("hide ziggy");
        plugin.onEvent("ks.voice.skin", map("selected", true));
        assert host.calls.get(host.calls.size() - 1).equals("show ziggy bottom");
        plugin.stop();
        assert host.calls.get(host.calls.size() - 1).equals("hide ziggy");
        int stopped = host.calls.size();
        plugin.onEvent("ks.voice.overlay", map("overlay", "full"));
        assert host.calls.size() == stopped : "Stopped plugins ignore events";
        System.out.println("ZiggyTest passed");
    }
}
