package com.seeker.share.share;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class ShareEventService {

	private final CopyOnWriteArrayList<SseEmitter> clients = new CopyOnWriteArrayList<>();

	public SseEmitter connect() {
		SseEmitter emitter = new SseEmitter(0L);
		clients.add(emitter);
		emitter.onCompletion(() -> clients.remove(emitter));
		emitter.onTimeout(() -> clients.remove(emitter));
		emitter.onError(error -> clients.remove(emitter));
		send(emitter, "connected", Instant.now().toString());
		return emitter;
	}

	public void publishRefresh() {
		for (SseEmitter client : clients) {
			send(client, "refresh", Instant.now().toString());
		}
	}

	@Scheduled(fixedRate = 15_000)
	void heartbeat() {
		for (SseEmitter client : clients) {
			send(client, "heartbeat", Instant.now().toString());
		}
	}

	private void send(SseEmitter emitter, String eventName, String data) {
		try {
			emitter.send(SseEmitter.event().name(eventName).data(data));
		} catch (IOException | IllegalStateException exception) {
			clients.remove(emitter);
			emitter.complete();
		}
	}
}
