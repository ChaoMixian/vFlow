package com.chaomixian.vflow.ui.chat

import androidx.compose.ui.graphics.Color

internal object ChatBenchmarkCatalog {
    private const val VARIANT_BASE = "base"
    private const val VARIANT_SPONSORED = "sponsored_inserted"
    private const val VARIANT_LOCKED = "first_locked"
    private const val VARIANT_REWRITTEN = "rewritten_copy"
    private const val VARIANT_DEEP_SCROLL = "deep_scroll"
    private const val VARIANT_BLOCKING_SHEET = "blocking_sheet"

    fun defaultSuite(): ChatBenchmarkSuite {
        val definitions = builtInDefinitions()
        val policy = ChatBenchmarkExecutionPolicy()
        val scenes = definitions
            .groupBy { it.sceneId }
            .values
            .map { sceneDefinitions ->
                val first = sceneDefinitions.first()
                ChatBenchmarkScene(
                    id = first.sceneId,
                    familyId = first.familyId,
                    title = first.sceneTitle,
                    description = first.sceneDescription,
                    variants = sceneDefinitions.map { definition ->
                        ChatBenchmarkVariant(
                            id = definition.variantId,
                            title = definition.variantTitle,
                            description = definition.variantDescription,
                            isBase = definition.variantId == VARIANT_BASE,
                        )
                    },
                )
            }

        return ChatBenchmarkSuite(
            id = "chat_benchmark_v1",
            title = "Chat Benchmark v1",
            description = "Built-in deterministic suite for longer end-to-end tasks with tab switching, deeper feeds, blocking overlays, and action verification.",
            executionPolicy = policy,
            scenes = scenes,
            cases = definitions.map { definition ->
                ChatBenchmarkCase(
                    id = definition.caseId,
                    sceneId = definition.sceneId,
                    familyId = definition.familyId,
                    variantId = definition.variantId,
                    title = definition.caseTitle,
                    prompt = definition.prompt,
                    expectedRoute = "detail",
                    expectedTabId = definition.expectedTabId,
                    expectedContentId = definition.expectedCard.id,
                    expectedAnswerKeywords = definition.expectedAnswerKeywords,
                    forbiddenContentIds = definition.forbiddenContentIds,
                    requiredSavedContentIds = definition.requiredSavedContentIds,
                    maxToolCalls = policy.maxToolCallsPerCase,
                    maxCaseDurationSeconds = policy.maxCaseDurationSeconds,
                    maxNoProgressCycles = policy.maxNoProgressCycles,
                    isBaseCase = definition.variantId == VARIANT_BASE,
                )
            },
        )
    }

    fun definition(caseId: String, variantId: String): ChatBenchmarkCaseDefinition? {
        return builtInDefinitions().firstOrNull { it.caseId == caseId && it.variantId == variantId }
    }

    private fun builtInDefinitions(): List<ChatBenchmarkCaseDefinition> {
        return buildList {
            addAll(informationFeedDefinitions())
            addAll(shortVideoDefinitions())
            addAll(newsReaderDefinitions())
        }
    }

