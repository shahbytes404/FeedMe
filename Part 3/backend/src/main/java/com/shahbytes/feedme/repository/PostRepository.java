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

public interface PostRepository extends JpaRepository<Post, String> {

    // 100 posts, 1 query -> 100 posts, 100 query -> 100 authors => 101 queries
    @EntityGraph(attributePaths = "author")
    List<Post> findByAuthor_IdOrderByCreatedAtDescIdDesc(String authorId);

    @EntityGraph(attributePaths = "author")
    List<Post> findByAuthor_idInOrderByCreatedAtDescIdDesc(Collection<String> authorIds, Pageable pageable);

    @EntityGraph(attributePaths = "author")
    List<Post> findByAuthor_IdOrderByCreatedAtDescIdDesc(String authorId, Pageable pageable);

    @EntityGraph(attributePaths = "author")
    @Query("""
                select p 
                from Post p 
                where p.author.id in :authorIds
                      and (p.createdAt < :createdAt
                                  or(p.createdAt = :createdAt and p.id < :postId)
                                              )
                      order by p.createdAt desc, p.id desc
            """)
    List<Post> findHomeFeedPageAfterCursor(
            @Param("authorIds") Collection<String> authorIds,
            @Param("created") Instant createdAt,
            @Param("postId") String postId,
            Pageable pageable
    );

    long countByAuthor_IdIn(Collection<String> authorIds);

    long countByAuthor_Id(String authorId);

    @EntityGraph(attributePaths = "author")
    @Query("""
                select p
                from Post p
                  where p.author.id = :authorId
                        and (p.createdAt < :createdAt or 
                                     (p.createdAt = :createdAt and p.id < :postId))
                        order by p.createdAt desc, p.id desc            
            """)
    List<Post> findUserFeedPageAfterCursor(String authorId, Instant createdAt, String postId, Pageable pageable);
}
