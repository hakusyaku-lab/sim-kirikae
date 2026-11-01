package jp.hakusyaku.simkirikae;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import rikka.shizuku.Shizuku;

public final class ShortcutActivity extends Activity {
    static final String EXTRA_SLOT = "slot";
    private final Handler main = new Handler(Looper.getMainLooper());
    private int slot;
    private boolean started;
    private final Shizuku.OnRequestPermissionResultListener permListener = (code, result) -> {
        if (result == PackageManager.PERMISSION_GRANTED) go();
        else { Toast.makeText(this, "Shizukuの許可がないので切り替えできません", Toast.LENGTH_LONG).show(); finish(); }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Intent i = getIntent();
        String s = i.getStringExtra(EXTRA_SLOT);
        slot = "1".equals(s) ? 0 : "2".equals(s) ? 1 : Switcher.TOGGLE;
        Shizuku.addRequestPermissionResultListener(permListener);
        tryStart(0);
    }

    private void tryStart(int tries) {
        if (Shizuku.pingBinder()) {
            try {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) { go(); return; }
                Shizuku.requestPermission(1);
                return;
            } catch (RuntimeException ignored) {}
        }
        if (tries < 20) { main.postDelayed(() -> tryStart(tries + 1), 100); return; }
        Toast.makeText(this, "Shizukuが動いていません。PCで「2_Shizukuを起動する.bat」を実行してください", Toast.LENGTH_LONG).show();
        finish();
    }

    private void go() {
        if (started) return;
        started = true;
        Switcher.run(this, slot, null);
        finish();
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permListener);
        super.onDestroy();
    }
}