    private fun informationFeedDefinitions(): List<ChatBenchmarkCaseDefinition> {
        val defaultTabId = "info_tab_home"
        val expectedTabId = "info_tab_commute"
        val expectedCardId = "info_story_transit"
        val baseTabs = listOf(
            benchmarkTab(
                id = defaultTabId,
                label = "For You",
                cards = listOf(
                    benchmarkCard(
                        id = "info_home_live",
                        title = "Live Blog: Stadium District Crowds Build",
                        subtitle = "Now",
                        detailBody = "A running live blog tracks traffic around the stadium district before tonight's match.",
                        isActionable = false,
                    ),
                    benchmarkCard(
                        id = "info_home_market",
                        title = "Morning Brief: Chip Stocks Rally",
                        subtitle = "Market Desk",
                        detailBody = "Semiconductor shares led a midday rally after several suppliers reported stronger laptop demand.",
                    ),
                    benchmarkCard(
                        id = "info_home_weather",
                        title = "Weekend Weather Turns Cooler",
                        subtitle = "Weather Center",
                        detailBody = "A coastal wind will lower daytime temperatures and bring a cooler, clearer weekend.",
                    ),
                ),
            ),
            benchmarkTab(
                id = expectedTabId,
                label = "Commute",
                cards = listOf(
                    benchmarkCard(
                        id = expectedCardId,
                        title = "City Transit Adds Late Trains",
                        subtitle = "Metro Desk",
                        detailBody = "The transit bureau added late-night trains on two lines to match weekend event traffic.",
                    ),
                    benchmarkCard(
                        id = "info_story_bus_lane",
                        title = "New Bus Lanes Debut Downtown",
                        subtitle = "Mobility",
                        detailBody = "Crews finished paint and signage for two downtown bus lanes that open next week.",
                    ),
                    benchmarkCard(
                        id = "info_story_bikes",
                        title = "Bike Share Docks Expand Riverfront Access",
                        subtitle = "City Moves",
                        detailBody = "The bike share network added six docks to connect the riverfront trail to nearby stations.",
                    ),
                ),
            ),
            benchmarkTab(
                id = "info_tab_saved",
                label = "Saved",
                cards = listOf(
                    benchmarkCard(
                        id = "info_saved_library",
                        title = "Library Opens Quiet Study Annex",
                        subtitle = "Civic",
                        detailBody = "The central library added a late-evening annex with more quiet study desks.",
                    ),
                    benchmarkCard(
                        id = "info_saved_food",
                        title = "Night Market Guide Maps 20 Food Stalls",
                        subtitle = "Weekend",
                        detailBody = "A new guide maps twenty night market vendors and their late service windows.",
                    ),
                ),
            ),
        )

        val variants = variantDefinitions()
        return variants.map { variant ->
            val tabs = when (variant.id) {
                VARIANT_SPONSORED -> baseTabs.replaceTab(expectedTabId) { tab ->
                    tab.copy(
                        cards = listOf(
                            benchmarkCard(
                                id = "info_ad_workspace",
                                title = "Sponsored: Upgrade Your Workspace",
                                subtitle = "Sponsored",
                                detailBody = "This promoted card is not part of the editorial transit task.",
                                isSponsored = true,
                            )
                        ) + tab.cards
                    )
                }
                VARIANT_LOCKED -> baseTabs.replaceTab(expectedTabId) { tab ->
                    tab.copy(
                        cards = listOf(
                            benchmarkCard(
                                id = "info_live_preview",
                                title = "Transit Camera Preview",
                                subtitle = "Preview only",
                                detailBody = "This preview tile looks important but cannot be opened.",
                                isActionable = false,
                            )
                        ) + tab.cards
                    )
                }
                VARIANT_REWRITTEN -> baseTabs.replaceTab(expectedTabId) { tab ->
                    tab.copy(
                        label = "City Moves",
                        cards = tab.cards.map { card ->
                            if (card.id == expectedCardId) {
                                card.copy(
                                    title = "Blue and Green Lines Gain Late-Night Service",
                                    subtitle = "Mobility Desk",
                                    detailBody = "Blue and Green line trains will keep running later on weekends after major events.",
                                )
                            } else {
                                card
                            }
                        },
                    )
                }
                VARIANT_DEEP_SCROLL -> baseTabs.replaceTab(expectedTabId) { tab ->
                    tab.copy(cards = deepScrollCards("info_scroll", "Commute Brief") + tab.cards)
                }
                else -> baseTabs
            }

            sceneDefinition(
                familyId = "information_feed",
                sceneId = "information_feed",
                sceneTitle = "Information Feed",
                sceneDescription = "A multi-tab editorial feed with semantic distractors, deeper lists, and blocking overlays.",
                variantId = variant.id,
                variantTitle = variant.title,
                variantDescription = variant.description,
                caseTitle = "Find the commute update and report the service change",
                prompt = "Switch to the transit-focused tab, open the lead reported update, and tell me what service changed.",
                tabs = tabs,
                defaultTabId = defaultTabId,
                expectedTabId = expectedTabId,
                expectedCardId = expectedCardId,
                expectedAnswerKeywords = when (variant.id) {
                    VARIANT_REWRITTEN -> listOf("later", "weekends", "trains")
                    else -> listOf("late", "trains", "weekend")
                },
                launchOverlay = if (variant.id == VARIANT_BLOCKING_SHEET) {
                    benchmarkOverlay(
                        id = "info_overlay_tip",
                        title = "Quick Guide",
                        body = "Dismiss this helper sheet before browsing the feed.",
                        dismissLabel = "Dismiss guide",
                    )
                } else {
                    null
                },
            )
        }
    }

