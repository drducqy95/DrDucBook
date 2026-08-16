package io.legado.app.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ReadRecordLocalizationTest {

    @Test
    fun `Vietnamese duration and friendly dates contain no Chinese units`() {
        val duration =
            1L * 24 * 60 * 60 * 1000 +
                2L * 60 * 60 * 1000 +
                3L * 60 * 1000 +
                4L * 1000
        val durationFormatter = ReadDurationFormatter(
            days = { "$it ngày" },
            hours = { "$it giờ" },
            minutes = { "$it phút" },
            seconds = { "$it giây" },
            zero = "0 giây",
        )
        val dateLabels = StringUtils.FriendlyDateLabels(
            today = "Hôm nay",
            yesterday = "Hôm qua",
            daysAgo = { "$it ngày trước" },
        )

        assertEquals("1 ngày 2 giờ 3 phút 4 giây", formatReadDuration(duration, durationFormatter))
        assertEquals("0 giây", formatReadDuration(0, durationFormatter))
        assertEquals(
            "Hôm nay",
            StringUtils.formatFriendlyDate(
                "2026-07-18",
                today = LocalDate.of(2026, 7, 18),
                labels = dateLabels,
            )
        )
        assertEquals(
            "Hôm qua",
            StringUtils.formatFriendlyDate(
                "2026-07-17",
                today = LocalDate.of(2026, 7, 18),
                labels = dateLabels,
            )
        )
        assertEquals(
            "3 ngày trước",
            StringUtils.formatFriendlyDate(
                "2026-07-15",
                today = LocalDate.of(2026, 7, 18),
                labels = dateLabels,
            )
        )
    }

    @Test
    fun `TOC word counts are localized at display time`() {
        val vietnameseWords = { count: String -> "$count chữ" }
        val vietnameseTenThousand = { count: String -> "$count vạn chữ" }
        val englishWords = { count: String -> "$count words" }
        val englishTenThousand = { count: String -> "${count}0K words" }

        assertEquals(
            "6613 chữ",
            StringUtils.formatWordCountForDisplay(
                "6613字",
                vietnameseWords,
                vietnameseTenThousand,
            )
        )
        assertEquals(
            "1.2 vạn chữ",
            StringUtils.formatWordCountForDisplay(
                "1.2万字",
                vietnameseWords,
                vietnameseTenThousand,
            )
        )
        assertEquals(
            "6613 words",
            StringUtils.formatWordCountForDisplay(
                "6613字",
                englishWords,
                englishTenThousand,
            )
        )
        assertEquals(
            "không rõ",
            StringUtils.formatWordCountForDisplay(
                "không rõ",
                vietnameseWords,
                vietnameseTenThousand,
            )
        )
    }
}
