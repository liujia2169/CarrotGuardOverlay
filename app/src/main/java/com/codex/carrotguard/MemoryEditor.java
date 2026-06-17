package com.codex.carrotguard;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class MemoryEditor {
    private static final String TAG = "MemoryEditor";
    private final Handler handler = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onSuccess(String message);
        void onError(String error);
        void onProgress(String status);
    }

    public void getGamePid(String packageName, Callback callback) {
        new Thread(() -> {
            try {
                String pid = findPidByPackage(packageName);
                if (pid != null) {
                    notifySuccess(callback, "游戏PID: " + pid);
                } else {
                    notifyError(callback, "未找到游戏进程，请先启动游戏");
                }
            } catch (Exception e) {
                notifyError(callback, "获取PID失败: " + e.getMessage());
            }
        }).start();
    }

    public void searchValue(String packageName, int searchValue, Callback callback) {
        new Thread(() -> {
            try {
                String pid = findPidByPackage(packageName);
                if (pid == null) {
                    notifyError(callback, "未找到游戏进程");
                    return;
                }

                notifyProgress(callback, "正在搜索数值: " + searchValue);
                List<Long> addresses = searchMemory(pid, searchValue);
                
                if (addresses.isEmpty()) {
                    notifyError(callback, "未找到数值，可能需要先在游戏中看到该数值");
                } else {
                    notifySuccess(callback, "找到 " + addresses.size() + " 个匹配地址");
                }
            } catch (Exception e) {
                notifyError(callback, "搜索失败: " + e.getMessage());
            }
        }).start();
    }

    public void modifyValue(String packageName, int oldValue, int newValue, Callback callback) {
        new Thread(() -> {
            try {
                String pid = findPidByPackage(packageName);
                if (pid == null) {
                    notifyError(callback, "未找到游戏进程");
                    return;
                }

                notifyProgress(callback, "搜索原数值: " + oldValue);
                List<Long> addresses = searchMemory(pid, oldValue);
                
                if (addresses.isEmpty()) {
                    notifyError(callback, "未找到原数值");
                    return;
                }

                notifyProgress(callback, "找到 " + addresses.size() + " 个地址，正在修改...");
                int modified = modifyMemory(pid, addresses, newValue);
                
                notifySuccess(callback, "成功修改 " + modified + " 个地址，新值: " + newValue);
            } catch (Exception e) {
                notifyError(callback, "修改失败: " + e.getMessage());
            }
        }).start();
    }

    private String findPidByPackage(String packageName) throws Exception {
        Process process = Runtime.getRuntime().exec("su");
        DataOutputStream os = new DataOutputStream(process.getOutputStream());
        os.writeBytes("ps -A | grep " + packageName + "\n");
        os.writeBytes("exit\n");
        os.flush();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.contains(packageName) && !line.contains("grep")) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length > 1) {
                    return parts[1];
                }
            }
        }
        process.waitFor();
        return null;
    }

    private List<Long> searchMemory(String pid, int searchValue) throws Exception {
        List<Long> addresses = new ArrayList<>();
        
        String mapsPath = "/proc/" + pid + "/maps";
        String memPath = "/proc/" + pid + "/mem";

        Process process = Runtime.getRuntime().exec("su");
        DataOutputStream os = new DataOutputStream(process.getOutputStream());
        os.writeBytes("cat " + mapsPath + " | grep 'rw-p'\n");
        os.writeBytes("exit\n");
        os.flush();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        List<long[]> regions = new ArrayList<>();

        while ((line = reader.readLine()) != null) {
            String[] parts = line.split("\\s+");
            if (parts.length > 0) {
                String[] addrRange = parts[0].split("-");
                if (addrRange.length == 2) {
                    try {
                        long start = Long.parseLong(addrRange[0], 16);
                        long end = Long.parseLong(addrRange[1], 16);
                        regions.add(new long[]{start, end});
                    } catch (NumberFormatException e) {
                        // Skip invalid addresses
                    }
                }
            }
        }
        process.waitFor();

        byte[] searchBytes = intToBytes(searchValue);
        
        for (long[] region : regions) {
            try {
                List<Long> found = searchRegion(pid, region[0], region[1], searchBytes);
                addresses.addAll(found);
            } catch (Exception e) {
                // Skip inaccessible regions
            }
        }

        return addresses;
    }

    private List<Long> searchRegion(String pid, long start, long end, byte[] searchBytes) throws Exception {
        List<Long> addresses = new ArrayList<>();
        long regionSize = end - start;
        
        if (regionSize > 100 * 1024 * 1024 || regionSize <= 0) {
            return addresses;
        }

        String memPath = "/proc/" + pid + "/mem";
        Process process = Runtime.getRuntime().exec("su");
        DataOutputStream os = new DataOutputStream(process.getOutputStream());
        os.writeBytes("dd if=" + memPath + " bs=1 skip=" + start + " count=" + regionSize + " 2>/dev/null | xxd -p\n");
        os.writeBytes("exit\n");
        os.flush();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder hexBuilder = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            hexBuilder.append(line.trim());
        }
        process.waitFor();

        String hex = hexBuilder.toString();
        String searchHex = bytesToHex(searchBytes);

        int index = 0;
        while ((index = hex.indexOf(searchHex, index)) != -1) {
            long address = start + (index / 2);
            addresses.add(address);
            index += searchHex.length();
        }

        return addresses;
    }

    private int modifyMemory(String pid, List<Long> addresses, int newValue) throws Exception {
        int modified = 0;
        byte[] newBytes = intToBytes(newValue);
        String newHex = bytesToHex(newBytes);

        for (long address : addresses) {
            try {
                Process process = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(process.getOutputStream());
                os.writeBytes("printf '\\x" + newHex.substring(0, 2) + "\\x" + newHex.substring(2, 4) + 
                             "\\x" + newHex.substring(4, 6) + "\\x" + newHex.substring(6, 8) + 
                             "' | dd of=/proc/" + pid + "/mem bs=1 seek=" + address + " 2>/dev/null\n");
                os.writeBytes("exit\n");
                os.flush();
                int exitCode = process.waitFor();
                
                if (exitCode == 0) {
                    modified++;
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to modify address: " + Long.toHexString(address), e);
            }
        }

        return modified;
    }

    private byte[] intToBytes(int value) {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(value);
        return buffer.array();
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private void notifySuccess(Callback callback, String message) {
        handler.post(() -> callback.onSuccess(message));
    }

    private void notifyError(Callback callback, String error) {
        handler.post(() -> callback.onError(error));
    }

    private void notifyProgress(Callback callback, String status) {
        handler.post(() -> callback.onProgress(status));
    }
}