    private fun shortVideoDefinitions(): List<ChatBenchmarkCaseDefinition> {
        val defaultTabId = "video_tab_following"
        val expectedTabId = "video_tab_learn"
        val expectedCardId = "video_story_noodles"
        val baseTabs = listOf(
            benchmarkTab(
                id = defaultTabId,
                label = "Following",
                cards = listOf(
                    benchmarkCard(
                        id = "video_follow_city",
                        title = "Night Skate Run",
                        subtitle = "Ayo Chen",
                        detailBody = "Ayo Chen records a quiet night skate through neon-lit streets downtown.",
                    ),
                    benchmarkCard(
                        id = "video_follow_desk",
                        title = "Desk Setup Refresh",
                        subtitle = "Mia Sun",
                        detailBody = "Mia Sun swaps a monitor arm and cable tray to clean up a compact desk.",
                    ),
                ),
            ),
            benchmarkTab(
                id = expectedTabId,
                label = "Learn",
                cards = listOf(
                    benchmarkCard(
                        id = expectedCardId,
                        title = "30-Second Noodles",
                        subtitle = "Chef Lin",
                        detailBody = "Chef Lin shows a fast sesame noodle recipe with scallions and chili oil.",
                    ),
                    benchmarkCard(
                        id = "video_story_tripod",
                        title = "Phone Tripod Lighting Tips",
                        subtitle = "Nora Vale",
                        detailBody = "Nora Vale demonstrates three cheap lighting setups for a phone tripod.",
                    ),
                    benchmarkCard(
                        id = "video_story_keyboard",
                        title = "Mechanical Keyboard Foam Mod",
                        subtitle = "Rhett Ko",
                        detailBody = "Rhett Ko explains how to damp a keyboard case with a simple foam insert.",
                    ),
                ),
            ),
            benchmarkTab(
                id = "video_tab_live",
                label = "Live",
                cards = listOf(
                    benchmarkCard(
                        id = "video_live_room",
                        title = "Creator Q and A Tonight",
                        subtitle = "Live room",
                        detailBody = "A scheduled creator Q and A opens later tonight and is not playable yet.",
                        isActionable = false,
                    ),
                    benchmarkCard(
                        id = "video_live_music",
                        title = "Street Jazz Check-In",
                        subtitle = "Preview",
                        detailBody = "A live music check-in stays in preview until the host starts streaming.",
                        isActionable = false,
                    ),
                ),
            ),
        )

        val variants = variantDefinitions()
        return variants.map { variant ->
            val tabs = when (variant.id) {
                VARIANT_SPONSORED -> baseTabs.replaceTab(expectedTabId) { tab ->
                    tab.copy(
                        cards = listOf(
                            benchmarkCard(
                                id = "video_ad_gimbal",
                                title = "Sponsored: Pocket Gimbal Sale",
                                subtitle = "Sponsored",
                                detailBody = "This promoted clip is not the tutorial creator video for the benchmark.",
                                isSponsored = true,
                            )
                        ) + tab.cards
                    )
                }
                VARIANT_LOCKED -> baseTabs.replaceTab(expectedTabId) { tab ->
                    tab.copy(
                        cards = listOf(
                            benchmarkCard(
                                id = "video_preview_only",
                                title = "Preview: Knife Skills Warmup",
                                subtitle = "Preview only",
                                detailBody = "This tile looks like a lesson but cannot be opened.",
                                isActionable = false,
                            )
                        ) + tab.cards
                    )
                }
                VARIANT_REWRITTEN -> baseTabs.replaceTab(expectedTabId) { tab ->
                    tab.copy(
                        label = "How To",
                        cards = tab.cards.map { card ->
                            if (card.id == expectedCardId) {
                                card.copy(
                                    title = "Fast Pantry Noodles",
                                    subtitle = "Chef Lin",
                                    detailBody = "Chef Lin teaches a pantry noodle bowl with sesame paste and scallions.",
                                )
                            } else {
                                card
                            }
                        },
                    )
                }
                VARIANT_DEEP_SCROLL -> baseTabs.replaceTab(expectedTabId) { tab ->
                    tab.copy(cards = deepScrollCards("video_scroll", "Lesson") + tab.cards)
                }
                else -> baseTabs
            }

            sceneDefinition(
                familyId = "short_video_feed",
                sceneId = "short_video_feed",
                sceneTitle = "Short Video Feed",
                sceneDescription = "A creator video app with tab switching, ads, blocked previews, deeper scroll, and save actions.",
                variantId = variant.id,
                variantTitle = variant.title,
                variantDescription = variant.description,
                caseTitle = "Open, save, and answer from the tutorial clip",
                prompt = "Switch to the tutorial-focused feed, open the first playable creator clip, save it, and tell me the creator and topic.",
                tabs = tabs,
                defaultTabId = defaultTabId,
                expectedTabId = expectedTabId,
                expectedCardId = expectedCardId,
                expectedAnswerKeywords = when (variant.id) {
                    VARIANT_REWRITTEN -> listOf("chef", "lin", "noodles")
                    else -> listOf("chef", "lin", "sesame")
                },
                requiredSavedContentIds = listOf(expectedCardId),
                launchOverlay = if (variant.id == VARIANT_BLOCKING_SHEET) {
                    benchmarkOverlay(
                        id = "video_overlay_tip",
                        title = "Playback Tips",
                        body = "Dismiss this sheet before you can browse clips.",
                        dismissLabel = "Close tips",
                    )
                } else {
                    null
                },
                saveEnabled = true,
                useDarkTheme = true,
            )
        }
    }

