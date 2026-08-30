package com.seeker.share.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DocumentSaveRequest(
		@NotBlank(message = "文档标题不能为空")
		@Size(max = 512, message = "标题过长")
		String title,

		@Size(max = 128, message = "分类过长")
		String category,

		@Size(max = 512, message = "标签过多")
		String tags,

		String content) {

	/** 协作保存(由 WebSocket 触发的落库,标题等元数据不变)。 */
	public static DocumentSaveRequest collaborative(String content) {
		return new DocumentSaveRequest(null, null, null, content);
	}
}
