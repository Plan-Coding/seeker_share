package com.seeker.share.share;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class ShareEventService {

	private final CopyOnWriteArrayList<ClientConnection> clients = new CopyOnWriteArrayList<>();

	public SseEmitter connect(String remoteAddress, String userAgent) {
		SseEmitter emitter = new SseEmitter(0L);
		Instant now = Instant.now();
		ClientConnection connection = new ClientConnection(
				emitter, normalizeAddress(remoteAddress), describe(userAgent), now, new AtomicReference<>(now));
		clients.add(connection);
		emitter.onCompletion(() -> disconnect(connection));
		emitter.onTimeout(() -> disconnect(connection));
		emitter.onError(error -> disconnect(connection));
		send(connection, "connected", now.toString());
		broadcast("devices", now.toString());
		return emitter;
	}

	public List<OnlineDevice> onlineDevices() {
		Map<String, List<ClientConnection>> grouped = clients.stream()
				.collect(Collectors.groupingBy(ClientConnection::deviceKey));
		return grouped.values().stream().map(connections -> {
			ClientConnection first = connections.getFirst();
			Instant connectedAt = connections.stream().map(ClientConnection::connectedAt)
					.min(Comparator.naturalOrder()).orElse(first.connectedAt());
			Instant lastSeen = connections.stream().map(connection -> connection.lastSeen().get())
					.max(Comparator.naturalOrder()).orElse(first.lastSeen().get());
			String id = UUID.nameUUIDFromBytes(first.deviceKey().getBytes(StandardCharsets.UTF_8)).toString();
			return new OnlineDevice(id, first.address(), first.device().name(), first.device().type(),
					first.device().browser(), connections.size(), connectedAt, lastSeen);
		}).sorted(Comparator.comparing(OnlineDevice::connectedAt)).toList();
	}

	public void publishRefresh() {
		broadcast("refresh", Instant.now().toString());
	}

	@Scheduled(fixedRate = 15_000)
	void heartbeat() {
		Instant now = Instant.now();
		for (ClientConnection client : clients) {
			client.lastSeen().set(now);
			send(client, "heartbeat", now.toString());
		}
	}

	private void broadcast(String eventName, String data) {
		for (ClientConnection client : clients) send(client, eventName, data);
	}

	private void send(ClientConnection client, String eventName, String data) {
		try {
			client.emitter().send(SseEmitter.event().name(eventName).data(data));
		} catch (IOException | IllegalStateException exception) {
			clients.remove(client);
			client.emitter().complete();
		}
	}

	private void disconnect(ClientConnection connection) {
		if (clients.remove(connection)) broadcast("devices", Instant.now().toString());
	}

	private String normalizeAddress(String address) {
		if (address == null || address.isBlank()) return "未知地址";
		return "0:0:0:0:0:0:0:1".equals(address) ? "127.0.0.1" : address;
	}

	private DeviceDescription describe(String userAgent) {
		String ua = userAgent == null ? "" : userAgent;
		String lower = ua.toLowerCase(Locale.ROOT);
		String type = lower.contains("mobile") || lower.contains("android") || lower.contains("iphone")
				? "MOBILE" : lower.contains("ipad") || lower.contains("tablet") ? "TABLET" : "DESKTOP";
		String system = lower.contains("iphone") || lower.contains("ipad") ? "iOS"
				: lower.contains("android") ? "Android"
				: lower.contains("windows") ? "Windows"
				: lower.contains("mac os") || lower.contains("macintosh") ? "macOS"
				: lower.contains("linux") ? "Linux" : "未知设备";
		String browser = lower.contains("edg/") ? "Edge"
				: lower.contains("firefox/") ? "Firefox"
				: lower.contains("chrome/") ? "Chrome"
				: lower.contains("safari/") ? "Safari" : "Browser";
		return new DeviceDescription(system + " · " + browser, type, browser);
	}

	private record DeviceDescription(String name, String type, String browser) { }

	private record ClientConnection(
			SseEmitter emitter,
			String address,
			DeviceDescription device,
			Instant connectedAt,
			AtomicReference<Instant> lastSeen) {
		String deviceKey() {
			return address + '|' + device.name();
		}
	}
}
