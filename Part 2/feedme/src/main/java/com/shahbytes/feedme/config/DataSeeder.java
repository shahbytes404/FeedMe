package com.shahbytes.feedme.config;

import com.shahbytes.feedme.models.FollowRelation;
import com.shahbytes.feedme.models.Post;
import com.shahbytes.feedme.models.UserProfile;
import com.shahbytes.feedme.repository.FollowRelationRepository;
import com.shahbytes.feedme.repository.PostRepository;
import com.shahbytes.feedme.repository.UserProfileRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner seedFeedMeData(
            UserProfileRepository userProfileRepository,
            FollowRelationRepository followRelationRepository,
            PostRepository postRepository
    ) {
        return ignored -> {
            if (userProfileRepository.count() > 0L) {
                return;
            }

            UserProfile ava = new UserProfile("u1", "ava", "Ava Chen", "Plaform engineer", false);

            UserProfile marcus = new UserProfile("u2", "marcus", "Marcus Vale", "Hot-user creator with a global audience", true);

            UserProfile zoya = new UserProfile("u3", "zoya", "Zoya Singh", "Specialist in something", false);

            UserProfile lina = new UserProfile("u4", "lina", "Lina tina", "Very much lenient", true);


            List<UserProfile> users = List.of(ava, marcus, zoya, lina);
            userProfileRepository.saveAll(users);

            followRelationRepository.saveAll(
                    List.of(
                            new FollowRelation(ava, marcus),
                            new FollowRelation(ava, zoya),
                            new FollowRelation(ava, lina),
                            new FollowRelation(marcus, lina),
                            new FollowRelation(marcus, zoya),
                            new FollowRelation(zoya, marcus),
                            new FollowRelation(lina, marcus),
                            new FollowRelation(lina, ava)

                    )
            );

            postRepository.saveAll(
                    Stream.of(
                            new Post(UUID.randomUUID().toString(), marcus, "Hi my name is marcus and i am a hot user"),
                            new Post(UUID.randomUUID().toString(), zoya, "Hi my name is zoya and i am a normal user"),
                            new Post(UUID.randomUUID().toString(), lina, "Hi my name is lina and i am a hot user"),
                            new Post(UUID.randomUUID().toString(), ava, "Hi my name is ava and i am a normal user"),
                            new Post(UUID.randomUUID().toString(), ava, "Why cant i be a hot user?"),
                            new Post(UUID.randomUUID().toString(), marcus, "Only great people can be hot users")
                    ).toList()
            );
        };
    }
}
