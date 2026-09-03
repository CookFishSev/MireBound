package com.fish.mirebound.client.tuning;

import com.fish.mirebound.mud.MudTuningAnchor;
import com.fish.mirebound.mud.tuning.MudTuningSelectionElement;
import com.fish.mirebound.network.payload.MudTuningSelectionPayload;
import java.util.List;

/** Client-owned presentation state; every world mutation remains server-authoritative. */
public final class MudTuningClientState {
    private static boolean hasFirst;
    private static boolean hasSecond;
    private static MudTuningAnchor first = MudTuningAnchor.WORLD_ORIGIN;
    private static MudTuningAnchor second = MudTuningAnchor.WORLD_ORIGIN;
    private static MudTuningSelectionElement selectedElement =
            MudTuningSelectionElement.NONE;
    private static List<MudTuningSelectionPayload.HighlightGroup> highlightGroups = List.of();
    private static long highlightRevision;
    private static MudTuningSelectionPayload.SelectionSummary summary =
            MudTuningSelectionPayload.SelectionSummary.EMPTY;
    private static MudTuningWandMode mode = MudTuningClientSettings.savedMode();
    private static MudTuningSummonType summonType = MudTuningSummonType.TENTACLE;
    private static GlobalScreen pendingGlobalScreen = GlobalScreen.SETTINGS;

    private MudTuningClientState() {
    }

    public static void accept(MudTuningSelectionPayload payload) {
        hasFirst = payload.hasFirst();
        hasSecond = payload.hasSecond();
        first = payload.first();
        second = payload.second();
        if (!hasFirst
                || selectedElement == MudTuningSelectionElement.SECOND && !hasSecond
                || selectedElement == MudTuningSelectionElement.BODY && !hasSecond) {
            selectedElement = MudTuningSelectionElement.NONE;
        }
        summary = payload.summary();
        highlightGroups = List.copyOf(payload.highlightGroups());
        highlightRevision++;
    }

    public static boolean hasFirst() {
        return hasFirst;
    }

    public static boolean hasSecond() {
        return hasSecond;
    }

    public static MudTuningAnchor first() {
        return first;
    }

    public static MudTuningAnchor second() {
        return second;
    }

    public static MudTuningSelectionElement selectedElement() {
        return selectedElement;
    }

    public static void selectElement(MudTuningSelectionElement element) {
        selectedElement = element == null
                ? MudTuningSelectionElement.NONE : element;
    }

    public static void cycleSelectedElement() {
        selectedElement = MudTuningSelectionElement.next(
                selectedElement, hasFirst, hasSecond);
    }

    public static List<MudTuningSelectionPayload.HighlightGroup> highlightGroups() {
        return highlightGroups;
    }

    public static long highlightRevision() {
        return highlightRevision;
    }

    public static MudTuningSelectionPayload.SelectionSummary summary() {
        return summary;
    }

    public static MudTuningWandMode mode() {
        return mode;
    }

    public static void cycleMode(int direction) {
        if (direction != 0) {
            mode = mode.cycle(direction);
            MudTuningClientSettings.saveMode(mode);
        }
    }

    public static void setMode(MudTuningWandMode next) {
        mode = next == null ? MudTuningWandMode.RANGE : next;
        MudTuningClientSettings.saveMode(mode);
    }

    public static MudTuningSummonType summonType() {
        return summonType;
    }

    public static void setSummonType(MudTuningSummonType next) {
        summonType = next == null ? MudTuningSummonType.TENTACLE : next;
        MudTuningTentacleTargeting.invalidate();
    }

    public static void expectGlobalScreen(GlobalScreen screen) {
        pendingGlobalScreen = screen == null ? GlobalScreen.SETTINGS : screen;
    }

    public static GlobalScreen consumeGlobalScreen() {
        GlobalScreen result = pendingGlobalScreen;
        pendingGlobalScreen = GlobalScreen.SETTINGS;
        return result;
    }

    public static void resetSession() {
        hasFirst = false;
        hasSecond = false;
        first = MudTuningAnchor.WORLD_ORIGIN;
        second = MudTuningAnchor.WORLD_ORIGIN;
        selectedElement = MudTuningSelectionElement.NONE;
        highlightGroups = List.of();
        highlightRevision++;
        summary = MudTuningSelectionPayload.SelectionSummary.EMPTY;
        pendingGlobalScreen = GlobalScreen.SETTINGS;
    }

    public enum GlobalScreen {
        SETTINGS,
        GENERATION
    }
}
