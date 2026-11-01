package jp.hakusyaku.simkirikae;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class Switcher {
    interface Done { void done(SimSwitcher.State state, String message, boolean ok); }

    static final int TOGGLE = -1;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Switcher() {}

    /** slot: 0=SIM①, 1=SIM②, TOGGLE=もう片方へ */
    static void run(Context ctx, int slot, Done done) {
        Context app = ctx.getApplicationContext();
        WORKER.execute(() -> {
            String msg; boolean ok = false; SimSwitcher.State after = null;
            try {
                SimSwitcher.State st = SimSwitcher.load(app);
                SimSwitcher.Sim target = slot == TOGGLE ? st.next() : st.bySlot(slot);
                if (target == null) {
                    msg = "SIM" + (slot == 1 ? "②" : "①") + " が見つかりません";
                } else if (target.subId == st.currentSubId) {
                    msg = "すでに " + target.label() + " です"; ok = true; after = st;
                } else {
                    SimSwitcher.switchTo(app, target, st.currentSubId);
                    after = new SimSwitcher.State(st.sims, target.subId);
                    msg = target.label() + " に切り替えました"; ok = true;
                }
            } catch (Throwable e) {
                msg = "切り替えできませんでした：" + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " " + e.getMessage());
            }
            String m = msg; boolean o = ok; SimSwitcher.State a = after;
            MAIN.post(() -> {
                Toast.makeText(app, m, Toast.LENGTH_SHORT).show();
                if (done != null) done.done(a, m, o);
            });
        });
    }

    static void load(Context ctx, Done done) {
        Context app = ctx.getApplicationContext();
        WORKER.execute(() -> {
            SimSwitcher.State st = null; String msg = null;
            try { st = SimSwitcher.load(app); } catch (Throwable e) { msg = e.getClass().getSimpleName() + " " + e.getMessage(); }
            SimSwitcher.State s = st; String m = msg;
            MAIN.post(() -> done.done(s, m, s != null));
        });
    }
}
