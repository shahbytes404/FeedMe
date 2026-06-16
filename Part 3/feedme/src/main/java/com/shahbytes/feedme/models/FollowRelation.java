package com.shahbytes.feedme.models;

import jakarta.persistence.*;

@Entity
@Table(name = "follow_relations")
public class FollowRelation {

    @EmbeddedId
    private FollowRelationId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("followerId")
    @JoinColumn(name = "follower_id", nullable = false)
    private UserProfile follower;

    @ManyToOne
    @MapsId("targetUserId")
    @JoinColumn(name = "target_user_id", nullable = false)
    private UserProfile targetUser;

    protected FollowRelation() {
    }

    public FollowRelation(UserProfile follower, UserProfile targetUser) {
        this.id = new FollowRelationId(follower.getId(), targetUser.getId());
        this.follower = follower;
        this.targetUser = targetUser;
    }

    public UserProfile getTargetUser() {
        return targetUser;
    }

    public UserProfile getFollower() {
        return follower;
    }

    public FollowRelationId getId() {
        return id;
    }
}
