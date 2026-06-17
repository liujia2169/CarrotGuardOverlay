package com.codex.carrotguard;

import android.os.Handler;
import android.os.Looper;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class MemoryEditor {
    private final Handler handler = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onSuccess(String msg);
        void onError(String err);
        void onProgress(String s);
    }

    public void modify(String pkg, int oldVal, int newVal, Callback cb) {
        new Thread(() -> {
            try {
                String pid = findPid(pkg);
                if (pid == null) { cb.onError("未找到游戏进程"); return; }
                cb.onProgress("搜索数值:" + oldVal);
                List<Long> addrs = search(pid, oldVal);
                if (addrs.isEmpty()) { cb.onError("未找到数值"); return; }
                cb.onProgress("找到" + addrs.size() + "个地址");
                int count = write(pid, addrs, newVal);
                cb.onSuccess("修改成功" + count + "个");
            } catch (Exception e) {
                cb.onError(e.getMessage());
            }
        }).start();
    }

    private String findPid(String pkg) throws Exception {
        Process p = Runtime.getRuntime().exec("su");
        DataOutputStream os = new DataOutputStream(p.getOutputStream());
        os.writeBytes("ps -A | grep " + pkg + "\nexit\n");
        os.flush();
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        while ((line = r.readLine()) != null) {
            if (line.contains(pkg) && !line.contains("grep")) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length > 1) return parts[1];
            }
        }
        p.waitFor();
        return null;
    }

    private List<Long> search(String pid, int val) throws Exception {
        List<Long> addrs = new ArrayList<>();
        byte[] target = intToBytes(val);
        Process p = Runtime.getRuntime().exec("su");
        DataOutputStream os = new DataOutputStream(p.getOutputStream());
        os.writeBytes("cat /proc/" + pid + "/maps | grep 'rw-p'\nexit\n");
        os.flush();
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        List<long[]> regions = new ArrayList<>();
        while ((line = r.readLine()) != null) {
            String[] parts = line.split("\\s+");
            if (parts.length > 0) {
                String[] range = parts[0].split("-");
                if (range.length == 2) {
                    try {
                        long start = Long.parseLong(range[0], 16);
                        long end = Long.parseLong(range[1], 16);
                        regions.add(new long[]{start, end});
                    } catch (NumberFormatException e) {}
                }
            }
        }
        p.waitFor();
        for (long[] region : regions) {
            try {
                addrs.addAll(searchRegion(pid, region[0], region[1], target));
            } catch (Exception e) {}
        }
        return addrs;
    }

    private List<Long> searchRegion(String pid, long start, long end, byte[] target) throws Exception {
        List<Long> addrs = new ArrayList<>();
        long size = end - start;
        if (size > 100*1024*1024 || size <= 0) return addrs;
        Process p = Runtime.getRuntime().exec("su");
        DataOutputStream os = new DataOutputStream(p.getOutputStream());
        os.writeBytes("dd if=/proc/" + pid + "/mem bs=1 skip=" + start + " count=" + size + " 2>/dev/null | xxd -p\nexit\n");
        os.flush();
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
        StringBuilder hex = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) hex.append(line.trim());
        p.waitFor();
        String h = bytesToHex(target);
        int idx = 0;
        while ((idx = hex.indexOf(h, idx)) != -1) {
            addrs.add(start + idx / 2);
            idx += h.length();
        }
        return addrs;
    }

    private int write(String pid, List<Long> addrs, int val) throws Exception {
        byte[] bytes = intToBytes(val);
        int count = 0;
        for (long addr : addrs) {
            try {
                Process p = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(p.getOutputStream());
                os.writeBytes("printf '\\x" + String.format("%02x", bytes[0]) + "\\x" + String.format("%02x", bytes[1]) + "\\x" + String.format("%02x", bytes[2]) + "\\x" + String.format("%02x", bytes[3]) + "' | dd of=/proc/" + pid + "/mem bs=1 seek=" + addr + " 2>/dev/null\nexit\n");
                os.flush();
                if (p.waitFor() == 0) count++;
            } catch (Exception e) {}
        }
        return count;
    }

    private byte[] intToBytes(int v) {
        ByteBuffer b = ByteBuffer.allocate(4);
        b.order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(v);
        return b.array();
    }

    private String bytesToHex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format("%02x", x));
        return s.toString();
    }
}
