package jp.hakusyaku.simkirikae;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import rikka.shizuku.Shizuku;

public final class MainActivity extends Activity {
    private TextView status;
    private Button b1, b2;
    private boolean busy;
    private final Shizuku.OnRequestPermissionResultListener perm = (c, r) -> runOnUiThread(this::refresh);
    private final Shizuku.OnBinderReceivedListener binder = () -> runOnUiThread(this::refresh);

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        int pad = (int) (24 * dp);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.parseColor("#F4F6F8"));

        TextView title = new TextView(this);
        title.setText("データ通信のSIM");
        title.setTextSize(22);
        title.setTextColor(Color.parseColor("#222222"));
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        status = new TextView(this);
        status.setTextSize(16);
        status.setTextColor(Color.parseColor("#555555"));
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, (int) (8 * dp), 0, (int) (24 * dp));
        root.addView(status);

        b1 = bigButton("SIM①", dp);
        b2 = bigButton("SIM②", dp);
        root.addView(b1);
        root.addView(b2);
        b1.setOnClickListener(v -> doSwitch(0));
        b2.setOnClickListener(v -> doSwitch(1));

        TextView hint = new TextView(this);
        hint.setText("ホーム画面のアイコンを長押しすると「SIM①」「SIM②」のショートカットが出ます（ホームに置けます）。\nクイック設定に「データ SIM①」「データ SIM②」のタイルも追加できます。");
        hint.setTextSize(13);
        hint.setTextColor(Color.parseColor("#777777"));
        hint.setPadding(0, (int) (24 * dp), 0, 0);
        root.addView(hint);
        setContentView(root);

        Shizuku.addRequestPermissionResultListener(perm);
        Shizuku.addBinderReceivedListenerSticky(binder);
    }

    private Button bigButton(String text, float dp) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(28);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (96 * dp));
        lp.setMargins(0, (int) (8 * dp), 0, (int) (8 * dp));
        b.setLayoutParams(lp);
        color(b, false);
        return b;
    }

    private void color(Button b, boolean active) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(24 * getResources().getDisplayMetrics().density);
        g.setColor(Color.parseColor(active ? "#1C7ED6" : "#ADB5BD"));
        b.setBackground(g);
    }

    @Override
    protected void onResume() { super.onResume(); refresh(); }

    @Override
    protected void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(perm);
        Shizuku.removeBinderReceivedListener(binder);
        super.onDestroy();
    }

    private void refresh() {
        if (!Shizuku.pingBinder()) { status.setText("Shizukuが動いていません。\nPCにつないで「2_Shizukuを起動する.bat」を実行してください。"); return; }
        try {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                status.setText("Shizukuの許可を待っています…");
                Shizuku.requestPermission(1);
                return;
            }
        } catch (RuntimeException e) { status.setText("Shizukuに接続できません"); return; }
        Switcher.load(this, (st, msg, ok) -> show(st, msg));
    }

    private void show(SimSwitcher.State st, String err) {
        if (st == null) { status.setText("読み込みに失敗：" + err); return; }
        SimSwitcher.Sim cur = st.current();
        status.setText(cur == null ? "使用中のSIM：なし" : "使用中：" + cur.label());
        SimSwitcher.Sim s1 = st.bySlot(0), s2 = st.bySlot(1);
        b1.setText(s1 == null ? "SIM①（なし）" : s1.label());
        b2.setText(s2 == null ? "SIM②（なし）" : s2.label());
        b1.setEnabled(s1 != null); b2.setEnabled(s2 != null);
        color(b1, s1 != null && s1.subId == st.currentSubId);
        color(b2, s2 != null && s2.subId == st.currentSubId);
    }

    private void doSwitch(int slot) {
        if (busy) return;
        busy = true;
        status.setText("切り替え中…");
        Switcher.run(this, slot, (st, msg, ok) -> { busy = false; if (st != null) show(st, null); else status.setText(msg); });
    }
}
