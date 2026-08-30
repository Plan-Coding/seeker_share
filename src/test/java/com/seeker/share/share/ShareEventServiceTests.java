package com.seeker.share.share;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class ShareEventServiceTests {

	@Test
	void groupsConnectionsFromTheSameDevice() {
		ShareEventService service = new ShareEventService();
		SseEmitter first = service.connect("192.168.1.8", "Mozilla/5.0 (Windows NT 10.0) Chrome/120");
		SseEmitter second = service.connect("192.168.1.8", "Mozilla/5.0 (Windows NT 10.0) Chrome/120");

		assertThat(service.onlineDevices()).singleElement().satisfies(device -> {
			assertThat(device.address()).isEqualTo("192.168.1.8");
			assertThat(device.name()).isEqualTo("Windows · Chrome");
			assertThat(device.connectionCount()).isEqualTo(2);
		});

		first.complete();
		second.complete();
	}

	@Test
	void distinguishesDifferentDeviceTypes() {
		ShareEventService service = new ShareEventService();
		service.connect("192.168.1.9", "Mozilla/5.0 (iPhone; CPU iPhone OS) AppleWebKit Safari/605");
		service.connect("192.168.1.10", "Mozilla/5.0 (X11; Linux x86_64) Firefox/120");

		assertThat(service.onlineDevices()).extracting(OnlineDevice::type)
				.containsExactlyInAnyOrder("MOBILE", "DESKTOP");
	}
}
