package com.petrichor.backend.course

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.Optional

interface CourseRepository : JpaRepository<Course, Long> {

    /**
     * 시퀀싱용 단건 로드: course + courseAssets + asset 을 한 방에 fetch(N+1 제거).
     *
     * fetch join + @EntityGraph 조합으로 courseAssets와 그 안의 asset까지 한 쿼리로 로드한다.
     * DISTINCT는 컬렉션 fetch join 시 중복 루트 제거용. 정렬은 SequencingService에서 sortOrder로 수행.
     */
    @EntityGraph(attributePaths = ["courseAssets", "courseAssets.asset"])
    @Query("select distinct c from Course c left join c.courseAssets ca where c.id = :id")
    fun findWithAssetsById(id: Long): Optional<Course>
}
