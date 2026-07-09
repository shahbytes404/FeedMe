package com.shahbytes.feedme.models;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EqualsAndHashCode
public class FollowRelationId {
    @Column(name = "follower_id")
    private String followerId;

    @Column(name = "target_user_id")
    private String targetUserId;
}
