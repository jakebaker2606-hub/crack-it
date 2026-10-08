package com.together.camera;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

public final class NetworkUtil {
    private NetworkUtil() {}

    public static String localIpv4() {
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || nif.isLoopback()) continue;
                for (InetAddress address : Collections.list(nif.getInetAddresses())) {
                    if (!(address instanceof Inet4Address) || address.isLoopbackAddress()) continue;
                    String ip = address.getHostAddress();
                    if (isPrivate(ip)) return ip;
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    public static boolean isPrivate(String ip) {
        if (ip == null) return false;
        String[] p = ip.split("\\.");
        if (p.length != 4) return false;
        try {
            int a = Integer.parseInt(p[0]), b = Integer.parseInt(p[1]);
            return a == 10 || (a == 172 && b >= 16 && b <= 31) || (a == 192 && b == 168) || (a == 169 && b == 254);
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
