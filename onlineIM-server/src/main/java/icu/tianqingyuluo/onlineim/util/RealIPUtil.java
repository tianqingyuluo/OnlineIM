package icu.tianqingyuluo.onlineim.util;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class RealIPUtil {

    public static String getRealLocalIP() throws SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface iface = interfaces.nextElement();
            // 跳过虚拟接口（WSL、Docker、Hyper-V等）和未启用的接口
            if (iface.isLoopback() || !iface.isUp() || isVirtualInterface(iface)) {
                continue;
            }
            // 遍历接口的IP地址
            Enumeration<InetAddress> addresses = iface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress addr = addresses.nextElement();
                if (addr instanceof Inet4Address) { // 优先IPv4
                    return addr.getHostAddress();
                }
            }
        }
        throw new RuntimeException("No physical network interface found");
    }

    // 判断是否为虚拟接口（根据名称或特征过滤）
    private static boolean isVirtualInterface(NetworkInterface iface) {
        String name = iface.getDisplayName().toLowerCase();
        // 常见虚拟接口关键词（可根据需要扩展）
        return name.contains("wsl")
                || name.contains("hyper-v")
                || name.contains("docker")
                || name.contains("virtual")
                || name.contains("vether")
                || name.contains("vmware")
                || name.contains("vbox");
    }
}
