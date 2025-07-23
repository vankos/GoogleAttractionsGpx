package com.example.googleAttractionsGpx.domain.repository

import com.example.googleAttractionsGpx.domain.models.Coordinates
import com.example.googleAttractionsGpx.domain.models.PointData

interface IGpxGenerator {
    fun getData(coordinates: Coordinates): List<PointData>
    fun generateGpx(pointDataList: List<PointData>): String
}