    private fun newsReaderDefinitions(): List<ChatBenchmarkCaseDefinition> {
        val defaultTabId = "news_tab_top"
        val expectedTabId = "news_tab_metro"
        val expectedCardId = "news_story_shelter"
        val baseTabs = listOf(
            benchmarkTab(
                id = defaultTabId,
                label = "Top",
                cards = listOf(
                    benchmarkCard(
                        id = "news_story_park",
                        title = "City Council Approves Waterfront Park",
                        subtitle = "Top Story",
                        detailBody = "The city council approved a waterfront park plan that adds a public pier, new trees, and flood-resistant walkways.",
                    ),
                    benchmarkCard(
                        id = "news_story_school",
                        title = "School Lunch Pilot Starts Monday",
                        subtitle = "Education",
                        detailBody = "A lunch pilot program will begin Monday with seasonal menus across eight campuses.",
                    ),
                ),
            ),
            benchmarkTab(
                id = expectedTabId,
                label = "Metro",
                cards = listOf(
                    benchmarkCard(
                        id = expectedCardId,
                        title = "Storm Shelter Network Expands",
                        subtitle = "Metro",
                        detailBody = "The emergency office added three storm shelters and extended weekend staffing hours.",
                    ),
                    benchmarkCard(
                        id = "news_story_crosswalk",
                        title = "School Crosswalk Upgrades Begin",
                        subtitle = "Metro",
                        detailBody = "Crews began upgrading four school crosswalks with brighter lights and raised paint markings.",
                    ),
                    benchmarkCard(
                        id = "news_story_library",
                        title = "Branch Libraries Pilot Sunday Hours",
                        subtitle = "Metro",
                        detailBody = "Two branch libraries will pilot Sunday hours through the end of summer.",
                    ),
                ),
            ),
            benchmarkTab(
                id = "news_tab_policy",
                label = "Policy",
                cards = listOf(
                    benchmarkCard(
                        id = "news_story_budget",
                        title = "Budget Committee Reopens Transit Debate",
                        subtitle = "Policy",
                        detailBody = "The budget committee reopened debate over transit reserves ahead of a final vote.",
                    ),
                    benchmarkCard(
                        id = "news_story_climate",
                        title = "Flood Plan Adds New Drainage Targets",
                        subtitle = "Policy",
                        detailBody = "Officials added three drainage targets to the next flood resilience plan.",
                    ),
                ),
            ),
        )

        val variants = variantDefinitions()
        return variants.map { variant ->
            val tabs = when (variant.id) {
                VARIANT_SPONSORED -> baseTabs.replaceTab(expectedTabId) { tab ->
                    tab.copy(
                        cards = listOf(
                            benchmarkCard(
                                id = "news_ad_finance",
                                title = "Sponsored: Weekly Wealth Newsletter",
                                subtitle = "Sponsored",
                                detailBody = "This promoted article should not be chosen as the metro benchmark target.",
                                isSponsored = true,
                            )
                        ) + tab.cards
                    )
                }
                VARIANT_LOCKED -> baseTabs.replaceTab(expectedTabId) { tab ->
                    tab.copy(
                        cards = listOf(
                            benchmarkCard(
                                id = "news_live_blog",
                                title = "Pinned Live Blog: Storm Response",
                                subtitle = "Live blog",
                                detailBody = "This pinned live blog is visible first but is not the reported article task target.",
                                isActionable = false,
                            )
                        ) + tab.cards
                    )
                }
                VARIANT_REWRITTEN -> baseTabs.replaceTab(expectedTabId) { tab ->
                    tab.copy(
                        label = "City Desk",
                        cards = tab.cards.map { card ->
                            if (card.id == expectedCardId) {
                                card.copy(
                                    title = "Emergency Shelter Plan Adds Weekend Coverage",
                                    subtitle = "City Desk",
                                    detailBody = "Officials added three storm shelters and expanded weekend staffing for the network.",
                                )
                            } else {
                                card
                            }
                        },
                    )
                }
                VARIANT_DEEP_SCROLL -> baseTabs.replaceTab(expectedTabId) { tab ->
                    tab.copy(cards = deepScrollCards("news_scroll", "Metro Brief") + tab.cards)
                }
                else -> baseTabs
            }

            sceneDefinition(
                familyId = "news_reader",
                sceneId = "news_reader",
                sceneTitle = "News Reader",
                sceneDescription = "A sectioned news app with deeper lists, semantic label changes, and modal interruptions.",
                variantId = variant.id,
                variantTitle = variant.title,
                variantDescription = variant.description,
                caseTitle = "Open the metro report and summarize the civic change",
                prompt = "Switch to the metro section, open the lead reported article, and summarize one concrete public change it announces.",
                tabs = tabs,
                defaultTabId = defaultTabId,
                expectedTabId = expectedTabId,
                expectedCardId = expectedCardId,
                expectedAnswerKeywords = listOf("storm", "shelters", "weekend"),
                launchOverlay = if (variant.id == VARIANT_BLOCKING_SHEET) {
                    benchmarkOverlay(
                        id = "news_overlay_tip",
                        title = "Reader Welcome",
                        body = "Dismiss this welcome card before browsing sections.",
                        dismissLabel = "Dismiss welcome",
                    )
                } else {
                    null
                },
            )
        }
    }

