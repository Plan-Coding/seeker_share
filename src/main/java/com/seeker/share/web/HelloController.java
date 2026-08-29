package com.seeker.share.web;

import com.seeker.share.common.ApiResponse;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
public class HelloController {

	@GetMapping("/hello")
	public ApiResponse<Map<String, String>> hello(
			@RequestParam(defaultValue = "World")
			@Size(min = 1, max = 40, message = "name 长度必须在 1 到 40 之间") String name) {
		return ApiResponse.ok(Map.of("message", "Hello, " + name + "!"));
	}
}
