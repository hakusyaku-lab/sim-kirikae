package jp.hakusyaku.simkirikae;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuBinderWrapper;
import rikka.shizuku.SystemServiceHelper;

/**
 * Shizuku 経由でデータ通信のSIMを切り替える（設定アプリのデータSIM選択と同じ処理）。
 * サービスの取得結果を使い回して、2回目以降の切り替えを速くする。
 */
final class SimSwitcher {
    static final class Sim {
        final int subId, slot;
        final String name;
        Sim(int subId, int slot, String name) { this.subId = subId; this.slot = slot; this.name = name; }
        String label() { return "SIM" + (slot == 0 ? "①" : slot == 1 ? "②" : String.valueOf(slot + 1)) + (name == null ? "" : " " + name); }
    }

    static final class State {
        final List<Sim> sims;
        final int currentSubId;
        State(List<Sim> sims, int currentSubId) { this.sims = sims; this.currentSubId = currentSubId; }
        Sim current() { for (Sim s : sims) if (s.subId == currentSubId) return s; return null; }
        Sim bySlot(int slot) { for (Sim s : sims) if (s.slot == slot) return s; return null; }
        Sim next() {
            if (sims.isEmpty()) return null;
            for (int i = 0; i < sims.size(); i++) if (sims.get(i).subId == currentSubId) return sims.get((i + 1) % sims.size());
            return sims.get(0);
        }
    }

    private static final String ISUB = "com.android.internal.telephony.ISub";
    private static final String ITELEPHONY = "com.android.internal.telephony.ITelephony";
    private static final String SHELL_PACKAGE = "com.android.shell";
    private static Object subService, phoneService;
    private static boolean exempted;

    private SimSwitcher() {}

    static boolean shizukuReady() {
        try { return Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED; }
        catch (RuntimeException e) { return false; }
    }

    private static String callingPackage(Context c) {
        return Shizuku.getUid() == 2000 ? SHELL_PACKAGE : c.getPackageName();
    }

    private static synchronized Object service(String name, String iface, boolean phone) throws Exception {
        Object cached = phone ? phoneService : subService;
        if (cached != null) {
            IBinder b = (IBinder) cached.getClass().getMethod("asBinder").invoke(cached);
            if (b != null && b.pingBinder()) return cached;
        }
        if (Build.VERSION.SDK_INT >= 28 && !exempted) {
            if (!HiddenApiBypass.addHiddenApiExemptions("")) throw new IllegalStateException("hidden API");
            exempted = true;
        }
        IBinder binder = SystemServiceHelper.getSystemService(name);
        if (binder == null) throw new IllegalStateException(name + " がありません");
        Object s = Class.forName(iface + "$Stub").getMethod("asInterface", IBinder.class)
                .invoke(null, new ShizukuBinderWrapper(binder));
        if (phone) phoneService = s; else subService = s;
        return s;
    }

    static State load(Context c) throws Exception {
        Object sub = service("isub", ISUB, false);
        int current = (Integer) call(sub, ISUB, "getDefaultDataSubId", new Class<?>[0]);
        Object value;
        String pkg = callingPackage(c);
        try {
            value = call(sub, ISUB, "getActiveSubscriptionInfoList", new Class<?>[]{String.class, String.class, boolean.class}, pkg, null, true);
        } catch (NoSuchMethodException e) {
            try {
                value = call(sub, ISUB, "getActiveSubscriptionInfoList", new Class<?>[]{String.class, String.class}, pkg, null);
            } catch (NoSuchMethodException e2) {
                value = call(sub, ISUB, "getActiveSubscriptionInfoList", new Class<?>[]{String.class}, pkg);
            }
        }
        List<Sim> sims = new ArrayList<>();
        if (value instanceof List<?>) {
            for (Object o : (List<?>) value) {
                if (!(o instanceof SubscriptionInfo)) continue;
                SubscriptionInfo i = (SubscriptionInfo) o;
                CharSequence n = i.getDisplayName();
                if (n == null || n.toString().trim().isEmpty()) n = i.getCarrierName();
                sims.add(new Sim(i.getSubscriptionId(), i.getSimSlotIndex(), n == null ? null : n.toString().trim()));
            }
        }
        sims.sort(Comparator.comparingInt(s -> s.slot));
        return new State(sims, current);
    }

    /** 指定のSIMをデータ通信用にする。すでにそのSIMなら何もしない。 */
    static void switchTo(Context c, Sim target, int currentSubId) throws Exception {
        if (target.subId == currentSubId) return;
        Object sub = service("isub", ISUB, false);
        call(sub, ISUB, "setDefaultDataSubId", new Class<?>[]{int.class}, target.subId);
        enableData(c, target.subId);
    }

    private static void enableData(Context c, int subId) throws Exception {
        Object ph = service("phone", ITELEPHONY, true);
        String pkg = Shizuku.getUid() == 0 ? null : callingPackage(c);
        try {
            call(ph, ITELEPHONY, "setDataEnabledForReason", new Class<?>[]{int.class, int.class, boolean.class, String.class},
                    subId, TelephonyManager.DATA_ENABLED_REASON_USER, true, pkg);
        } catch (NoSuchMethodException e) {
            try {
                call(ph, ITELEPHONY, "setDataEnabledForReason", new Class<?>[]{int.class, int.class, boolean.class},
                        subId, TelephonyManager.DATA_ENABLED_REASON_USER, true);
            } catch (NoSuchMethodException e2) {
                call(ph, ITELEPHONY, "setUserDataEnabled", new Class<?>[]{int.class, boolean.class}, subId, true);
            }
        }
    }

    private static Object call(Object target, String iface, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = Class.forName(iface).getMethod(name, types);
        try {
            return m.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable t = e.getCause();
            if (t instanceof Exception) throw (Exception) t;
            if (t instanceof Error) throw (Error) t;
            throw e;
        }
    }
}
