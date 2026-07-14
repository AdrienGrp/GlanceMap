package com.glancemap.glancemapwearos.presentation.features.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OamDownloadCatalogTest {
    @Test
    fun chinaUsesDedicatedOamFolder() {
        val chinaAreas = OamDownloadCatalog.areas.filter { it.continent == "China" }

        assertEquals(EXPECTED_CHINA_REGIONS, chinaAreas.map { it.region }.toSet())
        assertTrue(chinaAreas.all { "/mapsV5/china/Ch-" in it.mapZipUrl })
        assertTrue(chinaAreas.all { "/pois/mapsforge/china/Ch-" in it.poiZipUrl })
    }

    @Test
    fun asiaDoesNotExposeObsoleteChinaPackages() {
        val asiaRegions =
            OamDownloadCatalog.areas
                .filter { it.continent == "Asia" }
                .map { it.region }
                .toSet()

        assertTrue(asiaRegions.intersect(OBSOLETE_ASIA_CHINA_REGIONS).isEmpty())
    }

    private companion object {
        val EXPECTED_CHINA_REGIONS =
            setOf(
                "Ch-Beijing-Hebei-Shangdong",
                "Ch-Gansu-Hunan",
                "Ch-Guangdong-Hainan",
                "Ch-Guangxi-Hainan",
                "Ch-Heilongjiang-Jilin-Liaoning",
                "Ch-Henan-Hubei",
                "Ch-HongKong-Macau",
                "Ch-Jiangsu-Anhui-Zhejiang",
                "Ch-Jiangxi-Fujian",
                "Ch-Mongolia-East",
                "Ch-Mongolia-West",
                "Ch-Qinghai-Guizhou-Ningxia",
                "Ch-Shaanxi-Shanxi",
                "Ch-Sichuan-Chongqing",
                "Ch-Tibet",
                "Ch-Xinjiang",
                "Ch-Yunnan",
            )
        val OBSOLETE_ASIA_CHINA_REGIONS =
            setOf(
                "China-East",
                "China-North",
                "China-South",
                "China-West",
                "HongKong-Macau",
                "Tibet",
            )
    }
}
