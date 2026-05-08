package com.example.googleAttractionsGpx.data.repository

import com.example.googleAttractionsGpx.domain.models.Coordinates
import java.io.InputStream
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

object GpxTrackParser {

    fun parseTrackPoints(input: InputStream): List<Coordinates> {
        val points = mutableListOf<Coordinates>()
        val factory = SAXParserFactory.newInstance()
        val parser = factory.newSAXParser()
        parser.parse(input, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                if (qName == "trkpt" && attributes != null) {
                    val lat = attributes.getValue("lat")?.toDoubleOrNull()
                    val lon = attributes.getValue("lon")?.toDoubleOrNull()
                    if (lat != null && lon != null) {
                        points.add(Coordinates(lat, lon))
                    }
                }
            }
        })
        return points
    }
}
