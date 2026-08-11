package com.ghostlock.skeleton;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
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
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {

    private static final int REQUEST_PICK_FILE = 100;
    private static final int REQUEST_CODE = 42;
    private static final String TAG = "GhostLockMain";

    /** shell 用户可读写的公共临时目录 */
    private static final String PAYLOAD_DIR = "/data/local/tmp";

    private TextView mStatus;
    private TextView mOutput;
    private TextView mFilePath;

    private IUserService mService;
    private volatile boolean mServiceBound;

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
        mService = null;
        mServiceBound = false;
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

        // 确保 /data/local/tmp 存在
        new File(PAYLOAD_DIR).mkdirs();

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

        LinearLayout btns1 = new LinearLayout(this);
        btns1.setOrientation(LinearLayout.HORIZONTAL);

        Button btnBind = new Button(this);
        btnBind.setText("绑定服务");
        btnBind.setOnClickListener(v -> bindService());
        btns1.addView(btnBind, lp(0, -2, 1));

        Button btnExec = new Button(this);
        btnExec.setText("执行 id");
        btnExec.setOnClickListener(v -> exec("id"));
        btns1.addView(btnExec, lp(0, -2, 1));

        Button btnUnbind = new Button(this);
        btnUnbind.setText("解绑");
        btnUnbind.setOnClickListener(v -> unbindService());
        btns1.addView(btnUnbind, lp(0, -2, 1));

        root.addView(btns1, lp(-1, -2, 0));

        // ─── 文件选择区域 ───
        TextView lblFile = new TextView(this);
        lblFile.setText("载荷文件（.so / ELF）→ /data/local/tmp/:");
        lblFile.setPadding(0, 20, 0, 4);
        root.addView(lblFile, lp(-1, -2, 0));

        LinearLayout btns2 = new LinearLayout(this);
        btns2.setOrientation(LinearLayout.HORIZONTAL);

        Button btnPick = new Button(this);
        btnPick.setText("选择文件");
        btnPick.setOnClickListener(v -> pickFile());
        btns2.addView(btnPick, lp(0, -2, 1));

        Button btnLoad = new Button(this);
        btnLoad.setText("加载执行");
        btnLoad.setOnClickListener(v -> loadAndExec());
        btns2.addView(btnLoad, lp(0, -2, 1));

        Button btnClear = new Button(this);
        btnClear.setText("清除");
        btnClear.setOnClickListener(v -> clearFile());
        btns2.addView(btnClear, lp(0, -2, 1));

        root.addView(btns2, lp(-1, -2, 0));

        mFilePath = new TextView(this);
        mFilePath.setText("未选择");
        mFilePath.setTextSize(11);
        mFilePath.setPadding(0, 6, 0, 10);
        mFilePath.setTypeface(android.graphics.Typeface.MONOSPACE);
        root.addView(mFilePath, lp(-1, -2, 0));

        // ─── 快捷操作 ───
        LinearLayout btns3 = new LinearLayout(this);
        btns3.setOrientation(LinearLayout.HORIZONTAL);

        Button btnTrigger = new Button(this);
        btnTrigger.setText("触发");
        btnTrigger.setOnClickListener(v -> exec("ghostlock trigger"));
        btns3.addView(btnTrigger, lp(0, -2, 1));

        Button btnSpray = new Button(this);
        btnSpray.setText("喷射");
        btnSpray.setOnClickListener(v -> exec("ghostlock spray"));
        btns3.addView(btnSpray, lp(0, -2, 1));

        Button btnWrite = new Button(this);
        btnWrite.setText("写入");
        btnWrite.setOnClickListener(v -> exec("ghostlock write"));
        btns3.addView(btnWrite, lp(0, -2, 1));

        Button btnPwn = new Button(this);
        btnPwn.setText("提权");
        btnPwn.setOnClickListener(v -> exec("ghostlock pwn"));
        btns3.addView(btnPwn, lp(0, -2, 1));

        root.addView(btns3, lp(-1, -2, 0));

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

    // ═══════════════ 文件选择器 ═══════════════

    private Uri mPickedUri;
    private String mPickedPath;

    private void pickFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimeTypes = {
            "application/octet-stream",
            "application/x-sharedlib",
            "application/x-executable"
        };
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        startActivityForResult(intent, REQUEST_PICK_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_FILE && resultCode == RESULT_OK && data != null) {
            mPickedUri = data.getData();
            if (mPickedUri != null) {
                String displayName = getDisplayName(mPickedUri);
                mFilePath.setText("已选择: " + displayName);
                try {
                    getContentResolver().takePersistableUriPermission(
                            mPickedUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {}
                mOutput.setText("文件已选中，点击「加载执行」可复制到 " + PAYLOAD_DIR + " 并加载");
            }
        }
    }

    private String getDisplayName(Uri uri) {
        String result = null;
        try {
            android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null) {
                int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                cursor.moveToFirst();
                if (nameIndex >= 0) result = cursor.getString(nameIndex);
                cursor.close();
            }
        } catch (Exception ignored) {}
        if (result == null) result = uri.getLastPathSegment();
        return result;
    }

    /**
     * 复制到 /data/local/tmp（shell 用户可读写），然后通过 native 层加载。
     */
    private void loadAndExec() {
        if (mPickedUri == null) {
            mOutput.setText("请先点击「选择文件」");
            return;
        }

        String displayName = getDisplayName(mPickedUri);
        if (displayName == null) displayName = "payload.so";

        File outFile = new File(PAYLOAD_DIR, displayName);
        try {
            InputStream in = getContentResolver().openInputStream(mPickedUri);
            OutputStream out = new FileOutputStream(outFile);
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            out.close();
            in.close();

            // 确保 shell 可读
            outFile.setReadable(true, false);
            outFile.setExecutable(true, false);

            mPickedPath = outFile.getAbsolutePath();
            mFilePath.setText("就绪: " + mPickedPath);
            mOutput.setText("已复制到 " + mPickedPath + "，正在加载...\n");

            exec("ghostlock load " + mPickedPath);
        } catch (Exception e) {
            mOutput.setText("复制失败: " + e.getMessage());
        }
    }

    private void clearFile() {
        mPickedUri = null;
        mPickedPath = null;
        mFilePath.setText("未选择");
        mOutput.setText("已清除");
        // 清理临时目录
        File dir = new File(PAYLOAD_DIR);
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    String name = f.getName();
                    if (name.endsWith(".so") || name.endsWith(".elf")) {
                        f.delete();
                    }
                }
            }
        }
    }

    // ═══════════════ Shizuku 操作 ═══════════════

    private void bindService() {
        if (!Shizuku.pingBinder()) {
            Toast.makeText(this, "请先启动 Shizuku 或 Sui", Toast.LENGTH_SHORT).show();
            mStatus.setText("未检测到 Shizuku");
            return;
        }
        if (mServiceBound) {
            mOutput.setText("服务已连接，无需重复绑定");
            return;
        }
        if (!checkPermission()) return;
        try {
            Shizuku.bindUserService(mServiceArgs, mServiceConn);
            mOutput.setText("正在绑定...");
        } catch (Exception e) {
            mOutput.setText("绑定失败: " + e.getMessage());
            Toast.makeText(this, "绑定失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void unbindService() {
        if (!mServiceBound) {
            mOutput.setText("服务未连接");
            return;
        }
        try {
            Shizuku.unbindUserService(mServiceArgs, mServiceConn, true);
        } catch (Exception e) {
            Log.w(TAG, "unbind error", e);
        }
        mService = null;
        mServiceBound = false;
        mOutput.setText("已解绑");
    }

    private void exec(String cmd) {
        if (!Shizuku.pingBinder()) {
            mOutput.setText("Shizuku 未运行");
            return;
        }
        if (!mServiceBound || mService == null) {
            mOutput.setText("服务未连接，请先点击绑定");
            return;
        }
        try {
            String result = mService.exec(cmd);
            mOutput.setText(result);
        } catch (RemoteException e) {
            mOutput.setText("执行失败: " + e.getMessage());
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
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                w < 0 ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT,
                h < 0 ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT,
                weight);
        p.setMargins(4, 4, 4, 4);
        return p;
    }
}
