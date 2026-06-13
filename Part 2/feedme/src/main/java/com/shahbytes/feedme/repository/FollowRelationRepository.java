package com.shahbytes.feedme.repository;

import com.shahbytes.feedme.models.FollowRelation;
import com.shahbytes.feedme.models.FollowRelationId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FollowRelationRepository extends JpaRepository<FollowRelation, FollowRelationId> {

    List<FollowRelation> findByFollower_Id(String followerId);

    List<FollowRelation> findByTargetUser_Id(String targetUserId);

    long countByFollower_Id(String followerId);
}
