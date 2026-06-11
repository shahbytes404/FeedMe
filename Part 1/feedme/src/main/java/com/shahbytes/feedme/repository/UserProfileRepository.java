package com.shahbytes.feedme.repository;

import com.shahbytes.feedme.models.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfile, String> {
}
