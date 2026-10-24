package `in`.thomas.pocketledger

import android.net.Uri
import androidx.compose.runtime.saveable.Saver

private fun entryFields(e: Entry): List<String> = listOf(
    e.id, e.accountId, e.destinationId.orEmpty(), e.type, e.amount.toString(), e.date.toString(),
    e.dateOnly.toString(), e.payee, e.category, e.note, e.smsKey.orEmpty(), e.deleted.toString()
)
private const val EntryFieldCount = 12
private fun entryFrom(e: List<String>) = Entry(
    id = e[0], accountId = e[1], destinationId = e[2].ifEmpty { null }, type = e[3],
    amount = e[4].toLong(), date = e[5].toLong(), dateOnly = e[6].toBoolean(), payee = e[7],
    category = e[8], note = e[9], smsKey = e[10].ifEmpty { null }, deleted = e[11].toBoolean()
)

val EntryDraftSaver = Saver<Entry?, ArrayList<String>>(
    save = { e -> if (e == null) arrayListOf() else ArrayList(entryFields(e)) },
    restore = { e -> if (e.isEmpty()) null else entryFrom(e) }
)
val AccountDraftSaver = Saver<Account?, ArrayList<String>>(
    save = { a -> if (a == null) arrayListOf() else arrayListOf(a.id, a.name, a.bank, a.suffix, a.opening.toString(), a.openingDate.toString()) },
    restore = { a -> if (a.isEmpty()) null else Account(a[0], a[1], a[2], a[3], a[4].toLong(), a[5].toLong()) }
)

/** A pending transfer link: both entries are stored as drafts so the confirmation survives recreation. */
val EntryPairSaver = Saver<Pair<Entry, Entry>?, ArrayList<String>>(
    save = { p -> if (p == null) arrayListOf() else ArrayList(entryFields(p.first) + entryFields(p.second)) },
    restore = { l -> if (l.size < EntryFieldCount * 2) null else entryFrom(l.subList(0, EntryFieldCount)) to entryFrom(l.subList(EntryFieldCount, EntryFieldCount * 2)) }
)

val TrustedSenderSaver = Saver<TrustedSender?, ArrayList<String>>(
    save = { t -> if (t == null) arrayListOf() else arrayListOf(t.sender, t.bank) },
    restore = { l -> if (l.size < 2) null else TrustedSender(l[0], l[1]) }
)

/** Restore location is kept as its URI string; the document permission outlives recreation within the task. */
val UriSaver = Saver<Uri?, String>(
    save = { it?.toString().orEmpty() },
    restore = { if (it.isEmpty()) null else Uri.parse(it) }
)

/**
 * Preview candidates are rebuilt from sender, body and received time, because parsing is deterministic.
 * Very large scans are dropped instead of risking an oversized saved-state bundle.
 */
private const val MaxSavedPreviewChars = 200_000
val CandidateListSaver = Saver<List<Candidate>?, ArrayList<String>>(
    save = { list ->
        if (list == null || list.sumOf { it.body.length + it.sender.length + 24 } > MaxSavedPreviewChars) arrayListOf()
        else arrayListOf<String>().also { out -> list.forEach { out += listOf(it.sender, it.body, it.received.toString()) } }
    },
    restore = { l ->
        if (l.isEmpty()) null
        else l.chunked(3).filter { it.size == 3 }.mapNotNull { (sender, body, received) ->
            SmsParser.parseSelected(body)?.let { Candidate(sender, body, received.toLong(), it) }
        }
    }
)

val IndexSetSaver = Saver<Set<Int>, ArrayList<Int>>(save = { ArrayList(it) }, restore = { it.toSet() })
