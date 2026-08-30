package com.seeker.share.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
	Optional<AppUser> findByUsernameIgnoreCase(String username);
	boolean existsByUsernameIgnoreCase(String username);
	long countByRoles_Id(UUID roleId);
}
