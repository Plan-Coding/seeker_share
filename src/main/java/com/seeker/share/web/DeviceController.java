package com.seeker.share.web;

import com.seeker.share.common.ApiResponse;
import com.seeker.share.share.OnlineDevice;
import com.seeker.share.share.ShareEventService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

	private final ShareEventService events;

	public DeviceController(ShareEventService events) {
		this.events = events;
	}

	@GetMapping
	public ApiResponse<List<OnlineDevice>> onlineDevices() {
		return ApiResponse.ok(events.onlineDevices());
	}
}
