package com.petrichor.backend.media

import org.springframework.data.jpa.repository.JpaRepository

interface AssetVariantRepository : JpaRepository<AssetVariant, Long> {

    /** 한 에셋의 모든 변환물 조회. */
    fun findByAssetId(assetId: Long): List<AssetVariant>
}
