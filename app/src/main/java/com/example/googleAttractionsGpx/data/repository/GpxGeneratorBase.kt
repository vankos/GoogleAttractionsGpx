package com.example.googleAttractionsGpx.data.repository

import com.example.googleAttractionsGpx.domain.models.PointData
import com.example.googleAttractionsGpx.domain.repository.IGpxGenerator

abstract class GpxGeneratorBase : IGpxGenerator {

    override fun generateGpx(pointDataList: List<PointData>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""").append("\n")
        sb.append("""<gpx version="1.1" creator="GooglePlaceGpxGenerator" xmlns="http://www.topografix.com/GPX/1/1">""")
            .append("\n")

        pointDataList.forEach { point ->
            sb.append("""  <wpt lat="${point.coordinates.latitude}" lon="${point.coordinates.longitude}">""").append("\n")
            val escapedName = point.name.replace("&", "&amp;")
            sb.append("""    <name>$escapedName</name>""").append("\n")
            val escapedDescription = point.description.replace("&", "&amp;")
            sb.append("""    <desc>$escapedDescription</desc>""").append("\n")
            sb.append("""  </wpt>""").append("\n")
        }

        sb.append("</gpx>").append("\n")
        return sb.toString()
    }
}
