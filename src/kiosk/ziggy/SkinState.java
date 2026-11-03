// SPDX-License-Identifier: Apache-2.0
package kiosk.ziggy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the skin knows about the voice turn: whether it is picked, which overlay KS wants, the
 * voice level, the words and the Appearance settings. Plain data, so it can be tested without
 * Android. Events are newer than the reads made at start, so a read never overwrites one.
 */
final class SkinState {
    enum Change { NONE, MODE, WORDS, LEVEL, APPEARANCE }

    private boolean selected;
    private boolean skinHeard;
    private String overlay = "hidden";
    /** idle, listening, processing or responding. Null until KS says, or on versions without it. */
    private String voice;
    private boolean voiceHeard;
    private float level;
    private String command = "";
    private String answer = "";
    private List<String> tools = new ArrayList<>();
    private boolean streaming;
    private boolean announcement;
    private boolean wordsHeard;
    private String theme = "auto";
    /** Null for Skin default. */
    private Float background;
    private float textScale = 1;
    private boolean reactive = true;
    private boolean appearanceHeard;

    /** Applies an event from KS and says what it changed. */
    synchronized Change onEvent(String event, Map<String, Object> payload) {
        switch (event) {
            case "ks.voice.skin":
                skinHeard = true;
                selected = Boolean.TRUE.equals(payload.get("selected"));
                return Change.MODE;
            case "ks.voice.overlay":
                overlay = text(payload.get("overlay"), "hidden");
                return Change.MODE;
            case "ks.voice.state":
                voiceHeard = true;
                voice = text(payload.get("state"), null);
                return Change.WORDS;
            case "ks.voice.level":
                Object value = payload.get("level");
                level = value instanceof Number ? clamp(((Number) value).floatValue(), 0, 1) : 0;
                return Change.LEVEL;
            case "ks.voice.text":
                wordsHeard = true;
                words(payload);
                return Change.WORDS;
            case "ks.voice.appearance":
                appearanceHeard = true;
                appearance(payload);
                return Change.APPEARANCE;
            default:
                return Change.NONE;
        }
    }

    /** The getVoiceState read at start, unless events already said more. */
    synchronized void readState(Map<?, ?> state) {
        if (!skinHeard) selected = Boolean.TRUE.equals(state.get("skinSelected"));
        if ("hidden".equals(overlay) && state.get("overlay") instanceof String) overlay = (String) state.get("overlay");
        if (!voiceHeard && state.get("state") instanceof String) voice = (String) state.get("state");
    }

    synchronized void readAppearance(Map<?, ?> settings) {
        if (!appearanceHeard) appearance(settings);
    }

    synchronized void readWords(Map<?, ?> text) {
        if (!wordsHeard) words(text);
    }

    /** What to draw now: null, "full" or "docked". */
    synchronized String drawn() {
        if (!selected) return null;
        return "full".equals(overlay) || "docked".equals(overlay) ? overlay : null;
    }

    /** The voice level the page follows: 0 while Reactive activity bar is off. */
    synchronized float level() {
        return reactive ? level : 0;
    }

    /**
     * Where the turn is: listening, thinking, answering while the answer plays, or done once it
     * has played and stays on screen. The clips only run while answering. KS reports the voice
     * state; without it, the words tell. Preview shows with no turn at all, and it answers so
     * the whole look can be seen.
     */
    synchronized String phase() {
        if (voice != null) {
            switch (voice) {
                case "responding": return "answering";
                case "processing": return "thinking";
                case "listening": return "listening";
                default:
                    return command.isEmpty() && answer.isEmpty() && !announcement ? "answering" : "done";
            }
        }
        if (announcement || !answer.isEmpty()) return "answering";
        if (!command.isEmpty()) return "thinking";
        return "listening";
    }

    /** Everything the page shows except the level, as JSON for its update call. */
    synchronized String json() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("phase", phase());
        out.put("command", command);
        out.put("answer", answer);
        out.put("tools", new ArrayList<>(tools));
        out.put("streaming", streaming);
        out.put("announcement", announcement);
        out.put("theme", theme);
        out.put("background", background);
        out.put("textScale", textScale);
        out.put("reactive", reactive);
        return Json.write(out);
    }

    synchronized float textScale() {
        return textScale;
    }

    synchronized void reset() {
        selected = skinHeard = streaming = announcement = wordsHeard = appearanceHeard = voiceHeard = false;
        voice = null;
        overlay = "hidden";
        level = 0;
        command = answer = "";
        tools = new ArrayList<>();
        theme = "auto";
        background = null;
        textScale = 1;
        reactive = true;
    }

    private void words(Map<?, ?> text) {
        command = text(text.get("command"), "");
        answer = text(text.get("answer"), "");
        List<String> lines = new ArrayList<>();
        if (text.get("tools") instanceof List) {
            for (Object line : (List<?>) text.get("tools")) if (line != null) lines.add(String.valueOf(line));
        }
        tools = lines;
        streaming = Boolean.TRUE.equals(text.get("streaming"));
        announcement = Boolean.TRUE.equals(text.get("announcement"));
    }

    private void appearance(Map<?, ?> settings) {
        theme = text(settings.get("theme"), "auto");
        background = settings.get("background") instanceof Number
            ? clamp(((Number) settings.get("background")).floatValue(), 0, 1) : null;
        textScale = settings.get("textScale") instanceof Number
            ? clamp(((Number) settings.get("textScale")).floatValue(), 0.5f, 2) : 1;
        reactive = !Boolean.FALSE.equals(settings.get("reactive"));
    }

    private static String text(Object value, String fallback) {
        return value instanceof String ? (String) value : fallback;
    }

    private static float clamp(float value, float min, float max) {
        if (Float.isNaN(value)) return min;
        return Math.max(min, Math.min(max, value));
    }
}
