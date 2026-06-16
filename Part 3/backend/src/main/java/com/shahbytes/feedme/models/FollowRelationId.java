package com.shahbytes.feedme.models;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;

@Embeddable
public class FollowRelationId {
    @Column(name = "follower_id")
    private String followerId;

    @Column(name = "target_user_id")
    private String targetUserId;

    protected FollowRelationId() {

    }

    public FollowRelationId(String followerId, String targetUserId) {
        this.followerId = followerId;
        this.targetUserId = targetUserId;
    }

    public String getFollowerId() {
        return followerId;
    }

    public String getTargetUserId() {
        return targetUserId;
    }


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        FollowRelationId that = (FollowRelationId) o;
        return Objects.equals(followerId, that.followerId) && Objects.equals(targetUserId, that.targetUserId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(followerId, targetUserId);
    }
}