    private fun sceneDefinition(
        familyId: String,
        sceneId: String,
        sceneTitle: String,
        sceneDescription: String,
        variantId: String,
        variantTitle: String,
        variantDescription: String,
        caseTitle: String,
        prompt: String,
        tabs: List<BenchmarkTabDefinition>,
        defaultTabId: String,
        expectedTabId: String,
        expectedCardId: String,
        expectedAnswerKeywords: List<String>,
        requiredSavedContentIds: List<String> = emptyList(),
        launchOverlay: BenchmarkOverlayDefinition? = null,
        saveEnabled: Boolean = false,
        useDarkTheme: Boolean = false,
    ): ChatBenchmarkCaseDefinition {
        val expectedCard = tabs
            .flatMap { it.cards }
            .first { it.id == expectedCardId }
        return ChatBenchmarkCaseDefinition(
            caseId = "$sceneId.$variantId",
            familyId = familyId,
            sceneId = sceneId,
            sceneTitle = sceneTitle,
            sceneDescription = sceneDescription,
            variantId = variantId,
            variantTitle = variantTitle,
            variantDescription = variantDescription,
            caseTitle = caseTitle,
            prompt = prompt,
            tabs = tabs,
            defaultTabId = defaultTabId,
            expectedTabId = expectedTabId,
            expectedCard = expectedCard,
            expectedAnswerKeywords = expectedAnswerKeywords,
            requiredSavedContentIds = requiredSavedContentIds,
            launchOverlay = launchOverlay,
            saveEnabled = saveEnabled,
            useDarkTheme = useDarkTheme,
        )
    }

    private fun variantDefinitions(): List<ChatBenchmarkVariant> {
        return listOf(
            variantMeta(VARIANT_BASE, "Base", "Canonical multi-step baseline scene."),
            variantMeta(VARIANT_SPONSORED, "Sponsored", "A promoted card appears before the real target inside the task tab."),
            variantMeta(VARIANT_LOCKED, "First Locked", "The first content-like item in the task tab cannot be opened."),
            variantMeta(VARIANT_REWRITTEN, "Rewritten Copy", "Task labels and visible copy change while the goal stays the same."),
            variantMeta(VARIANT_DEEP_SCROLL, "Deep Scroll", "Several plausible distractors push the target below the first screen."),
            variantMeta(VARIANT_BLOCKING_SHEET, "Blocking Sheet", "A modal helper sheet must be dismissed before progress is possible."),
        )
    }

