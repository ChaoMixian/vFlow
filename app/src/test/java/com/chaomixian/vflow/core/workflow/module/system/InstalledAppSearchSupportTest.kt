package com.chaomixian.vflow.core.workflow.module.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstalledAppSearchSupportTest {

    @Test
    fun rankCandidates_prefersExactAppLabelMatch() {
        val matches = InstalledAppSearchSupport.rankCandidates(
            query = "IT之家",
            candidates = listOf(
                InstalledAppSearchCandidate(
                    appName = "IT之家",
                    packageName = "com.ruanmei.ithome",
                    activityName = "com.ruanmei.ithome.MainActivity",
                    userId = 0,
                    userLabel = "用户 0",
                    isLaunchable = true,
                ),
                InstalledAppSearchCandidate(
                    appName = "今日头条",
                    packageName = "com.ss.android.article.news",
                    activityName = "com.ss.android.article.news.MainActivity",
                    userId = 0,
                    userLabel = "用户 0",
                    isLaunchable = true,
                ),
            ),
            maxResults = 5,
        )

        assertEquals("com.ruanmei.ithome", matches.first().candidate.packageName)
        assertTrue(matches.first().isExactMatch)
    }

    @Test
    fun rankCandidates_canMatchPackageFragmentWhenAppLabelDiffers() {
        val matches = InstalledAppSearchSupport.rankCandidates(
            query = "ithome",
            candidates = listOf(
                InstalledAppSearchCandidate(
                    appName = "IT之家",
                    packageName = "com.ruanmei.ithome",
                    activityName = "com.ruanmei.ithome.MainActivity",
                    userId = 0,
                    userLabel = "用户 0",
                    isLaunchable = true,
                ),
                InstalledAppSearchCandidate(
                    appName = "哔哩哔哩",
                    packageName = "tv.danmaku.bili",
                    activityName = "tv.danmaku.bili.MainActivityV2",
                    userId = 0,
                    userLabel = "用户 0",
                    isLaunchable = true,
                ),
            ),
            maxResults = 5,
        )

        assertEquals("com.ruanmei.ithome", matches.first().candidate.packageName)
    }

    @Test
    fun normalizeForMatching_removesSeparators() {
        assertEquals(
            "it之家2026",
            InstalledAppSearchSupport.normalizeForMatching(" IT-之家 2026 "),
        )
    }
}
