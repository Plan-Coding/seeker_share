package com.seeker.share.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

	private final AppUserRepository users;

	public DatabaseUserDetailsService(AppUserRepository users) {
		this.users = users;
	}

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		return users.findByUsernameIgnoreCase(username)
				.map(UserPrincipal::from)
				.orElseThrow(() -> new UsernameNotFoundException("账户不存在"));
	}
}
