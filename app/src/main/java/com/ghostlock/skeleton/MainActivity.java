package com.ghostlock.skeleton;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {

    private static final int REQUEST_CODE = 42;
    private static final String TAG = "GhostLockMain";

    private TextView mStatus;
    private TextView mOutput;

    private IUserService mService;
    private boolean mServiceBound;

    private final Shizuku.UserServiceArgs mServiceArgs =
            new Shizuku.UserServiceArgs(
                    new ComponentName(BuildConfig.APPLICATION_ID, UserService.class.getName()))
                    .daemon(false)
                    .processNameSuffix("service")
                    .debuggable(true)
                    .version(BuildConfig.VERSION_CODE);

    private final ServiceConnection mServiceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            mService = IUserService.Stub.asInterface(binder);
            mServiceBound = true;
            mStatus.setText("用户服务已连接 — uid=" + Shizuku.getUid());
            Log.i(TAG, "UserService connected");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            mServiceBound = false;
            mStatus.setText("用户服务已断开");
        }
    };

    private final Shizuku.OnBinderReceivedListener BINDER_RECEIVED = () -> {
        mStatus.setText("Shizuku 已就绪 — uid=" + Shizuku.getUid());
    };

    private final Shizuku.OnBinderDeadListener BINDER_DEAD = () -> {
        mStatus.setText("Binder 已断开");
        mServiceBound = false;
        mService = null;
    };

    private final Shizuku.OnRequestPermissionResultListener PERM_LISTENER = (code, result) -> {
        if (result == PackageManager.PERMISSION_GRANTED) {
            mStatus.setText("权限已授予");
        } else {
            mStatus.setText("权限被拒绝");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 40, 40, 40);

        TextView title = new TextView(this);
        title.setText("GhostLock");
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER);
        root.addView(title, lp(0, -2, 1));

        mStatus = new TextView(this);
        mStatus.setText("等待 Shizuku...");
        mStatus.setPadding(0, 20, 0, 10);
        root.addView(mStatus, lp(0, -2, 1));

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);

        Button btnBind = new Button(this);
        btnBind.setText("绑定服务");
        btnBind.setOnClickListener(v -> bindService());
        btns.addView(btnBind, lp(0, -2, 1));

        Button btnExec = new Button(this);
        btnExec.setText("执行 id");
        btnExec.setOnClickListener(v -> exec("id"));
        btns.addView(btnExec, lp(0, -2, 1));

        Button btnUnbind = new Button(this);
        btnUnbind.setText("解绑");
        btnUnbind.setOnClickListener(v -> unbindService());
        btns.addView(btnUnbind, lp(0, -2, 1));

        root.addView(btns, lp(-1, -2, 0));

        mOutput = new TextView(this);
        mOutput.setPadding(0, 20, 0, 0);
        mOutput.setTextSize(12);
        mOutput.setTypeface(android.graphics.Typeface.MONOSPACE);
        root.addView(mOutput, lp(0, -1, 1));

        scroll.addView(root);
        setContentView(scroll);

        Shizuku.addBinderReceivedListenerSticky(BINDER_RECEIVED);
        Shizuku.addBinderDeadListener(BINDER_DEAD);
        Shizuku.addRequestPermissionResultListener(PERM_LISTENER);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Shizuku.removeBinderReceivedListener(BINDER_RECEIVED);
        Shizuku.removeBinderDeadListener(BINDER_DEAD);
        Shizuku.removeRequestPermissionResultListener(PERM_LISTENER);
    }

    private void bindService() {
        if (!checkPermission()) return;
        try {
            Shizuku.bindUserService(mServiceArgs, mServiceConn);
            mOutput.setText("正在绑定...");
        } catch (Exception e) {
            mOutput.setText("错误: " + e.getMessage());
        }
    }

    private void unbindService() {
        if (mServiceBound) {
            Shizuku.unbindUserService(mServiceArgs, mServiceConn, true);
            mServiceBound = false;
            mOutput.setText("已解绑");
        }
    }

    private void exec(String cmd) {
        if (mService == null) {
            mOutput.setText("服务未连接");
            return;
        }
        try {
            String result = mService.exec(cmd);
            mOutput.setText(result);
        } catch (RemoteException e) {
            mOutput.setText("错误: " + e.getMessage());
        }
    }

    private boolean checkPermission() {
        if (Shizuku.isPreV11()) {
            mStatus.setText("Shizuku 版本太旧");
            return false;
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        if (Shizuku.shouldShowRequestPermissionRationale()) {
            mStatus.setText("权限已被永久拒绝");
            return false;
        }
        Shizuku.requestPermission(REQUEST_CODE);
        return false;
    }

    private LinearLayout.LayoutParams lp(int w, int h, float weight) {
        LinearLayout.LayoutParams p;
        p = new LinearLayout.LayoutParams(
                w < 0 ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT,
                h < 0 ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT,
                weight);
        p.setMargins(4, 4, 4, 4);
        return p;
    }
}
