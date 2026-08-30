package com.seeker.share.web;

import com.seeker.share.common.ApiResponse;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/server")
public class ServerInfoController {

	private final int port;
	private final Instant startedAt = Instant.now();

	public ServerInfoController(@Value("${server.port}") int port) {
		this.port = port;
	}

	@GetMapping
	public ApiResponse<ServerInfo> info() {
		return ApiResponse.ok(new ServerInfo(hostName(), accessUrls(), startedAt));
	}

	private List<String> accessUrls() {
		try {
			return NetworkInterface.networkInterfaces()
					.filter(this::isUsable)
					.flatMap(NetworkInterface::inetAddresses)
					.filter(address -> address instanceof Inet4Address && address.isSiteLocalAddress())
					.map(address -> "http://" + address.getHostAddress() + ":" + port)
					.distinct()
					.toList();
		} catch (SocketException exception) {
			return List.of();
		}
	}

	private boolean isUsable(NetworkInterface networkInterface) {
		try {
			return networkInterface.isUp() && !networkInterface.isLoopback() && !networkInterface.isVirtual();
		} catch (SocketException exception) {
			return false;
		}
	}

	private String hostName() {
		try {
			return InetAddress.getLocalHost().getHostName();
		} catch (Exception exception) {
			return "Seeker Share";
		}
	}
}
