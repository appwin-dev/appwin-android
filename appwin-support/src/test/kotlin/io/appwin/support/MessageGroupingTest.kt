package io.appwin.support

import io.appwin.support.domain.Message
import io.appwin.support.domain.MessageAuthorType
import io.appwin.support.ui.groupMessages
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Only the last message of a run carries the avatar and the timestamp, so what
 * matters here is where a run ends.
 */
class MessageGroupingTest {

  private fun message(
    id: String,
    author: MessageAuthorType,
    atMillis: Long,
  ) = Message(
    id = id,
    authorType = author,
    authorName = null,
    body = id,
    readAtMillis = null,
    createdAtMillis = atMillis,
  )

  private val minute = 60_000L

  @Test
  fun `un message seul ferme son propre groupe`() {
    val out = groupMessages(listOf(message("a", MessageAuthorType.CUSTOMER, minute)))

    assertEquals(listOf(true), out.map { it.isLastInGroup })
  }

  @Test
  fun `trois messages du meme auteur dans la meme minute ne montrent qu un horodatage`() {
    val out = groupMessages(
      listOf(
        message("a", MessageAuthorType.CUSTOMER, minute),
        message("b", MessageAuthorType.CUSTOMER, minute + 1_000),
        message("c", MessageAuthorType.CUSTOMER, minute + 2_000),
      ),
    )

    assertEquals(listOf(false, false, true), out.map { it.isLastInGroup })
  }

  @Test
  fun `changer d auteur ferme le groupe`() {
    val out = groupMessages(
      listOf(
        message("a", MessageAuthorType.CUSTOMER, minute),
        message("b", MessageAuthorType.ORGANIZATION_MEMBER, minute + 1_000),
      ),
    )

    assertEquals(listOf(true, true), out.map { it.isLastInGroup })
  }

  @Test
  fun `deux minutes differentes ne se groupent pas`() {
    // Deux messages à une minute d'écart ne sont pas une rafale : masquer
    // l'heure du premier perdrait une information réelle.
    val out = groupMessages(
      listOf(
        message("a", MessageAuthorType.CUSTOMER, minute),
        message("b", MessageAuthorType.CUSTOMER, 2 * minute),
      ),
    )

    assertEquals(listOf(true, true), out.map { it.isLastInGroup })
  }

  @Test
  fun `l IA et l humain du studio restent deux auteurs distincts`() {
    // Les deux sont `isStudio`, mais la bulle nomme l'un et pas l'autre : les
    // grouper masquerait qui a répondu.
    val out = groupMessages(
      listOf(
        message("a", MessageAuthorType.AI_ASSISTANT, minute),
        message("b", MessageAuthorType.ORGANIZATION_MEMBER, minute + 1_000),
      ),
    )

    assertEquals(listOf(true, true), out.map { it.isLastInGroup })
  }

  @Test
  fun `une liste vide ne produit rien`() {
    assertEquals(emptyList<Boolean>(), groupMessages(emptyList()).map { it.isLastInGroup })
  }
}
