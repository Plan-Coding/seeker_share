package com.seeker.share.security;

import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class PasswordPolicy {

	public static final int MIN_LENGTH = 12;

	public void validate(String password, String username) {
		List<String> errors = new ArrayList<>();
		if (password == null || password.length() < MIN_LENGTH) errors.add("至少 12 个字符");
		if (password != null && password.length() > 128) errors.add("不能超过 128 个字符");
		if (password == null || password.chars().noneMatch(Character::isUpperCase)) errors.add("包含大写字母");
		if (password == null || password.chars().noneMatch(Character::isLowerCase)) errors.add("包含小写字母");
		if (password == null || password.chars().noneMatch(Character::isDigit)) errors.add("包含数字");
		if (password == null || password.chars().noneMatch(value -> !Character.isLetterOrDigit(value))) errors.add("包含特殊字符");
		if (password != null && password.chars().anyMatch(Character::isWhitespace)) errors.add("不能包含空白字符");
		if (password != null && username != null && password.toLowerCase().contains(username.toLowerCase())) {
			errors.add("不能包含用户名");
		}
		if (!errors.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码强度不足：" + String.join("、", errors));
		}
	}
}
