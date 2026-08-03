package com.ajuia.artjournal

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JournalComposeUiTest {
  @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun demoDataCanBeLoadedAndReviewedAcrossMainSections() {
    composeRule.onNodeWithTag("workspace-local").performClick()

    composeRule
      .onNodeWithText("Создайте или выберите группу зачета")
      .assertIsDisplayed()

    openSection("themes", "Темы зачетов & Проекты")
    openSection("schedule", "Календарь & Расписание")
    openSection("tracker", "Аналитика & Анализ баллов")
    openSection("settings", "Настройки Журнала")

    composeRule.onNodeWithText("Заполнить демо-данными").performClick()
    composeRule.onNodeWithTag("bottom-nav-journal").performClick()

    composeRule.waitUntil(timeoutMillis = 15_000) {
      composeRule
        .onAllNodesWithText("Группа А (Младшая)")
        .fetchSemanticsNodes()
        .isNotEmpty()
    }

    composeRule.onNodeWithText("Группа А (Младшая)").assertIsDisplayed()
    composeRule.onNodeWithText("Иванов Максим").assertIsDisplayed()
    composeRule.onNodeWithText("Петрова София").assertIsDisplayed()
  }

  private fun openSection(testTag: String, title: String) {
    composeRule.onNodeWithTag("bottom-nav-$testTag").performClick()
    composeRule.onNodeWithText(title).assertIsDisplayed()
  }
}
