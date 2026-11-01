package jp.hakusyaku.simkirikae;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import rikka.shizuku.Shizuku;

public abstract class SimTileBase extends TileService {
    abstract int slot();

    private String mark() { return slot() == 0 ? "①" : "②"; }

    @Override
    public void onStartListening() { refresh(); }

    @Override
    public void onClick() {
        if (!SimSwitcher.shizukuReady()) { openApp(); return; }
        // 押した瞬間に表示を切り替え先にしておく（実際の切り替えは裏で）
        show(Tile.STATE_ACTIVE, "切り替え中…");
        Switcher.run(this, slot(), (state, msg, ok) -> {
            if (state != null) apply(state); else show(Tile.STATE_INACTIVE, "失敗");
            // もう片方のタイルも更新
            poke(this, slot() == 0 ? Sim2Tile.class : Sim1Tile.class);
        });
    }

    private void refresh() {
        if (!SimSwitcher.shizukuReady()) { show(Tile.STATE_INACTIVE, Shizuku.pingBinder() ? "許可が必要" : "Shizuku停止中"); return; }
        Switcher.load(this, (state, msg, ok) -> { if (state != null) apply(state); else show(Tile.STATE_UNAVAILABLE, "読込失敗"); });
    }

    private void apply(SimSwitcher.State st) {
        SimSwitcher.Sim me = st.bySlot(slot());
        if (me == null) { show(Tile.STATE_UNAVAILABLE, "SIMなし"); return; }
        boolean on = me.subId == st.currentSubId;
        show(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE, (me.name == null ? "" : me.name) + (on ? "（使用中）" : ""));
    }

    private void show(int state, String sub) {
        Tile t = getQsTile();
        if (t == null) return;
        t.setLabel("データ SIM" + mark());
        if (Build.VERSION.SDK_INT >= 29) t.setSubtitle(sub);
        t.setState(state);
        t.updateTile();
    }

    private static void poke(TileService s, Class<?> cls) {
        try { TileService.requestListeningState(s, new android.content.ComponentName(s, cls)); } catch (RuntimeException ignored) {}
    }

    @SuppressWarnings("deprecation")
    private void openApp() {
        Intent i = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE));
        else startActivityAndCollapse(i);
    }
}
