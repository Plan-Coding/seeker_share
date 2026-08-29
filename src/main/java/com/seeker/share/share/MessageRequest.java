package com.seeker.share.share;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MessageRequest(
		@NotBlank(message = "消息不能为空")
		@Size(max = 5000, message = "消息不能超过 5000 个字符") String content) {
}
