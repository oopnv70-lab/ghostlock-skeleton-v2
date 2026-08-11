package com.ghostlock.skeleton;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class UserService extends IUserService.Stub {

    private static final String TAG = "GhostLockService";

    @Override
    public void destroy() {
        Log.i(TAG, "destroy");
        System.exit(0);
    }

    @Override
    public String exec(String command) {
        Log.i(TAG, "exec: " + command);
        StringBuilder output = new StringBuilder();
        try {
            java.lang.Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = r.readLine()) != null) {
                output.append(line).append("\n");
            }
            BufferedReader er = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            while ((line = er.readLine()) != null) {
                output.append(line).append("\n");
            }
            p.waitFor();
            output.append("exit=").append(p.exitValue());
        } catch (Exception e) {
            output.append("ERROR: ").append(e.getMessage());
        }
        return output.toString().trim();
    }
}