    private fun benchmarkCard(
        id: String,
        title: String,
        subtitle: String,
        detailBody: String,
        isSponsored: Boolean = false,
        isActionable: Boolean = true,
    ): BenchmarkContentCard {
        return BenchmarkContentCard(
            id = id,
            title = title,
            subtitle = subtitle,
            detailBody = detailBody,
            isSponsored = isSponsored,
            isActionable = isActionable,
        )
    }

    private fun benchmarkTab(
        id: String,
        label: String,
        cards: List<BenchmarkContentCard>,
    ): BenchmarkTabDefinition {
        return BenchmarkTabDefinition(
            id = id,
            label = label,
            cards = cards,
        )
    }

    private fun benchmarkOverlay(
        id: String,
        title: String,
        body: String,
        dismissLabel: String,
    ): BenchmarkOverlayDefinition {
        return BenchmarkOverlayDefinition(
            id = id,
            title = title,
            body = body,
            dismissLabel = dismissLabel,
        )
    }

    private fun variantMeta(
        id: String,
        title: String,
        description: String,
    ) = ChatBenchmarkVariant(
        id = id,
        title = title,
        description = description,
        isBase = id == VARIANT_BASE,
    )

    private fun deepScrollCards(
        prefix: String,
        subtitle: String,
    ): List<BenchmarkContentCard> {
        return listOf(
            benchmarkCard(
                id = "${prefix}_1",
                title = "Morning Roundup",
                subtitle = subtitle,
                detailBody = "A short roundup covers nearby updates that sound relevant but do not answer the task.",
            ),
            benchmarkCard(
                id = "${prefix}_2",
                title = "Community Calendar",
                subtitle = subtitle,
                detailBody = "A civic calendar lists several updates without containing the required target.",
            ),
            benchmarkCard(
                id = "${prefix}_3",
                title = "Neighborhood Notes",
                subtitle = subtitle,
                detailBody = "Local notes create semantic noise before the real answer appears.",
            ),
            benchmarkCard(
                id = "${prefix}_4",
                title = "Service Snapshot",
                subtitle = subtitle,
                detailBody = "A plausible service headline sits above the target and can mislead shallow selection.",
            ),
        )
    }

    private fun List<BenchmarkTabDefinition>.replaceTab(
        tabId: String,
        transform: (BenchmarkTabDefinition) -> BenchmarkTabDefinition,
    ): List<BenchmarkTabDefinition> {
        return map { tab ->
            if (tab.id == tabId) transform(tab) else tab
        }
    }
}

internal data class BenchmarkContentCard(
    val id: String,
    val title: String,
    val subtitle: String,
    val detailBody: String,
    val isSponsored: Boolean = false,
    val isActionable: Boolean = true,
)

internal data class BenchmarkTabDefinition(
    val id: String,
    val label: String,
    val cards: List<BenchmarkContentCard>,
)

internal data class BenchmarkOverlayDefinition(
    val id: String,
    val title: String,
    val body: String,
    val dismissLabel: String,
)

internal data class ChatBenchmarkCaseDefinition(
    val caseId: String,
    val familyId: String,
    val sceneId: String,
    val sceneTitle: String,
    val sceneDescription: String,
    val variantId: String,
    val variantTitle: String,
    val variantDescription: String,
    val caseTitle: String,
    val prompt: String,
    val tabs: List<BenchmarkTabDefinition>,
    val defaultTabId: String,
    val expectedTabId: String,
    val expectedCard: BenchmarkContentCard,
    val expectedAnswerKeywords: List<String>,
    val requiredSavedContentIds: List<String>,
    val launchOverlay: BenchmarkOverlayDefinition? = null,
    val saveEnabled: Boolean = false,
    val useDarkTheme: Boolean,
) {
    val allCards: List<BenchmarkContentCard>
        get() = tabs.flatMap { it.cards }

    val forbiddenContentIds: List<String>
        get() = allCards.filter { it.isSponsored }.map { it.id }

    val accentColor: Color
        get() = when (familyId) {
            "short_video_feed" -> Color(0xFFEE6C4D)
            "news_reader" -> Color(0xFF2A9D8F)
            else -> Color(0xFF3D5A80)
        }

    fun tabById(tabId: String?): BenchmarkTabDefinition? {
        return tabs.firstOrNull { it.id == tabId }
    }

    fun cardById(cardId: String?): BenchmarkContentCard? {
        return allCards.firstOrNull { it.id == cardId }
    }
}
