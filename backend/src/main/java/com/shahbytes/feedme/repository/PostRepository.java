package com.shahbytes.feedme.repository;

import com.shahbytes.feedme.models.Post;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface PostRepository extends JpaRepository<Post, String> {
    @EntityGraph(attributePaths = "author")
    List<Post> findByAuthor_IdInOrderByCreatedAtDescIdDesc(Collection<String> authorIds, Pageable pageable);

    @EntityGraph(attributePaths = "author")
    List<Post> findByAuthor_IdOrderByCreatedAtDescIdDesc(String authorId, Pageable pageable);

    @EntityGraph(attributePaths = "author")
    @Query("""
                 select p from Post p
                     where p.author.id in :authorIds
                         and (p.createdAt < :createdAt
                             or (p.createdAt = :createdAt and p.id < :postId))
                                         order by p.createdAt desc, p.id desc
            """)
    List<Post> findHomeFeedPageAfterCursor(@Param("authorIds") Collection<String> authorIds,
                                           @Param("createdAt") Instant createdAt,
                                           @Param("postId") String postId,
                                           Pageable pageable);

    long countByAuthor_IdIn(Set<String> nonHotAuthorIds);

    @EntityGraph(attributePaths = "author")
    @Query("""
                 select p from Post p
                     where p.author.id = :authorId
                         and (p.createdAt < :createdAt
                             or (p.createdAt = :createdAt and p.id < :postId))
                                         order by p.createdAt desc, p.id desc
            """)
    List<Post> findUserFeedPageAfterCursor(
            @Param("authorId") String authorId,
            @Param("createdAt") Instant createdAt,
            @Param("postId") String postId,
            Pageable pageable);

    long countByAuthor_Id(String userId);
}
